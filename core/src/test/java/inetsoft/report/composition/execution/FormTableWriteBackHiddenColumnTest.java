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

import inetsoft.report.StyleConstants;
import inetsoft.report.composition.FormTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79695: a form table writes its rows back to the embedded worksheet table by position,
 * and only the columns the form shows. A column the form does not show (e.g. a hidden ID) kept
 * its value at the old row position, so after a delete or an insert it no longer belonged to the
 * row it was on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormTableWriteBackHiddenColumnTest {
   @Test
   void deleteFirstRowKeepsHiddenIdWithItsRow() throws Exception {
      Fixture f = idNameFixture();
      f.lens.deleteRow(1);
      f.box.writeBackFormData(f.table);

      assertRows(f.embedded, new Object[][] {{ 2, "Bread" }, { 3, "Cheese" }});
   }

   @Test
   void deleteLastRow() throws Exception {
      Fixture f = idNameFixture();
      f.lens.deleteRow(3);
      f.box.writeBackFormData(f.table);

      assertRows(f.embedded, new Object[][] {{ 1, "Apple" }, { 2, "Bread" }});
   }

   @Test
   void nonContiguousDeletes() throws Exception {
      Fixture f = new Fixture(new String[] { XSchema.INTEGER, XSchema.STRING },
                              new Object[][] {{ 1, "Apple" }, { 2, "Bread" }, { 3, "Cheese" },
                                              { 4, "Dates" }},
                              new int[] { 1 });
      f.lens.deleteRow(1);
      // the lens is now Bread, Cheese, Dates; Cheese is lens row 2
      f.lens.deleteRow(2);
      f.box.writeBackFormData(f.table);

      assertRows(f.embedded, new Object[][] {{ 2, "Bread" }, { 4, "Dates" }});
   }

   // the lens is kept after an apply (script path), the next apply must start from the rows'
   // new positions
   @Test
   void deleteApplyDeleteApplyOnSameLens() throws Exception {
      Fixture f = idNameFixture();
      f.lens.deleteRow(1);
      f.box.writeBackFormData(f.table);
      assertRows(f.embedded, new Object[][] {{ 2, "Bread" }, { 3, "Cheese" }});

      f.lens.deleteRow(1);
      f.box.writeBackFormData(f.table);
      assertRows(f.embedded, new Object[][] {{ 3, "Cheese" }});
   }

   @Test
   void insertInMiddleLeavesHiddenIdEmpty() throws Exception {
      Fixture f = idNameFixture();
      f.lens.insertRow(2);
      f.lens.setObject(2, 0, "New");
      f.box.writeBackFormData(f.table);

      assertRows(f.embedded, new Object[][] {{ 1, "Apple" }, { null, "New" }, { 2, "Bread" },
                                             { 3, "Cheese" }});
   }

   @Test
   void insertApplyThenDelete() throws Exception {
      Fixture f = idNameFixture();
      f.lens.insertRow(2);
      f.lens.setObject(2, 0, "New");
      f.box.writeBackFormData(f.table);

      f.lens.deleteRow(1);
      f.box.writeBackFormData(f.table);

      assertRows(f.embedded, new Object[][] {{ null, "New" }, { 2, "Bread" }, { 3, "Cheese" }});
   }

   @Test
   void editOnlyKeepsHiddenIds() throws Exception {
      Fixture f = idNameFixture();
      f.lens.setObject(2, 0, "Baguette");
      f.box.writeBackFormData(f.table);

      assertRows(f.embedded, new Object[][] {{ 1, "Apple" }, { 2, "Baguette" }, { 3, "Cheese" }});
   }

   @Test
   void deleteUnderSortedView() throws Exception {
      Fixture f = idNameFixture();
      f.lens.sort(0, StyleConstants.SORT_DESC);
      // sorted view is Cheese, Bread, Apple
      f.lens.deleteRow(1);
      f.box.writeBackFormData(f.table);

      assertRows(f.embedded, new Object[][] {{ 1, "Apple" }, { 2, "Bread" }});
   }

   // the hidden column is not the first one, so the visible columns map to 0 and 2
   @Test
   void hiddenNonFirstColumn() throws Exception {
      Fixture f = new Fixture(new String[] { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER },
                              new Object[][] {{ "Apple", 1, 10 }, { "Bread", 2, 20 },
                                              { "Cheese", 3, 30 }},
                              new int[] { 0, 2 });
      f.lens.deleteRow(1);
      f.box.writeBackFormData(f.table);

      assertRows(f.embedded, new Object[][] {{ "Bread", 2, 20 }, { "Cheese", 3, 30 }});
   }

   private Fixture idNameFixture() throws Exception {
      return new Fixture(new String[] { XSchema.INTEGER, XSchema.STRING },
                         new Object[][] {{ 1, "Apple" }, { 2, "Bread" }, { 3, "Cheese" }},
                         new int[] { 1 });
   }

   private void assertRows(XEmbeddedTable embedded, Object[][] expected) {
      assertEquals(expected.length + 1, embedded.getRowCount(), "row count");

      for(int r = 0; r < expected.length; r++) {
         for(int c = 0; c < expected[r].length; c++) {
            assertEquals(expected[r][c], embedded.getObject(r + 1, c),
                         "row " + r + " col " + c);
         }
      }
   }

   /**
    * A form table "Table1" bound to an embedded worksheet table "Prices", showing only the
    * columns in visibleCols; the others are hidden, so they are not in the form lens.
    */
   private static class Fixture {
      Fixture(String[] types, Object[][] data, int[] visibleCols) throws Exception {
         String[] names = { "Name", "ID", "Qty" };
         // the embedded table of the tests is (ID, Name) or (Name, ID, Qty)
         String[] header = types.length == 2 ? new String[] { "ID", "Name" } : names;
         Object[][] arr = new Object[data.length + 1][];
         arr[0] = header;
         System.arraycopy(data, 0, arr, 1, data.length);

         Worksheet ws = new Worksheet();
         EmbeddedTableAssembly wstable = new EmbeddedTableAssembly(ws, "Prices");
         embedded = new XEmbeddedTable(types, arr);
         wstable.setEmbeddedData(embedded);
         ws.addAssembly(wstable);

         Viewsheet vs = new Viewsheet();
         ReflectionTestUtils.setField(vs, "originalWs", ws);
         table = new TableVSAssembly(vs, "Table1");
         TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getInfo();
         info.setForm(true);
         info.setWriteBack(true);
         info.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "Prices"));
         ColumnSelection cols = new ColumnSelection();
         Object[][] visible = new Object[arr.length][visibleCols.length];

         for(int j = 0; j < visibleCols.length; j++) {
            String h = header[visibleCols[j]];
            ColumnRef col = new ColumnRef(new AttributeRef(null, h));
            col.setDataType(types[visibleCols[j]]);
            cols.addAttribute(col);

            for(int i = 0; i < arr.length; i++) {
               visible[i][j] = arr[i][visibleCols[j]];
            }
         }

         info.setColumnSelection(cols);
         vs.addAssembly(table);

         lens = new FormTableLens(new DefaultTableLens(visible));
         AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                           AssetEntry.Type.VIEWSHEET, "FormWriteBack79695", null);
         ViewsheetSandbox real = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                      false, entry);
         box = Mockito.spy(real);
         Mockito.doReturn(lens).when(box).getFormTableLens("Table1");
      }

      final XEmbeddedTable embedded;
      final TableVSAssembly table;
      final FormTableLens lens;
      final ViewsheetSandbox box;
   }
}
