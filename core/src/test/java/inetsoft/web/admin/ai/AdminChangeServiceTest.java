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
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows", false, false))
             .thenReturn("100")          // before
             .thenReturn("500");         // after read-back
      AdminChangeResult res = service.applyChange(req("max.rows", "500"), principal);

      assertEquals("100", res.getBeforeValue());
      assertEquals("500", res.getAfterValue());
      // Finding 3's undo gate in AdminChangesetApplyService relies on this being true whenever
      // the snapshot read actually succeeded - keep the two halves from drifting apart.
      assertTrue(res.isBeforeRead());
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
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows", false, false))
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
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows", false, false))
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

   // Finding 2: AdminChangeService must NOT trim. It is reachable from rollback with a STORED
   // value that never went through AdminPropertyCatalog.canonicalizeValue - trimming here would
   // write back a different value than was actually there before, while still reporting
   // "verified" because status is computed against this same (wrongly trimmed) desired value.
   @Test void writesAndVerifiesAValueWithSurroundingWhitespaceVerbatim() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("mail.smtp.host", false, false))
             .thenReturn("old").thenReturn(" smtp.example.com ");
      AdminChangeResult res =
         service.applyChange(req("mail.smtp.host", " smtp.example.com "), principal);

      sreeEnv.verify(() -> SreeEnv.setProperty("mail.smtp.host", " smtp.example.com "));
      assertEquals(" smtp.example.com ", res.getAfterValue());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
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

   @Test void rejectsBlankAction() {
      AdminChangeRequest r = req("max.rows", "500");
      r.setAction("   ");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.applyChange(r, principal));
      assertTrue(ex.getMessage().contains("action"));
      sreeEnv.verifyNoInteractions();
      auditStatic.verify(Audit::getInstance, never());
      verifyNoInteractions(audit, sideEffects);
   }

   @Test void rejectsNullAction() {
      AdminChangeRequest r = req("max.rows", "500");
      r.setAction(null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.applyChange(r, principal));
      assertTrue(ex.getMessage().contains("action"));
      sreeEnv.verifyNoInteractions();
      auditStatic.verify(Audit::getInstance, never());
      verifyNoInteractions(audit, sideEffects);
   }

   @Test void rejectsInvalidAction() {
      AdminChangeRequest r = req("max.rows", "500");
      r.setAction("delete");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.applyChange(r, principal));
      assertTrue(ex.getMessage().contains("action"));
      sreeEnv.verifyNoInteractions();
      auditStatic.verify(Audit::getInstance, never());
      verifyNoInteractions(audit, sideEffects);
   }

   @Test void invokesEditSideEffectsWhenSettingAValue() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows", false, false))
             .thenReturn("100").thenReturn("500");
      service.applyChange(req("max.rows", "500"), principal);

      verify(sideEffects).applyEditSideEffects("max.rows");
      verify(sideEffects, never()).applyPreRemoveSideEffects(any());
      verify(sideEffects, never()).applyPostRemoveSideEffects(any());
   }

   @Test void invokesRemoveSideEffectsWhenRemovingAValue() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows", false, false))
             .thenReturn("100").thenReturn(null);
      service.applyChange(req("max.rows", null), principal);

      verify(sideEffects).applyPreRemoveSideEffects("max.rows");
      verify(sideEffects).applyPostRemoveSideEffects("max.rows");
      verify(sideEffects, never()).applyEditSideEffects(any());
   }

   // [ordering] applyPreRemoveSideEffects must run BEFORE SreeEnv.remove() (it reads the
   // property's pre-removal value); applyPostRemoveSideEffects must run AFTER SreeEnv.save()
   // -- matching PropertiesController.deleteProperty's ordering exactly, within the
   // successful-apply path
   @Test void ordersRemoveSideEffectsAroundRemoveAndSave() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows", false, false))
             .thenReturn("100").thenReturn(null);
      service.applyChange(req("max.rows", null), principal);

      InOrder inOrder = inOrder(sideEffects, SreeEnv.class);
      inOrder.verify(sideEffects).applyPreRemoveSideEffects("max.rows");
      inOrder.verify(sreeEnv, () -> SreeEnv.remove("max.rows"));
      inOrder.verify(sreeEnv, SreeEnv::save);
      inOrder.verify(sideEffects).applyPostRemoveSideEffects("max.rows");
   }

   @Test void alwaysAuditsWhenSaveThrows() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows", false, false))
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

   @Test void writesAnAllowListedCredentialThroughSetPasswordSoItIsEncryptedAtRest() {
      // openid.client.secret is read by OpenIDConfig.getClientSecret via Tool.decryptPassword, so
      // a raw setProperty would leave plaintext where every reader expects ciphertext.
      sreeEnv.when(() -> SreeEnv.getProperty("openid.client.secret", false, false))
             .thenReturn(null)              // before
             .thenReturn("ENC(cipher)");    // after read-back, stored form
      sreeEnv.when(() -> SreeEnv.getPassword("openid.client.secret")).thenReturn("s3cret");

      AdminChangeResult res = service.applyChange(req("openid.client.secret", "s3cret"), principal);

      sreeEnv.verify(() -> SreeEnv.setPassword("openid.client.secret", "s3cret"));
      sreeEnv.verify(() -> SreeEnv.setProperty("openid.client.secret", "s3cret"), never());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
   }

   @Test void verifiesACredentialThroughGetPasswordNotTheRawStoredValue() {
      // Comparing the ciphertext read-back against the plaintext desired value would mark every
      // successful credential write FAILED and roll it straight back.
      sreeEnv.when(() -> SreeEnv.getProperty("openid.client.secret", false, false))
             .thenReturn(null)
             .thenReturn("ENC(cipher)");
      sreeEnv.when(() -> SreeEnv.getPassword("openid.client.secret")).thenReturn("s3cret");

      AdminChangeResult res = service.applyChange(req("openid.client.secret", "s3cret"), principal);

      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
      // The stored form is what is reported and audited, because rollback replays it verbatim.
      assertEquals("ENC(cipher)", res.getAfterValue());
   }

   @Test void rollingBackACredentialWritesTheStoredFormVerbatimWithoutReEncrypting() {
      // The rollback value came from a getProperty read, so it is ALREADY ciphertext. Sending it
      // through setPassword would encrypt it a second time and restore something that never
      // decrypts back to the original secret.
      AdminChangeRequest r = req("openid.client.secret", "ENC(previous)");
      r.setAction(AdminChangeRecord.ACTION_ROLLBACK);
      sreeEnv.when(() -> SreeEnv.getProperty("openid.client.secret", false, false))
             .thenReturn("ENC(cipher)")
             .thenReturn("ENC(previous)");

      AdminChangeResult res = service.applyChange(r, principal);

      sreeEnv.verify(() -> SreeEnv.setProperty("openid.client.secret", "ENC(previous)"));
      sreeEnv.verify(() -> SreeEnv.setPassword(anyString(), anyString()), never());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
   }

   @Test void writesASecretNamedPropertyThatIsNotAnAllowListedCredentialVerbatim() {
      // log.fluentd.security.password matches isSecret but is read with a plain getProperty.
      // Encrypting it would hand the fluentd client ciphertext as its password. (The plan service
      // refuses this property outright; this pins the write path regardless of how it is reached.)
      sreeEnv.when(() -> SreeEnv.getProperty("log.fluentd.security.password", false, false))
             .thenReturn(null)
             .thenReturn("literal");

      service.applyChange(req("log.fluentd.security.password", "literal"), principal);

      sreeEnv.verify(() -> SreeEnv.setProperty("log.fluentd.security.password", "literal"));
      sreeEnv.verify(() -> SreeEnv.setPassword(anyString(), anyString()), never());
   }
}
