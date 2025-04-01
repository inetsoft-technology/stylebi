/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.composition.graph;

import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.lens.SubTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class BrushSummaryFilterTest {
   @Test
   public void testSerialize() throws Exception {
      final SummaryFilter summaryFilter =
         new SummaryFilter(XTableUtil.getDefaultTableLens(), new int[]{ 0, 1 }, new int[]{ 2 },
                           new SumFormula(), null);
      summaryFilter.moreRows(Integer.MAX_VALUE);
      SubTableLens subTable = new SubTableLens(summaryFilter, 0, 0, 2, 2);
      BrushSummaryFilter originalTable = new BrushSummaryFilter(summaryFilter, subTable);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(BrushSummaryFilter.class, deserializedTable.getClass());
   }
}
