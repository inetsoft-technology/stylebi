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

import inetsoft.report.internal.Region;
import inetsoft.util.DataSerializable;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.*;

/**
 * AbstractArea contains regions.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public abstract class AbstractArea implements DataSerializable, Serializable {
   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      Region[] regions = getRegions();
      int len = regions == null ? 0 : regions.length;
      output.writeInt(len);

      for(int i = 0; i < len; i++) {
         output.writeUTF(regions[i].getClassName());
         regions[i].writeData(output);
      }
   }

   /**
    * Get the class name.
    */
   protected String getClassName() {
      return getClass().getName();
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }

   /**
    * Get regions.
    */
   public abstract Region[] getRegions();

   /**
    * Get the first region.
    */
   public Region getRegion() {
      Region[] regions = getRegions();

      if(regions == null || regions.length == 0) {
         return null;
      }

      return getRegions()[0];
   }

   /**
    * Constructor.
    */
   protected AbstractArea(AffineTransform trans) {
      this.trans = trans;
   }

   /**
    * Set the relative position. The position is after transform.
    * For example, if AxisLineArea, the relative position is AxisArea's top left
    * corner position.
    */
   public void setRelPos(Point2D pos) {
      this.rpos = pos;
   }

   /**
    * Get the relative position.
    */
   public Point2D getRelPos() {
      return rpos;
   }

   /**
    * Get the data info for this area.
    */
   public ChartAreaInfo getChartAreaInfo() {
      return null;
   }

   /**
    * Get all child areas.
    */
   protected DefaultArea[] getChildAreas(AbstractArea[] areas) {
      final List<DefaultArea> allAreas = new ArrayList<>();

      for(final AbstractArea area : areas) {
         if(area instanceof ContainerArea) {
            DefaultArea[] dareas = ((ContainerArea) area).getAllAreas();
            Collections.addAll(allAreas, dareas);
         }
         else {
            allAreas.add(((DefaultArea) area));
         }
      }

      return allAreas.toArray(new DefaultArea[0]);
   }

   protected void setLightWeight(boolean lightWeight) {
      this.lightWeight = lightWeight;
   }

   protected boolean isLightWeight() {
      return lightWeight;
   }

   protected AffineTransform trans;
   private Point2D rpos = new Point2D.Double(0, 0);
   private boolean lightWeight;
}
