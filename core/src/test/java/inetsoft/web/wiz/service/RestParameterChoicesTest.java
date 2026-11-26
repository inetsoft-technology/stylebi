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
   void dateFormatsClassifyOnlyYearFirstIsoLayouts() {
      // every spelling the catalogues use for a year-first ISO date or date-time
      for(String p : new String[] {
         "YYYY-MM-DD", "yyyy-MM-dd", "yyyy-mm-dd", "YYYY-mm-dd", "YYYY-MM-DDTHH:MM:SSZ",
         "yyyy-MM-ddTHH:mm:ss", "yyyy-MM-ddTHH:mm:ssZ", "yyyy-mm-ddThh:mm:ss",
         "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd’T’HH:mm:ss", "YYYY-MM-DD HH:MM",
         "yyyy-MM-dd HH:mm:ss", "YYYY-MM-DDTHH:MM:SS", "yyyy-MM-ddTHH:mm:ss.SSSZ",
         " yyyy-MM-ddTHH:mm:ssZ", "YYYY-MM-DD|YYYY-MM-DDThh:mm:ssZ"
      })
      {
         assertFalse(RestParameterChoices.dateFormats(p).isEmpty(), "placeholder: " + p);
      }

      // day-first, year only, a non-date alternative, a plain hint and no placeholder stay hints
      for(String p : new String[] {
         "dd-mm-YYYY", "yyyy", "yyyy-MM-dd|milliseconds", "MM/dd/yyyy", "HH:MM", "Cash|Accrual",
         "", null
      })
      {
         assertTrue(RestParameterChoices.dateFormats(p).isEmpty(), "placeholder: " + p);
         assertEquals("anything", RestParameterChoices.resolveDate(p, "anything"));
      }
   }

   @Test
   void resolveDateRefusesWhatTheVendorCannotParse() {
      String dateOnly = "YYYY-MM-DD";

      assertEquals("2020-01-01", RestParameterChoices.resolveDate(dateOnly, "2020-01-01"));
      assertEquals("2020-02-29", RestParameterChoices.resolveDate(dateOnly, "2020-02-29"));

      for(String bad : new String[] {
         "garbage", "01.01.2020", "13/01/2020", "Jan 1 2020", "1 Jan 2020", "2020-13-01",
         "2021-02-29", "2020-1-1", "Today", "-30days", "1577836800", "2020-01-01T00:00:00Z"
      })
      {
         assertNull(RestParameterChoices.resolveDate(dateOnly, bad), "value: " + bad);
      }
   }

   @Test
   void resolveDateNormalizesOnlyYearFirstShapes() {
      assertEquals("2020-01-01", RestParameterChoices.resolveDate("YYYY-MM-DD", "2020/01/01"));
      assertEquals("2020-01-01", RestParameterChoices.resolveDate("YYYY-MM-DD", "2020.01.01"));
      assertEquals("2020-01-01", RestParameterChoices.resolveDate("YYYY-MM-DD", "20200101"));
      assertEquals("2020-01-01", RestParameterChoices.resolveDate("YYYY-MM-DD", " 2020-01-01 "));
      assertNull(RestParameterChoices.resolveDate("YYYY-MM-DD", "2020/13/01"));
      // the compact form is taken for a date-only placeholder, never for one with a time
      assertNull(RestParameterChoices.resolveDate("YYYY-MM-DDTHH:MM:SSZ", "20200101"));
   }

   @Test
   void resolveDateForADateTimePlaceholder() {
      String p = "YYYY-MM-DDTHH:MM:SSZ";

      assertEquals("2020-01-01", RestParameterChoices.resolveDate(p, "2020-01-01"));
      assertEquals("2020-01-01T10:20:30Z",
                   RestParameterChoices.resolveDate(p, "2020-01-01T10:20:30Z"));
      assertEquals("2020-01-01T10:20:30.123+08:00",
                   RestParameterChoices.resolveDate(p, "2020-01-01T10:20:30.123+08:00"));
      assertEquals("2020-01-01 10:20", RestParameterChoices.resolveDate(p, "2020-01-01 10:20"));
      // a Unix epoch is a value the vendor accepts for a time
      assertEquals("1577836800", RestParameterChoices.resolveDate(p, "1577836800"));
      assertEquals("1577836800123", RestParameterChoices.resolveDate(p, "1577836800123"));
      assertNull(RestParameterChoices.resolveDate(p, "2020-01-01T25:00:00Z"));
      assertNull(RestParameterChoices.resolveDate(p, "yesterday"));
      assertNull(RestParameterChoices.resolveDate(p, "01.01.2020"));
   }

   @Test
   void resolveDateForAlternativeFormats() {
      String p = "YYYY-MM-DD|YYYY-MM-DDThh:mm:ssZ";

      assertEquals("2020-01-01", RestParameterChoices.resolveDate(p, "2020-01-01"));
      assertEquals("2020-01-01T00:00:00Z",
                   RestParameterChoices.resolveDate(p, "2020-01-01T00:00:00Z"));
      assertNull(RestParameterChoices.resolveDate(p, "garbage"));
   }

   @Test
   void resolveValueKeepsBlankVariableAndClosedListBehaviour() {
      assertNull(RestParameterChoices.resolveValue("YYYY-MM-DD", "garbage"));
      assertEquals("$(day)", RestParameterChoices.resolveValue("YYYY-MM-DD", "$(day)"));
      assertEquals(" ", RestParameterChoices.resolveValue("YYYY-MM-DD", " "));
      assertEquals("Cash", RestParameterChoices.resolveValue("Cash|Accrual", "cash"));
      assertNull(RestParameterChoices.resolveValue("Cash|Accrual", "sideways"));
      assertEquals("whatever", RestParameterChoices.resolveValue("dd-mm-YYYY", "whatever"));
   }

   @Test
   void invalidValueTextNamesTheDeclaredDateFormat() {
      assertEquals(
         "Date Sent = '01.01.2020' (must be a date in the format: YYYY-MM-DD)",
         TabularQueryContractSupport.invalidValueText("Date Sent", "01.01.2020", "YYYY-MM-DD"));
      assertEquals(
         "Since = 'x' (must be a date in the format: YYYY-MM-DD | YYYY-MM-DDThh:mm:ssZ)",
         TabularQueryContractSupport.invalidValueText(
            "Since", "x", "YYYY-MM-DD|YYYY-MM-DDThh:mm:ssZ"));
      assertEquals("Method = 'x' (must be one of: Cash | Accrual)",
                   TabularQueryContractSupport.invalidValueText("Method", "x", "Cash|Accrual"));
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
