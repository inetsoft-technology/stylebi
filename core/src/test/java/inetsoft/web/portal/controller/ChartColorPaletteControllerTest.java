package inetsoft.web.portal.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorPalettes;
import inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults;
import inetsoft.util.DataSpace;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartColorPaletteControllerTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      CSSDictionary.resetDictionaryCache();
   }

   @Test
   void gateOffServesLegacyPalette() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");

      String[] colors = new ChartColorPaletteController().getChartColorPalette();

      assertEquals(40, colors.length);
      assertEquals("#518db9", colors[0]);
      assertEquals("#cccc33", colors[39]);
   }

   @Test
   void gateOnServesModernPalette() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      String[] colors = new ChartColorPaletteController().getChartColorPalette();

      assertEquals(40, colors.length);
      assertEquals("#00d4e8", colors[0]);
      assertEquals("#64748b", colors[7]);
   }

   @Test
   void darkModeServesDarkPalette() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");

      String[] colors = new ChartColorPaletteController().getChartColorPalette();

      assertEquals("#22d3ee", colors[0]);
      assertEquals("#94a3b8", colors[7]);
   }

   @Test
   void everyEntryIsALowercaseHexTriplet() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      for(String color : new ChartColorPaletteController().getChartColorPalette()) {
         assertTrue(color.matches("#[0-9a-f]{6}"), "not a hex triplet: " + color);
      }
   }

   // Pins the endpoint<->client URL contract: nothing else ties the @GetMapping path string to
   // CHART_COLOR_PALETTE_URI in chart-palette.service.ts, so a typo on either side would fail
   // silently (the client's catchError keeps the legacy grid rather than erroring visibly).
   @Test
   void mappedPathMatchesClientContract() throws NoSuchMethodException {
      Method method = ChartColorPaletteController.class.getMethod("getChartColorPalette");
      GetMapping mapping = method.getAnnotation(GetMapping.class);

      assertNotNull(mapping, "getChartColorPalette must carry @GetMapping");
      assertArrayEquals(new String[] { "/api/portal/chart-color-palette" }, mapping.value());
   }

   // fromFrame() deliberately does not cap palette length (a customer format.css declaring a
   // gapless palette past index 40 must still render all of it), so the controller is the only
   // seam that can enforce the 40-entry contract the swatch grid depends on. The gate-off path is
   // used here because it reads Default directly, bypassing VSChartPaletteDefaults' per-org memo -
   // exercising the gate-on path would require reflecting into that memo to force a re-resolve.
   @Test
   void truncatesToFortyWhenCssPaletteIsLonger() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");

      try {
         writeFormatCss("ChartPalette[name='Default'][index='41'] { color: #123456; }");
         forceReload();
         CSSDictionary.resetDictionaryCache();

         String[] colors = new ChartColorPaletteController().getChartColorPalette();

         assertEquals(40, colors.length);
         assertEquals("#518db9", colors[0]);
         assertEquals("#cccc33", colors[39]);
      }
      finally {
         DataSpace space = DataSpace.getDataSpace();

         if(space.exists("portal", "format.css")) {
            space.delete("portal", "format.css");
         }

         forceReload();
         CSSDictionary.resetDictionaryCache();
      }
   }

   private void writeFormatCss(String content) throws IOException {
      DataSpace space = DataSpace.getDataSpace();
      space.withOutputStream("portal", "format.css",
                              out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
   }

   // ColorPalettes caches per org with no reset hook, only refreshing once its internal
   // last-modified stamp is stale. Force a genuine reload regardless of that staleness window.
   private void forceReload() throws Exception {
      Field singletonField = ColorPalettes.class.getDeclaredField("singleton");
      singletonField.setAccessible(true);
      Object singleton = singletonField.get(null);

      Field paletteMapField = ColorPalettes.class.getDeclaredField("paletteMap");
      paletteMapField.setAccessible(true);
      Map<?, ?> paletteMap = (Map<?, ?>) paletteMapField.get(singleton);
      paletteMap.remove(OrganizationManager.getInstance().getCurrentOrgID());

      Field lastField = ColorPalettes.class.getDeclaredField("last");
      lastField.setAccessible(true);
      lastField.setLong(singleton, 0L);
   }
}
