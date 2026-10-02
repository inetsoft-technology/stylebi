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

/**
 * Verifies the default selection list/tree size follows density so five data rows stay visible at
 * every tier, and that the recognition predicate accepts exactly the sizes the seed can produce.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionSizeMatrixTest {
   @Test
   void theSizeMatrixHoldsFiveRowsAtEveryTier() {
      assertEquals(new Dimension(132, 202), sizeAt("comfortable"));
      assertEquals(new Dimension(124, 170), sizeAt("compact"));
      assertEquals(new Dimension(116, 136), sizeAt("dense"));
   }

   @Test
   void aLegacyContextKeepsTheLegacySize() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      assertEquals(new Dimension(100, 120),
                   VSDensityDefaults.selectionSize(VizContext.of((VSAssemblyInfo) null)));
   }

   @Test
   void theRecognitionPredicateAcceptsOnlySeededSizes() {
      assertTrue(VSDensityDefaults.isSeededSelectionSize(new Dimension(100, 120)), "legacy");
      assertTrue(VSDensityDefaults.isSeededSelectionSize(new Dimension(132, 202)), "comfortable");
      assertTrue(VSDensityDefaults.isSeededSelectionSize(new Dimension(124, 170)), "compact");
      assertTrue(VSDensityDefaults.isSeededSelectionSize(new Dimension(116, 136)), "dense");

      assertFalse(VSDensityDefaults.isSeededSelectionSize(new Dimension(300, 400)), "author size");
      assertFalse(VSDensityDefaults.isSeededSelectionSize(new Dimension(132, 203)), "one off the tier");
      assertFalse(VSDensityDefaults.isSeededSelectionSize(new Dimension(202, 132)), "axes swapped");
   }

   private Dimension sizeAt(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      return VSDensityDefaults.selectionSize(VizContext.of(VizMark.MODERN_LIGHT));
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }
}
