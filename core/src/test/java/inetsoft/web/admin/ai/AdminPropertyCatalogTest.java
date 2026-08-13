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
      // The four properties the name predicate does NOT match but which Enterprise Manager writes
      // with setPassword. Before they were listed, admin-chat wrote them plain and silently
      // downgraded a stored credential to plaintext at rest.
      for(String name : new String[] { "mail.smtp.pass", "mail.smtp.clientsecret",
                                       "mail.smtp.accesstoken", "mail.smtp.refreshtoken",
                                       "log.fluentd.security.sharedkey",
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
      //   mail.smtp.tokenuri              same save block as the four above, plain setProperty
      //   log.fluentd.security.password   sibling of sharedkey, plain setProperty - its reader
      //                                   calls decryptPassword, which proves nothing, because
      //                                   decryptPassword passes plaintext straight through
      //   auth0.client.secret             read plain; the legacy migration encrypts on the way out
      //   sso.rsa.public.key              a PUBLIC key
      for(String name : new String[] { "mail.smtp.tokenuri", "log.fluentd.security.password",
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
