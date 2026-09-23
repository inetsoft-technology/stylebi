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
import inetsoft.uql.CompositeValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked table seeds a density-derived cell padding; an unmarked one has none, which is what
 * Revert has to restore. Authorship rides on CompositeValue's USER tier rather than a boolean,
 * so an author value survives a reseed and clearing it hands the value back to the density.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCellPaddingSeedTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void markedTableSeedsTheDensityCellPadding() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(6, 8, 6, 8), info.getCellPadding());
   }

   @Test
   void eachTierSeedsItsOwnValue() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      TableVSAssemblyInfo compact = new TableVSAssemblyInfo();
      compact.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(new Insets(4, 6, 4, 6), compact.getCellPadding());

      SreeEnv.setProperty("viewsheet.density", "dense");
      TableVSAssemblyInfo dense = new TableVSAssemblyInfo();
      dense.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(new Insets(3, 4, 3, 4), dense.getCellPadding());
   }

   @Test
   void crosstabAndCalcTableSeedTheSameValue() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CrosstabVSAssemblyInfo crosstab = new CrosstabVSAssemblyInfo();
      CalcTableVSAssemblyInfo calc = new CalcTableVSAssemblyInfo();

      crosstab.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      calc.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(4, 6, 4, 6), crosstab.getCellPadding());
      assertEquals(new Insets(4, 6, 4, 6), calc.getCellPadding());
   }

   @Test
   void unmarkedTableHasNoCellPadding() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(info.getCellPadding());
   }

   @Test
   void revertClearsASeededCellPadding() {
      // Review Focus 5: Revert calls the seed with an unmarked context and needs the legacy
      // absence written, not left alone - otherwise a reverted table keeps modern spacing
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertNotNull(info.getCellPadding(), "seeded before revert");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(info.getCellPadding(), "cleared by revert");
   }

   @Test
   void authorValueSurvivesAReseed() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(new Insets(1, 2, 1, 2), CompositeValue.Type.USER);

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertTrue(info.isUserCellPadding());
      assertEquals(new Insets(1, 2, 1, 2), info.getCellPadding());
   }

   @Test
   void clearingTheAuthorValueHandsItBackToDensity() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      info.setCellPadding(new Insets(1, 2, 1, 2), CompositeValue.Type.USER);

      info.resetUserCellPadding();

      assertFalse(info.isUserCellPadding());
      assertEquals(new Insets(6, 8, 6, 8), info.getCellPadding());
   }
}
