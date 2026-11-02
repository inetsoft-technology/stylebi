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
package inetsoft.web.vswizard.service;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #79582: the wizard wrote a fixed 200x40 over a marked range slider's density box.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizardViewsheetServiceSliderSizeTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void markedSliderTakesDensityBox() {
      assertEquals(new Dimension(232, 82), size(true, "comfortable"));
      assertEquals(new Dimension(224, 74), size(true, "compact"));
      assertEquals(new Dimension(216, 66), size(true, "dense"));
   }

   @Test
   void unmarkedSliderKeepsLegacyBox() {
      assertEquals(new Dimension(200, 40), size(false, "comfortable"));
      assertEquals(new Dimension(200, 40), size(false, "dense"));
   }

   private Dimension size(boolean marked, String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      TimeSliderVSAssembly assembly = new TimeSliderVSAssembly(new Viewsheet(), "Slider1");
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setVizMark(marked ? VizMark.MODERN_LIGHT : null);
      return new WizardViewsheetService(null, null).getDefaultAssemblyPixelSize(assembly);
   }
}
