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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.web.service.HighlightService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78197: a shape whose stored fg/bg color is not an int (e.g. "red") must stay readable
 * through the property dialog services, and the dialog write must stay round-trip safe.
 */
@Tag("core")
class ShapeColorHexTest {
   @Test
   void namedColorResolvesToHex() {
      assertEquals("#ff0000", VSObjectPropertyService.getColorHexString("red"));
   }

   @Test
   void unresolvableColorFallsBackToEmpty() {
      assertEquals("", VSObjectPropertyService.getColorHexString("notacolor"));
      assertEquals("", VSObjectPropertyService.getColorHexString("rgb(255,0,0)"));
   }

   @Test
   void intStoredColorsUnchanged() {
      assertEquals("#ff0000", VSObjectPropertyService.getColorHexString("16711680"));
      assertEquals("#ff0000", VSObjectPropertyService.getColorHexString("#FF0000"));
      assertEquals("#000f00", VSObjectPropertyService.getColorHexString("#F00"));
      assertNull(VSObjectPropertyService.getColorHexString(null));
      assertEquals("", VSObjectPropertyService.getColorHexString(""));
   }

   @Test
   void highlightTwinMatches() {
      assertEquals("#ff0000", HighlightService.getColorHexString("red"));
      assertEquals("", HighlightService.getColorHexString("notacolor"));
      assertEquals("#ff0000", HighlightService.getColorHexString("16711680"));
   }

   @Test
   void emptyStaticColorWritesAsNoColor() {
      assertEquals("", VSObjectPropertyService.decodeStaticColor("", "linePropPaneModel.colorValue"));
      assertEquals("", VSObjectPropertyService.decodeStaticColor(null, "linePropPaneModel.colorValue"));
   }

   @Test
   void validStaticColorWritesAsDecimal() {
      assertEquals("16711680",
                   VSObjectPropertyService.decodeStaticColor("#ff0000", "linePropPaneModel.colorValue"));
   }

   @Test
   void garbageStaticColorNamesField() {
      IllegalArgumentException ex = assertThrows(
         IllegalArgumentException.class,
         () -> VSObjectPropertyService.decodeStaticColor("notacolor", "linePropPaneModel.colorValue"));
      assertTrue(ex.getMessage().contains("linePropPaneModel.colorValue"));
      assertTrue(ex.getMessage().contains("notacolor"));
   }
}
