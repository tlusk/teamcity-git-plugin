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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import jetbrains.buildServer.agent.BuildInterruptReason;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.ScriptGen;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.*;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitCommandLine extends GeneralCommandLine {

  private final GitAgentSSHService mySsh;
  private final ScriptGen myScriptGen;
  private final List<Runnable> myPostActions = new ArrayList<Runnable>();
  private final File myTmpDir;
  private final boolean myDeleteTempFiles;
  private final GitProgressLogger myLogger;
  private final GitVersion myGitVersion;
  private final Context myCtx;
  private File myWorkingDirectory;
  private boolean myRepeatOnEmptyOutput = false;
  private VcsRootSshKeyManager mySshKeyManager;
  private boolean myHasProgress = false;

  public GitCommandLine(@Nullable GitAgentSSHService ssh,
                        @NotNull ScriptGen scriptGen,
                        @NotNull File tmpDir,
                        boolean deleteTempFiles,
                        @NotNull GitProgressLogger logger,
                        @NotNull GitVersion gitVersion,
                        @NotNull Map<String, String> env,
                        @NotNull Context ctx) {
    mySsh = ssh;
    myScriptGen = scriptGen;
    myTmpDir = tmpDir;
    myDeleteTempFiles = deleteTempFiles;
    myLogger = logger;
    myGitVersion = gitVersion;
    myCtx = ctx;
    setPassParentEnvs(true);
    setEnvParams(env);
  }

  public ExecResult run(@NotNull GitCommandSettings settings) throws VcsException {
    AuthSettings authSettings = settings.getAuthSettings();
    if (authSettings != null) {
      if (mySsh == null)
        throw new IllegalStateException("Ssh is not initialized");
      if (authSettings.getAuthMethod() == AuthenticationMethod.PASSWORD) {
        try {
          final File askPass = myScriptGen.generateAskPass(authSettings);
          String askPassPath = askPass.getAbsolutePath();
          if (askPassPath.contains(" ") && SystemInfo.isWindows) {
            askPassPath = GitUtils.getShortFileName(askPass);
          }
          getParametersList().addAt(0, "-c");
          getParametersList().addAt(1, "core.askpass=" + askPassPath);
          if (!getParametersList().getParametersString().contains("credential.helper") && !myGitVersion.isLessThan(UpdaterImpl.EMPTY_CRED_HELPER)) {
            //disable credential.helper if it wasn't specified by us
            getParametersList().addAt(2, "-c");
            getParametersList().addAt(3, "credential.helper=");
          }
          addPostAction(new Runnable() {
            public void run() {
              if (myDeleteTempFiles)
                FileUtil.delete(askPass);
            }
          });
          addEnvParam("GIT_ASKPASS", askPassPath);
        } catch (IOException e) {
          throw new VcsException(e);
        }
      }
      if (settings.isUseNativeSsh()) {
        return CommandUtil.runCommand(this, settings.getTimeout());
      } else {
        SshHandler h = new SshHandler(mySsh, mySshKeyManager, authSettings, this, myTmpDir, myCtx.getSshMacType());
        try {
          return CommandUtil.runCommand(this, settings.getTimeout());
        } finally {
          h.unregister();
        }
      }
    } else {
      return CommandUtil.runCommand(this, settings.getTimeout());
    }
  }


  @NotNull
  public GitVersion getGitVersion() {
    return myGitVersion;
  }

  public void addPostAction(@NotNull Runnable action) {
    myPostActions.add(action);
  }

  public List<Runnable> getPostActions() {
    return myPostActions;
  }

  @Override
  public void setWorkingDirectory(File workingDirectory) {
    myWorkingDirectory = workingDirectory;
    super.setWorkingDirectory(workingDirectory);
  }

  @Nullable
  public File getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public GitCommandLine repeatOnEmptyOutput(boolean doRepeat) {
    myRepeatOnEmptyOutput = doRepeat;
    return this;
  }

  public boolean isRepeatOnEmptyOutput() {
    return myRepeatOnEmptyOutput;
  }

  public void setSshKeyManager(VcsRootSshKeyManager sshKeyManager) {
    mySshKeyManager = sshKeyManager;
  }

  public void addEnvParam(@NotNull String name, @NotNull String value) {
    Map<String, String> existing = getEnvParams();
    if (existing == null)
      existing = new HashMap<String, String>();
    Map<String, String> newParams = new HashMap<String, String>(existing);
    newParams.put(name, value);
    setEnvParams(newParams);
  }

  @NotNull
  public GitProgressLogger getLogger() {
    return myLogger;
  }

  @NotNull
  public ByteArrayOutputStream createStderrBuffer() {
    LineAwareByteArrayOutputStream buffer = new LineAwareByteArrayOutputStream(Charset.forName("UTF-8"), new GitProgressListener(myLogger));
    buffer.setCREndsLine(true);
    return buffer;
  }

  public void logStart(@NotNull String msg) {
    if (myHasProgress) {
      myLogger.openBlock(msg);
    } else {
      myLogger.message(msg);
    }
  }

  public void logFinish(@NotNull String msg) {
    if (myHasProgress)
      myLogger.closeBlock(msg);
  }

  public void setHasProgress(final boolean hasProgress) {
    myHasProgress = hasProgress;
  }


  public void checkCanceled() throws VcsException {
    BuildInterruptReason reason = myCtx.getInterruptionReason();
    if (reason != null)
      throw new CheckoutCanceledException(reason);
  }
}
