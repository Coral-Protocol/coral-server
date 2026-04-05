package org.coralprotocol.coralserver.util

class IndentedStringBuilder(private val indent: String = "  ") {
    private val builder = StringBuilder()
    private var currentIndent = ""

    fun appendLine(text: String = "") {
        if (text.isEmpty()) {
            builder.appendLine()
        } else {
            builder.appendLine("$currentIndent$text")
        }
    }

    fun withIndent(block: IndentedStringBuilder.() -> Unit) {
        currentIndent += indent
        block()
        currentIndent = currentIndent.dropLast(indent.length)
    }

    fun withIndentedXml(
        elementName: String,
        vararg attributes: Pair<String, String>,
        block: IndentedStringBuilder.() -> Unit
    ) {
        val attrString = if (attributes.isEmpty()) {
            ""
        } else {
            " " + attributes.joinToString(" ") { (key, value) ->
                "$key=\"$value\""
            }
        }

        appendLine("<$elementName$attrString>")
        withIndent(block)
        appendLine("</$elementName>")
    }

    override fun toString() = builder.toString()
}

fun buildIndentedString(indent: String = "  ", block: IndentedStringBuilder.() -> Unit): String {
    return IndentedStringBuilder(indent).apply(block).toString()
}