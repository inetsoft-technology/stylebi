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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The last column fills the grid inside a table's card inset, which is the width the browser
 * clips the grid to, so columns that fit leave no horizontal scroll. Every table is 400px wide;
 * the padded ones carry 16px on each side, leaving a 368px grid.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BaseTableColWidthsInsetTest {
   @Test
   void lastColumnFillsTheGridInsideTheInset() {
      TableVSAssembly table = createTable(new Insets(16, 16, 16, 16), 100, 100, 100);

      assertArrayEquals(new double[] { 100, 100, 168 },
                        BaseTableService.getColWidths(table, createLens(table)));
   }

   @Test
   void lastColumnFillsTheCardWithoutAnInset() {
      TableVSAssembly table = createTable(new Insets(0, 0, 0, 0), 100, 100, 100);

      assertArrayEquals(new double[] { 100, 100, 200 },
                        BaseTableService.getColWidths(table, createLens(table)));
   }

   @Test
   void columnsWiderThanTheCardAreNotFilled() {
      TableVSAssembly table = createTable(new Insets(16, 16, 16, 16), 200, 150, 100);

      assertArrayEquals(new double[] { 200, 150, 100 },
                        BaseTableService.getColWidths(table, createLens(table)));
   }

   // 88px is left inside the inset, under the 100px default, so the last column takes the
   // lens's width, which the lens has filled to the card's 120px remainder
   @Test
   void widthlessLastColumnDoesNotTakeTheLensCardFill() {
      TableVSAssembly table = createTable(new Insets(16, 16, 16, 16), 150, 130, Double.NaN);

      assertArrayEquals(new double[] { 150, 130, 100 },
                        BaseTableService.getColWidths(table, createLens(table)));
   }

   @Test
   void cardWidthsIgnoreTheInset() {
      TableVSAssembly table = createTable(new Insets(16, 16, 16, 16), 100, 100, 100);

      assertArrayEquals(new double[] { 100, 100, 200 },
                        BaseTableService.getColWidths(table, createLens(table), false));
   }

   private static TableVSAssembly createTable(Insets padding, double... widths) {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(new Dimension(400, 200));
      info.setPadding(padding);

      for(int i = 0; i < widths.length; i++) {
         info.setColumnWidthValue(i, widths[i]);
      }

      return table;
   }

   private static VSTableLens createLens(TableVSAssembly table) {
      VSTableLens lens = new VSTableLens(new DefaultTableLens(new Object[][] {
         { "A", "B", "C" },
         { 1, 2, 3 }
      }));
      lens.initTableGrid(table.getVSAssemblyInfo());
      return lens;
   }
}
