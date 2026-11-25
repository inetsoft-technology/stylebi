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
package inetsoft.report.painter;

import inetsoft.report.Presenter;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyDescriptor;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82043: the Bar and Bar +/- presenters can scale to the column's data maximum.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BarPresenterAutoMaximumTest {
   @Test
   void theMaximumResolverCopyKeepsTheModernBarThickness() {
      BarPresenter bar = new BarPresenter(100, Color.BLUE);
      bar.setAutoMaximum(true);
      bar.useModernThickness(8);

      BarPresenter copy = (BarPresenter) bar.withMaximumResolver(() -> 50.0);

      assertEquals(8, copy.modernThickness());
   }

   @Test
   void aModernBarWithAutoMaximumIsDrawnAtTheModernThicknessNotTheFullHeight() {
      BarPresenter bar = new BarPresenter(100, Color.BLUE);
      bar.setAutoMaximum(true);
      bar.useModernThickness(8);
      BarPresenter copy = (BarPresenter) bar.withMaximumResolver(() -> 50.0);

      // a 40 by 20 cell: a bar of the modern thickness is 8 high, centered, so the cell's top and
      // bottom rows are clear and its middle row is drawn
      BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      copy.paint(g, 50.0, 0, 0, 40, 20);
      g.dispose();

      assertEquals(0, image.getRGB(10, 3) >>> 24, "above the bar");
      assertEquals(0, image.getRGB(10, 16) >>> 24, "below the bar");
      assertNotEquals(0, image.getRGB(10, 10) >>> 24, "the bar");
   }

   @Test
   void barDefaultOffDrawsAgainstConfiguredMaximum() {
      BarPresenter bar = new BarPresenter();
      assertFalse(bar.isAutoMaximum());

      // a resolver is ignored while auto maximum is off
      Presenter copy = bar.withMaximumResolver(() -> 1000);
      assertEquals(50, barWidth(copy, 50));
      assertEquals(5, barWidth(copy, 5));
      assertEquals(100, barWidth(copy, 250));
      assertEquals(50, barWidth(bar, 50));
   }

   @Test
   void barAutoMaximumScalesToResolvedMaximum() {
      BarPresenter bar = new BarPresenter();
      bar.setAutoMaximum(true);
      Presenter copy = bar.withMaximumResolver(() -> 5000);

      assertEquals(100, barWidth(copy, 5000));
      assertEquals(50, barWidth(copy, 2500));
      assertEquals(10, barWidth(copy, 500));
      // the configured maximum is still what the bean reports (and what the dialog saves)
      assertEquals(100, ((BarPresenter) copy).getMaximum());
   }

   @Test
   void barAutoMaximumFallsBackWithoutDataMaximum() {
      BarPresenter bar = new BarPresenter();
      bar.setAutoMaximum(true);

      // no resolver (e.g. a text assembly), no number in the column, or no positive number
      assertEquals(50, barWidth(bar, 50));
      assertEquals(50, barWidth(bar.withMaximumResolver(() -> Double.NaN), 50));
      assertEquals(50, barWidth(bar.withMaximumResolver(() -> 0), 50));
      assertEquals(50, barWidth(bar.withMaximumResolver(() -> -10), 50));
   }

   @Test
   void bar2AutoMaximumUsesAbsoluteMaximumForBothSides() {
      Bar2Presenter bar = new Bar2Presenter();
      assertTrue(bar.absoluteMaximum());
      assertFalse(new BarPresenter().absoluteMaximum());

      // default off: half cell per side for the configured maximum 100
      assertArrayEquals(new int[] { 0, 25 }, bar2Widths(bar, 50));

      bar.setAutoMaximum(true);
      Presenter copy = bar.withMaximumResolver(() -> 400);
      assertArrayEquals(new int[] { 0, 50 }, bar2Widths(copy, 400));
      assertArrayEquals(new int[] { 50, 0 }, bar2Widths(copy, -400));
      assertArrayEquals(new int[] { 25, 0 }, bar2Widths(copy, -200));
      assertArrayEquals(new int[] { 0, 13 }, bar2Widths(copy, 100));
      assertEquals(100, ((Bar2Presenter) copy).getMaximum());
   }

   @Test
   void withMaximumResolverCopiesAndLeavesOriginalUnchanged() {
      BarPresenter bar = new BarPresenter(200, Color.RED);
      bar.setAutoMaximum(true);
      bar.setPreferredSize(new Dimension(70, 14));
      bar.setBackground(Color.YELLOW);
      Presenter copy = bar.withMaximumResolver(() -> 1000);

      assertNotSame(bar, copy);
      assertEquals(bar, copy);
      assertEquals(bar.hashCode(), copy.hashCode());
      assertEquals(Color.YELLOW, ((BarPresenter) copy).getBackground());
      // the original has no resolver, so it still draws against its configured maximum
      assertEquals(50, barWidth(bar, 100));
      assertEquals(10, barWidth(copy, 100));

      Bar2Presenter bar2 = new Bar2Presenter(200, Color.RED, Color.BLUE);
      bar2.setAutoMaximum(true);
      Presenter copy2 = bar2.withMaximumResolver(() -> 1000);
      assertNotSame(bar2, copy2);
      assertEquals(bar2, copy2);
      assertEquals(Color.BLUE, ((Bar2Presenter) copy2).getNegativeColor());
   }

   @Test
   void equalsAndHashCodeIncludeAutoMaximum() {
      BarPresenter on = new BarPresenter();
      on.setAutoMaximum(true);
      assertNotEquals(new BarPresenter(), on);
      assertNotEquals(new BarPresenter().hashCode(), on.hashCode());
      assertEquals(new BarPresenter(), new BarPresenter());

      Bar2Presenter on2 = new Bar2Presenter();
      on2.setAutoMaximum(true);
      assertNotEquals(new Bar2Presenter(), on2);
      assertNotEquals(new Bar2Presenter().hashCode(), on2.hashCode());
      assertEquals(new Bar2Presenter(), new Bar2Presenter());
   }

   @Test
   void propertyDialogListsTheAutoMaximumProperties() throws Exception {
      assertEquals(Set.of("autoMaximum", "ignoreOutliers", "color", "maximum"),
                   propertyNames(BarPresenter.class));
      assertEquals(Set.of("autoMaximum", "ignoreOutliers", "color", "maximum", "negativeColor"),
                   propertyNames(Bar2Presenter.class));
   }

   @Test
   void presenterParameterTurnsAutoMaximumOn() throws Exception {
      PresenterRef ref = new PresenterRef(BarPresenter.class.getName());
      assertFalse(((BarPresenter) ref.createPresenter()).isAutoMaximum());

      ref.setParameter("autoMaximum", "true");
      assertTrue(((BarPresenter) ref.createPresenter()).isAutoMaximum());

      PresenterRef ref2 = new PresenterRef(Bar2Presenter.class.getName());
      ref2.setParameter("autoMaximum", Boolean.TRUE);
      assertTrue(((Bar2Presenter) ref2.createPresenter()).isAutoMaximum());
   }

   private static Set<String> propertyNames(Class<?> cls) throws Exception {
      PropertyDescriptor[] descs = new PresenterRef(cls.getName()).getPropertyDescriptors();
      return Arrays.stream(descs).map(PropertyDescriptor::getName).collect(Collectors.toSet());
   }

   /**
    * Paint into a 100 x 20 cell and count the filled pixels on the middle line.
    */
   static int barWidth(Presenter presenter, double value) {
      boolean[] filled = paintLine(presenter, value);
      int cnt = 0;

      for(boolean f : filled) {
         cnt += f ? 1 : 0;
      }

      return cnt;
   }

   /**
    * Paint into a 100 x 20 cell and count the filled pixels left and right of the centre.
    */
   static int[] bar2Widths(Presenter presenter, double value) {
      boolean[] filled = paintLine(presenter, value);
      int left = 0;
      int right = 0;

      for(int x = 0; x < filled.length; x++) {
         if(filled[x]) {
            if(x < filled.length / 2) {
               left++;
            }
            else {
               right++;
            }
         }
      }

      return new int[] { left, right };
   }

   static boolean[] paintLine(Presenter presenter, double value) {
      BufferedImage img = new BufferedImage(100, 20, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();

      try {
         g.setColor(Color.WHITE);
         g.fillRect(0, 0, 100, 20);
         g.setColor(Color.BLACK);
         presenter.paint(g, value, 0, 0, 100, 20);
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
