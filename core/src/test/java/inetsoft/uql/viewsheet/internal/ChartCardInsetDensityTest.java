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

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The chart's card inset is seeded at creation and re-seeded on a density change, so it must
 * follow the density tier rather than the flat 12px it shipped at. An author-set inset
 * (userPadding) is never substituted, and an unmarked chart keeps the legacy 10px.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartCardInsetDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void comfortableSeedsTheWidestInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(16, 16, 16, 16), info.getPadding());
   }

   @Test
   void compactSeedsTheShippedInset() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(12, 12, 12, 12), info.getPadding());
   }

   @Test
   void denseSeedsTheTightestInset() {
      SreeEnv.setProperty("viewsheet.density", "dense");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(8, 8, 8, 8), info.getPadding());
   }

   @Test
   void unmarkedChartKeepsTheLegacyInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(new Insets(10, 10, 10, 10), info.getPadding());
   }

   @Test
   void authorSetInsetIsNeverSubstituted() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setUserPadding(true);
      info.setPadding(new Insets(4, 4, 4, 4));

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(4, 4, 4, 4), info.getPadding());
   }

   @Test
   void resetCardInsetFollowsTheCurrentDensity() {
      // the padding pane's follow-the-default checkbox calls this and nothing else
      SreeEnv.setProperty("viewsheet.density", "dense");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setPadding(new Insets(4, 4, 4, 4));

      info.resetCardInset(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(8, 8, 8, 8), info.getPadding());
   }
}
