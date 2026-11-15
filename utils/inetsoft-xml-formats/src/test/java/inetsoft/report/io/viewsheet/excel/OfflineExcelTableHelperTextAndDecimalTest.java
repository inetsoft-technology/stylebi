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

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.VSFormatTableLens;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.util.Tool;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;

import static inetsoft.report.io.viewsheet.ExportCellFormatFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80208: the table/crosstab Excel writers must show a MessageFormat cell as its display text
 * ("12 pcs") and an unformatted decimal with the digits the dashboard shows (12.25, not 12.3).
 * Runs the real ExportUtil and writer classes and reads the cells back with POI.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OfflineExcelTableHelperTextAndDecimalTest {
   /** Maps the pixel grid of the writer onto sheet cells, one cell per default cell size. */
   private static class GridExporter extends PoiExcelVSExporter {
      GridExporter(ExcelContext context) {
         super(context, new ByteArrayOutputStream());
      }

      @Override
      public Point getRowCol(Point position) {
         return new Point(position.x / 100, position.y / 20);
      }

      @Override
      public Point getRowCol(int x, int y, Point coordinate) {
         return new Point(x / 100, y / 20);
      }

      @Override
      public int getRow(Point position, int offset) {
         return (position.y + offset) / 20;
      }

      @Override
      public int getCol(Point position, int offset) {
         return (position.x + offset) / 100;
      }
   }

   private XSSFWorkbook book;
   private Sheet sheet;
   private VSTableLens lens;
   private GridExporter exporter;
   private TableVSAssembly assembly;

   @BeforeEach
   void setUp() {
      book = new XSSFWorkbook();
      sheet = book.createSheet("s");
      ExcelContext context = Mockito.mock(ExcelContext.class);
      Mockito.when(context.getWorkbook()).thenReturn(book);
      exporter = new GridExporter(context);
      Viewsheet vs = Mockito.mock(Viewsheet.class);
      assembly = Mockito.mock(TableVSAssembly.class);
      Mockito.when(assembly.getViewsheet()).thenReturn(vs);
      Mockito.when(assembly.getVSAssemblyInfo()).thenReturn(new TableVSAssemblyInfo());
      lens = new VSTableLens(new VSFormatTableLens(box("T1"), "T1", new Base(), true));
   }

   @AfterEach
   void tearDown() throws Exception {
      book.close();
   }

   /** Write the data cells of row 1 through the table writer, as the data-cell loop does. */
   private Cell writeTable(int col) {
      OfflineExcelTableHelper helper = new OfflineExcelTableHelper(book, sheet, Mockito.mock(OfflineExcelVSExporter.class), assembly);
      helper.setExporter(exporter);
      String pattern = ExportUtil.getCellFormat(lens, 1, col, false, false);
      Object obj = ExportUtil.getObject(lens, 1, col, pattern, true);
      String text = Tool.toString(obj);
      helper.writeTableCell(0, 0, new Dimension(1, 1), new Rectangle2D.Double(0, 0, 100, 20), 1,
                            col, new VSCompositeFormat(), text == null ? "" : text, obj, null,
                            new VSCompositeFormat(), new Rectangle(0, 0, COLS, 2), pattern,
                            columnWidths(), new Insets(0, 0, 0, 0));
      return sheet.getRow(1).getCell(col);
   }

   /** Same cell through the crosstab/calc table writer. */
   private Cell writeCrosstab(int col) {
      ExcelCrosstabHelper helper = new ExcelCrosstabHelper(book, sheet, assembly);
      helper.setExporter(exporter);
      String pattern = ExportUtil.getCellFormat(lens, 1, col, false, false);
      Object obj = ExportUtil.getObject(lens, 1, col, pattern, true);
      String text = "@".equals(pattern) ? lens.getText(1, col) : Tool.toString(obj);
      helper.writeTableCell(0, 0, new Dimension(1, 1), new Rectangle2D.Double(0, 0, 100, 20), 1,
                            col, new VSCompositeFormat(), text, obj, null,
                            new VSCompositeFormat(), new Rectangle(0, 0, COLS, 2), pattern,
                            columnWidths(), new Insets(0, 0, 0, 0));
      return sheet.getRow(1).getCell(col);
   }

   private static int[] columnWidths() {
      int[] w = new int[COLS];
      java.util.Arrays.fill(w, 100);
      return w;
   }

   private static String shown(Cell cell) {
      return new DataFormatter().formatCellValue(cell);
   }

   /** The UI export writer (OfflineExcelTableHelper) overrides writeTableCell with its own formula. */
   @Test
   void offlineWriterShowsTextFormatAndDecimals() {
      for(int col : new int[] { MSG0, MSG1 }) {
         Cell cell = writeTable(col);
         assertEquals(CellType.STRING, cell.getCellType(), "col " + col);
         assertEquals("12 pcs", shown(cell), "col " + col);
      }

      assertEquals("ID-A1", shown(writeTable(MSG_STR)));
      Cell d1 = writeTable(D1225);
      assertEquals(CellType.NUMERIC, d1.getCellType());
      assertEquals("12.25", shown(d1));
      assertEquals("1234.5678", shown(writeTable(D_1234)));
   }
}
