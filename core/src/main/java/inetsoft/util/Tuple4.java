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
package inetsoft.util;

import java.util.Objects;

public class Tuple4<V1, V2, V3, V4> {
   public Tuple4(V1 first, V2 second, V3 third, V4 forth) {
      this.first = first;
      this.second = second;
      this.third = third;
      this.forth = forth;
   }

   public V1 getFirst() {
      return first;
   }

   public V2 getSecond() {
      return second;
   }

   public V3 getThird() {
      return third;
   }

   public V4 getForth() {
      return forth;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      Tuple4<?, ?, ?, ?> Tuple4 = (Tuple4<?, ?, ?, ?>) o;
      return Objects.equals(first, Tuple4.first) &&
         Objects.equals(second, Tuple4.second) &&
         Objects.equals(third, Tuple4.third) &&
         Objects.equals(forth, Tuple4.forth);
   }

   @Override
   public int hashCode() {
      return Objects.hash(first, second, third, forth);
   }

   @Override
   public String toString() {
      return "Tuple4{" +
         "first=" + first +
         ", second=" + second +
         ", third=" + third +
         ", forth=" + forth +
         '}';
   }

   private final V1 first;
   private final V2 second;
   private final V3 third;
   private final V4 forth;
}
