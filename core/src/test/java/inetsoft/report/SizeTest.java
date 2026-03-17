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
package inetsoft.report;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class SizeTest {

   // ---- Default constructor ----

   @Test
   void defaultConstructorCreatesZeroWidth() {
      Size size = new Size();
      assertEquals(0f, size.width);
   }

   @Test
   void defaultConstructorCreatesZeroHeight() {
      Size size = new Size();
      assertEquals(0f, size.height);
   }

   // ---- Float constructor ----

   @Test
   void floatConstructorSetsWidth() {
      Size size = new Size(8.5f, 11f);
      assertEquals(8.5f, size.width);
   }

   @Test
   void floatConstructorSetsHeight() {
      Size size = new Size(8.5f, 11f);
      assertEquals(11f, size.height);
   }

   @Test
   void floatConstructorWithZero() {
      Size size = new Size(0f, 0f);
      assertEquals(0f, size.width);
      assertEquals(0f, size.height);
   }

   // ---- Double constructor ----

   @Test
   void doubleConstructorCastsWidthToFloat() {
      Size size = new Size(8.5, 11.0);
      assertEquals((float) 8.5, size.width);
   }

   @Test
   void doubleConstructorCastsHeightToFloat() {
      Size size = new Size(8.5, 11.0);
      assertEquals((float) 11.0, size.height);
   }

   // ---- Dimension constructor ----

   @Test
   void dimensionConstructorSetsWidth() {
      Size size = new Size(new Dimension(100, 200));
      assertEquals(100f, size.width);
   }

   @Test
   void dimensionConstructorSetsHeight() {
      Size size = new Size(new Dimension(100, 200));
      assertEquals(200f, size.height);
   }

   // ---- Size copy constructor ----

   @Test
   void sizeCopyConstructorCopiesWidth() {
      Size original = new Size(3.0f, 4.0f);
      Size copy = new Size(original);
      assertEquals(3.0f, copy.width);
   }

   @Test
   void sizeCopyConstructorCopiesHeight() {
      Size original = new Size(3.0f, 4.0f);
      Size copy = new Size(original);
      assertEquals(4.0f, copy.height);
   }

   @Test
   void sizeCopyConstructorIsIndependent() {
      Size original = new Size(3.0f, 4.0f);
      Size copy = new Size(original);
      copy.width = 99f;
      assertEquals(3.0f, original.width);
   }

   // ---- Pixels + DPI constructor ----

   @Test
   void pixelDpiConstructorConvertsWidthToInches() {
      // 72 pixels at 72 DPI = 1 inch
      Size size = new Size(72, 144, 72);
      assertEquals(1.0f, size.width, 0.001f);
   }

   @Test
   void pixelDpiConstructorConvertsHeightToInches() {
      // 144 pixels at 72 DPI = 2 inches
      Size size = new Size(72, 144, 72);
      assertEquals(2.0f, size.height, 0.001f);
   }

   @Test
   void pixelDpiConstructorWith96Dpi() {
      Size size = new Size(96, 192, 96);
      assertEquals(1.0f, size.width, 0.001f);
      assertEquals(2.0f, size.height, 0.001f);
   }

   @Test
   void pixelDpiConstructorZeroPixels() {
      Size size = new Size(0, 0, 72);
      assertEquals(0f, size.width);
      assertEquals(0f, size.height);
   }

   // ---- rotate ----

   @Test
   void rotateSwapsWidth() {
      Size size = new Size(8.5f, 11f);
      Size rotated = size.rotate();
      assertEquals(11f, rotated.width);
   }

   @Test
   void rotateSwapsHeight() {
      Size size = new Size(8.5f, 11f);
      Size rotated = size.rotate();
      assertEquals(8.5f, rotated.height);
   }

   @Test
   void rotateReturnsNewInstance() {
      Size size = new Size(8.5f, 11f);
      Size rotated = size.rotate();
      assertNotSame(size, rotated);
   }

   @Test
   void rotateDoesNotMutateOriginalWidth() {
      Size size = new Size(8.5f, 11f);
      size.rotate();
      assertEquals(8.5f, size.width);
   }

   @Test
   void rotateDoesNotMutateOriginalHeight() {
      Size size = new Size(8.5f, 11f);
      size.rotate();
      assertEquals(11f, size.height);
   }

   @Test
   void rotateSquareSizeWidthUnchanged() {
      Size size = new Size(5f, 5f);
      Size rotated = size.rotate();
      assertEquals(5f, rotated.width);
      assertEquals(5f, rotated.height);
   }

   @Test
   void doubleRotateRestoresOriginalWidth() {
      Size size = new Size(3f, 7f);
      Size result = size.rotate().rotate();
      assertEquals(3f, result.width);
   }

   @Test
   void doubleRotateRestoresOriginalHeight() {
      Size size = new Size(3f, 7f);
      Size result = size.rotate().rotate();
      assertEquals(7f, result.height);
   }

   // ---- getDimension ----

   @Test
   void getDimensionTruncatesWidth() {
      Size size = new Size(8.9f, 11.4f);
      Dimension dim = size.getDimension();
      assertEquals(8, dim.width);
   }

   @Test
   void getDimensionTruncatesHeight() {
      Size size = new Size(8.9f, 11.4f);
      Dimension dim = size.getDimension();
      assertEquals(11, dim.height);
   }

   @Test
   void getDimensionForZeroSize() {
      Size size = new Size(0f, 0f);
      Dimension dim = size.getDimension();
      assertEquals(0, dim.width);
      assertEquals(0, dim.height);
   }

   @Test
   void getDimensionExactIntValues() {
      Size size = new Size(8f, 11f);
      Dimension dim = size.getDimension();
      assertEquals(8, dim.width);
      assertEquals(11, dim.height);
   }

   // ---- setDimension ----

   @Test
   void setDimensionUpdatesWidth() {
      Size size = new Size(1f, 1f);
      size.setDimension(new Dimension(200, 400));
      assertEquals(200f, size.width);
   }

   @Test
   void setDimensionUpdatesHeight() {
      Size size = new Size(1f, 1f);
      size.setDimension(new Dimension(200, 400));
      assertEquals(400f, size.height);
   }

   @Test
   void setDimensionRoundTrip() {
      Size size = new Size(100f, 200f);
      Dimension dim = size.getDimension();
      Size size2 = new Size();
      size2.setDimension(dim);
      assertEquals(size.getDimension().width, (int) size2.width);
      assertEquals(size.getDimension().height, (int) size2.height);
   }

   // ---- equals ----

   @Test
   void equalSizesAreEqual() {
      Size a = new Size(8.5f, 11f);
      Size b = new Size(8.5f, 11f);
      assertEquals(a, b);
   }

   @Test
   void differentWidthIsNotEqual() {
      Size a = new Size(8.5f, 11f);
      Size b = new Size(9.0f, 11f);
      assertNotEquals(a, b);
   }

   @Test
   void differentHeightIsNotEqual() {
      Size a = new Size(8.5f, 11f);
      Size b = new Size(8.5f, 12f);
      assertNotEquals(a, b);
   }

   @Test
   void nullIsNotEqualToSize() {
      Size size = new Size(1f, 2f);
      assertFalse(size.equals(null));
   }

   @Test
   void differentTypeIsNotEqual() {
      Size size = new Size(1f, 2f);
      assertFalse(size.equals("not a size"));
   }

   @Test
   void zeroSizesAreEqual() {
      assertEquals(new Size(), new Size());
   }

   // ---- toString ----

   @Test
   void toStringContainsWidth() {
      Size size = new Size(8.5f, 11f);
      assertTrue(size.toString().contains("8.5"), "toString should contain width");
   }

   @Test
   void toStringContainsHeight() {
      Size size = new Size(8.5f, 11f);
      assertTrue(size.toString().contains("11.0"), "toString should contain height");
   }

   @Test
   void toStringUsesXSeparator() {
      Size size = new Size(1f, 2f);
      assertTrue(size.toString().contains("x"), "toString should use 'x' separator");
   }
}
