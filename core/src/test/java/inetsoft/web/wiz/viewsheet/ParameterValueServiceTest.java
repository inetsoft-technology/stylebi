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

import inetsoft.uql.asset.AssetVariable;
import inetsoft.uql.schema.StringType;
import inetsoft.uql.schema.UserVariable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Bug 78156: pre-store validation in {@link ParameterValueService#validate}. */
@Tag("core")
class ParameterValueServiceTest {
   private static AssetVariable variable(int style) {
      AssetVariable var = new AssetVariable("v");
      var.setTypeNode(new StringType());
      var.setDisplayStyle(style);
      var.setMultipleSelection(style == UserVariable.LIST);
      return var;
   }

   @Test
   void refusesANullElementInAMultiValueArray() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> ParameterValueService.validate(variable(UserVariable.LIST), "multiVar",
                                              Arrays.asList("2", null)));

      assertTrue(e.getMessage().contains("multiVar") && e.getMessage().contains("value[1]"),
                 e.getMessage());
   }

   @Test
   void refusesTheNullSentinelInAMultiValueArray() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> ParameterValueService.validate(variable(UserVariable.LIST), "multiVar",
                                              List.of("a", "__null__")));

      assertTrue(e.getMessage().contains("__null__"), e.getMessage());
   }

   @Test
   void aLoneNullStaysTheClearShape() {
      assertDoesNotThrow(() -> ParameterValueService.validate(
         variable(UserVariable.COMBOBOX), "v", Arrays.asList((Object) null)));
      assertDoesNotThrow(() -> ParameterValueService.validate(
         variable(UserVariable.COMBOBOX), "v", List.of("__null__")));
   }

   @Test
   void refusesTwoValuesOnASingleSelectParameter() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> ParameterValueService.validate(variable(UserVariable.COMBOBOX), "single",
                                              List.of("a", "b")));

      assertTrue(e.getMessage().contains("single") && e.getMessage().contains("single-select"),
                 e.getMessage());
   }

   @Test
   void acceptsSeveralValuesOnListAndCheckboxesParameters() {
      for(int style : new int[]{UserVariable.LIST, UserVariable.CHECKBOXES}) {
         assertDoesNotThrow(() -> ParameterValueService.validate(
            variable(style), "v", List.of("a", "b")), "style " + style);
      }
   }

   @Test
   void acceptsSeveralValuesOnAMultipleSelectionOrUsedInOneOfParameter() {
      AssetVariable multi = variable(UserVariable.COMBOBOX);
      multi.setMultipleSelection(true);
      assertDoesNotThrow(() -> ParameterValueService.validate(multi, "v", List.of("a", "b")));
   }

   private static AssetVariable typed(String type) throws Exception {
      AssetVariable var = new AssetVariable("v");
      var.setTypeNode(inetsoft.uql.schema.XSchema.createPrimitiveType(type));
      var.setDisplayStyle(UserVariable.LIST);
      var.setMultipleSelection(true);
      return var;
   }

   private static void refuses(String type, String value) throws Exception {
      AssetVariable var = typed(type);
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> ParameterValueService.validate(var, "v", List.of(value)), type + " " + value);
      assertTrue(e.getMessage().contains("'v'") && e.getMessage().contains(value), e.getMessage());
      // and inside a multi-value array, naming the element index
      Exception m = assertThrows(IllegalArgumentException.class,
         () -> ParameterValueService.validate(var, "v", List.of(value, value)), type + " " + value);
      assertTrue(m.getMessage().contains("value[0]"), m.getMessage());
   }

   @Test
   void refusesOutOfRangeIntegers() throws Exception {
      refuses("integer", "3000000000");
      refuses("short", "40000");
      refuses("byte", "300");
      refuses("long", "9223372036854775808");
      refuses("integer", "12.7");
   }

   @Test
   void refusesNonFiniteAndNonDecimalFloats() throws Exception {
      refuses("double", "0x10");
      refuses("double", "0b11");
      refuses("double", "NaN");
      refuses("double", "1e400");
      refuses("double", "1d");
      refuses("float", "1e39");
      refuses("float", "1e-50");
      refuses("double", "12%");
   }

   @Test
   void refusesImpossibleOrUnparseableDates() throws Exception {
      refuses("date", "2026-13-45");
      refuses("date", "2026-02-30");
      refuses("date", "2026-04-31");
      refuses("date", "garbage");
      refuses("timeInstant", "2026-02-30 10:00:00");
      refuses("timeInstant", "2026-02-28 25:61:00");
      refuses("timeInstant", "garbage");
      refuses("time", "25:61:00");
      refuses("time", "10:60:00");
   }

   @Test
   void acceptsValidTypedValuesAndBoundaries() throws Exception {
      for(String[] c : new String[][]{
         {"integer", "2147483647"}, {"integer", "-2147483648"}, {"short", "32767"},
         {"byte", "-128"}, {"long", "9223372036854775807"}, {"double", "1.5e3"},
         {"double", "-.5"}, {"float", "3.4e38"}, {"date", "2024-02-29"},
         {"timeInstant", "2026-03-01 23:59:59"}, {"time", "23:59:59"}})
      {
         AssetVariable var = typed(c[0]);
         assertDoesNotThrow(() -> ParameterValueService.validate(var, "v", List.of(c[1])),
                            c[0] + " " + c[1]);
         assertDoesNotThrow(() -> ParameterValueService.validate(var, "v", List.of(c[1], c[1])),
                            c[0] + " " + c[1]);
      }
   }

   @Test
   void aClearShapeSkipsTypedValidation() throws Exception {
      assertDoesNotThrow(() -> ParameterValueService.validate(
         typed("integer"), "v", Arrays.asList((Object) null)));
      assertDoesNotThrow(() -> ParameterValueService.validate(
         typed("integer"), "v", List.of("__null__")));
   }
}
