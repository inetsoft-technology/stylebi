/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.internal.table;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link CalcCellContext} is allocated per expanded row/column of a calc table
 * (RuntimeCalcTableLens.IndexMap), so an oversized default backing table for its
 * groups map multiplies directly into heap usage whenever a bound data table
 * produces a large row count.
 */
@Tag("core")
class CalcCellContextCapacityTest {
   @Test
   void groupsMapStartsSmall() throws Exception {
      CalcCellContext context = new CalcCellContext();

      assertTrue(tableSize(context.groups) <= 4,
                 "groups map should start sized for a couple of entries, not fastutil's default of 16");
   }

   /**
    * Reads fastutil's private backing-table size field, since there is no public
    * accessor for a map's capacity.
    */
   private static int tableSize(Map<?, ?> map) throws Exception {
      Field n = map.getClass().getDeclaredField("n");
      n.setAccessible(true);
      return n.getInt(map);
   }
}
