/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.packageDependencies.ui;

import com.intellij.openapi.util.Comparing;

import java.util.Comparator;

public class DependencyNodeComparator implements Comparator<PackageDependenciesNode>{
  private final boolean mySortByType;

  public DependencyNodeComparator(final boolean sortByType) {
    mySortByType = sortByType;
  }

  public DependencyNodeComparator() {
    mySortByType = false;
  }

  public int compare(PackageDependenciesNode p1, PackageDependenciesNode p2) {
    if (p1.getWeight() != p2.getWeight()) return p1.getWeight() - p2.getWeight();
    if (mySortByType) {
      if (p1 instanceof Comparable) {
        return ((Comparable)p1).compareTo(p2);
      }
    }
    return Comparing.compare(p1.toString(), p2.toString());
  }
}