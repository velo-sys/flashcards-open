package de.flashcards.open.ui.study

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.flashcards.open.R
import de.flashcards.open.data.repo.Settings
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.card.SelfGrade
import de.flashcards.open.domain.scheduler.Rating
import de.flashcards.open.ui.common.OriginChip
import de.flashcards.open.ui.common.appContainer
import de.flashcards.open.ui.common.appViewModel
import de.flashcards.open.ui.common.focusFrame
import de.flashcards.open.ui.markdown.CardContent
import de.flashcards.open.ui.nav.StudyRoute
import de.flashcards.open.ui.theme.LocalRatingColors

@Composable
fun StudyScreen(route: StudyRoute, back: () -> Unit) {
    val vm = appViewModel(key = "study${route.hashCode()}") { c, _ -> StudyViewModel(c, route) }
    val s by vm.ui.collectAsState()
    val settings by appContainer().settings.settings.collectAsState(initial = Settings())
    val rootFocus = remember { FocusRequester() }
    val inputFocus = remember { FocusRequester() }
    val choiceFocus = remember { FocusRequester() }
    var focusedRating by remember { mutableStateOf<Rating?>(null) }

    // On every new card, move the focus to where the answer belongs
    LaunchedEffect(s.card?.id, s.position, s.flipped, s.finished, s.loading) {
        focusedRating = null
        val target = when {
            s.loading || s.finished || s.empty || s.flipped -> rootFocus
            s.type == CardType.INPUT -> inputFocus
            s.type == CardType.CHOICE || s.type == CardType.MATCH -> choiceFocus
            else -> rootFocus
        }
        runCatching { target.requestFocus() }
    }

    fun handleKey(code: Int): Boolean {
        if (s.loading) return false
        if (s.finished || s.empty) {
            return when (code) {
                AndroidKeyEvent.KEYCODE_BUTTON_A, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_SPACE,
                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_BUTTON_B,
                -> { back(); true }
                else -> false
            }
        }
        val confirm = code == AndroidKeyEvent.KEYCODE_BUTTON_A || code == AndroidKeyEvent.KEYCODE_ENTER ||
            code == AndroidKeyEvent.KEYCODE_SPACE || code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
            code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
        if (!s.flipped) {
            // Input card: every key belongs to the text field
            if (s.type == CardType.INPUT) return false
            if (s.type == CardType.CHOICE) {
                val n = digit(code)
                if (n != null && n <= s.choices.size) { vm.choose(n - 1); return true }
                return false // the D-pad and Ⓐ operate the focused button
            }
            if (s.type == CardType.MATCH) {
                val n = digit(code)
                if (n != null && n <= (s.board?.targets?.size ?: 0)) { vm.assign(n - 1); return true }
                // Once every term has a target, Ⓐ checks – before that it assigns the focused target
                if (confirm && s.matchReady) { vm.check(); return true }
                return false
            }
            return when {
                confirm -> { vm.flip(); true }
                code == AndroidKeyEvent.KEYCODE_BUTTON_B || code == AndroidKeyEvent.KEYCODE_BUTTON_X ||
                    code == AndroidKeyEvent.KEYCODE_BUTTON_Y -> true // no function before revealing
                else -> false
            }
        }
        val rating = if (s.threeLevel) {
            when (code) {
                AndroidKeyEvent.KEYCODE_BUTTON_B, AndroidKeyEvent.KEYCODE_1, AndroidKeyEvent.KEYCODE_NUMPAD_1 -> SelfGrade.WRONG.rating
                AndroidKeyEvent.KEYCODE_BUTTON_X, AndroidKeyEvent.KEYCODE_2, AndroidKeyEvent.KEYCODE_NUMPAD_2 -> SelfGrade.PARTIAL.rating
                AndroidKeyEvent.KEYCODE_BUTTON_Y, AndroidKeyEvent.KEYCODE_3, AndroidKeyEvent.KEYCODE_NUMPAD_3 -> SelfGrade.CORRECT.rating
                // Ⓐ confirms the suggested grade
                else -> if (confirm) s.suggestion?.rating ?: focusedRating ?: SelfGrade.CORRECT.rating else null
            }
        } else {
            when (code) {
                AndroidKeyEvent.KEYCODE_BUTTON_B, AndroidKeyEvent.KEYCODE_1, AndroidKeyEvent.KEYCODE_NUMPAD_1 -> Rating.AGAIN
                AndroidKeyEvent.KEYCODE_BUTTON_X, AndroidKeyEvent.KEYCODE_2, AndroidKeyEvent.KEYCODE_NUMPAD_2 -> Rating.HARD
                AndroidKeyEvent.KEYCODE_3, AndroidKeyEvent.KEYCODE_NUMPAD_3 -> Rating.GOOD
                AndroidKeyEvent.KEYCODE_BUTTON_Y, AndroidKeyEvent.KEYCODE_4, AndroidKeyEvent.KEYCODE_NUMPAD_4 -> Rating.EASY
                else -> if (confirm) focusedRating ?: Rating.GOOD else null
            }
        } ?: return false
        vm.rate(rating)
        return true
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                val code = e.key.nativeKeyCode
                when (e.type) {
                    KeyEventType.KeyDown -> handleKey(code)
                    // Swallow key-up of handled keys as well (otherwise B → system back, for one)
                    KeyEventType.KeyUp -> code in HANDLED_KEYS && !s.loading &&
                        !(s.type == CardType.INPUT && !s.flipped)
                    else -> false
                }
            }
            .safeDrawingPadding(),
    ) {
        when {
            s.loading || s.card == null && !s.finished && !s.empty -> Unit
            s.empty -> EndView(stringResource(if (s.cram) R.string.study_no_cards else R.string.study_nothing_due), null, back)
            s.finished -> EndView(stringResource(R.string.study_done), pluralStringResource(R.plurals.study_done_count, s.reviewed, s.reviewed), back)
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val card = s.card ?: return@BoxWithConstraints
                val landscape = maxWidth > maxHeight && maxWidth > 600.dp
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Header(s, back)
                    if (landscape) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CardPane(s.front, settings.cardFontScale, Modifier.weight(1f), onClick = vm::flip)
                            AnswerPane(
                                s = s, fontScale = settings.cardFontScale, inputFocus = inputFocus, choiceFocus = choiceFocus,
                                onInput = vm::onInput, onCheck = vm::check, onChoose = vm::choose, onFlip = vm::flip,
                                onSelectItem = vm::selectItem, onAssign = vm::assign,
                                landscape = true, modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            Column(Modifier.width(180.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Buttons(s, vertical = true, onFlip = vm::flip, onCheck = vm::check, onRate = vm::rate, onFocus = { focusedRating = it }, modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        // On match cards the task is short, terms and targets need the room
                        val match = s.type == CardType.MATCH
                        FlashCard(s, settings.cardFontScale, Modifier.weight(if (match) 0.4f else 1f), onClick = vm::flip)
                        AnswerPane(
                            s = s, fontScale = settings.cardFontScale, inputFocus = inputFocus, choiceFocus = choiceFocus,
                            onInput = vm::onInput, onCheck = vm::check, onChoose = vm::choose, onFlip = vm::flip,
                            onSelectItem = vm::selectItem, onAssign = vm::assign,
                            landscape = false,
                            modifier = Modifier.fillMaxWidth()
                                .then(if (match) Modifier.weight(1f) else Modifier.heightIn(max = 240.dp))
                                .padding(top = 10.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Buttons(s, vertical = false, onFlip = vm::flip, onCheck = vm::check, onRate = vm::rate, onFocus = { focusedRating = it })
                    }
                    Text(
                        stringResource(keysHint(s)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Digit key 1–9 (top row or numpad) as a number, null otherwise. */
private fun digit(code: Int): Int? = when (code) {
    in AndroidKeyEvent.KEYCODE_1..AndroidKeyEvent.KEYCODE_9 -> code - AndroidKeyEvent.KEYCODE_1 + 1
    in AndroidKeyEvent.KEYCODE_NUMPAD_1..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> code - AndroidKeyEvent.KEYCODE_NUMPAD_1 + 1
    else -> null
}

private fun keysHint(s: StudyUi): Int = when {
    !s.flipped && s.type == CardType.INPUT -> R.string.study_keys_hint_input
    !s.flipped && s.type == CardType.CHOICE -> R.string.study_keys_hint_choice
    !s.flipped && s.type == CardType.MATCH -> R.string.study_keys_hint_match
    s.threeLevel -> R.string.study_keys_hint_three
    else -> R.string.study_keys_hint
}

private val HANDLED_KEYS = setOf(
    AndroidKeyEvent.KEYCODE_BUTTON_A, AndroidKeyEvent.KEYCODE_BUTTON_B, AndroidKeyEvent.KEYCODE_BUTTON_X,
    AndroidKeyEvent.KEYCODE_BUTTON_Y, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_SPACE,
    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
) + (AndroidKeyEvent.KEYCODE_1..AndroidKeyEvent.KEYCODE_9) +
    (AndroidKeyEvent.KEYCODE_NUMPAD_1..AndroidKeyEvent.KEYCODE_NUMPAD_9)

@Composable
private fun Header(s: StudyUi, back: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
        Text(
            buildString {
                if (s.cram) append(stringResource(R.string.study_cram_prefix)).append(" · ")
                if (s.title.isNotBlank()) append(s.title).append(" · ")
                append(stringResource(R.string.study_remaining, s.remaining))
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
    s.deck?.let { OriginChip(it.name, s.topic, Modifier.padding(bottom = 8.dp)) }
}

@Composable
private fun FlashCard(s: StudyUi, fontScale: Float, modifier: Modifier, onClick: () -> Unit) {
    val card = s.card ?: return
    val scroll = rememberScrollState()
    LaunchedEffect(card.id, s.position) { scroll.scrollTo(0) }
    CardSurface(modifier, onClick) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp)) {
            CardContent(s.front, Modifier.fillMaxWidth(), fontScale)
            if (s.showBack) {
                DashedDivider()
                CardContent(card.backMd, Modifier.fillMaxWidth(), fontScale)
            }
            if (s.flipped) Tags(card.tags)
        }
    }
}

/**
 * The room for the answer: buttons on choice cards, a text field on input cards, terms and
 * targets on match cards.
 * In landscape the back goes here as well; in portrait the card itself shows it.
 */
@Composable
private fun AnswerPane(
    s: StudyUi,
    fontScale: Float,
    inputFocus: FocusRequester,
    choiceFocus: FocusRequester,
    onInput: (String) -> Unit,
    onCheck: () -> Unit,
    onChoose: (Int) -> Unit,
    onFlip: () -> Unit,
    onSelectItem: (Int) -> Unit,
    onAssign: (Int) -> Unit,
    landscape: Boolean,
    modifier: Modifier,
) {
    when (s.type) {
        CardType.CHOICE -> ChoicesPane(s, choiceFocus, onChoose, modifier)
        CardType.MATCH -> MatchPane(s, choiceFocus, onSelectItem, onAssign, modifier)
        // In landscape the expected answer belongs here, in portrait it sits inside the card
        CardType.INPUT -> InputPane(s, fontScale, inputFocus, onInput, onCheck, showExpected = landscape, modifier = modifier)
        // Cloze and basic cards: in portrait the back is already part of the card
        CardType.BASIC, CardType.CLOZE -> if (landscape) {
            when {
                s.showBack -> CardPane(s.card?.backMd.orEmpty(), fontScale, modifier, tags = s.card?.tags.orEmpty(), onClick = {})
                !s.flipped -> HiddenPane(modifier, onClick = onFlip)
                else -> Unit
            }
        }
    }
}

@Composable
private fun ChoicesPane(s: StudyUi, first: FocusRequester, onChoose: (Int) -> Unit, modifier: Modifier) {
    val colors = LocalRatingColors.current
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        s.choices.forEachIndexed { i, choice ->
            val container = when {
                !s.flipped -> MaterialTheme.colorScheme.secondaryContainer
                choice.correct -> colors.good
                i == s.chosen -> colors.again
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
            val content = when {
                !s.flipped -> MaterialTheme.colorScheme.onSecondaryContainer
                choice.correct || i == s.chosen -> Color.White
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Button(
                onClick = { onChoose(i) },
                colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                    .focusFrame(RoundedCornerShape(10.dp)),
            ) {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(choice.text, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
                if (s.flipped) {
                    if (choice.correct) Text("✓", fontWeight = FontWeight.Bold)
                    else if (i == s.chosen) Text("✗", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Match card: the terms with their target on top, the targets to choose from below.
 * A term is tapped, then its target; the digits 1–9 pick a target directly.
 * Once revealed, every term says whether it is right, and otherwise shows the correct solution.
 */
@Composable
private fun MatchPane(
    s: StudyUi,
    first: FocusRequester,
    onSelectItem: (Int) -> Unit,
    onAssign: (Int) -> Unit,
    modifier: Modifier,
) {
    val board = s.board ?: return
    val colors = LocalRatingColors.current
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (s.flipped) {
            Text(
                stringResource(R.string.study_match_result, s.matchCorrect, board.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = when (s.matchCorrect) {
                    board.size -> colors.good
                    0 -> colors.again
                    else -> colors.hard
                },
            )
        }
        board.items.forEachIndexed { i, item ->
            val target = s.assignment[i]?.let { board.targets.getOrNull(it) }
            val right = target != null && board.isCorrect(i, target)
            val active = !s.flipped && i == s.activeItem
            val border = when {
                s.flipped -> if (right) colors.good else colors.again
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(if (active || s.flipped) 2.dp else 1.dp, border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !s.flipped) { onSelectItem(i) },
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "→",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Text(
                        target ?: stringResource(R.string.study_match_open),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (target != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (target != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (s.flipped) {
                        Text(
                            if (right) "✓" else "✗",
                            fontWeight = FontWeight.Bold,
                            color = if (right) colors.good else colors.again,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            if (s.flipped && !right) {
                Text(
                    stringResource(R.string.study_match_solution, board.expected(i)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.good,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        if (!s.flipped) {
            Text(
                stringResource(R.string.study_match_targets),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            board.targets.forEachIndexed { i, target ->
                val used = s.assignment.containsValue(i)
                Button(
                    onClick = { onAssign(i) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (used) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (used) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .focusFrame(RoundedCornerShape(10.dp)),
                ) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(target, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InputPane(
    s: StudyUi,
    fontScale: Float,
    focus: FocusRequester,
    onInput: (String) -> Unit,
    onCheck: () -> Unit,
    showExpected: Boolean,
    modifier: Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = s.input,
            onValueChange = onInput,
            readOnly = s.flipped,
            singleLine = true,
            label = { Text(stringResource(R.string.study_input_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCheck() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).focusFrame(),
        )
        Verdict(s.suggestion)
        if (showExpected && s.showBack) {
            Text(
                stringResource(R.string.study_expected),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            CardContent(s.card?.backMd.orEmpty(), Modifier.fillMaxWidth().padding(top = 4.dp), fontScale)
        }
    }
}

/** The result of the automatic check – just a hint, the grading happens below. */
@Composable
private fun Verdict(grade: SelfGrade?) {
    if (grade == null) return
    val colors = LocalRatingColors.current
    val (label, color) = when (grade) {
        SelfGrade.CORRECT -> R.string.study_result_correct to colors.good
        SelfGrade.PARTIAL -> R.string.study_result_partial to colors.hard
        SelfGrade.WRONG -> R.string.study_result_wrong to colors.again
    }
    Text(
        stringResource(label),
        color = color,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CardPane(md: String, fontScale: Float, modifier: Modifier, tags: String = "", onClick: () -> Unit) {
    CardSurface(modifier, onClick) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            CardContent(md, Modifier.fillMaxWidth(), fontScale)
            Tags(tags)
        }
    }
}

@Composable
private fun HiddenPane(modifier: Modifier, onClick: () -> Unit) {
    CardSurface(modifier, onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("?", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun CardSurface(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        content = content,
    )
}

@Composable
private fun Tags(tags: String) {
    if (tags.isNotBlank()) {
        Text(tags, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun DashedDivider() {
    val color = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().padding(vertical = 18.dp).height(2.dp)) {
        drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
    }
}

@Composable
private fun Buttons(
    s: StudyUi,
    vertical: Boolean,
    onFlip: () -> Unit,
    onCheck: () -> Unit,
    onRate: (Rating) -> Unit,
    onFocus: (Rating?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!s.flipped) {
        // Choice cards are answered through the answer buttons
        if (s.type == CardType.CHOICE) return
        // Input and match cards are checked, the rest is flipped
        val checks = s.type == CardType.INPUT || s.type == CardType.MATCH
        Button(
            onClick = if (checks) onCheck else onFlip,
            modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).focusFrame(),
        ) {
            Text(
                stringResource(if (checks) R.string.study_check else R.string.study_show_answer),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        return
    }
    val colors = LocalRatingColors.current
    val items = if (s.threeLevel) {
        listOf(
            Triple(SelfGrade.WRONG.rating, R.string.grade_wrong, colors.again),
            Triple(SelfGrade.PARTIAL.rating, R.string.grade_partial, colors.hard),
            Triple(SelfGrade.CORRECT.rating, R.string.grade_correct, colors.good),
        )
    } else {
        listOf(
            Triple(Rating.AGAIN, R.string.rating_again, colors.again),
            Triple(Rating.HARD, R.string.rating_hard, colors.hard),
            Triple(Rating.GOOD, R.string.rating_good, colors.good),
            Triple(Rating.EASY, R.string.rating_easy, colors.easy),
        )
    }
    @Composable
    fun RatingButton(r: Rating, label: Int, color: Color, modifier: Modifier) {
        Button(
            onClick = { onRate(r) },
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp),
            // The suggested grade gets an outline; Ⓐ confirms it
            border = if (s.suggestion?.rating == r) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            modifier = modifier.heightIn(min = 56.dp).onFocusChanged { if (it.isFocused) onFocus(r) }.focusFrame(RoundedCornerShape(10.dp)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(label), fontWeight = FontWeight.SemiBold, maxLines = 1)
                s.intervals.getOrNull(r.value - 1)?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
    }
    if (vertical) {
        items.forEach { (r, l, c) -> RatingButton(r, l, c, modifier.fillMaxWidth()) }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (r, l, c) -> RatingButton(r, l, c, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun EndView(title: String, subtitle: String?, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🎉", style = MaterialTheme.typography.displayLarge)
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        Button(onClick = back, modifier = Modifier.padding(top = 20.dp).focusFrame()) { Text(stringResource(R.string.action_back)) }
    }
}
