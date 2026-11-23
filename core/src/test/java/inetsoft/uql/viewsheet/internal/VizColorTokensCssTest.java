/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorPalettes;
import inetsoft.util.DataSpace;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The direction colors, the card background and the dark text resolve from the "Modern-tokens" and
 * "Modern Dark-tokens" sets in format.css, the way the palette does, so an org's styling reaches
 * them. defaults.css declares the same values as the Java fallbacks, so only a differing override
 * proves the CSS is consulted. This class writes, and always removes, a real dataspace file.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VizColorTokensCssTest {
   private static final VizContext LIGHT = VizContext.of(VizMark.MODERN_LIGHT);
   private static final VizContext DARK = VizContext.of(VizMark.MODERN_DARK);

   @AfterEach
   void cleanup() throws Exception {
      DataSpace space = DataSpace.getDataSpace();

      if(space.exists("portal", "format.css")) {
         space.delete("portal", "format.css");
      }

      reload();
      assertEquals(new Color(0xAD6119), VSChartPaletteDefaults.directionDown(LIGHT));
   }

   @Test
   void theDefaultsAreTheSeededValues() {
      assertEquals(new Color(0x1C857B), VSChartPaletteDefaults.directionUp(LIGHT));
      assertEquals(new Color(0xAD6119), VSChartPaletteDefaults.directionDown(LIGHT));
      assertEquals(new Color(0x767676), VSChartPaletteDefaults.directionNeutral(LIGHT));
      assertEquals(new Color(0x3FBFB2), VSChartPaletteDefaults.directionUp(DARK));
      assertEquals(new Color(0xE8954A), VSChartPaletteDefaults.directionDown(DARK));
      assertEquals(new Color(0xA8A5AD), VSChartPaletteDefaults.directionNeutral(DARK));
      assertEquals(Color.WHITE, VSObjectChromeDefaults.cardBackground(LIGHT));
      assertEquals(new Color(0x252428), VSObjectChromeDefaults.cardBackground(DARK));
      assertEquals("0xe6e0e9", VSObjectChromeDefaults.darkForegroundValue());
   }

   @Test
   void theDirectionColorsFollowFormatCss() throws Exception {
      writeFormatCss("ChartPalette[name='Modern-tokens'][index='2'] { color: #ff00ff; }\n"
                     + "ChartPalette[name='Modern Dark-tokens'][index='1'] { color: #00ff00; }");
      reload();

      assertEquals(new Color(0xff00ff), VSChartPaletteDefaults.directionDown(LIGHT));
      assertEquals(new Color(0x00ff00), VSChartPaletteDefaults.directionUp(DARK));
      // the untouched slots still come from defaults.css
      assertEquals(new Color(0x1C857B), VSChartPaletteDefaults.directionUp(LIGHT));
      assertEquals(new Color(0xE8954A), VSChartPaletteDefaults.directionDown(DARK));
   }

   @Test
   void theNegativeBarFollowsTheDirectionColor() throws Exception {
      Color before = VSObjectChromeDefaults.dataBarNegativeColor(LIGHT);
      writeFormatCss("ChartPalette[name='Modern-tokens'][index='2'] { color: #ff00ff; }");
      reload();

      assertNotEquals(before, VSObjectChromeDefaults.dataBarNegativeColor(LIGHT));
   }

   @Test
   void theCardBackgroundAndTheDarkTextFollowFormatCss() throws Exception {
      writeFormatCss("ChartPalette[name='Modern-tokens'][index='4'] { color: #f0f0f0; }\n"
                     + "ChartPalette[name='Modern Dark-tokens'][index='4'] { color: #101010; }\n"
                     + "ChartPalette[name='Modern Dark-tokens'][index='5'] { color: #ffeedd; }");
      reload();

      assertEquals(new Color(0xf0f0f0), VSObjectChromeDefaults.cardBackground(LIGHT));
      assertEquals(new Color(0x101010), VSObjectChromeDefaults.cardBackground(DARK));
      assertEquals("0xffeedd", VSObjectChromeDefaults.darkForegroundValue());
   }

   @Test
   void aBrokenRuleFallsBackToTheSeededValue() throws Exception {
      writeFormatCss("ChartPalette[name='Modern-tokens'][index='2a'] { color: #ff00ff; }");
      reload();

      assertEquals(new Color(0xAD6119), assertDoesNotThrow(
         () -> VSChartPaletteDefaults.directionDown(LIGHT)));
   }

   @Test
   void theTokenSetsAreNotOfferedAsPalettes() {
      assertFalse(ColorPalettes.getPaletteNames().contains("Modern-tokens"));
      assertFalse(ColorPalettes.getPaletteNames().contains("Modern Dark-tokens"));
      assertTrue(ColorPalettes.getPaletteNames().contains("Modern"));
   }

   private void writeFormatCss(String content) throws IOException {
      DataSpace space = DataSpace.getDataSpace();
      space.withOutputStream("portal", "format.css",
                              out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
   }

   // the ColorPalettes cache has no reset hook, so clear this org's entry and zero its stamp
   private void reload() throws Exception {
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
      VSChartPaletteDefaults.clearMemo();
      CSSDictionary.resetDictionaryCache();
   }
}
