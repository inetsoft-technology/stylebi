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
package inetsoft.web.viewsheet.model;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class VSRangeSliderModelDensityTest {
   @Mock RuntimeViewsheet rvs;
   private Viewsheet vs;

   @BeforeEach
   void setup() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("Viewsheet1");
      vs.getViewsheetInfo().setVizDensity("comfortable");
      when(rvs.getID()).thenReturn("Viewsheet1");
      when(rvs.getViewsheet()).thenReturn(vs);
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private TimeSliderVSAssembly slider(boolean marked, int width, int height) {
      TimeSliderVSAssembly assembly = new TimeSliderVSAssembly();
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setName("RangeSlider1");
      info.setVizMark(marked ? VizMark.MODERN_LIGHT : null);
      info.initDefaultFormat();
      info.setPixelSize(new Dimension(width, height));
      return assembly;
   }

   private VSRangeSliderModel model(TimeSliderVSAssembly assembly, boolean contained) {
      vs.addAssembly(assembly);

      if(contained) {
         CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly();
         container.getVSAssemblyInfo().setName("CurrentSelection1");
         vs.addAssembly(container);
         container.setAssemblies(new String[] { assembly.getName() });
      }

      return new VSRangeSliderModel(assembly, rvs);
   }

   @Test
   void aMarkedSliderSendsItsPaddingAndAnInsetTrack() {
      VSRangeSliderModel model = model(slider(true, 300, 82), false);
      assertEquals(new Insets(16, 16, 16, 16), model.getPadding());
      assertEquals(300 - 32, model.getMaxRangeBarWidth(), 0.01);
   }

   @Test
   void aMarkedContainedSliderGivesUpTheListsTwoPixels() {
      VSRangeSliderModel model = model(slider(true, 300, 82), true);
      assertEquals(300 - 32 - 2, model.getMaxRangeBarWidth(), 0.01);
   }

   @Test
   void anUnmarkedSliderSendsNoPaddingAndTodaysTrack() {
      VSRangeSliderModel model = model(slider(false, 200, 40), false);
      assertEquals(new Insets(0, 0, 0, 0), model.getPadding());
      assertEquals(200, model.getMaxRangeBarWidth(), 0.01);
   }

   @Test
   void anUnmarkedContainedSliderKeepsTodaysTrack() {
      VSRangeSliderModel model = model(slider(false, 200, 40), true);
      assertEquals(200, model.getMaxRangeBarWidth(), 0.01);
   }

   @Test
   void anAdhocSliderSendsNoPadding() {
      TimeSliderVSAssembly assembly = slider(true, 200, 82);
      ((TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo()).setAdhocFilter(true);
      VSRangeSliderModel model = model(assembly, true);
      assertEquals(new Insets(0, 0, 0, 0), model.getPadding());
      assertEquals(200, model.getMaxRangeBarWidth(), 0.01);
   }
}
