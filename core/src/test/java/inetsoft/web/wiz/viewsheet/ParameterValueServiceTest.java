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
}
