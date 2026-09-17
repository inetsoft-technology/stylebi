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
}
