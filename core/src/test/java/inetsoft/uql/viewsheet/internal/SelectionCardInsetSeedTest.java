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
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The selection family's card inset and cell padding seeds, and the precedence they must not break.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionCardInsetSeedTest {
   @Test
   void seedsTheTierInsetAtEachDensity() {
      assertEquals(new Insets(16, 16, 16, 16), seededInset("comfortable"));
      assertEquals(new Insets(12, 12, 12, 12), seededInset("compact"));
      assertEquals(new Insets(8, 8, 8, 8), seededInset("dense"));
   }

   @Test
   void seedsTheTierCellPaddingAtEachDensity() {
      assertEquals(new Insets(6, 8, 6, 8), seededCellPadding("comfortable"));
      assertEquals(new Insets(4, 6, 4, 6), seededCellPadding("compact"));
      assertEquals(new Insets(3, 4, 3, 4), seededCellPadding("dense"));
   }

   @Test
   void revertClearsBothValues() {
      SelectionListVSAssemblyInfo info = seeded("comfortable");
      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Insets(0, 0, 0, 0), info.getPadding(), "inset back to legacy zero");
      assertNull(info.getCellPadding(), "cell padding back to legacy absence");
   }

   @Test
   void aCssPaddingBeatsTheSeed() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setCellPadding(new Insets(1, 1, 1, 1), CompositeValue.Type.CSS);
      SreeEnv.setProperty("viewsheet.density", "comfortable");

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Insets(1, 1, 1, 1), info.getCellPadding(),
                   "CSS beats DEFAULT wholesale - the seed must not replace it or add to it");
   }

   @Test
   void anAuthorCellPaddingBeatsBoth() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setCellPadding(new Insets(1, 1, 1, 1), CompositeValue.Type.CSS);
      info.setCellPadding(new Insets(9, 9, 9, 9), CompositeValue.Type.USER);
      SreeEnv.setProperty("viewsheet.density", "comfortable");

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Insets(9, 9, 9, 9), info.getCellPadding());
   }

   private Insets seededInset(String density) {
      return seeded(density).getPadding();
   }

   private Insets seededCellPadding(String density) {
      return seeded(density).getCellPadding();
   }

   private SelectionListVSAssemblyInfo seeded(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
      return info;
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }
}
