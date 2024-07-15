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
package inetsoft.report.composition.region;

import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.VDimensionLabel;
import inetsoft.report.Hyperlink;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * RadarDimensionLabelArea class.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class RadarDimensionLabelArea extends DimensionLabelArea {
   /**
    * Constructor.
    * @param dlabel VDimensionLabel.
    */
   public RadarDimensionLabelArea(Axis axis, VDimensionLabel dlabel,
                                  boolean isOuter, AffineTransform trans,
                                  IndexedSet<String> palette)
   {
      this(axis, dlabel, isOuter, trans, palette, null, null);
   }

   /**
    * Constructor.
    * @param dlabel VDimensionLabel.
    */
   public RadarDimensionLabelArea(Axis axis, VDimensionLabel dlabel,
                                  boolean isOuter, AffineTransform trans,
                                  IndexedSet<String> palette,
                                  Hyperlink.Ref[] links, String linkURI)
   {
      super(axis, dlabel, isOuter, trans, palette, links, linkURI);
   }

   /**
    * Method to test whether regions contain the specified point.
    * @param point the specified point.
    */
   @Override
   public boolean contains(Point point) {
      return getRegion().contains(point.x, point.y);
   }

   /**
    * Text horizontal alignment enabled.
    */
   @Override
   public boolean isHAlignmentEnabled() {
      return false;
   }

   /**
    * Text vertical alignment enabled.
    */
   @Override
   public boolean isVAlignmentEnabled() {
      return false;
   }
}
