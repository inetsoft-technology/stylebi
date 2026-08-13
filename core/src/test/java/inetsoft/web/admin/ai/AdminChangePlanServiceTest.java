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
   /** U+001F, built from a Java escape sequence rather than pasted as a raw control byte. */
   private static final String SEPARATOR_ESCAPE = "\u001f";

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
   void rejectsAChangeToASecretProperty() {
      // Finding 5a: password.encryption.key is StyleBI's password-encryption master key; blanking
      // it through admin-chat would make every stored encrypted credential undecryptable. Unlike
      // the read path (which withholds the value but still shows the property), a WRITE is
      // refused outright.
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("t", "password.encryption.key", "x")))
            .getMessage().contains("password.encryption.key"));
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

   @Test
   void rejectsAStoredCurrentValueContainingAControlCharacter() {
      // currentValue is read from SreeEnv, not supplied by the caller, so it needs its own guard:
      // a control character already present in stored config would otherwise forge a field
      // boundary in the canonical form just as a hostile request value would.
      sreeEnv.when(() -> SreeEnv.getProperty("mail.smtp.host", false, false))
         .thenReturn("stored" + SEPARATOR_ESCAPE + "value");
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("t", "mail.smtp.host", "new.example.com")))
            .getMessage().startsWith("currentValue:"));
   }

   @Test
   void hashDistinguishesAnUnsetValueFromTheLiteralStringNull() {
      sreeEnv.when(() -> SreeEnv.getProperty("mail.smtp.host", false, false)).thenReturn(null);
      String unsetHash = service.resolve(request("t", "mail.smtp.host", "x")).planHash();

      sreeEnv.when(() -> SreeEnv.getProperty("mail.smtp.host", false, false)).thenReturn("null");
      assertNotEquals(unsetHash, service.resolve(request("t", "mail.smtp.host", "x")).planHash());
   }

   @Test void allowsAnAllowListedCredentialIntoThePlan() {
      // The change that unblocks configuring OIDC SSO end to end. Reading it is still refused;
      // this is the write path only.
      PlanRequest req = request("set the OIDC client secret",
                                    "openid.client.secret", "s3cret");
      ResolvedPlan plan = service.resolve(req);
      assertEquals(1, plan.changes().size());
      assertEquals("s3cret", plan.changes().get(0).proposedValue());
   }

   @Test void masksACredentialsCurrentValueInThePlan() {
      // The plan is relayed to a model provider. Ciphertext is not the secret, but there is no
      // reason to ship it, and "(set)" is what actually helps an operator reading the diff.
      sreeEnv.when(() -> SreeEnv.getProperty("openid.client.secret", false, false))
             .thenReturn("ENC(cipher)");
      ResolvedPlan plan = service.resolve(
         request("rotate it", "openid.client.secret", "new"));
      assertEquals("(set)", plan.changes().get(0).currentValue());
   }

   @Test void stillRefusesASecretThatIsNotAnAllowListedCredential() {
      // password.encryption.key is the master key; log.fluentd.security.password is read with a
      // plain getProperty. Neither may be written here, and the allow-list is why.
      for(String prop : new String[] { "password.encryption.key", "jwt.signing.key",
                                       "log.fluentd.security.password", "license.key" })
      {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resolve(request("try it", prop, "x")));
         assertTrue(ex.getMessage().contains(prop), "expected the error to name " + prop);
      }
   }

   @Test void refusesACredentialWhenCloudSecretsAreConfigured() {
      // In that mode the property holds the NAME of a secret, not the secret, so writing a literal
      // value would store something nothing downstream can resolve. Scoped to this test: a
      // class-wide Tool mock would silently change every other test's environment.
      try(MockedStatic<Tool> tool = mockStatic(Tool.class, withSettings().strictness(
             Strictness.LENIENT)))
      {
         tool.when(Tool::isCloudSecrets).thenReturn(true);
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resolve(request("set it", "openid.client.secret", "s3cret")));
         assertTrue(ex.getMessage().contains("cloud secrets"));
      }
   }

   @Test void refusesAnEmptyValueForACredentialAndSaysWhatToUseInstead() {
      // "" is an explicit set-to-empty everywhere else, but SreeEnv.setPassword guards on
      // isEmptyString and returns without writing, so it would silently leave the old secret in
      // place. Refused here so the message can point at null, rather than surfacing as a bare
      // FAILED from the read-back verify at apply time.
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("blank it", "openid.client.secret", "")));
      assertTrue(ex.getMessage().contains("openid.client.secret"));
      assertTrue(ex.getMessage().contains("null"));
   }

   @Test void stillAllowsResettingACredentialWithANullValue() {
      // Reset goes through SreeEnv.remove, not setPassword, so it is unaffected by the guard above
      // and remains the supported way to clear a credential.
      ResolvedPlan plan = service.resolve(request("clear it", "openid.client.secret", null));
      assertEquals(1, plan.changes().size());
      assertNull(plan.changes().get(0).proposedValue());
   }
}
