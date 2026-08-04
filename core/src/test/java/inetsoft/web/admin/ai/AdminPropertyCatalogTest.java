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
}
