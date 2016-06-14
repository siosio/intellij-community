/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.history.integration;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.StoredContent;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalHistoryEventDispatcher extends VirtualFileAdapter implements VirtualFileManagerListener, CommandListener {
  private static final Key<Boolean> WAS_VERSIONED_KEY =
    Key.create(LocalHistoryEventDispatcher.class.getSimpleName() + ".WAS_VERSIONED_KEY");

  private final LocalHistoryFacade myVcs;
  private final IdeaGateway myGateway;

  public LocalHistoryEventDispatcher(LocalHistoryFacade vcs, IdeaGateway gw) {
    myVcs = vcs;
    myGateway = gw;
  }

  @Override
  public void beforeRefreshStart(boolean asynchronous) {
    beginChangeSet();
  }

  @Override
  public void afterRefreshFinish(boolean asynchronous) {
    endChangeSet(LocalHistoryBundle.message("system.label.external.change"));
  }

  @Override
  public void commandStarted(CommandEvent e) {
    beginChangeSet();
  }

  @Override
  public void beforeCommandFinished(CommandEvent e) { }

  @Override
  public void commandFinished(CommandEvent e) {
    endChangeSet(e.getCommandName());
  }

  @Override
  public void undoTransparentActionStarted() { }

  @Override
  public void undoTransparentActionFinished() { }

  public void startAction() {
    myGateway.registerUnsavedDocuments(myVcs);
    myVcs.forceBeginChangeSet();
  }

  public void finishAction(String name) {
    myGateway.registerUnsavedDocuments(myVcs);
    endChangeSet(name);
  }

  private void beginChangeSet() {
    myVcs.beginChangeSet();
  }

  private void endChangeSet(String name) {
    myVcs.endChangeSet(name);
  }

  @Override
  public void fileCreated(@NotNull VirtualFileEvent e) {
    beginChangeSet();
    createRecursively(e.getFile());
    endChangeSet(null);
  }

  private void createRecursively(VirtualFile f) {
    VfsUtilCore.visitChildrenRecursively(f, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile f) {
        if (isVersioned(f)) {
          myVcs.created(f.getPath(), f.isDirectory());
        }
        return true;
      }

      @Nullable
      @Override
      public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile f) {
        // For unversioned files we try to get cached children in hope that they are already generated by content root manager:
        //  cached children may mean that there are versioned sub-folders or sub-files.
        return myGateway.isVersioned(f, true)
               ? IdeaGateway.loadAndIterateChildren(f)
               : IdeaGateway.iterateDBChildren(f);
      }
    });
  }

  @Override
  public void beforeContentsChange(@NotNull VirtualFileEvent e) {
    if (!areContentChangesVersioned(e)) return;
    VirtualFile f = e.getFile();

    Pair<StoredContent, Long> content = myGateway.acquireAndUpdateActualContent(f, null);
    if (content != null) {
      myVcs.contentChanged(f.getPath(), content.first, content.second);
    }
  }

  @Override
  public void beforePropertyChange(@NotNull VirtualFilePropertyEvent e) {
    if (VirtualFile.PROP_NAME.equals(e.getPropertyName())) {
      VirtualFile f = e.getFile();
      f.putUserData(WAS_VERSIONED_KEY, myGateway.isVersioned(f));
    }
  }

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent e) {
    if (VirtualFile.PROP_NAME.equals(e.getPropertyName())) {
      VirtualFile f = e.getFile();

      boolean isVersioned = myGateway.isVersioned(f);
      Boolean wasVersioned = f.getUserData(WAS_VERSIONED_KEY);
      if (wasVersioned == null) return;
      f.putUserData(WAS_VERSIONED_KEY, null);

      if (!wasVersioned && !isVersioned) return;

      String oldName = (String)e.getOldValue();
      myVcs.renamed(f.getPath(), oldName);
    }
    else if (VirtualFile.PROP_WRITABLE.equals(e.getPropertyName())) {
      if (!isVersioned(e.getFile())) return;
      VirtualFile f = e.getFile();
      if (!f.isDirectory()) {
        myVcs.readOnlyStatusChanged(f.getPath(), !(Boolean)e.getOldValue());
      }
    }
  }

  @Override
  public void beforeFileMovement(@NotNull VirtualFileMoveEvent e) {
    VirtualFile f = e.getFile();
    f.putUserData(WAS_VERSIONED_KEY, myGateway.isVersioned(f));
  }

  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent e) {
    VirtualFile f = e.getFile();

    boolean isVersioned = myGateway.isVersioned(f);
    Boolean wasVersioned = f.getUserData(WAS_VERSIONED_KEY);
    if (wasVersioned == null) return;
    f.putUserData(WAS_VERSIONED_KEY, null);

    if (!wasVersioned && !isVersioned) return;

    myVcs.moved(f.getPath(), e.getOldParent().getPath());
  }

  @Override
  public void beforeFileDeletion(@NotNull VirtualFileEvent e) {
    VirtualFile f = e.getFile();
    Entry entry = myGateway.createEntryForDeletion(f);
    if (entry != null) {
      myVcs.deleted(f.getPath(), entry);
    }
  }

  private boolean isVersioned(VirtualFile f) {
    return myGateway.isVersioned(f);
  }

  private boolean areContentChangesVersioned(VirtualFileEvent e) {
    return myGateway.areContentChangesVersioned(e.getFile());
  }
}