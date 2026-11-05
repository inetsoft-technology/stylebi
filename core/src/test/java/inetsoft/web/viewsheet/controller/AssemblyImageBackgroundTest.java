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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A downloaded chart image has no page behind it, so the card tint is painted as the opaque grey
 * it shows over the sheet's page rather than a translucent fill.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssemblyImageBackgroundTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
   }

   @Test
   void theCardTintIsFlattenedOverTheSheetPage() {
      ChartVSAssembly chart = chart(true);
      assertTrue(chart.getVSAssemblyInfo().getFormat().isDefaultCardTint(),
                 "precondition: the seeded tint");

      assertEquals(0xFF1A1A1A, paint(chart), "an opaque 15% grey over the black page");
   }

   @Test
   void aLegacyCardIsPaintedAsBefore() {
      ChartVSAssembly chart = chart(false);

      assertEquals(0xFFFFFFFF, paint(chart));
   }

   @Test
   void anAuthorColourKeepsItsOwnAlpha() {
      ChartVSAssembly chart = chart(true);
      chart.getVSAssemblyInfo().getFormat().getUserDefinedFormat().setBackgroundValue("#336699");
      chart.getVSAssemblyInfo().getFormat().getUserDefinedFormat().setAlphaValue(40);

      int argb = paint(chart);

      assertEquals(Math.round(40 * 255f / 100), argb >>> 24);
   }

   /** A chart on a black page, created with the gate on or off. */
   private static ChartVSAssembly chart(boolean modern) {
      SreeEnv.setProperty("viewsheet.modernVisualization", String.valueOf(modern));
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().initDefaultFormat();
      vs.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
         .getUserDefinedFormat().setBackgroundValue("#000000");
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(chart);
      return chart;
   }

   private static int paint(ChartVSAssembly chart) {
      BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      AssemblyImageService.drawBackground(g, chart, 4, 4);
      g.dispose();
      return image.getRGB(1, 1);
   }
}
