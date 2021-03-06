/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TestLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.util.cache.ResetCacheHandler;
import jetbrains.buildServer.vcs.*;
import org.apache.log4j.Level;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.util.Util.map;
import static jetbrains.buildServer.vcs.RepositoryStateData.createVersionState;
import static junit.framework.Assert.assertEquals;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.BDDAssertions.then;
import static org.testng.AssertJUnit.fail;

@SuppressWarnings({"ResultOfMethodCallIgnored", "ArraysAsListWithZeroOrOneArgument"})
@Test
public class CollectChangesTest extends BaseRemoteRepositoryTest {

  private PluginConfigBuilder myConfig;
  private File myRepo;
  private TestLogger myLogger;

  public CollectChangesTest() {
    super("repo.git", "TW-43643-1", "TW-43643-2");
  }


  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLogger = new TestLogger();
    myLogger.setLogLevel(Level.INFO);
    myConfig = new PluginConfigBuilder(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath()));
    myRepo = getRemoteRepositoryDir("repo.git");
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCollectBuildChanges(boolean fetchInSeparateProcess) throws Exception {
    myConfig.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    List<ModificationData> changes = support.collectChanges(root, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7", CheckoutRules.DEFAULT);
    assertEquals(2, changes.size());

    ModificationData mod_97442a = changes.get(1);
    then(mod_97442a.getVersion()).isEqualTo("97442a720324a0bd092fb9235f72246dc8b345bc");
    then(mod_97442a.getDescription()).isEqualTo("The second commit\n");
    then(mod_97442a.getChanges()).extracting("type", "fileName")
      .containsOnly(tuple(VcsChange.Type.ADDED, "dir/a.txt"),
                    tuple(VcsChange.Type.ADDED, "dir/b.txt"),
                    tuple(VcsChange.Type.ADDED, "dir/tr.txt"));

    ModificationData mod_ad4528 = changes.get(0);
    then(mod_ad4528.getVersion()).isEqualTo("ad4528ed5c84092fdbe9e0502163cf8d6e6141e7");
    then(mod_ad4528.getDescription()).isEqualTo("more changes\n");
    assertEquals(3, mod_ad4528.getChanges().size());
    then(mod_ad4528.getChanges()).extracting("type", "fileName")
      .containsOnly(tuple(VcsChange.Type.CHANGED, "dir/a.txt"),
                    tuple(VcsChange.Type.ADDED, "dir/c.txt"),
                    tuple(VcsChange.Type.REMOVED, "dir/tr.txt"));
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testConcurrentCollectBuildChanges(boolean fetchInSeparateProcess) throws Throwable {
    myConfig.setSeparateProcessForFetch(fetchInSeparateProcess);
    final GitVcsSupport support = git();
    final List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());

    final VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    Runnable r1 = new Runnable() {
      public void run() {
        try {
          List<ModificationData> changes = support.collectChanges(root, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7", CheckoutRules.DEFAULT);
          then(changes).hasSize(2);
          ModificationData mod_97442a = changes.get(1);
          then(mod_97442a.getVersion()).isEqualTo("97442a720324a0bd092fb9235f72246dc8b345bc");
          then(mod_97442a.getDescription()).isEqualTo("The second commit\n");
          then(mod_97442a.getChanges()).extracting("type", "fileName")
            .containsOnly(tuple(VcsChange.Type.ADDED, "dir/a.txt"),
                          tuple(VcsChange.Type.ADDED, "dir/b.txt"),
                          tuple(VcsChange.Type.ADDED, "dir/tr.txt"));

          ModificationData mod_ad4528 = changes.get(0);
          then(mod_ad4528.getVersion()).isEqualTo("ad4528ed5c84092fdbe9e0502163cf8d6e6141e7");
          then(mod_ad4528.getDescription()).isEqualTo("more changes\n");
          assertEquals(3, mod_ad4528.getChanges().size());
          then(mod_ad4528.getChanges()).extracting("type", "fileName")
            .containsOnly(tuple(VcsChange.Type.CHANGED, "dir/a.txt"),
                          tuple(VcsChange.Type.ADDED, "dir/c.txt"),
                          tuple(VcsChange.Type.REMOVED, "dir/tr.txt"));
        } catch (Throwable e) {
          errors.add(e);
        }
      }
    };

    Runnable r2 = new Runnable() {
      public void run() {
        try {
          then(support.collectChanges(root, "ee886e4adb70fbe3bdc6f3f6393598b3f02e8009", "f3f826ce85d6dad25156b2d7550cedeb1a422f4c", CheckoutRules.DEFAULT))
            .hasSize(2);
        } catch (Throwable e) {
          errors.add(e);
        }
      }
    };

    Runnable r3 = new Runnable() {
      public void run() {
        try {
          List<ModificationData> changes = support.collectChanges(root, "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7", "f3f826ce85d6dad25156b2d7550cedeb1a422f4c", CheckoutRules.DEFAULT);
          then(changes).hasSize(3);
          ModificationData mod_f3f826 = changes.get(0);
          then(mod_f3f826.getVersion()).isEqualTo("f3f826ce85d6dad25156b2d7550cedeb1a422f4c");
          then(mod_f3f826.getDescription()).isEqualTo("merge commit\n");
          then(mod_f3f826.getChanges()).extracting("type", "fileName")
            .containsOnly(tuple(VcsChange.Type.REMOVED, "dir/a.txt"),
                          tuple(VcsChange.Type.CHANGED, "dir/b.txt"),
                          tuple(VcsChange.Type.ADDED, "dir/q.txt"));

          ModificationData mod_ee886e = changes.get(1);
          then(mod_ee886e.getDescription()).isEqualTo("b-mod, d-add\n");
          then(mod_ee886e.getVersion()).isEqualTo("ee886e4adb70fbe3bdc6f3f6393598b3f02e8009");
          then(mod_ee886e.getChanges()).extracting("type", "fileName")
            .containsOnly(tuple(VcsChange.Type.CHANGED, "dir/b.txt"),
                          tuple(VcsChange.Type.ADDED, "dir/d.txt"));


          ModificationData mod_6fce8f = changes.get(2);
          then(mod_6fce8f.getDescription()).isEqualTo("a-mod, c-rm\n");
          then(mod_6fce8f.getVersion()).isEqualTo("6fce8fe45550eb72796704a919dad68dc44be44a");
          then(mod_6fce8f.getChanges()).extracting("type", "fileName")
            .containsOnly(tuple(VcsChange.Type.CHANGED, "dir/a.txt"),
                          tuple(VcsChange.Type.REMOVED, "dir/c.txt"));
        } catch (Throwable e) {
          errors.add(e);
        }
      }
    };

    Runnable r4 = new Runnable() {
      public void run() {
        try {
          String unknown = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
          then(support.collectChanges(root, unknown, "f3f826ce85d6dad25156b2d7550cedeb1a422f4c", CheckoutRules.DEFAULT)).isEmpty();
        } catch (Throwable e) {
          errors.add(e);
        }
      }
    };

    BaseTestCase.runAsyncAndFailOnException(1, r1, r2, r3, r4);

    if (!errors.isEmpty()) {
      throw errors.get(0);
    }
  }


  @Test
  public void collect_changes_should_understand_revisions_with_timestamps() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();
    List<ModificationData> changes = git().collectChanges(root,
                                                          GitUtils.makeVersion("2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", 1237391915000L),
                                                          GitUtils.makeVersion("ee886e4adb70fbe3bdc6f3f6393598b3f02e8009", 1238085489000L),
                                                          CheckoutRules.DEFAULT);
    then(changes).extracting("version")
      .containsOnly("97442a720324a0bd092fb9235f72246dc8b345bc",
                    "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7",
                    "ee886e4adb70fbe3bdc6f3f6393598b3f02e8009");
  }


  @TestFor(issues = "TW-30485")
  public void collect_changes_between_states_should_understand_revisions_with_timestamps() throws Exception {
    VcsRoot root = vcsRoot().withBranch("refs/heads/master").withFetchUrl(myRepo).build();
    RepositoryStateData stateInOldFormat = RepositoryStateData.createSingleVersionState(GitUtils.makeVersion("2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6", 1286183770000L));
    RepositoryStateData stateInNewFormat = createVersionState("refs/heads/master", map("refs/heads/master", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9"));
    List<ModificationData> changes = git().getCollectChangesPolicy().collectChanges(root, stateInOldFormat, stateInNewFormat, CheckoutRules.DEFAULT);
    then(changes).extracting("version")
      .containsOnly("5711cbfe566b6c92e331f95d4b236483f4532eed",
                    "465ad9f630e451b9f2b782ffb09804c6a98c4bb9");
  }


  public void all_changes_should_have_parents() throws Exception {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();
    List<ModificationData> changes = support.collectChanges(root, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", "ee886e4adb70fbe3bdc6f3f6393598b3f02e8009", CheckoutRules.DEFAULT);
    then(changes).extracting("version", "parentRevisions")
      .containsOnly(tuple("ee886e4adb70fbe3bdc6f3f6393598b3f02e8009", asList("ad4528ed5c84092fdbe9e0502163cf8d6e6141e7")),
                    tuple("ad4528ed5c84092fdbe9e0502163cf8d6e6141e7", asList("97442a720324a0bd092fb9235f72246dc8b345bc")),
                    tuple("97442a720324a0bd092fb9235f72246dc8b345bc", asList("2276eaf76a658f96b5cf3eb25f3e1fda90f6b653")));
  }


  public void collect_changes_after_cache_reset() throws Exception {
    GitVcsSupport git = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();
    git.collectChanges(root, "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", CheckoutRules.DEFAULT);

    ServerPluginConfig config = myConfig.build();
    MirrorManager mirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl());
    RepositoryManager repositoryManager = new RepositoryManagerImpl(config, mirrorManager);
    ResetCacheHandler resetHandler = new GitResetCacheHandler(repositoryManager);
    for (String cache : resetHandler.listCaches())
      resetHandler.resetCache(cache);

    try {
      git.collectChanges(root, "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", CheckoutRules.DEFAULT);
    } catch (VcsException e) {
      fail("Reset of caches breaks repository");
    }
  }


  public void test_collect_changes_between_states() throws IOException, VcsException {
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();
    GitVcsSupport git = git();
    RepositoryStateData fromState = createVersionState("master", map("master", "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7"));
    RepositoryStateData toState = createVersionState("master", map("master", "3b9fbfbb43e7edfad018b482e15e7f93cca4e69f",
                                                                                       "personal-branch2", "3df61e6f11a5a9b919cb3f786a83fdd09f058617"));
    List<ModificationData> changes = git.getCollectChangesPolicy().collectChanges(root, fromState, toState, CheckoutRules.DEFAULT);
    then(changes).extracting("version")
      .containsOnly("3b9fbfbb43e7edfad018b482e15e7f93cca4e69f",
                    "3df61e6f11a5a9b919cb3f786a83fdd09f058617",
                    "e7f491963fbc5c96a27e4169b97746a5a7f83870",
                    "1391281d33a83a7205f2f05d3eb64c349c636e87",
                    "f3f826ce85d6dad25156b2d7550cedeb1a422f4c",
                    "ee886e4adb70fbe3bdc6f3f6393598b3f02e8009",
                    "6fce8fe45550eb72796704a919dad68dc44be44a");
  }


  public void start_using_full_branch_name_as_default_branch_name() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();
    RepositoryStateData from = createVersionState("master", map("master", "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7"));
    RepositoryStateData to = createVersionState("refs/heads/master", map("refs/heads/master", "3b9fbfbb43e7edfad018b482e15e7f93cca4e69f"));
    List<ModificationData> changes = git().getCollectChangesPolicy().collectChanges(root, from, to, CheckoutRules.DEFAULT);
    then(changes).extracting("version")
      .containsOnly("3b9fbfbb43e7edfad018b482e15e7f93cca4e69f",
                    "f3f826ce85d6dad25156b2d7550cedeb1a422f4c",
                    "ee886e4adb70fbe3bdc6f3f6393598b3f02e8009",
                    "6fce8fe45550eb72796704a919dad68dc44be44a");
  }


  @TestFor(issues = "TW-23781")
  public void collect_changes_between_repositories_with_different_urls_and_branches() throws Exception {
    File forkDir = myTempFiles.createTempDir();
    copyRepository(myRepo, forkDir);
    VcsRoot root1 = vcsRoot().withFetchUrl(myRepo).withBranch("master").build();
    VcsRoot root2 = vcsRoot().withFetchUrl(forkDir).withBranch("patch-tests").build();
    RepositoryStateData state1 = createVersionState("refs/heads/master", map("refs/heads/master", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9"));
    RepositoryStateData state2 = createVersionState("refs/heads/patch-tests", map("refs/heads/patch-tests", "27de3d118ca320d3a8a08320ff05aa0567996590"));
    List<ModificationData> changes = git().getCollectChangesPolicy().collectChanges(root1, state1, root2, state2, CheckoutRules.DEFAULT);
    then(changes).extracting("version")
      .containsOnly("27de3d118ca320d3a8a08320ff05aa0567996590",
                    "d1a88fd33c516c1b607db75eb62244b2ea495c42",
                    "37c371a6db0acefc77e3be99d16a44413e746591",
                    "b5d65401a4e8a09b80b8d73ca4392f1913e99ff5",
                    "592c5bcee6d906482177a62a6a44efa0cff9bbc7",
                    "1837cf38309496165054af8bf7d62a9fe8997202",
                    "049a98762a29677da352405b27b3d910cb94eb3b",
                    "7e916b0edd394d0fca76456af89f4ff7f7f65049",
                    "0dd03338d20d2e8068fbac9f24899d45d443df38",
                    "70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46",
                    "a894d7d58ffde625019a9ecf8267f5f1d1e5c341");
  }


  @TestFor(issues = "TW-29798")
  public void do_not_do_fetch_per_branch() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(myRepo)
      .withBranch("master")
      .withReportTags(true)
      .build();

    //setup fetcher with counter
    ServerPluginConfig config = myConfig.build();
    VcsRootSshKeyManager manager = new EmptyVcsRootSshKeyManager();
    FetchCommand fetchCommand = new FetchCommandImpl(config, new TransportFactoryImpl(config, manager), new FetcherProperties(config), manager);
    FetchCommandCountDecorator fetchCounter = new FetchCommandCountDecorator(fetchCommand);
    GitVcsSupport git = gitSupport().withPluginConfig(myConfig).withFetchCommand(fetchCounter).build();

    RepositoryStateData state = git.getCurrentState(root);
    RepositoryStateData s1 = createVersionState("refs/heads/master", map("refs/heads/master", state.getBranchRevisions().get("refs/heads/master")));//has a single branch
    RepositoryStateData s2 = createVersionState("refs/heads/master", state.getBranchRevisions());//has many branches

    git.getCollectChangesPolicy().collectChanges(root, s1, s2, CheckoutRules.DEFAULT);
    assertEquals(fetchCounter.getFetchCount(), 1);

    FileUtil.delete(config.getCachesDir());
    fetchCounter.resetFetchCounter();

    git.getCollectChangesPolicy().collectChanges(root, s2, s1, CheckoutRules.DEFAULT);
    assertEquals(fetchCounter.getFetchCount(), 1);
  }


  @Test
  //this test should be removed if a single fetch works fine
  public void should_be_able_to_do_fetch_per_branch() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(myRepo)
      .withBranch("master")
      .withReportTags(true)
      .build();

    //setup fetcher with counter
    ServerPluginConfig config = myConfig.withPerBranchFetch(true).build();
    VcsRootSshKeyManager manager = new EmptyVcsRootSshKeyManager();
    FetchCommand fetchCommand = new FetchCommandImpl(config, new TransportFactoryImpl(config, manager), new FetcherProperties(config), manager);
    FetchCommandCountDecorator fetchCounter = new FetchCommandCountDecorator(fetchCommand);
    GitVcsSupport git = gitSupport().withPluginConfig(myConfig).withFetchCommand(fetchCounter).build();

    RepositoryStateData state = git.getCurrentState(root);
    RepositoryStateData s1 = createVersionState("refs/heads/master", map("refs/heads/master", state.getBranchRevisions().get("refs/heads/master")));//has a single branch
    RepositoryStateData s2 = createVersionState("refs/heads/master", state.getBranchRevisions());//has many branches

    git.getCollectChangesPolicy().collectChanges(root, s1, s2, CheckoutRules.DEFAULT);
    then(fetchCounter.getFetchCount()).isGreaterThan(1);

    FileUtil.delete(config.getCachesDir());
    fetchCounter.resetFetchCounter();

    git.getCollectChangesPolicy().collectChanges(root, s2, s1, CheckoutRules.DEFAULT);
    then(fetchCounter.getFetchCount()).isGreaterThan(1);
  }


  @Test
  @TestFor(issues = "http://youtrack.jetbrains.com/issue/TW-29798#comment=27-537697")
  public void fetch_should_not_fail_if_remote_repository_does_not_have_some_branches() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(myRepo)
      .withBranch("master")
      .withReportTags(true)
      .build();

    //setup fetcher with a counter
    ServerPluginConfig config = myConfig.build();
    VcsRootSshKeyManager manager = new EmptyVcsRootSshKeyManager();
    FetchCommand fetchCommand = new FetchCommandImpl(config, new TransportFactoryImpl(config, manager), new FetcherProperties(config), manager);
    FetchCommandCountDecorator fetchCounter = new FetchCommandCountDecorator(fetchCommand);
    GitVcsSupport git = gitSupport().withPluginConfig(myConfig).withFetchCommand(fetchCounter).build();

    RepositoryStateData state = git.getCurrentState(root);
    RepositoryStateData s1 = createVersionState("refs/heads/master", map("refs/heads/master", state.getBranchRevisions().get("refs/heads/master")));//has a single branch
    Map<String, String> branches = new HashMap<String, String>(state.getBranchRevisions());
    branches.put("refs/heads/unknown.branch", branches.get(state.getDefaultBranchName()));//unknown branch that points to a commit that exists in remote repo
    RepositoryStateData s2 = createVersionState("refs/heads/master", branches);//has many branches, some of them don't exist in remote repository

    git.getCollectChangesPolicy().collectChanges(root, s1, s2, CheckoutRules.DEFAULT);
    assertEquals(fetchCounter.getFetchCount(), 1);

    FileUtil.delete(config.getCachesDir());
    fetchCounter.resetFetchCounter();

    git.getCollectChangesPolicy().collectChanges(root, s2, s1, CheckoutRules.DEFAULT);
    assertEquals(fetchCounter.getFetchCount(), 1);
  }


  @DataProvider(name = "separateProcess,newConnectionForPrune")
  public static Object[][] createData() {
    return new Object[][] {
      new Object[] { Boolean.TRUE, Boolean.TRUE },
      new Object[] { Boolean.TRUE, Boolean.FALSE },
      new Object[] { Boolean.FALSE, Boolean.TRUE },
      new Object[] { Boolean.FALSE, Boolean.FALSE }
    };
  }

  @TestFor(issues = {"TW-36080", "TW-35700"})
  @Test(dataProvider = "separateProcess,newConnectionForPrune")
  public void branch_turned_into_dir(boolean fetchInSeparateProcess, boolean newConnectionForPrune) throws Exception {
    myConfig.setSeparateProcessForFetch(fetchInSeparateProcess).setNewConnectionForPrune(newConnectionForPrune);
    VcsRoot root = vcsRoot().withFetchUrl(myRepo)
      .withBranch("master")
      .build();
    RepositoryStateData s1 = createVersionState("refs/heads/master",
                                                map("refs/heads/master", "f3f826ce85d6dad25156b2d7550cedeb1a422f4c",
                                                    "refs/heads/patch-tests", "a894d7d58ffde625019a9ecf8267f5f1d1e5c341"));
    RepositoryStateData s2 = createVersionState("refs/heads/master",
                                                map("refs/heads/master", "3b9fbfbb43e7edfad018b482e15e7f93cca4e69f",
                                                    "refs/heads/patch-tests", "a894d7d58ffde625019a9ecf8267f5f1d1e5c341"));

    git().getCollectChangesPolicy().collectChanges(root, s1, s2, CheckoutRules.DEFAULT);

    //rename refs/heads/patch-tests to refs/heads/patch-tests/a and make it point to commit not yet fetched by TC, so the fetch is required
    Repository r = new RepositoryBuilder().setGitDir(myRepo).build();
    r.getRefDatabase().newRename("refs/heads/patch-tests", "refs/heads/patch-tests/a").rename();
    RefUpdate refUpdate = r.updateRef("refs/heads/patch-tests/a");
    refUpdate.setForceUpdate(true);
    refUpdate.setNewObjectId(ObjectId.fromString("39679cc440c83671fbf6ad8083d92517f9602300"));
    refUpdate.update();

    RepositoryStateData s3 = createVersionState("refs/heads/master",
                                                map("refs/heads/master", "3b9fbfbb43e7edfad018b482e15e7f93cca4e69f",
                                                    "refs/heads/patch-tests/a", "39679cc440c83671fbf6ad8083d92517f9602300"));
    git().getCollectChangesPolicy().collectChanges(root, s2, s3, CheckoutRules.DEFAULT);
  }


  @TestFor(issues = "TW-36653")
  public void comma_in_branch_name() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    File brokenRef = new File(myRepo, "refs" + File.separator + "heads" + File.separator + "aaa,bbb");
    brokenRef.getParentFile().mkdirs();
    FileUtil.writeFileAndReportErrors(brokenRef, "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6\n");

    RepositoryStateData s1 = createVersionState("refs/heads/master", map("refs/heads/master", "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6"));
    RepositoryStateData s2 = createVersionState("refs/heads/master", map("refs/heads/master", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9",
                                                                         "refs/heads/aaa,bbb", "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6"));

    git().getCollectChangesPolicy().collectChanges(root, s1, s2, CheckoutRules.DEFAULT);//no error
  }


  @TestFor(issues = "TW-29770")
  public void collect_changes_with_branch_pointing_to_a_non_commit() throws Exception {
    //setup remote repo with a branch pointing to a non commit
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    File brokenRef = new File(myRepo, "refs" + File.separator + "heads" + File.separator + "broken_branch");
    brokenRef.getParentFile().mkdirs();
    FileUtil.writeFileAndReportErrors(brokenRef, "1fefad14fba39ac378e4e345e295fa1f90e343ae\n");//it's a tree, not a commit

    RepositoryStateData state1 = createVersionState("refs/heads/master",
                                                    map("refs/heads/master", "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6",
                                                        "refs/heads/broken_branch", "1fefad14fba39ac378e4e345e295fa1f90e343ae"));
    RepositoryStateData state2 = createVersionState("refs/heads/master",
                                                    map("refs/heads/master", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9",
                                                        "refs/heads/broken_branch", "1fefad14fba39ac378e4e345e295fa1f90e343ae"));

    //collect changes in this repo, no exception should be thrown
    git().getCollectChangesPolicy().collectChanges(root, state1, state2, CheckoutRules.DEFAULT);
  }


  @TestFor(issues = {"TW-41943", "TW-46600"})
  public void collect_changes_with_broken_commit_encoding() throws Exception {
    myLogger.enableDebug();//TW-46600 happens only when debug is enabled
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    RepositoryStateData state1 = createVersionState("refs/heads/master",
                                                    map("refs/heads/master", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9"));
    RepositoryStateData state2 = createVersionState("refs/heads/master", map("refs/heads/master", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9",
                                                                             "refs/heads/brokenEncoding", "b0799af24940ea316efd2985b5c5c10b47875abd"));
    List<ModificationData> changes = git().getCollectChangesPolicy().collectChanges(root, state1, state2, CheckoutRules.DEFAULT);
    ModificationData commit = changes.get(0);
    assertEquals("Cannot parse commit message due to unknown commit encoding 'brokenEncoding'", commit.getDescription());
  }


  @TestFor(issues = "TW-43643")
  public void should_fetch_all_refs_when_commit_not_found() throws Exception {
    File repo = getRemoteRepositoryDir("TW-43643-1");

    VcsRoot rootBranch1 = vcsRoot().withFetchUrl(repo).withBranch("branch1").build();
    //clone repository on server
    RepositoryStateData s1 = RepositoryStateData.createVersionState("refs/heads/branch1", "b56875abce7e1488991223c29ed14cc26ec4b786");
    RepositoryStateData s2 = RepositoryStateData.createVersionState("refs/heads/branch1", "22d8a6d243915cb9f878a0ef95a0999bb5f56715");
    git().getCollectChangesPolicy().collectChanges(rootBranch1, s1, s2, CheckoutRules.DEFAULT);

    //update remote repository: branch1 is removed, branch2 is added
    File updatedRepo = getRemoteRepositoryDir("TW-43643-2");
    FileUtil.delete(repo);
    repo.mkdirs();
    FileUtil.copyDir(updatedRepo, repo);

    //delete clone on server to emulate git gc which prunes the '22d8a6d243915cb9f878a0ef95a0999bb5f56715'
    //commit unreachable from branches (tags are not fetched by default)
    MirrorManagerImpl mirrors = new MirrorManagerImpl(myConfig.build(), new HashCalculatorImpl());
    File cloneOnServer = mirrors.getMirrorDir(repo.getCanonicalPath());
    FileUtil.delete(cloneOnServer);

    //collect changes in master to clone the repository
    VcsRoot rootMaster = vcsRoot().withFetchUrl(repo).withBranch("master").build();
    RepositoryStateData s3 = RepositoryStateData.createVersionState("refs/heads/master", "b56875abce7e1488991223c29ed14cc26ec4b786");
    RepositoryStateData s4 = RepositoryStateData.createVersionState("refs/heads/master", "ea5bd5a6e37ac1592fb1c4864bb38cbce95fa93a");
    git().getCollectChangesPolicy().collectChanges(rootMaster, s3, s4, CheckoutRules.DEFAULT);

    //clone on the server doesn't contain the commit branch1 was pointing to:
    Repository repository = new RepositoryBuilder().setGitDir(cloneOnServer).setBare().build();
    then(repository.hasObject(ObjectId.fromString("22d8a6d243915cb9f878a0ef95a0999bb5f56715"))).isFalse();

    //but we we collect changes between branch1 and branch2 we should fetch all
    //available refs, get the '22d8a6d243915cb9f878a0ef95a0999bb5f56715' reachable from tag
    //and report changes:
    VcsRoot rootBranch2 = vcsRoot().withFetchUrl(repo).withBranch("branch2").build();
    RepositoryStateData s5 = RepositoryStateData.createVersionState("refs/heads/branch2", "bc979d0e5bc0e6030a9db27c75004e6eb8cdb961");
    List<ModificationData> changes = git().getCollectChangesPolicy().collectChanges(rootBranch1, s2, rootBranch2, s5, CheckoutRules.DEFAULT);
    then(changes).extracting("version").containsExactly("bc979d0e5bc0e6030a9db27c75004e6eb8cdb961");
  }


  @DataProvider
  private Object[][] ref_error() {
    return new Object[][] {
      {
        "refs/heads/Master",
        "Failed to fetch ref refs/heads/Master: on case-insensitive file system it clashes with refs/heads/master. Please remove conflicting refs from repository."
      },
      {
        "refs/heads/master/release",
        "Failed to fetch ref refs/heads/master/release: it clashes with refs/heads/master. Please remove conflicting refs from repository."
      }
    };
  }


  @TestFor(issues = "TW-43859")
  @Test(dataProvider = "ref_error")
  public void should_report_conflicting_refs(@NotNull String conflictingRef, @NotNull String expectedError) throws Exception {
    GitVcsSupport git = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();
    //fetch repo
    git.collectChanges(root, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7", CheckoutRules.DEFAULT);

    RepositoryStateData state1 = git.getCurrentState(root);

    //create conflicting ref in packed-refs
    File remotePackedRefs = new File(myRepo, "packed-refs");
    FileUtil.writeToFile(remotePackedRefs, ("2276eaf76a658f96b5cf3eb25f3e1fda90f6b653 " + conflictingRef + "\n").getBytes(), true);

    //try to fetch
    RepositoryStateData state2 = git.getCurrentState(root);
    try {
      git.getCollectChangesPolicy().collectChanges(root, state1, state2, CheckoutRules.DEFAULT);
    } catch (VcsException e) {
      //might succeed, but if it fails, then error should explain why
      String msg = e.getMessage();
      then(msg).contains(expectedError);
    }
  }


  private GitVcsSupport git() {
    return gitSupport().withPluginConfig(myConfig).build();
  }
}
