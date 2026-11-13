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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The per-type vocabulary check of a {@code dynamicValue} name, against the REAL
 * {@code PropertyAliases} registry (Bug #80459, design 1.2/1.3). The type lists below are the
 * registry's facts at the time of writing; a type dropping out of one of them is exactly the event
 * that would silently remove a button from a host, so it must fail here.
 */
@Tag("core")
class DynamicValueVocabularyTest {
   private static final Set<String> VISIBLE_TYPES = Set.of(
      "calctable", "calendar", "chart", "checkbox", "combobox", "crosstab", "gauge",
      "groupcontainer", "image", "line", "oval", "radiobutton", "rectangle", "selectioncontainer",
      "selectionlist", "selectiontree", "slider", "spinner", "submit", "tab", "table", "text",
      "textinput", "timeslider", "viewsheet");

   private static final Set<String> ENABLED_TYPES = Set.of(
      "calctable", "calendar", "chart", "checkbox", "combobox", "crosstab", "gauge",
      "image", "radiobutton", "selectioncontainer", "selectionlist", "selectiontree",
      "slider", "spinner", "submit", "tab", "table", "text", "textinput", "timeslider");

   private static final Set<String> TITLE_TYPES = Set.of(
      "calctable", "calendar", "chart", "checkbox", "crosstab", "radiobutton",
      "selectioncontainer", "selectionlist", "selectiontree", "table", "timeslider");

   private static final Set<String> ALL_TYPES = Set.of(
      "calctable", "calendar", "chart", "checkbox", "combobox", "crosstab", "gauge",
      "groupcontainer", "image", "line", "oval", "radiobutton", "rectangle", "selectioncontainer",
      "selectionlist", "selectiontree", "slider", "spinner", "submit", "tab", "table", "text",
      "textinput", "timeslider", "viewsheet");

   private static String problem(String type, String name) {
      DynamicValueName parsed = DynamicValueName.parse(name);

      assertNotNull(parsed, name + ": " + DynamicValueName.problem(name));
      return DynamicValueVocabulary.problem(type, parsed);
   }

   private static void assertValidOnlyFor(Set<String> valid, String name) {
      for(String type : ALL_TYPES) {
         if(valid.contains(type)) {
            assertNull(problem(type, name), name + " on " + type);
         }
         else {
            assertNotNull(problem(type, name), name + " must be refused on " + type);
         }
      }
   }

   @Test
   void visibleIsValidForEveryTypeThatHostsTheBasicGeneralPane() {
      assertEquals(25, VISIBLE_TYPES.size());
      assertValidOnlyFor(VISIBLE_TYPES, "visible");
   }

   @Test
   void enabledIsValidForTheGeneralPropPaneHostsAndNotForShapes() {
      assertEquals(20, ENABLED_TYPES.size());
      assertValidOnlyFor(ENABLED_TYPES, "enabled");

      for(String shape : new String[] { "oval", "rectangle", "line" }) {
         String problem = problem(shape, "enabled");

         assertTrue(problem.contains("'enabled' is not a property of " + shape), problem);
         assertTrue(problem.contains("Known names:"), problem);
         assertTrue(problem.contains("visible"), problem);
      }
   }

   /** The vocabulary is the one set_assembly_properties writes, so a read-only or dead name is refused. */
   @Test
   void aDeadOrReadOnlyNameIsRefusedLikeSetAssemblyProperties() {
      // groupcontainer: the Composer hides its Enabled control and the apply method never reads it
      String group = problem("groupcontainer", "enabled");
      assertNotNull(group);
      assertTrue(group.contains("read-only on groupcontainer"), group);

      // a raw path to a field no apply method reads back
      String locked = problem("text", "locked");
      assertNotNull(locked);
      assertTrue(locked.contains("read-only on text"), locked);

      String cellHeight = problem("table", "tableViewGeneralPaneModel.sizePositionPaneModel.cellHeight");
      assertNotNull(cellHeight);
      assertTrue(cellHeight.contains("read-only on table"), cellHeight);

      // a live raw path of the same type is still accepted
      assertNull(problem("table", "tableViewGeneralPaneModel.sizePositionPaneModel.width"));
   }

   @Test
   void titleIsValidForTheElevenTitleHosts() {
      assertEquals(11, TITLE_TYPES.size());
      assertValidOnlyFor(TITLE_TYPES, "title");
   }

   @Test
   void aNearMissIsRefusedWithTheNearestAlias() {
      String problem = problem("text", "visble");

      assertTrue(problem.contains("Did you mean 'visible'?"), problem);
      assertNotNull(problem("text", "Visible"), "case is significant");
   }

   @Test
   void aKeyIsAnAliasOfTheConcreteType() {
      assertNull(problem("gauge", "minorIncrement"));
      assertNotNull(problem("text", "minorIncrement"));
      assertNull(problem("slider", "increment"));
      assertNotNull(problem("gauge", "increment"));
      assertNull(problem("oval", "fillColor"));
      assertNull(problem("line", "lineColor"));
      assertNotNull(problem("line", "fillColor"));
      assertNull(problem("combobox", "minDate"));
      assertNull(problem("text", "text"));
      assertNotNull(problem("chart", "text"));
   }

   @Test
   void aRawDottedPathMustResolveOnTheDialogModelOfThatType() {
      String image = "imageAdvancedPaneModel.dynamicImagePaneModel.dynamicImageValue";

      assertNull(problem("image", image));
      assertNotNull(problem("text", image));

      for(String type : new String[] { "slider", "spinner", "textinput", "checkbox", "combobox",
                                       "radiobutton" })
      {
         assertNull(problem(type, "dataInputPaneModel.columnValue"), type);
         assertNull(problem(type, "dataInputPaneModel.rowValue"), type);
      }

      assertNotNull(problem("text", "dataInputPaneModel.columnValue"));
      assertNotNull(problem("image", "imageAdvancedPaneModel.dynamicImagePaneModel.noSuchField"));
   }

   @Test
   void aRawPathWithAnUpperCaseSegmentInitialIsTheSameSpellingAsTheLowerCaseOne() {
      assertNull(problem("slider", "DataInputPaneModel.columnValue"));
   }

   @Test
   void rangeValuesIndexIsValidOnlyOnGauge() {
      for(String type : ALL_TYPES) {
         if(type.equals("gauge")) {
            assertNull(problem(type, "rangeValues[0]"));
            assertNull(problem(type, "rangeValues[7]"));
         }
         else {
            assertNotNull(problem(type, "rangeValues[0]"), type);
         }
      }
   }

   @Test
   void selectionSourceKeysFollowTheSelectionTypes() {
      assertNull(problem("selectionlist", "source:measure"));
      assertNull(problem("selectiontree", "source:measure"));
      assertNotNull(problem("chart", "source:measure"));
      assertNotNull(problem("timeslider", "source:measure"));

      for(String key : new String[] { "parentIdColumn", "idColumn", "labelColumn" }) {
         assertNull(problem("selectiontree", "source:" + key), key);
         assertNotNull(problem("selectionlist", "source:" + key), key);
      }
   }

   @Test
   void chartOnlyNamespaces() {
      for(String name : new String[] {
         "chartTarget:line.value", "chartTarget:statistics.percentageOf",
         "chartRegion:title/x/title", "chartRegion:legend/2/title" })
      {
         for(String type : ALL_TYPES) {
            if(type.equals("chart")) {
               assertNull(problem(type, name), name);
            }
            else {
               assertNotNull(problem(type, name), name + " on " + type);
            }
         }
      }
   }

   @Test
   void tableOptionIsCrosstabOnly() {
      for(String name : new String[] { "tableOption:rowTotals", "tableOption:colTotals" }) {
         for(String type : ALL_TYPES) {
            if(type.equals("crosstab")) {
               assertNull(problem(type, name), name);
            }
            else {
               assertNotNull(problem(type, name), name + " on " + type);
            }
         }
      }
   }

   @Test
   void dateComparisonIsChartAndCrosstab() {
      for(String name : new String[] { "dateComparison:periodsBack",
                                       "dateComparison:customPeriods[1].end" })
      {
         for(String type : ALL_TYPES) {
            if(type.equals("chart") || type.equals("crosstab")) {
               assertNull(problem(type, name), name + " on " + type);
            }
            else {
               assertNotNull(problem(type, name), name + " on " + type);
            }
         }
      }
   }

   @Test
   void bindingIsChartCrosstabAndTable() {
      for(String type : ALL_TYPES) {
         boolean binds = type.equals("chart") || type.equals("crosstab") || type.equals("table");
         String problem = problem(type, "binding:x[0].column");

         if(binds) {
            assertNull(problem, type);
         }
         else {
            assertNotNull(problem, type);
         }
      }
   }

   @Test
   void aTypeTheRegistryDoesNotCoverIsRefusedByName() {
      String problem = problem("cube", "visible");

      assertTrue(problem.contains("cube"), problem);
      assertTrue(problem.contains("not covered"), problem);
      assertNotNull(problem("cube", "chartTarget:line.value"));
      assertNotNull(problem(null, "visible"));
   }

   @Test
   void everyRefusalNamesTheFieldAndTheType() {
      String problem = problem("text", "chartTarget:line.value");

      assertTrue(problem.contains("chartTarget:line.value"), problem);
      assertTrue(problem.contains("text"), problem);
      assertTrue(problem.contains("chart"), problem);
   }
}
