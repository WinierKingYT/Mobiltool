package com.personaltool.desktop.bridge.agent

object PtyTerminalNormalizer {

    private val ANSI_ESCAPE_REGEX = Regex("\u001B\\[[;?0-9]*[a-zA-Z]|\u001B\\([a-zA-Z]|\r")

    fun stripAnsiControlSequences(rawOutput: String): String {
        return rawOutput.replace(ANSI_ESCAPE_REGEX, "").trim()
    }

    fun isInteractivePrompt(cleanedOutput: String): Boolean {
        val lower = cleanedOutput.lowercase()
        return lower.contains("do you want to") ||
                lower.contains("(y/n)") ||
                lower.contains("[y/n]") ||
                lower.contains("approve this action") ||
                lower.contains("press enter to continue")
    }
}
