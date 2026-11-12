/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.report.filter;

import inetsoft.report.Comparer;
import inetsoft.util.Tool;

/**
 * A comparer that orders NULL above every other value, as a database whose ascending ORDER BY
 * returns NULL last (DB2, Oracle, PostgreSQL) does. {@link SortFilter} calls its comparers
 * ascending and reverses the result for a descending column, so NULL is last when ascending and
 * first when descending. Everything that is not NULL is compared by the wrapped comparer.
 */
public class NullsHighComparer implements Comparer {
   /**
    * Create a comparer.
    * @param base the comparer for non-null values.
    */
   public NullsHighComparer(Comparer base) {
      this.base = base;
   }

   /**
    * Get the comparer used for non-null values.
    */
   public Comparer getBase() {
      return base;
   }

   @Override
   public int compare(Object v1, Object v2) {
      if(v1 == null || v2 == null) {
         return v1 == v2 ? 0 : (v1 == null ? 1 : -1);
      }

      return base.compare(v1, v2);
   }

   @Override
   public int compare(double v1, double v2) {
      if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? 1 : -1);
      }

      return base.compare(v1, v2);
   }

   @Override
   public int compare(float v1, float v2) {
      if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? 1 : -1);
      }

      return base.compare(v1, v2);
   }

   @Override
   public int compare(long v1, long v2) {
      if(v1 == Tool.NULL_LONG || v2 == Tool.NULL_LONG) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_LONG ? 1 : -1);
      }

      return base.compare(v1, v2);
   }

   @Override
   public int compare(int v1, int v2) {
      if(v1 == Tool.NULL_INTEGER || v2 == Tool.NULL_INTEGER) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_INTEGER ? 1 : -1);
      }

      return base.compare(v1, v2);
   }

   @Override
   public int compare(short v1, short v2) {
      if(v1 == Tool.NULL_SHORT || v2 == Tool.NULL_SHORT) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_SHORT ? 1 : -1);
      }

      return base.compare(v1, v2);
   }

   private final Comparer base;
}
