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

import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The sort order WizFieldInfoFactory reports for a dimension.
 *
 * <p>Load-bearing for changeType's rebuild branch, which re-seeds the wizard temp chart from the target
 * chart's own binding via {@code collectFlatBinding}: order is what gives a value-based sort (and a
 * ranking) its direction, so a sortByCol reported without it is inert and the rebuilt chart comes back
 * unsorted.
 */
@Tag("core")
class WizFieldInfoFactorySortOrderTest {
   private static DimensionFieldInfo reported(int order) {
      VSDimensionRef dim = mock(VSDimensionRef.class);
      when(dim.getGroupColumnValue()).thenReturn("STATE");
      when(dim.getOrder()).thenReturn(order);
      return WizFieldInfoFactory.createChartDimensionFieldInfo(dim);
   }

   @Test
   void reportsTheFourExplicitOrders() {
      assertEquals(XConstants.SORT_ASC, reported(XConstants.SORT_ASC).getOrder());
      assertEquals(XConstants.SORT_DESC, reported(XConstants.SORT_DESC).getOrder());
      assertEquals(XConstants.SORT_VALUE_ASC, reported(XConstants.SORT_VALUE_ASC).getOrder());
      assertEquals(XConstants.SORT_VALUE_DESC, reported(XConstants.SORT_VALUE_DESC).getOrder());
   }

   /**
    * An order carrying no user intent must stay unreported: a re-applied config is authoritative, so
    * echoing SORT_NONE would override whatever ordering the recommender picks for the target type.
    */
   @Test
   void staysSilentOnAnOrderThatCarriesNoIntent() {
      assertNull(reported(XConstants.SORT_NONE).getOrder());
      assertNull(reported(XConstants.SORT_ORIGINAL).getOrder());
   }

   /**
    * SORT_SPECIFIC is excluded because the manual value list it depends on is deliberately NOT reported
    * (applyFieldConfig reverses a supplied list for a funnel, so a round trip through it inverts), and a
    * manual order with no list would sort by nothing.
    */
   @Test
   void staysSilentOnAManualOrder() {
      assertNull(reported(XConstants.SORT_SPECIFIC).getOrder());
   }
}
