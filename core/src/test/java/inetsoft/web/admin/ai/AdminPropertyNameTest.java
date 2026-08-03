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
 * Pins property-name handling against PropertiesEngine's actual behaviour.
 *
 * Two rules matter and neither is guessable:
 *  - computePropertyNameCase lowercases everything EXCEPT four case-preserving families, so a
 *    blanket toLowerCase would address a different property that StyleBI then creates - the change
 *    would apply, verify and audit while the intended property stayed untouched.
 *  - Any name beginning "inetsoft.org." is fully lowercased. The inetsoft.org. branch in
 *    computePropertyNameCase is unreachable (all four patterns are anchored on log./plugin./
 *    inetsoft.uql. prefixes), so org-qualified case-preserving names are NOT addressable.
 */
@Tag("core")
class AdminPropertyNameTest {
   @Test
   void lowercasesAnOrdinaryProperty() {
      AdminPropertyName name = AdminPropertyName.parse("  Query.Runtime.MaxRow  ");
      assertEquals("query.runtime.maxrow", name.key());
      assertEquals("query.runtime.maxrow", name.baseName());
      assertNull(name.orgId());
      assertFalse(name.isOrgScoped());
   }

   @Test
   void preservesCaseForLogLevelFamily() {
      assertEquals("log.level.com.Example", AdminPropertyName.parse("log.level.com.Example").key());
   }

   @Test
   void preservesCaseForPluginClasspathFamily() {
      assertEquals("plugin.extra.classpath.MyPlugin",
                   AdminPropertyName.parse("plugin.extra.classpath.MyPlugin").key());
   }

   @Test
   void preservesCaseForContextLogLevelFamily() {
      assertEquals("log.ORG_ID.level.com.Example",
                   AdminPropertyName.parse("log.ORG_ID.level.com.Example").key());
   }

   @Test
   void preservesCaseForJdbcPoolTestQueryFamily() {
      assertEquals("inetsoft.uql.jdbc.pool.MyDS.connectionTestQuery",
                   AdminPropertyName.parse("inetsoft.uql.jdbc.pool.MyDS.connectionTestQuery").key());
   }

   @Test
   void splitsOrgQualifiedName() {
      AdminPropertyName name = AdminPropertyName.parse("inetsoft.org.ACME.Mail.SMTP.Host");
      assertEquals("inetsoft.org.acme.mail.smtp.host", name.key());
      assertEquals("mail.smtp.host", name.baseName());
      assertEquals("acme", name.orgId());
      assertTrue(name.isOrgScoped());
   }

   @Test
   void orgQualifiedNamesAreFullyLowercased() {
      // Documented limitation: matches PropertiesEngine, whose inetsoft.org. branch is dead code.
      AdminPropertyName name = AdminPropertyName.parse("inetsoft.org.acme.log.level.com.Example");
      assertEquals("inetsoft.org.acme.log.level.com.example", name.key());
      assertEquals("log.level.com.example", name.baseName());
   }

   @Test
   void treatsOrgPrefixWithNoFurtherSegmentAsAnOrdinaryProperty() {
      AdminPropertyName name = AdminPropertyName.parse("inetsoft.org.acme");
      assertEquals("inetsoft.org.acme", name.key());
      assertNull(name.orgId());
   }

   @Test
   void qualifiesAnOrgNameWithAnEmptyBaseAsOrdinary() {
      AdminPropertyName name = AdminPropertyName.parse("inetsoft.org.acme.");
      assertNull(name.orgId());
      assertEquals("inetsoft.org.acme.", name.key());
   }

   @Test
   void rejectsNullAndBlank() {
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> AdminPropertyName.parse(null)).getMessage().startsWith("property:"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> AdminPropertyName.parse("   ")).getMessage().startsWith("property:"));
   }
}
