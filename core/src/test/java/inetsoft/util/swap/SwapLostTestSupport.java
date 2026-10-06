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

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Support for tests of a table built over a base whose swap file is lost (bug #77651). A lost
 * swapped fragment fails the base's cell reads ({@code getObject}), never its
 * {@code moreRows}, so {@link LostTable} fails only there.
 */
public final class SwapLostTestSupport {
   private SwapLostTestSupport() {
   }

   /**
    * The failure of a lost swap file, as a swapped fragment throws it.
    */
   public static SwapFileReadException swapLost() {
      return new SwapFileReadException(new File("lost.tdat"), new FileNotFoundException("lost.tdat"));
   }

   /**
    * Run {@code read} on another thread, failing if it is still running after
    * {@code capSeconds}.
    */
   public static <T> T within(long capSeconds, Callable<T> read) throws Exception {
      try {
         return await(capSeconds, read);
      }
      catch(ExecutionException ex) {
         Throwable cause = ex.getCause();

         if(cause instanceof Exception) {
            throw (Exception) cause;
         }

         throw (Error) cause;
      }
   }

   /**
    * Get what {@code read} failed with (on another thread, bounded by {@code capSeconds}),
    * failing if it returned instead.
    */
   public static Throwable failureOf(long capSeconds, Callable<?> read) throws Exception {
      Object value;

      try {
         value = await(capSeconds, read);
      }
      catch(ExecutionException ex) {
         return ex.getCause();
      }

      fail("the read returned " + describe(value) + " without an exception");
      return null;
   }

   private static <T> T await(long capSeconds, Callable<T> read) throws Exception {
      ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
         Thread thread = new Thread(r, "swap-lost-reader");
         thread.setDaemon(true);
         return thread;
      });

      try {
         return pool.submit(read).get(capSeconds, TimeUnit.SECONDS);
      }
      catch(TimeoutException ex) {
         fail("the read is still running after " + capSeconds + " s");
         return null;
      }
      finally {
         pool.shutdownNow();
      }
   }

   /**
    * Get the swap file read failure in the cause chain of {@code failure}, failing if there
    * is none.
    */
   public static SwapFileReadException swapIn(Throwable failure) {
      SwapFileReadException swap = SwapFileReadException.find(failure);

      if(swap == null) {
         throw new AssertionError("not a swap file read failure: " + failure, failure);
      }

      return swap;
   }

   /**
    * Read every row and cell of {@code table}.
    */
   public static List<List<Object>> drain(TableLens table) {
      List<List<Object>> rows = new ArrayList<>();

      for(int r = 0; table.moreRows(r); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < table.getColCount(); c++) {
            row.add(table.getObject(r, c));
         }

         rows.add(row);
      }

      return rows;
   }

   private static String describe(Object value) {
      if(value instanceof List) {
         return ((List<?>) value).size() + " rows";
      }

      return String.valueOf(value);
   }

   /**
    * A table whose data rows from {@code lostFrom} on fail every cell read with
    * {@code failure}, as the rows of a swapped fragment whose file is lost. Its
    * {@code moreRows} never fails.
    */
   public static class LostTable extends DefaultTableLens {
      public LostTable(Object[][] data, int lostFrom, RuntimeException failure) {
         super(data);
         this.lostFrom = lostFrom;
         this.failure = failure;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r >= lostFrom && r < getRowCount()) {
            lostRead = true;
            throw failure;
         }

         return super.getObject(r, c);
      }

      /**
       * Check if a lost row was read.
       */
      public boolean isLostRead() {
         return lostRead;
      }

      private final int lostFrom;
      private final RuntimeException failure;
      private volatile boolean lostRead;
   }
}
