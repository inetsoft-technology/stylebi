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
package inetsoft.web.binding;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorPalettes;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.css.CSSDictionary;
import inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel;
import inetsoft.web.binding.service.graph.aesthetic.ColorFrameModelFactory;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactory;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSChartBindingColorPalettesTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      CSSDictionary.resetDictionaryCache();
   }

   @Test
   void hiddenIsOffByDefaultOnTheModel() {
      assertFalse(new CategoricalColorModel().isHidden(),
                  "a palette is visible unless the server says otherwise");
   }

   // The dialog matches a chart's palette by colour list, so an omitted palette would match
   // nothing, fall back to Default, and repaint the chart on a no-op OK.
   @Test
   void nullVsIdFlagsTheNineHiddenNamesAndOmitsNothing() throws Exception {
      VSChartBindingController controller = newController();
      int total = ColorPalettes.getPaletteNames().size();

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      CategoricalColorModel[] modernPalettes =
         controller.getColorPalettes(null, null, null, null);
      assertEquals(total, modernPalettes.length, "no palette may be omitted, hidden or not");

      Set<String> hiddenNames =
         VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of(VizMark.MODERN_LIGHT));
      long hiddenCount = Arrays.stream(modernPalettes).filter(CategoricalColorModel::isHidden).count();
      assertEquals(9, hiddenCount, "exactly the nine retired palettes must be flagged hidden");

      for(CategoricalColorModel model : modernPalettes) {
         assertEquals(hiddenNames.contains(model.getName()), model.isHidden(),
                      model.getName() + " hidden flag must match hiddenPaletteNames");
      }
   }

   @Test
   void nullVsIdLeavesEveryPaletteVisibleWhenGateIsOff() throws Exception {
      VSChartBindingController controller = newController();
      int total = ColorPalettes.getPaletteNames().size();

      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      CategoricalColorModel[] classicPalettes =
         controller.getColorPalettes(null, null, null, null);

      assertEquals(total, classicPalettes.length, "no palette may be omitted, hidden or not");
      assertTrue(Arrays.stream(classicPalettes).noneMatch(CategoricalColorModel::isHidden),
                 "a classic chart is offered every palette");
   }

   // Builds the endpoint with only the factory its own code path exercises - it only ever
   // wraps ColorPalettes.getPalette() results, which are always CategoricalColorFrame - so no
   // Spring context or cluster wiring is needed to reach the real flag-attaching loop.
   private VSChartBindingController newController() {
      List<VisualFrameModelFactory<?, ?>> factories =
         List.of(new ColorFrameModelFactory.CategoricalColorFactory());
      VisualFrameModelFactoryService visualService = new VisualFrameModelFactoryService(factories);
      return new VSChartBindingController(visualService, null);
   }
}
