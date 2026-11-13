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
package inetsoft.web.wiz.pairing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code name} grammar of the {@code dynamicValue} kind (Bug #80459, design 1.1/1.2). */
@Tag("core")
class DynamicValueNameTest {
   /** Every name of design table 1.4, with the placeholders filled in. */
   private static final Map<String, String[]> VALID = valid();

   private static Map<String, String[]> valid() {
      Map<String, String[]> m = new LinkedHashMap<>();

      for(String key : new String[] {
         "visible", "enabled", "title", "text", "min", "max", "minorIncrement", "majorIncrement",
         "increment", "rangeValues[0]", "rangeValues[12]", "targetValue", "column", "aggregate",
         "with", "nthOperand", "fillColor", "labelText", "lineColor", "formula", "minDate",
         "maxDate", "dataInputPaneModel.columnValue", "dataInputPaneModel.rowValue",
         "imageAdvancedPaneModel.dynamicImagePaneModel.dynamicImageValue" })
      {
         m.put(key, new String[] { "prop", key });
      }

      for(String key : new String[] { "measure", "parentIdColumn", "idColumn", "labelColumn" }) {
         m.put("source:" + key, new String[] { "source", key });
      }

      for(String field : new String[] {
         "column", "dateLevel", "aggregate", "secondaryColumn", "n", "sort.sortByField",
         "ranking.mode", "ranking.n", "ranking.measure", "percentage",
         "calculateInfo.percentageDirection" })
      {
         m.put("binding:x[0]." + field, new String[] { "binding", "x[0]." + field });
         m.put("binding:Measures2[17]." + field,
               new String[] { "binding", "Measures2[17]." + field });
      }

      for(String key : new String[] { "rowTotals", "colTotals" }) {
         m.put("tableOption:" + key, new String[] { "tableOption", key });
      }

      for(String key : new String[] {
         "periodsBack", "level", "endDate", "interval", "intervalGranularity",
         "intervalContextLevel", "intervalEndDate", "customPeriods[0].start",
         "customPeriods[3].end" })
      {
         m.put("dateComparison:" + key, new String[] { "dateComparison", key });
      }

      for(String key : new String[] {
         "line.value", "line.label", "band.from", "band.to", "band.fromLabel", "band.toLabel",
         "statistics.statisticValue", "statistics.percentageOf", "statistics.label" })
      {
         m.put("chartTarget:" + key, new String[] { "chartTarget", key });
      }

      for(String key : new String[] {
         "title/x/title", "title/x2/title", "title/y/title", "title/y2/title", "legend/0/title",
         "legend/3/title" })
      {
         m.put("chartRegion:" + key, new String[] { "chartRegion", key });
      }

      return m;
   }

   /** Names that must be refused, and a fragment of the reason where the reason is specific. */
   private static final Map<String, String> INVALID = invalid();

   private static Map<String, String> invalid() {
      Map<String, String> m = new LinkedHashMap<>();
      m.put(null, "required");
      m.put("", "required");
      m.put("   ", "required");
      m.put("a b", "whitespace");
      m.put(" visible", "whitespace");
      m.put("visible ", "whitespace");
      m.put("vis\tible", "whitespace");
      m.put("vis" + (char) 0 + "ible", "control");
      m.put("a".repeat(201), "200");
      m.put("prop:visible", "drop the 'prop:' prefix");
      m.put("unknown:x", "namespace");
      m.put("format:color", "not available yet");
      m.put("format:", "not available yet");
      m.put(":visible", "namespace");
      m.put("visible:", "namespace");
      m.put("9lives", null);
      m.put("a..b", null);
      m.put(".a", null);
      m.put("a.", null);
      m.put("a-b", null);
      m.put("rangeValues[x]", null);
      m.put("rangeValues[-1]", null);
      m.put("rangeValues[01]", null);
      m.put("rangeValues[]", null);
      m.put("rangeValues[0", null);
      m.put("rangeValues[0].x", null);
      m.put("fillColor[0]", null);
      m.put("source:other", null);
      m.put("source:", null);
      m.put("tableOption:total", null);
      m.put("binding:a.b[0].column", null);
      m.put("binding:x[0].bogus", null);
      m.put("binding:x[0]", null);
      m.put("binding:x[-1].column", null);
      m.put("binding:x[01].column", null);
      m.put("binding:x.column", null);
      m.put("binding:[0].column", null);
      m.put("binding:x[0].column.extra", null);
      m.put("dateComparison:customPeriods[-1].start", null);
      m.put("dateComparison:customPeriods[0].mid", null);
      m.put("dateComparison:customPeriods.start", null);
      m.put("dateComparison:bogus", null);
      m.put("chartTarget:line.from", null);
      m.put("chartTarget:band", null);
      m.put("chartRegion:legend/-1/title", null);
      m.put("chartRegion:legend/x/title", null);
      m.put("chartRegion:title/z/title", null);
      m.put("chartRegion:title/x", null);
      return m;
   }

   private static void assertAllRejected(Predicate<String> accepts) {
      for(String bad : INVALID.keySet()) {
         assertFalse(accepts.test(bad), "accepted: " + bad);
      }
   }

   @Test
   void everyNameOfTheTableParsesToTheRightNamespaceAndKey() {
      assertEquals(77, VALID.size(), "the corpus changed size");

      VALID.forEach((name, expected) -> {
         DynamicValueName parsed = DynamicValueName.parse(name);

         assertNotNull(parsed, name + ": " + DynamicValueName.problem(name));
         assertEquals(expected[0], parsed.namespace(), name);
         assertEquals(expected[1], parsed.key(), name);
         assertNull(DynamicValueName.problem(name), name);
      });
   }

   @Test
   void everyInvalidNameIsRefusedWithAReason() {
      INVALID.forEach((name, fragment) -> {
         assertNull(DynamicValueName.parse(name), String.valueOf(name));

         String problem = DynamicValueName.problem(name);

         assertNotNull(problem, String.valueOf(name));

         if(fragment != null) {
            assertTrue(problem.contains(fragment), name + " -> " + problem);
         }
      });
   }

   @Test
   void theExplicitPropPrefixIsRefusedWithTheHint() {
      String problem = DynamicValueName.problem("prop:visible");

      assertTrue(problem.contains("drop the 'prop:' prefix"), problem);
      assertTrue(problem.contains("set_assembly_properties"), problem);
   }

   @Test
   void formatIsReservedNotUnknown() {
      String problem = DynamicValueName.problem("format:valueFillColor");

      assertTrue(problem.contains("'format'"), problem);
      assertTrue(problem.contains("not available yet"), problem);
   }

   @Test
   void caseIsSignificantForTheKeyButTheSyntaxStillParses() {
      // Vocabulary, not syntax, decides whether 'Visible' is a real alias.
      assertEquals("Visible", DynamicValueName.parse("Visible").key());
      assertNull(DynamicValueName.parse("Source:measure"));
      assertNull(DynamicValueName.parse("source:Measure"));
   }

   @Test
   void theLengthLimitIsExactlyTwoHundred() {
      assertNotNull(DynamicValueName.parse("a".repeat(200)));
      assertNull(DynamicValueName.parse("a".repeat(201)));
   }

   /**
    * Positive control (a regex review without one proves nothing): the SAME invalid corpus run
    * against deliberately loosened grammars must trip the assertion, so a loosening of the real
    * grammar could not pass this class unnoticed.
    */
   @Test
   void theCorpusDetectsALoosenedGrammar() {
      // The real grammar passes the very helper the controls below must fail.
      assertAllRejected(n -> DynamicValueName.parse(n) != null);

      Pattern anyKey = Pattern.compile("^[^\\s]{1,200}$");
      assertThrows(AssertionFailedError.class,
         () -> assertAllRejected(n -> n != null && anyKey.matcher(n).matches()));

      // The real grammar plus a binding namespace whose shelf and field key are free text.
      Pattern looseBinding = Pattern.compile("^binding:.+$");
      assertThrows(AssertionFailedError.class,
         () -> assertAllRejected(n -> DynamicValueName.parse(n) != null ||
            (n != null && looseBinding.matcher(n).matches())));

      // The real grammar plus an index that accepts a sign and leading zeros.
      Pattern looseIndex = Pattern.compile("^rangeValues\\[-?[0-9]*\\]$");
      assertThrows(AssertionFailedError.class,
         () -> assertAllRejected(n -> DynamicValueName.parse(n) != null ||
            (n != null && looseIndex.matcher(n).matches())));
   }
}
