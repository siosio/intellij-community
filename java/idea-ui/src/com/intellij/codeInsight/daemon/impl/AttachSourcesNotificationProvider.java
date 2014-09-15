/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ProjectTopics;
import com.intellij.codeEditor.JavaEditorFileSwapper;
import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.PathUIUtils;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.GuiUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class AttachSourcesNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final ExtensionPointName<AttachSourcesProvider> EXTENSION_POINT_NAME =
    new ExtensionPointName<AttachSourcesProvider>("com.intellij.attachSourcesProvider");

  private static final Key<EditorNotificationPanel> KEY = Key.create("add sources to class");

  private final Project myProject;

  public AttachSourcesNotificationProvider(Project project, final EditorNotifications notifications) {
    myProject = project;
    myProject.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        notifications.updateAllNotifications();
      }
    });
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull final VirtualFile file, @NotNull FileEditor fileEditor) {
    if (file.getFileType() != JavaClassFileType.INSTANCE) return null;

    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(ProjectBundle.message("class.file.decompiled.text"));

    final VirtualFile sourceFile = JavaEditorFileSwapper.findSourceFile(myProject, file);
    if (sourceFile == null) {
      final List<LibraryOrderEntry> libraries = findLibraryEntriesForFile(file);
      if (libraries != null) {
        List<AttachSourcesProvider.AttachSourcesAction> actions = new ArrayList<AttachSourcesProvider.AttachSourcesAction>();

        PsiFile clsFile = PsiManager.getInstance(myProject).findFile(file);
        boolean hasNonLightAction = false;
        for (AttachSourcesProvider each : Extensions.getExtensions(EXTENSION_POINT_NAME)) {
          for (AttachSourcesProvider.AttachSourcesAction action : each.getActions(libraries, clsFile)) {
            if (hasNonLightAction) {
              if (action instanceof AttachSourcesProvider.LightAttachSourcesAction) {
                continue; // Don't add LightAttachSourcesAction if non light action exists.
              }
            }
            else {
              if (!(action instanceof AttachSourcesProvider.LightAttachSourcesAction)) {
                actions.clear(); // All previous actions is LightAttachSourcesAction and should be removed.
                hasNonLightAction = true;
              }
            }
            actions.add(action);
          }
        }

        Collections.sort(actions, new Comparator<AttachSourcesProvider.AttachSourcesAction>() {
          @Override
          public int compare(AttachSourcesProvider.AttachSourcesAction o1, AttachSourcesProvider.AttachSourcesAction o2) {
            return o1.getName().compareToIgnoreCase(o2.getName());
          }
        });

        AttachSourcesProvider.AttachSourcesAction defaultAction;
        if (findSourceFileInSameJar(file) != null) {
          defaultAction = new AttachJarAsSourcesAction(file);
        }
        else {
          defaultAction = new ChooseAndAttachSourcesAction(myProject, panel);
        }
        actions.add(defaultAction);

        for (final AttachSourcesProvider.AttachSourcesAction action : actions) {
          panel.createActionLabel(GuiUtils.getTextWithoutMnemonicEscaping(action.getName()), new Runnable() {
            @Override
            public void run() {
              List<LibraryOrderEntry> entries = findLibraryEntriesForFile(file);
              if (!Comparing.equal(libraries, entries)) {
                Messages.showErrorDialog(myProject, "Can't find library for " + file.getName(), "Error");
                return;
              }

              panel.setText(action.getBusyText());

              action.perform(entries);
            }
          });
        }
      }
    }
    else {
      panel.createActionLabel(ProjectBundle.message("class.file.open.source.action"), new Runnable() {
        @Override
        public void run() {
          OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, sourceFile);
          FileEditorManager.getInstance(myProject).openTextEditor(descriptor, true);
        }
      });
    }

    return panel;
  }

  @Nullable
  private List<LibraryOrderEntry> findLibraryEntriesForFile(VirtualFile file) {
    List<LibraryOrderEntry> entries = null;

    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(myProject);
    for (OrderEntry entry : index.getOrderEntriesForFile(file)) {
      if (entry instanceof LibraryOrderEntry) {
        if (entries == null) entries = ContainerUtil.newSmartList();
        entries.add((LibraryOrderEntry)entry);
      }
    }

    return entries;
  }

  @Nullable
  private static VirtualFile findSourceFileInSameJar(VirtualFile classFile) {
    String name = classFile.getName();
    int i = name.indexOf('$');
    if (i != -1) name = name.substring(0, i);
    i = name.indexOf('.');
    if (i != -1) name = name.substring(0, i);
    return classFile.getParent().findChild(name + JavaFileType.DOT_DEFAULT_EXTENSION);
  }

  private static class AttachJarAsSourcesAction implements AttachSourcesProvider.AttachSourcesAction {
    private final VirtualFile myClassFile;

    public AttachJarAsSourcesAction(VirtualFile classFile) {
      myClassFile = classFile;
    }

    @Override
    public String getName() {
      return ProjectBundle.message("module.libraries.attach.sources.button");
    }

    @Override
    public String getBusyText() {
      return ProjectBundle.message("library.attach.sources.action.busy.text");
    }

    @Override
    public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
      final List<Library.ModifiableModel> modelsToCommit = new ArrayList<Library.ModifiableModel>();
      for (LibraryOrderEntry orderEntry : orderEntriesContainingFile) {
        final Library library = orderEntry.getLibrary();
        if (library == null) continue;
        final VirtualFile root = findRoot(library);
        if (root == null) continue;
        final Library.ModifiableModel model = library.getModifiableModel();
        model.addRoot(root, OrderRootType.SOURCES);
        modelsToCommit.add(model);
      }
      if (modelsToCommit.isEmpty()) return new ActionCallback.Rejected();
      new WriteAction() {
        @Override
        protected void run(@NotNull final Result result) {
          for (Library.ModifiableModel model : modelsToCommit) {
            model.commit();
          }
        }
      }.execute();

      return new ActionCallback.Done();
    }

    @Nullable
    private VirtualFile findRoot(Library library) {
      for (VirtualFile classesRoot : library.getFiles(OrderRootType.CLASSES)) {
        if (VfsUtilCore.isAncestor(classesRoot, myClassFile, true)) {
          return classesRoot;
        }
      }
      return null;
    }
  }

  private static class ChooseAndAttachSourcesAction implements AttachSourcesProvider.AttachSourcesAction {
    private final Project myProject;
    private final JComponent myParentComponent;

    public ChooseAndAttachSourcesAction(Project project, JComponent parentComponent) {
      myProject = project;
      myParentComponent = parentComponent;
    }

    @Override
    public String getName() {
      return ProjectBundle.message("module.libraries.choose.sources.button");
    }

    @Override
    public String getBusyText() {
      return ProjectBundle.message("library.attach.sources.action.busy.text");
    }

    @Override
    public ActionCallback perform(final List<LibraryOrderEntry> libraries) {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleJavaPathDescriptor();
      descriptor.setTitle(ProjectBundle.message("library.attach.sources.action"));
      descriptor.setDescription(ProjectBundle.message("library.attach.sources.description"));
      Library firstLibrary = libraries.get(0).getLibrary();
      VirtualFile[] roots = firstLibrary != null ? firstLibrary.getFiles(OrderRootType.CLASSES) : VirtualFile.EMPTY_ARRAY;
      VirtualFile[] candidates = FileChooser.chooseFiles(descriptor, myProject, roots.length == 0 ? null : PathUtil.getLocalFile(roots[0]));
      final VirtualFile[] files = PathUIUtils.scanAndSelectDetectedJavaSourceRoots(myParentComponent, candidates);
      if (files.length == 0) {
        return new ActionCallback.Rejected();
      }

      final Map<Library, LibraryOrderEntry> librariesToAppendSourcesTo = new HashMap<Library, LibraryOrderEntry>();
      for (LibraryOrderEntry library : libraries) {
        librariesToAppendSourcesTo.put(library.getLibrary(), library);
      }
      if (librariesToAppendSourcesTo.size() == 1) {
        appendSources(firstLibrary, files);
      }
      else {
        librariesToAppendSourcesTo.put(null, null);
        String title = ProjectBundle.message("library.choose.one.to.attach");
        List<LibraryOrderEntry> entries = ContainerUtil.newArrayList(librariesToAppendSourcesTo.values());
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<LibraryOrderEntry>(title, entries) {
          @Override
          public ListSeparator getSeparatorAbove(LibraryOrderEntry value) {
            return value == null ? new ListSeparator() : null;
          }

          @NotNull
          @Override
          public String getTextFor(LibraryOrderEntry value) {
            return value == null ? "All" : value.getPresentableName() + " (" + value.getOwnerModule().getName() + ")";
          }

          @Override
          public PopupStep onChosen(LibraryOrderEntry libraryOrderEntry, boolean finalChoice) {
            if (libraryOrderEntry != null) {
              appendSources(libraryOrderEntry.getLibrary(), files);
            }
            else {
              for (Library libOrderEntry : librariesToAppendSourcesTo.keySet()) {
                if (libOrderEntry != null) {
                  appendSources(libOrderEntry, files);
                }
              }
            }
            return FINAL_CHOICE;
          }
        }).showCenteredInCurrentWindow(myProject);
      }

      return new ActionCallback.Done();
    }

    private static void appendSources(final Library library, final VirtualFile[] files) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          Library.ModifiableModel model = library.getModifiableModel();
          for (VirtualFile virtualFile : files) {
            model.addRoot(virtualFile, OrderRootType.SOURCES);
          }
          model.commit();
        }
      });
    }
  }
}
