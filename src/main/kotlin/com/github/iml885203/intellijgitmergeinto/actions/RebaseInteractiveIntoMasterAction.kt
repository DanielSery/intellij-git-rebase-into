package com.github.iml885203.intellijgitmergeinto.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import git4idea.GitVcs
import git4idea.branch.GitRebaseParams
import git4idea.rebase.GitRebaseUtils
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitRefDialog

class RebaseInteractiveIntoMasterAction : AnAction() {
    private lateinit var project: Project
    private var isRunning: Boolean = false

    override fun actionPerformed(e: AnActionEvent) {
        project = e.project ?: return

        if (isRunning) {
            return
        }

        val repositories = GitRepositoryManager.getInstance(project).repositories
        val repository = repositories.firstOrNull() ?: return
        val currentBranch = repository.currentBranch?.name ?: return

        val dialog = GitRefDialog(project, repositories,
            "Interactive rebase",
            "Rebase onto")

        if (!dialog.showAndGet()) {
            return
        }

        val gitVersion = GitVcs.getInstance(project).version
        val progressManager = ProgressManager.getInstance()

        progressManager.run(object :
            Task.Backgroundable(project,"Rebasing",true) {
            override fun run(indicator: ProgressIndicator) {
                val selectedParams = GitRebaseParams(
                    version = gitVersion,
                    branch = currentBranch,
                    newBase = dialog.reference,
                    upstream = dialog.reference,
                    interactive = true,
                    preserveMerges = false)
                GitRebaseUtils.rebase(project, repositories, selectedParams, indicator);
            }
        })
    }
}