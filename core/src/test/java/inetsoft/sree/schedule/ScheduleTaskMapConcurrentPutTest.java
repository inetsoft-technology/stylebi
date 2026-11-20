/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.sree.schedule;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.dep.ScheduleTaskAsset;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81905, tasks created at the same time on different nodes were stored but listed nowhere.
 * <p>
 * All tasks of a folder are the entries of one stored folder that every writer reads, changes
 * and writes back as a whole. The monitor of {@code ScheduleManager} only serializes the writers
 * of one node, so the tests call the {@code ScheduleTaskMap} and the folder service directly from
 * several threads, which is what several nodes amount to.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskMapConcurrentPutTest {
   // the default organization, the folder service audits against an existing organization
   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final int THREADS = 9;
   private static final int TASKS = 60;

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   IndexedStorage indexedStorage;

   private Principal savedPrincipal;
   private final List<Thread> threads = new ArrayList<>();

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal());
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         for(Thread thread : threads) {
            thread.join(30000);
            assertFalse(thread.isAlive(), "a worker thread is still running");
         }
      }
      finally {
         ThreadContext.setContextPrincipal(savedPrincipal);
         taskMap().clear();
         taskMap().clearCache();
      }
   }

   @Test
   void concurrentPutsOfDistinctTasks_areAllListed() throws Exception {
      List<Runnable> work = new ArrayList<>();

      for(int i = 0; i < TASKS; i++) {
         String name = "StmcpPut" + i;
         work.add(() -> taskMap().put(key(name), newTask(name)));
      }

      runConcurrently(work);

      taskMap().clearCache();
      assertEquals(names(0, TASKS, "StmcpPut"), listed(),
                   "every created task is listed, none has lost its folder entry");

      for(int i = 0; i < TASKS; i++) {
         assertNotNull(taskMap().get(key("StmcpPut" + i)), "the task is stored");
      }
   }

   @Test
   void concurrentPutsAndRemoves_listExactlyTheRemainingTasks() throws Exception {
      int existing = 30;

      for(int i = 0; i < existing; i++) {
         String name = "StmcpOld" + i;
         taskMap().put(key(name), newTask(name));
      }

      List<Runnable> work = new ArrayList<>();

      for(int i = 0; i < existing; i++) {
         String name = "StmcpOld" + i;
         work.add(() -> taskMap().remove(key(name)));
      }

      for(int i = 0; i < existing; i++) {
         String name = "StmcpNew" + i;
         work.add(() -> taskMap().put(key(name), newTask(name)));
      }

      runConcurrently(work);

      taskMap().clearCache();
      assertEquals(names(0, existing, "StmcpNew"), listed(),
                   "the removed tasks are gone, the created tasks are all listed");
   }

   @Test
   void concurrentFolderCreatesAndTaskPuts_areAllListedInTheParent() throws Exception {
      ScheduleTaskFolderService folderService = folderService();
      int folders = 12;
      int tasks = 36;
      List<Runnable> work = new ArrayList<>();

      for(int i = 0; i < folders; i++) {
         String path = "StmcpFolder" + i;
         work.add(() -> {
            try {
               folderService.addFolder(rootEntry(), path, "/", AssetRepository.GLOBAL_SCOPE,
                                       principal());
            }
            catch(Exception e) {
               throw new RuntimeException(e);
            }
         });
      }

      for(int i = 0; i < tasks; i++) {
         String name = "StmcpMixed" + i;
         work.add(() -> taskMap().put(key(name), newTask(name)));
      }

      runConcurrently(work);

      taskMap().clearCache();
      assertEquals(names(0, tasks, "StmcpMixed"), listed(), "every task is listed");
      AssetFolder root = (AssetFolder) indexedStorage
         .getXMLSerializable(rootEntry().toIdentifier(), null, ORG);
      assertNotNull(root);
      long folderEntries = Arrays.stream(root.getEntries())
         .filter(AssetEntry::isScheduleTaskFolder).count();
      assertEquals(folders, folderEntries, "every created folder is listed in its parent");
   }

   // the deploy import adds the folders of an imported task to their parent, a read-modify-write
   // of the same folder blob as a task create
   @Test
   void concurrentImportFoldersAndTaskPuts_areAllListedInTheParent() throws Exception {
      int folders = 12;
      int tasks = 36;
      ScheduleTaskAsset asset = new ScheduleTaskAsset();
      List<Runnable> work = new ArrayList<>();

      for(int i = 0; i < folders; i++) {
         AssetEntry folder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                            AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                                            "StmcpImport" + i, null, ORG);
         work.add(() -> ReflectionTestUtils.invokeMethod(
            asset, "importScheduleFolder", folder, indexedStorage, (IdentityID) null));
      }

      for(int i = 0; i < tasks; i++) {
         String name = "StmcpImpTask" + i;
         work.add(() -> taskMap().put(key(name), newTask(name)));
      }

      runConcurrently(work);

      taskMap().clearCache();
      assertEquals(names(0, tasks, "StmcpImpTask"), listed(), "every task is listed");
      AssetFolder root = (AssetFolder) indexedStorage
         .getXMLSerializable(rootEntry().toIdentifier(), null, ORG);
      long folderEntries = Arrays.stream(root.getEntries())
         .filter(AssetEntry::isScheduleTaskFolder).count();
      assertEquals(folders, folderEntries, "every imported folder is listed in its parent");
   }

   /**
    * Runs every action on one of a fixed number of threads that are all released together.
    */
   private void runConcurrently(List<Runnable> work) throws Exception {
      CountDownLatch start = new CountDownLatch(1);
      ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>(work);
      List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

      for(int i = 0; i < THREADS; i++) {
         Thread thread = new Thread(() -> {
            ThreadContext.setContextPrincipal(principal());

            try {
               start.await();
               Runnable action;

               while((action = queue.poll()) != null) {
                  action.run();
               }
            }
            catch(Throwable e) {
               failures.add(e);
            }
         }, "stmcp-worker-" + i);
         threads.add(thread);
         thread.start();
      }

      start.countDown();

      for(Thread thread : threads) {
         thread.join(60000);
         assertFalse(thread.isAlive(), "a worker thread did not finish");
      }

      assertTrue(failures.isEmpty(), "a worker failed: " + failures);
   }

   private ScheduleTaskFolderService folderService() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      return new ScheduleTaskFolderService(scheduleManager, securityEngine, null, indexedStorage,
                                           null);
   }

   private AssetEntry rootEntry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            "/", null, ORG);
   }

   private Set<String> listed() {
      // internal tasks are listed for the default organization, only the test's tasks count
      return taskMap().keySet().stream()
         .filter(k -> k.startsWith("Stmcp"))
         .collect(Collectors.toCollection(TreeSet::new));
   }

   private Set<String> names(int from, int to, String prefix) {
      return java.util.stream.IntStream.range(from, to)
         .mapToObj(i -> AssetEntry.createAssetEntry(key(prefix + i), ORG).getName())
         .collect(Collectors.toCollection(TreeSet::new));
   }

   private ScheduleTaskMap taskMap() {
      return scheduleManager.getOrgTaskMap(ORG);
   }

   private String key(String taskId) {
      return ReflectionTestUtils.invokeMethod(scheduleManager, "getTaskIdentifier", taskId, ORG);
   }

   private static ScheduleTask newTask(String name) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(ALICE);
      task.setRemovable(false);
      // a task without a condition isn't loaded (ScheduleTask.parseXML)
      task.addCondition(TimeCondition.at(1, 30, 0));
      return task;
   }

   private static SRPrincipal principal() {
      SRPrincipal principal = new SRPrincipal(ALICE, new IdentityID[0], new String[0], ORG,
                                              Tool.getSecureRandom().nextLong());
      principal.setIgnoreLogin(true);
      return principal;
   }
}
