/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import java.util.*;

public class TimestampIndexChangelist {
   public List<TimestampIndexChange> getChanges(Map<String, Long> oldTimestamps,
                                                Map<String, Long> newTimestamps)
   {
      final List<TimestampIndexChange> changes = new ArrayList<>();

      for(final Map.Entry<String, Long> entry : oldTimestamps.entrySet()) {
         final String key = entry.getKey();
         final Long oldTimestamp = entry.getValue();
         final Long newTimeStamp = newTimestamps.get(key);

         if(newTimeStamp == null) {
            changes.add(new TimestampIndexChange(key, TimestampIndexChangeType.REMOVE));
         }
         else if(!oldTimestamp.equals(newTimeStamp)) {
            changes.add(new TimestampIndexChange(key, TimestampIndexChangeType.MODIFY));
         }
      }

      for(final Map.Entry<String, Long> entry : newTimestamps.entrySet()) {
         final String key = entry.getKey();
         final Long oldTimestamp = oldTimestamps.get(key);

         if(oldTimestamp == null) {
            changes.add(new TimestampIndexChange(key, TimestampIndexChangeType.ADD));
         }
      }

      return changes;
   }
}
