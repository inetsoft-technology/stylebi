/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.internal.table;

import inetsoft.report.BaseLayout;
import inetsoft.report.CellBinding;
import inetsoft.report.TableCellBinding;
import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLayout;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.XTable;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RuntimeCalcTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      RuntimeCalcTableLens originalTable = new RuntimeCalcTableLens(
         new CalcTableLens(XTableUtil.getDefaultTableLens()));
      RuntimeCalcTableLens.IndexMap colMap = new RuntimeCalcTableLens.IndexMap(originalTable.getColCount());
      originalTable.setColMap(colMap);
      CalcCellContext calcCellContext = new CalcCellContext();
      originalTable.addCalcColumn(0, calcCellContext);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(RuntimeCalcTableLens.class, deserializedTable.getClass());
   }

   /**
    * Bug: a GROUP cell bound to a numeric column (with the date option left at its
    * unconditional default of DAY_DATE_GROUP) must not have a date format auto-applied.
    * Doing so makes createXFormatInfo() report a "yyyy-MM-dd" date format for the cell,
    * and DateFormat.format(Object) treats the numeric value as epoch-millis, rendering
    * e.g. EMPLOYEE_ID=5 as a date near 1970-01-01.
    */
   @Test
   public void testNoAutoDateFormatForNumericGroupColumn() {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "EMPLOYEE_ID" },
         { 5 }
      });

      TableLayout layout = new TableLayout();
      layout.setColCount(1);
      TableDataDescriptor desc = data.getDescriptor();
      TableDataPath rpath = desc.getRowDataPath(0);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(1);
      layout.addRegion(rpath, region);

      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "EMPLOYEE_ID");
      cell.setBType(TableCellBinding.GROUP);
      // a freshly-created OrderInfo defaults its date option to DAY_DATE_GROUP
      // (OrderInfo's constructor), matching how a real GROUP cell's binding
      // ends up with an unset-but-non-null OrderInfo.
      cell.setOrderInfo(new OrderInfo());
      layout.setCellBinding(0, 0, cell);

      CalcTableVSAssembly assembly = new CalcTableVSAssembly();
      assembly.setTableLayout(layout);
      assembly.setScriptTable(data);

      CalcTableLens calc = new CalcTableLens(data);
      calc.setElement(assembly);

      RuntimeCalcTableLens runtime = new RuntimeCalcTableLens(calc);

      XFormatInfo finfo = runtime.createXFormatInfo(0, 0);
      Assertions.assertNull(finfo);
   }
}
