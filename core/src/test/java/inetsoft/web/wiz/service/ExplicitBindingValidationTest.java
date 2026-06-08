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

import inetsoft.web.wiz.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ExplicitBindingValidationTest {
   private static ExplicitBinding pin(String role, String field) {
      ExplicitBinding eb = new ExplicitBinding();
      eb.setRole(role);
      eb.setField(field);
      return eb;
   }

   private static MeasureFieldInfo measure(String field) {
      MeasureFieldInfo info = new MeasureFieldInfo();
      info.setField(field);
      return info;
   }

   private static DimensionFieldInfo dimension(String field) {
      DimensionFieldInfo info = new DimensionFieldInfo();
      info.setField(field);
      return info;
   }

   @Test
   void unknownSlotThrows() {
      Map<String, SimpleFieldInfo> configs = Map.of("amount", measure("amount"));

      UnsatisfiableBindingException e = assertThrows(UnsatisfiableBindingException.class,
         () -> WizAutoBindingService.validateExplicitBindings(
            List.of(pin("bogus", "amount")), configs, null));

      assertEquals("bogus", e.getRole());
      assertEquals("amount", e.getField());
      assertTrue(e.getReason().contains("unknown slot"));
   }

   @Test
   void measureOnDimensionOnlySlotThrows() {
      // shape is a dimension-only aesthetic; a measure cannot be placed there.
      Map<String, SimpleFieldInfo> configs = Map.of("amount", measure("amount"));

      UnsatisfiableBindingException e = assertThrows(UnsatisfiableBindingException.class,
         () -> WizAutoBindingService.validateExplicitBindings(
            List.of(pin("shape", "amount")), configs, null));

      assertEquals("shape", e.getRole());
      assertEquals("amount", e.getField());
   }

   @Test
   void measureOnGroupSlotThrows() {
      // group is dimension-only (in DIMENSION_SLOTS but not MEASURE_SLOTS); a measure
      // pinned there must be rejected just like shape.
      Map<String, SimpleFieldInfo> configs = Map.of("amount", measure("amount"));

      UnsatisfiableBindingException e = assertThrows(UnsatisfiableBindingException.class,
         () -> WizAutoBindingService.validateExplicitBindings(
            List.of(pin("group", "amount")), configs, null));

      assertEquals("group", e.getRole());
      assertEquals("amount", e.getField());
      assertTrue(e.getReason().contains("dimension-only"));
   }

   @Test
   void dimensionOnMeasureOnlySlotThrows() {
      // aggregates is measure-only (in MEASURE_SLOTS but not DIMENSION_SLOTS); a
      // dimension pinned there must be rejected, symmetric with the group case.
      Map<String, SimpleFieldInfo> configs = Map.of("category", dimension("category"));

      UnsatisfiableBindingException e = assertThrows(UnsatisfiableBindingException.class,
         () -> WizAutoBindingService.validateExplicitBindings(
            List.of(pin("aggregates", "category")), configs, null));

      assertEquals("aggregates", e.getRole());
      assertEquals("category", e.getField());
      assertTrue(e.getReason().contains("measure-only"));
   }

   @Test
   void unknownFieldThrows() {
      Map<String, SimpleFieldInfo> configs = Map.of("amount", measure("amount"));

      UnsatisfiableBindingException e = assertThrows(UnsatisfiableBindingException.class,
         () -> WizAutoBindingService.validateExplicitBindings(
            List.of(pin("x", "missing_field")), configs, null));

      assertEquals("missing_field", e.getField());
   }

   @Test
   void validPinsPass() {
      Map<String, SimpleFieldInfo> configs = Map.of(
         "amount", measure("amount"),
         "category", dimension("category"));

      assertDoesNotThrow(() -> WizAutoBindingService.validateExplicitBindings(
         List.of(pin("y", "amount"), pin("color", "category")), configs, null));
   }

   @Test
   void emptyBindingsNoOp() {
      assertDoesNotThrow(() -> WizAutoBindingService.validateExplicitBindings(
         List.of(), Map.of(), null));
      assertDoesNotThrow(() -> WizAutoBindingService.validateExplicitBindings(
         null, null, null));
   }
}
