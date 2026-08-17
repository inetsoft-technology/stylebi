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
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSObjectChromeDefaultsTest {
   private int rgb(Color c) {
      return c.getRGB() & 0xFFFFFF;
   }

   private void withGate(String value, Runnable body) {
      String saved = SreeEnv.getProperty("viewsheet.modernVisualization");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", value);
         body.run();
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", saved);
      }
   }

   private void withDark(Runnable body) {
      String savedModern = SreeEnv.getProperty("viewsheet.modernVisualization");
      String savedDark = SreeEnv.getProperty("viewsheet.darkMode");

      try {
         SreeEnv.setProperty("viewsheet.modernVisualization", "true");
         SreeEnv.setProperty("viewsheet.darkMode", "true");
         body.run();
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", savedModern);
         SreeEnv.setProperty("viewsheet.darkMode", savedDark);
      }
   }

   @Test
   void colorConstants() {
      assertEquals(0xD9D5CC, rgb(VSObjectChromeDefaults.objectBorderColor(VizContext.ofGate())));
      assertEquals("#f8f7f4", VSObjectChromeDefaults.pageBackgroundCss(VizContext.ofGate()));
   }

   @Test
   void chartBorderSeedModernUnderGate() {
      withGate("true", () -> {
         ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
         info.initDefaultFormat();
         BorderColors b = info.getFormat().getDefaultFormat().getBorderColors();
         assertEquals(0xD9D5CC, rgb(b.topColor), "new chart under the gate seeds the modern border");
      });
   }

   @Test
   void chartBorderSeedLegacyGateOff() {
      withGate("false", () -> {
         ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
         info.initDefaultFormat();
         BorderColors b = info.getFormat().getDefaultFormat().getBorderColors();
         assertEquals(0xDADADA, rgb(b.topColor), "gate off keeps the legacy border seed");
      });
   }

   @Test
   void viewsheetPageSeedModernUnderGate() {
      withGate("true", () -> {
         ViewsheetVSAssemblyInfo info = new ViewsheetVSAssemblyInfo();
         info.initDefaultFormat();
         assertEquals(0xF8F7F4, rgb(info.getFormat().getDefaultFormat().getBackground()),
                      "new viewsheet under the gate seeds the modern warm page");
      });
   }

   @Test
   void viewsheetPageSeedLegacyGateOff() {
      withGate("false", () -> {
         ViewsheetVSAssemblyInfo info = new ViewsheetVSAssemblyInfo();
         info.initDefaultFormat();
         assertEquals(0xF5F5F5, rgb(info.getFormat().getDefaultFormat().getBackground()),
                      "gate off keeps the legacy #f5f5f5 page");
      });
   }

   @Test
   void cardBackgroundWhiteWhenNotDark() {
      // legacy and light modern keep white object cards
      assertEquals("#ffffff", VSObjectChromeDefaults.cardBackgroundCss(VizContext.ofGate()));
   }

   @Test
   void colorConstantsDark() {
      withDark(() -> {
         VizContext ctx = VizContext.ofGate();
         assertEquals(0x49454F, rgb(VSObjectChromeDefaults.objectBorderColor(ctx)));
         assertEquals("#1c1b1f", VSObjectChromeDefaults.pageBackgroundCss(ctx));
         assertEquals("#252428", VSObjectChromeDefaults.cardBackgroundCss(ctx));
      });
   }

   @Test
   void chartCardSeedDark() {
      withDark(() -> {
         ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
         info.initDefaultFormat();
         assertEquals(0x252428, rgb(info.getFormat().getDefaultFormat().getBackground()),
                      "new chart under dark seeds the dark card background");
      });
   }

   @Test
   void chartCardSeedWhiteLightModern() {
      withGate("true", () -> {
         ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
         info.initDefaultFormat();
         assertEquals(0xFFFFFF, rgb(info.getFormat().getDefaultFormat().getBackground()),
                      "light modern keeps the white chart card");
      });
   }

   @Test
   void viewsheetPageSeedDark() {
      withDark(() -> {
         ViewsheetVSAssemblyInfo info = new ViewsheetVSAssemblyInfo();
         info.initDefaultFormat();
         assertEquals(0x1C1B1F, rgb(info.getFormat().getDefaultFormat().getBackground()),
                      "new viewsheet under dark seeds the dark page");
      });
   }

   @Test
   void textForegroundCssNullWhenNotDark() {
      withGate("true", () -> assertNull(VSObjectChromeDefaults.textForegroundCss(VizContext.ofGate())));
   }

   @Test
   void textForegroundCssDark() {
      withDark(() -> assertEquals("#e6e0e9", VSObjectChromeDefaults.textForegroundCss(VizContext.ofGate())));
   }

   @Test
   void applyDarkForegroundSubstitutesBareDefault() {
      withDark(() -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         VSCompositeFormat out = VSObjectChromeDefaults.applyDarkForeground(fmt, VizContext.ofGate());
         assertNotSame(fmt, out, "returns a clone, never mutates the source");
         assertEquals(0xE6E0E9, rgb(out.getForeground()));
      });
   }

   @Test
   void applyDarkForegroundPreservesUserForeground() {
      withDark(() -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.getUserDefinedFormat().setForegroundValue("0x123456");
         assertSame(fmt, VSObjectChromeDefaults.applyDarkForeground(fmt, VizContext.ofGate()),
                    "a user foreground is left untouched in dark");
      });
   }

   @Test
   void applyDarkForegroundNoOpInLightModern() {
      withGate("true", () -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         assertSame(fmt, VSObjectChromeDefaults.applyDarkForeground(fmt, VizContext.ofGate()));
      });
   }

   @Test
   void cardCornerRadiusConstant() {
      assertEquals(12, VSObjectChromeDefaults.cardCornerRadius());
   }

   @Test
   void resolveSeededCornerKeepsSeedUnderGate() {
      withGate("true", () -> assertEquals(12, VSObjectChromeDefaults.resolveSeededCorner(12)));
   }

   @Test
   void resolveSeededCornerStripsSeedGateOff() {
      withGate("false", () -> assertEquals(0, VSObjectChromeDefaults.resolveSeededCorner(12)));
   }

   @Test
   void resolveSeededCornerPreservesNonSeedValues() {
      // only the exact seed is gate-owned; any other value is a customer/legacy radius
      withGate("false", () -> {
         assertEquals(8, VSObjectChromeDefaults.resolveSeededCorner(8));
         assertEquals(16, VSObjectChromeDefaults.resolveSeededCorner(16));
         assertEquals(0, VSObjectChromeDefaults.resolveSeededCorner(0));
      });
      withGate("true", () -> assertEquals(8, VSObjectChromeDefaults.resolveSeededCorner(8)));
   }

   private int seededRadius(VSAssemblyInfo info) {
      info.initDefaultFormat();
      return info.getFormat().getRoundCorner();
   }

   @Test
   void cardCornerSeededForDataAndSelectionTypesUnderGate() {
      withGate("true", () -> {
         assertEquals(12, seededRadius(new ChartVSAssemblyInfo()), "chart");
         assertEquals(12, seededRadius(new TableVSAssemblyInfo()), "table");
         assertEquals(12, seededRadius(new CrosstabVSAssemblyInfo()), "crosstab");
         assertEquals(12, seededRadius(new CalcTableVSAssemblyInfo()), "calc table");
         assertEquals(12, seededRadius(new EmbeddedTableVSAssemblyInfo()), "embedded table");
         assertEquals(12, seededRadius(new SelectionListVSAssemblyInfo()), "selection list");
         assertEquals(12, seededRadius(new SelectionTreeVSAssemblyInfo()), "selection tree");
         assertEquals(12, seededRadius(new CurrentSelectionVSAssemblyInfo()), "current selection");
      });
   }

   @Test
   void calendarKeepsItsOwnRadiusInBothGateStates() {
      // CalendarVSAssemblyInfo:88 overrides initDefaultFormat and clones a static template whose
      // object format hardcodes roundCorner=10 (:1420), so the seed never reaches it. 10 survives the
      // gate strip because that keys on exact equality with 12.
      withGate("true", () -> assertEquals(10, seededRadius(new CalendarVSAssemblyInfo())));
      withGate("false", () -> assertEquals(10, seededRadius(new CalendarVSAssemblyInfo())));
   }

   @Test
   void cardCornerNotSeededForExcludedTypesUnderGate() {
      withGate("true", () -> {
         assertEquals(0, seededRadius(new GaugeVSAssemblyInfo()), "gauge stays square");
         assertEquals(0, seededRadius(new TextVSAssemblyInfo()), "text stays square");
         assertEquals(0, seededRadius(new ComboBoxVSAssemblyInfo()), "inputs stay square");
         assertEquals(0, seededRadius(new TimeSliderVSAssemblyInfo()), "range slider stays square");
         assertEquals(0, seededRadius(new RectangleVSAssemblyInfo()), "shapes own their radius");
         // TabVSAssemblyInfo:65 unconditionally sets its own roundCorner of 4; the point is that it is
         // not overwritten by the 12px seed. 4 survives because the strip keys on exact equality with 12.
         assertEquals(4, seededRadius(new TabVSAssemblyInfo()), "tab keeps its own radius, not the seed");
      });
   }

   @Test
   void cardCornerNotSeededGateOff() {
      withGate("false", () -> {
         assertEquals(0, seededRadius(new ChartVSAssemblyInfo()), "chart");
         assertEquals(0, seededRadius(new TableVSAssemblyInfo()), "table");
         assertEquals(0, seededRadius(new SelectionListVSAssemblyInfo()), "selection list");
         assertEquals(0, seededRadius(new CurrentSelectionVSAssemblyInfo()), "current selection");
      });
   }

   @Test
   void cardCornerSeedRevertsWhenGateTurnedOff() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      withGate("true", () -> info.initDefaultFormat());
      withGate("true", () -> assertEquals(12, info.getFormat().getRoundCorner(), "rounded while on"));
      withGate("false", () -> assertEquals(0, info.getFormat().getRoundCorner(), "square once off"));
   }

   @Test
   void cardCornerUserRadiusSurvivesGateOff() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      withGate("true", () -> info.initDefaultFormat());
      info.getFormat().getUserDefinedFormat().setRoundCornerValue(6);
      withGate("false", () -> assertEquals(6, info.getFormat().getRoundCorner(),
                                           "a user radius is not gate-stripped"));
   }
}
