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
package inetsoft.report;

import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/*
 * StyleFont resolves 'default.font.family' through PropertiesEngine, a Spring bean, and fonts are
 * built from static initializers (Util.DEFAULT_FONT, Util.WATER_FONT). An exception escaping a
 * static initializer marks the class permanently unusable for the life of the JVM, so a missing
 * application context used to poison Util -- and with it most of the report/chart code -- for the
 * rest of the surefire fork. These tests pin the fallback that keeps that from happening.
 */
@Tag("core")
class StyleFontDefaultFamilyTest {
   @Test
   void getDefaultFontFamilyFallsBackWhenNoApplicationContext() {
      withoutApplicationContext(() -> {
         assertEquals("Roboto", StyleFont.getDefaultFontFamily());
      });
   }

   @Test
   void constructingDefaultFontDoesNotThrowWhenNoApplicationContext() {
      withoutApplicationContext(() -> {
         StyleFont font =
            assertDoesNotThrow(() -> new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, 0, 10));
         assertEquals("Roboto", font.getName());
         assertTrue(StyleFont.isDefaultFont(font));
      });
   }

   /**
    * Runs the given assertions with the global application context cleared, restoring it
    * afterwards so the rest of the fork is unaffected.
    */
   private void withoutApplicationContext(Runnable assertions) {
      ConfigurationContext context = ConfigurationContext.getContext();
      ApplicationContext saved = context.getApplicationContext();

      try {
         context.setApplicationContext(null);
         assertions.run();
      }
      finally {
         context.setApplicationContext(saved);
      }
   }
}
