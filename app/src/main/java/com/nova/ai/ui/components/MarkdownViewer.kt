package com.nova.ai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.ui.theme.*

@Composable
fun MarkdownViewer(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val blocks = parseMarkdownBlocks(text)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(
                        code = block.code,
                        language = block.language,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("code", block.code)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                is MarkdownBlock.Header -> {
                    Text(
                        text = block.text,
                        fontSize = when (block.level) {
                            1 -> 20.sp
                            2 -> 18.sp
                            else -> 16.sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = NovaPrimary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownBlock.BulletPoint -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            fontWeight = FontWeight.Bold,
                            color = NovaPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = parseInlineMarkdown(block.text),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
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
                text = language.ifBlank { "code" },
                color = TextSecondary,
                fontSize = 12.sp,
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

        // Code Content
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

private sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletPoint(val text: String) : MarkdownBlock()
}

private fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = raw.lines()
    var inCodeBlock = false
    var codeLanguage = ""
    val codeBuilder = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                // End code block
                blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeBuilder.toString().trimEnd()))
                codeBuilder.clear()
                inCodeBlock = false
            } else {
                // Start code block
                inCodeBlock = true
                codeLanguage = trimmed.removePrefix("```").trim()
            }
            continue
        }

        if (inCodeBlock) {
            codeBuilder.append(line).append("\n")
            continue
        }

        if (trimmed.startsWith("# ")) {
            blocks.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ").trim()))
        } else if (trimmed.startsWith("## ")) {
            blocks.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ").trim()))
        } else if (trimmed.startsWith("### ")) {
            blocks.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ").trim()))
        } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            blocks.add(MarkdownBlock.BulletPoint(trimmed.substring(2).trim()))
        } else if (trimmed.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(line))
        }
    }

    if (inCodeBlock && codeBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeBuilder.toString()))
    }

    return blocks
}

private fun parseInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            } else if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF1E293B),
                            color = NovaPrimary
                        )
                    ) {
                        append(" " + text.substring(i + 1, end) + " ")
                    }
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}
