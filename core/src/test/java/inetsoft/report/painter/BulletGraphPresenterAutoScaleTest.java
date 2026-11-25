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
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyDescriptor;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82275: a Bullet Graph can work its scale out from the column's data, so its default
 * settings are usable for a column that does not run from 0 to 100.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BulletGraphPresenterAutoScaleTest {
   private static final Color BAR = new Color(255, 0, 0);
   private static final Color TARGET = new Color(0, 255, 0);
   private static final Color[] BANDS = {
      new Color(200, 200, 201), new Color(190, 190, 191),
      new Color(180, 180, 181), new Color(170, 170, 171) };

   @Test
   void autoScaleIsOffByDefaultAndIsADialogProperty() throws Exception {
      assertFalse(new BulletGraphPresenter().isAutoScale());

      PropertyDescriptor[] descs =
         new PresenterRef(BulletGraphPresenter.class.getName()).getPropertyDescriptors();
      assertTrue(Arrays.stream(descs).anyMatch(d -> "autoScale".equals(d.getName())));

      PresenterRef ref = new PresenterRef(BulletGraphPresenter.class.getName());
      ref.setParameter("autoScale", "true");
      assertTrue(((BulletGraphPresenter) ref.createPresenter()).isAutoScale());
   }

   @Test
   void theConfiguredScaleIsUsedWhileAutoScaleIsOff() {
      // the defaults: 0 to 100, target 0
      Presenter p = bullet(false).withStatsResolver(() -> new ColumnStats(10, 200, 71.25));

      assertEquals(50, barEnd(p, 50), 1);
      assertEquals(100, barEnd(p, 200), 1);
      assertEquals(0, targetX(p), 1);
   }

   @Test
   void theScaleFollowsTheColumnsData() {
      Presenter p = bullet(true).withStatsResolver(() -> new ColumnStats(10, 200, 71.25));

      // 0 to 200: 50 is a quarter of the cell and the target is the average, 71.25
      assertEquals(25, barEnd(p, 50), 1);
      assertEquals(100, barEnd(p, 200), 1);
      assertEquals(35, targetX(p), 1);
   }

   @Test
   void theRangesSplitTheSpanInFour() {
      Presenter p = bullet(true).withStatsResolver(() -> new ColumnStats(10, 200, 71.25));
      BufferedImage image = paint(p, 0.0);

      assertEquals(BANDS[0].getRGB(), image.getRGB(10, 3));
      assertEquals(BANDS[1].getRGB(), image.getRGB(30, 3));
      assertEquals(BANDS[2].getRGB(), image.getRGB(60, 3));
      assertEquals(BANDS[3].getRGB(), image.getRGB(90, 3));
   }

   @Test
   void aColumnWithNegativeValuesStartsAtItsSmallest() {
      Presenter p = bullet(true).withStatsResolver(() -> new ColumnStats(-40, 20, -8.3));

      // -40 to 20: a value of -5 is 35 of the 60 wide span
      assertEquals(58, barEnd(p, -5.0), 1);
      assertEquals(0, barEnd(p, -40.0), 1);
   }

   @Test
   void aColumnOfNegativesEndsAtZero() {
      Presenter p = bullet(true).withStatsResolver(() -> new ColumnStats(-50, -10, -30));

      // -50 to 0
      assertEquals(80, barEnd(p, -10.0), 1);
   }

   @Test
   void theConfiguredScaleIsUsedWhenTheColumnHasNoUsableNumbers() {
      assertEquals(50, barEnd(bullet(true).withStatsResolver(() -> null), 50), 1);
      assertEquals(50, barEnd(bullet(true).withStatsResolver(
         () -> new ColumnStats(Double.NaN, Double.NaN, Double.NaN)), 50), 1);
      // every value is zero: no span to draw
      assertEquals(50, barEnd(bullet(true).withStatsResolver(
         () -> new ColumnStats(0, 0, 0)), 50), 1);
   }

   @Test
   void theTargetStaysInsideTheScale() {
      Presenter p = bullet(true).withStatsResolver(() -> new ColumnStats(0, 100, 500));

      assertEquals(99, targetX(p), 1);
   }

   @Test
   void theCopyKeepsEveryOtherSetting() {
      BulletGraphPresenter bullet = bullet(true);
      bullet.setPreferredSize(new Dimension(70, 10));
      bullet.setLabelVisible(true);
      BulletGraphPresenter copy =
         (BulletGraphPresenter) bullet.withStatsResolver(() -> null);

      assertEquals(bullet.getPreferredSize(null), copy.getPreferredSize(null));
      assertTrue(copy.isLabelVisible());
      assertEquals(bullet.getBarColor(), copy.getBarColor());
      assertEquals(bullet.modernTarget(), copy.modernTarget());
      assertTrue(copy.isAutoScale());
   }

   @Test
   void aTableLensScalesToTheColumn() {
      Object[][] data = {
         { "Name", "Sales" }, { "a", 10.0 }, { "b", 50.0 }, { "c", 200.0 },
         { "d", 25.0 }, { "e", Double.NaN }, { "f", "text" } };
      AttributeTableLens lens = new AttributeTableLens(new DefaultTableLens(data));
      lens.setPresenter(1, bullet(true));

      Presenter p = ((PresenterPainter) lens.getObject(2, 1)).getPresenter();
      Presenter q = ((PresenterPainter) lens.getObject(3, 1)).getPresenter();

      // 0 to 200, average of 10, 50, 200 and 25
      assertEquals(25, barEnd(p, 50), 1);
      assertEquals(35, targetX(p), 1);
      assertEquals(100, barEnd(q, 200), 1);
   }

   private static BulletGraphPresenter bullet(boolean auto) {
      BulletGraphPresenter bullet = new BulletGraphPresenter();
      bullet.setAutoScale(auto);
      bullet.setBarColor(BAR);
      bullet.setColor1(BANDS[0]);
      bullet.setColor2(BANDS[1]);
      bullet.setColor3(BANDS[2]);
      bullet.setColor4(BANDS[3]);
      bullet.useModernTarget(TARGET);
      return bullet;
   }

   // a 100 by 20 cell, no labels: the bands fill rows 2 to 17, the bar rows 6 to 13
   private static BufferedImage paint(Presenter p, Object value) {
      BufferedImage image = new BufferedImage(100, 20, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, 100, 20);
      g.setColor(Color.BLACK);
      p.paint(g, value, 0, 0, 100, 20);
      g.dispose();
      return image;
   }

   // the x one past the last bar pixel, the target line being ignored
   private static int barEnd(Presenter p, Object value) {
      BufferedImage image = paint(p, value);
      int end = 0;

      for(int x = 0; x < 100; x++) {
         if((image.getRGB(x, 10) & 0xFFFFFF) == (BAR.getRGB() & 0xFFFFFF)) {
            end = x + 1;
         }
      }

      return end;
   }

   private static int targetX(Presenter p) {
      BufferedImage image = paint(p, 0.0);

      for(int x = 0; x < 100; x++) {
         if((image.getRGB(x, 5) & 0xFFFFFF) == (TARGET.getRGB() & 0xFFFFFF)) {
            return x;
         }
      }

      return -1;
   }
}
