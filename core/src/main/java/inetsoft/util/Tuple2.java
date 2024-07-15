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

public class Tuple2<V1, V2> {
   public Tuple2(V1 first, V2 second) {
      this.first = first;
      this.second = second;
   }

   public V1 getFirst() {
      return first;
   }

   public V2 getSecond() {
      return second;
   }

   @Override
   public boolean equals(Object o) {
       if(this == o) {
          return true;
       }

       if(o == null || getClass() != o.getClass()) {
          return false;
       }

       Tuple2<?, ?> tuple2 = (Tuple2<?, ?>) o;
       return Objects.equals(first, tuple2.first) && Objects.equals(second, tuple2.second);
   }

   @Override
   public int hashCode() {
      return Objects.hash(first, second);
   }

   @Override
   public String toString() {
      return "Tuple2{" +
         "first=" + first +
         ", second=" + second +
         '}';
   }

   private final V1 first;
   private final V2 second;
}
