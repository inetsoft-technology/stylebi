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
package inetsoft.report.gui.viewsheet;

import inetsoft.report.internal.Common;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSTimeSliderDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedSliderInsetsItsBodyAndSpacesItsLabels() throws Exception {
      VSTimeSlider painter = painter(marked("comfortable"));
      assertEquals(new Insets(16, 16, 16, 16), painter.getBodyInset());
      assertEquals(6, painter.getLabelGap());
   }

   @Test
   void excelDrawsNoInset() throws Exception {
      VSTimeSlider painter = painter(marked("comfortable"));
      painter.setInsetsCard(false);
      assertEquals(new Insets(0, 0, 0, 0), painter.getBodyInset());
      assertEquals(6, painter.getLabelGap(), "the spacing still applies");
   }

   @Test
   void anUnmarkedSliderPaintsWithTodaysGeometry() throws Exception {
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(null);
      info.initDefaultFormat();
      VSTimeSlider painter = painter(info);
      assertEquals(new Insets(0, 0, 0, 0), painter.getBodyInset());
      assertEquals(2, painter.getLabelGap());
   }

   @Test
   void anAdhocSliderPaintsWithTodaysGeometry() throws Exception {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setAdhocFilter(true);
      VSTimeSlider painter = painter(info);
      assertEquals(new Insets(0, 0, 0, 0), painter.getBodyInset());
      assertEquals(2, painter.getLabelGap());
   }

   @Test
   void aMarkedImageFitsItsBox() throws Exception {
      assertEquals(new Dimension(232, 82), painter(marked("comfortable")).getImageSize());
   }

   @Test
   void paintingAMarkedSliderDoesNotThrow() throws Exception {
      assertDoesNotThrow(() -> paint(marked("comfortable"), 232, 82));
      assertDoesNotThrow(() -> paint(marked("comfortable"), 400, 45));
   }

   @Test
   void aLegacySliderImageGrowsByNoMoreThanItsLabelsNeed() throws Exception {
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(null);
      info.initDefaultFormat();
      info.setPixelSize(new Dimension(200, 30));
      assertEquals(legacyImageSize(info, 200, 30), painter(info).getImageSize());
   }

   @Test
   void anAdhocSliderImageGrowsByNoMoreThanItsLabelsNeed() throws Exception {
      TimeSliderVSAssemblyInfo info = marked("comfortable");
      info.setAdhocFilter(true);
      info.setPixelSize(new Dimension(200, 30));
      assertEquals(legacyImageSize(info, 200, 30), painter(info).getImageSize());
   }

   // the image height before padding and label gaps existed
   private static Dimension legacyImageSize(TimeSliderVSAssemblyInfo info, int w, int h) {
      int lineY = h / 2 - 9 / 2;
      int fontH = info.getFormat().getFont() == null ? 10 :
         Common.getFontMetrics(info.getFormat().getFont()).getHeight();
      return new Dimension(w, Math.max(h, lineY + 9 + fontH));
   }

   private static void paint(TimeSliderVSAssemblyInfo info, int w, int h) throws Exception {
      info.setPixelSize(new Dimension(w, h));
      VSTimeSlider painter = painter(info);
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      painter.paintComponent(img.createGraphics());
   }

   private static TimeSliderVSAssemblyInfo marked(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.initDefaultFormat();
      return info;
   }

   private static VSTimeSlider painter(TimeSliderVSAssemblyInfo info) throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly assembly = new TimeSliderVSAssembly(vs, "Slider1");
      TimeSliderVSAssemblyInfo target = (TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo();
      target.copyInfo(info);
      SelectionList list = new SelectionList();
      list.addSelectionValue(new SelectionValue("2025", "2025"));
      list.addSelectionValue(new SelectionValue("2026", "2026"));
      target.setSelectionList(list);
      vs.addAssembly(assembly);

      VSTimeSlider painter = new VSTimeSlider(vs);
      painter.setViewsheet(vs);
      painter.setTheme(new FlexTheme(PortalThemesManager.getColorTheme()));
      painter.setAssemblyInfo(target);
      return painter;
   }
}
