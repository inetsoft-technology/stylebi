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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.gui.viewsheet.cylinder.VSCylinder;
import inetsoft.report.gui.viewsheet.slidingscale.VSSlidingScale;
import inetsoft.report.gui.viewsheet.thermometer.VSHorizontalThermometer;
import inetsoft.report.gui.viewsheet.thermometer.VSVerticalThermometer;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Bug #78270 (P2): a trailing blank range boundary ({@code ["60","90","",""]}, runtime NaN) must
 * paint the last band up to the max like the explicit-max control {@code ["60","90","100"]}, in
 * every renderer that draws range bands (the gauge was fixed by #5025). Before the fix the NaN
 * was cast to int 0 and drew a wedge to the image origin (a 2px sliver in the sliding scale).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RangeBandTrailingBlankBoundaryTest {
   private static final Color[] COLORS = { Color.RED, Color.YELLOW, Color.GREEN };

   @Test
   void blankRangeEntriesReadBackAsNaN() {
      CylinderVSAssemblyInfo info = new CylinderVSAssemblyInfo();
      info.setRanges(new Object[] { "60", "90", "", "" });
      double[] ranges = info.getRanges();

      assertEquals(4, ranges.length);
      assertTrue(Double.isNaN(ranges[2]));
      assertTrue(Double.isNaN(ranges[3]));
   }

   @Test
   void cylinderTrailingBlanksPaintLikeExplicitMax() throws Exception {
      assertEquals(polygons(cylinder(new Object[] { "60", "90", "100" })),
                   polygons(cylinder(new Object[] { "60", "90", "", "" })));
   }

   @Test
   void verticalThermometerTrailingBlanksPaintLikeExplicitMax() throws Exception {
      assertEquals(polygons(vertical(new Object[] { "60", "90", "100" })),
                   polygons(vertical(new Object[] { "60", "90", "", "" })));
   }

   @Test
   void horizontalThermometerTrailingBlanksPaintLikeExplicitMax() throws Exception {
      assertEquals(polygons(horizontal(new Object[] { "60", "90", "100" })),
                   polygons(horizontal(new Object[] { "60", "90", "", "" })));
   }

   @Test
   void slidingScaleTrailingBlanksPaintLikeExplicitMax() throws Exception {
      assertEquals(rects(slidingScale(new Object[] { "60", "90", "100" })),
                   rects(slidingScale(new Object[] { "60", "90", "", "" })));
   }

   @Test
   void cylinderExplicitMaxControlPaintsThreeRealBands() throws Exception {
      // guards the fixture: the control must paint a real 90..max band
      List<String> control = polygons(cylinder(new Object[] { "60", "90", "100" }));
      assertEquals(3, control.size());
      assertEquals("[100,10][70,10][72,18][100,18]", control.get(0).replace(" ", ""));
   }

   private static Graphics2D cylinder(Object[] ranges) throws Exception {
      CylinderVSAssemblyInfo info = new CylinderVSAssemblyInfo();
      configure(info, ranges);
      VSCylinder r = new VSCylinder();
      r.setAssemblyInfo(info);
      set(r, "majorTickStartY", 10.0);
      set(r, "majorTickEndY", 90.0);
      set(r, "rangeStartX", 100.0);
      set(r, "rangeWidth", 20.0);
      return render(r);
   }

   private static Graphics2D vertical(Object[] ranges) throws Exception {
      ThermometerVSAssemblyInfo info = new ThermometerVSAssemblyInfo();
      configure(info, ranges);
      VSVerticalThermometer r = new VSVerticalThermometer();
      r.setAssemblyInfo(info);
      set(r, "ticksHeight", 80.0);
      set(r, "majorTickStartY", 90.0);
      set(r, "rangeStartX", 100.0);
      set(r, "rangeWidth", 20.0);
      return render(r);
   }

   private static Graphics2D horizontal(Object[] ranges) throws Exception {
      ThermometerVSAssemblyInfo info = new ThermometerVSAssemblyInfo();
      configure(info, ranges);
      VSHorizontalThermometer r = new VSHorizontalThermometer();
      r.setAssemblyInfo(info);
      set(r, "ticksLength", 160.0);
      set(r, "majorTickStartX", 10.0);
      set(r, "rangeStartY", 50.0);
      set(r, "rangeWidth", 20.0);
      return render(r);
   }

   private static Graphics2D slidingScale(Object[] ranges) throws Exception {
      SlidingScaleVSAssemblyInfo info = new SlidingScaleVSAssemblyInfo();
      configure(info, ranges);
      Constructor<VSSlidingScale> ctor = VSSlidingScale.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      VSSlidingScale r = ctor.newInstance();
      r.setAssemblyInfo(info);
      set(r, "linePosition", new Point2D.Double(10, 10));
      set(r, "lineWidth", 160.0);
      set(r, "lineHeight", 6.0);
      return render(r);
   }

   private static void configure(RangeOutputVSAssemblyInfo info, Object[] ranges) {
      info.setMin("0");
      info.setMax("100");
      info.setRanges(ranges);
      info.setRangeColors(COLORS.clone());
   }

   private static void set(Object target, String name, Object value) throws Exception {
      for(Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
         try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
            return;
         }
         catch(NoSuchFieldException ignore) {
            // look in the superclass
         }
      }

      fail("no field " + name);
   }

   private static Graphics2D render(Object renderer) throws Exception {
      BufferedImage img = new BufferedImage(300, 200, BufferedImage.TYPE_4BYTE_ABGR);
      Graphics2D real = img.createGraphics();
      Graphics2D g = mock(Graphics2D.class, AdditionalAnswers.delegatesTo(real));

      try {
         Method m = renderer.getClass().getDeclaredMethod("fillRanges", Graphics2D.class);
         m.setAccessible(true);
         m.invoke(renderer, g);
      }
      finally {
         real.dispose();
      }

      return g;
   }

   private static List<String> polygons(Graphics2D g) {
      ArgumentCaptor<Shape> captor = ArgumentCaptor.forClass(Shape.class);
      verify(g, atLeastOnce()).fill(captor.capture());
      List<String> result = new ArrayList<>();

      for(Shape shape : captor.getAllValues()) {
         Polygon p = (Polygon) shape;
         StringBuilder sb = new StringBuilder();

         for(int i = 0; i < p.npoints; i++) {
            sb.append('[').append(p.xpoints[i]).append(',').append(p.ypoints[i]).append(']');
         }

         result.add(sb.toString());
      }

      return result;
   }

   private static List<String> rects(Graphics2D g) {
      ArgumentCaptor<Integer> x = ArgumentCaptor.forClass(Integer.class);
      ArgumentCaptor<Integer> y = ArgumentCaptor.forClass(Integer.class);
      ArgumentCaptor<Integer> w = ArgumentCaptor.forClass(Integer.class);
      ArgumentCaptor<Integer> h = ArgumentCaptor.forClass(Integer.class);
      verify(g, atLeastOnce()).fillRect(x.capture(), y.capture(), w.capture(), h.capture());
      List<String> result = new ArrayList<>();

      for(int i = 0; i < x.getAllValues().size(); i++) {
         result.add(x.getAllValues().get(i) + "," + y.getAllValues().get(i) + ","
                    + w.getAllValues().get(i) + "," + h.getAllValues().get(i));
      }

      return result;
   }
}
