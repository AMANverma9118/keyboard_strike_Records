package com.keywordrecord.keyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.darkrockstudios.symspellkt.common.SpellCheckSettings
import com.darkrockstudios.symspellkt.common.Verbosity
import com.darkrockstudios.symspellkt.impl.SymSpell
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DictionarySuggestionProvider(context: Context) {

    private val appContext = context.applicationContext
    private val loadExecutor = Executors.newSingleThreadExecutor()
    private val isReady = AtomicBoolean(false)
    private var symSpell: SymSpell? = null
    private var database: SQLiteDatabase? = null

    private val dbHelper = object : SQLiteOpenHelper(appContext, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE words (word TEXT PRIMARY KEY, frequency INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX idx_words_prefix ON words(word)")
            db.execSQL(
                "CREATE TABLE bigrams (word1 TEXT NOT NULL, word2 TEXT NOT NULL, frequency INTEGER NOT NULL, " +
                    "PRIMARY KEY (word1, word2))"
            )
            db.execSQL("CREATE INDEX idx_bigrams_word1 ON bigrams(word1)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS bigrams")
            db.execSQL("DROP TABLE IF EXISTS words")
            onCreate(db)
            populateDatabase(db)
        }
    }

    fun initialize() {
        if (isReady.get()) return
        loadExecutor.execute {
            try {
                val db = dbHelper.writableDatabase
                if (db.wordCount() == 0) {
                    populateDatabase(db)
                }
                database = db
                symSpell = buildSymSpell()
                isReady.set(true)
            } catch (_: Exception) {
                database = dbHelper.readableDatabase
                isReady.set((database?.wordCount() ?: 0) > 0)
            }
        }
    }

    fun isInitialized(): Boolean = isReady.get()

    fun parseComposing(segment: String): ComposingContext {
        if (segment.isEmpty()) {
            return ComposingContext("", "", "", false)
        }
        val normalized = segment.trimEnd()
        val parts = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val isTypingWord = segment.last().isLetter()
        val currentWord = if (isTypingWord) parts.lastOrNull().orEmpty().lowercase(Locale.getDefault()) else ""
        val previousWord = when {
            isTypingWord && parts.size > 1 -> parts[parts.size - 2].lowercase(Locale.getDefault())
            !isTypingWord && parts.isNotEmpty() -> parts.last().lowercase(Locale.getDefault())
            else -> ""
        }
        return ComposingContext(segment, currentWord, previousWord, isTypingWord)
    }

    fun getSuggestions(context: ComposingContext, limit: Int = 5): List<String> {
        val db = database ?: return emptyList()
        if (!isReady.get() || context.segment.isEmpty()) return emptyList()

        val results = linkedSetOf<String>()

        if (context.isTypingWord && context.currentWord.isNotEmpty()) {
            val word = context.currentWord
            if (context.previousWord.isNotEmpty()) {
                getBigramPrefixMatches(db, context.previousWord, word).forEach { results.add(it) }
            }
            getPrefixMatches(db, word).forEach { results.add(it) }
            getSymSpellCorrections(word).forEach { results.add(it) }
            if (results.size < 2 && isKnownWord(db, word)) {
                getNextWords(db, word).forEach { results.add(it) }
            }
        } else if (context.previousWord.isNotEmpty()) {
            getNextWords(db, context.previousWord).forEach { results.add(it) }
        }

        return results.take(limit)
    }

    private fun buildSymSpell(): SymSpell {
        val spell = SymSpell(
            SpellCheckSettings(
                verbosity = Verbosity.Closest,
                topK = 8,
                maxEditDistance = 2.0,
                prefixLength = 7,
                lowerCaseTerms = true
            )
        )
        appContext.assets.open(UNIGRAM_ASSET).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) {
                    val word = parts[0].lowercase(Locale.getDefault())
                    val frequency = parts[1].toDoubleOrNull() ?: return@forEach
                    if (word.isNotEmpty() && word.all { it.isLetter() }) {
                        spell.createDictionaryEntry(word, frequency)
                    }
                }
            }
        }
        return spell
    }

    private fun getSymSpellCorrections(word: String): List<String> {
        if (word.length < 2) return emptyList()
        val spell = symSpell ?: return emptyList()
        return try {
            spell.lookup(word, Verbosity.Closest, editDistance = 2.0)
                .asSequence()
                .filter { it.distance > 0.0 && it.term != word }
                .map { it.term }
                .distinct()
                .take(5)
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun populateDatabase(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            loadUnigrams(db)
            loadBigrams(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun loadUnigrams(db: SQLiteDatabase) {
        val stmt = db.compileStatement("INSERT OR IGNORE INTO words(word, frequency) VALUES(?, ?)")
        appContext.assets.open(UNIGRAM_ASSET).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) {
                    val word = parts[0].lowercase(Locale.getDefault())
                    val frequency = parts[1].toLongOrNull() ?: return@forEach
                    if (word.length >= 1 && word.all { it.isLetter() }) {
                        stmt.bindString(1, word)
                        stmt.bindLong(2, frequency)
                        stmt.executeInsert()
                        stmt.clearBindings()
                    }
                }
            }
        }
    }

    private fun loadBigrams(db: SQLiteDatabase) {
        val stmt = db.compileStatement(
            "INSERT OR REPLACE INTO bigrams(word1, word2, frequency) VALUES(?, ?, ?)"
        )
        appContext.assets.open(BIGRAM_ASSET).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val frequency = parts.last().toLongOrNull() ?: return@forEach
                    val word2 = parts[parts.size - 2].lowercase(Locale.getDefault())
                    val word1 = parts.dropLast(2).joinToString(" ").lowercase(Locale.getDefault())
                    if (word1.isNotEmpty() && word2.isNotEmpty() &&
                        word1.all { it.isLetter() } &&
                        word2.all { it.isLetter() }
                    ) {
                        stmt.bindString(1, word1)
                        stmt.bindString(2, word2)
                        stmt.bindLong(3, frequency)
                        stmt.executeInsert()
                        stmt.clearBindings()
                    }
                }
            }
        }
    }

    private fun getBigramPrefixMatches(db: SQLiteDatabase, previousWord: String, prefix: String): List<String> {
        val cursor = db.rawQuery(
            "SELECT word2 FROM bigrams WHERE word1 = ? AND word2 LIKE ? ORDER BY frequency DESC LIMIT 6",
            arrayOf(previousWord.lowercase(Locale.getDefault()), "$prefix%")
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(it.getString(0))
                }
            }
        }
    }

    private fun getPrefixMatches(db: SQLiteDatabase, prefix: String): List<String> {
        val cursor = db.rawQuery(
            "SELECT word FROM words WHERE word LIKE ? AND word != ? ORDER BY frequency DESC LIMIT 8",
            arrayOf("$prefix%", prefix)
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(it.getString(0))
                }
            }
        }
    }

    private fun isKnownWord(db: SQLiteDatabase, word: String): Boolean {
        val cursor = db.rawQuery("SELECT 1 FROM words WHERE word = ? LIMIT 1", arrayOf(word))
        return cursor.use { it.moveToFirst() }
    }

    private fun getNextWords(db: SQLiteDatabase, previousWord: String): List<String> {
        val lower = previousWord.lowercase(Locale.getDefault())
        val cursor = db.rawQuery(
            "SELECT word2 FROM bigrams WHERE word1 = ? ORDER BY frequency DESC LIMIT 10",
            arrayOf(lower)
        )
        val fromBigrams = cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(it.getString(0))
                }
            }
        }
        if (fromBigrams.isNotEmpty()) return fromBigrams
        return getPopularNextWords(db, lower)
    }

    private fun getPopularNextWords(db: SQLiteDatabase, previousWord: String): List<String> {
        val cursor = db.rawQuery(
            "SELECT word FROM words WHERE word != ? ORDER BY frequency DESC LIMIT 10",
            arrayOf(previousWord)
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(it.getString(0))
                }
            }
        }
    }

    private fun SQLiteDatabase.wordCount(): Int {
        val cursor = rawQuery("SELECT COUNT(*) FROM words", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    companion object {
        private const val DB_NAME = "keyboard_dictionary.db"
        private const val DB_VERSION = 4
        private const val UNIGRAM_ASSET = "frequency_dictionary_en.txt"
        private const val BIGRAM_ASSET = "frequency_bigramdictionary_en.txt"
    }
}
