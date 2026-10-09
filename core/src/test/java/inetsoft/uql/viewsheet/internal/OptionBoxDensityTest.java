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
class OptionBoxDensityTest {
   @BeforeEach
   void density() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   static Stream<Supplier<ListInputVSAssemblyInfo>> types() {
      return Stream.of(CheckBoxVSAssemblyInfo::new, RadioButtonVSAssemblyInfo::new);
   }

   private static ListInputVSAssemblyInfo marked(Supplier<ListInputVSAssemblyInfo> type,
                                                 int box, int row)
   {
      ListInputVSAssemblyInfo info = type.get();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(200, box));
      info.setCellHeight(row);
      return info;
   }

   private static void open(ListInputVSAssemblyInfo info) {
      info.seedChromeDefaults(VizContext.of(info));
   }

   private static void revert(ListInputVSAssemblyInfo info) {
      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.ofTransition(null, null));
   }

   private static TitledVSAssemblyInfo titled(ListInputVSAssemblyInfo info) {
      return (TitledVSAssemblyInfo) info;
   }

   private static int rows(ListInputVSAssemblyInfo info) {
      int content = info.getPixelSize().height - titled(info).getTitleHeight();
      return Math.max(1, content / info.getCellHeight());
   }

   @ParameterizedTest
   @MethodSource("types")
   void aNewMarkedBoxIsTheLaneOverOneRowAtEveryTier(Supplier<ListInputVSAssemblyInfo> type) {
      int[][] tiers = { { 20, 20, 40 }, { 26, 24, 50 }, { 30, 28, 58 } };
      String[] modes = { "dense", "compact", "comfortable" };

      for(int i = 0; i < modes.length; i++) {
         SreeEnv.setProperty("viewsheet.density", modes[i]);
         ListInputVSAssemblyInfo info = marked(type, 2 * AssetUtil.defh, AssetUtil.defh);

         open(info);

         assertEquals(tiers[i][0], titled(info).getTitleHeight(), modes[i] + " lane");
         assertEquals(tiers[i][1], info.getCellHeight(), modes[i] + " row");
         assertEquals(tiers[i][2], info.getPixelSize().height, modes[i] + " box");
         assertEquals(1, rows(info), modes[i] + " rows");
         assertEquals(AssetUtil.defh, info.getCellHeightValue(), "no tier row is stored");
      }
   }

   @ParameterizedTest
   @MethodSource("types")
   void todaysSeededBoxIsRewrittenOnOpen(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = marked(type, 60, 40);

      open(info);

      assertEquals(58, info.getPixelSize().height);
      assertEquals(AssetUtil.defh, info.getCellHeightValue());
      assertEquals(28, info.getCellHeight());
   }

   @ParameterizedTest
   @MethodSource("types")
   void todaysDenseBoxReturnsToTheLegacySize(Supplier<ListInputVSAssemblyInfo> type) {
      SreeEnv.setProperty("viewsheet.density", "dense");
      ListInputVSAssemblyInfo info = marked(type, 48, 28);

      open(info);

      assertEquals(40, info.getPixelSize().height);
      assertEquals(20, info.getCellHeight());
   }

   @ParameterizedTest
   @MethodSource("types")
   void anAuthorBoxKeepsItsSizeButLosesTodaysRow(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = marked(type, 140, 40);

      open(info);

      assertEquals(140, info.getPixelSize().height);
      assertEquals(28, info.getCellHeight());
   }

   @ParameterizedTest
   @MethodSource("types")
   void revertReturnsTheLegacyBoxAndRow(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo modern = marked(type, 40, 20);
      open(modern);
      revert(modern);

      assertEquals(40, modern.getPixelSize().height);
      assertEquals(20, titled(modern).getTitleHeight());
      assertEquals(20, modern.getCellHeight());

      ListInputVSAssemblyInfo neverOpened = marked(type, 60, 40);
      revert(neverOpened);

      assertEquals(40, neverOpened.getPixelSize().height, "today's pair reverts directly too");
      assertEquals(20, neverOpened.getCellHeightValue());
   }

   @ParameterizedTest
   @MethodSource("types")
   void aRestoreLeavesAnUnmarkedBoxAlone(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = type.get();
      info.setVizMark(null);
      info.setPixelSize(new Dimension(200, 56));
      info.setCellHeight(36);

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(56, info.getPixelSize().height);
      assertEquals(36, info.getCellHeightValue());
   }

   @ParameterizedTest
   @MethodSource("types")
   void flaggedValuesSurviveOpenDensityAndRevert(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = marked(type, 60, 40);
      info.setUserSize(true);
      info.setUserCellHeight(true);

      open(info);
      SreeEnv.setProperty("viewsheet.density", "dense");
      open(info);
      revert(info);

      assertEquals(60, info.getPixelSize().height);
      assertEquals(40, info.getCellHeightValue());
   }

   @ParameterizedTest
   @MethodSource("types")
   void anAuthorLaneIsKeptAndTheBoxGrowsAroundIt(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = marked(type, 40, 20);
      titled(info).setTitleHeightValue(40);
      titled(info).setUserTitleHeight(true);

      open(info);

      assertEquals(40, titled(info).getTitleHeight());
      assertEquals(68, info.getPixelSize().height);
   }

   @ParameterizedTest
   @MethodSource("types")
   void aDensityChangeMovesAFollowingBox(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = marked(type, 40, 20);
      open(info);
      assertEquals(58, info.getPixelSize().height);

      SreeEnv.setProperty("viewsheet.density", "compact");
      open(info);

      assertEquals(50, info.getPixelSize().height);
   }

   @ParameterizedTest
   @MethodSource("types")
   void theSizeHooks(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo following = marked(type, 58, 20);
      assertTrue(following.takesDensitySize());
      assertTrue(following.densitySizeIsHeightOnly());
      assertTrue(following.followsDensitySize());

      assertFalse(marked(type, 100, 20).followsDensitySize(), "an author box");

      ListInputVSAssemblyInfo flagged = marked(type, 140, 20);
      flagged.setUserSize(true);
      flagged.resetSize(VizContext.of(flagged));
      assertEquals(new Dimension(200, 58), flagged.getPixelSize());
      assertFalse(flagged.isUserSize());
   }

   @ParameterizedTest
   @MethodSource("types")
   void aFlaggedOrUnmarkedRowDoesNotResolve(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo flagged = marked(type, 58, 20);
      flagged.setUserCellHeight(true);
      assertEquals(20, flagged.getCellHeight());

      ListInputVSAssemblyInfo unmarked = type.get();
      unmarked.setVizMark(null);
      assertEquals(20, unmarked.getCellHeight());
   }
}
