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

import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77867: the queries of overlapping requests on one viewsheet run their data fetch at
 * the same time (the sandbox locks are released around it, #74001). Each query appends the
 * detail calc fields to the worksheet's base table, copies the viewsheet table bound to it
 * and prunes the calc fields its output doesn't use. None of that may fail or take away a calc
 * field another query needs.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSAQueryCalcFieldConcurrencyTest {
   @BeforeEach
   void setUp() throws Exception {
      ws = new Worksheet();
      EmbeddedTableAssembly base = new EmbeddedTableAssembly(ws, "T");
      ColumnSelection columns = new ColumnSelection();

      for(String col : new String[] { "a", "b", "c", "d" }) {
         ColumnRef column = new ColumnRef(new AttributeRef(null, col));
         column.setDataType("integer");
         columns.addAttribute(column);
      }

      base.setColumnSelection(columns, false);
      ws.addAssembly(base);

      vs = new Viewsheet();

      for(String calc : CALCS) {
         CalculateRef cref = new CalculateRef(true);
         ExpressionRef eref = new ExpressionRef(null, calc);
         eref.setExpression("field['a'] + 1");
         cref.setDataRef(eref);
         cref.setDataType("integer");
         vs.addCalcField("T", cref);
      }

      ViewsheetSandbox box = mock(ViewsheetSandbox.class, withSettings().stubOnly());
      doReturn(vs).when(box).getViewsheet();
      query = new OutputVSAQuery(box, "Text1");
      prune = VSAQuery.class.getDeclaredMethod("removeUnusedCalcFields", TableAssembly.class);
      prune.setAccessible(true);
   }

   /**
    * One query outputs a calc field while the others output a plain column and prune all the
    * calc fields. The calc field must stay in the table the first query runs on.
    */
   @Test
   void concurrentQueriesKeepTheCalcFieldsTheyUse() throws Exception {
      List<Throwable> errors = run(4, (thread, i) -> {
         boolean usesCalc = thread == 0;
         TableAssembly table = bindQuery("V_MT_" + thread, usesCalc ? "calc1" : "a");
         prune.invoke(query, table);
         TableAssembly child = ((MirrorTableAssembly) table).getTableAssembly();
         ColumnSelection priv = child.getColumnSelection(false);
         ColumnSelection pub = child.getColumnSelection(true);

         // the data fetch reads the child after the prune
         for(int k = 0; k < 3; k++) {
            if(usesCalc && (priv.getAttribute("calc1") == null ||
               pub.getAttribute("calc1") == null))
            {
               throw new AssertionError("calc1 was removed from the table the query runs on");
            }

            Thread.yield();
         }

         if(!usesCalc && priv.getAttribute("calc1") != null) {
            throw new AssertionError("the unused calc1 was not removed");
         }
      });

      assertNoErrors(errors);
      ColumnSelection shared = ((TableAssembly) ws.getAssembly("T")).getColumnSelection(false);

      for(String calc : CALCS) {
         assertNotNull(shared.getAttribute(calc), calc + " was removed from the worksheet");
      }
   }

   /**
    * A query on a worksheet wrapper copies the base table from the shared worksheet while
    * other queries append the calc fields to it. The copy must not fail or miss a calc field.
    */
   @Test
   void wrapperCopyOfTheBaseTableIsConsistent() throws Exception {
      VSAQuery.appendCalcField((TableAssembly) ws.getAssembly("T"), "T", true, vs);

      List<Throwable> errors = run(4, (thread, i) -> {
         if(thread == 0) {
            TableAssembly copy =
               (TableAssembly) new WorksheetWrapper(ws).getAssembly("T");

            for(String calc : CALCS) {
               if(copy.getColumnSelection(false).getAttribute(calc) == null) {
                  throw new AssertionError(calc + " is missing from the copy");
               }
            }
         }
         else {
            VSAQuery.getVSTableAssembly("T", false, vs, ws);
         }
      });

      assertNoErrors(errors);
   }

   /**
    * Do what a query does to the worksheet before it fetches the data: get the viewsheet
    * table, copy it for the query and select the output column.
    */
   private TableAssembly bindQuery(String name, String output) {
      TableAssembly vtable = VSAQuery.getVSTableAssembly("T", false, vs, ws);
      TableAssembly table = ViewsheetSandbox.copyBoundTable(vtable, name);
      ColumnSelection columns = table.getColumnSelection(false);
      ColumnSelection ncolumns = new ColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref = columns.getAttribute(i);

         if(output.equals(ref.getAttribute())) {
            ncolumns.addAttribute(ref);
         }
      }

      assertEquals(1, ncolumns.getAttributeCount(), output + " is not in the viewsheet table");
      table.setColumnSelection(ncolumns, false);
      return table;
   }

   private static List<Throwable> run(int threads, Step step) throws Exception {
      List<Throwable> errors = new CopyOnWriteArrayList<>();
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      CyclicBarrier start = new CyclicBarrier(threads);
      long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RUN_MILLIS);
      AtomicInteger runs = new AtomicInteger();

      try {
         List<Future<?>> futures = new ArrayList<>();

         for(int t = 0; t < threads; t++) {
            final int thread = t;
            futures.add(pool.submit(() -> {
               start.await();

               for(int i = 0; i < MAX_RUNS && errors.size() < MAX_ERRORS &&
                  System.nanoTime() < end; i++)
               {
                  try {
                     step.run(thread, i);
                     runs.incrementAndGet();
                  }
                  catch(InvocationTargetException ex) {
                     errors.add(ex.getCause());
                  }
                  catch(Throwable ex) {
                     errors.add(ex);
                  }
               }

               return null;
            }));
         }

         for(Future<?> future : futures) {
            future.get(RUN_MILLIS + 60_000, TimeUnit.MILLISECONDS);
         }
      }
      finally {
         pool.shutdownNow();
      }

      assertTrue(runs.get() > 0, "no query ran");
      return errors;
   }

   private static void assertNoErrors(List<Throwable> errors) {
      if(!errors.isEmpty()) {
         AssertionError error = new AssertionError(
            errors.size() + " concurrent queries failed, first: " + errors.get(0));
         error.initCause(errors.get(0));
         throw error;
      }
   }

   @FunctionalInterface
   private interface Step {
      void run(int thread, int iteration) throws Exception;
   }

   private static final String[] CALCS = { "calc1", "calc2", "calc3" };
   private static final long RUN_MILLIS = 3000;
   private static final int MAX_RUNS = 20000;
   private static final int MAX_ERRORS = 20;
   private Worksheet ws;
   private Viewsheet vs;
   private OutputVSAQuery query;
   private Method prune;
}
