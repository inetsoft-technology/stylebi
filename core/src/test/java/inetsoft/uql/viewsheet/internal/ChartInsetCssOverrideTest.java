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
import inetsoft.uql.viewsheet.ChartVSAssembly;
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
 * SeedChromeDefaultsTest has no CSS fixture machinery and its teardown only resets properties, so
 * this class writes (and always removes) a real dataspace file, modelled on
 * VSChartPaletteCssOverrideTest - the precedent for a live format.css in a test.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartInsetCssOverrideTest {
   @AfterEach
   void cleanup() throws Exception {
      DataSpace space = DataSpace.getDataSpace();

      if(space.exists("portal", "format.css")) {
         space.delete("portal", "format.css");
      }

      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      CSSDictionary.resetDictionaryCache();
   }

   @Test
   void aCssPaddingSurvivesTheCardInsetSeed() throws Exception {
      writeFormatCss("Chart { padding: 5px; }");
      CSSDictionary.resetDictionaryCache();
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();

      assertEquals(new Insets(5, 5, 5, 5),
                   chart.getVSAssemblyInfo().getPadding(),
                   "setCSSDefaults installs the CSS padding just before the seed runs, and the " +
                   "seed must leave it alone; the deleted equality test was providing this");
   }

   @Test
   void resetCardInsetReadsTheCssPaddingLiveRatherThanTrustingTheStaleField() throws Exception {
      // the padding pane's "follows default" checkbox clears userPadding and calls
      // resetCardInset; with a CSS padding still in force that must re-read the live CSS value,
      // not leave whatever stale author edit the field was holding
      writeFormatCss("Chart { padding: 5px; }");
      CSSDictionary.resetDictionaryCache();
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.initDefaultFormat();
      info.setUserPadding(true);
      info.setPadding(new Insets(20, 20, 20, 20));

      info.setUserPadding(false);
      info.resetCardInset(VizContext.of(info));

      assertEquals(new Insets(5, 5, 5, 5), info.getPadding(),
                   "the CSS padding wins over both the stale author edit and the modern seed");
   }

   private void writeFormatCss(String content) throws IOException {
      DataSpace space = DataSpace.getDataSpace();

      space.withOutputStream("portal", "format.css",
                             out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
   }
}
