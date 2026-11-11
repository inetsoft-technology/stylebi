package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.internal.GDefaults;
import inetsoft.report.StyleConstants;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.LegendsDescriptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSChartChromeDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private void darkOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
   }

   @Test
   void lightModernValuesUnchanged() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(0xD6D2C8, rgb(VSChartChromeDefaults.gridlineColor(VizContext.ofGate())));
      assertEquals(0x6A685F, rgb(VSChartChromeDefaults.labelColor(VizContext.ofGate())));
      assertEquals(0x35342F, rgb(VSChartChromeDefaults.titleColor(VizContext.ofGate())));
   }

   @Test
   void plainAccessorsDark() {
      darkOn();
      assertEquals(0x4A484F, rgb(VSChartChromeDefaults.gridlineColor(VizContext.ofGate())));
      assertEquals(0x4A484F, rgb(VSChartChromeDefaults.legendBorderColor(VizContext.ofGate())));
      assertEquals(0xCAC4D0, rgb(VSChartChromeDefaults.labelColor(VizContext.ofGate())));
      assertEquals(0xE6E0E9, rgb(VSChartChromeDefaults.titleColor(VizContext.ofGate())));
   }

   @Test
   void resolveAxisLineDarkOnlyWhenStillLegacyDefault() {
      darkOn();
      // a bare legacy default becomes the dark axis line
      assertEquals(0x7E7B86, rgb(VSChartChromeDefaults.resolveAxisLineColor(GDefaults.DEFAULT_LINE_COLOR, VizContext.ofGate())));
      // a user/format.css color is preserved
      Color custom = new Color(0x123456);
      assertEquals(0x123456, rgb(VSChartChromeDefaults.resolveAxisLineColor(custom, VizContext.ofGate())));
   }

   @Test
   void axisLineLightModern() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(0x85827A, rgb(VSChartChromeDefaults.resolveAxisLineColor(GDefaults.DEFAULT_LINE_COLOR, VizContext.ofGate())));
   }

   @Test
   void spokesKeepTheGridColour() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(0xD6D2C8, rgb(VSChartChromeDefaults.resolveSpokeColor(GDefaults.DEFAULT_LINE_COLOR, VizContext.ofGate())));
      assertEquals(0x123456, rgb(VSChartChromeDefaults.resolveSpokeColor(new Color(0x123456), VizContext.ofGate())));
      assertEquals(GDefaults.DEFAULT_LINE_COLOR, VSChartChromeDefaults.resolveSpokeColor(GDefaults.DEFAULT_LINE_COLOR, VizContext.LEGACY));
   }

   @Test
   void legendBackgroundLightIsWhite() {
      // light modern and legacy keep the white legend panel (byte-identical)
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(0xFFFFFF, rgb(VSChartChromeDefaults.legendBackground(VizContext.ofGate())));
   }

   @Test
   void legendBackgroundDark() {
      darkOn();
      assertEquals(0x3E3C43, rgb(VSChartChromeDefaults.legendBackground(VizContext.ofGate())));
   }

   @Test
   void theLegendIsFramedOnlyWhenClassicOrInPlace() {
      VizContext light = VizContext.of(VizMark.MODERN_LIGHT);
      VizContext dark = VizContext.of(VizMark.MODERN_DARK);
      int[] outside = { LegendsDescriptor.RIGHT, LegendsDescriptor.TOP,
                        LegendsDescriptor.BOTTOM, LegendsDescriptor.LEFT };

      for(int layout : outside) {
         for(VizContext ctx : new VizContext[] { light, dark }) {
            assertFalse(VSChartChromeDefaults.legendFramed(ctx, layout), "framed " + layout);
            assertEquals(StyleConstants.NONE, VSChartChromeDefaults.legendBorder(ctx, layout),
                         "border " + layout);
            assertNull(VSChartChromeDefaults.legendFill(ctx, layout), "fill " + layout);
         }
      }

      int inPlace = LegendsDescriptor.IN_PLACE;
      assertTrue(VSChartChromeDefaults.legendFramed(light, inPlace));
      assertEquals(StyleConstants.THIN_LINE, VSChartChromeDefaults.legendBorder(light, inPlace));
      assertEquals(StyleConstants.THIN_LINE, VSChartChromeDefaults.legendBorder(dark, inPlace));
      assertEquals(0xFFFFFF, rgb(VSChartChromeDefaults.legendFill(light, inPlace)));
      assertEquals(0x3E3C43, rgb(VSChartChromeDefaults.legendFill(dark, inPlace)));

      for(int layout = LegendsDescriptor.NO_LEGEND; layout <= inPlace; layout++) {
         assertTrue(VSChartChromeDefaults.legendFramed(VizContext.LEGACY, layout),
                    "classic framed " + layout);
         assertEquals(StyleConstants.THIN_LINE,
                      VSChartChromeDefaults.legendBorder(VizContext.LEGACY, layout),
                      "classic border " + layout);
         assertEquals(Color.WHITE, VSChartChromeDefaults.legendFill(VizContext.LEGACY, layout),
                      "classic fill " + layout);
      }
   }

   @Test
   void resolveGridlineDark() {
      darkOn();
      assertEquals(0x4A484F, rgb(VSChartChromeDefaults.resolveGridlineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, VizContext.ofGate())));
      // a user/format.css color is preserved
      assertEquals(0x123456, rgb(VSChartChromeDefaults.resolveGridlineColor(new Color(0x123456), VizContext.ofGate())));
   }

   @Test
   void resolveLegendBorderDark() {
      darkOn();
      assertEquals(0x4A484F, rgb(VSChartChromeDefaults.resolveLegendBorderColor(GDefaults.DEFAULT_LINE_COLOR, VizContext.ofGate())));
      // a user/format.css color is preserved
      assertEquals(0x123456, rgb(VSChartChromeDefaults.resolveLegendBorderColor(new Color(0x123456), VizContext.ofGate())));
   }

   @Test
   void aTopOrBottomLegendGapIsOneStepLarger() {
      String[] modes = { "dense", "compact", "comfortable" };
      int[] side = { 10, 14, 18 };

      for(int i = 0; i < modes.length; i++) {
         VizContext ctx = modernOn(modes[i]);

         for(int layout : new int[] { LegendsDescriptor.TOP, LegendsDescriptor.BOTTOM }) {
            assertEquals(side[i] + 4,
                         VSChartChromeDefaults.resolveLegendGap(0, false, ctx, layout),
                         modes[i] + " layout " + layout);
         }

         for(int layout : new int[] { LegendsDescriptor.RIGHT, LegendsDescriptor.LEFT,
                                      LegendsDescriptor.IN_PLACE })
         {
            assertEquals(side[i], VSChartChromeDefaults.resolveLegendGap(0, false, ctx, layout),
                         modes[i] + " layout " + layout);
         }

         assertEquals(7, VSChartChromeDefaults.resolveLegendGap(
            7, true, ctx, LegendsDescriptor.TOP), "an opinion is kept");
      }

      assertEquals(7, VSChartChromeDefaults.resolveLegendGap(
         7, false, VizContext.LEGACY, LegendsDescriptor.TOP), "a legacy context is kept");
   }

   private static VizContext modernOn(String density) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity(density);
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      return VizContext.of(chart.getVSAssemblyInfo());
   }

   private static int rgb(Color c) {
      return c.getRGB() & 0xFFFFFF;
   }
}
