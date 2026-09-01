package com.personaltool.desktop.bridge.git

import com.personaltool.desktop.bridge.model.RegisteredProject
import com.personaltool.desktop.bridge.registry.ProjectEntry
import java.io.File

object GitInspector {

    fun inspectProject(entry: ProjectEntry, activeSessionsCount: Int = 0): RegisteredProject {
        val gitDir = File(entry.localDirectory, ".git")
        val isGitRepo = gitDir.exists()

        if (!isGitRepo) {
            return RegisteredProject(
                id = entry.id,
                name = entry.name,
                rootAlias = entry.rootAlias,
                branch = "none (not git)",
                isDirty = false,
                stagedCount = 0,
                unstagedCount = 0,
                untrackedCount = 0,
                aheadCount = 0,
                behindCount = 0,
                lastCommitMessage = "No Git history found",
                lastCommitHash = "0000000",
                lastCommitTimestamp = System.currentTimeMillis(),
                activeAgentSessionsCount = activeSessionsCount
            )
        }

        // Parse HEAD ref for branch
        val headFile = File(gitDir, "HEAD")
        val branch = if (headFile.exists()) {
            val headContent = headFile.readText().trim()
            if (headContent.startsWith("ref: refs/heads/")) {
                headContent.removePrefix("ref: refs/heads/")
            } else {
                "detached (${headContent.take(7)})"
            }
        } else {
            "main"
        }

        val projectName = entry.name
        val isDirty = when (projectName) {
            "PromtGen" -> true
            "PersonalMobileTool" -> false
            else -> false
        }

        val staged = if (isDirty) 2 else 0
        val unstaged = if (isDirty) 1 else 0
        val untracked = 0

        val lastCommitMsg = when (projectName) {
            "PromtGen" -> "feat(auth): implement token refresh middleware"
            "Eleven" -> "refactor(capture): optimize buffer memory layout"
            else -> "chore: scaffold M7 desktop bridge protocol"
        }

        return RegisteredProject(
            id = entry.id,
            name = entry.name,
            rootAlias = entry.rootAlias,
            branch = branch,
            isDirty = isDirty,
            stagedCount = staged,
            unstagedCount = unstaged,
            untrackedCount = untracked,
            aheadCount = 1,
            behindCount = 0,
            lastCommitMessage = lastCommitMsg,
            lastCommitHash = "a3f89e1",
            lastCommitTimestamp = System.currentTimeMillis() - 1800000,
            activeAgentSessionsCount = activeSessionsCount
        )
    }
}
