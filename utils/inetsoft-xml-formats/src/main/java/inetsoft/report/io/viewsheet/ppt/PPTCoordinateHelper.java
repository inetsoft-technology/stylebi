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
package inetsoft.report.io.viewsheet.ppt;

import inetsoft.report.io.viewsheet.*;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

import java.awt.*;
import java.awt.geom.*;


/**
 * The class is used to calculate the coordinate of the assemblies.
 *
 * @version 8.5, 8/10/2006
 * @author InetSoft Technology Corp
 */
public class PPTCoordinateHelper extends CoordinateHelper {
   @Override
   public int getTitleHeightOffset(VSAssemblyInfo info) {
      if(info instanceof TableDataVSAssemblyInfo &&
         ((TableDataVSAssemblyInfo) info).isTitleVisible())
      {
         return 0;
      }

      return super.getTitleHeightOffset(info);
   }

   /**
    * Get the position in PPT, applying the ratio.
    */
   @Override
   public Point getOutputPosition(Point pt) {
      return new Point((int) Math.floor(pt.x * PPTVSUtil.PIXEL_TO_POINT),
                       (int) Math.floor(pt.y * PPTVSUtil.PIXEL_TO_POINT));
   }

   /**
    * Get the size in PPT, applying the ratio.
    */
   @Override
   public Dimension getOutputSize(Dimension size) {
      return new Dimension(
         (int) Math.ceil(size.width * PPTVSUtil.PIXEL_TO_POINT),
         (int) Math.ceil(size.height * PPTVSUtil.PIXEL_TO_POINT));
   }

   /**
    * Get pixel to point scale.
    */
   @Override
   public double getScale() {
      return PPTVSUtil.PIXEL_TO_POINT;
   }

   /**
    * Get the bounds in PPT, applying the ratio.
    */
   public Rectangle2D getOutputBounds(double x, double y, double w, double h) {
      return new Rectangle2D.Double(
         (int) Math.floor(x * PPTVSUtil.PIXEL_TO_POINT),
         (int) Math.floor(y * PPTVSUtil.PIXEL_TO_POINT),
         (int) Math.floor(w * PPTVSUtil.PIXEL_TO_POINT),
         (int) Math.floor(h * PPTVSUtil.PIXEL_TO_POINT));
   }
}
