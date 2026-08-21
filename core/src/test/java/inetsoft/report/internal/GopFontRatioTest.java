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
package inetsoft.report.internal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the font.ratio.x/font.ratio.y value grammar. pdf.font.ratio is the
 * obsolete name for font.ratio.x and takes the same name:ratio;name:ratio
 * form; a bare scalar is not a supported value.
 */
@Tag("core")
class GopFontRatioTest {
   @Test
   void nameWithoutStyleAppliesToEveryStyle() {
      HashMap ratios = parse("Arial:1.1");

      assertEquals(4, ratios.size());
      assertEquals(Double.valueOf(1.1), ratios.get("Arial"));
      assertEquals(Double.valueOf(1.1), ratios.get("Arial-bold"));
      assertEquals(Double.valueOf(1.1), ratios.get("Arial-italic"));
      assertEquals(Double.valueOf(1.1), ratios.get("Arial-bolditalic"));
   }

   @Test
   void nameWithStyleAppliesToThatStyleOnly() {
      HashMap ratios = parse("Arial-bold:1.1");

      assertEquals(1, ratios.size());
      assertEquals(Double.valueOf(1.1), ratios.get("Arial-bold"));
      assertNull(ratios.get("Arial"));
   }

   @Test
   void multiplePairsAreAllParsed() {
      HashMap ratios = parse("MS Hei:1.1;Algerian-bolditalic:1.02");

      assertEquals(Double.valueOf(1.1), ratios.get("MS Hei"));
      assertEquals(Double.valueOf(1.02), ratios.get("Algerian-bolditalic"));
      // "MS Hei" has no style suffix, so it also covers the three styles
      assertEquals(Double.valueOf(1.1), ratios.get("MS Hei-bold"));
      assertNull(ratios.get("Algerian"));
   }

   /**
    * A bare scalar is the grammar the obsolete pdf.font.ratio reader in
    * PDFPrinter used to accept. It scaled the glyphs without scaling the
    * layout that measures them, so it is no longer supported anywhere.
    */
   @Test
   void bareScalarIsIgnored() {
      assertTrue(parse("1.1").isEmpty());
      assertTrue(parse("0.95").isEmpty());
   }

   @Test
   void malformedPairsAreIgnored() {
      assertTrue(parse("Arial:notanumber").isEmpty());
      assertTrue(parse(":1.1").isEmpty());
      assertTrue(parse("Arial").isEmpty());
   }

   @Test
   void goodPairsSurviveAlongsideBadOnes() {
      HashMap ratios = parse("Arial-bold:1.1;garbage;Times-italic:oops");

      assertEquals(1, ratios.size());
      assertEquals(Double.valueOf(1.1), ratios.get("Arial-bold"));
   }

   /**
    * A space after the ';' separator is the natural way to write the list,
    * so the font name must be trimmed or the key can never match the name
    * getNameWithStyle() builds from the font.
    */
   @Test
   void whitespaceAroundPairsIsIgnored() {
      HashMap ratios = parse("MS Hei:1.1; Algerian-bolditalic:1.02");

      assertEquals(Double.valueOf(1.02), ratios.get("Algerian-bolditalic"));
      assertNull(ratios.get(" Algerian-bolditalic"));
   }

   @Test
   void whitespaceAroundRatioIsIgnored() {
      HashMap ratios = parse("Arial-bold: 1.1 ");

      assertEquals(Double.valueOf(1.1), ratios.get("Arial-bold"));
   }

   private static HashMap parse(String prop) {
      HashMap ratios = new HashMap();
      new Gop().parseRatios(ratios, prop, "font.ratio.x");
      return ratios;
   }
}
