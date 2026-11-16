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
package inetsoft.report.internal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #80139: sub-rows of a tall wrapped cell split across pages must add up to the text height.
 * The error varies with the float line pitch (font height + spacing), not with spacing or line
 * count alone.
 */
@Tag("core")
class StyleCoreRoundupTest {
   private static final float[] PITCHES = { 12.797f, 13.2f, 14.4f };
   private static final int[][] CASES = {
      { 80, 3 }, { 150, 4 }, { 300, 0 }, { 45, 0 }, { 100, 0 }, { 150, 30 }
   };

   @Test
   void exactMultipleIsNotRoundedToNextMultiple() {
      for(float pitch : PITCHES) {
         for(int k : new int[] { 1, 7, 53, 200 }) {
            float exact = k * pitch;
            float fewUlps = exact + 4 * Math.ulp(exact);
            assertEquals(exact, StyleCore.roundup(exact, pitch), pitch * 1e-3f,
                         "pitch " + pitch + " k " + k);
            assertEquals(exact, StyleCore.roundup(fewUlps, pitch), pitch * 1e-3f,
                         "pitch " + pitch + " k " + k + " plus ulps");
         }
      }
   }

   @Test
   void genuinePartialLineStillRoundsUp() {
      for(float pitch : new float[] { 11f, 12f, 12.797f, 13.2f }) {
         assertEquals(6 * pitch, StyleCore.roundup(5.5f * pitch, pitch), pitch * 1e-3f);
         assertEquals(6 * pitch, StyleCore.roundup(5.01f * pitch, pitch), pitch * 1e-3f);
      }
   }

   @Test
   void integerPitchesUnchanged() {
      assertEquals(12f, StyleCore.roundup(12f, 12f), 0f);
      assertEquals(24f, StyleCore.roundup(12.5f, 12f), 0f);
      assertEquals(11f, StyleCore.roundup(11f, 11f), 0f);
      assertEquals(22f, StyleCore.roundup(11.5f, 11f), 0f);
   }

   @Test
   void spanLoopHasNoSurplusLines() {
      for(float pitch : PITCHES) {
         for(int[] c : CASES) {
            assertTrue(Math.abs(surplusLines(c[0], pitch)) < 1e-2,
                       "pitch " + pitch + " lines " + c[0] + " spacing " + c[1] + " surplus " +
                       surplusLines(c[0], pitch));
         }
      }
   }

   @Test
   void spanLoopUnchangedForIntegerPitches() {
      for(float pitch : new float[] { 11f, 12f }) {
         for(int[] c : CASES) {
            assertEquals(0, surplusLines(c[0], pitch), 1e-4);
         }
      }
   }

   @Test
   void splitRowCountClampedToLines() {
      float fh = 12f;

      for(int[] c : new int[][] { { 150, 30 }, { 150, 4 }, { 80, 3 }, { 300, 0 } }) {
         float spacing = c[1];
         float height = c[0] * (fh + spacing);
         int n = (int) Math.ceil(height / (fh * 3));
         int clamped = TableElementDef.clampSplitRowCount(n, height, spacing, fh);
         assertTrue(clamped >= 1 && clamped <= c[0], "lines " + c[0] + " spacing " + spacing);

         if(spacing <= 4) {
            assertEquals(n, clamped, "no-op at normal spacing");
         }
         else {
            assertTrue(clamped < n, "large spacing must reduce n=" + n);
         }
      }

      assertEquals(1, TableElementDef.clampSplitRowCount(1, 5f, 0f, 12f));
      assertEquals(1, TableElementDef.clampSplitRowCount(3, 5f, 0f, 12f));
   }

   // same loop as the span branch of TableElementDef.calculateRowHeight; returns the lines of
   // sub-rows beyond the text
   private static double surplusLines(int lines, float pitch) {
      float total = lines * pitch;
      int n = (int) Math.ceil(total / (3 * pitch));
      float sum = 0;

      for(int r = 0; r < n; r++) {
         float ch = StyleCore.roundup(total / (n - r), pitch);
         sum += ch;
         total -= ch;
      }

      return sum / pitch - lines;
   }
}
