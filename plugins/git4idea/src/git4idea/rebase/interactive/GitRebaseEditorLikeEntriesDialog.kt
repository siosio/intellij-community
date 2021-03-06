// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.interactive.GitRebaseEntriesTableModel.Companion.ACTION_COLUMN
import git4idea.rebase.interactive.GitRebaseEntriesTableModel.Companion.HASH_COLUMN
import javax.swing.JComponent
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableModel

internal class GitRebaseEditorLikeEntriesDialog(
  project: Project,
  entries: List<GitRebaseEntry>
) : DialogWrapper(project, false) {
  private val table = GitRebaseEntriesTable(entries)

  init {
    title = "Interactive Rebase Entries"
    init()
  }

  override fun createCenterPanel(): JComponent = BorderLayoutPanel()
    .addToCenter(JBScrollPane(table))
    .withPreferredSize(600, 400)

  override fun createActions() = arrayOf(okAction)

  override fun getDimensionServiceKey() = "Git.Interactive.Rebase.Editor.Like.Entries.Dialog"
}

private class GitRebaseEntriesTable(entries: List<GitRebaseEntry>) : JBTable(GitRebaseEntriesTableModel(entries)) {
  init {
    adjustColumnWidth(ACTION_COLUMN)
    adjustColumnWidth(HASH_COLUMN)
  }

  private fun adjustColumnWidth(columnIndex: Int) {
    val contentWidth = getExpandedColumnWidth(columnIndex) + UIUtil.DEFAULT_HGAP
    val column = columnModel.getColumn(columnIndex)
    column.maxWidth = contentWidth
    column.preferredWidth = contentWidth
  }
}

private class GitRebaseEntriesTableModel(private val entries: List<GitRebaseEntry>) : AbstractTableModel(), TableModel {
  companion object {
    const val ACTION_COLUMN = 0
    const val HASH_COLUMN = 1
    const val SUBJECT_COLUMN = 2
  }

  override fun getColumnName(column: Int) = when (column) {
    ACTION_COLUMN -> "Action"
    HASH_COLUMN -> "Hash"
    SUBJECT_COLUMN -> "Subject"
    else -> throw IllegalArgumentException("Unsupported column index: $column")
  }

  override fun getRowCount() = entries.size

  override fun getColumnCount() = 3

  override fun getValueAt(rowIndex: Int, columnIndex: Int) = when (columnIndex) {
    ACTION_COLUMN -> entries[rowIndex].action.name
    HASH_COLUMN -> entries[rowIndex].commit
    SUBJECT_COLUMN -> entries[rowIndex].subject
    else -> throw IllegalArgumentException("Unsupported column index: $columnIndex")
  }
}