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

package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.report.internal.table.MergedTable;
import inetsoft.test.*;
import inetsoft.util.Catalog;
import inetsoft.util.FileSystemService;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.LendableReentrantLock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A union (distinct), intersect or minus table lens whose merged table can't be created,
 * or whose merge fails, throws from its reads instead of reporting a completed empty (or
 * truncated) table, and a later read recovers once the cause is gone (bug #77524).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
public class SetTableLensMergedTableFailureTest {
   @Test
   public void minusCreateFailureThrows() {
      assertCreateFailureThrows(new FailingMinus(data(5), data(0)));
   }

   @Test
   public void intersectCreateFailureThrows() {
      assertCreateFailureThrows(new FailingIntersect(data(5), data(5)));
   }

   @Test
   public void distinctUnionCreateFailureThrows() {
      FailingUnion lens = new FailingUnion(data(5), data(5));
      lens.setDistinct(true);
      assertCreateFailureThrows(lens);
   }

   private void assertCreateFailureThrows(SetTableLens lens) {
      Failing failing = (Failing) lens;
      failing.control().failCreate.set(true);

      assertThrows(SetTableLens.SetOperationException.class, () -> lens.moreRows(0));
      // still failing within the retry delay, without creating the merged table again
      assertThrows(SetTableLens.SetOperationException.class, () -> lens.moreRows(TableLens.EOT));
      assertThrows(SetTableLens.SetOperationException.class, lens::getRowCount);
      assertThrows(SetTableLens.SetOperationException.class, () -> lens.getObject(1, 0));
      assertEquals(1, failing.control().creates.get());
   }

   @Test
   public void rowCountFirstThrows() {
      FailingMinus lens = new FailingMinus(data(5), data(0));
      lens.control().failCreate.set(true);

      SetTableLens.SetOperationException ex =
         assertThrows(SetTableLens.SetOperationException.class, lens::getRowCount);
      assertInstanceOf(IOException.class, ex.getCause());
      // the message is shown to the user, it is localized and never exposes the cause
      assertEquals(Catalog.getCatalog().getString("common.table.getDataFailed"), ex.getMessage());
      assertFalse(ex.getMessage().contains("temp file"), ex.getMessage());
   }

   /**
    * A base failing to report its header row count fails the read too, it is never an
    * empty table, and the merged table created for the pass is disposed.
    */
   @Test
   public void headerRowCountFailureThrowsAndRecovers() {
      AtomicBoolean fail = new AtomicBoolean(true);
      DefaultTableLens left = new DefaultTableLens(data(5)) {
         @Override
         public int getHeaderRowCount() {
            if(fail.get()) {
               throw new IllegalStateException("base failed");
            }

            return super.getHeaderRowCount();
         }
      };
      FailingMinus lens = new FailingMinus(left, new DefaultTableLens(data(0)));
      lens.control().retryDelay = 0;

      assertThrows(SetTableLens.SetOperationException.class, () -> lens.moreRows(0));
      assertTrue(lens.control().last.isDisposed(), "the failed merged table is disposed");

      fail.set(false);
      assertFalse(lens.moreRows(TableLens.EOT));
      assertEquals(6, lens.getRowCount());
   }

   /**
    * A first read while dispose() is disposing the bases (outside of the lens monitor) is an
    * empty table, it never merges the bases being disposed.
    */
   @Test
   public void readDuringDisposeIsEmpty() {
      FailingMinus[] holder = new FailingMinus[1];
      Boolean[] more = new Boolean[1];
      DefaultTableLens left = new DefaultTableLens(data(5)) {
         @Override
         public void dispose() {
            more[0] = holder[0].moreRows(0);
            super.dispose();
         }
      };
      FailingMinus lens = new FailingMinus(left, new DefaultTableLens(data(0)));
      holder[0] = lens;

      lens.dispose();

      assertEquals(Boolean.FALSE, more[0]);
      assertEquals(0, lens.control().creates.get());
   }

   /**
    * A failing pass superseded by invalidate() leaves the next pass alone, the next pass
    * returns all the rows.
    */
   @Test
   public void supersededWorkerFailureLeavesNextPass() {
      MinusTableLens[] holder = new MinusTableLens[1];
      AtomicInteger passes = new AtomicInteger();
      MinusTableLens lens = new MinusTableLens(new DefaultTableLens(data(10)),
                                               new DefaultTableLens(data(0)))
      {
         @Override
         protected MergedTable.Visitor getVisitor(Pass pass) {
            MergedTable.Visitor visitor = super.getVisitor(pass);
            boolean first = passes.incrementAndGet() == 1;
            AtomicInteger visited = new AtomicInteger();

            return row -> {
               if(first && visited.incrementAndGet() > 3) {
                  holder[0].invalidate();
                  throw new IllegalStateException("visitor failed");
               }

               visitor.visit(row);
            };
         }
      };
      holder[0] = lens;

      assertFalse(assertDoesNotThrow(() -> lens.moreRows(TableLens.EOT)));
      assertEquals(11, assertDoesNotThrow(lens::getRowCount));
      assertEquals(2, passes.get());
   }

   /**
    * The cancelled flag outlives a cancel(), a pass started after it that fails is still a
    * failed merge, not a cancelled one.
    */
   @Test
   public void failureAfterEarlierCancelThrows() {
      MinusTableLens lens = new MinusTableLens(new DefaultTableLens(data(10)),
                                               new DefaultTableLens(data(0)))
      {
         @Override
         protected MergedTable.Visitor getVisitor(Pass pass) {
            MergedTable.Visitor visitor = super.getVisitor(pass);
            AtomicInteger visited = new AtomicInteger();

            return row -> {
               if(visited.incrementAndGet() > 3) {
                  throw new IllegalStateException("visitor failed");
               }

               visitor.visit(row);
            };
         }
      };

      lens.cancel();
      assertTrue(lens.isCancelled());
      assertThrows(SetTableLens.SetOperationException.class, () -> lens.moreRows(TableLens.EOT));
   }

   @Test
   public void readAfterTransientCreateFailureRecovers() {
      FailingMinus lens = new FailingMinus(data(5), data(0));
      lens.control().retryDelay = 0;
      lens.control().failCreate.set(true);

      assertThrows(SetTableLens.SetOperationException.class, () -> lens.moreRows(0));

      lens.control().failCreate.set(false);
      assertTrue(lens.moreRows(0));
      assertFalse(lens.moreRows(TableLens.EOT));
      assertEquals(6, lens.getRowCount());
      assertEquals("id", lens.getObject(0, 0));
      assertEquals(2, lens.control().creates.get());
   }

   @Test
   public void invalidateRetriesAtOnce() {
      FailingMinus lens = new FailingMinus(data(5), data(0));
      lens.control().failCreate.set(true);
      assertThrows(SetTableLens.SetOperationException.class, () -> lens.moreRows(0));

      lens.control().failCreate.set(false);
      lens.invalidate();
      assertFalse(lens.moreRows(TableLens.EOT));
      assertEquals(6, lens.getRowCount());
   }

   @Test
   public void disposedBeforeReadIsEmpty() {
      FailingMinus lens = new FailingMinus(data(5), data(0));
      lens.dispose();

      assertFalse(lens.moreRows(0));
      assertEquals(0, lens.getRowCount());
      assertEquals(0, lens.control().creates.get());
   }

   /**
    * A base table failing while it is added to the merged table on the calling thread.
    */
   @Test
   public void addTableFailureThrowsAndRecovers() {
      AtomicBoolean fail = new AtomicBoolean(true);
      DefaultTableLens left = new DefaultTableLens(data(5)) {
         @Override
         public Object getObject(int r, int c) {
            if(r == 4 && fail.get()) {
               throw new IllegalStateException("base failed");
            }

            return super.getObject(r, c);
         }
      };
      FailingMinus lens = new FailingMinus(left, new DefaultTableLens(data(0)));
      lens.control().retryDelay = 0;

      assertThrows(SetTableLens.SetOperationException.class, () -> lens.moreRows(0));
      assertTrue(lens.control().last.isDisposed(), "the failed merged table is disposed");
      assertThrows(SetTableLens.SetOperationException.class, lens::getRowCount);

      fail.set(false);
      assertFalse(lens.moreRows(TableLens.EOT));
      assertEquals(6, lens.getRowCount());
   }

   /**
    * The merge worker failing must not complete the pass with the rows found so far.
    */
   @Test
   public void workerFailureThrowsAndRecovers() {
      AtomicBoolean fail = new AtomicBoolean(true);
      MinusTableLens lens = new MinusTableLens(new DefaultTableLens(data(10)),
                                               new DefaultTableLens(data(0)))
      {
         @Override
         protected MergedTable.Visitor getVisitor(Pass pass) {
            MergedTable.Visitor visitor = super.getVisitor(pass);
            AtomicInteger visited = new AtomicInteger();

            return row -> {
               if(fail.get() && visited.incrementAndGet() > 3) {
                  throw new IllegalStateException("visitor failed");
               }

               visitor.visit(row);
            };
         }

         @Override
         long getFailureRetryDelay() {
            return 0;
         }
      };

      assertThrows(SetTableLens.SetOperationException.class, () -> lens.moreRows(TableLens.EOT));

      fail.set(false);
      assertFalse(lens.moreRows(TableLens.EOT));
      assertEquals(11, lens.getRowCount());
   }

   /**
    * A pass that cancel() ended may fail with any exception (e.g. the btree's "File
    * interrupted!" RuntimeException), it still completes the rows found so far as a cancel
    * did before (bug #77397), it is not reported as a failed merge.
    */
   @Test
   public void cancelledWorkerFailureCompletesRowsSoFar() {
      MinusTableLens[] holder = new MinusTableLens[1];
      MinusTableLens lens = new MinusTableLens(new DefaultTableLens(data(10)),
                                               new DefaultTableLens(data(0)))
      {
         @Override
         protected MergedTable.Visitor getVisitor(Pass pass) {
            MergedTable.Visitor visitor = super.getVisitor(pass);
            AtomicInteger visited = new AtomicInteger();

            return row -> {
               if(visited.incrementAndGet() > 3) {
                  holder[0].cancel();
                  throw new RuntimeException("File interrupted!");
               }

               visitor.visit(row);
            };
         }
      };
      holder[0] = lens;

      assertFalse(assertDoesNotThrow(() -> lens.moreRows(TableLens.EOT)));
      assertTrue(lens.isCancelled());
      assertEquals(4, assertDoesNotThrow(lens::getRowCount));
   }

   /**
    * A thread holding a script lock merges on its own thread (bug #76938), a failure of
    * that merge fails the read too.
    */
   @Test
   public void scriptLockWorkerFailureThrows() {
      AtomicBoolean fail = new AtomicBoolean(true);
      MinusTableLens lens = new MinusTableLens(new DefaultTableLens(data(10)),
                                               new DefaultTableLens(data(0)))
      {
         @Override
         protected MergedTable.Visitor getVisitor(Pass pass) {
            MergedTable.Visitor visitor = super.getVisitor(pass);
            AtomicInteger visited = new AtomicInteger();

            return row -> {
               if(fail.get() && visited.incrementAndGet() > 3) {
                  throw new IllegalStateException("visitor failed");
               }

               visitor.visit(row);
            };
         }

         @Override
         long getFailureRetryDelay() {
            return 0;
         }
      };
      LendableReentrantLock lock = new LendableReentrantLock();
      lock.lock();
      JavaScriptEngine.pushHeldScriptLock(lock);

      try {
         // getRowCount() first: the merge ran (and failed) inside this call, never 0 rows
         assertThrows(SetTableLens.SetOperationException.class, lens::getRowCount);
         assertThrows(SetTableLens.SetOperationException.class,
                      () -> lens.moreRows(TableLens.EOT));

         fail.set(false);
         assertFalse(lens.moreRows(TableLens.EOT));
         assertEquals(11, lens.getRowCount());
      }
      finally {
         JavaScriptEngine.popHeldScriptLock();
         lock.unlock();
      }
   }

   @Test
   public void mergedTableWithoutTempFileThrowsIOException() throws Exception {
      File cache = new File(FileSystemService.getInstance().getCacheDirectory());
      assertTrue(cache.isDirectory());
      // a read-only directory attribute does not stop file creation on windows (no posix view)
      Assumptions.assumeTrue(
         cache.toPath().getFileSystem().supportedFileAttributeViews().contains("posix"),
         "posix file permissions are not supported");
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(cache.toPath());
      assertTrue(cache.setWritable(false, false));

      try {
         File probe = new File(cache, "probe-77524.tmp");
         Assumptions.assumeFalse(canCreate(probe), "cache directory is still writable");
         assertNull(FileSystemService.getInstance().getCacheTempFile("mergedTable", "dat"));

         IOException ex = assertThrows(IOException.class, MergedTable::new);
         assertTrue(ex.getMessage().contains("temp file"), ex.getMessage());

         // the real lens over an unwritable cache directory fails, never an empty table
         MinusTableLens lens = new MinusTableLens(new DefaultTableLens(data(5)),
                                                  new DefaultTableLens(data(0)));
         assertThrows(SetTableLens.SetOperationException.class, () -> lens.moreRows(0));
      }
      finally {
         // restore the original mode, without an assert that could skip it
         Files.setPosixFilePermissions(cache.toPath(), perms);
      }
   }

   /**
    * dispose() of an instance whose constructor never assigned the btree or the file (it is
    * still finalized) does not throw.
    */
   @Test
   public void disposeHalfBuiltMergedTable() throws Exception {
      Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
      field.setAccessible(true);
      Object unsafe = field.get(null);
      MergedTable table = (MergedTable) unsafe.getClass()
         .getMethod("allocateInstance", Class.class).invoke(unsafe, MergedTable.class);

      assertDoesNotThrow(table::dispose);
      assertTrue(table.isDisposed());
   }

   private static boolean canCreate(File file) {
      try {
         boolean created = file.createNewFile();
         file.delete();
         return created;
      }
      catch(IOException ex) {
         return false;
      }
   }

   private static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { "k" + r, r };
      }

      return data;
   }

   private static final class Control {
      MergedTable create(Factory factory) throws Exception {
         creates.incrementAndGet();

         if(failCreate.get()) {
            throw new IOException("Cannot create the cache temp file of a merged table");
         }

         return last = factory.create();
      }

      final AtomicBoolean failCreate = new AtomicBoolean();
      final AtomicInteger creates = new AtomicInteger();
      volatile long retryDelay = 60000;
      volatile MergedTable last;
   }

   private interface Factory {
      MergedTable create() throws Exception;
   }

   private interface Failing {
      Control control();
   }

   private static final class FailingMinus extends MinusTableLens implements Failing {
      FailingMinus(Object[][] left, Object[][] right) {
         this(new DefaultTableLens(left), new DefaultTableLens(right));
      }

      FailingMinus(TableLens left, TableLens right) {
         super(left, right);
      }

      @Override
      protected MergedTable createMergedTable() throws Exception {
         return control.create(super::createMergedTable);
      }

      @Override
      long getFailureRetryDelay() {
         return control.retryDelay;
      }

      @Override
      public Control control() {
         return control;
      }

      private final Control control = new Control();
   }

   private static final class FailingIntersect extends IntersectTableLens implements Failing {
      FailingIntersect(Object[][] left, Object[][] right) {
         super(new DefaultTableLens(left), new DefaultTableLens(right));
      }

      @Override
      protected MergedTable createMergedTable() throws Exception {
         return control.create(super::createMergedTable);
      }

      @Override
      long getFailureRetryDelay() {
         return control.retryDelay;
      }

      @Override
      public Control control() {
         return control;
      }

      private final Control control = new Control();
   }

   private static final class FailingUnion extends UnionTableLens implements Failing {
      FailingUnion(Object[][] left, Object[][] right) {
         super(new DefaultTableLens(left), new DefaultTableLens(right));
      }

      @Override
      protected MergedTable createMergedTable() throws Exception {
         return control.create(super::createMergedTable);
      }

      @Override
      long getFailureRetryDelay() {
         return control.retryDelay;
      }

      @Override
      public Control control() {
         return control;
      }

      private final Control control = new Control();
   }
}
