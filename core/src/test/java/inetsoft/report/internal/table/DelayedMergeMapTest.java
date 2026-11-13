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

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DelayedMergeMap backs CalcCellContext.groups whenever two contexts merge (a cell
 * expanded in both directions, RuntimeCalcTableLens.getCellContext(row, col)), so an
 * oversized merged-table allocation here multiplies directly into heap usage across
 * a crosstab's cells.
 */
@Tag("core")
class DelayedMergeMapTest {
   @Test
   void mergedMapSizedToActualEntryCount() throws Exception {
      Map<String, String> map1 = new Object2ObjectOpenHashMap<>();
      map1.put("a", "1");
      Map<String, String> map2 = new Object2ObjectOpenHashMap<>();
      map2.put("b", "2");

      DelayedMergeMap<String, String> merged = new DelayedMergeMap<>(map1, map2);
      merged.values(); // forces createMergedMap()

      assertTrue(tableSize(merged) <= 4,
                 "merged map should be sized for its actual entry count, not fastutil's default of 16");
   }

   /**
    * Reflects into DelayedMergeMap's private mergedMap field, then into fastutil's
    * private backing-table size field, since there is no public capacity accessor.
    */
   private static int tableSize(DelayedMergeMap<?, ?> merged) throws Exception {
      Field mergedMapField = DelayedMergeMap.class.getDeclaredField("mergedMap");
      mergedMapField.setAccessible(true);
      Object mergedMap = mergedMapField.get(merged);

      Field n = mergedMap.getClass().getDeclaredField("n");
      n.setAccessible(true);
      return n.getInt(mergedMap);
   }
}
