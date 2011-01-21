/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;

import java.io.File;

/**
 * Utilities for th testing
 */
public class GitTestUtil {
  /**
   * The private constructor for the test class
   */
  private GitTestUtil() {

  }

  /**
   * Test data file
   *
   * @param path the file path relatively to data directory
   * @return the IO file object (the file is absolute)
   */
  public static File dataFile(String... path) {
    File f = new File("git-tests", "data");
    for (String p : path) {
      f = new File(f, p);
    }
    return f.getAbsoluteFile();
  }

  public static VcsRoot getVcsRoot() {
    return getVcsRoot("repo.git");
  }

  public static VcsRoot getVcsRoot(String repositoryName) {
    VcsRootImpl root = new VcsRootImpl(1, Constants.VCS_NAME);
    root.addProperty(Constants.FETCH_URL, GitUtils.toURL(dataFile(repositoryName)));
    root.addProperty(Constants.BRANCH_NAME, "master");
    return root;
  }

}
