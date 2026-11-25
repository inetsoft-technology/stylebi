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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The closed-choice rule of {@link RestParameterChoices} (bug #82261), pinned by the golden
 * fixture the composer plugin's TypeScript mirror asserts too.
 */
@Tag("core")
class RestParameterChoicesTest {
   private static JsonNode golden() throws Exception {
      try(InputStream in = RestParameterChoicesTest.class
         .getResourceAsStream("rest-parameter-choices-golden.json"))
      {
         assertNotNull(in, "golden fixture missing");
         return new ObjectMapper().readTree(in);
      }
   }

   private static String text(JsonNode n) {
      return n == null || n.isNull() ? null : n.asText();
   }

   @Test
   void closedChoicesMatchTheGoldenFixture() throws Exception {
      int count = 0;

      for(JsonNode c : golden().get("closedChoices")) {
         List<String> expected = new ArrayList<>();
         c.get("choices").forEach(x -> expected.add(x.asText()));
         assertEquals(expected, RestParameterChoices.closedChoices(text(c.get("placeholder"))),
                      "placeholder: " + c.get("placeholder"));
         count++;
      }

      assertTrue(count > 20, "fixture unexpectedly small: " + count);
   }

   @Test
   void resolveMatchesTheGoldenFixture() throws Exception {
      int count = 0;

      for(JsonNode c : golden().get("resolve")) {
         assertEquals(text(c.get("result")),
                      RestParameterChoices.resolve(text(c.get("placeholder")),
                                                   text(c.get("value"))),
                      "case: " + c);
         count++;
      }

      assertTrue(count > 10, "fixture unexpectedly small: " + count);
   }

   @Test
   void dateFormatIsTestedAnchoredNotBySubstring() {
      assertTrue(RestParameterChoices.isDateFormat("yyyy-MM-dd"));
      assertTrue(RestParameterChoices.isDateFormat("YYYY-MM-DD"));
      // "committed", "in-progress" and "description" contain mm/ss/dd-style runs only as
      // substrings of a real word.
      assertFalse(RestParameterChoices.isDateFormat("committed"));
      assertFalse(RestParameterChoices.isDateFormat("in-progress"));
      assertFalse(RestParameterChoices.isDateFormat("description"));
   }

   @Test
   void caseCollidingChoicesNeedAnExactMatch() {
      assertEquals("a", RestParameterChoices.resolve("a|A", "a"));
      assertEquals("A", RestParameterChoices.resolve("a|A", "A"));
      assertNull(RestParameterChoices.resolve("a|A", "b"));
   }

   @Test
   void longChoiceListsAreCappedInTheMessage() {
      List<String> many = new ArrayList<>();

      for(int i = 0; i < 40; i++) {
         many.add("v" + i);
      }

      String described = RestParameterChoices.describeChoices(many);
      assertTrue(described.endsWith("(+10 more)"), described);
      assertFalse(described.contains("v39"), described);
   }

   @Test
   void describeParameterIsSingleLine() {
      String hint = RestParameterChoices.describeParameter("D", false, "yyyy\nMM\r\ndd");
      assertFalse(hint.contains("\n") || hint.contains("\r"), hint);
      assertEquals("D [format: yyyy MM dd]", hint);
      assertEquals("M (required) [one of: Cash | Accrual]",
                   RestParameterChoices.describeParameter("M", true, "Cash|Accrual"));
      assertEquals("N", RestParameterChoices.describeParameter("N", false, null));
   }
}
