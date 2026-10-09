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
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ControlDensitySizeTest {
   @BeforeEach
   void density() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   static Stream<Supplier<VSAssemblyInfo>> controls() {
      return Stream.of(ComboBoxVSAssemblyInfo::new, TextInputVSAssemblyInfo::new,
                       SpinnerVSAssemblyInfo::new, SubmitVSAssemblyInfo::new);
   }

   private static VSAssemblyInfo marked(Supplier<VSAssemblyInfo> type, int height) {
      VSAssemblyInfo info = type.get();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(100, height));
      return info;
   }

   @ParameterizedTest
   @MethodSource("controls")
   void aDensityChangeMovesAnUnflaggedControlAndLeavesAFlaggedOne(Supplier<VSAssemblyInfo> type) {
      VSAssemblyInfo following = marked(type, 30);
      VSAssemblyInfo authored = marked(type, 30);
      authored.setUserSize(true);

      SreeEnv.setProperty("viewsheet.density", "dense");
      following.seedChromeDefaults(VizContext.of(following));
      authored.seedChromeDefaults(VizContext.of(authored));

      assertEquals(24, following.getPixelSize().height);
      assertEquals(30, authored.getPixelSize().height);
   }

   @ParameterizedTest
   @MethodSource("controls")
   void revertKeepsAFlaggedHeightAndReturnsAnUnflaggedOne(Supplier<VSAssemblyInfo> type) {
      VSAssemblyInfo following = marked(type, 28);
      VSAssemblyInfo authored = marked(type, 28);
      authored.setUserSize(true);

      for(VSAssemblyInfo info : new VSAssemblyInfo[] { following, authored }) {
         info.setVizMark(null);
         info.seedChromeDefaults(VizContext.ofTransition(null, null));
      }

      assertEquals(AssetUtil.defh, following.getPixelSize().height);
      assertEquals(28, authored.getPixelSize().height);
   }

   @ParameterizedTest
   @MethodSource("controls")
   void aRestoreLeavesAnUnmarkedControlAlone(Supplier<VSAssemblyInfo> type) {
      VSAssemblyInfo info = type.get();
      info.setVizMark(null);
      info.setPixelSize(new Dimension(100, 28));

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(28, info.getPixelSize().height, "a legacy author height survives an open");
   }

   @ParameterizedTest
   @MethodSource("controls")
   void followsDensitySizeRecognisesExactlyTheRuleHeights(Supplier<VSAssemblyInfo> type) {
      for(int h : new int[] { 20, 24, 28, 30 }) {
         assertTrue(marked(type, h).followsDensitySize(), "height " + h);
      }

      assertFalse(marked(type, 40).followsDensitySize(), "an author height");

      VSAssemblyInfo flagged = marked(type, 30);
      flagged.setUserSize(true);
      assertFalse(flagged.followsDensitySize(), "a flagged height");
   }

   @ParameterizedTest
   @MethodSource("controls")
   void resetSizeWritesTheTierHeightAndKeepsTheWidth(Supplier<VSAssemblyInfo> type) {
      VSAssemblyInfo info = marked(type, 40);
      info.setPixelSize(new Dimension(300, 40));
      info.setUserSize(true);

      info.resetSize(VizContext.of(info));

      assertEquals(new Dimension(300, 30), info.getPixelSize());
      assertFalse(info.isUserSize());
   }

   @ParameterizedTest
   @MethodSource("controls")
   void onlyAHeightChangeIsTheAuthors(Supplier<VSAssemblyInfo> type) {
      VSAssemblyInfo info = type.get();

      assertTrue(info.takesDensitySize());
      assertTrue(info.densitySizeIsHeightOnly());
      assertFalse(info.isAuthorSizeChange(new Dimension(100, 30), new Dimension(300, 30)));
      assertTrue(info.isAuthorSizeChange(new Dimension(100, 30), new Dimension(100, 40)));
   }

   @Test
   void aBoxTypeTakesEitherDimension() {
      SelectionListVSAssemblyInfo list = new SelectionListVSAssemblyInfo();

      assertFalse(list.densitySizeIsHeightOnly());
      assertTrue(list.isAuthorSizeChange(new Dimension(100, 120), new Dimension(120, 120)));
      assertFalse(list.isAuthorSizeChange(new Dimension(100, 120), new Dimension(100, 120)));
   }
}
