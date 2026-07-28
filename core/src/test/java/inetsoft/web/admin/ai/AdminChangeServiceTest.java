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
import inetsoft.web.admin.properties.PropertyChangeSideEffects;
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
   @Mock private PropertyChangeSideEffects sideEffects;
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
      service = new AdminChangeService(sideEffects);
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
      AdminChangeRecord record = cap.getValue();
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, record.getStatus());
      assertEquals("max.rows", record.getProperty());
      assertEquals("100", record.getBeforeValue());
      assertEquals("500", record.getAfterValue());
      assertEquals(ActionRecord.OBJECT_TYPE_EMPROPERTY, record.getObjectType());
      assertEquals("host-1", record.getServerHostName());
   }

   @Test void reportsFailedWhenReadBackMismatches() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows"))
             .thenReturn("100").thenReturn("100");   // never took
      AdminChangeResult res = service.applyChange(req("max.rows", "500"), principal);
      assertEquals(AdminChangeRecord.STATUS_FAILED, res.getStatus());
      ArgumentCaptor<AdminChangeRecord> cap = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(audit).auditAdminChange(cap.capture(), eq(principal));
      AdminChangeRecord record = cap.getValue();
      assertEquals(AdminChangeRecord.STATUS_FAILED, record.getStatus());
      assertEquals("max.rows", record.getProperty());
      assertEquals("100", record.getBeforeValue());
      assertEquals("100", record.getAfterValue());
      assertEquals(ActionRecord.OBJECT_TYPE_EMPROPERTY, record.getObjectType());
      assertEquals("host-1", record.getServerHostName());
   }

   @Test void nullValueRemovesProperty() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows"))
             .thenReturn("100").thenReturn(null);
      AdminChangeResult res = service.applyChange(req("max.rows", null), principal);
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
      sreeEnv.verify(() -> SreeEnv.remove("max.rows"));
      ArgumentCaptor<AdminChangeRecord> cap = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(audit).auditAdminChange(cap.capture(), eq(principal));
      AdminChangeRecord record = cap.getValue();
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, record.getStatus());
      assertEquals("max.rows", record.getProperty());
      assertEquals("100", record.getBeforeValue());
      assertNull(record.getAfterValue());
      assertEquals(ActionRecord.OBJECT_TYPE_EMPROPERTY, record.getObjectType());
      assertEquals("host-1", record.getServerHostName());
   }

   @Test void rejectsBlankTransactionId() {
      AdminChangeRequest r = req("max.rows", "500");
      r.setTransactionId("   ");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.applyChange(r, principal));
      assertTrue(ex.getMessage().contains("transactionId"));
      sreeEnv.verifyNoInteractions();
      auditStatic.verify(Audit::getInstance, never());
      verifyNoInteractions(audit);
   }

   @Test void rejectsNullTransactionId() {
      AdminChangeRequest r = req("max.rows", "500");
      r.setTransactionId(null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.applyChange(r, principal));
      assertTrue(ex.getMessage().contains("transactionId"));
      sreeEnv.verifyNoInteractions();
      auditStatic.verify(Audit::getInstance, never());
      verifyNoInteractions(audit);
   }

   @Test void rejectsBlankProperty() {
      AdminChangeRequest r = req("  ", "500");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.applyChange(r, principal));
      assertTrue(ex.getMessage().contains("property"));
      sreeEnv.verifyNoInteractions();
      auditStatic.verify(Audit::getInstance, never());
      verifyNoInteractions(audit);
   }

   @Test void rejectsNullProperty() {
      AdminChangeRequest r = req(null, "500");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.applyChange(r, principal));
      assertTrue(ex.getMessage().contains("property"));
      sreeEnv.verifyNoInteractions();
      auditStatic.verify(Audit::getInstance, never());
      verifyNoInteractions(audit);
   }

   @Test void invokesEditSideEffectsWhenSettingAValue() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows"))
             .thenReturn("100").thenReturn("500");
      service.applyChange(req("max.rows", "500"), principal);

      verify(sideEffects).applyEditSideEffects("max.rows");
      verify(sideEffects, never()).applyRemoveSideEffects(any());
   }

   @Test void invokesRemoveSideEffectsWhenRemovingAValue() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows"))
             .thenReturn("100").thenReturn(null);
      service.applyChange(req("max.rows", null), principal);

      verify(sideEffects).applyRemoveSideEffects("max.rows");
      verify(sideEffects, never()).applyEditSideEffects(any());
   }

   @Test void alwaysAuditsWhenSaveThrows() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows"))
             .thenReturn("100")          // before
             .thenReturn("100");         // best-effort after read in catch block
      sreeEnv.when(SreeEnv::save).thenThrow(new RuntimeException("disk full"));

      AdminChangeResult res = service.applyChange(req("max.rows", "500"), principal);

      assertEquals(AdminChangeRecord.STATUS_FAILED, res.getStatus());
      assertNotNull(res.getError());
      assertEquals("disk full", res.getError());

      ArgumentCaptor<AdminChangeRecord> cap = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(audit).auditAdminChange(cap.capture(), eq(principal));
      AdminChangeRecord record = cap.getValue();
      assertEquals(AdminChangeRecord.STATUS_FAILED, record.getStatus());
      assertEquals("max.rows", record.getProperty());
      assertEquals("100", record.getBeforeValue());
      assertEquals("100", record.getAfterValue());
      assertEquals(ActionRecord.OBJECT_TYPE_EMPROPERTY, record.getObjectType());
      assertEquals("host-1", record.getServerHostName());
   }
}
