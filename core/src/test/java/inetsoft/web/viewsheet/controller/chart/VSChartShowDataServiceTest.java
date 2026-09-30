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

package inetsoft.web.viewsheet.controller.chart;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.DataSetTable;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.Map;

import static inetsoft.test.XTableUtil.date;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSChartShowDataServiceTest {
   @Test
   public void testSerializeDcFormatTableLens() throws Exception {
      DataSet dataSet = new DefaultDataSet(new Object[][]{
         { "col1", "col2", "col3" },
         { "a", date("2021-01-03"), 3 },
         { "a", date("2021-01-05"), 5 },
         { "b", date("2021-01-10"), 10 },
         { "b", date("2021-01-24"), 24 },
         { "c", date("2021-01-24"), 24 },
         });
      DataSetTable base = new DataSetTable(dataSet);
      final GraphtDataSelector selector = (data, row, fields) -> true;
      DateComparisonFormat dcFormat = new DateComparisonFormat(dataSet, selector, 0,
                                                               DateComparisonInfo.DAY, 0,
                                                               "col3", "col2", null,
                                                               new Object[0], null,
                                                               false, true);

      VSChartShowDataService.DcFormatTableLens originalTable =
         new VSChartShowDataService.DcFormatTableLens(base, dcFormat);
      Map<TableDataPath, TableFormat> formatMap = originalTable.getFormatMap();
      TableDataPath col1Path = new TableDataPath("col1");
      TableFormat format = new TableFormat();
      format.background = Color.BLUE;
      format.foreground = Color.RED;
      formatMap.put(col1Path, format);

      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(VSChartShowDataService.DcFormatTableLens.class,
                              deserializedTable.getClass());

      VSChartShowDataService.DcFormatTableLens deserializedTable2 =
         (VSChartShowDataService.DcFormatTableLens) deserializedTable;
      Map<TableDataPath, TableFormat> deserializedFormatMap = deserializedTable2.getFormatMap();
      Assertions.assertEquals(formatMap, deserializedFormatMap);
   }

   // Bug #77366, a chart sharing another assembly's date comparison must hide the first
   // period using the shared dc, not the stale dc stored on the chart itself.
   @Test
   public void testHideFirstPeriodUsesSharedDateComparison() throws Exception {
      DateComparisonInfo ownDc = mockStdPeriodDc(date("2024-01-01"));
      DateComparisonInfo sharedDc = mockStdPeriodDc(date("2019-01-01"));

      VSChartInfo chartInfo = mock(VSChartInfo.class);
      when(chartInfo.isAppliedDateComparison()).thenReturn(true);

      VSDataRef dcRef = mock(VSDataRef.class);
      when(dcRef.getFullName()).thenReturn("Year(Date)");

      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.getVSChartInfo()).thenReturn(chartInfo);
      when(info.isDateComparisonEnabled()).thenReturn(true);
      when(info.getComparisonShareFrom()).thenReturn("Crosstab1");
      when(info.getDateComparisonInfo()).thenReturn(ownDc);
      when(info.getDateComparisonRef()).thenReturn(dcRef);

      CrosstabVSAssemblyInfo crosstabInfo = mock(CrosstabVSAssemblyInfo.class);
      when(crosstabInfo.getDateComparisonInfo()).thenReturn(sharedDc);
      CrosstabVSAssembly crosstab = mock(CrosstabVSAssembly.class);
      when(crosstab.getVSAssemblyInfo()).thenReturn(crosstabInfo);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Crosstab1")).thenReturn(crosstab);

      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSAssemblyInfo()).thenReturn(info);
      when(chart.getViewsheet()).thenReturn(vs);

      TableLens table = new DefaultTableLens(new Object[][] {
         { "Year(Date)", "Sum(Quantity)" },
         { date("2018-01-01"), 10 },
         { date("2019-01-01"), 20 },
         { date("2020-01-01"), 30 },
         { date("2021-01-01"), 40 },
      });

      VSChartShowDataService service = new VSChartShowDataService(null, null, null, null);
      Method method = VSChartShowDataService.class.getDeclaredMethod(
         "hideFirstPeriod", DataVSAssembly.class, TableLens.class);
      method.setAccessible(true);
      TableLens result = (TableLens) method.invoke(service, chart, table);

      // header + 2019..2021, only the extra 2018 period queried for comparison is hidden
      Assertions.assertEquals(4, result.getRowCount());
      Assertions.assertEquals(date("2019-01-01"), result.getObject(1, 0));
      Assertions.assertEquals(date("2021-01-01"), result.getObject(3, 0));
   }

   private static DateComparisonInfo mockStdPeriodDc(java.util.Date startDate) {
      DateComparisonInfo dc = mock(DateComparisonInfo.class);
      when(dc.isStdPeriod()).thenReturn(true);
      when(dc.isValueOnly()).thenReturn(false);
      when(dc.getStartDate()).thenReturn(startDate);
      return dc;
   }
}
