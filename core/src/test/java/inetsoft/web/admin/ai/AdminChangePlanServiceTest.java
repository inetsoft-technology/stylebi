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
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The plan hash is what turns "the operator reviewed this" from a promise into something the server
 * verifies. It covers CURRENT values as well as proposed ones, so a property that drifted between
 * preview and apply produces a different hash and the apply is refused rather than silently doing
 * something the operator never saw.
 */
@Tag("core")
class AdminChangePlanServiceTest {
   private final AdminPropertyCatalog catalog = new AdminPropertyCatalog();
   private final AdminChangePlanService service =
      new AdminChangePlanService(catalog, new AdminRiskClassifier(catalog));
   private MockedStatic<SreeEnv> sreeEnv;

   @BeforeEach
   void setUp() {
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
   }

   @AfterEach
   void tearDown() {
      sreeEnv.close();
   }

   private static PlanRequest request(String task, String property, String value) {
      PlanRequest.Change change = new PlanRequest.Change();
      change.setProperty(property);
      change.setValue(value);
      PlanRequest req = new PlanRequest();
      req.setTask(task);
      req.setChanges(List.of(change));
      return req;
   }

   @Test
   void resolvesAliasCurrentValueAndClassification() {
      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false))
         .thenReturn("100");

      ResolvedPlan plan = service.resolve(request("cap rows", "  Max.Rows ", "500"));

      assertEquals("cap rows", plan.task());
      assertEquals(1, plan.changes().size());
      PlanChange change = plan.changes().get(0);
      assertEquals("query.runtime.maxrow", change.property());
      assertNull(change.orgId());
      assertEquals("100", change.currentValue());
      assertEquals("500", change.proposedValue());
      assertEquals("low", change.risk());
      assertTrue(change.recognized());
      assertNotNull(change.description());
      assertFalse(plan.requiresStorageBackup());
      assertFalse(plan.requiresAgentSignoff());
      assertNotNull(plan.planHash());
   }

   @Test
   void canonicalizesTheProposedValue() {
      sreeEnv.when(() -> SreeEnv.getProperty("log.detail.level", false, false)).thenReturn("warn");
      ResolvedPlan plan = service.resolve(request("quieter", "log.detail.level", "INFO"));
      assertEquals("info", plan.changes().get(0).proposedValue());
   }

   @Test
   void readsCurrentValueWithOrgScopeDisabled() {
      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false)).thenReturn("1");
      service.resolve(request("t", "query.runtime.maxrow", "2"));
      sreeEnv.verify(() -> SreeEnv.getProperty(anyString()), never());
   }

   @Test
   void reportsOrgIdForAnOrgQualifiedChange() {
      sreeEnv.when(() -> SreeEnv.getProperty("inetsoft.org.acme.mail.smtp.host", false, false))
         .thenReturn("old");
      ResolvedPlan plan = service.resolve(request("t", "inetsoft.org.acme.smtp.host", "new"));
      PlanChange change = plan.changes().get(0);
      assertEquals("inetsoft.org.acme.mail.smtp.host", change.property());
      assertEquals("acme", change.orgId());
      assertEquals("high", change.risk());
   }

   @Test
   void requiresSignoffAndBackupForAStorageScopedHighRiskChange() {
      sreeEnv.when(() -> SreeEnv.getProperty("security.exposedefaultorgtoall", false, false))
         .thenReturn("false");
      ResolvedPlan plan = service.resolve(request("t", "security.exposedefaultorgtoall", "true"));
      assertTrue(plan.requiresAgentSignoff());
      assertTrue(plan.requiresStorageBackup());
   }

   @Test
   void requiresSignoffWithoutBackupForAValueReversibleHighRiskChange() {
      sreeEnv.when(() -> SreeEnv.getProperty("mail.smtp.host", false, false)).thenReturn("old");
      ResolvedPlan plan = service.resolve(request("t", "mail.smtp.host", "new"));
      assertTrue(plan.requiresAgentSignoff());
      assertFalse(plan.requiresStorageBackup());
   }

   @Test
   void hashIsStableForTheSameResolvedPlan() {
      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false))
         .thenReturn("100");
      assertEquals(service.resolve(request("t", "max.rows", "500")).planHash(),
                   service.resolve(request("t", "query.runtime.maxrow", "500")).planHash());
   }

   @Test
   void hashChangesWhenTheCurrentValueDrifts() {
      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false))
         .thenReturn("100");
      String before = service.resolve(request("t", "max.rows", "500")).planHash();

      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false))
         .thenReturn("999");
      assertNotEquals(before, service.resolve(request("t", "max.rows", "500")).planHash());
   }

   @Test
   void hashChangesWithTheProposedValueAndTheTask() {
      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false))
         .thenReturn("100");
      String base = service.resolve(request("t", "max.rows", "500")).planHash();
      assertNotEquals(base, service.resolve(request("t", "max.rows", "600")).planHash());
      assertNotEquals(base, service.resolve(request("other", "max.rows", "500")).planHash());
   }

   @Test
   void rejectsAnEmptyOrMissingChangeList() {
      PlanRequest empty = new PlanRequest();
      empty.setTask("t");
      empty.setChanges(List.of());
      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(empty))
         .getMessage().startsWith("changes:"));

      PlanRequest missing = new PlanRequest();
      missing.setTask("t");
      assertThrows(IllegalArgumentException.class, () -> service.resolve(missing));
   }

   @Test
   void rejectsABlankTask() {
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("  ", "max.rows", "500"))).getMessage().startsWith("task:"));
   }

   @Test
   void rejectsADuplicateProperty() {
      // Two entries for one property have no defined apply order, and the second would silently
      // win. Refuse instead.
      PlanRequest.Change first = new PlanRequest.Change();
      first.setProperty("max.rows");
      first.setValue("500");
      PlanRequest.Change second = new PlanRequest.Change();
      second.setProperty("query.runtime.maxrow");
      second.setValue("600");
      PlanRequest req = new PlanRequest();
      req.setTask("t");
      req.setChanges(List.of(first, second));

      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req))
         .getMessage().contains("query.runtime.maxrow"));
   }

   @Test
   void rejectsAnInvalidValue() {
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("t", "max.rows", "abc"))).getMessage()
            .contains("query.runtime.maxrow"));
   }

   @Test
   void passesAnUncataloguedValueThroughAsHighRisk() {
      sreeEnv.when(() -> SreeEnv.getProperty("some.unknown.prop", false, false)).thenReturn(null);
      PlanChange change = service.resolve(request("t", "some.unknown.prop", "whatever"))
         .changes().get(0);
      assertFalse(change.recognized());
      assertEquals("high", change.risk());
      assertEquals("whatever", change.proposedValue());
      assertNull(change.description());
   }

   @Test
   void rejectsAProposedValueContainingTheRecordSeparator() {
      // Without this, a value carrying the separator could forge field boundaries and make two
      // materially different plans hash identically - defeating the gate the hash exists to be.
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("t", "mail.smtp.host", "abc\u001fdef")))
            .getMessage().startsWith("value:"));
   }

   @Test
   void rejectsAnUncataloguedValueContainingAControlCharacter() {
      // The uncatalogued path bypasses canonicalizeValue entirely, so it needs its own guard.
      assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("t", "some.unknown.prop", "x\u0000y")));
   }

   @Test
   void rejectsATaskContainingAControlCharacter() {
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("bad\u0000task", "max.rows", "500")))
            .getMessage().startsWith("task:"));
   }

   @Test
   void stillAcceptsOrdinaryValues() {
      // Guard against over-rejecting: normal values, whitespace and punctuation must still pass.
      sreeEnv.when(() -> SreeEnv.getProperty("mail.smtp.host", false, false)).thenReturn("old");
      assertDoesNotThrow(() -> service.resolve(request("t", "mail.smtp.host", " smtp.example.com ")));
   }
}
