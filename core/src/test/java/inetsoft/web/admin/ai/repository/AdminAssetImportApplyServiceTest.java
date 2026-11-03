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
package inetsoft.web.admin.ai.repository;

import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.content.repository.ImportAssetServiceProxy;
import inetsoft.web.admin.content.repository.model.ExportedAssetsModel;
import inetsoft.web.admin.content.repository.model.ImportAssetResponse;
import org.junit.jupiter.api.*;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80093: apply took the Tier-2 snapshot unconditionally and reported "applied" as soon as
 * the import was handed to a background job, before it ran.
 */
@Tag("core")
class AdminAssetImportApplyServiceTest {
   private static final String STAGING = "tok";

   private RepositoryImportChangePlanService planService;
   private ImportAssetServiceProxy importService;
   private AdminBackupService backupService;
   private Principal user;
   private AtomicLong now;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<Tool> toolStatic;
   private Audit audit;

   @BeforeEach
   void setUp() throws Exception {
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      audit = mock(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);
      toolStatic = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      toolStatic.when(Tool::getHost).thenReturn("host");
      toolStatic.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      toolStatic.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> ((String) inv.getArgument(0)).substring(4));

      planService = mock(RepositoryImportChangePlanService.class);
      importService = mock(ImportAssetServiceProxy.class);
      backupService = mock(AdminBackupService.class);
      user = mock(Principal.class);
      now = new AtomicLong(1_000L);

      when(backupService.backup(anyString())).thenReturn("snapshot-ref");
      String token = TaskAuditToken.issue("hash", "t");
      when(planService.resolve(any(), any())).thenReturn(new RepositoryImportPlan(
         STAGING, ExportedAssetsModel.builder().selectedEntities(List.of())
            .dependentAssets(List.of()).build(),
         List.of(), List.of(), List.of(), false, true, true, "hash",
         token));
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
      toolStatic.close();
   }

   private AdminAssetImportApplyService service() {
      return new AdminAssetImportApplyService(planService, importService, backupService,
         now::get, ms -> now.addAndGet(ms), 2_000L, 500L);
   }

   private RepositoryImportApplyRequest request(boolean overwrite) {
      RepositoryImportApplyRequest req = new RepositoryImportApplyRequest();
      req.setTask("t");
      req.setStagingToken(STAGING);
      req.setPlanHash("hash");
      req.setTaskToken(TaskAuditToken.issue("hash", "t"));
      req.setReviewOutcome("ok");
      req.setOverwrite(overwrite);
      return req;
   }

   private static ImportAssetResponse queued() {
      return ImportAssetResponse.builder().complete(false).build();
   }

   private void importReturns(boolean background, ImportAssetResponse first,
                              ImportAssetResponse... more) throws Exception
   {
      when(importService.importAsset(eq(STAGING), any(), any(), any(), any(), any(), anyBoolean(),
                                     eq(background), any(), any()))
         .thenReturn(first, more);
   }

   @Test
   void additiveImportSkipsTheSnapshot() throws Exception {
      importReturns(false, ImportAssetResponse.builder().build());

      RepositoryImportApplyResult result = service().apply(request(false), user);

      verify(backupService, never()).backup(anyString());
      assertNull(result.backupRef());
      assertEquals("applied", result.status());
      assertNotNull(result.transactionId());
   }

   @Test
   void overwriteImportTakesTheSnapshotOnce() throws Exception {
      importReturns(false, ImportAssetResponse.builder().build());

      RepositoryImportApplyResult result = service().apply(request(true), user);

      verify(backupService, times(1)).backup(anyString());
      assertEquals("snapshot-ref", result.backupRef());
   }

   @Test
   void appliedOnlyAfterThePollReportsComplete() throws Exception {
      importReturns(false, queued());
      importReturns(true, queued(), ImportAssetResponse.builder()
         .addWarnings("w1").build());

      RepositoryImportApplyResult result = service().apply(request(false), user);

      assertEquals("applied", result.status());
      assertEquals(List.of("w1"), result.warnings());
      verify(importService, times(2)).importAsset(eq(STAGING), any(), any(), any(), any(), any(),
         anyBoolean(), eq(true), any(), any());
   }

   @Test
   void jobFailureOnThePollIsReportedAsFailed() throws Exception {
      importReturns(false, queued());
      when(importService.importAsset(eq(STAGING), any(), any(), any(), any(), any(), anyBoolean(),
                                     eq(true), any(), any()))
         .thenThrow(new ExecutionException(new IllegalStateException("disk full")));

      RepositoryImportApplyResult result = service().apply(request(false), user);

      assertEquals("failed", result.status());
      assertTrue(result.failedAssets().get(0).contains("disk full"), result.failedAssets().toString());
   }

   @Test
   void neverCompletingImportIsInProgressNotApplied() throws Exception {
      importReturns(false, queued());
      importReturns(true, queued());

      RepositoryImportApplyResult result = service().apply(request(false), user);

      assertEquals("in-progress", result.status());
      assertNotNull(result.transactionId());
      org.mockito.ArgumentCaptor<inetsoft.util.audit.AdminChangeRecord> rec =
         org.mockito.ArgumentCaptor.forClass(inetsoft.util.audit.AdminChangeRecord.class);
      verify(audit).auditAdminChange(rec.capture(), any());
      assertEquals("in-progress", rec.getValue().getStatus());
   }

   @Test
   void pollForAnUnknownJobIsInProgressNotFailed() throws Exception {
      importReturns(false, queued());
      importReturns(true, ImportAssetResponse.builder().failed(true).build());

      RepositoryImportApplyResult result = service().apply(request(false), user);

      assertEquals("in-progress", result.status());
      assertTrue(result.failedAssets().isEmpty());
   }

   @Test
   void completedImportWithFailedAssetsIsFailed() throws Exception {
      importReturns(false, queued());
      importReturns(true, ImportAssetResponse.builder().failed(true)
         .addFailedAssets("ws1").build());

      RepositoryImportApplyResult result = service().apply(request(false), user);

      assertEquals("failed", result.status());
      assertEquals(List.of("ws1"), result.failedAssets());
   }

   @Test
   void clusterWrappedJobFailureIsFailedAndAudited() throws Exception {
      importReturns(false, queued());
      when(importService.importAsset(eq(STAGING), any(), any(), any(), any(), any(), anyBoolean(),
                                     eq(true), any(), any()))
         .thenThrow(new RuntimeException(new ExecutionException(new IllegalStateException("boom"))));

      RepositoryImportApplyResult result = service().apply(request(false), user);

      assertEquals("failed", result.status());
      assertEquals(List.of("import job failed: boom"), result.failedAssets());
      org.mockito.ArgumentCaptor<inetsoft.util.audit.AdminChangeRecord> rec =
         org.mockito.ArgumentCaptor.forClass(inetsoft.util.audit.AdminChangeRecord.class);
      verify(audit).auditAdminChange(rec.capture(), any());
      assertEquals("failed", rec.getValue().getStatus());
      assertNull(rec.getValue().getSnapshotScope());
   }

   @Test
   void interruptedPollIsInProgressAndKeepsTheInterruptFlag() throws Exception {
      importReturns(false, queued());
      AdminAssetImportApplyService svc = new AdminAssetImportApplyService(planService,
         importService, backupService, now::get, ms -> { throw new InterruptedException(); },
         2_000L, 500L);

      try {
         RepositoryImportApplyResult result = svc.apply(request(false), user);
         assertEquals("in-progress", result.status());
         assertTrue(Thread.currentThread().isInterrupted());
         verify(audit).auditAdminChange(any(), any());
      }
      finally {
         Thread.interrupted();
      }
   }

   @Test
   void secondApplyWhileAnotherHoldsTheLockGetsApplyInProgressWithoutSideEffects()
      throws Exception
   {
      // static mocks are thread-local, so another thread holds the apply lock directly
      java.lang.reflect.Field field = AdminAssetImportApplyService.class
         .getDeclaredField("APPLY_LOCK");
      field.setAccessible(true);
      java.util.concurrent.locks.ReentrantLock lock =
         (java.util.concurrent.locks.ReentrantLock) field.get(null);
      java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
      Thread holder = new Thread(() -> {
         lock.lock();

         try {
            held.countDown();
            release.await();
         }
         catch(InterruptedException ignore) {
         }
         finally {
            lock.unlock();
         }
      });
      holder.start();

      try {
         assertTrue(held.await(10, java.util.concurrent.TimeUnit.SECONDS));
         // real clock and a short deadline so the lock wait (a quarter of it) is ~50 ms
         AdminAssetImportApplyService second = new AdminAssetImportApplyService(planService,
            importService, backupService, System::currentTimeMillis, Thread::sleep, 200L, 50L);
         assertThrows(AdminAssetImportApplyService.ApplyInProgressException.class,
                      () -> second.apply(request(true), user));
         verifyNoInteractions(planService, backupService, importService);
      }
      finally {
         release.countDown();
         holder.join(10_000);
      }
   }
}
