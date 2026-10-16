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
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TextDensitySizeTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // created the way the composer creates one: the host's mark is stamped, then initDefaultFormat
   private static TextVSAssembly created(String density, VizMark mark) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity(density);
      vs.getVSAssemblyInfo().setVizMark(mark);
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.initDefaultFormat();
      vs.addAssembly(text);
      return text;
   }

   private static TextVSAssemblyInfo info(TextVSAssembly text) {
      return (TextVSAssemblyInfo) text.getVSAssemblyInfo();
   }

   // no viewsheet, so the context takes the org's density
   private static TextVSAssemblyInfo marked(String orgDensity, Dimension size) {
      SreeEnv.setProperty("viewsheet.density", orgDensity);
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(size);
      return info;
   }

   // a bottom-tabs strip sits under its children, so a child the density resizes has to move
   private static TabVSAssembly bottomTabs(Viewsheet vs, String... children) {
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setPixelOffset(new Point(78, 722));
      tabInfo.setPixelSize(new Dimension(400, 24));
      tabInfo.setBottomTabsValue(true);
      vs.addAssembly(tab);
      tab.setAssemblies(children);
      return tab;
   }

   @Test
   void aNewTextTakesItsTiersControlHeight() {
      assertEquals(new Dimension(100, 30),
                   info(created("comfortable", VizMark.MODERN_LIGHT)).getPixelSize());
      assertEquals(new Dimension(100, 28),
                   info(created("compact", VizMark.MODERN_LIGHT)).getPixelSize());
      assertEquals(new Dimension(100, 24),
                   info(created("dense", VizMark.MODERN_LIGHT)).getPixelSize());
   }

   @Test
   void aNewUnmarkedTextKeepsTheLegacyHeight() {
      assertEquals(new Dimension(100, 20), info(created("comfortable", null)).getPixelSize());
   }

   @Test
   void aNewTextTakesTheDashboardsDensityOverTheOrgs() {
      SreeEnv.setProperty("viewsheet.density", "dense");

      assertEquals(new Dimension(100, 30),
                   info(created("comfortable", VizMark.MODERN_LIGHT)).getPixelSize());
   }

   @Test
   void anAutoSizeTextTakesNoDensitySize() {
      TextVSAssemblyInfo info = marked("comfortable", new Dimension(100, 28));
      info.setAutoSizeValue(true);

      assertFalse(info.takesDensitySize());
      assertFalse(info.followsDensitySize());
      info.seedDensitySize(VizContext.of(info));
      assertEquals(new Dimension(100, 28), info.getPixelSize());
   }

   // the warning text turns Auto Size on at runtime before its format is initialized
   @Test
   void theWarningTextKeepsTheLegacyHeight() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      vs.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      assertEquals(20, vs.getWarningTextAssembly(true).getPixelSize().height);
   }

   // 20 is the legacy height, so a text saved before density sizing never reads as following
   @Test
   void onlyATierHeightFollows() {
      assertFalse(marked("compact", new Dimension(100, 20)).followsDensitySize());
      assertTrue(marked("compact", new Dimension(100, 24)).followsDensitySize());
      assertTrue(marked("compact", new Dimension(100, 28)).followsDensitySize());
      assertTrue(marked("compact", new Dimension(100, 30)).followsDensitySize());
      assertFalse(marked("compact", new Dimension(100, 40)).followsDensitySize());
   }

   @Test
   void anAuthorHeightDoesNotFollow() {
      TextVSAssemblyInfo info = marked("comfortable", new Dimension(100, 28));
      info.setUserSize(true);

      assertFalse(info.followsDensitySize());
      info.seedDensitySize(VizContext.of(info));
      assertEquals(new Dimension(100, 28), info.getPixelSize());
   }

   @Test
   void aDashboardDensityChangeMovesAFollowingTextAndKeepsItsWidth() {
      TextVSAssembly text = created("compact", VizMark.MODERN_LIGHT);
      info(text).setPixelSize(new Dimension(240, 28));
      Viewsheet vs = text.getViewsheet();

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(240, 30), info(text).getPixelSize());

      vs.getViewsheetInfo().setVizDensity("dense");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(240, 24), info(text).getPixelSize());
   }

   @Test
   void aDensityChangeLeavesAnAuthorHeightAlone() {
      TextVSAssembly text = created("compact", VizMark.MODERN_LIGHT);
      info(text).setPixelSize(new Dimension(100, 28));
      info(text).setUserSize(true);
      Viewsheet vs = text.getViewsheet();

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);

      assertEquals(new Dimension(100, 28), info(text).getPixelSize());
   }

   @Test
   void aDensityChangeLeavesALegacyHeightTextAlone() {
      TextVSAssembly text = created("compact", null);
      Viewsheet vs = text.getViewsheet();
      VizModernizeUtil.applyMark(vs, VizMark.MODERN_LIGHT);

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);

      assertEquals(new Dimension(100, 20), info(text).getPixelSize());
   }

   // accepted cost: an unflagged legacy text at a tier height reads as following once marked
   @Test
   void aLegacyTextAtATierHeightFollowsOnceModernized() {
      TextVSAssembly text = created("compact", null);
      info(text).setPixelSize(new Dimension(100, 28));
      Viewsheet vs = text.getViewsheet();

      VizModernizeUtil.applyMark(vs, VizMark.MODERN_LIGHT);
      assertEquals(new Dimension(100, 28), info(text).getPixelSize());

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(100, 30), info(text).getPixelSize());
   }

   @Test
   void aDensityChangeKeepsABottomTabsTextOnTheStrip() {
      TextVSAssembly text = created("compact", VizMark.MODERN_LIGHT);
      info(text).setPixelOffset(new Point(78, 722 - 28));
      Viewsheet vs = text.getViewsheet();
      bottomTabs(vs, text.getName());

      vs.getViewsheetInfo().setVizDensity("comfortable");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(100, 30), info(text).getPixelSize());
      assertEquals(new Point(78, 722 - 30), info(text).getPixelOffset());

      vs.getViewsheetInfo().setVizDensity("dense");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Point(78, 722 - 24), info(text).getPixelOffset());
   }

   @Test
   void aRestoreDoesNotResize() {
      TextVSAssemblyInfo info = marked("comfortable", new Dimension(100, 28));

      VizModernizeUtil.reseedAfterRestore(info);

      assertEquals(new Dimension(100, 28), info.getPixelSize());
   }

   @Test
   void modernizeAndRevertDoNotResize() {
      TextVSAssembly text = created("comfortable", null);
      Viewsheet vs = text.getViewsheet();

      VizModernizeUtil.applyMark(vs, VizMark.MODERN_LIGHT);
      assertEquals(new Dimension(100, 20), info(text).getPixelSize());

      info(text).setPixelSize(new Dimension(100, 30));
      VizModernizeUtil.revert(vs);
      assertEquals(new Dimension(100, 30), info(text).getPixelSize());
   }

   @Test
   void resetSizeReturnsTheTierHeightAndKeepsTheWidth() {
      TextVSAssemblyInfo info = marked("comfortable", new Dimension(240, 44));
      info.setUserSize(true);

      info.resetSize(VizContext.of(info));

      assertEquals(new Dimension(240, 30), info.getPixelSize());
      assertFalse(info.isUserSize());
   }

   @Test
   void onlyAHeightChangeIsTheAuthors() {
      TextVSAssemblyInfo info = marked("compact", new Dimension(100, 28));

      assertTrue(info.densitySizeIsHeightOnly());
      assertFalse(info.isAuthorSizeChange(new Dimension(100, 28), new Dimension(300, 28)));
      assertTrue(info.isAuthorSizeChange(new Dimension(100, 28), new Dimension(100, 40)));
   }
}
