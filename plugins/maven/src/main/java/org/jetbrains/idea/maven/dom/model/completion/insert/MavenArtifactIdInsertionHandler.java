// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion.insert;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.onlinecompletion.OfflineSearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;

import java.util.List;
import java.util.stream.Collectors;

public class MavenArtifactIdInsertionHandler extends MavenDependencyInsertionHandler {

  public static final InsertHandler<LookupElement> INSTANCE = new MavenArtifactIdInsertionHandler();

  @Override
  protected void setDependency(@NotNull InsertionContext context,
                               MavenRepositoryArtifactInfo completionItem,
                               XmlFile contextFile, MavenDomShortArtifactCoordinates domCoordinates) {
    if (completionItem.getArtifactId() == null) {
      return;
    }
    domCoordinates.getArtifactId().setStringValue(completionItem.getArtifactId());
    XmlTag tag = domCoordinates.getGroupId().getXmlTag();
    if (tag == null) {
      domCoordinates.getGroupId().setStringValue("");
    }
    int position = domCoordinates.getGroupId().getXmlTag().getValue().getTextRange().getStartOffset();
    context.getEditor().getCaretModel().moveToOffset(position);
    MavenDependencyCompletionUtil.invokeCompletion(context, CompletionType.BASIC);
  }
}
