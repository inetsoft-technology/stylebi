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
package inetsoft.web.wiz.binding;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L3-Group3, findings G3-1/G3-2: {@code AestheticChannels.requireFieldChannel} gated {@code size}
 * on {@code sizeSupported} but had no equivalent gate for {@code color}/{@code shape} on a
 * contour chart — a contour chart's colour is chart-level density shading, not a per-point/
 * per-category field binding, and it has no point markers for shape to vary at all.
 * Live-confirmed 2026-09-01: both channels accepted and stored a field on a {@code
 * scatter_contour} chart, but the render showed no per-category variation for either — the
 * exact "stored but silently never rendered" shape the existing {@code size} guard exists to
 * prevent.
 */
@Tag("core")
class AestheticChannelsTest {
   @Test
   void refusesColorOnAContourChart() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> AestheticChannels.requireFieldChannel("color", false, true, false));
      assertTrue(thrown.getMessage().toLowerCase().contains("contour"), thrown.getMessage());
   }

   @Test
   void refusesShapeOnAContourChart() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> AestheticChannels.requireFieldChannel("shape", false, true, false));
      assertTrue(thrown.getMessage().toLowerCase().contains("contour"), thrown.getMessage());
   }

   @Test
   void allowsColorAndShapeOnAnOrdinaryChart() {
      assertEquals("color", AestheticChannels.requireFieldChannel("color", false, true, true));
      assertEquals("shape", AestheticChannels.requireFieldChannel("shape", false, true, true));
   }

   @Test
   void sizeAndColorShapeGatesAreIndependent() {
      // A contour chart also doesn't support size (GraphTypes.supportsSize), but the two gates
      // must be checked independently -- refusing size for the wrong reason (colorShapeSupported)
      // would name the wrong channel in the message.
      Exception sizeRefused = assertThrows(
         IllegalArgumentException.class,
         () -> AestheticChannels.requireFieldChannel("size", false, false, false));
      assertTrue(sizeRefused.getMessage().contains("size"), sizeRefused.getMessage());

      Exception colorRefused = assertThrows(
         IllegalArgumentException.class,
         () -> AestheticChannels.requireFieldChannel("color", false, false, false));
      assertTrue(colorRefused.getMessage().contains("color"), colorRefused.getMessage());
   }

   @Test
   void textAndSizeAreUnaffectedByTheContourGate() {
      assertEquals("text", AestheticChannels.requireFieldChannel("text", false, true, false));
      assertEquals("size", AestheticChannels.requireFieldChannel("size", false, true, false));
   }

   @Test
   void theThreeAndFourArgOverloadsAgreeWhenUnrestricted() {
      assertEquals(AestheticChannels.requireFieldChannel("color", false, true),
                   AestheticChannels.requireFieldChannel("color", false, true, true));
   }
}
