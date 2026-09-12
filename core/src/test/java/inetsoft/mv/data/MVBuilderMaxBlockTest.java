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
package inetsoft.mv.data;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link MVBuilder#getMaxBreakRow}, the bound on how far a block may extend while
 * looking for a distinct-count break boundary.
 *
 * <p>{@code mv.max.block} is a block <em>length</em>, but the loop used to compare it against
 * {@code nextb} -- an absolute index into the source table. The allowance therefore shrank as the
 * build advanced, and once the row cursor passed {@code mv.max.block} the loop body stopped
 * running altogether: the protection silently switched off for the rest of the table, and the
 * "caused MV block to exceed maximum size" warning fired for every remaining block.
 */
@Tag("core")
class MVBuilderMaxBlockTest {
   private static final int MAX_BLOCK = 10_000_000;
   private static final int PREFERRED = 1_000_000;

   /**
    * The reported bug: at a row cursor past mv.max.block the old comparison
    * ({@code nextb <= maxBlock}) was already false, so no rows were scanned for a break boundary.
    */
   @Test
   void allowsFullBlockLengthWellPastMaxBlock() {
      int r = 20_000_000;
      int maxRow = MVBuilder.getMaxBreakRow(r, PREFERRED, MAX_BLOCK);

      assertTrue(maxRow > r + PREFERRED,
                 "a block starting past mv.max.block must still be allowed to look ahead");
      assertEquals(r + MAX_BLOCK, maxRow, "every block gets the same allowance");
   }

   /** Every block gets the same allowance, regardless of how far into the table it starts. */
   @Test
   void allowanceIsIndependentOfBlockPosition() {
      int first = MVBuilder.getMaxBreakRow(0, PREFERRED, MAX_BLOCK);
      int later = MVBuilder.getMaxBreakRow(50_000_000, PREFERRED, MAX_BLOCK);

      assertEquals(first - 0, later - 50_000_000);
   }

   /**
    * A mv.max.block smaller than the block already accumulated must not produce a bound behind
    * the cursor -- that would make size negative, and hasNext() treats size <= 0 as end of data,
    * silently truncating the MV.
    */
   @Test
   void neverBoundsBelowTheBlockAlreadyAccumulated() {
      int r = 5_000;
      int size = PREFERRED;
      int maxRow = MVBuilder.getMaxBreakRow(r, size, 100);

      assertEquals(r + size, maxRow);
      assertTrue(maxRow - r > 0, "block size must stay positive");
   }

   /**
    * A large configured mv.max.block near the end of a big table must not wrap to a negative
    * bound -- that would silently switch the protection back off, the very bug being fixed.
    */
   @Test
   void saturatesInsteadOfOverflowing() {
      int maxRow = MVBuilder.getMaxBreakRow(Integer.MAX_VALUE - 10, PREFERRED, MAX_BLOCK);

      assertEquals(Integer.MAX_VALUE, maxRow);
      assertTrue(maxRow > 0, "bound must never wrap negative");
   }

   @Test
   void honorsAnExplicitlyConfiguredLimit() {
      assertEquals(1_000 + 250_000, MVBuilder.getMaxBreakRow(1_000, 1_000, 250_000));
   }
}
