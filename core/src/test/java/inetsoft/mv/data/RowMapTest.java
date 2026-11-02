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
package inetsoft.mv.data;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link RowMap}, the group map of {@link GroupedTableBlock}. {@code put} used to
 * compute the bucket index before the table was resized and then insert the new entry into the
 * resized table at that stale index. About half the time (when the bit the resize starts using
 * is set in the hash) the entry landed in the wrong bucket, so a later {@code put} of the same
 * group missed it and created a second entry: the group was reported twice with split
 * aggregates.
 *
 * <p>The map does not touch any Spring bean, so no context is needed.
 */
@Tag("core")
class RowMapTest {
   /**
    * Putting a group again right after it was inserted must find the existing entry, whatever
    * resize the insert itself triggered.
    */
   @Test
   void reinsertAfterResizeFindsSameEntry() {
      // initial capacity 1024 (the GroupedTableBlock minimum) resizes at the 717th group; the
      // smaller capacities exercise many more resizes, so both outcomes of the hash bit occur.
      for(int capacity : new int[] { 1, 2, 4, 16, 128, 1024 }) {
         RowMap map = new RowMap(capacity);

         for(int i = 0; i < 6000; i++) {
            MVRow row = row(i);
            RowMap.Entry entry = map.put(row);
            assertSame(row, entry.row, "capacity " + capacity + ", group " + i + " is new");

            RowMap.Entry again = map.put(row(i));
            assertSame(entry, again,
                       "capacity " + capacity + ", group " + i + " was inserted twice");
         }

         assertEquals(6000, map.size(), "capacity " + capacity);
         assertEquals(6000, map.getRows().size(), "capacity " + capacity);
      }
   }

   /**
    * The GroupedTableBlock default: more than 716 distinct groups in a 1024-slot map, each
    * group seen several times in a row as the detail rows of one block would be.
    */
   @Test
   void everyGroupAppearsOnceAfterDefaultMapResizes() {
      RowMap map = new RowMap(1024);
      int groups = 1500;

      for(int round = 0; round < 3; round++) {
         for(int i = 0; i < groups; i++) {
            map.put(row(i));
         }
      }

      List<MVRow> rows = map.getRows();
      assertEquals(groups, map.size());
      assertEquals(groups, rows.size());
      assertEquals(groups, rows.stream().map(r -> r.getGroups()[0]).distinct().count());
   }

   /**
    * Group ids spread over the whole int range, as dictionary-index combinations of real groups
    * are. Sequential small ids would never set the bit a resize starts using (a map resizes
    * while its group ids are still below its length), and so would never reach the defect.
    */
   private static MVRow row(long group) {
      return new MVRow(new long[] { group * 0x9E3779B97F4A7C15L }, new double[] { 1 });
   }
}
