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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

public class VSCrosstabBindingScriptableTest {

   private CrosstabVSAScriptable crosstabVSAScriptable;

   private VSCrosstabBindingScriptable vsCrosstabBindingScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs-crosstab-1");

      CrosstabVSAssembly crosstabVSAssembly = new CrosstabVSAssembly();

       CrosstabVSAssemblyInfo crosstabVSAssemblyInfo = (CrosstabVSAssemblyInfo) crosstabVSAssembly.getVSAssemblyInfo();
      crosstabVSAssemblyInfo.setName("crosstab1");

      viewsheet.addAssembly(crosstabVSAssembly);

      ViewsheetSandbox viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs-crosstab-1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      crosstabVSAScriptable = new CrosstabVSAScriptable(viewsheetSandbox);
      crosstabVSAScriptable.setAssembly("crosstab1");

      vsCrosstabBindingScriptable = new VSCrosstabBindingScriptable(crosstabVSAScriptable);
   }

   @Test
   void testBaiscFunctions() {
      vsCrosstabBindingScriptable.init();

      String[] rowFields = new String[]{"row1", "row2"};
      String[] colFields = new String[]{"col1", "col2"};
      String[] measureFields = new String[]{"measure1", "measure2"};

      vsCrosstabBindingScriptable.setRowFields(rowFields);
      vsCrosstabBindingScriptable.setColFields(colFields);
      vsCrosstabBindingScriptable.setMeasureFields(measureFields);
      vsCrosstabBindingScriptable.setFormula("measure1", XConstants.SUM_FORMULA, null);
      vsCrosstabBindingScriptable.setPercentageType("measure1", "16");
      vsCrosstabBindingScriptable.setPercentageMode("2");
      vsCrosstabBindingScriptable.setFormula("measure2", XConstants.AVERAGE_FORMULA, "row2");

      assert vsCrosstabBindingScriptable.getRowFields().equals(List.of("row1", "row2"));
      assert vsCrosstabBindingScriptable.getColFields().equals(List.of("col1", "col2"));
      assert vsCrosstabBindingScriptable.getMeasureFields().equals(List.of("measure1", "measure2"));
      assert vsCrosstabBindingScriptable.getFormula("measure1").equals(XConstants.SUM_FORMULA);
      assert vsCrosstabBindingScriptable.getPercentageMode().equals("2");
      assert vsCrosstabBindingScriptable.getPercentageType("measure1").equals("16");

      vsCrosstabBindingScriptable.setShowRowTotal(true);
      vsCrosstabBindingScriptable.setShowColumnTotal(true);
      assert vsCrosstabBindingScriptable.isShowColumnTotal();
      assert vsCrosstabBindingScriptable.isShowRowTotal();

      vsCrosstabBindingScriptable.setColumnOrder("row1", XConstants.ROW_HEADER, XConstants.SORT_ASC, "Sum(measure1)");
      assert vsCrosstabBindingScriptable.getColumnOrder("row1", XConstants.ROW_HEADER) == XConstants.SORT_ASC;

      vsCrosstabBindingScriptable.setTimeSeries("row1", true);
      assert vsCrosstabBindingScriptable.isTimeSeries("row1");
   }

   @Test
   void testTopNFunctions() {
     crosstabVSAScriptable.setQuery("query2");
     vsCrosstabBindingScriptable.setRowFields(new String[]{"name"});
     vsCrosstabBindingScriptable.setColFields(new String[]{"date1"});
     vsCrosstabBindingScriptable.setMeasureFields(new String[]{"total"});
     vsCrosstabBindingScriptable.setFormula("total", XConstants.SUM_FORMULA, null);
     vsCrosstabBindingScriptable.setShowColumnTotal(true);

     vsCrosstabBindingScriptable.setTopN("name", XConstants.ROW_HEADER, 3);
     vsCrosstabBindingScriptable.setTopNSummaryCol("name", XConstants.ROW_HEADER, "Sum(total)");
     vsCrosstabBindingScriptable.setTopNReverse("name", XConstants.ROW_HEADER, false);
     vsCrosstabBindingScriptable.setGroupOthers("name", XConstants.ROW_HEADER, true);
     vsCrosstabBindingScriptable.setGroupOrder("date", XConstants.COL_HEADER,XConstants.YEAR_DATE_GROUP);
     vsCrosstabBindingScriptable.setGroupTotal("date", XConstants.COL_HEADER, "show");

     assert vsCrosstabBindingScriptable.getTopN("name", XConstants.ROW_HEADER) == 3;
     assert vsCrosstabBindingScriptable.getTopNSummaryCol("name", XConstants.ROW_HEADER).equals("Sum(total)");
     assert !vsCrosstabBindingScriptable.isTopNReverse("name", XConstants.ROW_HEADER);
     assert vsCrosstabBindingScriptable.isGroupOthers("name", XConstants.ROW_HEADER);
     assert vsCrosstabBindingScriptable.getGroupOrder("date", XConstants.COL_HEADER) == XConstants.YEAR_DATE_GROUP;
     assert !vsCrosstabBindingScriptable.getGroupTotal("date", XConstants.COL_HEADER);
   }
}
