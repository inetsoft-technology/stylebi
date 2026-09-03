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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.io.viewsheet.ShapeShadowUtil;
import inetsoft.report.script.PropertyDescriptor;
import inetsoft.uql.viewsheet.internal.ShapeVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ShapeShadowTest {
   @Test
   void defaultsApproximateTheLegacyHardcodedShadow() {
      ShapeShadow shadow = new ShapeShadow();

      // the shadow that used to be hardcoded fell 5px down and to the right
      assertEquals(ShapeShadow.SOUTH_EAST, shadow.getDirection());
      assertEquals(5, shadow.getOffsetX());
      assertEquals(5, shadow.getOffsetY());
      assertEquals(30, shadow.getAlpha());
      assertEquals(6, shadow.getBlur());
   }

   @ParameterizedTest
   @CsvSource({
      "N,   0, -7",
      "NE,  7, -7",
      "E,   7,  0",
      "SE,  7,  7",
      "S,   0,  7",
      "SW, -7,  7",
      "W,  -7,  0",
      "NW, -7, -7"
   })
   void directionMapsToSignedOffsets(String direction, int expectedX, int expectedY) {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(direction);
      shadow.setDistance(7);

      assertEquals(expectedX, shadow.getOffsetX(), "offsetX for " + direction);
      assertEquals(expectedY, shadow.getOffsetY(), "offsetY for " + direction);
   }

   @Test
   void unknownDirectionCastsNoOffset() {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection("bogus");
      shadow.setDistance(9);

      assertEquals(0, shadow.getOffsetX());
      assertEquals(0, shadow.getOffsetY());
   }

   @Test
   void shadowColorAppliesOpacity() {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setColor("#ff0000");
      shadow.setAlpha(50);

      Color color = shadow.getShadowColor();
      assertEquals(255, color.getRed());
      assertEquals(0, color.getGreen());
      assertEquals(0, color.getBlue());
      assertEquals(128, color.getAlpha());
   }

   @Test
   void shadowColorFallsBackToBlackForAnUnparseableColor() {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setColor("not-a-color");
      shadow.setAlpha(100);

      Color color = shadow.getShadowColor();
      assertEquals(Color.BLACK.getRGB(), color.getRGB());
   }

   @Test
   void alphaIsClampedToTheValidRange() {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setAlpha(500);
      assertEquals(255, shadow.getShadowColor().getAlpha());

      shadow.setAlpha(-20);
      assertEquals(0, shadow.getShadowColor().getAlpha());
   }

   @Test
   void cloneAndEqualsCoverEveryField() {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setColor("#123456");
      shadow.setAlpha(42);
      shadow.setDirection(ShapeShadow.NORTH_WEST);
      shadow.setDistance(11);
      shadow.setBlur(3);

      ShapeShadow clone = (ShapeShadow) shadow.clone();
      assertEquals(shadow, clone);
      assertEquals(shadow.hashCode(), clone.hashCode());

      clone.setBlur(4);
      assertNotEquals(shadow, clone);
   }

   @Test
   void onlyTheFieldsTheClientNeedsAreSerialized() throws Exception {
      // regression: getShadowColor() is a bean property, so jackson walked
      // java.awt.Color -> ColorSpace -> ICC profile and emitted ~9kb of base64
      // for every shape on every model refresh
      String json = new ObjectMapper().writeValueAsString(new ShapeShadow());

      assertFalse(json.contains("shadowColor"), json);
      assertFalse(json.contains("colorSpace"), json);
      assertFalse(json.contains("offsetX"), json);
      assertFalse(json.contains("blurRadius"), json);
      assertTrue(json.contains("\"direction\""), json);
      assertTrue(json.length() < 200, "unexpectedly large payload: " + json.length());
   }

   // ---- insets, which is what keeps the export from clipping the shadow ----

   @Test
   void insetsGrowOnlyTheSidesTheShadowFallsOn() {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(ShapeShadow.SOUTH_EAST);
      shadow.setDistance(5);
      shadow.setBlur(0);   // no blur, so the insets are the offset alone

      Insets insets = ShapeShadowUtil.getShadowInsets(shadow);
      assertEquals(0, insets.top);
      assertEquals(0, insets.left);
      assertEquals(5, insets.bottom);
      assertEquals(5, insets.right);
   }

   @Test
   void insetsGrowUpAndLeftForANorthWestShadow() {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(ShapeShadow.NORTH_WEST);
      shadow.setDistance(5);
      shadow.setBlur(0);

      Insets insets = ShapeShadowUtil.getShadowInsets(shadow);
      assertEquals(5, insets.top);
      assertEquals(5, insets.left);
      assertEquals(0, insets.bottom);
      assertEquals(0, insets.right);
   }

   @Test
   void blurGrowsEverySide() {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(ShapeShadow.EAST);
      shadow.setDistance(4);
      shadow.setBlur(3);

      // the margin is twice the blur radius so the ConvolveOp's unconvolved
      // edge band lands in empty space instead of clipping the soft edge
      int margin = 2 * shadow.getBlurRadius();
      assertEquals(10, margin);   // radius = round(3 * 1.5) = 5

      Insets insets = ShapeShadowUtil.getShadowInsets(shadow);
      assertEquals(margin, insets.top);
      assertEquals(margin, insets.left);
      assertEquals(margin, insets.bottom);
      assertEquals(margin + 4, insets.right);
   }

   @Test
   void blurRadiusScalesUpToMatchTheCssBlur() {
      ShapeShadow shadow = new ShapeShadow();

      // VSFaceUtil derives sigma as radius/3; css blur-radius b is sigma b/2
      shadow.setBlur(6);
      assertEquals(9, shadow.getBlurRadius());

      shadow.setBlur(0);
      assertEquals(0, shadow.getBlurRadius(), "no blur means no convolve");

      shadow.setBlur(1);
      assertEquals(2, shadow.getBlurRadius());
   }

   @Test
   void aNullColorFallsBackToTheDefaultRatherThanPersistingNull() {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setColor(null);

      assertEquals(ShapeShadow.DEFAULT_COLOR, shadow.getColor());

      // black, carrying the default 30% opacity rather than being opaque
      Color color = shadow.getShadowColor();
      assertEquals(0, color.getRed());
      assertEquals(0, color.getGreen());
      assertEquals(0, color.getBlue());
      assertEquals(Math.round(30 * 255f / 100), color.getAlpha());
   }

   @Test
   void theLiteralNullColorDoesNotBlowUp() {
      // Tool.getColorFromHexString returns null for "null" without throwing,
      // so the exception path alone is not enough of a guard
      ShapeShadow shadow = new ShapeShadow();
      shadow.setColor("null");
      shadow.setAlpha(100);

      assertEquals(Color.BLACK.getRGB(), shadow.getShadowColor().getRGB());
   }

   @Test
   void insetsAreZeroWhenTheShadowIsOff() {
      ShapeVSAssemblyInfo info = Mockito.mock(ShapeVSAssemblyInfo.class);
      Mockito.when(info.isShadow()).thenReturn(false);

      assertFalse(ShapeShadowUtil.isShapeShadow(info));
      assertNoInsets(ShapeShadowUtil.getShadowInsets(info));
   }

   @Test
   void insetsAreZeroForANullShadow() {
      assertNoInsets(ShapeShadowUtil.getShadowInsets((ShapeShadow) null));
   }

   @Test
   void insetsAreAppliedWhenTheShadowIsOn() {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(ShapeShadow.SOUTH);
      shadow.setDistance(4);
      shadow.setBlur(0);

      ShapeVSAssemblyInfo info = Mockito.mock(ShapeVSAssemblyInfo.class);
      Mockito.when(info.isShadow()).thenReturn(true);
      Mockito.when(info.getShadowInfo()).thenReturn(shadow);

      assertTrue(ShapeShadowUtil.isShapeShadow(info));

      Insets insets = ShapeShadowUtil.getShadowInsets(info);
      assertEquals(0, insets.top);
      assertEquals(4, insets.bottom);
   }

   @Test
   void propertyDescriptorConvertsAnObjectLiteralToAShapeShadow() {
      Map<String, Object> literal = new HashMap<>();
      literal.put("color", "#ff0000");
      literal.put("alpha", 40);
      literal.put("direction", ShapeShadow.WEST);
      literal.put("distance", 20);
      literal.put("blur", 8);

      Object converted = PropertyDescriptor.convert(literal, ShapeShadow.class);

      assertTrue(converted instanceof ShapeShadow, "expected a ShapeShadow, got " + converted);
      ShapeShadow shadow = (ShapeShadow) converted;
      assertEquals("#ff0000", shadow.getColor());
      assertEquals(40, shadow.getAlpha());
      assertEquals(ShapeShadow.WEST, shadow.getDirection());
      assertEquals(20, shadow.getDistance());
      assertEquals(8, shadow.getBlur());
   }

   @Test
   void propertyDescriptorConvertLeavesDefaultsWhenFieldsAreMissing() {
      Map<String, Object> literal = new HashMap<>();
      literal.put("direction", ShapeShadow.NORTH);

      ShapeShadow shadow = (ShapeShadow) PropertyDescriptor.convert(literal, ShapeShadow.class);

      assertEquals(ShapeShadow.DEFAULT_COLOR, shadow.getColor());
      assertEquals(ShapeShadow.DEFAULT_ALPHA, shadow.getAlpha());
      assertEquals(ShapeShadow.NORTH, shadow.getDirection());
      assertEquals(ShapeShadow.DEFAULT_DISTANCE, shadow.getDistance());
      assertEquals(ShapeShadow.DEFAULT_BLUR, shadow.getBlur());
   }

   private static void assertNoInsets(Insets insets) {
      assertEquals(0, insets.top);
      assertEquals(0, insets.left);
      assertEquals(0, insets.bottom);
      assertEquals(0, insets.right);
   }
}
