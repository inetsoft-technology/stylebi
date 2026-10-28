/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.asset.sync;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bugs #79519 and #79521: the rename transform reports whether it finished, a task stays in the
 * queue only if it did not finish because the node is stopping, and a replayed task is skipped
 * only if the asset with its old name exists again.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  RenameTransformQueueReplayTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RenameTransformCompletionTest {
   @BeforeEach
   void setUp() throws Exception {
      cluster().awaitTasks();
      cluster().resetSubmissions();
      repository = mock(AssetRepository.class);
      AssetUtil.setAssetRepository(false, repository);
   }

   @AfterEach
   void tearDown() throws Exception {
      release.countDown();
      cluster().awaitTasks();
      AssetUtil.setAssetRepository(false, null);
      dss.awaitQueueOpen();
   }

   // ---- DependencyTransformer.renameDep reports completion ----

   @Test
   void renameDepIsCompleteWhenEveryDependentIsTransformed() throws Exception {
      doReturn(mock(IndexedStorage.class)).when(repository).getStorage(any());

      assertTrue(DependencyTransformer.renameDep(newTask(3)));
      verify(repository, times(3)).getStorage(any());
   }

   @Test
   void renameDepIsCompleteForATaskWithoutDependents() {
      assertTrue(DependencyTransformer.renameDep(newTask(0)));
   }

   /**
    * Storage closed by a stopping server makes each dependent fail fast, so the transform threads
    * end normally with no interrupt; the failures must still make the transform incomplete.
    */
   @Test
   void renameDepIsIncompleteWhenADependentFails() throws Exception {
      doReturn(mock(IndexedStorage.class)).when(repository).getStorage(any());
      doThrow(new IllegalStateException("storage closed"))
         .when(repository).getStorage(argThat(e -> e != null && "Dependent1".equals(e.getPath())));

      assertFalse(DependencyTransformer.renameDep(newTask(3)));
   }

   @Test
   void renameDepWithoutThreadsReportsAFailedDependent() throws Exception {
      doReturn(mock(IndexedStorage.class)).when(repository).getStorage(any());
      assertTrue(DependencyTransformer.renameDep(newTask(2), false));

      doThrow(new IllegalStateException("storage closed")).when(repository).getStorage(any());
      assertFalse(DependencyTransformer.renameDep(newTask(2), false));
   }

   @Test
   void renameDepIsIncompleteWhenInterruptedAndRestoresTheInterrupt() throws Exception {
      CountDownLatch entered = new CountDownLatch(1);
      AtomicBoolean workerInterrupted = new AtomicBoolean();
      doAnswer(inv -> {
         entered.countDown();

         try {
            release.await(30, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            workerInterrupted.set(true);
            throw e;
         }

         return mock(IndexedStorage.class);
      }).when(repository).getStorage(any());
      CompletableFuture<Boolean> result = new CompletableFuture<>();
      AtomicBoolean interruptRestored = new AtomicBoolean();
      Thread service = new Thread(() -> {
         result.complete(DependencyTransformer.renameDep(newTask(1)));
         interruptRestored.set(Thread.currentThread().isInterrupted());
      });
      service.start();

      try {
         assertTrue(entered.await(10, TimeUnit.SECONDS));
         service.interrupt();
         assertFalse(result.get(20, TimeUnit.SECONDS), "an interrupted transform is incomplete");
      }
      finally {
         service.join(20000L);
      }

      assertTrue(interruptRestored.get(), "the interrupt is restored");
      assertTrue(workerInterrupted.get(), "the transform threads are stopped");
   }

   @Test
   void renameDepIsIncompleteWhenItTimesOut() throws Exception {
      CountDownLatch entered = new CountDownLatch(1);
      doAnswer(inv -> {
         entered.countDown();
         release.await(30, TimeUnit.SECONDS);
         return mock(IndexedStorage.class);
      }).when(repository).getStorage(any());

      assertFalse(DependencyTransformer.renameDep(newTask(1), true, 200L, TimeUnit.MILLISECONDS));
      assertTrue(entered.await(10, TimeUnit.SECONDS));
      assertFalse(Thread.currentThread().isInterrupted());
   }

   // ---- Rename.run dequeues only a finished or skipped task, or on a running node ----

   @Test
   void unfinishedTaskOnAStoppingNodeIsNotDequeued() throws Exception {
      new RenameTransformTask.Rename(newTask(1)).run(i -> false, () -> true);
      assertEquals(0, removeSubmissions());
   }

   @Test
   void failingTaskOnAStoppingNodeIsNotDequeued() throws Exception {
      RenameTransformTask.Rename rename = new RenameTransformTask.Rename(newTask(1));

      assertThrows(IllegalStateException.class, () -> rename.run(i -> {
         throw new IllegalStateException("storage closed");
      }, () -> true));
      assertEquals(0, removeSubmissions());
   }

   @Test
   void finishedTaskIsDequeued() throws Exception {
      new RenameTransformTask.Rename(newTask(1)).run(i -> true, () -> true);
      assertEquals(1, removeSubmissions());
   }

   /**
    * On a running node an unfinished task is dequeued as before, so that it doesn't wait for the
    * next cluster start or keep waitUntilRenameFinished() waiting.
    */
   @Test
   void unfinishedTaskOnARunningNodeIsDequeued() throws Exception {
      new RenameTransformTask.Rename(newTask(1)).run(i -> false, () -> false);
      assertEquals(1, removeSubmissions());
   }

   @Test
   void staleReplayIsSkippedAndDequeued() throws Exception {
      doReturn(true).when(repository).containsEntry(any());
      AtomicBoolean transformed = new AtomicBoolean();

      new RenameTransformTask.Rename(newTask(1), true).run(i -> {
         transformed.set(true);
         return true;
      }, () -> true);

      assertFalse(transformed.get(), "the stale task is not transformed");
      assertEquals(1, removeSubmissions(), "a deliberate skip is dequeued");
   }

   @Test
   void notStoppingCheckUsesTheThreadAndTheContext() throws Exception {
      assertFalse(RenameTransformTask.isNodeStopping(), "the test context is running");

      CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
      Thread thread = new Thread(() -> {
         Thread.currentThread().interrupt();
         interrupted.complete(RenameTransformTask.isNodeStopping());
      });
      thread.start();
      assertTrue(interrupted.get(10, TimeUnit.SECONDS), "an interrupted service thread");
      thread.join(10000L);
   }

   // ---- the replay staleness check ----

   @Test
   void renameIsStaleWhenTheOldAssetExists() throws Exception {
      String oldName = wsId("cf-ws", ORG);
      RenameDependencyInfo task = newTask(rename(oldName, wsId("x", ORG), null));

      assertEquals(oldName, RenameTransformTask.getStaleRename(
         task, e -> "cf-ws".equals(e.getPath()) && ORG.equals(e.getOrgID())));
      assertNull(RenameTransformTask.getStaleRename(task, e -> "x".equals(e.getPath())),
                 "a missing new asset is no reason to skip");
   }

   @Test
   void renameIsAppliedWhenTheLookupFails() throws Exception {
      RenameDependencyInfo task = newTask(rename(wsId("cf-ws", ORG), wsId("x", ORG), null));

      assertNull(RenameTransformTask.getStaleRename(task, e -> {
         throw new IllegalStateException("not ready");
      }));
   }

   @Test
   void sameOrCaseOnlyRenameIsNeverStale() throws Exception {
      assertNull(RenameTransformTask.getStaleRename(
         newTask(rename(wsId("cf-ws", ORG), wsId("CF-WS", ORG), null)), e -> true));
      assertNull(RenameTransformTask.getStaleRename(
         newTask(rename(wsId("cf-ws", ORG), wsId("cf-ws", ORG), null)), e -> true));
   }

   @Test
   void onlyWholeRepositoryAssetsAreJudged() throws Exception {
      // a worksheet column, a data source, and a query-scope identifier can't be judged
      RenameInfo column = new RenameInfo("col1", "col2", RenameInfo.ASSET | RenameInfo.COLUMN,
                                         wsId("cf-ws", ORG));
      RenameInfo source = new RenameInfo("orders", "orders2",
                                         RenameInfo.QUERY | RenameInfo.DATA_SOURCE);
      String queryOld = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                       "orders", null, ORG).toIdentifier();
      String queryNew = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                       "orders2", null, ORG).toIdentifier();
      RenameInfo query = new RenameInfo(queryOld, queryNew,
                                        RenameInfo.SQL_TABLE | RenameInfo.DATA_SOURCE);

      assertNull(RenameTransformTask.getStaleRename(newTask(column), e -> true));
      assertNull(RenameTransformTask.getStaleRename(newTask(source), e -> true));
      assertNull(RenameTransformTask.getStaleRename(newTask(query), e -> true));
   }

   @Test
   void renameIsCheckedInItsOwnOrganization() throws Exception {
      String org = "r79521-org";
      // the old name exists only in the default organization
      RenameDependencyInfo task = newTask(rename(wsId("cf-ws", org), wsId("x", org), org));
      assertNull(RenameTransformTask.getStaleRename(task, e -> ORG.equals(e.getOrgID())));

      // and in the task's organization
      List<AssetEntry> looked = new ArrayList<>();
      assertNotNull(RenameTransformTask.getStaleRename(task, e -> looked.add(e)));
      assertEquals(org, looked.get(0).getOrgID());

      // an identifier without the organization takes the rename's
      String oldName = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                      "cf-ws", null, org).toIdentifier();
      oldName = oldName.substring(0, oldName.lastIndexOf('^'));
      String newName = oldName.replace("cf-ws", "x");
      looked.clear();
      assertNotNull(RenameTransformTask.getStaleRename(newTask(rename(oldName, newName, org)),
                                                       e -> looked.add(e)));
      assertEquals(org, looked.get(0).getOrgID());
   }

   private RenameDependencyInfo newTask(int dependents) {
      RenameInfo rinfo = new RenameInfo(wsId("OldWS", ORG), wsId("NewWS", ORG),
                                        RenameInfo.ASSET | RenameInfo.SOURCE);
      RenameDependencyInfo dinfo = newTask(rinfo);

      for(int i = 0; i < dependents; i++) {
         dinfo.setRenameInfo(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                            AssetEntry.Type.VIEWSHEET, "Dependent" + i, null, ORG),
                             dinfo.getRenameInfos());
      }

      return dinfo;
   }

   private static RenameDependencyInfo newTask(RenameInfo rinfo) {
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      dinfo.setRenameInfos(new ArrayList<>(List.of(rinfo)));
      return dinfo;
   }

   /**
    * Creates a worksheet rename, of the given organization if not {@code null}.
    */
   private static RenameInfo rename(String oldName, String newName, String org) throws Exception {
      RenameInfo rinfo = new RenameInfo(oldName, newName, RenameInfo.ASSET | RenameInfo.SOURCE);

      if(org == null) {
         return rinfo;
      }

      StringWriter xml = new StringWriter();
      PrintWriter writer = new PrintWriter(xml);
      rinfo.writeXML(writer);
      writer.flush();
      Document doc = Tool.parseXML(new StringReader(xml.toString().replace(
         "organizationId=\"" + rinfo.getOrganizationId() + "\"", "organizationId=\"" + org + "\"")));
      rinfo = new RenameInfo();
      rinfo.parseXML(doc.getDocumentElement());
      assertEquals(org, rinfo.getOrganizationId());
      return rinfo;
   }

   private static String wsId(String name, String org) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null,
                            org).toIdentifier();
   }

   private long removeSubmissions() throws Exception {
      cluster().awaitTasks();
      return cluster().submissions.stream()
         .filter(s -> s.equals("dependencyStorage:Remove"))
         .count();
   }

   private RenameTransformQueueReplayTest.RecordingCluster cluster() {
      return (RenameTransformQueueReplayTest.RecordingCluster) clusterBean;
   }

   @Autowired
   private Cluster clusterBean;
   @Autowired
   private DependencyStorageService dss;
   // held here: the configuration context keeps only a weak reference
   private AssetRepository repository;
   private final CountDownLatch release = new CountDownLatch(1);

   private static final String ORG = Organization.getDefaultOrganizationID();
}
