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
package inetsoft.report.gui.viewsheet;

import inetsoft.report.io.viewsheet.ShapeShadowUtil;
import inetsoft.uql.viewsheet.ShapeShadow;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Geometry of the configurable shape drop shadow rasterizer.
 *
 * The shape must sit at (insets.left, insets.top) of the returned composite.
 * VSShape.paintComponent relies on that to draw the composite back by the same
 * amount so the shape still lands where the assembly actually is -- otherwise
 * the background and borders, which paint() draws separately, end up offset
 * from the shape.
 */
// VSFaceUtil has a static initializer that needs the server environment
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSFaceUtilShadowTest {
   /** A fully opaque red square, so shadow pixels are easy to tell apart. */
   private static BufferedImage square(int size) {
      BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         g.setColor(Color.RED);
         g.fillRect(0, 0, size, size);
      }
      finally {
         g.dispose();
      }

      return img;
   }

   private static ShapeShadow shadow(String direction, int distance, int blur) {
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(direction);
      shadow.setDistance(distance);
      shadow.setBlur(blur);
      shadow.setColor("#000000");
      shadow.setAlpha(100);

      return shadow;
   }

   @Test
   void compositeGrowsByTheInsetsOnEverySide() {
      ShapeShadow shadow = shadow(ShapeShadow.SOUTH_EAST, 4, 0);
      Insets insets = ShapeShadowUtil.getShadowInsets(shadow);

      BufferedImage out = VSFaceUtil.addShadow(square(10), shadow, insets);

      assertEquals(10 + insets.left + insets.right, out.getWidth());
      assertEquals(10 + insets.top + insets.bottom, out.getHeight());
   }

   @Test
   void shapeIsPlacedAtTheInsetOriginForADownRightShadow() {
      ShapeShadow shadow = shadow(ShapeShadow.SOUTH_EAST, 4, 0);
      Insets insets = ShapeShadowUtil.getShadowInsets(shadow);

      BufferedImage out = VSFaceUtil.addShadow(square(10), shadow, insets);

      // the shape's top-left corner must be exactly at (left, top)
      assertEquals(Color.RED.getRGB(), out.getRGB(insets.left, insets.top),
                   "shape should start at the inset origin");
   }

   @Test
   void shapeIsPlacedAtTheInsetOriginForAnUpLeftShadow() {
      // the case that did not exist before the shadow became configurable
      ShapeShadow shadow = shadow(ShapeShadow.NORTH_WEST, 4, 0);
      Insets insets = ShapeShadowUtil.getShadowInsets(shadow);

      assertEquals(4, insets.left);
      assertEquals(4, insets.top);

      BufferedImage out = VSFaceUtil.addShadow(square(10), shadow, insets);

      assertEquals(Color.RED.getRGB(), out.getRGB(insets.left, insets.top));
      // and the shadow occupies the margin that was added above/left of it
      assertTrue(alphaAt(out, 0, 0) > 0, "shadow should fill the top-left margin");
   }

   @Test
   void shadowIsCastOnTheSideTheDirectionPointsAt() {
      ShapeShadow shadow = shadow(ShapeShadow.SOUTH_EAST, 4, 0);
      Insets insets = ShapeShadowUtil.getShadowInsets(shadow);

      BufferedImage out = VSFaceUtil.addShadow(square(10), shadow, insets);

      // the shadow reaches the bottom-right corner of the composite
      assertTrue(alphaAt(out, out.getWidth() - 1, out.getHeight() - 1) > 0,
                 "bottom-right should carry the shadow");
      // the top-right corner is past the shape (10 wide) and above the
      // shadow (offset down by 4), so nothing is drawn there
      assertEquals(0, alphaAt(out, out.getWidth() - 1, 0),
                   "top-right should be empty for a south-east shadow");
   }

   @Test
   void shadowUsesTheConfiguredColor() {
      ShapeShadow shadow = shadow(ShapeShadow.SOUTH_EAST, 4, 0);
      shadow.setColor("#0000ff");
      Insets insets = ShapeShadowUtil.getShadowInsets(shadow);

      BufferedImage out = VSFaceUtil.addShadow(square(10), shadow, insets);
      int rgb = out.getRGB(out.getWidth() - 1, out.getHeight() - 1);

      assertEquals(0, (rgb >> 16) & 0xff, "no red in a blue shadow");
      assertEquals(255, rgb & 0xff, "shadow should be blue");
   }

   @Test
   void aZeroBlurSkipsTheConvolveAndStillProducesAShadow() {
      // getGaussianBlurFilter rejects a radius below 1, so blur 0 must not blur
      ShapeShadow shadow = shadow(ShapeShadow.EAST, 3, 0);
      Insets insets = ShapeShadowUtil.getShadowInsets(shadow);

      BufferedImage out = assertDoesNotThrow(
         () -> VSFaceUtil.addShadow(square(8), shadow, insets));

      assertEquals(8 + 3, out.getWidth());
      assertEquals(8, out.getHeight());
   }

   @Test
   void theBlurFadesOutInsteadOfBeingCutOffFlat() {
      // regression: the insets used to allow only `blur` of margin, which put
      // the ConvolveOp's unconvolved EDGE_NO_OP band right where the soft edge
      // belonged, so the shadow ended in a hard step to fully transparent
      ShapeShadow shadow = new ShapeShadow();   // SE, distance 5, blur 6
      Insets insets = ShapeShadowUtil.getShadowInsets(shadow);

      BufferedImage out = VSFaceUtil.addShadow(square(10), shadow, insets);

      // walk right from inside the shadow to the edge of the composite, along a
      // row that the shape itself does not cover
      int y = insets.top + 9 + shadow.getOffsetY() / 2;
      int previous = Integer.MAX_VALUE;
      int steps = 0;

      for(int x = insets.left + 10; x < out.getWidth(); x++) {
         int alpha = alphaAt(out, x, y);
         assertTrue(alpha <= previous, "alpha should only decrease outward");

         if(alpha > 0 && alpha < 255) {
            steps++;
         }

         previous = alpha;
      }

      // a genuine gradient, not a single step from opaque to nothing
      assertTrue(steps >= 3,
                 "expected a soft outward falloff, saw " + steps + " partial steps");
      assertEquals(0, alphaAt(out, out.getWidth() - 1, y),
                   "the shadow should have faded to nothing by the edge");
   }

   @Test
   void aNullShadowReturnsTheImageUnchanged() {
      BufferedImage img = square(6);

      assertSame(img, VSFaceUtil.addShadow(img, null, new Insets(1, 1, 1, 1)));
      assertSame(img, VSFaceUtil.addShadow(img, new ShapeShadow(), null));
   }

   private static int alphaAt(BufferedImage img, int x, int y) {
      return (img.getRGB(x, y) >> 24) & 0xff;
   }
}
