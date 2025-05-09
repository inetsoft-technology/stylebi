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
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.DataSetTable;
import inetsoft.test.SreeHome;
import inetsoft.test.TestSerializeUtils;
import inetsoft.uql.XTable;
import inetsoft.uql.viewsheet.internal.DateComparisonFormat;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.Map;

import static inetsoft.test.XTableUtil.date;

@SreeHome
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
}
