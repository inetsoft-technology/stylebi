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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.composition.FormTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78398: the fetch of a form table's data runs without the sandbox locks (#74001), so two
 * threads may build the same form table at the same time, e.g. a table load and a script. The
 * second one built replaced the first one and its edits, the shared column selection was
 * cleared and converted again by each of them, and one built from the data before a reset was
 * cached after it. These tests force the interleavings of the threads, they don't reproduce the
 * timing of a running server.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormTableLensConcurrencyTest {
   // an edit made on the form table built first must not be lost when another one is built
   @Test
   void editOfFirstBuiltIsKept() throws Exception {
      CountDownLatch bFetching = new CountDownLatch(1);
      CountDownLatch edited = new CountDownLatch(1);
      AtomicInteger fetches = new AtomicInteger();
      ViewsheetSandbox box = formTableBox(n -> {
         fetches.incrementAndGet();

         if("B".equals(Thread.currentThread().getName())) {
            bFetching.countDown();
            await(edited);
         }

         return XTableUtil.getDefaultTableLens();
      });

      FutureTask<FormTableLens> b = new FutureTask<>(() -> getFormTableLens(box));
      new Thread(b, "B").start();
      assertTrue(bFetching.await(10, TimeUnit.SECONDS));

      FormTableLens form = getFormTableLens(box);
      form.setObject(1, 0, "EDITED");
      edited.countDown();

      assertSame(form, b.get(10, TimeUnit.SECONDS), "the one built first must be returned");
      assertEquals(2, fetches.get());
      assertSame(form, getFormTableLens(box));
      assertEquals("EDITED", getFormTableLens(box).getObject(1, 0));
   }

   // two first builds must not leave the table's column selection empty
   @Test
   void columnsAreKeptByConcurrentBuilds() throws Exception {
      GatedColumnSelection cols = new GatedColumnSelection();

      for(String name : new String[] { "col1", "col2", "col3" }) {
         cols.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      ViewsheetSandbox box = formTableBox(n -> XTableUtil.getDefaultTableLens());
      TableVSAssemblyInfo info = getInfo(box);
      info.setColumnSelection(cols);

      FutureTask<FormTableLens> a = new FutureTask<>(() -> getFormTableLens(box));
      FutureTask<FormTableLens> b = new FutureTask<>(() -> getFormTableLens(box));
      new Thread(a, "A").start();
      new Thread(b, "B").start();
      a.get(30, TimeUnit.SECONDS);
      b.get(30, TimeUnit.SECONDS);

      ColumnSelection ncols = info.getColumnSelection();
      assertEquals(3, ncols.getAttributeCount(), "the columns must not be lost");

      for(int i = 0; i < ncols.getAttributeCount(); i++) {
         assertInstanceOf(FormRef.class, ncols.getAttribute(i));
      }

      assertEquals(0, cols.clears.get(), "the shared selection must not be cleared");
   }

   // a form table built from the data before a reset must not be cached after it
   @Test
   void buildAcrossResetIsBuiltAgain() throws Exception {
      AtomicInteger fetches = new AtomicInteger();
      ViewsheetSandbox[] boxes = new ViewsheetSandbox[1];
      boxes[0] = formTableBox(n -> {
         int fetch = fetches.incrementAndGet();
         return fetch == 1 ? resetOnRead(boxes[0], "old") : table("new");
      });

      FormTableLens form = getFormTableLens(boxes[0]);

      assertEquals(2, fetches.get(), "the form table must be built again after the reset");
      assertEquals("new", form.getObject(1, 0));
      assertSame(form, boxes[0].getCachedFormTableLens("Table1"));
   }

   // a form table reset every time it's built is built a bounded number of times, the last one
   // is cached, an edit must not be made on one that isn't
   @Test
   void buildResetEveryTimeIsCached() throws Exception {
      AtomicInteger fetches = new AtomicInteger();
      ViewsheetSandbox[] boxes = new ViewsheetSandbox[1];
      boxes[0] = formTableBox(n -> resetOnRead(boxes[0], "v" + fetches.incrementAndGet()));

      FormTableLens form = getFormTableLens(boxes[0]);

      assertEquals(3, fetches.get(), "it must be built again a bounded number of times");
      assertEquals("v3", form.getObject(1, 0));
      assertSame(form, boxes[0].getCachedFormTableLens("Table1"));
   }

   private static FormTableLens getFormTableLens(ViewsheetSandbox box) throws Exception {
      // the static mock is per thread
      try(MockedStatic<VSAQuery> st =
             Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(),
                                               Mockito.anyInt())).thenReturn(query(box));
         return box.getFormTableLens("Table1");
      }
   }

   // a sandbox of a form table "Table1" whose query returns the data of the function
   private ViewsheetSandbox formTableBox(IntFunction<TableLens> data) throws Exception {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      ((TableVSAssemblyInfo) table.getInfo()).setForm(true);
      vs.addAssembly(table);
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "FormConcurrency78398", null);
      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                  false, entry);
      AtomicInteger calls = new AtomicInteger();
      TableVSAQuery query = Mockito.mock(TableVSAQuery.class);
      Mockito.when(query.getData()).thenAnswer(inv -> data.apply(calls.incrementAndGet()));
      queries.put(box, query);
      return box;
   }

   private static TableVSAQuery query(ViewsheetSandbox box) {
      return queries.get(box);
   }

   private static TableVSAssemblyInfo getInfo(ViewsheetSandbox box) {
      return (TableVSAssemblyInfo) box.getViewsheet().getAssembly("Table1").getInfo();
   }

   // data whose first row starts with the value
   private static DefaultTableLens table(String value) {
      Object[][] rows = XTableUtil.getDefaultData();
      rows[1][0] = value;
      return new DefaultTableLens(rows);
   }

   // data that resets the form table when it's read, like a selection's change during the build
   private static TableLens resetOnRead(ViewsheetSandbox box, String value) {
      Object[][] rows = XTableUtil.getDefaultData();
      rows[1][0] = value;

      return new DefaultTableLens(rows) {
         @Override
         public boolean moreRows(int row) {
            if(!reset) {
               reset = true;
               box.resetDataMap("Table1");
               box.syncFormData("Table1");
            }

            return super.moreRows(row);
         }

         private boolean reset;
      };
   }

   private static void await(CountDownLatch latch) {
      try {
         latch.await(10, TimeUnit.SECONDS);
      }
      catch(InterruptedException ex) {
         Thread.currentThread().interrupt();
      }
   }

   /**
    * The order the two threads cleared and converted the shared selection in before the fix:
    * A clears it, B copies it, A converts its columns, B clears and converts its copy. A gate
    * that never opens, e.g. when the selection isn't cleared, is waited for a short time only.
    */
   private static class GatedColumnSelection extends ColumnSelection {
      @Override
      public ColumnSelection clone(boolean shallow) {
         if("B".equals(Thread.currentThread().getName())) {
            awaitShort(aCleared);
            ColumnSelection copy = super.clone(shallow);
            bCopied.countDown();
            return copy;
         }

         return super.clone(shallow);
      }

      @Override
      public void clear() {
         clears.incrementAndGet();
         String thread = Thread.currentThread().getName();

         if("A".equals(thread)) {
            super.clear();
            aCleared.countDown();
            awaitShort(bCopied);
            return;
         }

         if("B".equals(thread)) {
            long end = System.currentTimeMillis() + 2000;

            while(getAttributeCount() < 3 && System.currentTimeMillis() < end) {
               Thread.onSpinWait();
            }
         }

         super.clear();
      }

      private static void awaitShort(CountDownLatch latch) {
         try {
            latch.await(2, TimeUnit.SECONDS);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      }

      private final CountDownLatch aCleared = new CountDownLatch(1);
      private final CountDownLatch bCopied = new CountDownLatch(1);
      private final AtomicInteger clears = new AtomicInteger();
   }

   private static final Map<ViewsheetSandbox, TableVSAQuery> queries =
      Collections.synchronizedMap(new IdentityHashMap<>());
}
