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
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.QueryManager;
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

   /**
    * Get the form table lens of a form table whose query returns a condition filter, which the
    * query manager cancels after it is returned on the fetches the predicate accepts.
    */
   private FormTableLens getFormTableLens(AtomicInteger fetches, IntPredicate cut,
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

      TableVSAQuery query = Mockito.mock(TableVSAQuery.class);
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

      try(MockedStatic<VSAQuery> st =
             Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(),
                                               Mockito.anyInt())).thenReturn(query);
         return box.getFormTableLens("Table1");
      }
   }
}
