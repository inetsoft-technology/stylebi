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

import inetsoft.report.composition.FormTableLens;
import inetsoft.report.filter.ConditionFilter;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.TableLoadException;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.util.CancelledException;
import inetsoft.util.ChangeCancelledException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78244: the data of a form table is a lazy table. A cancel of the table's query manager
 * after the data is returned and before it is read, e.g. by a selection's change while an auto
 * refresh builds the form table, cuts it short with a negative row count, and the form table
 * failed with NegativeArraySizeException: -2. getFormTableLens() must fetch such data again,
 * a bounded number of times, and a cancel by the user must stop it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormTableLensCancelTest {
   @Test
   void cutFormDataIsFetchedAgain() throws Exception {
      AtomicInteger fetches = new AtomicInteger();
      FormTableLens form = getFormTableLens(fetches, n -> n <= 2, false);

      assertEquals(3, fetches.get(), "the cut data must be fetched again");
      assertEquals(XTableUtil.getDefaultTableLens().getRowCount(), form.getRowCount());
   }

   @Test
   void formDataCutEveryTimeIsSkipped() {
      AtomicInteger fetches = new AtomicInteger();

      assertThrows(ChangeCancelledException.class,
                   () -> getFormTableLens(fetches, n -> true, false));
      assertEquals(20, fetches.get(), "the data must be fetched again a bounded number of times");
   }

   @Test
   void userCancelIsThrown() {
      AtomicInteger fetches = new AtomicInteger();
      CancelledException ex = assertThrows(CancelledException.class,
                                           () -> getFormTableLens(fetches, n -> true, true));

      assertFalse(ex instanceof ChangeCancelledException,
                  "the user's cancel must not be skipped like a newer change's");
      assertEquals(1, fetches.get(), "the user's cancel must not be fetched again");
   }

   // the pending edits check of every update tick reads it, it must not run the query
   @Test
   void cachedFormTableLensIsOnlyTheBuiltOne() throws Exception {
      ViewsheetSandbox box = formTableBox(new AtomicInteger(), n -> false, false);

      try(MockedStatic<VSAQuery> st =
             Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(),
                                               Mockito.anyInt())).thenReturn(query);
         assertNull(box.getCachedFormTableLens("Table1"), "it must not build the form table");
         Mockito.verify(query, Mockito.never()).getData();

         FormTableLens form = box.getFormTableLens("Table1");
         assertSame(form, box.getCachedFormTableLens("Table1"));

         // a table a script made no form has no form data
         ((TableVSAssemblyInfo) box.getViewsheet().getAssembly("Table1").getInfo())
            .setForm(false);
         assertNull(box.getCachedFormTableLens("Table1"));
      }
   }

   // a table whose rows failed to load also reads as cancelled, but running its query again
   // would not help, the form table keeps the rows that loaded like before (#77901)
   @Test
   void loadFailedFormDataIsNotFetchedAgain() throws Exception {
      ViewsheetSandbox box = formTableBox(new AtomicInteger(), n -> false, false);
      AtomicInteger fetches = new AtomicInteger();
      Mockito.doAnswer(inv -> {
         fetches.incrementAndGet();
         XNodeTableLens data = Mockito.mock(XNodeTableLens.class);
         Mockito.when(data.isCancelled()).thenReturn(true);
         Mockito.when(data.getLoadException()).thenReturn(new Exception("read failed"));
         Mockito.when(data.getRowCount()).thenReturn(3);
         Mockito.when(data.getColCount()).thenReturn(2);
         return data;
      }).when(query).getData();

      try(MockedStatic<VSAQuery> st =
             Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(),
                                               Mockito.anyInt())).thenReturn(query);
         FormTableLens form = box.getFormTableLens("Table1");

         assertEquals(1, fetches.get(), "a load failure must not be fetched again");
         assertEquals(3, form.getRowCount());
      }
   }

   // a reader that must not use partial rows is told by moreRows() that another reader of the
   // shared cached table cancelled it (#78200), it's fetched again like a cut table
   @Test
   void dataCancelledByOtherReaderIsFetchedAgain() throws Exception {
      ViewsheetSandbox box = formTableBox(new AtomicInteger(), n -> false, false);
      AtomicInteger fetches = new AtomicInteger();
      Mockito.doAnswer(inv -> {
         if(fetches.incrementAndGet() > 1) {
            return new ConditionFilter(XTableUtil.getDefaultTableLens(), new ConditionGroup());
         }

         XNodeTableLens data = Mockito.mock(XNodeTableLens.class);
         Mockito.when(data.isCancelled()).thenReturn(true);
         Mockito.when(data.moreRows(Mockito.anyInt())).thenThrow(
            new TableLoadException("Query was cancelled.", null));
         return data;
      }).when(query).getData();

      try(MockedStatic<VSAQuery> st =
             Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(),
                                               Mockito.anyInt())).thenReturn(query);
         FormTableLens form = box.getFormTableLens("Table1");

         assertEquals(2, fetches.get(), "the cancelled data must be fetched again");
         assertEquals(XTableUtil.getDefaultTableLens().getRowCount(), form.getRowCount());
      }
   }

   // a reader that must not use partial rows fails on a load failure, it's not fetched again
   @Test
   void loadFailureOfReaderIsThrown() throws Exception {
      ViewsheetSandbox box = formTableBox(new AtomicInteger(), n -> false, false);
      AtomicInteger fetches = new AtomicInteger();
      Exception cause = new Exception("read failed");
      Mockito.doAnswer(inv -> {
         fetches.incrementAndGet();
         XNodeTableLens data = Mockito.mock(XNodeTableLens.class);
         Mockito.when(data.isCancelled()).thenReturn(true);
         Mockito.when(data.getLoadException()).thenReturn(cause);
         Mockito.when(data.moreRows(Mockito.anyInt())).thenThrow(
            new TableLoadException("read failed", cause));
         return data;
      }).when(query).getData();

      try(MockedStatic<VSAQuery> st =
             Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(),
                                               Mockito.anyInt())).thenReturn(query);
         TableLoadException ex = assertThrows(TableLoadException.class,
                                              () -> box.getFormTableLens("Table1"));

         assertSame(cause, ex.getCause());
         assertEquals(1, fetches.get(), "a load failure must not be fetched again");
      }
   }

   /**
    * Get the form table lens of a form table whose query returns a condition filter, which the
    * query manager cancels after it is returned on the fetches the predicate accepts.
    */
   private FormTableLens getFormTableLens(AtomicInteger fetches, IntPredicate cut,
                                          boolean userCancel)
      throws Exception
   {
      ViewsheetSandbox box = formTableBox(fetches, cut, userCancel);

      try(MockedStatic<VSAQuery> st =
             Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(),
                                               Mockito.anyInt())).thenReturn(query);
         return box.getFormTableLens("Table1");
      }
   }

   // a sandbox of a form table "Table1" whose query is the query field
   private ViewsheetSandbox formTableBox(AtomicInteger fetches, IntPredicate cut,
                                         boolean userCancel)
      throws Exception
   {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      ((TableVSAssemblyInfo) table.getInfo()).setForm(true);
      vs.addAssembly(table);
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "FormCancel78244", null);
      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                  false, entry);
      QueryManager qmgr = box.getQueryManager("Table1");

      query = Mockito.mock(TableVSAQuery.class);
      Mockito.when(query.getData()).thenAnswer(inv -> {
         ConditionFilter data =
            new ConditionFilter(XTableUtil.getDefaultTableLens(), new ConditionGroup());
         qmgr.addPending(data);

         if(cut.test(fetches.incrementAndGet())) {
            // the cancel lands after the data is returned, e.g. the next selection's change
            if(userCancel) {
               qmgr.cancel();
            }
            else {
               qmgr.cancelForChange();
            }
         }

         return data;
      });
      return box;
   }

   private TableVSAQuery query; // the query of the last formTableBox()
}
