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
package inetsoft.report.composition.execution.reliability;

import java.util.*;

/**
 * How the harness reads a lens (Testing #77123). The result is always collected by row
 * index, so every pattern must give the same list.
 */
public record ReadPattern(Kind kind, long seed) {
   public enum Kind { SEQUENTIAL, RANDOM, REVERSE, PAGED_100, INVALIDATE_THEN_SEQUENTIAL }

   public static final ReadPattern SEQUENTIAL = new ReadPattern(Kind.SEQUENTIAL, 0);
   public static final ReadPattern REVERSE = new ReadPattern(Kind.REVERSE, 0);
   public static final ReadPattern PAGED_100 = new ReadPattern(Kind.PAGED_100, 0);
   public static final ReadPattern INVALIDATE_THEN_SEQUENTIAL =
      new ReadPattern(Kind.INVALIDATE_THEN_SEQUENTIAL, 0);

   public static ReadPattern random(long seed) {
      return new ReadPattern(Kind.RANDOM, seed);
   }

   /**
    * Every pattern, with a random one of the given seed.
    */
   public static List<ReadPattern> all(long seed) {
      return List.of(SEQUENTIAL, random(seed), REVERSE, PAGED_100, INVALIDATE_THEN_SEQUENTIAL);
   }

   /**
    * The data rows 1..rows in the order this pattern visits them; for PAGED_100 and
    * INVALIDATE_THEN_SEQUENTIAL the caller also does the page probe / invalidate.
    */
   public int[] order(int rows) {
      int[] order = new int[rows];

      for(int i = 0; i < rows; i++) {
         order[i] = i + 1;
      }

      if(kind == Kind.REVERSE) {
         for(int i = 0; i < rows; i++) {
            order[i] = rows - i;
         }
      }
      else if(kind == Kind.RANDOM) {
         Random random = new Random(seed);

         for(int i = rows - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
         }
      }

      return order;
   }

   @Override
   public String toString() {
      return kind == Kind.RANDOM ? "RANDOM(" + seed + ")" : kind.name();
   }
}
