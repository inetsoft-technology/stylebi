/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimestampIndexChangelistTest {
   @BeforeEach
   void setup() {
      oldTimestamps = new HashMap<>();
      oldTimestamps.put("A", 0L);
   }

   @Test
   void add() {
      final HashMap<String, Long> newTimestamps = new HashMap<>(oldTimestamps);
      newTimestamps.put("B", 0L);

      final List<TimestampIndexChange> actualChanges =
         changelist.getChanges(oldTimestamps, newTimestamps);

      final List<TimestampIndexChange> expectedChanges =
         Collections.singletonList(new TimestampIndexChange("B", TimestampIndexChangeType.ADD));

      assertEquals(expectedChanges, actualChanges);
   }

   @Test
   void remove() {
      final HashMap<String, Long> newTimestamps = new HashMap<>(oldTimestamps);
      newTimestamps.remove("A");

      final List<TimestampIndexChange> actualChanges =
         changelist.getChanges(oldTimestamps, newTimestamps);

      final List<TimestampIndexChange> expectedChanges =
         Collections.singletonList(new TimestampIndexChange("A", TimestampIndexChangeType.REMOVE));

      assertEquals(expectedChanges, actualChanges);
   }

   @Test
   void modify() {
      final HashMap<String, Long> newTimestamps = new HashMap<>(oldTimestamps);
      newTimestamps.put("A", 1L);

      final List<TimestampIndexChange> actualChanges =
         changelist.getChanges(oldTimestamps, newTimestamps);

      final List<TimestampIndexChange> expectedChanges =
         Collections.singletonList(new TimestampIndexChange("A", TimestampIndexChangeType.MODIFY));

      assertEquals(expectedChanges, actualChanges);
   }

   private final TimestampIndexChangelist changelist = new TimestampIndexChangelist();
   private Map<String, Long> oldTimestamps;
}
