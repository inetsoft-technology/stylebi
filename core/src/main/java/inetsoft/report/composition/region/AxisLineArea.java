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

import inetsoft.graph.guide.axis.AxisLine;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.GTool;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Axis line area.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class AxisLineArea extends DefaultArea implements MenuArea, RollOverArea {
   /**
    * Constructor.
    */
   public AxisLineArea(AxisLine line, AffineTransform trans,
                       IndexedSet<String> palette, boolean secondary,
                       boolean facet)
   {
      super(line, trans);

      String ofield = line.getAxis().getScale().getMeasure();

      if(ofield != null) {
         this.fieldNameIdx = palette.put(ofield);
      }
      else {
         String[] fields = line.getAxis().getScale().getFields();
         this.fieldNameIdx = fields.length == 0 ? -1 : palette.put(fields[0]);
      }

      this.axisType = ((DefaultAxis) line.getAxis()).getAxisType();
      this.palette = palette;
      this.secondary = secondary;
      this.facet = facet;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeInt(fieldNameIdx);
      output.writeUTF(axisType);
      output.writeBoolean(secondary);
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      Point2D p = getRelPos();
      Rectangle2D.Double rect2d = (Rectangle2D.Double)
         GTool.transform(getBounds(vobj), trans);
      rect2d.x = rect2d.x - p.getX();
      rect2d.y = rect2d.y - p.getY();
      int min = facet ? 2 : 3;

      if(GTool.isHorizontal(vobj.getScreenTransform()) && rect2d.height > 0
         && rect2d.height < 3)
      {
         rect2d.height = min;
      }
      else if(!GTool.isHorizontal(vobj.getScreenTransform()) && rect2d.width > 0
         && rect2d.width < 3)
      {
         // must make sure the axis line area doesn't overlap label areas,
         // otherwise the area lookup won't work
         rect2d.x = Math.max(0, rect2d.x - 2);
         rect2d.width = min;
      }

      return new Region[] {new RectangleRegion(rect2d)};
   }

   /**
    * Get axis type.
    * @return the type of the axis, x or y.
    */
   public String getAxisType() {
      return axisType;
   }

   /**
    * Paint the region.
    * @param g the graphics.
    */
   @Override
   public void paintArea(Graphics g, Color color) {
      ((RectangleRegion) getRegion()).fill(g, color);
   }

    /**
    * Get the data path for this area.
    */
   @Override
   public ChartAreaInfo getChartAreaInfo() {
      ChartAreaInfo info = ChartAreaInfo.createAxisLine();
      info.setProperty("axisType", axisType);
      info.setProperty("column", getFieldName());
      info.setProperty("secondary", secondary);
      info.setProperty("linear", true);
      return info;
   }

   /**
    * Get the field name.
    * @return the field name.
    */
   public String getFieldName() {
      if(fieldNameIdx < 0) {
         return null;
      }

      return palette.get(fieldNameIdx);
   }

   /**
    * Check if this is a secondary axis.
    */
   public boolean isSecondary() {
      return secondary;
   }

   private int fieldNameIdx;
   private String axisType;
   private boolean secondary;
   private boolean facet;
}
