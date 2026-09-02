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
      // Belt-and-braces: no test above this line sets it, but a fault-injection test failing
      // mid-assertion must never leave the JVM system property flag on for every test after it.
      System.clearProperty(FAULT_INJECTION_ENABLED_PROPERTY);
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
      // license.key matches isSecret on its prefix but every writer in the product stores it with
      // a plain setProperty. Encrypting it would hand the license check ciphertext where it
      // expects a key. (The plan service refuses this property outright; this pins the write path
      // regardless of how it is reached.)
      //
      // This test used log.fluentd.security.password until Redmine #76170, on the reasoning that
      // its reader called getProperty. Redmine #76051 had already made its writer encrypt, so the
      // property it pinned had moved out from under it - see the test below, which now pins the
      // opposite behaviour for that name.
      sreeEnv.when(() -> SreeEnv.getProperty("license.key", false, false))
             .thenReturn(null)
             .thenReturn("literal");

      service.applyChange(req("license.key", "literal"), principal);

      sreeEnv.verify(() -> SreeEnv.setProperty("license.key", "literal"));
      sreeEnv.verify(() -> SreeEnv.setPassword(anyString(), anyString()), never());
   }

   @Test void encryptsTheFluentdPasswordNowThatItsWriterDoesToo() {
      // Redmine #76170. Redmine #76051 (dc8877f8a) moved LogSettingService's write of this
      // property onto the same toPassword helper as log.fluentd.security.sharedkey, which calls
      // Tool.encryptPassword - so the two adjacent halves of one Logging page are finally written
      // the same way. Until it was allow-listed, admin-chat refused to set it at all; a write that
      // got through would have put plaintext where the Logging page stores ciphertext.
      sreeEnv.when(() -> SreeEnv.getProperty("log.fluentd.security.password", false, false))
             .thenReturn(null)
             .thenReturn("ENC(cipher)");
      sreeEnv.when(() -> SreeEnv.getPassword("log.fluentd.security.password"))
             .thenReturn("fluentd-pw");

      AdminChangeResult res =
         service.applyChange(req("log.fluentd.security.password", "fluentd-pw"), principal);

      sreeEnv.verify(() -> SreeEnv.setPassword("log.fluentd.security.password", "fluentd-pw"));
      sreeEnv.verify(
         () -> SreeEnv.setProperty(eq("log.fluentd.security.password"), anyString()), never());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
   }

   @Test void refusesToWriteACredentialAtApplyTimeWhenCloudSecretsAreOn() {
      // The plan service refuses this at preview, but a plan is approved then executed later. If
      // cloud secrets came on in between, setPassword would skip encryption and write the literal
      // secret into a property everything downstream reads as a secret-manager reference - and
      // report success. The apply path re-checks for that reason.
      toolStatic.when(Tool::isCloudSecrets).thenReturn(true);
      sreeEnv.when(() -> SreeEnv.getProperty("openid.client.secret", false, false))
             .thenReturn(null);

      AdminChangeResult res = service.applyChange(req("openid.client.secret", "s3cret"), principal);

      assertEquals(AdminChangeRecord.STATUS_FAILED, res.getStatus());
      assertTrue(res.getError().contains("cloud secrets"));
      sreeEnv.verify(() -> SreeEnv.setPassword(anyString(), anyString()), never());
      sreeEnv.verify(() -> SreeEnv.setProperty(eq("openid.client.secret"), anyString()), never());
   }

   @Test void stillRollsBackACredentialUnderCloudSecrets() {
      // A rollback writes the stored form verbatim and never encrypts, so the cloud-secrets hazard
      // does not apply to it - and blocking it would strand a half-applied changeset.
      toolStatic.when(Tool::isCloudSecrets).thenReturn(true);
      AdminChangeRequest r = req("openid.client.secret", "secret-ref");
      r.setAction(AdminChangeRecord.ACTION_ROLLBACK);
      sreeEnv.when(() -> SreeEnv.getProperty("openid.client.secret", false, false))
             .thenReturn("other-ref")
             .thenReturn("secret-ref");

      AdminChangeResult res = service.applyChange(r, principal);

      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
      sreeEnv.verify(() -> SreeEnv.setProperty("openid.client.secret", "secret-ref"));
   }

   @Test void doesNotReEncryptACredentialOnRestoreEither() {
      // RESTORE is accepted by requireValidAction and issued by nothing today, but by its name it
      // replays a stored value the way rollback does. encryptOnWrite therefore names APPLY rather
      // than excluding ROLLBACK: a deny-list would encrypt this and double-encrypt the credential,
      // in a path with no caller to notice.
      AdminChangeRequest r = req("openid.client.secret", "ENC(fromBackup)");
      r.setAction(AdminChangeRecord.ACTION_RESTORE);
      sreeEnv.when(() -> SreeEnv.getProperty("openid.client.secret", false, false))
             .thenReturn("ENC(current)")
             .thenReturn("ENC(fromBackup)");

      AdminChangeResult res = service.applyChange(r, principal);

      sreeEnv.verify(() -> SreeEnv.setProperty("openid.client.secret", "ENC(fromBackup)"));
      sreeEnv.verify(() -> SreeEnv.setPassword(anyString(), anyString()), never());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
   }

   @Test void encryptsAMailCredentialThatTheNamePredicateDoesNotMatch() {
      // mail.smtp.pass is not caught by isSecret, so before it was allow-listed admin-chat wrote
      // it with a plain setProperty while EmailSettingsService writes it with setPassword -
      // storing the SMTP password unencrypted at rest, and "succeeding" because the defensive
      // decrypt on read passes plaintext through.
      sreeEnv.when(() -> SreeEnv.getProperty("mail.smtp.pass", false, false))
             .thenReturn(null)
             .thenReturn("ENC(cipher)");
      sreeEnv.when(() -> SreeEnv.getPassword("mail.smtp.pass")).thenReturn("hunter2");

      AdminChangeResult res = service.applyChange(req("mail.smtp.pass", "hunter2"), principal);

      sreeEnv.verify(() -> SreeEnv.setPassword("mail.smtp.pass", "hunter2"));
      sreeEnv.verify(() -> SreeEnv.setProperty(eq("mail.smtp.pass"), anyString()), never());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
   }

   @Test void writesTheAdjacentPlaintextMailPropertyVerbatim() {
      // mail.smtp.tokenuri sits in the same save block as the four encrypted ones and is written
      // with a plain setProperty. Encrypting it would store ciphertext where a URL is expected.
      sreeEnv.when(() -> SreeEnv.getProperty("mail.smtp.tokenuri", false, false))
             .thenReturn(null)
             .thenReturn("https://oauth2.example/token");

      service.applyChange(req("mail.smtp.tokenuri", "https://oauth2.example/token"), principal);

      sreeEnv.verify(() -> SreeEnv.setProperty("mail.smtp.tokenuri",
                                               "https://oauth2.example/token"));
      sreeEnv.verify(() -> SreeEnv.setPassword(anyString(), anyString()), never());
   }

   // ── test-only fault injection (Track A#1, docs/teams/2026-08-26-a1-fault-injection) ──────

   /**
    * Mirrors the private constant in {@code AdminChangeService} - kept as a literal here
    * deliberately, so a test failure that changes the constant's spelling is caught as a real
    * regression rather than silently recompiling against whatever the source currently says.
    */
   private static final String FAULT_INJECTION_ENABLED_PROPERTY =
      "inetsoft.admin.ai.faultInjection.enabled";

   @Test void doesNothingWhenTheReservedNameIsUsedButTheFlagIsNotSet() throws Exception {
      // The flag is never set in this test - proves the reserved-name pattern alone is inert,
      // which is the whole point of requiring BOTH gates.
      sreeEnv.when(() -> SreeEnv.getProperty("test.faultinjection.apply.throw.p1", false, false))
             .thenReturn(null).thenReturn("500");

      AdminChangeResult res =
         service.applyChange(req("test.faultinjection.apply.throw.p1", "500"), principal);

      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
      sreeEnv.verify(() -> SreeEnv.setProperty("test.faultinjection.apply.throw.p1", "500"));
   }

   @Test void doesNothingForAnOrdinaryPropertyEvenWhenTheFlagIsSet() throws Exception {
      // The flag alone is not enough either - an unrelated property name must behave exactly as
      // it does in every other test in this file.
      System.setProperty(FAULT_INJECTION_ENABLED_PROPERTY, "true");
      sreeEnv.when(() -> SreeEnv.getProperty("max.rows", false, false))
             .thenReturn("100").thenReturn("500");

      AdminChangeResult res = service.applyChange(req("max.rows", "500"), principal);

      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
      sreeEnv.verify(() -> SreeEnv.setProperty("max.rows", "500"));
   }

   @Test void throwModeThrowsOnApplyWithoutTouchingSreeEnv() {
      System.setProperty(FAULT_INJECTION_ENABLED_PROPERTY, "true");

      AdminChangeService.AdminChangeFaultInjectedException thrown = assertThrows(
         AdminChangeService.AdminChangeFaultInjectedException.class,
         () -> service.applyChange(req("test.faultinjection.apply.throw.p2", "x"), principal));

      assertTrue(thrown.getMessage().contains("test.faultinjection.apply.throw.p2"));
      sreeEnv.verifyNoInteractions();
      // A throw-mode probe propagates BEFORE writeAudit runs (see AdminChangeService's own
      // placement, before its try block) - the caller (AdminChangesetApplyService) is the one
      // that must decide how to record an unknown-state failure, not this method.
      verifyNoInteractions(audit);
   }

   @Test void failModeReturnsFailedWithoutThrowingOrTouchingSreeEnv() {
      System.setProperty(FAULT_INJECTION_ENABLED_PROPERTY, "true");

      AdminChangeResult res =
         service.applyChange(req("test.faultinjection.apply.fail.p3", "x"), principal);

      assertEquals(AdminChangeRecord.STATUS_FAILED, res.getStatus());
      assertTrue(res.getError().contains("test.faultinjection.apply.fail.p3"));
      sreeEnv.verifyNoInteractions();
      ArgumentCaptor<AdminChangeRecord> cap = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(audit).auditAdminChange(cap.capture(), eq(principal));
      assertEquals(AdminChangeRecord.STATUS_FAILED, cap.getValue().getStatus());
      assertNull(cap.getValue().getBeforeValue());
      assertNull(cap.getValue().getAfterValue());
   }

   @Test void aProbeOnlyFiresOnItsConfiguredAction() throws Exception {
      // An "apply"-mode probe must behave as an ORDINARY property (real SreeEnv write) when the
      // action is ROLLBACK - this is what lets a single probe apply cleanly and only fail its own
      // later rollback (the "rollback" mode probes below), or vice versa.
      System.setProperty(FAULT_INJECTION_ENABLED_PROPERTY, "true");
      AdminChangeRequest r = req("test.faultinjection.apply.throw.p4", "restored");
      r.setAction(AdminChangeRecord.ACTION_ROLLBACK);
      sreeEnv.when(() -> SreeEnv.getProperty("test.faultinjection.apply.throw.p4", false, false))
             .thenReturn("other").thenReturn("restored");

      AdminChangeResult res = service.applyChange(r, principal);

      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
      sreeEnv.verify(
         () -> SreeEnv.setProperty("test.faultinjection.apply.throw.p4", "restored"));
   }

   @Test void rollbackModeAppliesNormallyThenThrowsOnlyOnItsOwnRollback() throws Exception {
      System.setProperty(FAULT_INJECTION_ENABLED_PROPERTY, "true");
      sreeEnv.when(() -> SreeEnv.getProperty("test.faultinjection.rollback.throw.p5", false, false))
             .thenReturn("before").thenReturn("after");

      // APPLY: fires no probe (this one only matches action=rollback), writes for real.
      AdminChangeResult applied =
         service.applyChange(req("test.faultinjection.rollback.throw.p5", "after"), principal);
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, applied.getStatus());
      sreeEnv.verify(
         () -> SreeEnv.setProperty("test.faultinjection.rollback.throw.p5", "after"));

      // ROLLBACK of the same property: now the probe fires and throws, no further SreeEnv write.
      AdminChangeRequest rollback =
         req("test.faultinjection.rollback.throw.p5", "before");
      rollback.setAction(AdminChangeRecord.ACTION_ROLLBACK);

      assertThrows(AdminChangeService.AdminChangeFaultInjectedException.class,
         () -> service.applyChange(rollback, principal));
      // Still only the one setProperty call from the apply above - the rollback attempt wrote
      // nothing.
      sreeEnv.verify(
         () -> SreeEnv.setProperty(eq("test.faultinjection.rollback.throw.p5"), anyString()),
         times(1));
   }

   @Test void rejectsAMalformedFaultInjectionNameEvenWithTheFlagSet() throws Exception {
      // Close enough to the reserved namespace to be a plausible typo, but not a real match
      // (missing the trailing label segment) - must fall through to the ordinary path rather
      // than silently matching a broader pattern than intended.
      System.setProperty(FAULT_INJECTION_ENABLED_PROPERTY, "true");
      sreeEnv.when(() -> SreeEnv.getProperty("test.faultinjection.apply.throw", false, false))
             .thenReturn(null).thenReturn("x");

      AdminChangeResult res =
         service.applyChange(req("test.faultinjection.apply.throw", "x"), principal);

      assertEquals(AdminChangeRecord.STATUS_VERIFIED, res.getStatus());
      sreeEnv.verify(() -> SreeEnv.setProperty("test.faultinjection.apply.throw", "x"));
   }
}
