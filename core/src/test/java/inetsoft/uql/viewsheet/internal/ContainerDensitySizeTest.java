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

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ContainerDensitySizeTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private Dimension seeded(String density, VizMark mark, Dimension start) {
      SreeEnv.setProperty("viewsheet.density", density);
      CurrentSelectionVSAssemblyInfo info = new CurrentSelectionVSAssemblyInfo();
      info.setVizMark(mark);
      info.setPixelSize(start);
      info.seedChromeDefaults(VizContext.of(info));
      return info.getPixelSize();
   }

   @Test
   void aFreshContainerTakesTwelveLanesAtEachTier() {
      Dimension legacy = new Dimension(300, 240);
      assertEquals(new Dimension(300, 360), seeded("comfortable", VizMark.MODERN_LIGHT, legacy));
      assertEquals(new Dimension(300, 312), seeded("compact", VizMark.MODERN_LIGHT, legacy));
      assertEquals(new Dimension(300, 240), seeded("dense", VizMark.MODERN_LIGHT, legacy));
   }

   @Test
   void anAuthorSizeIsLeftAlone() {
      assertEquals(new Dimension(300, 500),
                   seeded("comfortable", VizMark.MODERN_LIGHT, new Dimension(300, 500)));
   }

   @Test
   void aDensityChangeMovesASeededSizeToTheNewTier() {
      assertEquals(new Dimension(300, 312),
                   seeded("compact", VizMark.MODERN_LIGHT, new Dimension(300, 360)));
   }

   @Test
   void revertRestoresTheLegacySize() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo info = new CurrentSelectionVSAssemblyInfo();
      info.setPixelSize(new Dimension(300, 360));
      info.setVizMark(null);

      info.seedChromeDefaults(VizContext.ofTransition(null, null));

      assertEquals(new Dimension(300, 240), info.getPixelSize());
   }

   // every open re-runs the seed; an unmarked container that happens to be tier-sized keeps its box
   @Test
   void openingAnUnmarkedContainerLeavesATierSizedBoxAlone() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      CurrentSelectionVSAssemblyInfo info = new CurrentSelectionVSAssemblyInfo();
      info.setPixelSize(new Dimension(300, 360));
      info.setVizMark(null);

      VizModernizeUtil.reseedAfterRestore(info);

      assertEquals(new Dimension(300, 360), info.getPixelSize());
   }

   @Test
   void theRecognizerAcceptsOnlySeededSizes() {
      assertTrue(VSDensityDefaults.isSeededContainerSize(new Dimension(300, 240)));
      assertTrue(VSDensityDefaults.isSeededContainerSize(new Dimension(300, 312)));
      assertTrue(VSDensityDefaults.isSeededContainerSize(new Dimension(300, 360)));
      assertFalse(VSDensityDefaults.isSeededContainerSize(new Dimension(300, 361)));
      assertFalse(VSDensityDefaults.isSeededContainerSize(new Dimension(320, 360)));
      assertFalse(VSDensityDefaults.isSeededContainerSize(null));
   }
}
