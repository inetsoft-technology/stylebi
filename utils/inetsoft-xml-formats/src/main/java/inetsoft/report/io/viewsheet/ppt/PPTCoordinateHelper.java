/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
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
