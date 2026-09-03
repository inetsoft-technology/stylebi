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

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The catalog only earns its keep if every entry is a REAL property. A plausible-but-nonexistent
 * name is worse than an absent one: the server would snapshot null, apply, verify null and report
 * success for a property nothing reads. Hence the seeded-names test below.
 *
 * Alias resolution must also survive an org prefix, or every org-scoped property loses type
 * validation, allowed values and its description.
 */
@Tag("core")
class AdminPropertyCatalogTest {
   private final AdminPropertyCatalog catalog = new AdminPropertyCatalog();

   @Test
   void seedsOnlyRealStyleBiProperties() {
      // Traced to SreeEnv call sites: PerformanceSettingsService, LogSettingService,
      // EmailSettingsService, PropertyChangeSideEffects.
      for(String name : new String[] { "query.runtime.maxrow", "log.detail.level",
                                       "mail.smtp.host", "security.exposedefaultorgtoall" })
      {
         assertNotNull(catalog.getEntry(AdminPropertyName.parse(name)), name);
      }
   }

   @Test
   void resolvesAnAliasToItsCanonicalName() {
      assertEquals("query.runtime.maxrow", catalog.resolve("  Max.Rows ").key());
      assertEquals("log.detail.level", catalog.resolve("log.level").key());
   }

   @Test
   void resolvesAnAliasUnderAnOrgPrefix() {
      AdminPropertyName name = catalog.resolve("inetsoft.org.acme.smtp.host");
      assertEquals("inetsoft.org.acme.mail.smtp.host", name.key());
      assertEquals("mail.smtp.host", name.baseName());
      assertEquals("acme", name.orgId());
   }

   @Test
   void findsAnEntryForAnOrgQualifiedName() {
      CatalogEntry entry = catalog.getEntry(AdminPropertyName.parse("inetsoft.org.acme.mail.smtp.host"));
      assertNotNull(entry);
      assertEquals("mail.smtp.host", entry.name());
   }

   @Test
   void returnsNullForAnUncataloguedProperty() {
      assertNull(catalog.getEntry(AdminPropertyName.parse("does.not.exist")));
   }

   @Test
   void findsAnEntryForABaseNameThatDiffersOnlyInCase() {
      // Finding 4: byKey's keys are always lowercased when the catalog is built, but
      // AdminPropertyName.baseName() preserves case verbatim for four families (log.level.*,
      // plugin.extra.classpath.*, etc.) - the whole point of those families. None of the seeded
      // catalog entries happens to fall in one of those families, so this test constructs an
      // AdminPropertyName directly (bypassing AdminPropertyName.parse, which would itself
      // lowercase an ordinary name) to simulate exactly the shape a case-preserving family
      // produces: a mixed-case baseName that matches a catalogued entry only case-insensitively.
      AdminPropertyName mixedCase = new AdminPropertyName("Mail.SMTP.Host", "Mail.SMTP.Host", null);
      CatalogEntry entry = catalog.getEntry(mixedCase);
      assertNotNull(entry);
      assertEquals("mail.smtp.host", entry.name());
   }

   @Test
   void leavesAnUnknownNameUnchangedWhenResolving() {
      assertEquals("some.unknown.prop", catalog.resolve("Some.Unknown.Prop").key());
   }

   @Test
   void acceptsAnInRangeInt() {
      CatalogEntry entry = catalog.getEntry(AdminPropertyName.parse("query.runtime.maxrow"));
      assertEquals("500", catalog.canonicalizeValue(entry, " 500 "));
   }

   @Test
   void rejectsANonNumericInt() {
      CatalogEntry entry = catalog.getEntry(AdminPropertyName.parse("query.runtime.maxrow"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> catalog.canonicalizeValue(entry, "abc")).getMessage()
            .contains("query.runtime.maxrow"));
   }

   @Test
   void rejectsAnOutOfRangeInt() {
      CatalogEntry entry = catalog.getEntry(AdminPropertyName.parse("query.runtime.maxrow"));
      assertThrows(IllegalArgumentException.class, () -> catalog.canonicalizeValue(entry, "-1"));
   }

   @Test
   void canonicalizesEnumCaseToWhatStyleBiStores() {
      // log.detail.level stores LogLevel.level() strings, which are lowercase - NOT "INFO".
      CatalogEntry entry = catalog.getEntry(AdminPropertyName.parse("log.detail.level"));
      assertEquals("info", catalog.canonicalizeValue(entry, "INFO"));
      assertEquals("warn", catalog.canonicalizeValue(entry, " Warn "));
   }

   @Test
   void rejectsAnEnumValueOutsideAllowedValues() {
      CatalogEntry entry = catalog.getEntry(AdminPropertyName.parse("log.detail.level"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> catalog.canonicalizeValue(entry, "trace")).getMessage().contains("debug"));
   }

   @Test
   void canonicalizesBooleanCase() {
      CatalogEntry entry = catalog.getEntry(
         AdminPropertyName.parse("security.exposedefaultorgtoall"));
      assertEquals("true", catalog.canonicalizeValue(entry, "TRUE"));
      assertThrows(IllegalArgumentException.class, () -> catalog.canonicalizeValue(entry, "yes"));
   }

   @Test
   void trimsAStringValue() {
      // The hash covers the exact value that will be written, and AdminChangeService writes this
      // method's return value verbatim (it no longer trims) - so canonicalizeValue must trim, or
      // the operator could approve " smtp.example.com " while "smtp.example.com" gets written.
      CatalogEntry entry = catalog.getEntry(AdminPropertyName.parse("mail.smtp.host"));
      assertEquals("smtp.example.com", catalog.canonicalizeValue(entry, " smtp.example.com "));
   }

   @Test
   void allowsNullAsAReset() {
      CatalogEntry entry = catalog.getEntry(AdminPropertyName.parse("query.runtime.maxrow"));
      assertNull(catalog.canonicalizeValue(entry, null));
   }

   @Test
   void classifiesEncryptedCredentialsByTheirWriterNotTheirName() {
      // The properties Enterprise Manager writes through an encrypting accessor. Before the four
      // the name predicate does NOT match were listed, admin-chat wrote them plain and silently
      // downgraded a stored credential to plaintext at rest.
      //
      // log.fluentd.security.password is here as of Redmine #76170: Redmine #76051 (dc8877f8a)
      // moved LogSettingService's write of it onto the same toPassword helper as the shared key,
      // and the entry that should have followed did not. Unlike the four above, its name matches,
      // so nothing leaked - the cost was that admin-chat refused to set a property it could have
      // set correctly.
      for(String name : new String[] { "mail.smtp.pass", "mail.smtp.clientsecret",
                                       "mail.smtp.accesstoken", "mail.smtp.refreshtoken",
                                       "log.fluentd.security.sharedkey",
                                       "log.fluentd.security.password",
                                       "openid.client.secret",
                                       "stylebi.google.openid.client.secret" })
      {
         assertTrue(AdminPropertyCatalog.isEncryptedCredential(name),
                    name + " is written with setPassword/encryptPassword and must be listed");
      }
   }

   @Test
   void excludesAdjacentPropertiesWhoseWriterDoesNotEncrypt() {
      // Each of these sits beside a listed property and reads like it belongs. None does.
      //   mail.smtp.tokenuri              same save block as the four above, plain setProperty -
      //                                   and it is READ with SreeEnv.getPassword, which decrypts,
      //                                   so by its reader it looks encrypted. It is not. This is
      //                                   the standing example of why only the writer settles it,
      //                                   because decryptPassword passes plaintext straight
      //                                   through (LocalPasswordEncryption's clear-text branch).
      //   auth0.client.secret             read plain; the legacy migration encrypts on the way out
      //   sso.rsa.public.key              a PUBLIC key
      //
      // log.fluentd.security.password used to head this list and no longer belongs to it - see
      // classifiesEncryptedCredentialsByTheirWriterNotTheirName above. It is the reason this test
      // asserts against the WRITER and not against a remembered classification.
      for(String name : new String[] { "mail.smtp.tokenuri",
                                       "auth0.client.secret", "sso.rsa.public.key",
                                       "google.maps.key", "enable.changepassword",
                                       "password.encryption.key", "jwt.signing.key" })
      {
         assertFalse(AdminPropertyCatalog.isEncryptedCredential(name),
                     name + " is not written encrypted and must not be listed");
      }
   }

   @Test
   void matchesAnEncryptedCredentialRegardlessOfTheCasingTheCallerUses() {
      // Source spells several of these camelCase (mail.smtp.clientSecret,
      // styleBI.google.openid.client.secret); the store lowercases via computePropertyNameCase.
      assertTrue(AdminPropertyCatalog.isEncryptedCredential("mail.smtp.clientSecret"));
      assertTrue(AdminPropertyCatalog.isEncryptedCredential("styleBI.google.openid.client.secret"));
      assertTrue(AdminPropertyCatalog.isEncryptedCredential("log.fluentd.security.sharedKey"));
   }

   @Test
   void withholdsEveryEncryptedCredentialOnReadAndNotOnlyTheNamesThePatternMatches() {
      // Redmine #76006. isSecret was a name test alone, so these four were returned in the clear
      // to a caller that relays responses to a model provider off-host. mail.smtp.pass carries
      // "pass" and not "password"; the two OAuth tokens carry none of the five substrings;
      // sharedkey ends in "key" but not ".key" and missed by the separator. Their neighbour
      // mail.smtp.clientsecret matched all along, so one settings block was split arbitrarily.
      for(String name : new String[] { "mail.smtp.pass", "mail.smtp.accesstoken",
                                       "mail.smtp.refreshtoken", "log.fluentd.security.sharedkey" })
      {
         assertFalse(matchesTheNamePattern(name),
                     name + ": the point of this test is that the NAME does not match - if it now "
                     + "does, the union below is no longer what covers it");
         assertTrue(AdminPropertyCatalog.isSecret(name),
                    name + " is written encrypted and must not be read back through admin-chat");
      }
   }

   @Test
   void keepsWithholdingSecretNamesThatNoWriterEncrypts() {
      // The union must not have narrowed to the allow-list. Most of the names isSecret withheld
      // before #76006 are written by nothing that encrypts, so the writer test alone would have
      // unmasked them all in order to close the four that needed closing.
      //
      // Deliberately no count here or in the isSecret javadoc. The javadoc said "fifteen" and this
      // comment said "fourteen" for the same group, and both were left behind by the three
      // CONFIRMED_NOT_SECRET carve-outs and again by log.fluentd.security.password moving to the
      // writer-classified list in #76170. What matters is that the group is non-empty, which the
      // loop asserts.
      for(String name : new String[] { "license.key", "jwt.signing.key", "password.encryption.key",
                                       "sso.rsa.private.key" })
      {
         assertFalse(AdminPropertyCatalog.isEncryptedCredential(name),
                     name + ": nothing encrypts this on write, so only the name test can cover it");
         assertTrue(AdminPropertyCatalog.isSecret(name), name + " must stay withheld");
      }
   }

   @Test
   void withholdsABearerCredentialThatNeitherItsNameNorItsWriterGivesAway() {
      // Redmine #76170. A Slack or Google Chat incoming-webhook URL carries its token in the path:
      // possession is sufficient to post to that channel, with no second factor and no request
      // signature. Both were returned in full to a caller that relays responses to a model
      // provider off-host, because the two tests that existed ask about the NAME and about the
      // STORAGE FORM, and a bearer token can be unremarkable in both.
      for(String name : new String[] { "share.slack.url", "share.googlechat.url" })
      {
         assertFalse(matchesTheNamePattern(name),
                     name + ": the point of this test is that the NAME does not match - if it now "
                     + "does, CONFIRMED_SECRET is no longer what covers it");
         assertFalse(AdminPropertyCatalog.isEncryptedCredential(name),
                     name + ": nothing encrypts this on write, so the writer test cannot cover it "
                     + "either - and listing it there would make admin-chat store ciphertext "
                     + "under a property every reader expects to hold a literal URL");
         assertTrue(AdminPropertyCatalog.isSecret(name),
                    name + " grants access to whoever holds it and must not be read back");
      }
   }

   @Test
   void withholdsABearerCredentialWhateverCasingTheCallerUses() {
      assertTrue(AdminPropertyCatalog.isSecret("Share.Slack.URL"));
      assertTrue(AdminPropertyCatalog.isSecret("SHARE.GOOGLECHAT.URL"));
   }

   @Test
   void withholdsOnlyTheWebhookUrlAndNotTheShareSettingsAroundIt() {
      // The carve-out must be the two names, not the share.* prefix. An operator needs to see
      // whether sharing is enabled, and only the URL is the credential.
      for(String name : new String[] { "share.googlechat.enabled", "share.slack.enabled",
                                       "share.email.enabled" })
      {
         assertFalse(AdminPropertyCatalog.isSecret(name), name + " is not a secret");
      }
   }

   @Test
   void keepsTheHandVerifiedNameListsDisjoint() throws Exception {
      // isSecret checks CONFIRMED_NOT_SECRET first and returns early, so a name in both sets is
      // read back in the clear with nothing to indicate the contradiction. The ordering inside
      // isSecret is only safe while this holds.
      //
      // Reflected over the actual fields rather than asserted through isSecret over hardcoded
      // names. That spelling looked equivalent and was not: adding one of the CONFIRMED_NOT_SECRET
      // names to CONFIRMED_SECRET leaves assertFalse(isSecret(name)) passing, because the early
      // return still fires - so the test would have gone green on precisely the contradiction it
      // claims to catch. Reading the sets is the only way to assert a property OF the sets.
      //
      // COMPOSITE_SECRET_PROPERTIES and ENCRYPTED_CREDENTIALS sit in the same union, so the early
      // return shadows them identically - every positive set is checked here, not just the two
      // this bug happened to touch. ENCRYPTED_CREDENTIALS matters most of the three: a name in it
      // AND in CONFIRMED_NOT_SECRET is read back in the clear with no diagnostic at all, which is
      // exactly the failure this test was written to catch.
      Set<String> notSecret = nameSet("CONFIRMED_NOT_SECRET");

      assertFalse(notSecret.isEmpty(),
                  "CONFIRMED_NOT_SECRET is empty - the checks below would be vacuous");

      for(String field :
          List.of("CONFIRMED_SECRET", "COMPOSITE_SECRET_PROPERTIES", "ENCRYPTED_CREDENTIALS"))
      {
         Set<String> secret = nameSet(field);
         assertFalse(secret.isEmpty(), field + " is empty - the check below would be vacuous");

         Set<String> both = new HashSet<>(secret);
         both.retainAll(notSecret);
         assertTrue(both.isEmpty(),
                    "a name cannot be both a verified secret and a verified non-secret; " +
                    "CONFIRMED_NOT_SECRET wins silently because isSecret returns early on it: " +
                    field + " " + both);
      }
   }

   @SuppressWarnings("unchecked")
   private static Set<String> nameSet(String field) throws Exception {
      Field f = AdminPropertyCatalog.class.getDeclaredField(field);
      f.setAccessible(true);
      return (Set<String>) f.get(null);
   }

   @Test
   void unmasksConfirmedFalsePositivesTheShapeTestCaughtForTheWrongReason() {
      // Known gap 4 (plugin/admin/README.md): the name-shape test over-matched these three.
      // Each was individually verified against its writer/reader before being carved out, the
      // same way ENCRYPTED_CREDENTIALS is verified - narrowing the shared pattern instead would
      // risk unmasking an unrelated property that happens to share its shape.
      for(String name : new String[] { "enable.changepassword", "sso.rsa.public.key",
                                       "google.maps.key" })
      {
         assertFalse(AdminPropertyCatalog.isSecret(name),
                     name + " is not a credential and must be readable through admin-chat");
      }

      // sso.rsa.public.key's PRIVATE counterpart must still be withheld - the exception list
      // must name the exact false positive, not loosen the .key suffix test generally.
      assertTrue(AdminPropertyCatalog.isSecret("sso.rsa.private.key"),
                 "sso.rsa.private.key is the actual secret and must stay withheld");
   }

   @Test
   void unmasksAConfirmedFalsePositiveWhateverCasingTheCallerUses() {
      assertFalse(AdminPropertyCatalog.isSecret("Enable.ChangePassword"));
      assertFalse(AdminPropertyCatalog.isSecret("SSO.RSA.Public.Key"));
      assertFalse(AdminPropertyCatalog.isSecret("Google.Maps.Key"));
   }

   @Test
   void leavesAnOrdinaryPropertyReadableIncludingTheOnesSittingBesideACredential() {
      // mail.smtp.tokenuri shares a save block with three of the four newly withheld names and
      // log.fluentd.security.username shares a page with sharedkey. Withholding either would cost
      // an operator a value they need and that nothing treats as sensitive.
      for(String name : new String[] { "mail.smtp.tokenuri", "mail.smtp.user", "mail.smtp.host",
                                       "log.fluentd.security.username", "query.runtime.maxrow" })
      {
         assertFalse(AdminPropertyCatalog.isSecret(name), name + " is not a secret");
      }
   }

   @Test
   void withholdsACredentialWhateverCasingTheCallerUses() {
      // Source spells these camelCase; the store lowercases through computePropertyNameCase, and
      // an agent will send either form.
      assertTrue(AdminPropertyCatalog.isSecret("mail.smtp.accessToken"));
      assertTrue(AdminPropertyCatalog.isSecret("mail.smtp.refreshToken"));
      assertTrue(AdminPropertyCatalog.isSecret("log.fluentd.security.sharedKey"));
   }

   @Test
   void withholdsServerSaveLocationsEvenThoughNeitherTheNamePatternNorEncryptedCredentialCoversIt() {
      // SchedulerConfigurationService.setServerLocations embeds a plaintext password as the
      // fourth pipe-delimited field of a "path|label|username|password" segment when the operator
      // types credentials directly, and writes the whole "server.save.locations" property with a
      // plain SreeEnv.setProperty. The name matches none of the five isSecret substrings/suffixes
      // and is not a single encrypted value, so it belongs in neither the name test nor
      // ENCRYPTED_CREDENTIALS - only the dedicated COMPOSITE_SECRET_PROPERTIES withhold list.
      assertFalse(matchesTheNamePattern("server.save.locations"),
                  "the point of this test is that the NAME does not match - if it now does, the "
                  + "union below is no longer what covers it");
      assertFalse(AdminPropertyCatalog.isEncryptedCredential("server.save.locations"),
                  "server.save.locations is a multi-location composite value, not a single "
                  + "credential admin-chat can round-trip through SreeEnv.setPassword");
      assertTrue(AdminPropertyCatalog.isSecret("server.save.locations"),
                 "server.save.locations can embed a plaintext password and must not be read back "
                 + "through admin-chat");
   }

   @Test
   void withholdsServerSaveLocationsWhateverCasingTheCallerUses() {
      assertTrue(AdminPropertyCatalog.isSecret("Server.Save.Locations"));
   }

   /**
    * The pre-#76006 predicate, kept here so a test can assert that a name is covered by the
    * writer half of the union rather than by the name half. Deliberately a copy: if
    * {@code isSecret} is ever narrowed back to its name test, this must NOT follow it, and the
    * test above must fail.
    */
   private static boolean matchesTheNamePattern(String baseName) {
      String lower = baseName.toLowerCase();
      return lower.contains("password") || lower.contains("secret") ||
         lower.contains("credential") || lower.endsWith(".key") || lower.startsWith("license.");
   }

   @Test
   void everyCatalogueEntryIsWellFormed() {
      // The class javadoc's hard rule: a catalogued name that does not exist in StyleBI would be
      // snapshotted as null, applied, read back as null, and reported as success for a property
      // nothing reads. That existence check cannot run here - it is a source-tree grep, done when
      // entries are added - but the shape checks that CAN run should, because a malformed entry
      // fails the same silent way.
      AdminPropertyCatalog catalog = new AdminPropertyCatalog();
      Set<String> seen = new HashSet<>();

      for(CatalogEntry entry : catalog.entries()) {
         String name = entry.name();
         assertNotNull(name, "every entry needs a name");
         assertEquals(name.toLowerCase(), name,
                      name + ": catalogued names are looked up lowercased, so store them that way");
         assertTrue(seen.add(name), name + ": duplicated catalog entry");
         assertNotNull(entry.description(), name + ": needs a description; it is shown in the plan");
         assertTrue(List.of("string", "int", "boolean", "enum").contains(entry.type()),
                    name + ": type must be one AdminPropertyCatalog.canonicalizeValue handles");
         assertTrue(List.of("low", "high").contains(entry.risk()), name + ": risk must be low/high");
         assertTrue(List.of("value", "storage").contains(entry.snapshotScope()),
                    name + ": snapshotScope must be value/storage");

         if("enum".equals(entry.type())) {
            assertNotNull(entry.allowedValues(), name + ": an enum needs allowedValues");
            assertFalse(entry.allowedValues().isEmpty(),
                        name + ": an enum with no allowed values rejects every value");
         }
      }
   }

   @Test
   void theSsoAreaIsCataloguedAndClassifiedConsistently() {
      // Catalogued so a from-scratch SSO setup reports exists:"confirmed" and gets its values
      // validated, instead of every property looking indistinguishable from a typo.
      AdminPropertyCatalog catalog = new AdminPropertyCatalog();

      for(String name : new String[] { "sso.protocol.type", "openid.client.id", "openid.issuer",
                                       "openid.jwks.uri", "openid.scopes", "openid.name.claim",
                                       "saml.roles.attribute", "onelogin.saml2.sp.entityid" })
      {
         CatalogEntry entry = catalog.getEntry(AdminPropertyName.parse(name));
         assertNotNull(entry, name + " should be catalogued");
         // No property in this area appears in PropertyChangeSideEffects or
         // PropertiesEngine.applyProperty, so a write reaches nothing beyond the value itself and
         // does not need a Tier-2 storage snapshot.
         assertEquals("value", entry.snapshotScope(), name + ": no side-effect channel reaches it");
         // Under-classifying here would skip review on a change that can lock every user out.
         assertEquals("high", entry.risk(), name + ": SSO changes govern who can log in");
      }
   }

   @Test
   void ssoProtocolTypeRejectsATypoThatWouldSilentlyDisableSso() {
      // SSOType.forName folds anything unrecognised to NONE, so "OpenId Connect" would turn SSO
      // off with no error. As an enum it is refused, and a recognised value is canonicalised to
      // the exact casing - which matters, because EmNavBarController compares the raw string.
      AdminPropertyCatalog catalog = new AdminPropertyCatalog();
      CatalogEntry entry = catalog.getEntry(AdminPropertyName.parse("sso.protocol.type"));

      assertEquals("OpenID", catalog.canonicalizeValue(entry, "openid"));
      assertEquals("SAML", catalog.canonicalizeValue(entry, "  saml  "));
      assertThrows(IllegalArgumentException.class,
                   () -> catalog.canonicalizeValue(entry, "OpenId Connect"));
   }
}
