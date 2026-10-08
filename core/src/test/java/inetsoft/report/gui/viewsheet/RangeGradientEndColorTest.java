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
package inetsoft.report.gui.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.gui.viewsheet.cylinder.VSCylinder;
import inetsoft.report.gui.viewsheet.gauge.DefaultVSGauge;
import inetsoft.report.gui.viewsheet.gauge.VSGauge;
import inetsoft.report.gui.viewsheet.slidingscale.VSSlidingScale;
import inetsoft.report.gui.viewsheet.thermometer.VSHorizontalThermometer;
import inetsoft.report.gui.viewsheet.thermometer.VSThermometer;
import inetsoft.report.gui.viewsheet.thermometer.VSVerticalThermometer;
import inetsoft.report.script.viewsheet.GaugeVSAScriptable;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.AdditionalAnswers;
import org.mockito.invocation.Invocation;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * Bug #78009: the end color of a range band's gradient is the color of the next row that
 * has a value; when no later row has a value it is the "Gradient" blend slot
 * (rangeColors[ranges.length]) when set, else the band's own darker() color. Each case
 * renders the real gauge/cylinder/thermometer/sliding-scale classes on a recording
 * Graphics2D and reads the gradient paints they set.
 *
 * Bug #78086: when a script shortens the ranges of a dialog design and does not set the
 * colors, the last band ends at the design blend color, not at a dropped row's color.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RangeGradientEndColorTest {
   private static final Color G = new Color(0x00aa00);
   private static final Color Y = new Color(0xffcc00);
   private static final Color R = new Color(0xdd0000);
   private static final Color O = new Color(0xff8800);
   private static final Color P = new Color(0x8800ff);
   private static final Color BLEND = new Color(0x0000ff);

   /** The renderer types that fill range bands with a gradient. */
   enum Kind {
      GAUGE, CYLINDER, SLIDING_SCALE, VERTICAL_THERMOMETER, HORIZONTAL_THERMOMETER
   }

   // ---- default gauge family (DefaultVSGauge.fillRanges0) ----

   @Test
   void gaugeWithThreeRowsEndsLastBandAtBlendColor() throws Exception {
      List<String> bands = render(Kind.GAUGE, "25",
         new String[] { "5", "15", "20", "", "" },
         new Color[] { G, Y, R, null, null, BLEND });

      assertTrue(bands.contains(band(G, Y)), bands::toString);
      assertTrue(bands.contains(band(Y, R)), bands::toString);
      assertTrue(bands.contains(band(R, BLEND)), bands::toString);
      assertFalse(bands.contains(band(R, R.darker())), bands::toString);
   }

   @Test
   void gaugeWithFiveRowsIsUnchanged() throws Exception {
      List<String> bands = render(Kind.GAUGE, "25",
         new String[] { "5", "10", "15", "20", "25" },
         new Color[] { G, Y, R, O, P, BLEND });

      assertEquals(List.of(band(G, Y), band(Y, R), band(R, O), band(O, P), band(P, BLEND)),
                   bands);
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   void bandBeforeEmptyMiddleRowFadesIntoNextValuedRow(Kind kind) throws Exception {
      // the cylinder and thermometers also paint a zero-height band for the empty row
      // (its null color filled from the next row), so check with contains, not equals
      List<String> bands = render(kind, "25",
         new String[] { "5", "15", "", "20", "" },
         new Color[] { G, Y, null, R, null, BLEND });

      assertTrue(bands.contains(band(G, Y)), bands::toString);
      assertTrue(bands.contains(band(Y, R)), bands::toString);
      assertTrue(bands.contains(band(R, BLEND)), bands::toString);
      assertFalse(bands.contains(band(Y, Y.darker())), bands::toString);
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   void rowWithColorButNoValueDoesNotLeakItsColor(Kind kind) throws Exception {
      List<String> bands = render(kind, "25",
         new String[] { "5", "15", "20", "", "" },
         new Color[] { G, Y, R, O, null, BLEND });

      assertTrue(bands.contains(band(R, BLEND)), bands::toString);
      assertFalse(bands.contains(band(R, O)), bands::toString);
   }

   @Test
   void gaugeBandClippedAtMaxStillFadesIntoNextValuedRow() throws Exception {
      // rows 4 and 5 are past max=25: band 4 is clipped at max and band 5 is never painted,
      // but band 4 keeps fading into row 5's color, as before
      List<String> bands = render(Kind.GAUGE, "25",
         new String[] { "5", "15", "20", "30", "40" },
         new Color[] { G, Y, R, O, P, BLEND });

      assertTrue(bands.contains(band(R, O)), bands::toString);
      assertTrue(bands.contains(band(O, P)), bands::toString);
      assertFalse(bands.contains(band(O, BLEND)), bands::toString);
   }

   @Test
   void gaugeScriptColorsWithoutBlendSlotKeepTheirLastBand() throws Exception {
      // a script that writes 3 colors for 3 ranges has no blend slot: the last band keeps
      // its pre-existing end color (its own color on the gauge)
      GaugeVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      info.setMin("0");
      info.setMax("25");
      info.setRangeGradientValue(true);
      info.setRanges(new Object[] { "5", "15", "20" });
      info.setRangeColors(new Color[] { G, Y, R });

      List<String> bands = render(Kind.GAUGE, info);

      assertEquals(List.of(band(G, Y), band(Y, R), band(R, R)), bands);
   }

   // ---- every gradient renderer ----

   @ParameterizedTest
   @EnumSource(Kind.class)
   void threeRowsEndAtBlendColor(Kind kind) throws Exception {
      List<String> bands = render(kind, "25",
         new String[] { "5", "15", "20", "", "" },
         new Color[] { G, Y, R, null, null, BLEND });

      assertTrue(bands.contains(band(Y, R)), bands::toString);
      assertTrue(bands.contains(band(R, BLEND)), bands::toString);
      assertFalse(bands.contains(band(R, R.darker())), bands::toString);
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   void fiveRowsEndAtBlendColor(Kind kind) throws Exception {
      List<String> bands = render(kind, "25",
         new String[] { "5", "10", "15", "20", "25" },
         new Color[] { G, Y, R, O, P, BLEND });

      assertTrue(bands.contains(band(G, Y)), bands::toString);
      assertTrue(bands.contains(band(O, P)), bands::toString);
      assertTrue(bands.contains(band(P, BLEND)), bands::toString);
      assertFalse(bands.contains(band(P, P.darker())), bands::toString);
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   void unsetBlendSlotFallsBackToDarker(Kind kind) throws Exception {
      List<String> bands = render(kind, "25",
         new String[] { "5", "15", "20", "", "" },
         new Color[] { G, Y, R, null, null, null });

      assertTrue(bands.contains(band(Y, R)), bands::toString);
      assertTrue(bands.contains(band(R, R.darker())), bands::toString);
   }

   @ParameterizedTest
   @EnumSource(value = Kind.class, names = "GAUGE", mode = EnumSource.Mode.EXCLUDE)
   void scriptColorsWithoutBlendSlotFallBackToDarker(Kind kind) throws Exception {
      RangeOutputVSAssemblyInfo info = createInfo(kind);
      info.setMin("0");
      info.setMax("25");
      info.setRangeGradientValue(true);
      info.setRanges(new Object[] { "5", "15", "20" });
      info.setRangeColors(new Color[] { G, Y, R });

      List<String> bands = render(kind, info);

      assertTrue(bands.contains(band(Y, R)), bands::toString);
      assertTrue(bands.contains(band(R, R.darker())), bands::toString);
   }

   /**
    * #76909: ranges and colors may differ in length. With gradient on, the end-color lookup
    * must stay bounded by the colors array.
    */
   @ParameterizedTest
   @EnumSource(Kind.class)
   void fewerColorsThanRangesDoesNotOverflow(Kind kind) throws Exception {
      RangeOutputVSAssemblyInfo info = createInfo(kind);
      info.setMin("0");
      info.setMax("100");
      info.setRangeGradientValue(true);
      info.setRanges(new Object[] { "30", "60", "90" });
      info.setRangeColors(new Color[] { R, Y });

      List<String> bands = render(kind, info);

      assertTrue(bands.contains(band(R, Y)), bands::toString);
   }

   // ---- #78086: a script shortens the ranges of a dialog design ----

   @ParameterizedTest
   @EnumSource(Kind.class)
   void scriptShortenedRangesEndLastBandAtBlendColor(Kind kind) throws Exception {
      RangeOutputVSAssemblyInfo info = createDesign(kind);
      info.setRanges(new Object[] { "5", "15", "20" });

      List<String> bands = render(kind, info);

      assertTrue(bands.contains(band(Y, R)), bands::toString);
      assertTrue(bands.contains(band(R, BLEND)), bands::toString);
      assertFalse(bands.contains(band(R, O)), bands::toString);
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   void scriptFourRangesEndLastBandAtBlendColor(Kind kind) throws Exception {
      RangeOutputVSAssemblyInfo info = createDesign(kind);
      info.setRanges(new Object[] { "5", "10", "15", "20" });

      List<String> bands = render(kind, info);

      assertTrue(bands.contains(band(R, O)), bands::toString);
      assertTrue(bands.contains(band(O, BLEND)), bands::toString);
      assertFalse(bands.contains(band(O, P)), bands::toString);
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   void scriptShortenedRangesOfDesignWithEmptyRowsEndAtBlendColor(Kind kind) throws Exception {
      RangeOutputVSAssemblyInfo info = createInfo(kind);
      info.setMin("0");
      info.setMax("25");
      info.setRangeValues(new String[] { "5", "15", "20", "", "" });
      info.setRangeColorsValue(new Color[] { G, Y, R, null, null, BLEND });
      info.setRangeGradientValue(true);
      info.setRanges(new Object[] { "5", "15" });

      List<String> bands = render(kind, info);

      assertTrue(bands.contains(band(G, Y)), bands::toString);
      assertTrue(bands.contains(band(Y, BLEND)), bands::toString);
      assertFalse(bands.contains(band(Y, R)), bands::toString);
   }

   @Test
   void gaugeScriptRangesThroughScriptableEndAtBlendAndKeepScriptColors() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      GaugeVSAssembly assembly = new GaugeVSAssembly();
      GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setName("Gauge1");
      vs.addAssembly(assembly);
      setDesign(info);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);
      GaugeVSAScriptable scriptable = new GaugeVSAScriptable(box);
      scriptable.setAssembly("Gauge1");

      // Gauge1.ranges = [5, 15, 20]
      scriptable.putMember("ranges", new String[] { "5", "15", "20" });

      assertEquals(List.of(band(G, Y), band(Y, R), band(R, BLEND)), render(Kind.GAUGE, info));
      // the rangeColors a script reads is unchanged: all 6 design colors
      assertArrayEquals(new Color[] { G, Y, R, O, P, BLEND }, info.getRangeColors());
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   void scriptColorsEqualToDesignKeepPositionalEndColor(Kind kind) throws Exception {
      // intended seam: colors set by a script are positional, even when they equal the
      // design colors, so the last band fades into colors[ranges.length] as before
      RangeOutputVSAssemblyInfo info = createDesign(kind);
      info.setRangeColors(new Color[] { G, Y, R, O, P, BLEND });
      info.setRanges(new Object[] { "5", "15", "20" });

      List<String> bands = render(kind, info);

      assertTrue(bands.contains(band(R, O)), bands::toString);
      assertFalse(bands.contains(band(R, BLEND)), bands::toString);
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   void scriptColorsWithBlendKeepTheirBlend(Kind kind) throws Exception {
      RangeOutputVSAssemblyInfo info = createDesign(kind);
      info.setRanges(new Object[] { "5", "15", "20" });
      info.setRangeColors(new Color[] { G, Y, R, BLEND });

      List<String> bands = render(kind, info);

      assertTrue(bands.contains(band(R, BLEND)), bands::toString);
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   void legacyDesignWithoutBlendEndsAtDarkerColor(Kind kind) throws Exception {
      // known limit: an older 3-row design has its 3 colors padded to 4 slots, the 4th unset,
      // so a script that shortens it to 2 ranges ends the last band at Y.darker() (was R),
      // the same way the unscripted design ends its last band at R.darker()
      RangeOutputVSAssemblyInfo info = createInfo(kind);
      info.setMin("0");
      info.setMax("25");
      info.setRangeValues(new String[] { "5", "15", "20" });
      info.setRangeColorsValue(new Color[] { G, Y, R });
      info.setRangeGradientValue(true);
      info.setRanges(new Object[] { "5", "15" });

      List<String> bands = render(kind, info);

      assertTrue(bands.contains(band(Y, Y.darker())), bands::toString);
      assertFalse(bands.contains(band(Y, R)), bands::toString);
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   void resetRuntimeValuesRestoresDesignAndClearsScriptColors(Kind kind) throws Exception {
      RangeOutputVSAssemblyInfo info = createDesign(kind);
      info.setRangeColors(new Color[] { G, Y, R, O, P, BLEND });
      info.setRanges(new Object[] { "5", "15", "20" });
      info.resetRuntimeValues();

      List<String> bands = render(kind, info);
      assertTrue(bands.contains(band(O, P)), bands::toString);
      assertTrue(bands.contains(band(P, BLEND)), bands::toString);

      // the script colors are gone, so a later ranges-only script ends at the blend color
      info.setRanges(new Object[] { "5", "15", "20" });
      bands = render(kind, info);
      assertTrue(bands.contains(band(R, BLEND)), bands::toString);
   }

   @Test
   void copyInfoAndCloneCarryScriptColorsState() throws Exception {
      GaugeVSAssemblyInfo design = new GaugeVSAssemblyInfo();
      setDesign(design);

      // the script colors equal the design colors: only the script-set state differs
      GaugeVSAssemblyInfo scripted = (GaugeVSAssemblyInfo) design.clone();
      scripted.setRangeColors(new Color[] { G, Y, R, O, P, BLEND });

      GaugeVSAssemblyInfo target = new GaugeVSAssemblyInfo();
      setDesign(target);
      target.copyInfo(scripted);
      target.setRanges(new Object[] { "5", "15", "20" });
      List<String> bands = render(Kind.GAUGE, target);
      assertTrue(bands.contains(band(R, O)), bands::toString);

      GaugeVSAssemblyInfo clean = new GaugeVSAssemblyInfo();
      setDesign(clean);
      target.copyInfo(clean);
      target.setRanges(new Object[] { "5", "15", "20" });
      bands = render(Kind.GAUGE, target);
      assertTrue(bands.contains(band(R, BLEND)), bands::toString);

      GaugeVSAssemblyInfo cloned = (GaugeVSAssemblyInfo) scripted.clone();
      cloned.setRanges(new Object[] { "5", "15", "20" });
      bands = render(Kind.GAUGE, cloned);
      assertTrue(bands.contains(band(R, O)), bands::toString);
   }

   /** A dialog design: 5 rows 5..25 with colors G,Y,R,O,P and the blend color, max 25. */
   private static RangeOutputVSAssemblyInfo createDesign(Kind kind) {
      RangeOutputVSAssemblyInfo info = createInfo(kind);
      setDesign(info);
      return info;
   }

   private static void setDesign(RangeOutputVSAssemblyInfo info) {
      info.setMin("0");
      info.setMax("25");
      info.setRangeValues(new String[] { "5", "10", "15", "20", "25" });
      info.setRangeColorsValue(new Color[] { G, Y, R, O, P, BLEND });
      info.setRangeGradientValue(true);
   }

   // ---- helpers ----

   private static String band(Color start, Color end) {
      return hex(start) + "->" + hex(end);
   }

   private static String hex(Color c) {
      return c == null ? "null" : String.format("#%06x", c.getRGB() & 0xffffff);
   }

   private static RangeOutputVSAssemblyInfo createInfo(Kind kind) {
      switch(kind) {
      case GAUGE:
         return new GaugeVSAssemblyInfo();
      case CYLINDER:
         return new CylinderVSAssemblyInfo();
      case SLIDING_SCALE:
         return new SlidingScaleVSAssemblyInfo();
      default:
         return new ThermometerVSAssemblyInfo();
      }
   }

   /** Set ranges and colors the way the gauge property dialog does (5 rows, 6 colors). */
   private static List<String> render(Kind kind, String max, String[] ranges, Color[] colors)
      throws Exception
   {
      RangeOutputVSAssemblyInfo info = createInfo(kind);
      info.setMin("0");
      info.setMax(max);
      info.setRangeValues(ranges);
      info.setRangeColorsValue(colors);
      info.setRangeGradientValue(true);
      return render(kind, info);
   }

   private static VSImageable createRenderer(Kind kind, RangeOutputVSAssemblyInfo info) {
      VSImageable renderer;

      switch(kind) {
      case GAUGE:
         renderer = VSGauge.getGauge(info.getFace());
         assertInstanceOf(DefaultVSGauge.class, renderer);
         break;
      case CYLINDER:
         renderer = VSCylinder.getCylinder(info.getFace());
         break;
      case SLIDING_SCALE:
         renderer = VSSlidingScale.getSlidingScale(info.getFace());
         break;
      case VERTICAL_THERMOMETER:
         renderer = VSThermometer.getThermometer(info.getFace());
         assertInstanceOf(VSVerticalThermometer.class, renderer);
         break;
      default:
         renderer = VSThermometer.getThermometer(110);
         assertInstanceOf(VSHorizontalThermometer.class, renderer);
      }

      assertNotNull(renderer, kind::toString);
      renderer.setAssemblyInfo(info);
      renderer.setPixelSize(new Dimension(300, 300));
      return renderer;
   }

   /**
    * Renders the range bands and returns each gradient band as "start->end", where start is
    * the band's own color and end the color it fades into.
    */
   private static List<String> render(Kind kind, RangeOutputVSAssemblyInfo info)
      throws Exception
   {
      VSImageable renderer = createRenderer(kind, info);

      if(kind == Kind.GAUGE) {
         // the gauge's arc geometry (center/radius) comes from adjust()
         findMethod(renderer.getClass(), "adjust").invoke(renderer);
      }

      BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_4BYTE_ABGR);
      Graphics2D real = img.createGraphics();
      Graphics2D g = mock(Graphics2D.class, AdditionalAnswers.delegatesTo(real));

      try {
         findMethod(renderer.getClass(), "fillRanges", Graphics2D.class).invoke(renderer, g);
      }
      finally {
         real.dispose();
      }

      // the cylinder and thermometers build their GradientPaint from the end color to the
      // band's own color; the gauge and sliding scale from the band's color to the end color
      boolean endFirst = kind == Kind.CYLINDER || kind == Kind.VERTICAL_THERMOMETER ||
         kind == Kind.HORIZONTAL_THERMOMETER;
      List<String> bands = new ArrayList<>();

      for(Invocation invocation : mockingDetails(g).getInvocations()) {
         if(!"setPaint".equals(invocation.getMethod().getName())) {
            continue;
         }

         Object paint = invocation.getArgument(0);
         Color first;
         Color last;

         if(paint instanceof GradientPaint) {
            first = ((GradientPaint) paint).getColor1();
            last = ((GradientPaint) paint).getColor2();
         }
         else if(paint instanceof LinearGradientPaint) {
            Color[] colors = ((LinearGradientPaint) paint).getColors();
            first = colors[0];
            last = colors[colors.length - 1];
         }
         else {
            continue;
         }

         bands.add(endFirst ? band(last, first) : band(first, last));
      }

      return bands;
   }

   private static Method findMethod(Class<?> cls, String name, Class<?>... params)
      throws NoSuchMethodException
   {
      for(Class<?> c = cls; c != null; c = c.getSuperclass()) {
         try {
            Method method = c.getDeclaredMethod(name, params);
            method.setAccessible(true);
            return method;
         }
         catch(NoSuchMethodException ignore) {
            // look in the superclass
         }
      }

      throw new NoSuchMethodException(cls.getName() + "." + name);
   }
}
