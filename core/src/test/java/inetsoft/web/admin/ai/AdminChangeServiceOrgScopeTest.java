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
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.properties.PropertyChangeSideEffects;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SreeEnv is asymmetric: getProperty(name) is ORG-SCOPED (it resolves to
 * inetsoft.org.{currentOrg}.{name} when that key exists) while setProperty(name, val) and
 * remove(name) write the literal key.
 *
 * Mixing them is the bug this test pins. With an org override present, the old code read the
 * override as beforeValue, wrote the GLOBAL key, re-read the unchanged override, reported "failed",
 * and the apply orchestration then "rolled back" by writing the override's value into the global
 * key - corrupting the global default. Reading with orgScope=false makes read/write/remove all
 * operate on the same literal key.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminChangeServiceOrgScopeTest {
   @Mock private PropertyChangeSideEffects sideEffects;
   @Mock private Principal principal;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Tool> toolStatic;
   private AdminChangeService service;

   @BeforeEach
   void setUp() {
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      // Tool.getHost() (called from writeAudit) reaches into SreeEnv.getProperty("local.host.name")
      // (the one-arg, org-scoped overload) as a transitive, unrelated call. Left unmocked, that
      // incidental call would trip readsWithOrgScopeDisabled's `never()` guard on
      // SreeEnv.getProperty(anyString()) for reasons that have nothing to do with the org-scope
      // fix under test. Mirrors AdminChangeServiceTest's mocking of Tool for the same reason.
      toolStatic = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT));
      toolStatic.when(Tool::getHost).thenReturn("host-1");
      service = new AdminChangeService(sideEffects);
   }

   @AfterEach
   void tearDown() {
      sreeEnv.close();
      toolStatic.close();
   }

   private AdminChangeRequest request(String property, String value) {
      AdminChangeRequest req = new AdminChangeRequest();
      req.setTransactionId("chg-1");
      req.setTaskDescription("t");
      req.setProperty(property);
      req.setValue(value);
      req.setAction(AdminChangeRecord.ACTION_APPLY);
      req.setRiskLevel(AdminChangeRecord.RISK_LOW);
      req.setSnapshotScope(AdminChangeRecord.SCOPE_VALUE);
      return req;
   }

   @Test
   void readsWithOrgScopeDisabled() {
      // The whole point: never SreeEnv.getProperty(name), which would straddle scopes.
      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false))
         .thenReturn("100", "500");

      AdminChangeResult result = service.applyChange(request("query.runtime.maxrow", "500"), principal);

      assertEquals(AdminChangeRecord.STATUS_VERIFIED, result.getStatus());
      assertEquals("100", result.getBeforeValue());
      assertEquals("500", result.getAfterValue());
      sreeEnv.verify(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false), times(2));
      sreeEnv.verify(() -> SreeEnv.getProperty(anyString()), never());
   }

   @Test
   void writesAndVerifiesAnOrgQualifiedPropertyOnItsOwnKey() {
      String key = "inetsoft.org.acme.mail.smtp.host";
      sreeEnv.when(() -> SreeEnv.getProperty(key, false, false))
         .thenReturn("old.example.com", "smtp.example.com");

      AdminChangeResult result = service.applyChange(request(key, "smtp.example.com"), principal);

      assertEquals(AdminChangeRecord.STATUS_VERIFIED, result.getStatus());
      sreeEnv.verify(() -> SreeEnv.setProperty(key, "smtp.example.com"));
      // The global key must be left alone.
      sreeEnv.verify(() -> SreeEnv.setProperty(eq("mail.smtp.host"), anyString()), never());
   }

   @Test
   void passesTheStrippedBaseNameToEditSideEffects() {
      // PropertyChangeSideEffects matches exact literals, so an org-qualified name would silently
      // never fire - a change that reports success while the repository is never notified.
      String key = "inetsoft.org.acme.security.exposedefaultorgtoall";
      sreeEnv.when(() -> SreeEnv.getProperty(key, false, false)).thenReturn("false", "true");

      service.applyChange(request(key, "true"), principal);

      verify(sideEffects).applyEditSideEffects("security.exposedefaultorgtoall");
   }

   @Test
   void passesTheStrippedBaseNameToRemoveSideEffects() {
      String key = "inetsoft.org.acme.security.exposedefaultorgtoall";
      sreeEnv.when(() -> SreeEnv.getProperty(key, false, false)).thenReturn("true", null);

      service.applyChange(request(key, null), principal);

      verify(sideEffects).applyPreRemoveSideEffects("security.exposedefaultorgtoall");
      verify(sideEffects).applyPostRemoveSideEffects("security.exposedefaultorgtoall");
      sreeEnv.verify(() -> SreeEnv.remove(key));
   }

   @Test
   void acceptsAReviewOutcomeForTheAuditRecord() {
      sreeEnv.when(() -> SreeEnv.getProperty("query.runtime.maxrow", false, false))
         .thenReturn("100", "500");
      AdminChangeRequest req = request("query.runtime.maxrow", "500");
      req.setReviewOutcome("approved by admin-reviewer");

      assertEquals("approved by admin-reviewer", req.getReviewOutcome());
      assertDoesNotThrow(() -> service.applyChange(req, principal));
   }
}
