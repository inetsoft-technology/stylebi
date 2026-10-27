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
package inetsoft.mv;

import inetsoft.mv.MVDef.MVContainer;
import inetsoft.mv.data.MVBuilder;
import inetsoft.mv.data.SubMV;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79384: a column fill task of {@link MVBuilder}'s block iterator that dies with an
 * {@link Error} (not an {@link Exception}) must fail the build instead of leaving
 * {@code hasNext()} waiting forever on a future that is never completed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVBuilderWorkerErrorTest {
   private static final int ROWS = 6;

   @Test
   void dimensionTaskErrorFailsBuild() {
      assertFails(1, 0, OutOfMemoryError::new, OutOfMemoryError.class);
   }

   @Test
   void dimensionTaskLinkageErrorFailsBuild() {
      assertFails(1, 0, () -> new NoClassDefFoundError("x"), NoClassDefFoundError.class);
   }

   @Test
   void measureTaskErrorFailsBuild() {
      assertFails(0, 1, OutOfMemoryError::new, OutOfMemoryError.class);
   }

   @Test
   void dimensionTaskExceptionFailsBuild() {
      assertFails(1, 0, () -> new IllegalStateException("boom"), IllegalStateException.class);
   }

   @Test
   void noFaultBuilds() throws Exception {
      Object r = run(-1, -1, null);
      assertEquals(Boolean.TRUE, r);
   }

   private void assertFails(int dimCol, int measCol, Supplier<Throwable> fault,
                            Class<? extends Throwable> expected)
   {
      Object r = run(dimCol == 1 ? 0 : -1, measCol == 1 ? 1 : -1, fault);
      assertTrue(r instanceof Throwable, "hasNext should fail, got " + r);
      Throwable t = (Throwable) r;
      boolean found = false;

      for(; t != null; t = t.getCause()) {
         if(expected.isInstance(t)) {
            found = true;
            break;
         }
      }

      assertTrue(found, "expected cause " + expected + " in " + r);
   }

   /** Runs hasNext on a daemon thread; returns Boolean, the thrown Throwable, or "HANG". */
   private Object run(int faultDim, int faultMeasure, Supplier<Throwable> fault) {
      DefaultTableLens table = new DefaultTableLens(newData()) {
         @Override
         public Object getObject(int r, int c) {
            // fail only in MVBuilder's own column fill tasks, not in the dictionary pass of
            // MVCreatorUtil that runs first
            if(r > 0 && (c == faultDim || c == faultMeasure) && inFillTask()) {
               Throwable t = fault.get();
               if(t instanceof Error) throw (Error) t;
               throw (RuntimeException) t;
            }

            return super.getObject(r, c);
         }
      };

      MVBuilder builder = new MVBuilder(table, newDef(), false);
      Iterator<SubMV> it = builder.getSubMVs();
      CompletableFuture<Object> result = new CompletableFuture<>();
      Thread th = new Thread(() -> {
         try {
            result.complete(it.hasNext());
         }
         catch(Throwable ex) {
            result.complete(ex);
         }
      });
      th.setDaemon(true);
      th.start();

      try {
         return result.get(8, TimeUnit.SECONDS);
      }
      catch(TimeoutException ex) {
         th.interrupt();
         return "HANG";
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private static boolean inFillTask() {
      for(StackTraceElement e : new Throwable().getStackTrace()) {
         if(e.getClassName().startsWith("inetsoft.mv.data.MVBuilder$") && "run".equals(e.getMethodName())) {
            return true;
         }
      }

      return false;
   }

   private static Object[][] newData() {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "dim", "str" };

      for(int r = 0; r < ROWS; r++) {
         data[r + 1] = new Object[] { "d" + (r % 3), "m" + r };
      }

      return data;
   }

   private static MVDef newDef() {
      ColumnRef dimRef = new ColumnRef(new AttributeRef(null, "dim"));
      dimRef.setDataType(XSchema.STRING);
      ColumnRef strRef = new ColumnRef(new AttributeRef(null, "str"));
      strRef.setDataType(XSchema.STRING);

      List<MVColumn> columns = new ArrayList<>();
      columns.add(new MVColumn(dimRef, true));
      columns.add(new MVColumn(strRef, false));

      MVDef def = new MVDef();
      def.container = new MVContainer(columns, null);
      return def;
   }
}
