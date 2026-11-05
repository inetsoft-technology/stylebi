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
package inetsoft.web.viewsheet.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A data tip or pop component over the tinted card renders on an opaque surface.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSDataTipCardSurfaceTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private static VSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return chart.getVSAssemblyInfo();
   }

   @Test
   void aModernTipViewIsOpaqueWhite() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      VSAssemblyInfo info = newChart();

      VSDataTipService.opaqueCardSurface(info);

      assertEquals(0xFFFFFF, info.getFormat().getBackground().getRGB() & 0xFFFFFF);
   }

   @Test
   void aDarkTipViewIsOpaqueCard() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VSAssemblyInfo info = newChart();

      VSDataTipService.opaqueCardSurface(info);

      assertEquals(0x252428, info.getFormat().getBackground().getRGB() & 0xFFFFFF);
   }

   @Test
   void aLegacyTipViewIsUnchanged() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      VSAssemblyInfo info = newChart();
      Color before = info.getFormat().getBackground();

      VSDataTipService.opaqueCardSurface(info);

      assertEquals(before, info.getFormat().getBackground());
   }
}
