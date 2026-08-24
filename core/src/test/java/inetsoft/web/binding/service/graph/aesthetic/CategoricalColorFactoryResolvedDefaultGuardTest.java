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
package inetsoft.web.binding.service.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults;
import inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the real seam: VisualFrameModelFactoryService.updateVisualFrameWrapper always hands
 * CategoricalColorFactory.updateVisualFrameWrapper0 a fresh wrapper (shouldRefresh returns true
 * unconditionally for CategoricalColorFrameWrapper, bug #19230), never the chart's live one. The
 * factory's write guard must therefore compare the submitted colour against the model's own
 * reported tiers, not the fresh wrapper's - otherwise every colour that merely differs from the
 * legacy palette gets pinned into the USER tier and survives Revert.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CategoricalColorFactoryResolvedDefaultGuardTest {
   @Test
   void anUntouchedModernPaletteIsNotPinned() {
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      CategoricalColorFrame frame = (CategoricalColorFrame) wrapper.getVisualFrame();
      frame.setDefaultColors(VSChartPaletteDefaults.modernPalette());

      CategoricalColorModel model = new CategoricalColorModel(wrapper);
      CategoricalColorFrameWrapper updated = newService().updateVisualFrameWrapper(wrapper, model);

      assertTrue(updated.getUserColors().isEmpty(),
                 "colours that echo the resolved default must not be pinned into the user tier");
   }

   @Test
   void aColourTheUserActuallyChangedIsStillPinned() {
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      CategoricalColorFrame frame = (CategoricalColorFrame) wrapper.getVisualFrame();
      frame.setDefaultColors(VSChartPaletteDefaults.modernPalette());

      CategoricalColorModel model = new CategoricalColorModel(wrapper);
      String[] colors = model.getColors().clone();
      // neither the modern head (0x3B82F6) nor the legacy palette (0xfde3a7) value at index 5
      colors[5] = "#123456";
      model.setColors(colors);

      CategoricalColorFrameWrapper updated = newService().updateVisualFrameWrapper(wrapper, model);

      assertEquals(1, updated.getUserColors().size(), "only the changed index is pinned");
      assertEquals(new Color(0x123456), updated.getUserColors().get(5));
   }

   @Test
   void resetProducedColorsClearAnExistingOverride() {
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      wrapper.setUserColor(0, Color.MAGENTA); // a stray override from an earlier Apply

      CategoricalColorModel model = new CategoricalColorModel(wrapper);
      String[] cssColors = model.getCssColors();
      String[] defaultColors = model.getDefaultColors();
      String[] resetColors = new String[model.getColors().length];

      // what the colour pane's reset() produces: colors[i] = cssColors[i] || defaultColors[i]
      for(int i = 0; i < resetColors.length; i++) {
         String css = cssColors[i];
         resetColors[i] = css != null && !css.isEmpty() ? css : defaultColors[i];
      }

      model.setColors(resetColors);
      CategoricalColorFrameWrapper updated = newService().updateVisualFrameWrapper(wrapper, model);

      assertTrue(updated.getUserColors().isEmpty(),
                 "reset-produced colours resolve to the default tier, so the fresh wrapper's " +
                 "user tier stays empty with no clear-user-colors flag needed");
   }

   /**
    * The service always substitutes a fresh wrapper, and a fresh CategoricalColorFrame carries the
    * stock legacy palette. Without putting the model's palette back, an ordinary apply resets a
    * modern chart's palette to legacy - invisible in the plot, because the render re-applies the
    * modern palette, but the colour picker then offers legacy swatches.
    */
   @Test
   void anApplyKeepsTheChartsOwnPalette() {
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      CategoricalColorFrame frame = (CategoricalColorFrame) wrapper.getVisualFrame();
      frame.setDefaultColors(VSChartPaletteDefaults.modernPalette());

      CategoricalColorModel model = new CategoricalColorModel(wrapper);
      CategoricalColorFrameWrapper updated = newService().updateVisualFrameWrapper(wrapper, model);

      assertEquals(VSChartPaletteDefaults.modernPalette()[0], updated.getDefaultColor(0),
                   "the palette survives the wrapper substitution");
      assertEquals(VSChartPaletteDefaults.modernPalette()[3], updated.getDefaultColor(3));
   }

   @Test
   void anApplyKeepsALegacyChartsPalette() {
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      CategoricalColorModel model = new CategoricalColorModel(wrapper);
      CategoricalColorFrameWrapper updated = newService().updateVisualFrameWrapper(wrapper, model);

      assertEquals(CategoricalColorFrame.COLOR_PALETTE[0], updated.getDefaultColor(0),
                   "and a legacy chart is equally unchanged");
   }

   private VisualFrameModelFactoryService newService() {
      List<VisualFrameModelFactory<?, ?>> factories = new ArrayList<>();
      factories.add(new ColorFrameModelFactory.CategoricalColorFactory());
      return new VisualFrameModelFactoryService(factories);
   }
}
