// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.actions.ActivateToolWindowAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

public class EditorEmptyTextPainter {
  public void paintEmptyText(@NotNull final JComponent splitters, @NotNull Graphics g) {
    UISettings.setupAntialiasing(g);
    UIUtil.TextPainter painter = createTextPainter();
    advertiseActions(splitters, painter);
    painter.draw(g, (width, height) -> {
      Dimension s = splitters.getSize();
      int w = (s.width - width) / 2;
      int h = (int)(s.height * heightRatio());
      return Couple.of(w, h);
    });
  }

  protected double heightRatio() {
    return 0.375; // fix vertical position @ golden ratio
  }

  protected void advertiseActions(@NotNull JComponent splitters, @NotNull UIUtil.TextPainter painter) {
    appendSearchEverywhere(painter);
    appendToolWindow(painter, "Project View", ToolWindowId.PROJECT_VIEW, splitters);
    appendAction(painter, "Go to File", getActionShortcutText("GotoFile"));
    appendAction(painter, "Recent Files", getActionShortcutText(IdeActions.ACTION_RECENT_FILES));
    appendAction(painter, "Navigation Bar", getActionShortcutText("ShowNavBar"));
    appendDnd(painter);
  }

  protected void appendDnd(@NotNull UIUtil.TextPainter painter) {
    appendLine(painter, "Drop files here to open");
  }

  protected void appendSearchEverywhere(@NotNull UIUtil.TextPainter painter) {
    Shortcut[] shortcuts = getActiveKeymapShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE).getShortcuts();
    appendAction(painter, "Search Everywhere", shortcuts.length == 0 ?
                                               "Double " + (SystemInfo.isMac ? MacKeymapUtil.SHIFT : "Shift") :
                                               KeymapUtil.getShortcutsText(shortcuts));
  }

  protected void appendToolWindow(@NotNull UIUtil.TextPainter painter,
                                  @NotNull String action,
                                  @NotNull String toolWindowId,
                                  @NotNull JComponent splitters) {
    if (!isToolwindowVisible(splitters, toolWindowId)) {
      String activateActionId = ActivateToolWindowAction.getActionIdForToolWindow(toolWindowId);
      appendAction(painter, action, getActionShortcutText(activateActionId));
    }
  }

  protected void appendAction(@NotNull UIUtil.TextPainter painter, @NotNull String action, @Nullable String shortcut) {
    if (StringUtil.isEmpty(shortcut)) return;
    appendLine(painter, action + " " + "<shortcut>" + shortcut + "</shortcut>");
  }

  protected void appendLine(@NotNull UIUtil.TextPainter painter, String line) {
    painter.appendLine(line);
  }

  @NotNull
  protected String getActionShortcutText(@NonNls @NotNull String actionId) {
    return KeymapUtil.getFirstKeyboardShortcutText(actionId);
  }

  protected static boolean isToolwindowVisible(@NotNull JComponent splitters, @NotNull String toolwindowId) {
    ProjectFrameHelper frame = ProjectFrameHelper.getFrameHelper(SwingUtilities.getWindowAncestor(splitters));
    if (frame != null) {
      Project project = frame.getProject();
      if (project != null) {
        if (!project.isInitialized()) return true;
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolwindowId);
        return toolWindow != null && toolWindow.isVisible();
      }
    }
    return false;
  }

  @NotNull
  public static UIUtil.TextPainter createTextPainter() {
    return new UIUtil.TextPainter()
      .withLineSpacing(1.8f)
      .withColor(JBColor.namedColor("Editor.foreground", new JBColor(Gray._80, Gray._160)))
      .withFont(JBUI.Fonts.label(16f));
  }
}
