package com.github.iml885203.intellijgitmergeinto.actions

import com.github.iml885203.intellijgitmergeinto.*
import com.github.iml885203.intellijgitmergeinto.settings.AppSettings
import com.github.iml885203.intellijgitmergeinto.settings.ProjectSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import git4idea.commands.GitCommand

class MergeIntoAction : AnAction() {
    private lateinit var project: Project
    private lateinit var gitCommander: GitCommander
    private lateinit var notifier: MyNotifier
    private lateinit var state: AppSettings.State
    private lateinit var projectState: ProjectSettings.State
    private lateinit var indicatorProcessor: IndicatorProcessor
    private var isRunning: Boolean = false

    override fun actionPerformed(e: AnActionEvent ) {
        project = e.project ?: return

        if (isRunning) {
            return
        }

        gitCommander = GitCommander(project)
        notifier = MyNotifier(project)
        if (gitCommander.repositoryNotFound()) {
            notifier.notifyFailed("No Git repository found")
            return
        }

        if (gitCommander.branchNotFound()) {
            notifier.notifyFailed("No current branch found")
            return
        }

        val progressManager = ProgressManager.getInstance()
        state = AppSettings.instance.state
        projectState = ProjectSettings.getInstance(project).state
        val targetBranch = if (projectState.targetBranchOverwrite) projectState.targetBranch else state.targetBranch
        if (state.runInBackground) {
            progressManager.run(object :
                Task.Backgroundable(project,"Merging into $targetBranch",true) {
                override fun run(indicator: ProgressIndicator) {
                    mergeBranch(gitCommander.getCurrentBranch(), targetBranch)
                }
            })
        } else {
            progressManager.runProcessWithProgressSynchronously({
                mergeBranch(gitCommander.getCurrentBranch(), targetBranch)
            }, "Merging Into $targetBranch", true, project)
        }
    }

    private fun mergeBranch(currentBranch: String, targetBranch: String) {
        this.isRunning = true
        val indicator = ProgressManager.getInstance().progressIndicator
        val pushAfterMerge = if (projectState.targetBranchOverwrite) projectState.pushAfterMerge else state.pushAfterMerge
        indicatorProcessor = IndicatorProcessor(indicator, step = if (pushAfterMerge) 6 else 5)

        try {
            gitCommander.checkUncommittedChanges()

            // Checkout develop branch
            indicatorProcessor.nextStep("Checking out develop branch...")
            gitCommander.execute(GitCommand.CHECKOUT, arrayOf(targetBranch))

            // Fetch latest changes
            indicatorProcessor.nextStep("Fetching latest changes...")
            gitCommander.execute(GitCommand.FETCH, arrayOf("origin", targetBranch))

            // Reset hard
            indicatorProcessor.nextStep("Resetting hard...")
            gitCommander.execute(GitCommand.RESET, arrayOf("--hard", "origin/$targetBranch"))

            // Merge current branch into develop
            indicatorProcessor.nextStep("Merging current branch into ${targetBranch}...")
            gitCommander.execute(GitCommand.MERGE, arrayOf(currentBranch))

            // Push changes
            if (pushAfterMerge) {
                indicatorProcessor.nextStep("Pushing changes...")
                gitCommander.execute(GitCommand.PUSH, arrayOf("origin", targetBranch))
            }

            // Checkout back to the original branch
            indicatorProcessor.nextStep("Checking out original branch...")
            gitCommander.execute(GitCommand.CHECKOUT, arrayOf(currentBranch))

            notifier.notifySuccess("Merge into $targetBranch completed.")
        } catch(ex: MergeIntoException) {
            when (ex.errorCode) {
                EnumErrorCode.UncommittedChanges -> notifier.notifyFailed(ex.message)
            }
        } catch (ex: Exception) {
            thisLogger().warn(ex)
            handleMergeFailure(ex, indicator, currentBranch)
        }
        this.isRunning = false
    }

    private fun handleMergeFailure(ex: Exception, indicator: ProgressIndicator, currentBranch: String) {
        try {
            indicator.checkCanceled()
        } catch (@Suppress("IncorrectCancellationExceptionHandling") _: Exception) {
            notifier.notifyFailed("Merge operation was cancelled.")
            return
        }

        val isUnknownError = ex.message == null
        if (isUnknownError) {
            notifier.notifyFailed("An error occurred during the merge operation. Exception: ${ex.javaClass.simpleName}")
            return
        }

        val isConflict = ex.message!!.contains("CONFLICT", ignoreCase = true)
        if (isConflict) {
            try {
                if (state.abortMergeWhenConflicts) {
                    gitCommander.execute(GitCommand.MERGE, arrayOf("--abort"))
                    gitCommander.execute(GitCommand.CHECKOUT, arrayOf(currentBranch))
                    notifier.notifyFailed("Merge conflict occurred. Please resolve the conflict and try again.")
                } else {
                    gitCommander.refreshVcsChanges()
                    val abortAndBackCallback = Runnable {
                        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Aborting and checking out branch") {
                            override fun run(indicator: ProgressIndicator) {
                                gitCommander.execute(GitCommand.MERGE, arrayOf("--abort"))
                                gitCommander.execute(GitCommand.CHECKOUT, arrayOf(currentBranch))
                                gitCommander.refreshVcsChanges()
                                VfsUtil.markDirtyAndRefresh(true, true, true, project.guessProjectDir())
                            }
                        })
                    }
                    notifier.notifyConflict("Merge conflict occurred.", abortAndBackCallback)
                }
            } catch (abortEx: Exception) {
                thisLogger().error("Failed to abort merge: ${abortEx.message}")
            }
            return
        }

        try {
            gitCommander.execute(GitCommand.CHECKOUT, arrayOf(currentBranch))
            notifier.notifyFailed(ex.message!!)
        } catch (checkoutEx: Exception) {
            thisLogger().error("Failed to checkout back to original branch: ${checkoutEx.message}")
        }
    }
}
