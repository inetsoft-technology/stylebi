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
package inetsoft.report.composition.execution;

import inetsoft.report.style.XTableStyle;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.XTableUtil;
import inetsoft.uql.viewsheet.internal.VizContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the dark-mode wiring of the Default-Style overlay in DataVSAQuery (the load-bearing,
 * export-visible table interior path): the shipped zebra stripe is a REGULAR Specification that wins
 * over body.background, so dark mode must recolor the spec itself, and only in dark. The color values
 * themselves are pinned by VSTableStructureDefaultsTest; this pins the overlay wiring.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DataVSAQueryDarkStructureTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   // a Default-Style stand-in carrying the shipped zebra (REGULAR) stripe
   private XTableStyle styleWithZebra() {
      XTableStyle style = new XTableStyle(XTableUtil.getDefaultTableLens());
      XTableStyle.Specification zebra = style.new Specification();
      zebra.setType(XTableStyle.Specification.REGULAR);
      zebra.setIndex(1);
      zebra.setRepeat(true);
      zebra.put("background", new Color(0xF5F5F5));
      style.addSpecification(zebra);
      return style;
   }

   private Color regularSpecBackground(XTableStyle style) {
      for(int i = 0; i < style.getSpecificationCount(); i++) {
         XTableStyle.Specification spec = style.getSpecification(i);

         if(spec.getType() == XTableStyle.Specification.REGULAR) {
            return (Color) spec.get("background");
         }
      }

      return null;
   }

   @Test
   void applyDarkZebraRecolorsRegularSpecOnly() {
      XTableStyle style = styleWithZebra();
      XTableStyle.Specification total = style.new Specification();
      total.setType(XTableStyle.Specification.ROW_GROUP_TOTAL);
      total.setIndex(0);
      total.put("background", new Color(0xEEEAE1));
      style.addSpecification(0, total);

      DataVSAQuery.applyDarkZebra(style, new Color(0x2D2B30));

      assertEquals(new Color(0x2D2B30), regularSpecBackground(style), "REGULAR zebra spec recolored");
      assertEquals(new Color(0xEEEAE1), total.get("background"), "non-REGULAR (group-total) spec untouched");
   }

   @Test
   void applyDarkZebraNullIsNoOp() {
      XTableStyle style = styleWithZebra();
      DataVSAQuery.applyDarkZebra(style, null);
      assertEquals(new Color(0xF5F5F5), regularSpecBackground(style), "null => shipped stripe unchanged");
   }

   @Test
   void applyModernTableStructureDarkRecolorsZebra() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      XTableStyle style = styleWithZebra();
      DataVSAQuery.applyModernTableStructure(style, VizContext.ofGate());
      assertEquals(new Color(0x2D2B30), regularSpecBackground(style),
                   "dark zebra applied through the gated zebraBackground() accessor");
   }

   @Test
   void applyModernTableStructureLightLeavesZebra() {
      // modern on, dark off: the dark interior accessors return null, so the shipped stripe stands
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      XTableStyle style = styleWithZebra();
      DataVSAQuery.applyModernTableStructure(style, VizContext.ofGate());
      assertEquals(new Color(0xF5F5F5), regularSpecBackground(style),
                   "light-modern leaves the shipped #F5F5F5 stripe");
   }
}
