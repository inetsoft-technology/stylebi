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
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSTableStructureDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A spreadsheet cannot show the card tint, so a modern table's cells keep the opaque card colour
 * they had before the tint: never the tint's grey and never a blend. A translucent cell fill on
 * a modern table is written as the opaque colour it shows over that card.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExcelCardFillTest {
   @Test
   void aLightModernTableCellIsWhite() {
      assertEquals("FFFFFF", cellFill(VizMark.MODERN_LIGHT, new VSCompositeFormat()));
   }

   @Test
   void aDarkModernTableCellIsTheDarkCard() {
      assertEquals("252428", cellFill(VizMark.MODERN_DARK, new VSCompositeFormat()));
   }

   // Excel drops a fill's alpha, so a translucent stripe is written as the colour it shows

   @Test
   void aLightModernZebraRowIsFlattenedOverTheCard() {
      assertEquals("F2F2F2", cellFill(VizMark.MODERN_LIGHT, zebraCell(VizMark.MODERN_LIGHT)));
   }

   @Test
   void aDarkModernZebraRowIsFlattenedOverTheDarkCard() {
      assertEquals("2C2B2F", cellFill(VizMark.MODERN_DARK, zebraCell(VizMark.MODERN_DARK)));
   }

   @Test
   void aLegacyTranslucentCellFillIsWrittenAsBefore() {
      assertEquals("AAAAAA", cellFill(null, zebraCell(VizMark.MODERN_LIGHT)));
   }

   /** A body row carrying the zebra stripe as the table lens resolves it. */
   private static VSCompositeFormat zebraCell(VizMark mark) {
      Color zebra = VSTableStructureDefaults.zebraBackground(VizContext.of(mark));
      VSCompositeFormat cell = new VSCompositeFormat();
      cell.getUserDefinedFormat().setBackground(zebra);
      cell.getUserDefinedFormat().setAlpha(Math.round(zebra.getAlpha() * 100 / 255f));
      assertEquals(zebra.getAlpha(), cell.getBackground().getAlpha(), "precondition: the stripe");
      return cell;
   }

   // one cell, which falls back to the table's object format when it has no fill
   private static String cellFill(VizMark mark, VSCompositeFormat cell) {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setVizMark(mark);
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.initDefaultFormat();
      vs.addAssembly(table);
      VSCompositeFormat format = table.getVSAssemblyInfo().getFormat();
      assertEquals(mark != null, format.isDefaultCardTint(), "seeded tint on a modern table");

      XSSFWorkbook book = new XSSFWorkbook();
      ExcelTableHelper helper = new ExcelTableHelper(book, book.createSheet(), table);
      PoiExcelVSExporter exporter = new PoiExcelVSExporter(
         Mockito.mock(ExcelContext.class), new ByteArrayOutputStream());
      helper.setExporter(exporter);

      exporter.prepareAssembly(table);
      helper.writeTableCell(0, 0, new Dimension(1, 1), null, 0, 0, cell,
                            "x", "x", null, format, new Rectangle(0, 0, 1, 1), null, null, null);

      XSSFCellStyle style =
         (XSSFCellStyle) book.getSheetAt(0).getRow(0).getCell(0).getCellStyle();
      StringBuilder hex = new StringBuilder();

      for(byte b : style.getFillForegroundXSSFColor().getRGB()) {
         hex.append(String.format("%02X", b & 0xFF));
      }

      return hex.toString();
   }
}
