/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class EncapsulateVariableFix extends InspectionGadgetsFix {

  private final String fieldName;

  public EncapsulateVariableFix(String fieldName) {
    this.fieldName = fieldName;
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("encapsulate.variable.quickfix",
                                           fieldName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Encapsulate field";
  }

  @Override
  public void doFix(final Project project, ProblemDescriptor descriptor) {
    final PsiElement nameElement = descriptor.getPsiElement();
    final PsiElement parent = nameElement.getParent();
    final PsiField field;
    if (parent instanceof PsiField) {
      field = (PsiField)parent;
    }
    else if (parent instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)parent;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return;
      }
      field = (PsiField)target;
    }
    else {
      return;
    }
    final JavaRefactoringActionHandlerFactory factory = JavaRefactoringActionHandlerFactory.getInstance();
    final RefactoringActionHandler encapsulateFieldsHandler = factory.createEncapsulateFieldsHandler();
    encapsulateFieldsHandler.invoke(project, new PsiElement[]{field}, null);
  }

  @Override
  protected boolean prepareForWriting() {
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
