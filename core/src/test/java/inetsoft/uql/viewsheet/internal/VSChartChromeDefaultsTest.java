package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.internal.GDefaults;
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
class VSChartChromeDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.modernChartChrome", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private void darkOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
   }

   @Test
   void lightModernValuesUnchanged() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(0xE8E5DE, rgb(VSChartChromeDefaults.gridlineColor()));
      assertEquals(0x6A685F, rgb(VSChartChromeDefaults.labelColor()));
      assertEquals(0x35342F, rgb(VSChartChromeDefaults.titleColor()));
   }

   @Test
   void plainAccessorsDark() {
      darkOn();
      assertEquals(0x3A383D, rgb(VSChartChromeDefaults.gridlineColor()));
      assertEquals(0x3A383D, rgb(VSChartChromeDefaults.legendBorderColor()));
      assertEquals(0xCAC4D0, rgb(VSChartChromeDefaults.labelColor()));
      assertEquals(0xE6E0E9, rgb(VSChartChromeDefaults.titleColor()));
   }

   @Test
   void resolveAxisLineDarkOnlyWhenStillLegacyDefault() {
      darkOn();
      // a bare legacy default becomes the dark gridline
      assertEquals(0x3A383D, rgb(VSChartChromeDefaults.resolveAxisLineColor(GDefaults.DEFAULT_LINE_COLOR)));
      // a user/format.css color is preserved
      Color custom = new Color(0x123456);
      assertEquals(0x123456, rgb(VSChartChromeDefaults.resolveAxisLineColor(custom)));
   }

   @Test
   void legendBackgroundLightIsWhite() {
      // light modern and legacy keep the white legend panel (byte-identical)
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(0xFFFFFF, rgb(VSChartChromeDefaults.legendBackground()));
   }

   @Test
   void legendBackgroundDark() {
      darkOn();
      assertEquals(0x252428, rgb(VSChartChromeDefaults.legendBackground()));
   }

   @Test
   void resolveGridlineDark() {
      darkOn();
      assertEquals(0x3A383D, rgb(VSChartChromeDefaults.resolveGridlineColor(GDefaults.DEFAULT_GRIDLINE_COLOR)));
      // a user/format.css color is preserved
      assertEquals(0x123456, rgb(VSChartChromeDefaults.resolveGridlineColor(new Color(0x123456))));
   }

   @Test
   void resolveLegendBorderDark() {
      darkOn();
      assertEquals(0x3A383D, rgb(VSChartChromeDefaults.resolveLegendBorderColor(GDefaults.DEFAULT_LINE_COLOR)));
      // a user/format.css color is preserved
      assertEquals(0x123456, rgb(VSChartChromeDefaults.resolveLegendBorderColor(new Color(0x123456))));
   }

   private static int rgb(Color c) {
      return c.getRGB() & 0xFFFFFF;
   }
}
