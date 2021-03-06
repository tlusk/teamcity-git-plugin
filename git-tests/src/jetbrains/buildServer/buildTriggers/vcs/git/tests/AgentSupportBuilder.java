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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.HashCalculatorImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManagerImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.BuildAgentConfigurationBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class AgentSupportBuilder {

  private final TempFiles myTempFiles;
  private MirrorManagerImpl myMirrorManager;
  private GitDetector myGitDetector;
  private BuildAgentConfiguration myAgentConfiguration;
  private PluginConfigFactoryImpl myPluginConfigFactory;
  private VcsRootSshKeyManagerProvider mySshKeyProvider;
  private GitMetaFactory myGitMetaFactory;
  private BuildAgent myAgent;
  private FS myFS;

  AgentSupportBuilder(@NotNull TempFiles tempFiles) {
    myTempFiles = tempFiles;
  }


  @NotNull
  GitAgentVcsSupport build() throws IOException {
    GitPathResolver resolver = new MockGitPathResolver();
    if (myGitDetector == null)
      myGitDetector = new GitDetectorImpl(resolver);
    if (myAgentConfiguration == null)
      myAgentConfiguration = BuildAgentConfigurationBuilder.agentConfiguration(myTempFiles.createTempDir(), myTempFiles.createTempDir()).build();
    if (myPluginConfigFactory == null)
      myPluginConfigFactory = new PluginConfigFactoryImpl(myAgentConfiguration, myGitDetector);
    if (myMirrorManager == null)
      myMirrorManager = new MirrorManagerImpl(new AgentMirrorConfig(myAgentConfiguration), new HashCalculatorImpl());
    if (mySshKeyProvider == null)
      mySshKeyProvider = new MockVcsRootSshKeyManagerProvider();
    if (myGitMetaFactory == null)
      myGitMetaFactory = new GitMetaFactoryImpl();
    if (myAgent == null)
      myAgent = new MockBuildAgent();
    if (myFS == null)
      myFS = new FSImpl();
    return new GitAgentVcsSupport(myFS, new MockDirectoryCleaner(),
                                  new GitAgentSSHService(myAgent, myAgentConfiguration, new MockGitPluginDescriptor(), mySshKeyProvider),
                                  myPluginConfigFactory, myMirrorManager, myGitMetaFactory);
  }


  MirrorManagerImpl getMirrorManager() {
    return myMirrorManager;
  }


  AgentSupportBuilder setGitDetector(final GitDetector gitDetector) {
    myGitDetector = gitDetector;
    return this;
  }


  AgentSupportBuilder setSshKeyProvider(final VcsRootSshKeyManagerProvider sshKeyProvider) {
    mySshKeyProvider = sshKeyProvider;
    return this;
  }


  AgentSupportBuilder setGitMetaFactory(final GitMetaFactory gitMetaFactory) {
    myGitMetaFactory = gitMetaFactory;
    return this;
  }


  AgentSupportBuilder setFS(final FS FS) {
    myFS = FS;
    return this;
  }


  BuildAgentConfiguration getAgentConfiguration() {
    return myAgentConfiguration;
  }
}
