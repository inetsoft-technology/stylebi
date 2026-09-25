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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.composer.vs.objects.event.ResizeTableColumnEvent;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A composer column resize tops a set last column up to the table's width and saves it. For a
 * table with a card inset that width is the grid inside the inset, or the saved last column
 * outgrows the grid and the table scrolls. Every table is 400px wide with 16px on each side,
 * leaving a 368px grid, and has three columns set to 100px; the first is dragged to 120px.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ComposerVSTableServiceColumnWidthInsetTest {
   @Test
   void resizeLeavesTheLastColumnFillingTheGrid() throws Exception {
      TableVSAssembly table = createTable(false);
      VSTableLens lens = resizeFirstColumn(table);

      assertEquals(100, table.getTableDataVSAssemblyInfo().getColumnWidthValue(2));
      assertArrayEquals(new double[] { 120, 100, 148 },
                        BaseTableService.getColWidths(table, lens));
   }

   // shrink leaves the 300px of columns unfilled, so the resize tops the last one up by 68px
   @Test
   void resizeTopsAShrunkLastColumnUpToTheGrid() throws Exception {
      TableVSAssembly table = createTable(true);
      resizeFirstColumn(table);

      assertEquals(168, table.getTableDataVSAssemblyInfo().getColumnWidthValue(2));
   }

   private static TableVSAssembly createTable(boolean shrink) {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(new Dimension(400, 200));
      info.setPadding(new Insets(16, 16, 16, 16));
      info.setShrinkValue(shrink);

      for(int i = 0; i < 3; i++) {
         info.setColumnWidthValue(i, 100);
      }

      return table;
   }

   private static VSTableLens resizeFirstColumn(TableVSAssembly table) throws Exception {
      VSTableLens lens = new VSTableLens(new DefaultTableLens(new Object[][] {
         { "A", "B", "C" },
         { 1, 2, 3 }
      }));
      lens.initTableGrid(table.getVSAssemblyInfo());

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getVSTableLens("Table1", false)).thenReturn(lens);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(table.getViewsheet());
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(any(), any())).thenReturn(rvs);

      ComposerVSTableService service = new ComposerVSTableService(
         mock(CoreLifecycleService.class), null, null, null, null, viewsheetService, null, null);

      ResizeTableColumnEvent event = new ResizeTableColumnEvent();
      event.setName("Table1");
      event.setRow(0);
      event.setStartCol(0);
      event.setEndCol(1);
      event.setWidths(new double[] { 120 });
      service.changeColumnWidth("vs1", event, null, null, null);

      return lens;
   }
}
