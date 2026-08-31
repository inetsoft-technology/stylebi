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
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 01-spec.md section 2 (add-time validity refusal, remove-of-absent refusal, already-installed-add
 * refusal per 03-reconcile.md addition 2), section 5 (per-key hash projection, de-licensing net
 * count), section 14 D2 (never trust {@code LicenseKeyModel.valid()} -- this service never
 * constructs one at all, only real {@link License} objects).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class LicenseChangePlanServiceTest {
   @Mock private LicenseManager licenseManager;
   private LicenseChangePlanService service;

   @BeforeEach
   void setUp() {
      service = new LicenseChangePlanService(licenseManager);
   }

   private static License license(String key, LicenseType type, LocalDateTime expires) {
      return License.builder().key(key).type(type).expires(expires).build();
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

   private void stubInstalled(License... installed) {
      lenient().when(licenseManager.getInstalledLicenses())
         .thenReturn(new LinkedHashSet<>(Arrays.asList(installed)));
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      LicenseChangePlanRequest req = request("  ", List.of(add("K1")));
      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req))
                    .getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      LicenseChangePlanRequest req = request("task", List.of());
      assertThrows(IllegalArgumentException.class, () -> service.resolve(req));
   }

   @Test void resolveThrowsOnNullChanges() {
      LicenseChangePlanRequest req = request("task", null);
      assertThrows(IllegalArgumentException.class, () -> service.resolve(req));
   }

   @Test void resolveThrowsOnUnrecognizedVerb() {
      LicenseChangeRequest change = add("K1");
      change.setVerb("replace");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change))));
      assertTrue(ex.getMessage().contains("verb"));
   }

   @Test void resolveThrowsOnBlankKey() {
      LicenseChangeRequest change = add("   ");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change))));
      assertTrue(ex.getMessage().contains("key"));
   }

   @Test void resolveThrowsOnDuplicateKeyEntries() {
      stubInstalled();
      lenient().when(licenseManager.parseLicense("K1"))
         .thenReturn(license("K1", LicenseType.CPU, LocalDateTime.now().plusYears(1)));
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(add("K1"), remove("K1")))));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   // -------------------------------------------------------------------------
   // add
   // -------------------------------------------------------------------------

   @Test void resolveAcceptsValidAdd() {
      stubInstalled();
      License resolved = license("K1", LicenseType.CPU, LocalDateTime.now().plusYears(1));
      when(licenseManager.parseLicense("K1")).thenReturn(resolved);

      ResolvedPlan plan = service.resolve(request("task", List.of(add("K1"))));

      assertEquals(1, plan.changes().size());
      PlanChange change = plan.changes().get(0);
      assertEquals("K1", change.property());
      assertNull(change.currentValue());
      assertNotNull(change.proposedValue());
      assertEquals("high", change.risk());
      assertEquals("storage", change.snapshotScope());
      assertTrue(plan.requiresAgentSignoff());
      assertTrue(plan.requiresStorageBackup());
   }

   @Test void resolveRefusesAddOfInvalidTypedKey() {
      stubInstalled();
      License invalid = license("BAD", LicenseType.INVALID, null);
      when(licenseManager.parseLicense("BAD")).thenReturn(invalid);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(add("BAD")))));
      assertTrue(ex.getMessage().contains("BAD"));
   }

   @Test void resolveRefusesAddOfExpiredKey() {
      stubInstalled();
      // type != INVALID but valid() is false because it has already expired.
      License expired = license("EXP", LicenseType.CPU, LocalDateTime.now().minusDays(1));
      when(licenseManager.parseLicense("EXP")).thenReturn(expired);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(add("EXP")))));
      assertTrue(ex.getMessage().contains("EXP"));
   }

   /** 03-reconcile.md addition 2 -- build-blocking: an add of an already-installed key is refused
    * outright, not merely folded into the plan hash, because {@code EnterpriseLicenseStrategy
    * .addLicense}'s cluster-node claiming exchange has no already-installed guard of its own. */
   @Test void resolveRefusesAddOfAlreadyInstalledKey() {
      License already = license("K1", LicenseType.CPU, LocalDateTime.now().plusYears(1));
      stubInstalled(already);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(add("K1")))));
      assertTrue(ex.getMessage().contains("already installed"));
      verify(licenseManager, never()).parseLicense(any());
   }

   // -------------------------------------------------------------------------
   // remove
   // -------------------------------------------------------------------------

   @Test void resolveAcceptsValidRemove() {
      License installed = license("K1", LicenseType.CPU, LocalDateTime.now().plusYears(1));
      stubInstalled(installed);

      ResolvedPlan plan = service.resolve(request("task", List.of(remove("K1"))));

      assertEquals(1, plan.changes().size());
      PlanChange change = plan.changes().get(0);
      assertEquals("K1", change.property());
      assertNotNull(change.currentValue());
      assertNull(change.proposedValue());
   }

   @Test void resolveRefusesRemoveOfNotInstalledKey() {
      stubInstalled();
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(remove("GHOST")))));
      assertTrue(ex.getMessage().contains("GHOST"));
      assertTrue(ex.getMessage().contains("not currently installed"));
   }

   // -------------------------------------------------------------------------
   // last-key-removal / de-licensing net count (section 5)
   // -------------------------------------------------------------------------

   @Test void resolveFlagsDeLicensingWarningWhenRemovingTheLastKey() {
      License only = license("K1", LicenseType.CPU, LocalDateTime.now().plusYears(1));
      stubInstalled(only);

      ResolvedPlan plan = service.resolve(request("task", List.of(remove("K1"))));

      assertTrue(plan.changes().get(0).description().contains("WARNING"));
   }

   @Test void resolveDoesNotFlagDeLicensingWarningWhenAnotherKeyRemains() {
      License k1 = license("K1", LicenseType.CPU, LocalDateTime.now().plusYears(1));
      License k2 = license("K2", LicenseType.CPU, LocalDateTime.now().plusYears(1));
      stubInstalled(k1, k2);

      ResolvedPlan plan = service.resolve(request("task", List.of(remove("K1"))));

      assertFalse(plan.changes().get(0).description().contains("WARNING"));
   }

   @Test void resolveEvaluatesMixedAddAndRemoveNet() {
      License only = license("K1", LicenseType.CPU, LocalDateTime.now().plusYears(1));
      stubInstalled(only);
      License newLicense = license("K2", LicenseType.CPU, LocalDateTime.now().plusYears(1));
      when(licenseManager.parseLicense("K2")).thenReturn(newLicense);

      // Removing K1 and adding K2 nets to 1 remaining key -- no de-licensing warning.
      ResolvedPlan plan = service.resolve(request("task", List.of(remove("K1"), add("K2"))));

      for(PlanChange change : plan.changes()) {
         assertFalse(change.description().contains("WARNING"));
      }
   }

   @Test void computeDeLicensingWarningNetsAddsAndRemoves() {
      assertTrue(LicenseChangePlanService.computeDeLicensingWarning(1, 0, 1));
      assertFalse(LicenseChangePlanService.computeDeLicensingWarning(2, 0, 1));
      assertFalse(LicenseChangePlanService.computeDeLicensingWarning(1, 1, 1));
      assertFalse(LicenseChangePlanService.computeDeLicensingWarning(0, 0, 0));
   }

   // -------------------------------------------------------------------------
   // hash stability / drift sensitivity (section 5)
   // -------------------------------------------------------------------------

   @Test void hashIsStableAcrossIdenticalResolves() {
      License installed = license("K1", LicenseType.CPU, LocalDateTime.now().plusYears(1));
      stubInstalled(installed);

      ResolvedPlan first = service.resolve(request("task", List.of(remove("K1"))));
      ResolvedPlan second = service.resolve(request("task", List.of(remove("K1"))));

      assertEquals(first.planHash(), second.planHash());
   }

   @Test void hashChangesWhenAnUnrelatedKeyIsInstalledConcurrently() {
      License k1 = license("K1", LicenseType.CPU, LocalDateTime.now().plusYears(1));
      stubInstalled(k1);
      ResolvedPlan before = service.resolve(request("task", List.of(remove("K1"))));

      License k2 = license("K2", LicenseType.CPU, LocalDateTime.now().plusYears(1));
      stubInstalled(k1, k2);
      ResolvedPlan after = service.resolve(request("task", List.of(remove("K1"))));

      assertNotEquals(before.planHash(), after.planHash());
   }
}
