/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.internal.j2d;

import inetsoft.sree.SreeEnv;
import inetsoft.util.ShutdownException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Bug #78026: Gop2D must construct when report.stringwidth.fontmetrics can't be
 * read. Otherwise Gop.getInstance() caches a plain Gop for the whole JVM and
 * PDFPrinter drops pages that nothing draws on.
 *
 * Deliberately has no Spring context. It constructs Gop1_4 directly instead of
 * going through Gop.getInstance()/Common, so it leaves no JVM-wide state behind.
 */
@Tag("core")
class Gop2DNoContextTest {
   @Test
   void constructsWithoutSpringContext() throws Exception {
      Gop1_4 gop = assertDoesNotThrow(() -> new Gop1_4());
      assertTrue(getStringWidthFontMetrics(gop));
   }

   @Test
   void defaultsToTrueWhenPropertyReadThrowsShutdownException() throws Exception {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty(anyString()))
            .thenThrow(new ShutdownException());
         Gop1_4 gop = assertDoesNotThrow(() -> new Gop1_4());
         assertTrue(getStringWidthFontMetrics(gop));
      }
   }

   @Test
   void defaultsToTrueWhenPropertyReadThrowsOtherException() throws Exception {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty(anyString()))
            .thenThrow(new IllegalStateException("broken properties"));
         Gop1_4 gop = assertDoesNotThrow(() -> new Gop1_4());
         assertTrue(getStringWidthFontMetrics(gop));
      }
   }

   @Test
   void defaultsToTrueWhenPropertyIsNull() throws Exception {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty(anyString())).thenReturn(null);
         Gop1_4 gop = assertDoesNotThrow(() -> new Gop1_4());
         assertTrue(getStringWidthFontMetrics(gop));
      }
   }

   @Test
   void honoursExplicitFalse() throws Exception {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("report.stringwidth.fontmetrics"))
            .thenReturn("false");
         assertFalse(getStringWidthFontMetrics(new Gop1_4()));
      }
   }

   private static boolean getStringWidthFontMetrics(Gop2D gop) throws Exception {
      Field field = Gop2D.class.getDeclaredField("stringWidthFontMetrics");
      field.setAccessible(true);
      return field.getBoolean(gop);
   }
}
