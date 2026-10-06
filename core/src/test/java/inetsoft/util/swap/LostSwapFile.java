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
package inetsoft.util.swap;

import inetsoft.report.lens.DefaultTableLens;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A swapped fragment whose swap file was deleted, as a cache sweeper leaves it (bug #77910).
 * Every {@link #read()} fails with the {@link SwapFileReadException} the fragment throws for
 * the lost file. Needs the {@code SwapperTestConfiguration} context.
 */
public final class LostSwapFile implements AutoCloseable {
   public LostSwapFile() {
      int[] values = new int[100];

      for(int i = 0; i < values.length; i++) {
         values[i] = i;
      }

      fragment = new XIntFragment(values);
      assertTrue(fragment.swap(), "fragment was not swapped");
      file = fragment.getFile(fragment.prefix + ".tdat");
      assertTrue(file.delete(), "swap file was not deleted");
   }

   /**
    * Read the lost fragment, which always fails.
    */
   public void read() {
      fragment.getSafely(0);
      fail("a fragment with a deleted swap file was read");
   }

   /**
    * Get the deleted swap file.
    */
   public File getFile() {
      return file;
   }

   @Override
   public void close() {
      fragment.dispose();
   }

   private final XIntFragment fragment;
   private final File file;

   /**
    * A table whose rows from {@code lostFrom} on are in the lost swap file while
    * {@link #lost} is set: reading them, through {@code moreRows} or {@code getObject} as
    * chosen, reads the lost fragment and fails as it does.
    */
   public static class Table extends DefaultTableLens {
      public Table(Object[][] data, int lostFrom, boolean moreRowsFails, LostSwapFile swap) {
         super(data);
         this.lostFrom = lostFrom;
         this.moreRowsFails = moreRowsFails;
         this.swap = swap;
      }

      @Override
      public boolean moreRows(int row) {
         if(moreRowsFails && lost && row >= lostFrom) {
            swap.read();
         }

         return super.moreRows(row);
      }

      @Override
      public Object getObject(int r, int c) {
         if(!moreRowsFails && lost && r >= lostFrom && r < getRowCount()) {
            swap.read();
         }

         return super.getObject(r, c);
      }

      public volatile boolean lost = true;
      private final int lostFrom;
      private final boolean moreRowsFails;
      private final LostSwapFile swap;
   }
}
