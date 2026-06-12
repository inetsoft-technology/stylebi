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

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.web.wiz.model.ExplicitBinding;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the crosstab rows/cols pin handling added to WizAutoBindingService. Exercises the
 * pure repartition helpers directly — no recommender / sandbox needed — since the recommender
 * itself cannot honor rows/cols pins (ChartPreference has no such slots), so the service
 * repartitions the rendered crosstab headers from the explicitBindings.
 */
@Tag("core")
class WizAutoBindingServiceCrosstabPinsTest {
   private static DataRef dim(String field) {
      VSDimensionRef ref = mock(VSDimensionRef.class);
      when(ref.getGroupColumnValue()).thenReturn(field);
      return ref;
   }

   private static ExplicitBinding pin(String role, String field) {
      ExplicitBinding b = new ExplicitBinding();
      b.setRole(role);
      b.setField(field);
      return b;
   }

   private static List<String> keys(DataRef[] refs) {
      return java.util.Arrays.stream(refs)
         .map(WizAutoBindingService::headerFieldKey)
         .collect(java.util.stream.Collectors.toList());
   }

   @Test
   void swapsRowsAndCols() {
      // Recommender's (undesired) layout: category on rows, country on cols.
      DataRef[] rows = { dim("category") };
      DataRef[] cols = { dim("country") };

      DataRef[][] out = WizAutoBindingService.repartitionHeaders(
         rows, cols, List.of("country"), List.of("category"));

      assertEquals(List.of("country"), keys(out[0]), "country pinned to rows");
      assertEquals(List.of("category"), keys(out[1]), "category pinned to cols");
   }

   @Test
   void unpinnedDimensionsKeepTheirGroupAndOrder() {
      DataRef[] rows = { dim("a"), dim("b") };
      DataRef[] cols = { dim("c") };

      // Pin only c -> rows; a and b are unpinned and must stay on rows in original order.
      DataRef[][] out = WizAutoBindingService.repartitionHeaders(
         rows, cols, List.of("c"), List.of());

      assertEquals(List.of("c", "a", "b"), keys(out[0]));
      assertEquals(List.of(), keys(out[1]));
   }

   @Test
   void matchingIsCaseInsensitive() {
      DataRef[] rows = { dim("Category") };
      DataRef[] cols = { dim("Country") };

      DataRef[][] out = WizAutoBindingService.repartitionHeaders(
         rows, cols, List.of("COUNTRY"), List.of("category"));

      assertEquals(List.of("country"), keys(out[0]));
      assertEquals(List.of("category"), keys(out[1]));
   }

   @Test
   void rowPinWinsWhenFieldPinnedToBothRolesAndNullArraysAreSafe() {
      DataRef[] rows = { dim("x") };

      DataRef[][] out = WizAutoBindingService.repartitionHeaders(
         rows, null, List.of("x"), List.of("x"));

      assertEquals(List.of("x"), keys(out[0]));
      assertEquals(List.of(), keys(out[1]));
   }

   @Test
   void pinnedHeaderFieldsFiltersByRoleInOrderAndDeduplicates() {
      List<ExplicitBinding> bindings = List.of(
         pin("rows", "country"),
         pin("cols", "category"),
         pin("rows", "state"),
         pin("rows", "country")); // duplicate -> ignored

      assertEquals(List.of("country", "state"),
                   WizAutoBindingService.pinnedHeaderFields(bindings, "rows"));
      assertEquals(List.of("category"),
                   WizAutoBindingService.pinnedHeaderFields(bindings, "cols"));
      assertTrue(WizAutoBindingService.pinnedHeaderFields(null, "rows").isEmpty());
   }
}
