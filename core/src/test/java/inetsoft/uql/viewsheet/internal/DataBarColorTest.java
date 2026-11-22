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
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A modern selection's measure bar takes a tint of the first palette colour over its card; a
 * classic selection keeps the classic colour, and the negative bar is left alone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataBarColorTest {
   private static final Color LEGACY_1 = new Color(0x518DB9);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      VSChartPaletteDefaults.clearMemo();
   }

   @Test
   void aModernSelectionBarIsATintOfTheFirstPaletteColour() {
      assertEquals(new Color(0x75C2FF),
                   VSObjectChromeDefaults.dataBarColor(VizContext.of(VizMark.MODERN_LIGHT)));
      assertEquals(new Color(0x427EBF),
                   VSObjectChromeDefaults.dataBarColor(VizContext.of(VizMark.MODERN_DARK)));
   }

   @Test
   void aClassicSelectionBarKeepsTheClassicColour() {
      assertEquals(LEGACY_1, VSObjectChromeDefaults.dataBarColor(VizContext.LEGACY));
      assertEquals(LEGACY_1, VSObjectChromeDefaults.dataBarColor(null));
   }

   @Test
   void modernizingASelectionRepaintsItsBarsAndRevertingRestoresThem() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      SelectionListVSAssemblyInfo info = newSelection();
      assertEquals(barValue(LEGACY_1), bar(info, 0));

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));

      for(int i = 0; i < 5; i++) {
         assertEquals(barValue(new Color(0x75C2FF)), bar(info, i));
      }

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(barValue(LEGACY_1), bar(info, 0));
   }

   @Test
   void aSelectionBornModernStartsWithTheTintedBar() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(barValue(new Color(0x75C2FF)), bar(newSelection(), 0));
   }

   @Test
   void theNegativeBarIsNotTouched() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SelectionListVSAssemblyInfo info = newSelection();
      String before = info.getFormatInfo()
         .getFormat(SelectionBaseVSAssemblyInfo.getMeasureNBarPath(0))
         .getDefaultFormat().getForegroundValue();

      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));

      assertEquals(before, info.getFormatInfo()
         .getFormat(SelectionBaseVSAssemblyInfo.getMeasureNBarPath(0))
         .getDefaultFormat().getForegroundValue());
   }

   private static String barValue(Color color) {
      return color.getRGB() + "";
   }

   private static String bar(SelectionListVSAssemblyInfo info, int level) {
      return info.getFormatInfo().getFormat(SelectionBaseVSAssemblyInfo.getMeasureBarPath(level))
         .getDefaultFormat().getForegroundValue();
   }

   private static SelectionListVSAssemblyInfo newSelection() {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "Selection1");
      list.getVSAssemblyInfo().initDefaultFormat();
      return (SelectionListVSAssemblyInfo) list.getVSAssemblyInfo();
   }
}
