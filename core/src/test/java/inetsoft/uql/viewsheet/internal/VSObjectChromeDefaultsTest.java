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
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
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
      assertEquals(0xD9D5CC, rgb(VSObjectChromeDefaults.objectBorderColor()));
      assertEquals("#f8f7f4", VSObjectChromeDefaults.pageBackgroundCss());
   }

   @Test
   void isModernGatedByProperty() {
      withGate("true", () -> assertTrue(VSObjectChromeDefaults.isModern()));
      withGate("false", () -> assertFalse(VSObjectChromeDefaults.isModern()));
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
      assertEquals("#ffffff", VSObjectChromeDefaults.cardBackgroundCss());
   }

   @Test
   void colorConstantsDark() {
      withDark(() -> {
         assertEquals(0x49454F, rgb(VSObjectChromeDefaults.objectBorderColor()));
         assertEquals("#1c1b1f", VSObjectChromeDefaults.pageBackgroundCss());
         assertEquals("#252428", VSObjectChromeDefaults.cardBackgroundCss());
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
      withGate("true", () -> assertNull(VSObjectChromeDefaults.textForegroundCss()));
   }

   @Test
   void textForegroundCssDark() {
      withDark(() -> assertEquals("#e6e0e9", VSObjectChromeDefaults.textForegroundCss()));
   }

   @Test
   void applyDarkForegroundSubstitutesBareDefault() {
      withDark(() -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         VSCompositeFormat out = VSObjectChromeDefaults.applyDarkForeground(fmt);
         assertNotSame(fmt, out, "returns a clone, never mutates the source");
         assertEquals(0xE6E0E9, rgb(out.getForeground()));
      });
   }

   @Test
   void applyDarkForegroundPreservesUserForeground() {
      withDark(() -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.getUserDefinedFormat().setForegroundValue("0x123456");
         assertSame(fmt, VSObjectChromeDefaults.applyDarkForeground(fmt),
                    "a user foreground is left untouched in dark");
      });
   }

   @Test
   void applyDarkForegroundNoOpInLightModern() {
      withGate("true", () -> {
         VSCompositeFormat fmt = new VSCompositeFormat();
         assertSame(fmt, VSObjectChromeDefaults.applyDarkForeground(fmt));
      });
   }
}
