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
import inetsoft.uql.viewsheet.*;
import inetsoft.util.DataSpace;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Insets;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RangeSliderDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
   }

   private static TimeSliderVSAssemblyInfo marked(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.initDefaultFormat();
      return info;
   }

   @ParameterizedTest
   @CsvSource({ "comfortable, 16", "compact, 12", "dense, 8" })
   void aMarkedSliderTakesTheCardPadding(String density, int inset) {
      assertEquals(new Insets(inset, inset, inset, inset), marked(density).getPadding());
   }

   @Test
   void anUnmarkedSliderHasNoPadding() {
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(null);
      info.initDefaultFormat();
      assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
   }

   // a format.css padding is stored on creation; legacy renderers never drew it
   @Test
   void anUnmarkedSliderDrawsNoStoredPadding() {
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(null);
      info.initDefaultFormat();
      info.setPadding(new Insets(5, 5, 5, 5));
      assertEquals(new Insets(0, 0, 0, 0), info.getBodyPadding());
   }

   @Test
   void anUnmarkedSliderDrawsItsAuthorPadding() {
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(null);
      info.initDefaultFormat();
      info.setPadding(new Insets(5, 5, 5, 5));
      info.setUserPadding(true);
      assertEquals(new Insets(5, 5, 5, 5), info.getBodyPadding());
   }

   @Test
   void aMarkedSliderKeepsAFormatCssPadding() throws Exception {
      CSSDictionary.resetDictionaryCache();
      DataSpace.getDataSpace().withOutputStream(
         "portal", "format.css",
         out -> out.write("RangeSlider { padding: 5px; }".getBytes(StandardCharsets.UTF_8)));
      CSSDictionary.resetDictionaryCache();

      try {
         TimeSliderVSAssemblyInfo info = marked("comfortable");
         assertEquals(new Insets(5, 5, 5, 5), info.getPadding());
         assertEquals(new Insets(5, 5, 5, 5), info.getBodyPadding());
      }
      finally {
         // reset before deleting, so the dictionary's file listener cannot recreate it
         CSSDictionary.resetDictionaryCache();
         DataSpace space = DataSpace.getDataSpace();

         if(space.exists("portal", "format.css")) {
            space.delete("portal", "format.css");
         }

         CSSDictionary.resetDictionaryCache();
      }
   }

   @Test
   void anAuthorPaddingSurvivesAReseed() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setPadding(new Insets(0, 0, 0, 0));
      info.setUserPadding(true);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
   }

   @Test
   void revertWritesZeroPaddingAndNoCorner() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.ofTransition(null, null));
      assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
      assertEquals(0, info.getFormat().getDefaultFormat().getRoundCornerValue());
   }

   @Test
   void aMarkedSliderTakesTheCardCorner() {
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   marked("comfortable").getFormat().getDefaultFormat().getRoundCornerValue());
   }

   @ParameterizedTest
   @CsvSource({ "comfortable, 82", "compact, 74", "dense, 66" })
   void aMarkedSlidersBodyIsContentPlusPadding(String density, int body) {
      assertEquals(body, marked(density).getSliderBodyHeight());
   }

   @Test
   void anAuthorPaddingCountsInTheBody() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setPadding(new Insets(0, 16, 0, 16));
      assertEquals(50, info.getSliderBodyHeight());
   }

   @Test
   void anUnmarkedSlidersBodyIsItsListHeight() {
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(null);
      info.setListHeight(2);
      assertEquals(2 * AssetUtil.defh, info.getSliderBodyHeight());
   }

   @Test
   void anAdhocSliderReadsNoPaddingAndNoSpacing() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setAdhocFilter(true);
      assertEquals(new Insets(0, 0, 0, 0), info.getBodyPadding());
      assertFalse(info.takesLabelSpacing());
      info.setAdhocFilter(false);
      assertEquals(new Insets(16, 16, 16, 16), info.getBodyPadding());
      assertTrue(info.takesLabelSpacing());
   }

   // an ad hoc filter saved or bookmarked as one reopens through the reseed
   @Test
   void aReseededAdhocSliderStaysSquare() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setAdhocFilter(true);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(0, info.getFormat().getDefaultFormat().getRoundCornerValue());

      info.setAdhocFilter(false);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   info.getFormat().getDefaultFormat().getRoundCornerValue());
   }

   @ParameterizedTest
   @CsvSource({ "comfortable, 232, 82", "compact, 224, 74", "dense, 216, 66" })
   void aNewMarkedSliderTakesTheTierBox(String density, int w, int h) {
      assertEquals(new Dimension(w, h), marked(density).getPixelSize());
   }

   @Test
   void modernizingAWidenedLegacySliderTakesTheTierHeightAtItsWidth() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(null);
      info.setPixelSize(new Dimension(400, 40));
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.ofTransition(null, VizMark.MODERN_LIGHT));
      assertEquals(new Dimension(400, 82), info.getPixelSize());
   }

   @Test
   void aDensityChangeMovesAWidenedSlidersHeightOnly() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setPixelSize(new Dimension(400, 82));
      SreeEnv.setProperty("viewsheet.density", "compact");
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(new Dimension(400, 74), info.getPixelSize());
   }

   @Test
   void anAuthorHeightIsKept() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setPixelSize(new Dimension(232, 45));
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(45, info.getPixelSize().height);
   }

   @Test
   void aFlaggedSizeIsKept() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setUserSize(true);
      SreeEnv.setProperty("viewsheet.density", "dense");
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(new Dimension(232, 82), info.getPixelSize());
   }

   @Test
   void revertReturnsTheLegacyHeightAndKeepsAnAuthorWidth() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setPixelSize(new Dimension(400, 82));
      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.ofTransition(null, null));
      assertEquals(new Dimension(400, 40), info.getPixelSize());
   }

   @ParameterizedTest
   @CsvSource({ "400, 55", "200, 40", "400, 82", "232, 82" })
   void aRestoreLeavesAnUnmarkedSliderAlone(int w, int h) {
      SreeEnv.setProperty("viewsheet.density", "compact");
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(null);
      info.setPixelSize(new Dimension(w, h));
      info.seedChromeDefaults(VizContext.of((VizMark) null));
      assertEquals(new Dimension(w, h), info.getPixelSize());
   }

   @Test
   void resetSizeWritesTheTierHeightAndKeepsAnAuthorWidth() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setPixelSize(new Dimension(400, 45));
      info.setUserSize(true);
      info.resetSize(VizContext.of(info));
      assertEquals(new Dimension(400, 82), info.getPixelSize());
      assertFalse(info.isUserSize());
   }

   @Test
   void onlyAHeightChangeIsTheAuthors() {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      assertFalse(info.isAuthorSizeChange(new Dimension(232, 82), new Dimension(400, 82)));
      assertTrue(info.isAuthorSizeChange(new Dimension(232, 82), new Dimension(232, 90)));
   }

   @Test
   void aContainedSliderIsNotResized() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      container.setAssemblies(new String[] { "RangeSlider1" });
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "RangeSlider1");
      vs.addAssembly(slider);
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(232, 40));
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(new Dimension(232, 40), info.getPixelSize());
      assertEquals(new Insets(16, 16, 16, 16), info.getPadding(), "the padding still seeds");
   }

   // RangeSlider1 in a selection container, on a dashboard pinned to a density
   private static TimeSliderVSAssemblyInfo containedSlider(Viewsheet vs, VizMark mark, int height) {
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      container.setAssemblies(new String[] { "RangeSlider1" });
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "RangeSlider1");
      vs.addAssembly(slider);
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      info.setVizMark(mark);
      info.initDefaultFormat();
      info.setPixelSize(new Dimension(200, height));
      return info;
   }

   private static Viewsheet sheet(String density) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity(density);
      return vs;
   }

   @Test
   void modernizeRestoresAnExpandedContainedSlidersBody() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = sheet("comfortable");
      TimeSliderVSAssemblyInfo info = containedSlider(vs, null, 40);
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      VizModernizeUtil.modernize(vs);

      assertNotNull(info.getVizMark(), "premise: the slider was modernized");
      assertEquals(new Dimension(200, 82), info.getPixelSize());
   }

   @Test
   void aDensityChangeRestoresAnExpandedContainedSlidersBody() {
      Viewsheet vs = sheet("dense");
      TimeSliderVSAssemblyInfo info = containedSlider(vs, VizMark.MODERN_LIGHT, 66);
      vs.getViewsheetInfo().setVizDensity("comfortable");

      VizModernizeUtil.reseed(vs);

      assertEquals(new Dimension(200, 82), info.getPixelSize());
   }

   @Test
   void revertRestoresAnExpandedContainedSlidersLegacyBody() {
      Viewsheet vs = sheet("comfortable");
      TimeSliderVSAssemblyInfo info = containedSlider(vs, VizMark.MODERN_LIGHT, 82);

      VizModernizeUtil.revert(vs);

      assertNull(info.getVizMark(), "premise: the slider was reverted");
      assertEquals(new Dimension(200, 60), info.getPixelSize(), "list height 40 plus the lane");
   }

   @Test
   void aCollapsedContainedSliderKeepsItsLane() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = sheet("comfortable");
      TimeSliderVSAssemblyInfo info = containedSlider(vs, null, AssetUtil.defh);
      info.setHidden(true);
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      VizModernizeUtil.modernize(vs);
      assertEquals(new Dimension(200, AssetUtil.defh), info.getPixelSize(), "modernize");

      vs.getViewsheetInfo().setVizDensity("dense");
      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(200, AssetUtil.defh), info.getPixelSize(), "density change");

      VizModernizeUtil.revert(vs);
      assertEquals(new Dimension(200, AssetUtil.defh), info.getPixelSize(), "revert");
   }

   // only a slider whose mark moved, or a marked one on a density change, is re-stored
   @Test
   void anUnmarkedContainedSliderKeepsItsBodyThroughADensityChangeAndARevert() {
      Viewsheet vs = sheet("comfortable");
      TimeSliderVSAssemblyInfo info = containedSlider(vs, null, 40);
      ((CurrentSelectionVSAssembly) vs.getAssembly("CurrentSelection1")).getVSAssemblyInfo()
         .setVizMark(VizMark.MODERN_LIGHT);

      VizModernizeUtil.reseed(vs);
      assertEquals(new Dimension(200, 40), info.getPixelSize(), "density change");

      assertTrue(VizModernizeUtil.revert(vs) > 0, "premise: the container was reverted");
      assertEquals(new Dimension(200, 40), info.getPixelSize(), "revert");
   }
}
