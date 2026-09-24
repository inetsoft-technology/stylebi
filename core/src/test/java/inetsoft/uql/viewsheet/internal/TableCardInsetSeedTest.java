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
 * A marked table seeds a density-derived card inset, matching the chart's own card inset; an
 * unmarked one has none, which is what Revert has to restore. An author-set inset (userPadding)
 * is never substituted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCardInsetSeedTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void markedTableSeedsTheDensityCardInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(16, 16, 16, 16), info.getPadding());
   }

   @Test
   void eachTierSeedsItsOwnInset() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      TableVSAssemblyInfo compact = new TableVSAssemblyInfo();
      compact.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(new Insets(12, 12, 12, 12), compact.getPadding());

      SreeEnv.setProperty("viewsheet.density", "dense");
      TableVSAssemblyInfo dense = new TableVSAssemblyInfo();
      dense.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(new Insets(8, 8, 8, 8), dense.getPadding());
   }

   @Test
   void unmarkedTableHasNoCardInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
   }

   @Test
   void revertClearsASeededCardInset() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
   }

   @Test
   void authorSetInsetIsNeverSubstituted() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setUserPadding(true);
      info.setPadding(new Insets(2, 2, 2, 2));

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(2, 2, 2, 2), info.getPadding());
   }

   @Test
   void crosstabAndCalcTableSeedTheSameInset() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CrosstabVSAssemblyInfo crosstab = new CrosstabVSAssemblyInfo();
      CalcTableVSAssemblyInfo calc = new CalcTableVSAssemblyInfo();

      crosstab.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      calc.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(12, 12, 12, 12), crosstab.getPadding());
      assertEquals(new Insets(12, 12, 12, 12), calc.getPadding());
   }
}
