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
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79565: an author-sized selection box is not grown for the card inset, so it must not take
 * a top and bottom inset either, or the inset comes out of the rows the author sized it for.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionAuthorSizeInsetTest {
   // the Census example's lists: 88px high, cell 31, title 18, none of them flagged as user-set
   @ParameterizedTest
   @CsvSource({ "dense,8", "compact,12", "comfortable,16" })
   void anAuthorSizedListKeepsItsContentArea(String density, int side) {
      SelectionListVSAssemblyInfo info = census(new SelectionListVSAssemblyInfo(), density);

      assertEquals(new Insets(0, side, 0, side), info.getPadding());
      assertEquals(new Dimension(344, 88), info.getPixelSize());
      assertEquals(31, info.getEffectiveCellHeight());
   }

   @ParameterizedTest
   @CsvSource({ "dense,8", "compact,12", "comfortable,16" })
   void anAuthorSizedTreeKeepsItsContentArea(String density, int side) {
      SelectionTreeVSAssemblyInfo info = census(new SelectionTreeVSAssemblyInfo(), density);

      assertEquals(new Insets(0, side, 0, side), info.getPadding());
      assertEquals(new Dimension(344, 88), info.getPixelSize());
   }

   @ParameterizedTest
   @CsvSource({ "dense,8", "compact,12", "comfortable,16" })
   void aDefaultSizeListStillTakesTheFullInsetAndTierBox(String density, int inset) {
      SreeEnv.setProperty("viewsheet.density", density);
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      VizContext ctx = VizContext.of(info);

      info.seedChromeDefaults(ctx);

      assertEquals(new Insets(inset, inset, inset, inset), info.getPadding());
      assertEquals(VSDensityDefaults.selectionSize(ctx), info.getPixelSize());
   }

   @Test
   void aReseedAtAnotherDensityStaysVerticallyFlat() {
      SelectionListVSAssemblyInfo info = census(new SelectionListVSAssemblyInfo(), "compact");
      SreeEnv.setProperty("viewsheet.density", "comfortable");

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Insets(0, 16, 0, 16), info.getPadding());
      assertEquals(new Dimension(344, 88), info.getPixelSize());
   }

   @Test
   void revertStillRestoresTheLegacyZeroInset() {
      SelectionListVSAssemblyInfo info = census(new SelectionListVSAssemblyInfo(), "compact");
      info.setVizMark(null);

      info.seedChromeDefaults(VizContext.ofTransition(null, null));

      assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
      assertEquals(new Dimension(344, 88), info.getPixelSize());
   }

   @Test
   void anAuthorPaddingIsStillKept() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setPixelSize(new Dimension(344, 88));
      info.setPadding(new Insets(5, 5, 5, 5));
      info.setUserPadding(true);
      info.setVizMark(VizMark.MODERN_LIGHT);

      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(new Insets(5, 5, 5, 5), info.getPadding());
   }

   private <T extends SelectionBaseVSAssemblyInfo> T census(T info, String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      info.setPixelSize(new Dimension(344, 88));
      info.setCellHeight(31);
      info.setTitleHeight(18);
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
      return info;
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }
}
