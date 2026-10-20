package com.nova.ai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.ui.theme.*

/**
 * Sanitizes raw text containing LaTeX before Markdown parsing:
 * 1. Replaces unsupported arrows (\longrightarrow -> \to, \uparrow -> ↑, etc.)
 * 2. Normalizes line breaks around $$ blocks so closing $$ is always followed by \n\n,
 *    preventing headers (####) and dividers (---) from sticking to the block.
 */
fun sanitizeLaTeX(rawText: String): String {
    if (rawText.isBlank()) return ""

    var s = rawText

    // 1. Replace unsupported arrows with standard/compatible equivalents
    s = s.replace(Regex("""\\longrightarrow\b"""), "\\to")
    s = s.replace(Regex("""\\longleftarrow\b"""), "\\leftarrow")
    s = s.replace(Regex("""\\longleftrightarrow\b"""), "\\leftrightarrow")
    s = s.replace(Regex("""\\Longrightarrow\b"""), "\\implies")
    s = s.replace(Regex("""\\Longleftarrow\b"""), "\\Leftarrow")
    s = s.replace(Regex("""\\Longleftrightarrow\b"""), "\\iff")

    s = s.replace(Regex("""\\uparrow\b"""), "↑")
    s = s.replace(Regex("""\\downarrow\b"""), "↓")
    s = s.replace(Regex("""\\updownarrow\b"""), "↕")
    s = s.replace(Regex("""\\Uparrow\b"""), "⇑")
    s = s.replace(Regex("""\\Downarrow\b"""), "⇓")
    s = s.replace(Regex("""\\Updownarrow\b"""), "⇕")

    // Normalize escaped bracket delimiters
    s = s.replace("\\\\[", "$$").replace("\\\\]", "$$")
    s = s.replace("\\[", "$$").replace("\\]", "$$")
    s = s.replace("\\\\(", "$").replace("\\\\)", "$")
    s = s.replace("\\(", "$").replace("\\)", "$")

    // Normalize escaped LaTeX commands: \\frac -> \frac
    s = s.replace(Regex("""\\\\([a-zA-Z]+)""")) { "\\" + it.groupValues[1] }

    // 2. Normalize newlines around $$ blocks:
    // Ensure after closing $$ there are strictly two newlines (\n\n) so following headers (####) and dividers (---) are not glued to the block
    s = s.replace(Regex("""\$\$\s*([\s\S]*?)\s*\$\$""")) { matchResult ->
        val innerFormula = matchResult.groupValues[1].trim()
        "\n\n$$$innerFormula$$\n\n"
    }

    s = s.replace(Regex("""\n{3,}"""), "\n\n")

    return s.trim()
}

fun preprocessRawMarkdown(raw: String): String = sanitizeLaTeX(raw)

@Composable
fun MarkdownViewer(
    text: String,
    modifier: Modifier = Modifier,
    isWatchMode: Boolean = false
) {
    val context = LocalContext.current
    val preprocessed = sanitizeLaTeX(text)
    val blocks = parseMarkdownBlocks(preprocessed)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (isWatchMode) 6.dp else 8.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(
                        code = block.code,
                        language = block.language,
                        onCopy = {
                            copyToClipboard(context, "code", block.code, "Код скопирован")
                        }
                    )
                }
                is MarkdownBlock.MathBlock -> {
                    MathDisplayBlock(
                        formula = block.formula,
                        isWatchMode = isWatchMode
                    )
                }
                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        color = DarkBorder,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = if (isWatchMode) 4.dp else 8.dp)
                    )
                }
                is MarkdownBlock.Header -> {
                    Text(
                        text = parseInlineMarkdown(block.text, isWatchMode),
                        fontSize = when (block.level) {
                            1 -> if (isWatchMode) 16.sp else 20.sp
                            2 -> if (isWatchMode) 14.5.sp else 18.sp
                            3 -> if (isWatchMode) 13.5.sp else 16.sp
                            4 -> if (isWatchMode) 12.5.sp else 14.5.sp
                            else -> if (isWatchMode) 12.sp else 13.5.sp
                        },
                        fontWeight = if (block.level <= 3) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (block.level <= 3) NovaPrimary else Color(0xFF7DD3FC),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownBlock.BulletPoint -> {
                    Row(
                        modifier = Modifier.padding(start = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = if (block.number != null) "${block.number}. " else "• ",
                            fontWeight = FontWeight.Bold,
                            color = NovaPrimary,
                            fontSize = if (isWatchMode) 13.5.sp else 15.sp,
                            lineHeight = if (isWatchMode) 18.sp else 22.sp
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, isWatchMode),
                            color = TextPrimary,
                            fontSize = if (isWatchMode) 13.5.sp else 15.sp,
                            lineHeight = if (isWatchMode) 18.sp else 22.sp
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text, isWatchMode),
                        color = TextPrimary,
                        fontSize = if (isWatchMode) 13.5.sp else 15.sp,
                        lineHeight = if (isWatchMode) 18.sp else 22.sp
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String, toastMsg: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
}

@Composable
private fun CodeBlockCard(
    code: String,
    language: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBackground)
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF131927))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifEmpty { "code" },
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Code Content (monospaced, no math parsing inside code)
        Text(
            text = code,
            color = Color(0xFFE2E8F0),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        )
    }
}

/**
 * Clean mathematical display equation block without code block background or "formula" headers.
 * Has extra vertical padding (min 8dp) so numerators and high fractions (\frac{6}{3} = 2) are never cut off.
 */
@Composable
private fun MathDisplayBlock(
    formula: String,
    isWatchMode: Boolean
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isWatchMode) 6.dp else 10.dp)
            .horizontalScroll(scrollState),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.padding(top = 6.dp, bottom = 6.dp, start = 8.dp, end = 8.dp)) {
            RenderMathBlockContent(formula = formula, isWatchMode = isWatchMode)
        }
    }
}

@Composable
private fun RenderMathBlockContent(formula: String, isWatchMode: Boolean) {
    val clean = cleanMathFormula(formula)

    // 1. System of equations: \begin{cases} ... \end{cases}
    if (clean.contains("\\begin{cases}")) {
        val casesBody = clean.substringAfter("\\begin{cases}").substringBefore("\\end{cases}").trim()
        val lines = casesBody.split(Regex("""\\\\|\r?\n""")).map { it.trim() }.filter { it.isNotEmpty() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            Text(
                text = "{",
                fontSize = if (isWatchMode) 22.sp else (22 + lines.size * 9).sp,
                fontWeight = FontWeight.ExtraLight,
                color = NovaPrimary,
                modifier = Modifier.padding(end = 6.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (line in lines) {
                    val eqPart = line.replace("&", "  ").trim()
                    RenderMathFormulaSegments(formula = eqPart, isWatchMode = isWatchMode)
                }
            }
        }
        return
    }

    // 2. Multi-line equations separated by \\
    if (clean.contains("\\\\")) {
        val lines = clean.split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            for (line in lines) {
                RenderMathFormulaSegments(formula = line, isWatchMode = isWatchMode)
            }
        }
        return
    }

    // 3. Single equation with segments (fractions, roots, text, tall brackets)
    Box(modifier = Modifier.padding(vertical = 4.dp)) {
        RenderMathFormulaSegments(formula = clean, isWatchMode = isWatchMode)
    }
}

@Composable
private fun RenderMathFormulaSegments(formula: String, isWatchMode: Boolean) {
    val segments = parseFormulaSegments(formula)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        for (seg in segments) {
            RenderSingleSegment(seg, isWatchMode)
        }
    }
}

@Composable
private fun RenderSingleSegment(seg: MathSegment, isWatchMode: Boolean) {
    when (seg) {
        is MathSegment.TextSeg -> {
            Text(
                text = formatMathAnnotated(seg.text, isWatchMode),
                fontSize = if (isWatchMode) 13.5.sp else 15.5.sp,
                color = TextPrimary
            )
        }
        is MathSegment.FractionSeg -> {
            // Mixed fraction: integer part and fraction are on the exact same horizontal line in an unbreakable Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                if (!seg.integerPart.isNullOrBlank()) {
                    Text(
                        text = formatMathAnnotated(seg.integerPart, isWatchMode),
                        fontSize = if (isWatchMode) 13.5.sp else 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        modifier = Modifier.padding(end = 3.dp)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatMathAnnotated(seg.numerator, isWatchMode),
                        fontSize = if (isWatchMode) 12.sp else 14.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = if (isWatchMode) 16.sp else 18.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp) // padding top 6dp eliminates clipping of numerators
                    )
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .widthIn(min = 18.dp)
                            .height(1.5.dp)
                            .background(NovaPrimary)
                    )
                    Text(
                        text = formatMathAnnotated(seg.denominator, isWatchMode),
                        fontSize = if (isWatchMode) 12.sp else 14.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = if (isWatchMode) 16.sp else 18.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )
                }
            }
        }
        is MathSegment.SqrtSeg -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            ) {
                if (!seg.degree.isNullOrBlank()) {
                    Text(
                        text = seg.degree,
                        fontSize = if (isWatchMode) 8.sp else 9.5.sp,
                        color = NovaPrimary,
                        modifier = Modifier.offset(y = (-5).dp)
                    )
                }
                Text(
                    text = "√",
                    fontSize = if (isWatchMode) 17.sp else 20.sp,
                    fontWeight = FontWeight.Light,
                    color = NovaPrimary,
                    modifier = Modifier.padding(end = 1.dp)
                )
                Box(
                    modifier = Modifier
                        .drawBehind {
                            drawLine(
                                color = NovaPrimary,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                        .padding(top = 4.dp, start = 2.dp, end = 2.dp)
                ) {
                    Text(
                        text = formatMathAnnotated(seg.radicand, isWatchMode),
                        fontSize = if (isWatchMode) 13.sp else 14.5.sp,
                        color = TextPrimary
                    )
                }
            }
        }
        is MathSegment.BracketSeg -> {
            // Tall brackets dynamically stretched to the height of enclosed content
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TallBracket(
                    bracketChar = seg.openChar,
                    isWatchMode = isWatchMode,
                    modifier = Modifier.fillMaxHeight()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    for (inner in seg.innerSegments) {
                        RenderSingleSegment(inner, isWatchMode)
                    }
                }
                TallBracket(
                    bracketChar = seg.closeChar,
                    isWatchMode = isWatchMode,
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun TallBracket(
    bracketChar: Char,
    isWatchMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (bracketChar == ' ') return

    val bracketColor = NovaPrimary
    val strokeWidth = if (isWatchMode) 1.5.dp else 1.8.dp
    val bracketWidth = if (isWatchMode) 7.dp else 9.dp

    Box(
        modifier = modifier
            .width(bracketWidth)
            .padding(vertical = 1.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            when (bracketChar) {
                '(' -> {
                    val path = Path().apply {
                        moveTo(w * 0.85f, 0f)
                        cubicTo(
                            w * 0.1f, h * 0.25f,
                            w * 0.1f, h * 0.75f,
                            w * 0.85f, h
                        )
                    }
                    drawPath(
                        path = path,
                        color = bracketColor,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
                ')' -> {
                    val path = Path().apply {
                        moveTo(w * 0.15f, 0f)
                        cubicTo(
                            w * 0.9f, h * 0.25f,
                            w * 0.9f, h * 0.75f,
                            w * 0.15f, h
                        )
                    }
                    drawPath(
                        path = path,
                        color = bracketColor,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
                '[' -> {
                    val path = Path().apply {
                        moveTo(w * 0.85f, 0f)
                        lineTo(w * 0.2f, 0f)
                        lineTo(w * 0.2f, h)
                        lineTo(w * 0.85f, h)
                    }
                    drawPath(
                        path = path,
                        color = bracketColor,
                        style = Stroke(width = sw, cap = StrokeCap.Square)
                    )
                }
                ']' -> {
                    val path = Path().apply {
                        moveTo(w * 0.15f, 0f)
                        lineTo(w * 0.8f, 0f)
                        lineTo(w * 0.8f, h)
                        lineTo(w * 0.15f, h)
                    }
                    drawPath(
                        path = path,
                        color = bracketColor,
                        style = Stroke(width = sw, cap = StrokeCap.Square)
                    )
                }
                '{' -> {
                    val path = Path().apply {
                        moveTo(w * 0.85f, 0f)
                        cubicTo(w * 0.3f, 0f, w * 0.3f, h * 0.45f, w * 0.1f, h * 0.5f)
                        cubicTo(w * 0.3f, h * 0.55f, w * 0.3f, h, w * 0.85f, h)
                    }
                    drawPath(
                        path = path,
                        color = bracketColor,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
                '}' -> {
                    val path = Path().apply {
                        moveTo(w * 0.15f, 0f)
                        cubicTo(w * 0.7f, 0f, w * 0.7f, h * 0.45f, w * 0.9f, h * 0.5f)
                        cubicTo(w * 0.7f, h * 0.55f, w * 0.7f, h, w * 0.15f, h)
                    }
                    drawPath(
                        path = path,
                        color = bracketColor,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
                '|' -> {
                    drawLine(
                        color = bracketColor,
                        start = Offset(w / 2f, 0f),
                        end = Offset(w / 2f, h),
                        strokeWidth = sw
                    )
                }
                else -> {
                    val path = Path().apply {
                        moveTo(w * 0.85f, 0f)
                        cubicTo(
                            w * 0.1f, h * 0.25f,
                            w * 0.1f, h * 0.75f,
                            w * 0.85f, h
                        )
                    }
                    drawPath(
                        path = path,
                        color = bracketColor,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

private sealed class MathSegment {
    data class TextSeg(val text: String) : MathSegment()
    data class FractionSeg(val integerPart: String? = null, val numerator: String, val denominator: String) : MathSegment()
    data class SqrtSeg(val degree: String?, val radicand: String) : MathSegment()
    data class BracketSeg(val openChar: Char, val closeChar: Char, val innerSegments: List<MathSegment>) : MathSegment()
}

private fun parseFormulaSegments(raw: String): List<MathSegment> {
    val segments = mutableListOf<MathSegment>()
    val s = cleanMathFormula(raw)
    var pos = 0
    val textBuf = StringBuilder()

    while (pos < s.length) {
        // 1. \left( ... \right) or \left[ ... \right] or \left\{ ... \right\} or \left| ... \right|
        if (s.startsWith("\\left", pos)) {
            var afterLeft = pos + 5
            var openChar = '('
            if (afterLeft < s.length) {
                if (s[afterLeft] == '\\' && afterLeft + 1 < s.length && (s[afterLeft + 1] == '{' || s[afterLeft + 1] == '}')) {
                    openChar = '{'
                    afterLeft += 2
                } else if (s[afterLeft] in listOf('(', '[', '{', '|')) {
                    openChar = s[afterLeft]
                    afterLeft++
                } else if (s[afterLeft] == '.') {
                    openChar = ' '
                    afterLeft++
                }
            }

            var depth = 1
            var j = afterLeft
            var rightStart = -1
            var closeChar = ')'
            var afterRight = -1

            while (j < s.length && depth > 0) {
                if (s.startsWith("\\left", j)) {
                    depth++
                    j += 5
                } else if (s.startsWith("\\right", j)) {
                    depth--
                    if (depth == 0) {
                        rightStart = j
                        if (j + 6 < s.length && s[j + 6] == '\\' && j + 7 < s.length && (s[j + 7] == '{' || s[j + 7] == '}')) {
                            closeChar = '}'
                            afterRight = j + 8
                        } else if (j + 6 < s.length && s[j + 6] in listOf(')', ']', '}', '|')) {
                            closeChar = s[j + 6]
                            afterRight = j + 7
                        } else if (j + 6 < s.length && s[j + 6] == '.') {
                            closeChar = ' '
                            afterRight = j + 7
                        } else {
                            closeChar = ')'
                            afterRight = j + 6
                        }
                        break
                    }
                    j += 6
                } else {
                    j++
                }
            }

            if (rightStart != -1 && afterRight != -1) {
                if (textBuf.isNotEmpty()) {
                    segments.add(MathSegment.TextSeg(textBuf.toString()))
                    textBuf.clear()
                }
                val innerText = s.substring(afterLeft, rightStart)
                val innerSegments = parseFormulaSegments(innerText)
                segments.add(MathSegment.BracketSeg(openChar, closeChar, innerSegments))
                pos = afterRight
                continue
            }
        }

        // 2. Regular brackets around fractions: ( ... \frac ... ) or [ ... \frac ... ]
        if ((s[pos] == '(' || s[pos] == '[') && pos + 1 < s.length) {
            val openCh = s[pos]
            val closeCh = if (openCh == '(') ')' else ']'
            val closeIdx = findMatchingChar(s, pos, openCh, closeCh)
            if (closeIdx != -1) {
                val inner = s.substring(pos + 1, closeIdx)
                if (inner.contains("\\frac") || inner.contains("\\dfrac") || inner.contains("\\tfrac")) {
                    if (textBuf.isNotEmpty()) {
                        segments.add(MathSegment.TextSeg(textBuf.toString()))
                        textBuf.clear()
                    }
                    val innerSegments = parseFormulaSegments(inner)
                    segments.add(MathSegment.BracketSeg(openCh, closeCh, innerSegments))
                    pos = closeIdx + 1
                    continue
                }
            }
        }

        // 3. Fraction: \frac{num}{den}, \dfrac{num}{den}, \tfrac{num}{den}
        if (s.startsWith("\\frac", pos) || s.startsWith("\\dfrac", pos) || s.startsWith("\\tfrac", pos)) {
            val isFrac = s.startsWith("\\frac", pos)
            val prefixLen = if (isFrac) 5 else 6
            val openNum = s.indexOf('{', pos + prefixLen)
            if (openNum != -1) {
                val closeNum = findMatchingBrace(s, openNum)
                if (closeNum != -1) {
                    val openDen = s.indexOf('{', closeNum + 1)
                    if (openDen != -1) {
                        val closeDen = findMatchingBrace(s, openDen)
                        if (closeDen != -1) {
                            // Check if textBuf ends with an integer part for mixed fraction (e.g. 2\frac{3}{5} or 5 \frac{1}{12})
                            val currentText = textBuf.toString()
                            val intMatch = Regex("""(?:^|(?<=[^0-9a-zA-Z]))(\d+)\s*$""").find(currentText)
                            var integerPart: String? = null

                            if (intMatch != null) {
                                val matchStart = intMatch.range.first
                                val preText = currentText.substring(0, matchStart)
                                if (preText.isNotEmpty()) {
                                    segments.add(MathSegment.TextSeg(preText))
                                }
                                integerPart = intMatch.groupValues[1]
                                textBuf.clear()
                            } else if (textBuf.isNotEmpty()) {
                                segments.add(MathSegment.TextSeg(textBuf.toString()))
                                textBuf.clear()
                            }

                            val num = s.substring(openNum + 1, closeNum)
                            val den = s.substring(openDen + 1, closeDen)
                            segments.add(MathSegment.FractionSeg(integerPart, num, den))
                            pos = closeDen + 1
                            continue
                        }
                    }
                }
            }
        }

        // 4. Square roots \sqrt[n]{x}
        else if (s.startsWith("\\sqrt", pos)) {
            var afterSqrt = pos + 5
            var degree: String? = null
            if (afterSqrt < s.length && s[afterSqrt] == '[') {
                val closeBracket = s.indexOf(']', afterSqrt + 1)
                if (closeBracket != -1) {
                    degree = s.substring(afterSqrt + 1, closeBracket)
                    afterSqrt = closeBracket + 1
                }
            }
            val openRad = s.indexOf('{', afterSqrt)
            if (openRad != -1) {
                val closeRad = findMatchingBrace(s, openRad)
                if (closeRad != -1) {
                    if (textBuf.isNotEmpty()) {
                        segments.add(MathSegment.TextSeg(textBuf.toString()))
                        textBuf.clear()
                    }
                    val rad = s.substring(openRad + 1, closeRad)
                    segments.add(MathSegment.SqrtSeg(degree, rad))
                    pos = closeRad + 1
                    continue
                }
            }
        }

        textBuf.append(s[pos])
        pos++
    }

    if (textBuf.isNotEmpty()) {
        segments.add(MathSegment.TextSeg(textBuf.toString()))
    }

    return segments
}

private fun findMatchingChar(text: String, openPos: Int, openCh: Char, closeCh: Char): Int {
    var depth = 1
    var i = openPos + 1
    while (i < text.length && depth > 0) {
        if (text[i] == openCh) depth++
        else if (text[i] == closeCh) depth--
        i++
    }
    return if (depth == 0) i - 1 else -1
}

private fun findMatchingBrace(text: String, openPos: Int): Int {
    var depth = 1
    var i = openPos + 1
    while (i < text.length && depth > 0) {
        if (text[i] == '{') depth++
        else if (text[i] == '}') depth--
        i++
    }
    return if (depth == 0) i - 1 else -1
}

private sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletPoint(val text: String, val number: Int? = null) : MarkdownBlock()
    data class MathBlock(val formula: String) : MarkdownBlock()
    data class HorizontalRule(val dummy: Unit = Unit) : MarkdownBlock()
}

private fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = raw.lines()
    var inCodeBlock = false
    var codeLanguage = ""
    val codeBuilder = StringBuilder()

    var inMathBlock = false
    val mathBuilder = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()

        // 1. Code Block toggle (```)
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeBuilder.toString().trimEnd()))
                codeBuilder.clear()
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLanguage = trimmed.removePrefix("```").trim()
            }
            continue
        }

        if (inCodeBlock) {
            codeBuilder.append(line).append("\n")
            continue
        }

        // 2. Math Block $$...$$ or \[...\]
        if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 2) {
            blocks.add(MarkdownBlock.MathBlock(trimmed.removePrefix("$$").removeSuffix("$$").trim()))
            continue
        }

        if (trimmed.startsWith("\\[") && trimmed.endsWith("\\]") && trimmed.length > 2) {
            blocks.add(MarkdownBlock.MathBlock(trimmed.removePrefix("\\[").removeSuffix("\\]").trim()))
            continue
        }

        if (trimmed.startsWith("$$") || trimmed.startsWith("\\[") || trimmed.startsWith("\\begin{cases}")) {
            inMathBlock = true
            mathBuilder.append(trimmed).append("\n")
            if (
                (trimmed.endsWith("$$") && trimmed != "$$") ||
                (trimmed.endsWith("\\]") && trimmed != "\\[") ||
                trimmed.endsWith("\\end{cases}")
            ) {
                blocks.add(MarkdownBlock.MathBlock(mathBuilder.toString().trim()))
                mathBuilder.clear()
                inMathBlock = false
            }
            continue
        }

        if (inMathBlock) {
            mathBuilder.append(line).append("\n")
            if (trimmed.endsWith("$$") || trimmed.endsWith("\\]") || trimmed.endsWith("\\end{cases}")) {
                blocks.add(MarkdownBlock.MathBlock(mathBuilder.toString().trim()))
                mathBuilder.clear()
                inMathBlock = false
            }
            continue
        }

        // 3. Freestanding formula lines like \frac{...}{...} on their own line
        if (isFreestandingFormulaLine(trimmed)) {
            blocks.add(MarkdownBlock.MathBlock(trimmed))
            continue
        }

        // 4. Horizontal rule (---, ***, ___)
        if (Regex("""^(\-{3,}|\*{3,}|_{3,})$""").matches(trimmed)) {
            blocks.add(MarkdownBlock.HorizontalRule())
            continue
        }

        // 5. Headers 1 to 6 (# to ######)
        val headerMatch = Regex("""^(#{1,6})\s+(.*)""").find(trimmed)
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val title = headerMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.Header(level, title))
            continue
        }

        // 6. Lists
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
            val itemText = trimmed.replace(Regex("""^[-*•]\s+"""), "")
            blocks.add(MarkdownBlock.BulletPoint(itemText, null))
        } else if (Regex("""^\d+\.\s""").containsMatchIn(trimmed)) {
            val numMatch = Regex("""^(\d+)\.\s+(.*)""").find(trimmed)
            if (numMatch != null) {
                val num = numMatch.groupValues[1].toIntOrNull()
                val text = numMatch.groupValues[2]
                blocks.add(MarkdownBlock.BulletPoint(text, num))
            } else {
                blocks.add(MarkdownBlock.Paragraph(line))
            }
        } else if (trimmed.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(line))
        }
    }

    if (inCodeBlock && codeBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeBuilder.toString()))
    }
    if (inMathBlock && mathBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.MathBlock(mathBuilder.toString().trim()))
    }

    return blocks
}

private fun isFreestandingFormulaLine(line: String): Boolean {
    val t = line.trim()
    if (t.length < 3) return false
    val mathKeywords = listOf(
        "\\frac{", "\\dfrac{", "\\tfrac{", "\\sqrt{", "\\begin{cases}",
        "\\sum_{", "\\int_{", "\\prod_{", "\\lim_{", "\\left(", "\\left[", "\\left\\{"
    )
    if (mathKeywords.any { t.startsWith(it) }) {
        val letterWords = t.split(Regex("""\s+""")).filter { w -> w.all { it.isLetter() } && w.length > 2 }
        return letterWords.size <= 1
    }
    // Mixed fractions on their own line e.g. "2\frac{3}{5}" or "x = 2\frac{3}{5}"
    if (Regex("""^(?:[a-zA-Z]\s*=\s*)?(\d+\s*)?\\(?:frac|dfrac|tfrac)\{""").containsMatchIn(t)) {
        return true
    }
    return false
}

/**
 * Checks if a candidate string is genuinely math or a protected currency expression ($10, $5.99, $10 and $20)
 */
fun isMathExpression(raw: String): Boolean {
    val clean = raw.trim()
    if (clean.isEmpty()) return false

    // 1. Currency: pure numbers like "10", "5.99", "1,000", "1000"
    if (clean.matches(Regex("""^\d+([.,]\d+)?$"""))) {
        return false
    }

    // Currency with common suffixes like "10 тыс", "5 млн", "100 млрд", "100k", "50m", "10 usd", "10 eur", "10 руб"
    if (clean.matches(Regex("""^\d+([.,]\d+)?\s*(тыс|тысяч|млн|миллион|млрд|руб|рублей|usd|eur|долл|долларов|евро|[kmb])\.?$""", RegexOption.IGNORE_CASE))) {
        return false
    }

    // Conversational text between dollars that has conversational connector words and NO math operators or LaTeX
    val lower = clean.lowercase()
    val nonMathWords = listOf("or", "and", "to", "for", "each", "per", "cost", "или", "до", "и", "по", "за", "рублей", "долларов")
    val hasNonMathWord = nonMathWords.any { Regex("""(^|\s)$it(\s|$)""", RegexOption.IGNORE_CASE).containsMatchIn(lower) }
    val hasMathSymbols = Regex("""[=+\-*\/<>±×÷√≈≠≤≥≡∼∝→⇒↑↓↕^_{}\\]""").containsMatchIn(clean)
    if (hasNonMathWord && !hasMathSymbols) {
        return false
    }

    // 2. Math indicators:
    if (clean.contains('\\')) return true
    if (Regex("""[=+\-*\/<>±×÷√≈≠≤≥≡∼∝→⇒↑↓↕]""").containsMatchIn(clean)) return true
    if (clean.contains('^') || clean.contains('_') || clean.contains('{')) return true

    // Single math variable or function with question mark: x, y, z, v, F, a, t, f(x), v = ?
    if (clean.matches(Regex("""^[a-zA-Zα-ωΑ-Ω](\([a-zA-Z0-9,\s]+\))?(\s*[?])?$"""))) return true

    // Math expression like "v = 10 м/с" or "A \to B"
    if (Regex("""^[a-zA-Zα-ωΑ-Ω]\s*=""").containsMatchIn(clean)) return true

    return false
}

fun cleanMathFormula(raw: String): String {
    var s = raw.trim()
    if (s.startsWith("$$") && s.endsWith("$$") && s.length >= 4) s = s.substring(2, s.length - 2).trim()
    else if (s.startsWith("\\[") && s.endsWith("\\]") && s.length >= 4) s = s.substring(2, s.length - 2).trim()
    else if (s.startsWith("\\(") && s.endsWith("\\)") && s.length >= 4) s = s.substring(2, s.length - 2).trim()
    else if (s.startsWith("\\$") && s.endsWith("\\$") && s.length >= 4) s = s.substring(2, s.length - 2).trim()
    else if (s.startsWith("$") && s.endsWith("$") && s.length >= 2) s = s.substring(1, s.length - 1).trim()

    // Strip trailing backslashes
    while (s.endsWith("\\")) {
        s = s.dropLast(1).trim()
    }
    // Strip stray leading backslash if followed by dollar or non-letter
    if (s.startsWith("\\$")) s = s.drop(2).trim()
    else if (s.startsWith("\\") && !Regex("""^\\[a-zA-Z]""").containsMatchIn(s)) s = s.drop(1).trim()

    // Normalize double backslashes before known LaTeX commands or macros
    s = s.replace(Regex("""\\\\([a-zA-Z]+)""")) { "\\" + it.groupValues[1] }

    return s
}

private fun parseInlineMarkdown(text: String, isWatchMode: Boolean = false): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            // 1. Bold text **text** (recursively parses math inside bold)
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    val boldContent = text.substring(i + 2, end)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                        append(parseInlineMarkdown(boldContent, isWatchMode))
                    }
                    i = end + 2
                    continue
                }
            }
            // 2. Inline code `code` (never parse math inside code)
            else if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF1E293B),
                            color = NovaPrimary,
                            fontSize = if (isWatchMode) 12.sp else 13.5.sp
                        )
                    ) {
                        append(" " + text.substring(i + 1, end) + " ")
                    }
                    i = end + 1
                    continue
                }
            }
            // 3. Inline math: $$...$$ inside paragraph
            else if (i + 1 < text.length && text[i] == '$' && text[i + 1] == '$') {
                val end = text.indexOf("$$", i + 2)
                if (end != -1) {
                    val mathContent = cleanMathFormula(text.substring(i + 2, end))
                    append(formatMathAnnotated(mathContent, isWatchMode))
                    i = end + 2
                    continue
                }
            }
            // 4. Bracket math \[...\] or \\[...\\] inside text
            else if (text.startsWith("\\[", i) || text.startsWith("\\\\[", i)) {
                val prefixLen = if (text.startsWith("\\\\[", i)) 3 else 2
                val closePattern = if (prefixLen == 3) "\\\\]" else "\\]"
                val end = text.indexOf(closePattern, i + prefixLen)
                if (end != -1) {
                    val mathContent = cleanMathFormula(text.substring(i + prefixLen, end))
                    append(formatMathAnnotated(mathContent, isWatchMode))
                    i = end + closePattern.length
                    continue
                }
            }
            // 5. Paren math \(...\) or \\(...\\) inside text
            else if (text.startsWith("\\(", i) || text.startsWith("\\\\(", i)) {
                val prefixLen = if (text.startsWith("\\\\(", i)) 3 else 2
                val closePattern = if (prefixLen == 3) "\\\\)" else "\\)"
                val end = text.indexOf(closePattern, i + prefixLen)
                if (end != -1) {
                    val mathContent = cleanMathFormula(text.substring(i + prefixLen, end))
                    append(formatMathAnnotated(mathContent, isWatchMode))
                    i = end + closePattern.length
                    continue
                }
            }
            // 6. Dollar math: single $ or escaped \$ or \\$
            val isEscapedDollar = text.startsWith("\\$", i) || text.startsWith("\\\\$", i)
            val isPlainDollar = text[i] == '$'
            if (isEscapedDollar || isPlainDollar) {
                val openLen = if (text.startsWith("\\\\$", i)) 3 else if (text.startsWith("\\$", i)) 2 else 1
                var end = -1
                var searchIdx = i + openLen
                while (searchIdx < text.length) {
                    if (text[searchIdx] == '$') {
                        end = searchIdx
                        break
                    }
                    searchIdx++
                }

                if (end != -1) {
                    val rawCandidate = text.substring(i + openLen, end)
                    val candidate = cleanMathFormula(rawCandidate)
                    if (isMathExpression(candidate)) {
                        append(formatMathAnnotated(candidate, isWatchMode))
                        i = end + 1
                        continue
                    }
                } else {
                    // Unclosed $ at end of clause/sentence (e.g. "Найти: $v = ?")
                    val rest = text.substring(i + openLen)
                    val candidate = cleanMathFormula(rest)
                    if (isMathExpression(candidate) && (candidate.contains('=') || candidate.contains('\\'))) {
                        append(formatMathAnnotated(candidate, isWatchMode))
                        i = text.length
                        continue
                    }
                }
            }
            // 7. Raw inline LaTeX commands like \frac{4}{18}, \sqrt{...}, \to, \alpha outside delimiters
            else if (text[i] == '\\' && i + 1 < text.length && text[i + 1].isLetter()) {
                val cmdMatch = Regex("""^\\(frac|dfrac|tfrac|sqrt|approx|neq|le|ge|pm|times|cdot|to|alpha|beta|gamma|delta|pi|theta|lambda|sigma|omega|Delta|Sigma|Omega|text)\b""").find(text.substring(i))
                if (cmdMatch != null) {
                    val expr = extractFullLatexExpression(text, i)
                    if (expr.isNotEmpty()) {
                        append(formatMathAnnotated(cleanMathFormula(expr), isWatchMode))
                        i += expr.length
                        continue
                    }
                }
            }

            append(text[i])
            i++
        }
    }
}

private fun extractFullLatexExpression(text: String, start: Int): String {
    var pos = start
    var openBraces = 0
    while (pos < text.length) {
        val c = text[pos]
        if (c == '{') openBraces++
        else if (c == '}') openBraces--
        else if (openBraces == 0 && (c.isWhitespace() || c == ',' || c == '.' || c == ';' || c == ')' || c == ']' || c == '!' || c == '?')) {
            break
        }
        pos++
    }
    return text.substring(start, pos)
}

/**
 * Converts LaTeX formulas to formatted AnnotatedString with superscripts, subscripts, Greek letters and math symbols
 * Implements soft error handling (throwOnError: false equivalent) so unknown macros or invalid inputs never crash
 */
fun formatMathAnnotated(rawMath: String, isWatchMode: Boolean = false): AnnotatedString {
    try {
        var s = cleanMathFormula(rawMath)

        // 1. Replace \text{...} and \mathrm{...} preserving inner text
        s = s.replace(Regex("""\\(?:text|mathrm|mathbf|mathit|operatorname)\{([^{}]+)\}""")) { it.groupValues[1] }

        // 2. Replace Greek letters and math symbols with word-boundary check for alpha LaTeX commands
        for ((sym, rep) in MATH_SYMBOLS) {
            if (sym.startsWith("\\") && sym.length > 1 && sym.substring(1).all { it.isLetter() }) {
                s = s.replace(Regex(Regex.escape(sym) + """(?![a-zA-Z])"""), rep)
            } else {
                s = s.replace(sym, rep)
            }
        }

        // 3. Replace common math functions
        s = s.replace(Regex("""\\(sin|cos|tan|cot|sec|csc|ln|log|exp|lim|max|min|det)\b""")) { it.groupValues[1] }

        // 4. Remove \left and \right delimiters (do not leave \left or \right as text)
        s = s.replace(Regex("""\\left([(\[{|.])""")) { if (it.groupValues[1] == ".") "" else it.groupValues[1] }
        s = s.replace(Regex("""\\right([)\]}|.])""")) { if (it.groupValues[1] == ".") "" else it.groupValues[1] }
        s = s.replace(Regex("""\\(?:left|right)\b"""), "")
        s = s.replace("\\{", "{").replace("\\}", "}")
        s = s.replace("\\.", "").replace("\\,", " ").replace("\\;", " ").replace("\\quad", "  ").replace("\\qquad", "    ")

        // 5. Replace \frac{a}{b} in inline context with clean fraction format, supporting mixed fractions (e.g. 2\frac{3}{5})
        while (s.contains("\\frac") || s.contains("\\dfrac") || s.contains("\\tfrac")) {
            val match = Regex("""(?:(\d+)\s*)?\\(?:frac|dfrac|tfrac)\{([^{}]+)\}\{([^{}]+)\}""").find(s) ?: break
            val intPart = match.groupValues[1]
            val num = match.groupValues[2]
            val den = match.groupValues[3]
            val fracStr = if (num.length <= 2 && den.length <= 2 && num.all { it.isDigit() } && den.all { it.isDigit() }) {
                (if (intPart.isNotEmpty()) intPart + "\u00A0" else "") + toSuperscript(num) + "⁄" + toSubscript(den)
            } else {
                (if (intPart.isNotEmpty()) intPart + "\u00A0" else "") + "($num / $den)"
            }
            s = s.substring(0, match.range.first) + fracStr + s.substring(match.range.last + 1)
        }

        // 6. Replace \sqrt[n]{x}
        while (s.contains("\\sqrt")) {
            val degMatch = Regex("""\\sqrt\[([^\]]+)\]\{([^{}]+)\}""").find(s)
            if (degMatch != null) {
                val deg = toSuperscript(degMatch.groupValues[1])
                val rad = degMatch.groupValues[2]
                s = s.substring(0, degMatch.range.first) + "${deg}√($rad)" + s.substring(degMatch.range.last + 1)
                continue
            }
            val simpleMatch = Regex("""\\sqrt\{([^{}]+)\}""").find(s)
            if (simpleMatch != null) {
                val rad = simpleMatch.groupValues[1]
                s = s.substring(0, simpleMatch.range.first) + "√($rad)" + s.substring(simpleMatch.range.last + 1)
                continue
            }
            break
        }

        // 7. Parse superscripts (^) and subscripts (_) into Compose AnnotatedString with distinct math styling
        return buildAnnotatedString {
            var idx = 0
            val mathColor = Color(0xFF38BDF8)

            while (idx < s.length) {
                val c = s[idx]
                if (c == '^') {
                    idx++
                    val (superText, nextIdx) = extractSuperOrSubArg(s, idx)
                    withStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = if (isWatchMode) 9.sp else 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = mathColor
                        )
                    ) {
                        append(superText)
                    }
                    idx = nextIdx
                    continue
                } else if (c == '_') {
                    idx++
                    val (subText, nextIdx) = extractSuperOrSubArg(s, idx)
                    withStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Subscript,
                            fontSize = if (isWatchMode) 9.sp else 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = mathColor
                        )
                    ) {
                        append(subText)
                    }
                    idx = nextIdx
                    continue
                }

                // Math variables, operators and equations are given clean math styling
                withStyle(SpanStyle(color = mathColor, fontWeight = FontWeight.Medium)) {
                    append(c)
                }
                idx++
            }
        }
    } catch (e: Exception) {
        // Soft error fallback
        return buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFF38BDF8))) {
                append(rawMath)
            }
        }
    }
}

private fun extractSuperOrSubArg(s: String, startIdx: Int): Pair<String, Int> {
    if (startIdx >= s.length) return Pair("", startIdx)
    if (s[startIdx] == '{') {
        val close = findMatchingBrace(s, startIdx)
        if (close != -1) {
            return Pair(s.substring(startIdx + 1, close), close + 1)
        }
    }
    return Pair(s[startIdx].toString(), startIdx + 1)
}

private fun toSuperscript(s: String): String {
    return s.map { c ->
        when (c) {
            '0' -> '⁰'
            '1' -> '¹'
            '2' -> '²'
            '3' -> '³'
            '4' -> '⁴'
            '5' -> '⁵'
            '6' -> '⁶'
            '7' -> '⁷'
            '8' -> '⁸'
            '9' -> '⁹'
            '+' -> '⁺'
            '-' -> '⁻'
            '=' -> '⁼'
            '(' -> '⁽'
            ')' -> '⁾'
            'n' -> 'ⁿ'
            'i' -> 'ⁱ'
            'x' -> 'ˣ'
            'y' -> 'ʸ'
            else -> c
        }
    }.joinToString("")
}

private fun toSubscript(s: String): String {
    return s.map { c ->
        when (c) {
            '0' -> '₀'
            '1' -> '₁'
            '2' -> '₂'
            '3' -> '₃'
            '4' -> '₄'
            '5' -> '₅'
            '6' -> '₆'
            '7' -> '₇'
            '8' -> '₈'
            '9' -> '₉'
            '+' -> '₊'
            '-' -> '₋'
            '=' -> '₌'
            '(' -> '₍'
            ')' -> '₎'
            'a' -> 'ₐ'
            'e' -> 'ₑ'
            'o' -> 'ₒ'
            'x' -> 'ₓ'
            'i' -> 'ᵢ'
            'j' -> 'ⱼ'
            'k' -> 'ₖ'
            'l' -> 'ₗ'
            'm' -> 'ₘ'
            'n' -> 'ₙ'
            'p' -> 'ₚ'
            's' -> 'ₛ'
            't' -> 'ₜ'
            else -> c
        }
    }.joinToString("")
}

private val MATH_SYMBOLS = listOf(
    "\\approx" to "≈",
    "\\neq" to "≠",
    "\\ne" to "≠",
    "\\leq" to "≤",
    "\\le" to "≤",
    "\\geq" to "≥",
    "\\ge" to "≥",
    "\\equiv" to "≡",
    "\\sim" to "∼",
    "\\pm" to "±",
    "\\mp" to "∓",
    "\\times" to "×",
    "\\cdot" to "·",
    "\\div" to "÷",
    "\\ast" to "∗",
    "\\star" to "⋆",
    "\\infty" to "∞",
    "\\sum" to "∑",
    "\\prod" to "∏",
    "\\int" to "∫",
    "\\iint" to "∬",
    "\\iiint" to "∭",
    "\\oint" to "∮",
    "\\partial" to "∂",
    "\\nabla" to "∇",
    "\\in" to "∈",
    "\\notin" to "∉",
    "\\subset" to "⊂",
    "\\subseteq" to "⊆",
    "\\supset" to "⊃",
    "\\supseteq" to "⊇",
    "\\cap" to "∩",
    "\\cup" to "∪",
    "\\setminus" to "∖",
    "\\emptyset" to "∅",
    "\\varnothing" to "∅",
    "\\forall" to "∀",
    "\\exists" to "∃",
    "\\nexists" to "∄",
    "\\neg" to "¬",
    "\\to" to "→",
    "\\rightarrow" to "→",
    "\\leftarrow" to "←",
    "\\gets" to "←",
    "\\Rightarrow" to "⇒",
    "\\implies" to "⇒",
    "\\Leftarrow" to "⇐",
    "\\Leftrightarrow" to "⇔",
    "\\iff" to "⇔",
    "\\longrightarrow" to "→",
    "\\longleftarrow" to "←",
    "\\longleftrightarrow" to "↔",
    "\\Longrightarrow" to "⇒",
    "\\Longleftarrow" to "⇐",
    "\\Longleftrightarrow" to "⇔",
    "\\uparrow" to "↑",
    "\\downarrow" to "↓",
    "\\updownarrow" to "↕",
    "\\Uparrow" to "⇑",
    "\\Downarrow" to "⇓",
    "\\Updownarrow" to "⇕",
    "\\nearrow" to "↗",
    "\\searrow" to "↘",
    "\\swarrow" to "↙",
    "\\nwarrow" to "↖",
    "\\mapsto" to "↦",
    "\\longmapsto" to "⟼",
    "\\degree" to "°",
    "\\circ" to "°",
    "\\dots" to "…",
    "\\ldots" to "…",
    "\\cdots" to "…",
    "\\perp" to "⊥",
    "\\parallel" to "∥",
    "\\angle" to "∠",
    "\\triangle" to "△",
    "\\alpha" to "α",
    "\\beta" to "β",
    "\\gamma" to "γ",
    "\\delta" to "δ",
    "\\epsilon" to "ε",
    "\\varepsilon" to "ε",
    "\\zeta" to "ζ",
    "\\eta" to "η",
    "\\theta" to "θ",
    "\\vartheta" to "θ",
    "\\iota" to "ι",
    "\\kappa" to "κ",
    "\\lambda" to "λ",
    "\\mu" to "μ",
    "\\nu" to "ν",
    "\\xi" to "ξ",
    "\\pi" to "π",
    "\\varpi" to "π",
    "\\rho" to "ρ",
    "\\varrho" to "ρ",
    "\\sigma" to "σ",
    "\\varsigma" to "σ",
    "\\tau" to "τ",
    "\\upsilon" to "υ",
    "\\phi" to "φ",
    "\\varphi" to "φ",
    "\\chi" to "χ",
    "\\psi" to "ψ",
    "\\omega" to "ω",
    "\\Gamma" to "Γ",
    "\\Delta" to "Δ",
    "\\Theta" to "Θ",
    "\\Lambda" to "Λ",
    "\\Xi" to "Ξ",
    "\\Pi" to "Π",
    "\\Sigma" to "Σ",
    "\\Upsilon" to "Υ",
    "\\Phi" to "Φ",
    "\\Psi" to "Ψ",
    "\\Omega" to "Ω",
    "\\quad" to "  ",
    "\\qquad" to "    ",
    "\\," to " ",
    "\\:" to " ",
    "\\;" to " "
)
