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
package inetsoft.report.composition.graph;

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
// Verifies the resolver contract the VGraphPair categorical seam relies on (gate on/off), not the
// seam wiring itself — a true seam test needs a live ViewsheetSandbox + full VGraph render, so the
// live/export seam behavior is verified manually.
class VGraphPairModernPaletteTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.modernChartPalette", null);
   }

   @Test
   void gateOnSwapsCategoricalFrameToModernHead() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      CategoricalColorFrame frame = new CategoricalColorFrame();
      // same resolver call the VGraphPair seam makes after setParentParams
      inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults.applyModernPalette(frame);
      assertEquals(new Color(0x00D4E8), frame.getColor(0));
   }

   @Test
   void gateOffLeavesLegacyPalette() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      CategoricalColorFrame frame = new CategoricalColorFrame();
      inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults.applyModernPalette(frame);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[0], frame.getColor(0));
   }
}
