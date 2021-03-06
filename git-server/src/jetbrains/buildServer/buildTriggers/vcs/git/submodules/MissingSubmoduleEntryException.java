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

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MissingSubmoduleEntryException extends CorruptObjectException implements SubmoduleException {

  private final String myMainRepositoryUrl;
  private final String myMainRepositoryCommit;
  private final String mySubmodulePath;

  public MissingSubmoduleEntryException(@NotNull String mainRepositoryUrl,
                                        @NotNull String mainRepositoryCommit,
                                        @NotNull String submodulePath,
                                        @NotNull Set<String> branches) {
    super(buildMessage(mainRepositoryUrl, mainRepositoryCommit, submodulePath, branches));
    myMainRepositoryUrl = mainRepositoryUrl;
    myMainRepositoryCommit = mainRepositoryCommit;
    mySubmodulePath = submodulePath;
  }


  public MissingSubmoduleEntryException(@NotNull String mainRepositoryUrl,
                                        @NotNull String mainRepositoryCommit,
                                        @NotNull String submodulePath) {
    this(mainRepositoryUrl, mainRepositoryCommit, submodulePath, Collections.<String>emptySet());
  }


  @NotNull
  public String getMainRepositoryCommit() {
    return myMainRepositoryCommit;
  }


  @NotNull
  public MissingSubmoduleEntryException addBranches(@NotNull Set<String> branches) {
    MissingSubmoduleEntryException result = new MissingSubmoduleEntryException(myMainRepositoryUrl, myMainRepositoryCommit, mySubmodulePath, branches);
    result.setStackTrace(getStackTrace());
    return result;
  }


  @NotNull
  private static String buildMessage(@NotNull String mainRepositoryUrl,
                                     @NotNull String mainRepositoryCommit,
                                     @NotNull String submodulePath,
                                     @NotNull Set<String> branches) {
    StringBuilder result = new StringBuilder();
    result.append("The repository '").append(mainRepositoryUrl)
      .append("' has a submodule in the '").append(mainRepositoryCommit)
      .append("' commit at the '").append(submodulePath)
      .append("' path, but has no entry for this path in .gitmodules configuration");
    SubmoduleExceptionUtil.addAffectedBranches(result, branches);
    return result.toString();
  }
}
