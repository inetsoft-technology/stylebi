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

import java.util.Objects;

public class TimestampIndexChange {
   public TimestampIndexChange(String key, TimestampIndexChangeType change) {
      this.key = key;
      this.change = change;
   }

   public String getKey() {
      return key;
   }

   public TimestampIndexChangeType getChange() {
      return change;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      final TimestampIndexChange that = (TimestampIndexChange) o;
      return Objects.equals(key, that.key) &&
         change == that.change;
   }

   @Override
   public String toString() {
      return "ChangeEntity{" +
         "key=" + key +
         ", change=" + change +
         '}';
   }

   private final String key;
   private final TimestampIndexChangeType change;
}
