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
package inetsoft.report.filter;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Bug #81202. No Spring bean is reachable from this class.
@Tag("core")
class AggregateNameMatcherTest {
   private static String describe(String exp, List<String> names) {
      StringBuilder sb = new StringBuilder();

      for(AggregateNameMatcher.Span s : AggregateNameMatcher.find(exp, names)) {
         sb.append(s.name).append('=').append(exp, s.start, s.end).append(';');
      }

      return sb.toString();
   }

   @Test
   void exactSpellingsAreNotMixedUp() {
      assertEquals("1=Sum([A]);0=Sum([a]);",
                   describe("Sum([A]) Sum([a])", Arrays.asList("Sum([a])", "Sum([A])")));
   }

   @Test
   void otherCaseIsUsedOnlyForTheTextLeftOver() {
      assertEquals("0=Sum([a]);0=SUM([A]);",
                   describe("Sum([a]) SUM([A])", Arrays.asList("Sum([a])")));
   }

   @Test
   void spansAreInOriginalCoordinatesWhenUpperCaseIsLonger() {
      String exp = "x straße y Sum([straße]) z";
      assertEquals("0=Sum([straße]);", describe(exp, Arrays.asList("Sum([STRASSE])")));
   }

   @Test
   void aMatchMayNotEndInsideASharpS() {
      assertTrue(AggregateNameMatcher.find("straße", Arrays.asList("STRAS")).isEmpty());
      assertTrue(AggregateNameMatcher.find("straße", Arrays.asList("SE")).isEmpty());
   }

   @Test
   void nullAndEmptyNamesAreIgnored() {
      assertTrue(AggregateNameMatcher.find("abc", Arrays.asList(null, "")).isEmpty());
      assertTrue(AggregateNameMatcher.find(null, Arrays.asList("a")).isEmpty());
   }

   @Test
   void longerNameWinsOverNameInsideIt() {
      assertEquals("1=field['Sum([a])'];",
                   describe("field['Sum([a])']", Arrays.asList("Sum([a])", "field['Sum([a])']")));
   }

   @Test
   void replaceRewritesOnlyNamesWithAReplacement() {
      assertEquals("X + Sum([b]) + X",
                   AggregateNameMatcher.replace("Sum([a]) + Sum([b]) + SUM([A])",
                                                Arrays.asList("Sum([a])", "Sum([b])"),
                                                Arrays.asList("X", null)));
   }
}
