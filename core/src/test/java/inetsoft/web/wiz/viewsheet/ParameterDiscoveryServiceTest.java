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
package inetsoft.web.wiz.viewsheet;

import inetsoft.uql.schema.StringType;
import inetsoft.uql.schema.UserVariable;
import inetsoft.web.wiz.viewsheet.model.ParameterModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for {@link ParameterDiscoveryService}. {@code discover} itself needs a live
 * {@code RuntimeViewsheet}/{@code ViewsheetSandbox} (cannot be constructed outside a Spring
 * context, per the existing note atop {@code CalendarInputServiceTest}) -- verified live instead,
 * see the plan's Task 6. {@code toModel} and {@code find} are plain static methods over a
 * directly-constructible {@link UserVariable}, so they are covered here.
 */
@Tag("core")
class ParameterDiscoveryServiceTest {
   @Test
   void mapsATypedVariableWithNoChoices() {
      UserVariable var = new UserVariable("stateVar");
      var.setTypeNode(new StringType());

      ParameterModel model = ParameterDiscoveryService.toModel(var, false, null);

      assertEquals("stateVar", model.name());
      assertEquals("string", model.type());
      assertFalse(model.boundToInputAssembly());
      assertNull(model.choices());
      assertNull(model.values());
      assertNull(model.currentValue());
   }

   @Test
   void mapsChoicesWhenPresent() {
      UserVariable var = new UserVariable("region");
      var.setTypeNode(new StringType());
      var.setChoices(new Object[]{"East", "West"});

      ParameterModel model = ParameterDiscoveryService.toModel(var, false, new Object[]{"East"});

      assertEquals(List.of("East", "West"), model.choices());
      assertEquals(List.of("East"), model.currentValue());
   }

   @Test
   void mapsValuesIndexPairedWithChoices() {
      // The bug-77040 case: an embedded picker whose real values ("2"/"4") differ from its
      // display labels ("Jane Doe"/"John Smith") -- collect_parameters must expose both, paired
      // by index, so a caller can submit the real value instead of the label.
      UserVariable var = new UserVariable("empVar");
      var.setTypeNode(new StringType());
      // UserVariable.setValues/setChoices sort by default (sortValue==true), which needs a live
      // Spring context (Tool.compare -> SreeEnv) this pure-logic test does not have -- disabled
      // here since sort order is irrelevant to what toModel reports.
      var.setSortValue(false);
      var.setChoices(new Object[]{"Jane Doe", "John Smith"});
      var.setValues(new Object[]{"2", "4"});

      ParameterModel model = ParameterDiscoveryService.toModel(var, false, null);

      assertEquals(List.of("Jane Doe", "John Smith"), model.choices());
      assertEquals(List.of("2", "4"), model.values());
   }

   @Test
   void omitsValuesWhenLengthDoesNotMatchChoices() {
      // A mismatched length can't be correlated by position -- report null rather than a
      // misleading pairing.
      UserVariable var = new UserVariable("region");
      var.setTypeNode(new StringType());
      var.setSortValue(false);
      var.setChoices(new Object[]{"East", "West"});
      var.setValues(new Object[]{"1"});

      ParameterModel model = ParameterDiscoveryService.toModel(var, false, null);

      assertNull(model.values());
   }

   @Test
   void omitsValuesWhenChoicesAbsent() {
      UserVariable var = new UserVariable("stateVar");
      var.setTypeNode(new StringType());
      var.setSortValue(false);
      var.setValues(new Object[]{"AZ"});

      ParameterModel model = ParameterDiscoveryService.toModel(var, false, null);

      assertNull(model.choices());
      assertNull(model.values());
   }

   @Test
   void flagsAVariableBoundToAnInputAssembly() {
      UserVariable var = new UserVariable("year");
      var.setTypeNode(new StringType());

      ParameterModel model = ParameterDiscoveryService.toModel(var, true, null);

      assertTrue(model.boundToInputAssembly());
   }

   @Test
   void findReturnsTheMatchingVariable() {
      UserVariable var = new UserVariable("stateVar");
      assertSame(var, ParameterDiscoveryService.find(List.of(var), "stateVar"));
   }

   @Test
   void findRefusesAnUnknownNameByName() {
      UserVariable var = new UserVariable("stateVar");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> ParameterDiscoveryService.find(List.of(var), "notAVariable"));

      assertTrue(e.getMessage().contains("notAVariable"), e.getMessage());
      assertTrue(e.getMessage().contains("collect_parameters"), e.getMessage());
   }

   @Test
   void toModelToleratesANullElementInAStoredValueArray() {
      // Bug 78156 S4: List.of(...) threw NPE on a stored [x, null], bricking every
      // collect_parameters/set_parameters call in the session.
      UserVariable var = new UserVariable("multiVar");
      var.setTypeNode(new StringType());

      ParameterModel model = ParameterDiscoveryService.toModel(var, false, new Object[]{"2", null});

      assertEquals(java.util.Arrays.asList("2", null), model.currentValue());
   }

   @Test
   void toModelToleratesNullChoicesAndValues() {
      UserVariable var = new UserVariable("v");
      var.setTypeNode(new StringType());
      var.setSortValue(false);
      var.setChoices(new Object[]{"a", null});
      var.setValues(new Object[]{"1", null});

      ParameterModel model = ParameterDiscoveryService.toModel(var, false, null);

      assertEquals(java.util.Arrays.asList("a", null), model.choices());
      assertEquals(java.util.Arrays.asList("1", null), model.values());
   }

   @Test
   void checkboxesAndListVariablesReportMultipleSelection() {
      for(int style : new int[]{UserVariable.LIST, UserVariable.CHECKBOXES}) {
         inetsoft.uql.asset.AssetVariable var = new inetsoft.uql.asset.AssetVariable("v");
         var.setTypeNode(new StringType());
         var.setDisplayStyle(style);
         // native Composer stores multipleSelection=false for CHECKBOXES
         var.setMultipleSelection(style == UserVariable.LIST);

         assertTrue(ParameterDiscoveryService.toModel(var, false, null).multipleSelection(),
                    "style " + style);
      }
   }

   @Test
   void comboboxVariableIsSingleSelect() {
      inetsoft.uql.asset.AssetVariable var = new inetsoft.uql.asset.AssetVariable("v");
      var.setTypeNode(new StringType());
      var.setDisplayStyle(UserVariable.COMBOBOX);

      assertFalse(ParameterDiscoveryService.toModel(var, false, null).multipleSelection());
   }
}
