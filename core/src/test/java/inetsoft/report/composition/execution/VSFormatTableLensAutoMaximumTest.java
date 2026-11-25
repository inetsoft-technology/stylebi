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

import inetsoft.report.*;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.painter.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82043: a Bar / Bar +/- presenter set through the table format (the presenter dialog),
 * read through a real VSFormatTableLens, scales to the column data when Auto Maximum is on and
 * keeps the configured maximum when it is off.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSFormatTableLensAutoMaximumTest {
   private static final int SALES = 1;
   private static final int LOSS = 2;

   @Test
   void formatPresenterDefaultOffKeepsConfiguredMaximum() {
      VSFormatTableLens lens = formatLens(BarPresenter.class, SALES, false);

      PresenterPainter painter = (PresenterPainter) lens.getObject(1, SALES);
      assertFalse(((BarPresenter) painter.getPresenter()).isAutoMaximum());
      assertEquals(0, width(lens, 1, SALES));   // 0 of max 100
      assertEquals(100, width(lens, 2, SALES)); // 1000 fills the cell
      assertEquals(100, width(lens, 6, SALES)); // 5000 fills the cell
   }

   @Test
   void formatPresenterAutoMaximumScalesToColumn() {
      VSFormatTableLens lens = formatLens(BarPresenter.class, SALES, true);

      // max of the Sales column is 5000
      assertEquals(0, width(lens, 1, SALES));
      assertEquals(20, width(lens, 2, SALES));
      assertEquals(40, width(lens, 3, SALES));
      assertEquals(60, width(lens, 4, SALES));
      assertEquals(80, width(lens, 5, SALES));
      assertEquals(100, width(lens, 6, SALES));

      // the presenter of the format keeps its configured maximum (nothing leaks into the
      // dialog model, which reads the presenter's getters)
      PresenterPainter painter = (PresenterPainter) lens.getObject(3, SALES);
      assertEquals(100, ((BarPresenter) painter.getPresenter()).getMaximum());
   }

   @Test
   void formatBar2AutoMaximumOnNegativeOnlyColumn() {
      VSFormatTableLens lens = formatLens(Bar2Presenter.class, LOSS, true);

      // all values are <= 0; the largest |value| is 2000, drawn to the left of the centre
      assertArrayEquals(new int[] { 0, 0 }, widths2(lens, 1, LOSS));  // 0
      assertArrayEquals(new int[] { 13, 0 }, widths2(lens, 2, LOSS)); // -500
      assertArrayEquals(new int[] { 25, 0 }, widths2(lens, 3, LOSS)); // -1000
      assertArrayEquals(new int[] { 50, 0 }, widths2(lens, 6, LOSS)); // -2000
   }

   @Test
   void formatBar2DefaultOffOnNegativeOnlyColumn() {
      VSFormatTableLens lens = formatLens(Bar2Presenter.class, LOSS, false);

      // every value is beyond the configured maximum of 100, so each fills its half
      assertArrayEquals(new int[] { 50, 0 }, widths2(lens, 2, LOSS));
      assertArrayEquals(new int[] { 50, 0 }, widths2(lens, 6, LOSS));
   }

   @Test
   void barAutoMaximumOnNegativeOnlyColumnFallsBackToConfiguredMaximum() {
      VSFormatTableLens lens = formatLens(BarPresenter.class, LOSS, true);

      // no positive value: Bar keeps the configured maximum and draws no bar for negatives. The
      // cell shows the value's text at its right edge instead, so only the bar's half is checked
      // for a negative value, and the zero draws nothing at all.
      assertEquals(0, width(lens, 1, LOSS), "row 1, the zero");

      for(int r = 2; r <= 6; r++) {
         boolean[] filled = paintLine((Painter) lens.getObject(r, LOSS));

         for(int x = 0; x < 50; x++) {
            assertFalse(filled[x], "row " + r + ", x " + x);
         }

         assertTrue(width(lens, r, LOSS) > 0, "row " + r + " shows the value's text");
      }
   }

   @Test
   void aModernTableScalesAnUnsetBarToTheColumn() {
      VSFormatTableLens lens = formatLens(BarPresenter.class, SALES, null, VizMark.MODERN_LIGHT);

      assertEquals(20, width(lens, 2, SALES)); // 1000 of 5000, not 1000 of the default 100
      assertEquals(100, width(lens, 6, SALES));
   }

   @Test
   void aModernTableKeepsABarTheAuthorTurnedOff() {
      VSFormatTableLens lens = formatLens(BarPresenter.class, SALES, false, VizMark.MODERN_LIGHT);

      assertEquals(100, width(lens, 2, SALES)); // the configured maximum of 100
      assertEquals(100, width(lens, 3, SALES));
   }

   @Test
   void aModernTableScalesAnUnsetBar2AndBulletGraph() {
      VSFormatTableLens bar2 = formatLens(Bar2Presenter.class, LOSS, null, VizMark.MODERN_LIGHT);
      assertArrayEquals(new int[] { 25, 0 }, widths2(bar2, 3, LOSS)); // -1000 of 2000

      VSFormatTableLens bullet =
         formatLens(BulletGraphPresenter.class, SALES, null, VizMark.MODERN_LIGHT);
      PresenterPainter painter = (PresenterPainter) bullet.getObject(3, SALES);
      assertTrue(((BulletGraphPresenter) painter.getPresenter()).isAutoScale());
   }

   @Test
   void aClassicTableKeepsTheConfiguredMaximumWhenNothingIsSet() {
      VSFormatTableLens lens = formatLens(BarPresenter.class, SALES, null, null);

      assertEquals(100, width(lens, 2, SALES));
      PresenterPainter painter = (PresenterPainter) lens.getObject(2, SALES);
      assertFalse(((BarPresenter) painter.getPresenter()).isAutoMaximum());
   }

   private static VSFormatTableLens formatLens(Class<? extends Presenter> cls, int col,
                                               boolean auto)
   {
      return formatLens(cls, col, auto ? Boolean.TRUE : null, null);
   }

   // auto is null when the author never set Auto Maximum or Auto Scale
   private static VSFormatTableLens formatLens(Class<? extends Presenter> cls, int col,
                                               Boolean auto, VizMark mark)
   {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "Name", "Sales", "Loss" },
         { "a", 0.0, 0.0 },
         { "b", 1000.0, -500.0 },
         { "c", 2000.0, -1000.0 },
         { "d", 3000.0, -1500.0 },
         { "e", 4000.0, -1800.0 },
         { "f", 5000.0, -2000.0 },
      });

      Viewsheet vs = new Viewsheet();
      TableVSAssembly assembly = new TableVSAssembly(vs, "T1");
      assembly.getVSAssemblyInfo().setVizMark(mark);

      PresenterRef ref = new PresenterRef(cls.getName());

      if(auto != null) {
         ref.setParameter(BulletGraphPresenter.class.equals(cls) ? "autoScale" : "autoMaximum",
                          auto);
      }

      VSCompositeFormat vfmt = new VSCompositeFormat();
      vfmt.getUserDefinedFormat().setPresenterValue(ref);
      TableDataPath path = data.getDescriptor().getCellDataPath(1, col);
      assertEquals(TableDataPath.DETAIL, path.getType());
      assembly.getFormatInfo().setFormat(path, vfmt);
      vs.addAssembly(assembly);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "Bar82043", null);
      ViewsheetSandbox box = new ViewsheetSandbox(
         vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      // tfmt = true as DataVSAQuery passes for a table (!chart)
      return new VSFormatTableLens(box, "T1", data, true);
   }

   private static int width(TableLens lens, int r, int c) {
      Object obj = lens.getObject(r, c);
      assertInstanceOf(PresenterPainter.class, obj, "cell " + r + "," + c);
      int cnt = 0;

      for(boolean f : paintLine((Painter) obj)) {
         cnt += f ? 1 : 0;
      }

      return cnt;
   }

   private static int[] widths2(TableLens lens, int r, int c) {
      Object obj = lens.getObject(r, c);
      assertInstanceOf(PresenterPainter.class, obj, "cell " + r + "," + c);
      boolean[] filled = paintLine((Painter) obj);
      int[] widths = new int[2];

      for(int x = 0; x < filled.length; x++) {
         if(filled[x]) {
            widths[x < filled.length / 2 ? 0 : 1]++;
         }
      }

      return widths;
   }

   private static boolean[] paintLine(Painter painter) {
      BufferedImage img = new BufferedImage(100, 20, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();

      try {
         g.setColor(Color.WHITE);
         g.fillRect(0, 0, 100, 20);
         g.setColor(Color.BLACK);
         painter.paint(g, 0, 0, 100, 20);
      }
      finally {
         g.dispose();
      }

      boolean[] filled = new boolean[100];

      for(int x = 0; x < 100; x++) {
         filled[x] = (img.getRGB(x, 10) & 0xFFFFFF) != 0xFFFFFF;
      }

      return filled;
   }
}
