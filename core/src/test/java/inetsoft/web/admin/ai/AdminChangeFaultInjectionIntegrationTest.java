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
package inetsoft.web.admin.ai;

import inetsoft.sree.SreeEnv;
import inetsoft.util.audit.*;
import inetsoft.web.admin.properties.PropertyChangeSideEffects;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Wires the REAL {@link AdminChangeService} - not a mock - into a real
 * {@link AdminChangesetApplyService}, through the test-only fault-injection hook, to prove what
 * {@code AdminChangesetApplyServiceTest} cannot: that the compensating-transaction paths
 * ({@code rolled-back}, {@code rollback-failed}, and the "fails by throwing" branch specifically)
 * are reachable through the ACTUAL apply implementation, not only through a mock configured to
 * throw. See {@code docs/teams/2026-08-26-a1-fault-injection/01-design.md} (stylebi-wiz repo) for
 * the full design and why this distinction matters: before this hook existed, nothing in the
 * codebase could make the real {@code AdminChangeService.applyChange} throw, so
 * {@code AdminChangesetApplyService.apply}'s {@code catch(Exception e)} branch around it had never
 * executed outside a mocked unit test.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminChangeFaultInjectionIntegrationTest {
   private static final String FAULT_INJECTION_ENABLED_PROPERTY =
      "inetsoft.admin.ai.faultInjection.enabled";

   @Mock private AdminBackupService backupService;
   @Mock private Principal principal;
   @Mock private PropertyChangeSideEffects sideEffects;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Audit> auditStatic;
   private AdminChangesetApplyService service;
   private AdminChangePlanService planService;
   /**
    * A tiny in-memory fake behind the static {@code SreeEnv} mock, keyed by property. Needed
    * because, unlike {@code AdminChangesetApplyServiceTest} (which mocks {@code AdminChangeService}
    * itself and so never actually calls {@code SreeEnv}), this class wires in the REAL
    * {@code AdminChangeService} - so {@code SreeEnv.getProperty} is read for real, more than once
    * per property (once by {@code request()}'s own {@code planService.resolve} call to compute the
    * hash, again by {@code AdminChangesetApplyService.apply}'s internal re-resolve, again by
    * {@code AdminChangeService.applyChange}'s own before/after reads) - a fixed
    * {@code .thenReturn(a).thenReturn(b)} sequence drifts out of step with that and produces a
    * spurious {@code PlanHashMismatchException}. A stateful fake keeps every read consistent with
    * the writes that actually happened, the same as the real property store would.
    */
   private final Map<String, String> store = new HashMap<>();

   @BeforeEach void setUp() {
      System.setProperty(FAULT_INJECTION_ENABLED_PROPERTY, "true");
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      sreeEnv.when(() -> SreeEnv.getProperty(anyString(), eq(false), eq(false)))
             .thenAnswer(inv -> store.get(inv.getArgument(0)));
      sreeEnv.when(() -> SreeEnv.setProperty(anyString(), anyString()))
             .thenAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)));
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      AdminPropertyCatalog catalog = new AdminPropertyCatalog();
      planService = new AdminChangePlanService(catalog, new AdminRiskClassifier(catalog));
      // The REAL AdminChangeService - the whole point of this test class.
      AdminChangeService realChangeService = new AdminChangeService(sideEffects);
      service = new AdminChangesetApplyService(planService, realChangeService, backupService);
   }

   @AfterEach void tearDown() {
      sreeEnv.close();
      auditStatic.close();
      System.clearProperty(FAULT_INJECTION_ENABLED_PROPERTY);
   }

   /** Both probe properties are uncatalogued, so every plan below needs signoff + a backup. */
   private ApplyRequest request(String task, String... propertyValuePairs) {
      List<PlanRequest.Change> changes = new ArrayList<>();

      for(int i = 0; i < propertyValuePairs.length; i += 2) {
         PlanRequest.Change change = new PlanRequest.Change();
         change.setProperty(propertyValuePairs[i]);
         change.setValue(propertyValuePairs[i + 1]);
         changes.add(change);
      }

      ApplyRequest req = new ApplyRequest();
      req.setTask(task);
      req.setChanges(changes);
      req.setReviewOutcome("approved");
      PlanRequest probe = new PlanRequest();
      probe.setTask(task);
      probe.setChanges(changes);
      req.setPlanHash(planService.resolve(probe).planHash());
      return req;
   }

   @Test void anUncataloguedPropertyRequiresBackupAndSignoff() {
      // Closes plugin/admin/README.md gap #1's first two clauses: no code change needed, an
      // uncatalogued property already produces requiresStorageBackup + requiresAgentSignoff.
      PlanRequest req = new PlanRequest();
      req.setTask("t");
      PlanRequest.Change change = new PlanRequest.Change();
      change.setProperty("admin.chat.livecheck.probe");
      change.setValue("1");
      req.setChanges(List.of(change));

      ResolvedPlan plan = planService.resolve(req);

      assertTrue(plan.requiresStorageBackup());
      assertTrue(plan.requiresAgentSignoff());
      assertFalse(plan.changes().get(0).recognized());
   }

   @Test void rolledBack_softFailureRollsBackTheEarlierRealChangeCleanly() throws Exception {
      when(backupService.backup(anyString())).thenReturn("snap-1");
      // demo.livecheck.r1a does NOT match the fault-injection pattern at all - it is an ordinary
      // (uncatalogued, but otherwise real) property, included to prove its rollback, triggered by
      // r1b's fault, is a genuine SreeEnv round trip through the real AdminChangeService.
      store.put("demo.livecheck.r1a", "before");

      ApplyResult applied = service.apply(request("t",
         "demo.livecheck.r1a", "after",
         "test.faultinjection.apply.fail.r1b", "x"), principal);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, applied.status());
      assertNull(applied.rollbackFailures());
      sreeEnv.verify(() -> SreeEnv.setProperty("demo.livecheck.r1a", "after"));
      // The undo restores the real before-value the server itself recorded during the apply -
      // and the fake store ends up back where it started, the same as a real property would.
      sreeEnv.verify(() -> SreeEnv.setProperty("demo.livecheck.r1a", "before"));
      assertEquals("before", store.get("demo.livecheck.r1a"));
   }

   @Test void rollbackFailed_throwOnApplyIsCaughtByTheRealCatchBlock() throws Exception {
      // The simple case: a throw-mode probe as the ONLY change. Proves the previously-dead
      // catch(Exception e) branch in AdminChangesetApplyService.apply now executes for real.
      when(backupService.backup(anyString())).thenReturn("snap-2");

      ApplyResult applied = service.apply(request("t",
         "test.faultinjection.apply.throw.r2", "x"), principal);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, applied.status());
      assertEquals(1, applied.rollbackFailures().size());
      assertEquals("test.faultinjection.apply.throw.r2",
                   applied.rollbackFailures().get(0).property());
      assertTrue(applied.rollbackFailures().get(0).error().contains("state unknown"));
      // Plan resolution legitimately reads the current value to compute the hash - the fault
      // fires only inside applyChange, after resolution - so only a read, never a write, happens.
      sreeEnv.verify(() -> SreeEnv.setProperty(anyString(), anyString()), never());
      assertNull(store.get("test.faultinjection.apply.throw.r2"));
   }

   @Test
   void rollbackFailed_namesTheItemWhoseOwnRollbackFailedNotTheItemThatTriggeredIt() throws Exception {
      // The operationally realistic case (design doc §2.3 scenario 3): the item that fails to
      // roll back (r3) is NOT the item whose apply failure triggered the rollback (r4) - the
      // harder shape AdminChangesetApplyService's own javadoc discusses.
      when(backupService.backup(anyString())).thenReturn("snap-3");
      store.put("test.faultinjection.rollback.throw.r3", "before");

      ApplyResult applied = service.apply(request("t",
         "test.faultinjection.rollback.throw.r3", "after",
         "test.faultinjection.apply.fail.r4", "x"), principal);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, applied.status());
      assertEquals(1, applied.rollbackFailures().size());
      assertEquals("test.faultinjection.rollback.throw.r3",
                   applied.rollbackFailures().get(0).property());
      // r3 really did apply for real through the actual AdminChangeService before its rollback
      // failed - this is the write the operator would be left with, unresolved and unreverted.
      sreeEnv.verify(
         () -> SreeEnv.setProperty("test.faultinjection.rollback.throw.r3", "after"));
      assertEquals("after", store.get("test.faultinjection.rollback.throw.r3"));
      verify(backupService).backup(applied.transactionId());
   }
}
