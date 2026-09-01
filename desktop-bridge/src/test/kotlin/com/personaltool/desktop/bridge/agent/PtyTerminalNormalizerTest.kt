package com.personaltool.desktop.bridge.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PtyTerminalNormalizerTest {

    @Test
    fun stripAnsiControlSequences_removesColorAndCursorEscapeCodes() {
        val raw = "\u001B[31mError: Build failed\u001B[0m\r\n\u001B[32mSuccess\u001B[0m"
        val cleaned = PtyTerminalNormalizer.stripAnsiControlSequences(raw)
        assertThat(cleaned).isEqualTo("Error: Build failed\nSuccess")
    }

    @Test
    fun isInteractivePrompt_detectsConfirmationQuestions() {
        val prompt1 = "Do you want to proceed with deployment? (y/n)"
        val prompt2 = "Please press enter to continue..."
        val nonPrompt = "Building package :app:assembleDebug completed."

        assertThat(PtyTerminalNormalizer.isInteractivePrompt(prompt1)).isTrue()
        assertThat(PtyTerminalNormalizer.isInteractivePrompt(prompt2)).isTrue()
        assertThat(PtyTerminalNormalizer.isInteractivePrompt(nonPrompt)).isFalse()
    }
}
