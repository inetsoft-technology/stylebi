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
package inetsoft.report.io.viewsheet;

import inetsoft.graph.internal.DimensionD;
import inetsoft.uql.viewsheet.ShapeShadow;
import inetsoft.uql.viewsheet.internal.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Helper for laying out the drop shadow of a shape assembly when exporting.
 *
 * A configurable shadow can be cast in any of eight directions, so the image
 * canvas and the destination rectangle both have to grow on whichever sides
 * the shadow actually falls on. Without this the shadow is clipped to (and
 * squeezed into) the assembly's own bounds.
 *
 * @version 14.0
 * @author InetSoft Technology Corp
 */
public final class ShapeShadowUtil {
   private ShapeShadowUtil() {
   }

   /**
    * Check whether the assembly is a shape drawing a configurable shadow.
    *
    * Line is excluded: it predates configurable shadow settings and has no
    * UI/script path to configure one, so it keeps its old fixed-size shadow
    * (VSShape.paintComponent) instead of the configurable-shadow insets this
    * class computes.
    */
   public static boolean isShapeShadow(VSAssemblyInfo info) {
      return info instanceof ShapeVSAssemblyInfo &&
         !(info instanceof LineVSAssemblyInfo) &&
         ((ShapeVSAssemblyInfo) info).isShadow();
   }

   /**
    * Get the amount, in pixels, that the shadow extends past each side of the
    * shape. Returns zero insets for anything that is not a shape drawing a
    * shadow, so callers can apply this unconditionally.
    */
   public static Insets getShadowInsets(VSAssemblyInfo info) {
      if(!isShapeShadow(info)) {
         return NO_SHADOW;
      }

      return getShadowInsets(((ShapeVSAssemblyInfo) info).getShadowInfo());
   }

   /**
    * Get the amount, in pixels, that the shadow extends past each side of the
    * shape.
    */
   public static Insets getShadowInsets(ShapeShadow shadow) {
      if(shadow == null) {
         return NO_SHADOW;
      }

      int offsetX = shadow.getOffsetX();
      int offsetY = shadow.getOffsetY();
      // twice the radius: the blur reaches about one radius past the shape,
      // and the ConvolveOp leaves the outermost radius of the layer
      // unconvolved (EDGE_NO_OP), so that band has to sit in empty space or
      // the soft edge is cut off flat
      int margin = 2 * shadow.getBlurRadius();

      return new Insets(Math.max(0, -offsetY) + margin,   // top
                        Math.max(0, -offsetX) + margin,   // left
                        Math.max(0, offsetY) + margin,    // bottom
                        Math.max(0, offsetX) + margin);   // right
   }

   /**
    * Get the shadow insets in the shape image's own coordinate space, which is
    * scaled by the assembly's scaling ratio (see VSShape.getImageSize). The
    * destination rectangle is built from the unscaled size, so the image has
    * to grow by the scaled insets for the shape to still map onto its own
    * position.
    */
   public static Insets getScaledShadowInsets(VSAssemblyInfo info) {
      Insets insets = getShadowInsets(info);

      if(insets == NO_SHADOW) {
         return insets;
      }

      DimensionD ratio = ((ShapeVSAssemblyInfo) info).getScalingRatio();

      // annotation shapes are not scaled, matching VSShape.getImageSize
      if(ratio == null || info instanceof AnnotationLineVSAssemblyInfo ||
         info instanceof AnnotationRectangleVSAssemblyInfo)
      {
         return insets;
      }

      return new Insets((int) Math.round(insets.top * ratio.getHeight()),
                        (int) Math.round(insets.left * ratio.getWidth()),
                        (int) Math.round(insets.bottom * ratio.getHeight()),
                        (int) Math.round(insets.right * ratio.getWidth()));
   }

   /**
    * Grow a destination rectangle so the shadow of the assembly is not
    * clipped. Returns the rectangle unchanged when there is no shadow.
    *
    * @param bounds the assembly's own bounds.
    * @param info the assembly info.
    * @param scale the coordinate scale applied to the bounds, so the insets
    *              are expressed in the same units.
    */
   public static Rectangle2D expandForShadow(Rectangle2D bounds, VSAssemblyInfo info,
                                             double scale)
   {
      if(bounds == null || !isShapeShadow(info)) {
         return bounds;
      }

      Insets insets = getShadowInsets(info);

      return new Rectangle2D.Double(
         bounds.getX() - insets.left * scale,
         bounds.getY() - insets.top * scale,
         bounds.getWidth() + (insets.left + insets.right) * scale,
         bounds.getHeight() + (insets.top + insets.bottom) * scale);
   }

   /**
    * Grow a destination rectangle so the shadow of the assembly is not
    * clipped, in unscaled pixel coordinates.
    */
   public static Rectangle2D expandForShadow(Rectangle2D bounds, VSAssemblyInfo info) {
      return expandForShadow(bounds, info, 1);
   }

   private static final Insets NO_SHADOW = new Insets(0, 0, 0, 0);
}
