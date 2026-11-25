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
package inetsoft.report.lens;

import inetsoft.report.painter.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An Auto Maximum bar that ignores outliers scales to the column without them, and draws a value
 * beyond that scale as a full bar with a break near its end.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AttributeTableLensOutlierTest {
   private static final int SALES = 1;
   private static final double HUGE = 99999990000.0;

   @Test
   void theBarScalesToTheColumnWithoutItsOutlier() {
      AttributeTableLens lens = lens(bar(true, true));

      // 10 .. 50 and one huge value: the scale is 50, so 30 is 60% of the cell
      assertEquals(60, filled(lens, 3, SALES)[0]);
      assertEquals(100, filled(lens, 5, SALES)[0]);
   }

   @Test
   void theOutlierIsAFullBarWithABreak() {
      AttributeTableLens lens = lens(bar(true, true));
      int[] huge = filled(lens, 6, SALES);

      // a 100px cell: 93px, a 3px gap, then a 4px stub
      assertEquals(97, huge[0]);
      assertEquals(3, huge[1], "the gap");
      assertTrue(gap(lens, 6, SALES), "pixels 93 to 95 are empty");
   }

   @Test
   void withoutIgnoringOutliersTheBarScalesToTheRealMaximum() {
      AttributeTableLens lens = lens(bar(true, false));

      // 30 of 99999990000 is far below one pixel, so only the 1px minimum shows
      assertEquals(1, filled(lens, 3, SALES)[0]);
      assertEquals(100, filled(lens, 6, SALES)[0]);
   }

   @Test
   void aColumnWithoutOutliersDrawsTheSameEitherWay() {
      AttributeTableLens on = lens(bar(true, true), new double[] { 10, 20, 30, 40, 50, 60 });
      AttributeTableLens off = lens(bar(true, false), new double[] { 10, 20, 30, 40, 50, 60 });

      for(int r = 1; r <= 6; r++) {
         assertEquals(filled(off, r, SALES)[0], filled(on, r, SALES)[0], "row " + r);
      }
   }

   @Test
   void ignoringOutliersDoesNothingWhileAutoMaximumIsOff() {
      AttributeTableLens lens = lens(bar(false, true));

      // the configured maximum of 100 and no break
      assertEquals(30, filled(lens, 3, SALES)[0]);
      assertEquals(100, filled(lens, 6, SALES)[0]);
   }

   @Test
   void theSignedBarScalesOnAbsoluteValuesAndBreaksBothSides() {
      Bar2Presenter bar = new Bar2Presenter();
      bar.setAutoMaximum(true);
      bar.setIgnoreOutliers(true);
      AttributeTableLens lens = lens(bar, new double[] {
         -40, -30, -20, -10, 10, 20, 30, 40, HUGE, -HUGE });

      // |values| up to 40 and two outliers: the scale is 40, a half cell each way
      int[] twenty = halves(lens, 6, SALES);
      assertEquals(25, twenty[1]);

      int[] positive = halves(lens, 9, SALES);
      assertEquals(47, positive[1], "the huge positive value, broken near its end");
      int[] negative = halves(lens, 10, SALES);
      assertEquals(47, negative[0], "the huge negative value, broken near its end");
   }

   @Test
   void theBulletGraphScalesWithoutTheOutlierAndTargetsTheMedian() {
      BulletGraphPresenter bullet = new BulletGraphPresenter();
      bullet.setAutoScale(true);
      bullet.setIgnoreOutliers(true);
      bullet.setBarColor(new Color(255, 0, 0));
      bullet.useModernTarget(new Color(0, 255, 0));
      AttributeTableLens lens = lens(bullet);

      Presenter0 p = new Presenter0((PresenterPainter) lens.getObject(3, SALES));

      // 0 to 50, so 30 is 60% of the cell; the median of 10, 20, 30, 40, 50 and the huge value is 35
      assertEquals(60, p.barEnd(), 1);
      assertEquals(70, p.targetX(), 1);
   }

   @Test
   void theLensGivesTheRealMaximumWhenAsked() {
      AttributeTableLens lens = lens(bar(true, true));

      assertEquals(HUGE, lens.getAutoMaximum(1, SALES, false));
      assertEquals(HUGE, lens.getAutoMaximum(1, SALES, false, false));
      assertEquals(50, lens.getAutoMaximum(1, SALES, false, true));
   }

   private static BarPresenter bar(boolean auto, boolean ignoreOutliers) {
      BarPresenter bar = new BarPresenter();
      bar.setAutoMaximum(auto);
      bar.setIgnoreOutliers(ignoreOutliers);
      return bar;
   }

   private static AttributeTableLens lens(inetsoft.report.Presenter presenter) {
      return lens(presenter, new double[] { 10, 20, 30, 40, 50, HUGE });
   }

   private static AttributeTableLens lens(inetsoft.report.Presenter presenter, double[] values) {
      Object[][] data = new Object[values.length + 1][2];
      data[0] = new Object[] { "Name", "Sales" };

      for(int i = 0; i < values.length; i++) {
         data[i + 1] = new Object[] { "n" + i, values[i] };
      }

      AttributeTableLens lens = new AttributeTableLens(new DefaultTableLens(data));
      lens.setPresenter(SALES, presenter);
      return lens;
   }

   // the cell's pixels of one row, on the middle line of a 100 by 20 cell
   private static boolean[] line(AttributeTableLens lens, int r, int c) {
      BufferedImage image = new BufferedImage(100, 20, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, 100, 20);
      g.setColor(Color.BLACK);
      ((PresenterPainter) lens.getObject(r, c)).paint(g, 0, 0, 100, 20);
      g.dispose();
      boolean[] filled = new boolean[100];

      for(int x = 0; x < 100; x++) {
         filled[x] = (image.getRGB(x, 10) & 0xFFFFFF) != 0xFFFFFF;
      }

      return filled;
   }

   // {filled pixels, unfilled pixels between the first and the last filled one}
   private static int[] filled(AttributeTableLens lens, int r, int c) {
      boolean[] line = line(lens, r, c);
      int first = -1;
      int last = -1;
      int count = 0;

      for(int x = 0; x < line.length; x++) {
         if(line[x]) {
            count++;
            last = x;

            if(first < 0) {
               first = x;
            }
         }
      }

      return new int[] { count, first < 0 ? 0 : last - first + 1 - count };
   }

   private static boolean gap(AttributeTableLens lens, int r, int c) {
      boolean[] line = line(lens, r, c);
      return !line[93] && !line[94] && !line[95] && line[92] && line[96];
   }

   // {filled pixels left of the centre, filled pixels right of it}
   private static int[] halves(AttributeTableLens lens, int r, int c) {
      boolean[] line = line(lens, r, c);
      int[] halves = new int[2];

      for(int x = 0; x < line.length; x++) {
         if(line[x]) {
            halves[x < 50 ? 0 : 1]++;
         }
      }

      return halves;
   }

   private static final class Presenter0 {
      Presenter0(PresenterPainter painter) {
         this.painter = painter;
         BufferedImage image = new BufferedImage(100, 20, BufferedImage.TYPE_INT_RGB);
         Graphics2D g = image.createGraphics();
         g.setColor(Color.WHITE);
         g.fillRect(0, 0, 100, 20);
         g.setColor(Color.BLACK);
         painter.paint(g, 0, 0, 100, 20);
         g.dispose();
         this.image = image;
      }

      // the x one past the last red bar pixel on the bar's line
      int barEnd() {
         int end = 0;

         for(int x = 0; x < 100; x++) {
            if((image.getRGB(x, 10) & 0xFFFFFF) == 0xFF0000) {
               end = x + 1;
            }
         }

         return end;
      }

      int targetX() {
         for(int x = 0; x < 100; x++) {
            if((image.getRGB(x, 5) & 0xFFFFFF) == 0x00FF00) {
               return x;
            }
         }

         return -1;
      }

      private final PresenterPainter painter;
      private final BufferedImage image;
   }
}
