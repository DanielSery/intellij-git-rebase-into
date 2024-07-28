package com.github.iml885203.intellijgitmergeinto

import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitCommander(private var project: Project) {
    private val repository: GitRepository?
    private fun repoRoot() = repository!!.root

    init {
        repository = findMainRepository(project)
    }

    fun repositoryNotFound() = repository == null
    fun branchNotFound() = repository?.currentBranch?.name == null
    fun getCurrentBranch() = repository?.currentBranch?.name ?: ""

    private fun findMainRepository(project: Project): GitRepository? {
        val repositories = GitRepositoryManager.getInstance(project).repositories
        return repositories.firstOrNull { it.root.path == project.basePath }
    }


    fun execute(command: GitCommand, params: Array<String>) {
        val handler = GitLineHandler(project, repoRoot(), command)
        for (param in params) {
            handler.addParameters(param)
        }
        Git.getInstance().runCommand(handler).throwOnError()
    }

    fun checkUncommittedChanges() {
        val handler = GitLineHandler(project, repoRoot(), GitCommand.STATUS)
        handler.addParameters("--porcelain")
        val result  = Git.getInstance().runCommand(handler)
        result.throwOnError()
        if (result.output.isNotEmpty()) {
            throw IllegalStateException("There are uncommitted changes in the current branch.")
        }
    }
}
