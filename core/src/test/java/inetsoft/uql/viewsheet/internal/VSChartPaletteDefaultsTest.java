package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
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
class VSChartPaletteDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.modernChartPalette", null);
   }

   @Test
   void gateOffReturnsFalseAndLeavesPaletteUntouched() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertFalse(VSChartPaletteDefaults.isModern());

      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartPaletteDefaults.applyModernPalette(frame);
      // gate off => still the legacy head color
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[0], frame.getColor(0));
   }

   @Test
   void gateOnSwapsToModernHeadButKeepsLegacyTail() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertTrue(VSChartPaletteDefaults.isModern());

      Color[] modern = VSChartPaletteDefaults.modernPalette();
      assertEquals(40, modern.length, "8 modern + 32 legacy tail = 40");
      assertEquals(new Color(0x00D4E8), modern[0]);
      assertEquals(new Color(0x64748B), modern[7]);
      // index 9+ preserves the legacy tail unchanged
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[8], modern[8]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[39], modern[39]);

      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartPaletteDefaults.applyModernPalette(frame);
      assertEquals(new Color(0x00D4E8), frame.getColor(0));
      assertEquals(new Color(0x00B87A), frame.getColor(1));
   }

   @Test
   void subGateFalseOptsOutEvenWhenBaseGateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.modernChartPalette", "false");
      assertFalse(VSChartPaletteDefaults.isModern());
   }

   // Regression: the value-based render path (getColor(Object)) resolves through the cached
   // unusedColors list, which is built lazily from defaultColors. If the frame was already used
   // (cache warm) before the palette swap, the swap must still take effect — setDefaultColors must
   // invalidate the cache. Without that, marks render the stale legacy palette while the color
   // dropdown (which reads defaultColors directly) shows modern.
   @Test
   void swapTakesEffectOnValuePathEvenWhenCacheAlreadyWarm() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      CategoricalColorFrame warmed = new CategoricalColorFrame();
      warmed.init("A", "B", "C");
      warmed.getColor("A");                       // warm the unusedColors cache from legacy
      VSChartPaletteDefaults.applyModernPalette(warmed);

      CategoricalColorFrame fresh = new CategoricalColorFrame();
      fresh.init("A", "B", "C");
      VSChartPaletteDefaults.applyModernPalette(fresh);   // never warmed

      // a warmed-then-swapped frame must render identically to a fresh-swapped frame — i.e. the
      // stale cache did not poison the render. Both go through the same brightness processing.
      assertEquals(fresh.getColor("A"), warmed.getColor("A"));
      assertEquals(fresh.getColor("B"), warmed.getColor("B"));
   }
}
