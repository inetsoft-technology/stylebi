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
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #79000: the data an assembly cached for a logical model viewsheet depends on the union of the
 * columns bound by ALL assemblies (VSUtil.shrinkTable shrinks one shared bound table to that
 * union), not only on the assembly's own binding. When another assembly changed the union, the
 * cached data stayed stale until the assembly happened to execute again (a format write, a
 * visible toggle), and then silently changed. Growing and shrinking the union must both expire
 * the cached data; a change that keeps the union must not.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxShrinkUnionCacheTest {
   @Test
   void tableDataExpiresWhenTheUnionGrows() throws Exception {
      assertExpiresOnUnionChange("Table1", "Order:Num", "Order:Num,Product:Total");
   }

   @Test
   void tableDataExpiresWhenTheUnionShrinks() throws Exception {
      assertExpiresOnUnionChange("Table1", "Order:Num,Product:Total", "Order:Num");
   }

   @Test
   void chartDataExpiresWhenTheUnionGrows() throws Exception {
      assertExpiresOnUnionChange("Chart1", "Order:Num", "Order:Num,Product:Total");
   }

   @Test
   void chartDataExpiresWhenTheUnionShrinks() throws Exception {
      assertExpiresOnUnionChange("Chart1", "Order:Num,Product:Total", "Order:Num");
   }

   @Test
   void dataIsKeptWhenTheUnionIsUnchanged() throws Exception {
      for(String name : new String[] { "Table1", "Chart1" }) {
         AtomicReference<String> union = new AtomicReference<>("Order:Num");
         ViewsheetSandbox box = sandbox();
         DataVSAQuery query = query();

         try(MockedStatic<VSAQuery> st = mockQuery(query);
             MockedStatic<VSUtil> su = mockUnion(union))
         {
            box.getData(name);
            box.getData(name);
            box.getData(name);
            Mockito.verify(query, Mockito.times(1)).getData();
         }
      }
   }

   @Test
   void dataIsKeptForAViewsheetThatIsNotBoundToALogicalModel() throws Exception {
      AtomicReference<String> union = new AtomicReference<>(null);
      ViewsheetSandbox box = sandbox();
      DataVSAQuery query = query();

      try(MockedStatic<VSAQuery> st = mockQuery(query);
          MockedStatic<VSUtil> su = mockUnion(union))
      {
         box.getData("Table1");
         box.getData("Table1");
         Mockito.verify(query, Mockito.times(1)).getData();
      }
   }

   @Test
   void viewTableExpiresWhenTheUnionChanges() throws Exception {
      AtomicReference<String> union = new AtomicReference<>("Order:Num");
      ViewsheetSandbox box = sandbox();
      DataVSAQuery query = query();

      try(MockedStatic<VSAQuery> st = mockQuery(query);
          MockedStatic<VSUtil> su = mockUnion(union))
      {
         VSTableLens first = box.getVSTableLens("Table1", false);
         assertSame(first, box.getVSTableLens("Table1", false));
         Mockito.verify(query, Mockito.times(1)).getData();

         union.set("Order:Num,Product:Total");
         VSTableLens second = box.getVSTableLens("Table1", false);
         assertNotSame(first, second, "the view table of a stale union was served from cache");
         Mockito.verify(query, Mockito.times(2)).getData();

         assertSame(second, box.getVSTableLens("Table1", false));
         Mockito.verify(query, Mockito.times(2)).getData();
      }
   }

   private static void assertExpiresOnUnionChange(String name, String before, String after)
      throws Exception
   {
      AtomicReference<String> union = new AtomicReference<>(before);
      ViewsheetSandbox box = sandbox();
      DataVSAQuery query = query();

      try(MockedStatic<VSAQuery> st = mockQuery(query);
          MockedStatic<VSUtil> su = mockUnion(union))
      {
         box.getData(name);
         box.getData(name);
         Mockito.verify(query, Mockito.times(1)).getData();

         union.set(after);
         box.getData(name);
         Mockito.verify(query, Mockito.times(2)).getData();

         // the data is cached again for the new union
         box.getData(name);
         Mockito.verify(query, Mockito.times(2)).getData();
      }
   }

   private static TableLens lens() {
      return new DefaultTableLens(new Object[][] {{ "a", "b" }, { 1, 10 }});
   }

   private static DataVSAQuery query() throws Exception {
      DataVSAQuery query = Mockito.mock(DataVSAQuery.class);
      Mockito.when(query.getData()).thenAnswer(inv -> lens());
      Mockito.when(query.getViewTableLens(Mockito.any()))
         .thenAnswer(inv -> new VSTableLens(inv.getArgument(0)));
      return query;
   }

   private static ViewsheetSandbox sandbox() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      vs.addAssembly(new ChartVSAssembly(vs, "Chart1"));
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "Union79000", null);
      return new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   private static MockedStatic<VSAQuery> mockQuery(VSAQuery query) {
      MockedStatic<VSAQuery> st = Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS);
      st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(), Mockito.anyInt()))
         .thenReturn(query);
      return st;
   }

   /**
    * Make the union fingerprint of the viewsheet controllable (the real fingerprint is covered
    * by VSUtilShrinkUnionKeyTest).
    */
   private static MockedStatic<VSUtil> mockUnion(AtomicReference<String> union) {
      MockedStatic<VSUtil> su = Mockito.mockStatic(VSUtil.class, Mockito.CALLS_REAL_METHODS);
      su.when(() -> VSUtil.getShrinkUnionKey(Mockito.any())).thenAnswer(inv -> union.get());
      return su;
   }
}
