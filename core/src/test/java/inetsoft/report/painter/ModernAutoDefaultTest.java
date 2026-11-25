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

import static org.junit.jupiter.api.Assertions.*;

/**
 * The modern style turns Auto Maximum and Auto Scale on for a presenter whose setting was never
 * set, and leaves one the author set, either way, alone. A value above zero always shows a bar.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernAutoDefaultTest {
   @Test
   void anUnsetBarIsTurnedOnAndAnExplicitOffIsKept() throws Exception {
      BarPresenter unset = (BarPresenter) created(BarPresenter.class, null);
      ModernAutoDefault.apply(unset);
      assertTrue(unset.isAutoMaximum());

      BarPresenter off = (BarPresenter) created(BarPresenter.class, false);
      ModernAutoDefault.apply(off);
      assertFalse(off.isAutoMaximum());

      BarPresenter on = (BarPresenter) created(BarPresenter.class, true);
      ModernAutoDefault.apply(on);
      assertTrue(on.isAutoMaximum());
   }

   @Test
   void theSameHoldsForBar2AndTheBulletGraph() throws Exception {
      Bar2Presenter bar2 = (Bar2Presenter) created(Bar2Presenter.class, null);
      ModernAutoDefault.apply(bar2);
      assertTrue(bar2.isAutoMaximum());

      Bar2Presenter bar2Off = (Bar2Presenter) created(Bar2Presenter.class, false);
      ModernAutoDefault.apply(bar2Off);
      assertFalse(bar2Off.isAutoMaximum());

      BulletGraphPresenter bullet =
         (BulletGraphPresenter) created(BulletGraphPresenter.class, null);
      ModernAutoDefault.apply(bullet);
      assertTrue(bullet.isAutoScale());

      BulletGraphPresenter bulletOff =
         (BulletGraphPresenter) created(BulletGraphPresenter.class, false);
      ModernAutoDefault.apply(bulletOff);
      assertFalse(bulletOff.isAutoScale());
   }

   @Test
   void aConfiguredMaximumIsTheAuthorsChoiceSoItIsNotScaledAway() throws Exception {
      // a saved presenter that sets only the Maximum, as dashboards saved before Auto Maximum did
      PresenterRef ref = new PresenterRef(BarPresenter.class.getName());
      ref.setParameter("maximum", 200.0);
      BarPresenter saved = (BarPresenter) ref.createPresenter();
      ModernAutoDefault.apply(saved);
      assertFalse(saved.isAutoMaximum());
      assertEquals(200.0, saved.getMaximum());

      PresenterRef ref2 = new PresenterRef(Bar2Presenter.class.getName());
      ref2.setParameter("maximum", 200.0);
      Bar2Presenter saved2 = (Bar2Presenter) ref2.createPresenter();
      ModernAutoDefault.apply(saved2);
      assertFalse(saved2.isAutoMaximum());

      // a script that builds one with its maximum
      BarPresenter scripted = new BarPresenter(500, Color.BLUE);
      ModernAutoDefault.apply(scripted);
      assertFalse(scripted.isAutoMaximum());

      Bar2Presenter scripted2 = new Bar2Presenter(500, Color.BLUE, Color.RED);
      ModernAutoDefault.apply(scripted2);
      assertFalse(scripted2.isAutoMaximum());
   }

   @Test
   void anyConfiguredBulletGraphSettingIsTheAuthorsChoice() throws Exception {
      for(String name : new String[] { "maximum", "minimum", "target", "range1", "range2",
                                       "range3" })
      {
         PresenterRef ref = new PresenterRef(BulletGraphPresenter.class.getName());
         ref.setParameter(name, 50.0);
         BulletGraphPresenter saved = (BulletGraphPresenter) ref.createPresenter();
         ModernAutoDefault.apply(saved);
         assertFalse(saved.isAutoScale(), name);
      }

      // colors are not part of the scale
      PresenterRef colors = new PresenterRef(BulletGraphPresenter.class.getName());
      colors.setParameter("color1", Color.RED);
      BulletGraphPresenter styled = (BulletGraphPresenter) colors.createPresenter();
      ModernAutoDefault.apply(styled);
      assertTrue(styled.isAutoScale());
   }

   @Test
   void thePresentersOwnDefaultStaysOff() {
      assertFalse(new BarPresenter().isAutoMaximum());
      assertFalse(new Bar2Presenter().isAutoMaximum());
      assertFalse(new BulletGraphPresenter().isAutoScale());
   }

   @Test
   void otherPresentersAndNullAreLeftAlone() {
      assertDoesNotThrow(() -> ModernAutoDefault.apply(null));
      assertDoesNotThrow(() -> ModernAutoDefault.apply(new TrafficLightPresenter()));
   }

   @Test
   void theCopyForACellKeepsTheChoice() {
      BarPresenter bar = new BarPresenter();
      bar.setAutoMaximum(false);
      BarPresenter copy = (BarPresenter) bar.withMaximumResolver(() -> 50.0);
      ModernAutoDefault.apply(copy);

      assertFalse(copy.isAutoMaximum());
   }

   @Test
   void aBarFarBelowTheMaximumStillShowsOnePixel() {
      BarPresenter bar = new BarPresenter(100, Color.BLUE);

      assertEquals(1, barWidth(bar, 0.2));
      assertEquals(0, barWidth(bar, 0.0));
      assertEquals(50, barWidth(bar, 50));
   }

   @Test
   void aBulletGraphBarFarBelowTheMaximumStillShowsOnePixel() {
      BulletGraphPresenter bullet = new BulletGraphPresenter();
      bullet.setBarColor(Color.RED);
      // away from the left edge, where the target line would cover the bar's one pixel
      bullet.setTarget(50);

      assertEquals(1, bulletBarWidth(bullet, 0.2));
      assertEquals(0, bulletBarWidth(bullet, 0.0));
   }

   private static Presenter created(Class<? extends Presenter> cls, Boolean auto)
      throws Exception
   {
      PresenterRef ref = new PresenterRef(cls.getName());

      if(auto != null) {
         ref.setParameter(BulletGraphPresenter.class.equals(cls) ? "autoScale" : "autoMaximum",
                          auto);
      }

      return ref.createPresenter();
   }

   // filled pixels on the middle line of a 100 by 20 cell
   private static int barWidth(Presenter presenter, double value) {
      BufferedImage image = new BufferedImage(100, 20, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, 100, 20);
      g.setColor(Color.BLACK);
      presenter.paint(g, value, 0, 0, 100, 20);
      g.dispose();
      int cnt = 0;

      for(int x = 0; x < 100; x++) {
         cnt += (image.getRGB(x, 10) & 0xFFFFFF) != 0xFFFFFF ? 1 : 0;
      }

      return cnt;
   }

   // red pixels on the bar line of a 100 by 20 bullet graph, the bands and target being other
   // colors
   private static int bulletBarWidth(BulletGraphPresenter presenter, double value) {
      presenter.setColor1(new Color(200, 200, 201));
      presenter.setColor2(new Color(190, 190, 191));
      presenter.setColor3(new Color(180, 180, 181));
      presenter.setColor4(new Color(170, 170, 171));
      presenter.useModernTarget(new Color(0, 255, 0));
      BufferedImage image = new BufferedImage(100, 20, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, 100, 20);
      g.setColor(Color.BLACK);
      presenter.paint(g, value, 0, 0, 100, 20);
      g.dispose();
      int cnt = 0;

      for(int x = 0; x < 100; x++) {
         cnt += (image.getRGB(x, 10) & 0xFFFFFF) == 0xFF0000 ? 1 : 0;
      }

      return cnt;
   }
}
