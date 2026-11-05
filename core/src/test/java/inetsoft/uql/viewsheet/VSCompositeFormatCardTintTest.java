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
package inetsoft.uql.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The card tint is one layer: an author colour drops its seeded alpha, and sub-formats never
 * inherit it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSCompositeFormatCardTintTest {
   private static VSCompositeFormat tinted() {
      VSCompositeFormat f = new VSCompositeFormat();
      f.getDefaultFormat().setBackgroundValue(VSObjectChromeDefaults.cardTintValue());
      f.getDefaultFormat().setAlphaValue(VSObjectChromeDefaults.CARD_TINT_ALPHA);
      return f;
   }

   @Test
   void anUntouchedCardKeepsTheSeededAlpha() {
      assertEquals(15, tinted().getAlpha());
      assertTrue(tinted().isDefaultCardTint());
   }

   @Test
   void anAuthorColourDropsTheSeededAlpha() {
      VSCompositeFormat f = tinted();
      f.getUserDefinedFormat().setBackgroundValue("#336699");
      assertEquals(100, f.getAlpha());
      assertEquals(100, f.getAlphaValue());
      assertEquals(255, f.getBackground().getAlpha());
      assertFalse(f.isDefaultCardTint());
   }

   @Test
   void anAuthorAlphaStillWins() {
      VSCompositeFormat f = tinted();
      f.getUserDefinedFormat().setAlphaValue(40);
      assertEquals(40, f.getAlpha());
   }

   @Test
   void anInheritedAlphaWithoutTheTintIsUnchanged() {
      VSCompositeFormat f = new VSCompositeFormat();
      f.getDefaultFormat().setBackgroundValue("#ffffff");
      f.getDefaultFormat().setAlphaValue(50);
      f.getUserDefinedFormat().setBackgroundValue("#336699");
      assertEquals(50, f.getAlpha());
   }

   @Test
   void pathFormatsDoNotInheritTheTint() {
      FormatInfo fi = new FormatInfo();
      fi.setFormat(VSAssemblyInfo.OBJECTPATH, tinted());
      VSCompositeFormat title = fi.getFormat(VSAssemblyInfo.TITLEPATH, false);
      assertNull(title.getBackground());
      assertEquals(100, title.getAlpha());
   }

   @Test
   void pathFormatsStillInheritAnOpaqueObjectBackground() {
      FormatInfo fi = new FormatInfo();
      VSCompositeFormat obj = new VSCompositeFormat();
      obj.getDefaultFormat().setBackgroundValue("#ffffff");
      fi.setFormat(VSAssemblyInfo.OBJECTPATH, obj);
      assertEquals(0xFFFFFF,
                   fi.getFormat(VSAssemblyInfo.TITLEPATH, false).getBackground().getRGB() & 0xFFFFFF);
   }

   @Test
   void pathFormatsStillInheritAnAuthorAlpha() {
      VSCompositeFormat obj = tinted();
      obj.getUserDefinedFormat().setAlphaValue(40);
      FormatInfo fi = new FormatInfo();
      fi.setFormat(VSAssemblyInfo.OBJECTPATH, obj);
      VSCompositeFormat title = fi.getFormat(VSAssemblyInfo.TITLEPATH, false);
      assertNull(title.getBackground());
      assertEquals(40, title.getAlpha());
   }
}
