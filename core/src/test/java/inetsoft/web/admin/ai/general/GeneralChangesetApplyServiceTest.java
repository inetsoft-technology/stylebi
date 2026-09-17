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
package inetsoft.web.admin.ai.general;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.general.model.CacheSettingsModel;
import inetsoft.web.admin.general.model.PerformanceSettingsModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pins the apply gates, the rollback contract, and the advisories rollback does not undo. */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class GeneralChangesetApplyServiceTest {
   @Mock
   private GeneralSettingsAccess access;
   @Mock
   private AdminBackupService backupService;
   private GeneralChangePlanService planService;
   private GeneralChangesetApplyService service;
   private MockedStatic<Tool> tool;
   private MockedStatic<SreeEnv> sreeEnv;

   private static final Principal PRINCIPAL = () -> "admin";
   private static final ObjectMapper MAPPER = new ObjectMapper();

   @BeforeEach
   void setUp() {
      planService = new GeneralChangePlanService(access);
      service = new GeneralChangesetApplyService(planService, access, backupService);
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      // TaskAuditToken.issue/verify round-trip through these two; the real pair needs a Spring
      // context. A reversible stand-in keeps the token contract (issue -> verify recovers the
      // task, a foreign token fails) without one.
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString())).thenAnswer(inv -> {
         String token = inv.getArgument(0);

         if(!token.startsWith("TKN:")) {
            throw new IllegalArgumentException("not a token");
         }

         return token.substring(4);
      });
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      sreeEnv.when(() -> SreeEnv.getProperty("sree.home")).thenReturn("/opt/sree");
   }

   @AfterEach
   void tearDown() {
      tool.close();
      sreeEnv.close();
   }

   private static PerformanceSettingsModel performance(long timeout) {
      return PerformanceSettingsModel.builder()
         .queryTimeout(timeout).queryPreviewTimeout(30).maxQueryRowCount(1000)
         .maxQueryPreviewRowCount(100).maxTableRowCount(500).dataSetCaching(false)
         .dataCacheSize(10).dataCacheTimeout(60).build();
   }

   private static CacheSettingsModel cache(boolean cleanUpStartup) {
      return CacheSettingsModel.builder()
         .directory("$(sree.home)/cache").cleanUpStartup(cleanUpStartup).build();
   }

   private static GeneralChangeRequest change(String subModel, ObjectNode spec) {
      GeneralChangeRequest r = new GeneralChangeRequest();
      r.setVerb("update");
      r.setSubModel(subModel);
      r.setSpec(spec);
      return r;
   }

   private static ObjectNode spec() {
      return MAPPER.createObjectNode();
   }

   /** Builds an apply request already carrying a valid hash and token for the given changes. */
   private GeneralApplyRequest signedRequest(String task, GeneralChangeRequest... changes)
      throws Exception
   {
      GeneralChangePlanRequest planRequest = new GeneralChangePlanRequest();
      planRequest.setTask(task);
      planRequest.setChanges(List.of(changes));
      ResolvedPlan plan = planService.resolve(planRequest, PRINCIPAL);

      GeneralApplyRequest req = new GeneralApplyRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      req.setPlanHash(plan.planHash());
      req.setTaskToken(plan.taskToken());
      req.setReviewOutcome("approved by operator");
      return req;
   }

   @Test
   void staleP1anHashIsAConflictWithNoTaskToken() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      GeneralApplyRequest req =
         signedRequest("t", change("performance", spec().put("queryTimeout", 120)));
      req.setPlanHash("stale");

      AdminChangesetApplyService.PlanHashMismatchException e = assertThrows(
         AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, PRINCIPAL));

      assertNull(e.current().taskToken(), "a 409 must never hand back a usable taskToken");
      verify(backupService, never()).backup(anyString());
   }

   @Test
   void missingPlanHashIsAConflict() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      GeneralApplyRequest req =
         signedRequest("t", change("performance", spec().put("queryTimeout", 120)));
      req.setPlanHash(null);

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                   () -> service.apply(req, PRINCIPAL));
   }

   @Test
   void badTaskTokenIsAConflict() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      GeneralApplyRequest req =
         signedRequest("t", change("performance", spec().put("queryTimeout", 120)));
      req.setTaskToken("nonsense");

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
                   () -> service.apply(req, PRINCIPAL));
      verify(backupService, never()).backup(anyString());
   }

   @Test
   void missingReviewOutcomeIsRefusedBeforeAnyBackup() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      GeneralApplyRequest req =
         signedRequest("t", change("performance", spec().put("queryTimeout", 120)));
      req.setReviewOutcome("  ");

      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.apply(req, PRINCIPAL))
                    .getMessage().contains("reviewOutcome"));
      verify(backupService, never()).backup(anyString());
   }

   @Test
   void successfulApplyBacksUpFirstAndReportsVerified() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any()))
         .thenReturn(performance(60))
         .thenReturn(performance(60))
         .thenReturn(performance(120));
      when(backupService.backup(anyString())).thenReturn("backup-ref");

      GeneralApplyRequest req =
         signedRequest("t", change("performance", spec().put("queryTimeout", 120)));
      GeneralApplyResult result = service.apply(req, PRINCIPAL);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("backup-ref", result.backupRef());
      assertEquals(1, result.results().size());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, result.results().get(0).status());
      assertTrue(result.transactionId().startsWith("gen-"));
      assertNull(result.rollbackFailures());
      verify(backupService).backup(anyString());
   }

   /** A performance write clears the asset data cache, which a rollback cannot refill. */
   @Test
   void performanceOutcomeCarriesTheCacheAdvisory() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any()))
         .thenReturn(performance(60))
         .thenReturn(performance(60))
         .thenReturn(performance(120));
      when(backupService.backup(anyString())).thenReturn("backup-ref");

      GeneralApplyResult result = service.apply(
         signedRequest("t", change("performance", spec().put("queryTimeout", 120))), PRINCIPAL);

      assertNotNull(result.results().get(0).advisory());
      assertTrue(result.results().get(0).advisory().contains("cache"),
                 result.results().get(0).advisory());
   }

   @Test
   void cacheSubModelCarriesNoAdvisory() throws Exception {
      when(access.read(eq(GeneralSubModel.CACHE), any()))
         .thenReturn(cache(false))
         .thenReturn(cache(false))
         .thenReturn(cache(true));
      when(backupService.backup(anyString())).thenReturn("backup-ref");

      GeneralApplyResult result = service.apply(
         signedRequest("t", change("cache", spec().put("cleanUpStartup", true))), PRINCIPAL);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNull(result.results().get(0).advisory());
   }

   /**
    * A write that returns but does not read back as written must roll the value back -- not be
    * silently dropped from the undo list as if nothing had happened (bug #76729's mislabel).
    */
   @Test
   void unverifiedWriteIsRolledBack() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any()))
         .thenReturn(performance(60))
         .thenReturn(performance(60))
         .thenReturn(performance(60))   // read-back after write: unchanged -> not verified
         .thenReturn(performance(60));  // read-back after rollback: restored
      when(backupService.backup(anyString())).thenReturn("backup-ref");

      GeneralApplyResult result = service.apply(
         signedRequest("t", change("performance", spec().put("queryTimeout", 120))), PRINCIPAL);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      // written once forward, once back
      verify(access, times(2)).write(eq(GeneralSubModel.PERFORMANCE), any(), any());
   }

   /** A write that throws having changed nothing needs no rollback, so the batch is clean. */
   @Test
   void failedWriteThatMovedNothingIsNotTreatedAsAppliedNorLeftUnknown() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      when(backupService.backup(anyString())).thenReturn("backup-ref");
      doThrow(new IllegalStateException("refused by the service"))
         .when(access).write(eq(GeneralSubModel.PERFORMANCE), any(), any());

      GeneralApplyResult result = service.apply(
         signedRequest("t", change("performance", spec().put("queryTimeout", 120))), PRINCIPAL);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      assertEquals(AdminChangeRecord.STATUS_FAILED, result.results().get(0).status());
      assertTrue(result.results().get(0).error().contains("refused by the service"));
   }

   /**
    * A failed {@code mv} write must report unknown state, never a clean rollback.
    *
    * <p>{@code MVSettingsService.setModel} calls {@code dataCycleManager.setDefaultCycle()} and
    * {@code save()} BEFORE {@code mvManager.setDefaultCycle()}, while the read-back reports
    * {@code mvManager}'s value -- so a throw from {@code save()} leaves the cycle manager holding
    * the new default although the read still shows the old one. The re-read therefore proves
    * nothing for this sub-model, and reporting "nothing moved" would claim a clean rollback over a
    * deployment that did change. This is the {@code readBackObservesEveryWrite() == false} branch;
    * {@link #failedWriteThatMovedNothingIsNotTreatedAsAppliedNorLeftUnknown} is the same failure
    * on a sub-model where the re-read IS conclusive, and the two must not report alike.
    */
   @Test
   void failedMvWriteReportsUnknownStateRatherThanACleanRollback() throws Exception {
      inetsoft.web.admin.general.model.MVSettingsModel before =
         inetsoft.web.admin.general.model.MVSettingsModel.builder()
            .onDemand(false).onDemandDefault(false).metadata(false).required(false).build();

      // The read-back returns the pre-apply value, exactly as it would when save() threw after
      // dataCycleManager had already been updated -- the case a naive "nothing moved" check gets
      // wrong.
      when(access.read(eq(GeneralSubModel.MV), any())).thenReturn(before);
      when(backupService.backup(anyString())).thenReturn("backup-ref");
      doThrow(new IllegalStateException("cycle registry save failed"))
         .when(access).write(eq(GeneralSubModel.MV), any(), any());

      GeneralApplyResult result = service.apply(
         signedRequest("t", change("mv", spec().put("metadata", true))), PRINCIPAL);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status(),
                   "an unobservable failed write must never be reported as rolled back");
      assertFalse(GeneralSubModel.MV.readBackObservesEveryWrite(),
                  "this test is about the sub-model whose read cannot observe every write");
      assertNotNull(result.rollbackFailures());
      assertEquals(1, result.rollbackFailures().size());
      assertEquals("mv", result.rollbackFailures().get(0).property());
      assertTrue(result.rollbackFailures().get(0).error().startsWith("state unknown:"),
                 result.rollbackFailures().get(0).error());
      assertTrue(result.rollbackFailures().get(0).error().contains("cycle registry save failed"));
      // Nothing was written, so nothing may be written back -- a compensating write here would be
      // a second mutation on a deployment whose state is already unknown.
      verify(access, times(1)).write(eq(GeneralSubModel.MV), any(), any());
   }

   /**
    * The storage-scope sub-models must roll back like any other. If someone re-couples storage
    * scope to non-compensable (as the presentation area does), this starts reporting a successful
    * restore as a rollback failure.
    */
   @Test
   void storageScopeSubModelRollsBackInsteadOfReportingFailure() throws Exception {
      inetsoft.web.admin.general.model.MVSettingsModel before =
         inetsoft.web.admin.general.model.MVSettingsModel.builder()
            .onDemand(false).onDemandDefault(false).metadata(false).required(false).build();

      when(access.read(eq(GeneralSubModel.MV), any()))
         .thenReturn(before)
         .thenReturn(before)
         .thenReturn(before)   // read-back after write: unchanged -> not verified
         .thenReturn(before);  // read-back after rollback: restored
      when(backupService.backup(anyString())).thenReturn("backup-ref");

      GeneralApplyResult result = service.apply(
         signedRequest("t", change("mv", spec().put("metadata", true))), PRINCIPAL);

      assertTrue(GeneralSubModel.MV.isStorageScope());
      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status(),
                   "a storage-scope sub-model in this area is still compensable");
      assertNull(result.rollbackFailures());
   }

   /** A rollback that itself fails must be reported, never swallowed into a clean status. */
   @Test
   void failedRollbackIsReported() throws Exception {
      when(access.read(eq(GeneralSubModel.PERFORMANCE), any())).thenReturn(performance(60));
      when(backupService.backup(anyString())).thenReturn("backup-ref");
      doNothing().doThrow(new IllegalStateException("cannot restore"))
         .when(access).write(eq(GeneralSubModel.PERFORMANCE), any(), any());

      GeneralApplyResult result = service.apply(
         signedRequest("t", change("performance", spec().put("queryTimeout", 120))), PRINCIPAL);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertEquals("performance", result.rollbackFailures().get(0).property());
   }
}
