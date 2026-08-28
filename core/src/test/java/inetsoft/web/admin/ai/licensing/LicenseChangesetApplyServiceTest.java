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
package inetsoft.web.admin.ai.licensing;

import inetsoft.report.internal.license.License;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.internal.license.LicenseType;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.general.LicenseKeySettingsService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 01-spec.md section 6 (apply/rollback per verb), section 7 (the Tier-2 backup, unconditional,
 * taken synchronously before any mutation), section 5/6 ({@code acknowledgeDelicensing} advisory
 * gate). {@code LicenseKeySettingsService} is mocked here (its own
 * cluster-broadcast/auth-cache-reset side-effect ordering is covered directly by
 * {@code LicenseKeySettingsServiceTest}); its mocked {@code addServerKey}/{@code removeServerKey}
 * mutate a small in-memory installed-license set so verification/rollback assertions reflect real
 * state transitions, matching {@code ProviderChangesetApplyServiceTest}'s own in-memory fake chain.
 *
 * <p>Mid-apply failures are injected by having a mocked {@code addServerKey}/{@code removeServerKey}
 * call throw -- never by making a key resolve as invalid, because the top-of-{@code apply} fresh
 * {@code resolve()} call would already refuse an invalid key before the loop is ever reached (the
 * per-entry re-check inside {@code applyAdd} only ever matters for the narrower race between that
 * fresh resolve and this specific entry's own turn in the loop, which a throwing mock reproduces
 * without fighting the resolve-time validation).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class LicenseChangesetApplyServiceTest {
   @Mock private LicenseManager licenseManager;
   @Mock private LicenseKeySettingsService licenseKeySettingsService;
   @Mock private AdminBackupService backupService;
   @Mock private XPrincipal user;

   private final Set<License> installed = new LinkedHashSet<>();
   private LicenseChangePlanService planService;
   private LicenseChangesetApplyService service;

   @BeforeEach
   void setUp() throws Exception {
      planService = new LicenseChangePlanService(licenseManager);
      service = new LicenseChangesetApplyService(planService, licenseManager,
                                                 licenseKeySettingsService, backupService);

      lenient().when(licenseManager.getInstalledLicenses())
         .thenAnswer(inv -> new LinkedHashSet<>(installed));
      lenient().when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");

      lenient().doAnswer(inv -> {
         String key = inv.getArgument(0);
         installed.add(licenseManager.parseLicense(key));
         return null;
      }).when(licenseKeySettingsService).addServerKey(anyString());

      lenient().doAnswer(inv -> {
         String key = inv.getArgument(0);
         installed.removeIf(l -> Objects.equals(l.key(), key));
         return null;
      }).when(licenseKeySettingsService).removeServerKey(anyString());
   }

   private static License license(String key, LicenseType type, LocalDateTime expires) {
      return License.builder().key(key).type(type).expires(expires).build();
   }

   private static License valid(String key) {
      return license(key, LicenseType.CPU, LocalDateTime.now().plusYears(1));
   }

   private static LicenseChangeRequest add(String key) {
      LicenseChangeRequest r = new LicenseChangeRequest();
      r.setVerb(LicenseChangeRequest.VERB_ADD);
      r.setKey(key);
      return r;
   }

   private static LicenseChangeRequest remove(String key) {
      LicenseChangeRequest r = new LicenseChangeRequest();
      r.setVerb(LicenseChangeRequest.VERB_REMOVE);
      r.setKey(key);
      return r;
   }

   private static LicenseChangePlanRequest request(String task, List<LicenseChangeRequest> changes) {
      LicenseChangePlanRequest req = new LicenseChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static LicenseApplyRequest applyRequest(String task, String hash, String reviewOutcome,
                                                    Boolean acknowledgeDelicensing,
                                                    LicenseChangeRequest... changes)
   {
      LicenseApplyRequest req = new LicenseApplyRequest();
      req.setTask(task);
      req.setChanges(Arrays.asList(changes));
      req.setPlanHash(hash);
      req.setReviewOutcome(reviewOutcome);
      req.setAcknowledgeDelicensing(acknowledgeDelicensing);
      return req;
   }

   private MockedStatic<Audit> mockAudit() {
      MockedStatic<Audit> audit = mockStatic(Audit.class);
      Audit instance = mock(Audit.class);
      audit.when(Audit::getInstance).thenReturn(instance);
      return audit;
   }

   // -------------------------------------------------------------------------
   // hash / reviewOutcome gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsPlanHashMismatchOnStaleHash() {
      when(licenseManager.parseLicense("K1")).thenReturn(valid("K1"));
      LicenseApplyRequest req = applyRequest("task", "not-the-real-hash", "looks good", null, add("K1"));
      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, user));
   }

   @Test void applyThrowsOnMissingReviewOutcome() {
      when(licenseManager.parseLicense("K1")).thenReturn(valid("K1"));
      String hash = planService.resolve(request("task", List.of(add("K1")))).planHash();
      LicenseApplyRequest req = applyRequest("task", hash, "  ", null, add("K1"));
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
   }

   /** A key that became installed between preview and apply is refused loud by the fresh top-of
    * -apply resolve, before the hash is even compared (03-reconcile.md addition 2). */
   @Test void applyRefusesLoudWhenAddKeyBecameInstalledConcurrently() {
      when(licenseManager.parseLicense("K1")).thenReturn(valid("K1"));
      String hash = planService.resolve(request("task", List.of(add("K1")))).planHash();
      installed.add(valid("K1"));

      LicenseApplyRequest req = applyRequest("task", hash, "looks good", null, add("K1"));
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("already installed"));
   }

   // -------------------------------------------------------------------------
   // acknowledgeDelicensing gate (section 5/6)
   // -------------------------------------------------------------------------

   @Test void applyThrowsWhenRemovingTheLastKeyWithoutAcknowledgeDelicensing() {
      installed.add(valid("K1"));
      String hash = planService.resolve(request("task", List.of(remove("K1")))).planHash();
      LicenseApplyRequest req = applyRequest("task", hash, "looks good", null, remove("K1"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("acknowledgeDelicensing"));
   }

   @Test void applySucceedsRemovingTheLastKeyWithAcknowledgeDelicensing() throws Exception {
      installed.add(valid("K1"));
      String hash = planService.resolve(request("task", List.of(remove("K1")))).planHash();
      LicenseApplyRequest req = applyRequest("task", hash, "looks good", true, remove("K1"));
      LicenseApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertTrue(installed.isEmpty());
   }

   // -------------------------------------------------------------------------
   // success
   // -------------------------------------------------------------------------

   @Test void appliesAnAddAndReportsApplied() throws Exception {
      when(licenseManager.parseLicense("K1")).thenReturn(valid("K1"));
      String hash = planService.resolve(request("task", List.of(add("K1")))).planHash();
      LicenseApplyRequest req = applyRequest("task", hash, "looks good", null, add("K1"));
      LicenseApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("admin-snapshot/ref", result.backupRef());
      assertNull(result.rollbackFailures());
      assertEquals(1, result.results().size());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, result.results().get(0).status());
      assertTrue(installed.stream().anyMatch(l -> "K1".equals(l.key())));
      verify(backupService).backup(anyString());
   }

   @Test void appliesARemoveAndReportsApplied() throws Exception {
      installed.add(valid("K1"));
      installed.add(valid("K2"));
      String hash = planService.resolve(request("task", List.of(remove("K1")))).planHash();
      LicenseApplyRequest req = applyRequest("task", hash, "looks good", null, remove("K1"));
      LicenseApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertTrue(installed.stream().noneMatch(l -> "K1".equals(l.key())));
      assertTrue(installed.stream().anyMatch(l -> "K2".equals(l.key())));
   }

   // -------------------------------------------------------------------------
   // backup ordering (section 7)
   // -------------------------------------------------------------------------

   @Test void backupIsTakenBeforeAnyMutation() throws Exception {
      when(licenseManager.parseLicense("K1")).thenReturn(valid("K1"));
      String hash = planService.resolve(request("task", List.of(add("K1")))).planHash();
      LicenseApplyRequest req = applyRequest("task", hash, "looks good", null, add("K1"));

      InOrder order = inOrder(backupService, licenseKeySettingsService);

      try(MockedStatic<Audit> audit = mockAudit()) {
         service.apply(req, user);
      }

      order.verify(backupService).backup(anyString());
      order.verify(licenseKeySettingsService).addServerKey("K1");
   }

   // -------------------------------------------------------------------------
   // throw mid-apply -> rollback / rollback-failed (section 6, "fails by throwing")
   // -------------------------------------------------------------------------

   /** A throw carries no verifiable before/after evidence for the entry that threw, so its own
    * state is unconditionally reported as "unknown" -- the overall status is {@code rollback-failed}
    * even though the EARLIER change's own rollback succeeds cleanly, matching
    * {@code ProviderChangesetApplyServiceTest.throwMidApplyRollsBackEarlierChangeButReportsRollbackFailedForTheUnknownState}'s
    * own precedent exactly (guide section 2.5's "a throw is a failed change, not only a returned
    * failed status" rule). */
   @Test void midApplyFailureRollsBackThePreviouslyAppliedAddButReportsRollbackFailedForTheUnknownState()
      throws Exception
   {
      when(licenseManager.parseLicense("K1")).thenReturn(valid("K1"));
      when(licenseManager.parseLicense("K2")).thenReturn(valid("K2"));
      String hash = planService.resolve(request("task", List.of(add("K1"), add("K2")))).planHash();
      doThrow(new RuntimeException("simulated failure adding K2"))
         .when(licenseKeySettingsService).addServerKey("K2");

      LicenseApplyRequest req = applyRequest("task", hash, "looks good", null, add("K1"), add("K2"));
      LicenseApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertTrue(result.rollbackFailures().stream()
         .anyMatch(f -> "K2".equals(f.property()) && f.error().contains("state unknown")));
      // K1's own rollback succeeded cleanly even though the overall status is rollback-failed.
      assertTrue(installed.isEmpty());
      verify(licenseKeySettingsService).addServerKey("K1");
      verify(licenseKeySettingsService).addServerKey("K2");
      verify(licenseKeySettingsService).removeServerKey("K1");
   }

   @Test void rollbackFailureReportsRollbackFailedNamingTheKey() throws Exception {
      when(licenseManager.parseLicense("K1")).thenReturn(valid("K1"));
      when(licenseManager.parseLicense("K2")).thenReturn(valid("K2"));
      String hash = planService.resolve(request("task", List.of(add("K1"), add("K2")))).planHash();
      doThrow(new RuntimeException("simulated failure adding K2"))
         .when(licenseKeySettingsService).addServerKey("K2");
      // Rollback of K1's add (a removeServerKey call) silently fails to actually remove it.
      doNothing().when(licenseKeySettingsService).removeServerKey("K1");

      LicenseApplyRequest req = applyRequest("task", hash, "looks good", null, add("K1"), add("K2"));
      LicenseApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertTrue(result.rollbackFailures().stream().anyMatch(f -> "K1".equals(f.property())));
   }

   // -------------------------------------------------------------------------
   // rollback of a remove -- claiming-node-drift advisory + re-check before re-adding
   // (03-reconcile.md addition 2 / 01-spec.md section 4)
   // -------------------------------------------------------------------------

   @Test void rollbackOfRemoveCarriesClaimingNodeDriftAdvisory() throws Exception {
      installed.add(valid("K1"));
      installed.add(valid("K2"));
      // K1's rollback re-adds it via addServerKey, which re-parses the key -- stubbed so the
      // rollback path's re-installed License is well-formed, not a Mockito default null.
      when(licenseManager.parseLicense("K1")).thenReturn(valid("K1"));
      when(licenseManager.parseLicense("K3")).thenReturn(valid("K3"));
      String hash = planService.resolve(
         request("task", List.of(remove("K1"), add("K3")))).planHash();
      doThrow(new RuntimeException("simulated failure adding K3"))
         .when(licenseKeySettingsService).addServerKey("K3");

      LicenseApplyRequest req = applyRequest("task", hash, "looks good", null, remove("K1"), add("K3"));
      LicenseApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      // Overall status is rollback-failed (K3's own throw is an unconditional "unknown state"
      // failure, matching the previous test's own rule) even though K1's remove was itself rolled
      // back cleanly, with the claiming-node-drift advisory attached to its own outcome.
      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertTrue(installed.stream().anyMatch(l -> "K1".equals(l.key())));
      LicenseApplyOutcome k1Outcome = result.results().stream()
         .filter(o -> "K1".equals(o.property())).findFirst().orElseThrow();
      assertNotNull(k1Outcome.advisory());
      assertTrue(k1Outcome.advisory().contains("different cluster node"));
   }

   /** 03-reconcile.md addition 2 -- a rollback re-add must re-check installed-membership before
    * calling {@code addServerKey}, not blindly retry, since a concurrent operator action could have
    * already re-added the same key through a different channel during the rollback window. */
   @Test void rollbackOfRemoveDoesNotReAddWhenAlreadyReinstalledConcurrently() throws Exception {
      installed.add(valid("K1"));
      when(licenseManager.parseLicense("K3")).thenReturn(valid("K3"));
      String hash = planService.resolve(
         request("task", List.of(remove("K1"), add("K3")))).planHash();

      // The instant K3's add is attempted, a concurrent channel races K1 back in, then the add fails.
      doAnswer(inv -> {
         installed.add(valid("K1"));
         throw new RuntimeException("simulated failure adding K3");
      }).when(licenseKeySettingsService).addServerKey("K3");

      LicenseApplyRequest req = applyRequest("task", hash, "looks good", null, remove("K1"), add("K3"));
      LicenseApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      verify(licenseKeySettingsService, never()).addServerKey("K1");
      assertTrue(result.rollbackFailures().stream()
         .anyMatch(f -> "K1".equals(f.property()) && f.error().contains("already reinstalled")));
   }

   // -------------------------------------------------------------------------
   // audit (section 8) -- OBJECT_TYPE_EMPROPERTY, snapshotScope=storage, organizationId null
   // -------------------------------------------------------------------------

   @Test void writesAnAuditRecordWithLicensingFields() throws Exception {
      when(licenseManager.parseLicense("K1")).thenReturn(valid("K1"));
      String hash = planService.resolve(request("task", List.of(add("K1")))).planHash();
      LicenseApplyRequest req = applyRequest("task", hash, "looks good", null, add("K1"));
      Audit auditInstance = mock(Audit.class);

      try(MockedStatic<Audit> audit = mockStatic(Audit.class);
          MockedStatic<Tool> tool = mockStatic(Tool.class, CALLS_REAL_METHODS))
      {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         tool.when(Tool::getHost).thenReturn("test-host");
         service.apply(req, user);
      }

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(user));
      AdminChangeRecord record = captor.getValue();
      assertEquals(ActionRecord.OBJECT_TYPE_EMPROPERTY, record.getObjectType());
      assertEquals(AdminChangeRecord.SCOPE_STORAGE, record.getSnapshotScope());
      assertEquals(AdminChangeRecord.RISK_HIGH, record.getRiskLevel());
      assertEquals("admin-snapshot/ref", record.getBackupRef());
      assertEquals(AdminChangeRecord.ACTION_APPLY, record.getAction());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, record.getStatus());
      assertEquals("K1", record.getProperty());
      assertNull(record.getOrganizationId());
   }
}
