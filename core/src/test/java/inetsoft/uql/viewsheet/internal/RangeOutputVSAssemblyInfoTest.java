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
import inetsoft.report.gui.viewsheet.gauge.BulletGraphGauge;
import inetsoft.report.gui.viewsheet.slidingscale.VSSlidingScale;
import inetsoft.report.gui.viewsheet.thermometer.VSHorizontalThermometer;
import inetsoft.report.gui.viewsheet.thermometer.VSVerticalThermometer;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76909 (follow-up to #76852): {@code setRanges}/{@code setRangeColors} grew their backing
 * arrays on a longer script write but never shrank them (and, for colors, never grew past the
 * default size either), leaving stale trailing entries from a prior write visible in the
 * rendered gauge/thermometer/cylinder/sliding-scale.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RangeOutputVSAssemblyInfoTest {
   @Test
   void setRangesShrinksExactlyToTheShorterWriteWithNoStaleTail() {
      GaugeVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      info.setRanges(new Object[] { "5000000", "15000000", "25000000", "30000000" });
      info.setRanges(new Object[] { "4000000", "8000000" });

      assertArrayEquals(new double[] { 4000000.0, 8000000.0 }, info.getRanges(), 1e-6);
   }

   @Test
   void setRangeColorsShrinksExactlyToTheShorterWriteWithNoStaleTail() {
      GaugeVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      info.setRangeColors(new Color[] { Color.RED, Color.ORANGE, Color.BLUE, Color.MAGENTA });
      info.setRangeColors(new Color[] { Color.GREEN, Color.YELLOW, Color.RED });

      assertArrayEquals(new Color[] { Color.GREEN, Color.YELLOW, Color.RED }, info.getRangeColors());
   }

   @Test
   void setRangesGrowingWriteStillWorks() {
      GaugeVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      info.setRanges(new Object[] { "10", "20" });
      info.setRanges(new Object[] { "10", "20", "30", "40" });

      assertArrayEquals(new double[] { 10.0, 20.0, 30.0, 40.0 }, info.getRanges(), 1e-6);
   }

   @Test
   void setRangeColorsGrowingPastDefaultSizeStillWorks() {
      // the default rangeColorsValue array already has 4 slots, so this also covers the
      // "never resize at all" half of the bug: 6 colors used to silently truncate to 4.
      GaugeVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      info.setRangeColors(new Color[] {
         Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA, Color.CYAN, Color.PINK });

      assertArrayEquals(
         new Color[] { Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA, Color.CYAN, Color.PINK },
         info.getRangeColors());
   }

   @Test
   void saveReloadRoundTripProducesCorrectlySizedArrays() throws Exception {
      GaugeVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      info.setName("Gauge1");
      info.setRanges(new Object[] { "10", "20", "30" });
      info.setRangeColors(new Color[] { Color.RED, Color.GREEN, Color.BLUE });

      StringWriter sw = new StringWriter();
      info.writeXML(new PrintWriter(sw));

      Document doc = Tool.parseXML(
         new ByteArrayInputStream(sw.toString().getBytes(StandardCharsets.UTF_8)), "UTF-8");
      Element elem = Tool.getFirstElement(doc);

      GaugeVSAssemblyInfo reloaded = new GaugeVSAssemblyInfo();
      reloaded.parseXML(elem);

      // getRanges()/getRangeColors() report the runtime (script) value, which a freshly
      // reloaded DynamicValue has not executed yet -- only the array length is guaranteed
      // here; the value-preservation assertions are already covered by the shrink/grow tests.
      assertEquals(3, reloaded.getRanges().length);
      assertEquals(3, reloaded.getRangeColors().length);
   }

   /**
    * Once the setters independently shrink/grow to the caller's exact incoming length,
    * ranges.length and colors.length can legitimately diverge (e.g. a script writes N ranges
    * but N-1 colors). Cylinder/Thermometer/SlidingScale/BulletGraph must render the agreeing
    * prefix, not throw or silently render nothing.
    */
   @Test
   void cylinderRendersPartiallyInsteadOfThrowingOnLengthMismatch() throws Exception {
      CylinderVSAssemblyInfo info = new CylinderVSAssemblyInfo();
      info.setMin("0");
      info.setMax("100");
      info.setRanges(new Object[] { "30", "60", "90" });
      info.setRangeColors(new Color[] { Color.RED, Color.YELLOW });

      VSCylinder cylinder = new VSCylinder();
      cylinder.setAssemblyInfo(info);

      invokePrivateFillRanges(cylinder, "fillRanges");
   }

   @Test
   void horizontalThermometerRendersPartiallyInsteadOfThrowingOnLengthMismatch() throws Exception {
      ThermometerVSAssemblyInfo info = new ThermometerVSAssemblyInfo();
      info.setMin("0");
      info.setMax("100");
      info.setRanges(new Object[] { "30", "60", "90" });
      info.setRangeColors(new Color[] { Color.RED, Color.YELLOW });

      VSHorizontalThermometer thermometer = new VSHorizontalThermometer();
      thermometer.setAssemblyInfo(info);

      invokePrivateFillRanges(thermometer, "fillRanges");
   }

   @Test
   void verticalThermometerRendersPartiallyInsteadOfThrowingOnLengthMismatch() throws Exception {
      ThermometerVSAssemblyInfo info = new ThermometerVSAssemblyInfo();
      info.setMin("0");
      info.setMax("100");
      info.setRanges(new Object[] { "30", "60", "90" });
      info.setRangeColors(new Color[] { Color.RED, Color.YELLOW });

      VSVerticalThermometer thermometer = new VSVerticalThermometer();
      thermometer.setAssemblyInfo(info);

      invokePrivateFillRanges(thermometer, "fillRanges");
   }

   /**
    * Thermometer's "set the range color same with previous one when the color is null" fallback
    * branch has its own, separate bounds risk: naively allowing length-mismatched rendering
    * without also fixing the loop bound to the shared prefix length would index colors[] using
    * ranges.length, which throws when the shorter colors array's last entry is null (this is
    * the exact class of regression the original, never-merged fix commit introduced and then
    * had to correct in its own review round).
    */
   @Test
   void horizontalThermometerRendersPartiallyWhenShorterColorsArrayEndsInNull() throws Exception {
      ThermometerVSAssemblyInfo info = new ThermometerVSAssemblyInfo();
      info.setMin("0");
      info.setMax("100");
      info.setRanges(new Object[] { "30", "60", "90" });
      info.setRangeColors(new Color[] { Color.RED, null });

      VSHorizontalThermometer thermometer = new VSHorizontalThermometer();
      thermometer.setAssemblyInfo(info);

      invokePrivateFillRanges(thermometer, "fillRanges");
   }

   @Test
   void verticalThermometerRendersPartiallyWhenShorterColorsArrayEndsInNull() throws Exception {
      ThermometerVSAssemblyInfo info = new ThermometerVSAssemblyInfo();
      info.setMin("0");
      info.setMax("100");
      info.setRanges(new Object[] { "30", "60", "90" });
      info.setRangeColors(new Color[] { Color.RED, null });

      VSVerticalThermometer thermometer = new VSVerticalThermometer();
      thermometer.setAssemblyInfo(info);

      invokePrivateFillRanges(thermometer, "fillRanges");
   }

   @Test
   void slidingScaleRendersPartiallyInsteadOfThrowingOnLengthMismatch() throws Exception {
      SlidingScaleVSAssemblyInfo info = new SlidingScaleVSAssemblyInfo();
      info.setMin("0");
      info.setMax("100");
      info.setRanges(new Object[] { "30", "60", "90" });
      info.setRangeColors(new Color[] { Color.RED, Color.YELLOW });

      Constructor<VSSlidingScale> ctor = VSSlidingScale.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      VSSlidingScale slidingScale = ctor.newInstance();
      slidingScale.setAssemblyInfo(info);

      // fillRanges() reads face geometry that is normally populated by parsing a real face
      // XML (irrelevant to the length-mismatch bug under test); set the one object-typed
      // field it dereferences so the only remaining failure mode under test is the
      // ranges/colors length mismatch itself.
      Field linePosition = VSSlidingScale.class.getDeclaredField("linePosition");
      linePosition.setAccessible(true);
      linePosition.set(slidingScale, new Point2D.Double(0, 0));

      invokePrivateFillRanges(slidingScale, "fillRanges");
   }

   @Test
   void bulletGraphGaugeDoesNotThrowWhenColorsArrayIsShorterThanRanges() throws Exception {
      GaugeVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      info.setMin("0");
      info.setMax("100");
      // 3 ranges, only 1 color: before this fix, presenter.setColor2/setColor3 indexed
      // colors[1]/colors[2] with no bound on colors.length at all.
      info.setRanges(new Object[] { "30", "60", "90" });
      info.setRangeColors(new Color[] { Color.RED });

      BulletGraphGauge gauge = new BulletGraphGauge();
      gauge.setAssemblyInfo(info);

      BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_4BYTE_ABGR);
      Graphics2D g = img.createGraphics();

      try {
         Method paint0 = BulletGraphGauge.class.getDeclaredMethod(
            "paint0", Graphics2D.class, Dimension.class, boolean.class);
         paint0.setAccessible(true);

         assertDoesNotThrow(() -> {
            try {
               paint0.invoke(gauge, g, new Dimension(200, 100), false);
            }
            catch(Exception e) {
               throw new RuntimeException(e);
            }
         });
      }
      finally {
         g.dispose();
      }
   }

   private void invokePrivateFillRanges(Object renderer, String methodName) throws Exception {
      BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_4BYTE_ABGR);
      Graphics2D g = img.createGraphics();

      try {
         Method fillRanges = renderer.getClass().getDeclaredMethod(methodName, Graphics2D.class);
         fillRanges.setAccessible(true);

         assertDoesNotThrow(() -> {
            try {
               fillRanges.invoke(renderer, g);
            }
            catch(Exception e) {
               throw new RuntimeException(e);
            }
         });
      }
      finally {
         g.dispose();
      }
   }
}
