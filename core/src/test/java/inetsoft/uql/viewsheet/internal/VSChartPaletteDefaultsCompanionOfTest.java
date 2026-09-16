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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSChartPaletteDefaultsCompanionOfTest {
   @Test
   void aPaletteColourTakesItsAuthoredCompanion() {
      // Modern slot 0 is #0490FF; Modern-soft slot 0 is #97BEEB
      assertEquals(new Color(0x97BEEB),
                   VSChartPaletteDefaults.companionOf(new Color(0x0490FF), light()));
   }

   @Test
   void theDarkAnchorTakesItsAuthoredCompanion() {
      // Modern Dark slot 2 is #49447D; Modern Dark-soft slot 2 is #8988AB
      assertEquals(new Color(0x8988AB),
                   VSChartPaletteDefaults.companionOf(new Color(0x49447D), dark()));
   }

   @Test
   void aColourOutsideThePaletteIsDerivedByRule() {
      Color off = new Color(0x3366CC);
      Color companion = VSChartPaletteDefaults.companionOf(off, light());

      assertNotNull(companion);
      assertNotEquals(off, companion);
   }

   @Test
   void aNullBaseHasNoCompanion() {
      assertNull(VSChartPaletteDefaults.companionOf(null, light()));
   }

   @Test
   void everyHeadSlotResolvesInBothModes() {
      Color[] modern = VSChartPaletteDefaults.modernPalette();
      Color[] modernDark = VSChartPaletteDefaults.darkPalette();

      for(int i = 0; i < 8; i++) {
         assertNotNull(VSChartPaletteDefaults.companionOf(modern[i], light()), "light slot " + i);
         assertNotNull(VSChartPaletteDefaults.companionOf(modernDark[i], dark()), "dark slot " + i);
      }
   }

   private VizContext light() {
      return VizContext.of(VizMark.MODERN_LIGHT);
   }

   private VizContext dark() {
      return VizContext.of(VizMark.MODERN_DARK);
   }
}
