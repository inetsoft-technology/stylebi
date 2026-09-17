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

import inetsoft.graph.aesthetic.BluesColorFrame;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.SpectralColorFrame;
import inetsoft.graph.aesthetic.TealColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * seedColorPalette re-seeds every bound colour aesthetic so Modernize and Revert keep a chart's
 * colours in step with its mark. It has always known about CategoricalColorFrame; a chart's
 * measure-to-colour binding is a second colour surface (VSChartPaletteDefaults.defaultLinearFrame)
 * that the hook must extend to cover, without touching a ramp an author picked deliberately.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartVSAssemblyInfoSeedTest {
   private ChartVSAssemblyInfo newChart() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      return info;
   }

   private void bindColor(ChartVSAssemblyInfo info, VisualFrame frame) {
      VSChartInfo cinfo = info.getVSChartInfo();
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setVisualFrame(frame);
      cinfo.setColorField(colorRef);
   }

   private VisualFrame colorFrame(ChartVSAssemblyInfo info) {
      return info.getVSChartInfo().getColorField().getVisualFrame();
   }

   /**
    * Case 1: a modern-marked chart whose colour aesthetic holds TealColorFrame, reverted to an
    * unmarked context, ends on BluesColorFrame.
    */
   @Test
   void revertingAModernChartOnTealFallsBackToBlues() {
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.MODERN_LIGHT);
      bindColor(info, new TealColorFrame());

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertInstanceOf(BluesColorFrame.class, colorFrame(info),
                        "Revert must not leave the modern default seeded on a legacy chart");
   }

   /**
    * Case 2: an unmarked chart holding BluesColorFrame, modernized, ends on TealColorFrame.
    */
   @Test
   void modernizingAnUnmarkedChartOnBluesAdvancesToTeal() {
      ChartVSAssemblyInfo info = newChart();
      bindColor(info, new BluesColorFrame());

      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertInstanceOf(TealColorFrame.class, colorFrame(info),
                        "Modernize must not leave the legacy default seeded on a modern chart");
   }

   /**
    * Case 3: a modern-marked chart holding SpectralColorFrame, reverted, still holds
    * SpectralColorFrame - the author's choice survives. This is the case that matters most: it is
    * what stops the fix from becoming a data-loss bug on every chart saved before this slice,
    * whose linear frame's changed flag reads false whether or not the author chose it.
    */
   @Test
   void anAuthorsSpectralRampSurvivesRevert() {
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.MODERN_LIGHT);
      SpectralColorFrame spectral = new SpectralColorFrame();
      bindColor(info, spectral);

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertSame(spectral, colorFrame(info),
                 "an author's deliberately chosen ramp must not be replaced by Revert");
   }

   /**
    * Case 4: the existing categorical behaviour is unchanged - a chart with a
    * CategoricalColorFrame still gets its palette re-seeded.
    */
   @Test
   void categoricalPaletteIsStillReseededAlongsideTheLinearGuard() {
      ChartVSAssemblyInfo info = newChart();
      CategoricalColorFrame frame = new CategoricalColorFrame();
      bindColor(info, frame);

      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(VSChartPaletteDefaults.modernPalette()[0], frame.getDefaultColor(0),
                   "the pre-existing categorical branch must keep re-seeding");
   }
}
