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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #79603: Excel export (Match Layout) of a standalone selection list dropped rows the box
 * holds on the 20px grid and wrote "More..." instead. The fit test used floorY(height) alone,
 * which loses up to 19px of the box's real grid extent floorY(y + height) - floorY(y).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExcelSelectionListFitTest {
   private static final String[] REGIONS = { "Midwest", "Northeast", "South", "West" };

   /** Census Region: y=153, h=88, cell 31. The box spans grid 140..240, so both rows fit. */
   @Test
   void censusRegionWritesAllValues() throws Exception {
      assertEquals(List.of(REGIONS), write(153, 88, 31, REGIONS));
   }

   /** Control: a list on the grid (y=140, h=100) always fitted, and must still. */
   @Test
   void gridAlignedBoxWritesAllValues() throws Exception {
      assertEquals(List.of(REGIONS), write(140, 100, 31, REGIONS));
   }

   /**
    * y=160, h=88: grid extent is only 160..240 (80px), minus the 20px title leaves 60 for two
    * 40px rows. Writing both would overlap the next assembly, so "More..." is still correct.
    */
   @Test
   void boxWithoutSpareGridExtentStillTruncates() throws Exception {
      assertEquals(List.of("Midwest", "More..."), write(160, 88, 31, REGIONS));
   }

   /** A list that really has more values than rows keeps "More...". */
   @Test
   void trulyTruncatedListKeepsMore() throws Exception {
      String[] states = new String[50];

      for(int i = 0; i < states.length; i++) {
         states[i] = "S" + i;
      }

      List<String> labels = write(250, 176, 29, states);
      assertEquals("More...", labels.get(labels.size() - 1));
      assertEquals(true, labels.size() < states.length);
   }

   private List<String> write(int y, int h, int cellHeight, String[] names) throws Exception {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly assembly = new SelectionListVSAssembly(vs, "Region");
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
      vs.addAssembly(assembly);
      info.setPixelOffset(new Point(0, y));
      info.setPixelSize(new Dimension(344, h));
      info.setColumnCount(2);
      info.setCellHeight(cellHeight);
      info.setTitleVisible(true);
      info.setTitleHeight(18);
      SelectionList list = new SelectionList();

      for(String name : names) {
         SelectionValue value = new SelectionValue(name, name);
         value.setFormat(new VSCompositeFormat());
         list.addSelectionValue(value);
      }

      info.setSelectionList(list);

      Workbook book = new XSSFWorkbook();
      Sheet sheet = book.createSheet("s");
      PoiExcelVSExporter exporter = Mockito.mock(PoiExcelVSExporter.class);
      Mockito.when(exporter.getAnchorPosition(Mockito.any()))
         .thenReturn(new XSSFClientAnchor(0, 0, 0, 0, 0, y / 20, 4, (y + h) / 20));
      Mockito.when(exporter.getBounds(Mockito.any(Point.class), Mockito.any(Point.class)))
         .thenReturn(new Rectangle(0, 0, 80, 20));
      Mockito.when(exporter.getRowCol(Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Point.class)))
         .thenAnswer(inv -> new Point((int) inv.getArgument(0) / 80,
                                      (int) inv.getArgument(1) / 20));
      ExcelSelectionListHelper helper = new ExcelSelectionListHelper(book);
      helper.setExporter(exporter);

      Method m = ExcelSelectionListHelper.class.getDeclaredMethod(
         "writeList", SelectionListVSAssembly.class, Sheet.class, List.class);
      m.setAccessible(true);
      List<SelectionValue> values = new ArrayList<>();

      for(SelectionValue v : list.getSelectionValues()) {
         values.add(v);
      }

      m.invoke(helper, assembly, sheet, values);
      List<String> labels = new ArrayList<>();

      for(Row row : sheet) {
         for(Cell cell : row) {
            if(!cell.getStringCellValue().isEmpty()) {
               labels.add(cell.getStringCellValue());
            }
         }
      }

      return labels;
   }
}
