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

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link CalcCellContext} is allocated per expanded row/column of a calc table
 * (RuntimeCalcTableLens.IndexMap), and each one owns two of these maps via
 * valueidx, so an oversized default backing table here multiplies directly into
 * heap usage on wide row expansions.
 */
@Tag("core")
class DelayedMergeIntMapTest {
   @Test
   void internalMapsStartSmall() throws Exception {
      DelayedMergeIntMap<String> map = new DelayedMergeIntMap<>();

      assertTrue(tableSize(map.getMap1()) <= 4,
                 "map1 should start sized for a couple of entries, not fastutil's default of 16");
      assertTrue(tableSize(map.getMap2()) <= 4,
                 "map2 should start sized for a couple of entries, not fastutil's default of 16");
   }

   /**
    * Merging happens when a row and column context combine (a cell expanded in
    * both directions), RuntimeCalcTableLens.getCellContext(row, col).
    */
   @Test
   void mergedMapSizedToActualEntryCount() throws Exception {
      DelayedMergeIntMap<String> map1 = new DelayedMergeIntMap<>();
      map1.put("a", 1);
      DelayedMergeIntMap<String> map2 = new DelayedMergeIntMap<>();
      map2.put("b", 2);

      DelayedMergeIntMap<String> merged = new DelayedMergeIntMap<>(map1, map2);
      merged.size(); // forces createMergedMap()

      assertTrue(tableSize(mergedMapOf(merged)) <= 4,
                 "merged map should be sized for its actual entry count, not fastutil's default of 16");
   }

   /**
    * Reads fastutil's private backing-table size field, since there is no public
    * accessor for a map's capacity.
    */
   private static int tableSize(Object2IntOpenHashMap<?> map) throws Exception {
      Field n = Object2IntOpenHashMap.class.getDeclaredField("n");
      n.setAccessible(true);
      return n.getInt(map);
   }

   private static Object2IntOpenHashMap<?> mergedMapOf(DelayedMergeIntMap<?> map) throws Exception {
      Field mergedMapField = DelayedMergeIntMap.class.getDeclaredField("mergedMap");
      mergedMapField.setAccessible(true);
      return (Object2IntOpenHashMap<?>) mergedMapField.get(map);
   }
}
