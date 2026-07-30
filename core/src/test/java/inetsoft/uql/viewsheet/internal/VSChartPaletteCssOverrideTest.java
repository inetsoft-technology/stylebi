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

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorPalettes;
import inetsoft.util.DataSpace;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.*;
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
 * A live format.css override is the only way to prove modernPalette()/darkPalette() actually
 * read from CSS rather than the fallback constants - defaults.css declares the same colors the
 * fallback produces, so an assertion that passes on both paths proves nothing. This class writes
 * (and always removes) a real dataspace file, so it is isolated from VSChartPaletteDefaultsTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSChartPaletteCssOverrideTest {
   @AfterEach
   void cleanup() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      DataSpace space = DataSpace.getDataSpace();

      if(space.exists("portal", "format.css")) {
         space.delete("portal", "format.css");
      }

      forceReload();
      VSChartPaletteDefaults.clearMemo();
      CSSDictionary.resetDictionaryCache();

      // the org-scoped ColorPalettes cache has no reset hook of its own - it only refreshes when
      // the CSS stamp advances past its internal 'last' field. Confirm the reload above actually
      // picked the clean defaults.css back up, so this class does not poison later tests.
      assertEquals(new Color(0x00D4E8), VSChartPaletteDefaults.modernPalette()[0]);
   }

   // Only a CSS value that differs from MODERN_HEAD can prove resolution actually flipped to CSS -
   // defaults.css and the fallback agree everywhere else, so any assertion using shared values
   // would pass whether or not CSS is even consulted.
   @Test
   void modernPaletteFollowsFormatCssOverride() throws Exception {
      writeFormatCss("ChartPalette[name='Modern'][index='1'] { color: #ff00ff; }");
      forceReload();
      VSChartPaletteDefaults.clearMemo();
      CSSDictionary.resetDictionaryCache();

      Color[] modern = VSChartPaletteDefaults.modernPalette();

      assertEquals(new Color(0xff00ff), modern[0]);
      // untouched indexes still come from the CSS declaration, not a partial substitution
      assertEquals(new Color(0x00B87A), modern[1]);
   }

   // Reproduces the reported failure: a malformed index on one ChartPalette rule throws during
   // ColorPalettes' lazy load. Its load-failure catch never reaches paletteMap.put(...), so a
   // first-time load for an org leaves that org's map entry null and the next getPalette() call
   // NPEs on the missing entry. resolve() must degrade to the head constants instead of
   // propagating that failure into the chart render.
   @Test
   void modernPaletteFallsBackWhenCssResolutionThrows() throws Exception {
      writeFormatCss("ChartPalette[name='Modern'][index='1a'] { color: #ff00ff; }");
      forceReload();
      VSChartPaletteDefaults.clearMemo();
      CSSDictionary.resetDictionaryCache();

      Color[] modern = assertDoesNotThrow(VSChartPaletteDefaults::modernPalette);

      assertEquals(40, modern.length);
      assertEquals(new Color(0x00D4E8), modern[0]);
      assertEquals(new Color(0x64748B), modern[7]);
   }

   // pickerPalette() resolves the Default palette directly, bypassing resolve()/the memo
   // entirely, so it needs its own proof that the same guard covers it.
   @Test
   void pickerPaletteFallsBackWhenCssResolutionThrows() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      writeFormatCss("ChartPalette[name='Default'][index='1a'] { color: #ff00ff; }");
      forceReload();
      VSChartPaletteDefaults.clearMemo();
      CSSDictionary.resetDictionaryCache();

      Color[] picker = assertDoesNotThrow(VSChartPaletteDefaults::pickerPalette);

      assertEquals(40, picker.length);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[0], picker[0]);
   }

   private void writeFormatCss(String content) throws IOException {
      DataSpace space = DataSpace.getDataSpace();
      space.withOutputStream("portal", "format.css",
                              out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
   }

   // ColorPalettes caches per org with no reset hook, only refreshing once its internal
   // last-modified stamp is stale. Clearing the current org's entry and zeroing that stamp forces
   // the next getPalette() call to genuinely reload from CSS, regardless of the 10s staleness
   // window CSSDictionary keeps on top of it.
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
