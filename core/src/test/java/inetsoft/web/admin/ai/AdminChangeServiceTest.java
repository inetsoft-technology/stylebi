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
import inetsoft.util.audit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.security.Principal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminChangeServiceTest {
   @Mock private Principal principal;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<Tool> toolStatic;
   private Audit audit;
   private AdminChangeService service;

   @BeforeEach void setup() {
      sreeEnv = mockStatic(SreeEnv.class);
      auditStatic = mockStatic(Audit.class);
      toolStatic = mockStatic(Tool.class);
      audit = mock(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);
      toolStatic.when(Tool::getHost).thenReturn("host-1");
      service = new AdminChangeService();
   }

   @AfterEach void tearDown() {
      sreeEnv.close(); auditStatic.close(); toolStatic.close();
   }

   private AdminChangeRequest req(String prop, String val) {
      AdminChangeRequest r = new AdminChangeRequest();
      r.setTransactionId("chg-1"); r.setProperty(prop); r.setValue(val);
      r.setAction(AdminChangeRecord.ACTION_APPLY);
      r.setRiskLevel(AdminChangeRecord.RISK_LOW);
      r.setSnapshotScope(AdminChangeRecord.SCOPE_VALUE);
      return r;
   }

   @Test void appliesVerifiesAndAudits() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows"))
             .thenReturn("100")          // before
             .thenReturn("500");         // after read-back
      AdminChangeResult res = service.applyChange(req("max.rows", "500"), principal);

      assertEquals("100", res.getBeforeValue());
      assertEquals("500", res.getAfterValue());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
      sreeEnv.verify(() -> SreeEnv.setProperty("max.rows", "500"));
      sreeEnv.verify(SreeEnv::save);
      ArgumentCaptor<AdminChangeRecord> cap = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(audit).auditAdminChange(cap.capture(), eq(principal));
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, cap.getValue().getStatus());
   }

   @Test void reportsFailedWhenReadBackMismatches() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows"))
             .thenReturn("100").thenReturn("100");   // never took
      AdminChangeResult res = service.applyChange(req("max.rows", "500"), principal);
      assertEquals(AdminChangeRecord.STATUS_FAILED, res.getStatus());
      verify(audit).auditAdminChange(any(), eq(principal));
   }

   @Test void nullValueRemovesProperty() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows"))
             .thenReturn("100").thenReturn(null);
      AdminChangeResult res = service.applyChange(req("max.rows", null), principal);
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
      sreeEnv.verify(() -> SreeEnv.remove("max.rows"));
   }
}
