/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.util;

/*
 * Regression coverage for the sree.collator org-scope leak: CoreTool.getCollator() cached the
 * first-resolved Collator (or lack thereof) in a JVM-wide static field/flag, so whichever
 * organization's request first triggered a case-insensitive string comparison after JVM start
 * permanently decided every other organization's collator, even organizations with no
 * sree.collator override at all. Follows the actAs(orgId) pattern from
 * MapGeneratorWebMapSuspendOrgScopeTest.
 */

import inetsoft.report.filter.DefaultComparer;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.CollationKey;
import java.text.Collator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CoreToolCollatorOrgScopeTest {
   @BeforeEach
   void setUp() {
      // Matches the shipped default (defaults.properties: string.compare.casesensitive=false)
      // so the test doesn't depend on whatever another test in the same fork last set it to.
      SreeEnv.setProperty("string.compare.casesensitive", "false");
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);
      SreeEnv.setProperty("string.compare.casesensitive", null);
      SreeEnv.setProperty("sree.collator", null, true);
   }

   @Test
   void orgCollatorOverride_doesNotLeakToOrgWithoutOverride() {
      String orgA = "collator_orgscope_org_a_" + UUID.randomUUID();
      String orgB = "collator_orgscope_org_b_" + UUID.randomUUID();

      assertTrue("apple".compareToIgnoreCase("banana") < 0,
                 "sanity check: natural order must be apple < banana");

      // Org A configures a custom collator that reverses natural ordering.
      actAs(orgA);
      SreeEnv.setProperty("sree.collator", ReversingTestCollator.class.getName(), true);
      int aResult = CoreTool.compare("apple", "banana", false, false);
      assertTrue(aResult > 0, "org A's custom collator must reverse the natural order");

      // Org B never configured an override -- must NOT inherit org A's collator. This
      // assertion fails today, since org A's resolution latches into the static cache first.
      actAs(orgB);
      int bResult = CoreTool.compare("apple", "banana", false, false);
      assertTrue(bResult < 0, "org B without an override must use natural ordering");
   }

   @Test
   void orgCollatorOverride_firstCallerDoesNotWinRegardlessOfOrder() {
      String orgA = "collator_orgscope_org_a_" + UUID.randomUUID();
      String orgB = "collator_orgscope_org_b_" + UUID.randomUUID();

      // Drive org B (no override) FIRST, then org A (override) -- proves the fix isn't just
      // "whichever org happens to run first coincidentally gets the no-override default", but
      // that each org independently resolves its own override regardless of call order.
      actAs(orgB);
      int bResult = CoreTool.compare("apple", "banana", false, false);
      assertTrue(bResult < 0, "org B without an override must use natural ordering");

      actAs(orgA);
      SreeEnv.setProperty("sree.collator", ReversingTestCollator.class.getName(), true);
      int aResult = CoreTool.compare("apple", "banana", false, false);
      assertTrue(aResult > 0, "org A's custom collator must reverse the natural order");
   }

   @Test
   void defaultComparer_reflectsPerOrgCollatorOverride() {
      String orgA = "collator_orgscope_org_a_" + UUID.randomUUID();

      // DefaultComparer -- not DefaultComparator -- is what production code actually uses as
      // "the default comparer" (SortFilter, CrossFilter, DataComparer.DEFAULT_COMPARER, etc.).
      // Its no-arg constructor defers to Tool.isCaseSensitive() (false by default), so it
      // reaches CoreTool.getCollator() on a stock install -- exercising the actual
      // most-consequential production path, not just the CoreTool.compare() entry point.
      actAs(orgA);
      SreeEnv.setProperty("sree.collator", ReversingTestCollator.class.getName(), true);

      int result = new DefaultComparer().compare("apple", "banana");

      assertTrue(result > 0, "DefaultComparer must reflect org A's collator override");
   }

   private static void actAs(String orgId) {
      ThreadContext.setContextPrincipal(new SRPrincipal(new IdentityID("tester", orgId),
         new IdentityID[0], new String[0], orgId, 1L));
      OrganizationContextHolder.setCurrentOrgId(orgId);
   }

   /**
    * Deliberately reverses natural case-insensitive string order so a test can distinguish
    * "this collator was actually used" from "natural ordering happened to apply anyway".
    */
   public static class ReversingTestCollator extends Collator {
      @Override
      public int compare(String source, String target) {
         return -source.compareToIgnoreCase(target);
      }

      @Override
      public CollationKey getCollationKey(String source) {
         throw new UnsupportedOperationException();
      }

      @Override
      public int hashCode() {
         return System.identityHashCode(this);
      }
   }
}
