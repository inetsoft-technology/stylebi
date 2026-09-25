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
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.DataSpace;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked table seeds a density-derived card inset, matching the chart's own card inset; an
 * unmarked one has none, which is what Revert has to restore. An author-set inset (userPadding)
 * is never substituted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@TestMethodOrder(MethodOrderer.MethodName.class)
class TableCardInsetSeedTest {
   @AfterEach
   void reset() throws Exception {
      SreeEnv.setProperty("viewsheet.density", null);
      SreeEnv.setProperty("viewsheet.modernVisualization", null);

      // reset before deleting: every seedChromeDefaults() call in this class - even the bare-info
      // ones with no CSS type set - reads through CSSDictionary.getDictionary() and caches a
      // dictionary with a live dataspace change listener on this same "portal"/format.css path.
      // Deleting the file while that listener is still registered fires it on a background
      // thread, which recreates an empty file - a stale recreation that can land after a later
      // test's own writeFormatCss and wipe out its content. Clearing the cache first unregisters
      // the listener so the delete is inert.
      CSSDictionary.resetDictionaryCache();

      DataSpace space = DataSpace.getDataSpace();

      if(space.exists("portal", "format.css")) {
         space.delete("portal", "format.css");
      }

      CSSDictionary.resetDictionaryCache();
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

   /**
    * The isCssPaddingDefined() half of seedChromeDefaults' guard has no table-side test: this is
    * the table counterpart to ChartInsetCssOverrideTest.aCssPaddingSurvivesTheCardInsetSeed, using
    * a Table selector rather than a Chart one. The default density (compact, unset here) would
    * seed (12, 12, 12, 12) if the guard's CSS half were missing, which is how this test would
    * catch that regression - the CSS value (5, 5, 5, 5) is a different number on every edge.
    */
   @Test
   void aTableCssPaddingSurvivesTheCardInsetSeed() throws Exception {
      // reset first, matching the @AfterEach ordering - see its comment. This method also runs
      // first in the class (@TestMethodOrder above), so this is belt-and-suspenders rather than
      // load-bearing, but it keeps the test correct even if a method is added ahead of it later.
      CSSDictionary.resetDictionaryCache();
      writeFormatCss(
         "Table { padding-top: 5px; padding-left: 5px; padding-bottom: 5px; padding-right: 5px; }");
      CSSDictionary.resetDictionaryCache();
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();

      assertEquals(new Insets(5, 5, 5, 5),
                   ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).getPadding(),
                   "setCSSDefaults installs the CSS padding just before the seed runs, and the " +
                   "seed must leave it alone");
   }

   private void writeFormatCss(String content) throws IOException {
      DataSpace space = DataSpace.getDataSpace();

      space.withOutputStream("portal", "format.css",
                             out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
   }
}
