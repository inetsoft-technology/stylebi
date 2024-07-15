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
import inetsoft.report.internal.*;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * MeasureLabelsArea Class.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class MeasureLabelsArea extends DefaultArea implements MenuArea, RollOverArea {
   /**
    * Constructor.
    */
   public MeasureLabelsArea(DefaultAxis vAxis, AffineTransform trans,
                            IndexedSet<String> palette, boolean secondary) {
      super(vAxis, trans);

      String name = vAxis.getScale().getMeasure();

      if(name == null) {
         String[] fields = vAxis.getScale().getFields();
         name = fields.length > 0 ? fields[0] : null;
      }

      this.measureNameIdx = palette.put(name);
      this.axisType = vAxis.getAxisType();
      this.palette = palette;
      this.secondary = secondary;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeInt(measureNameIdx);
      output.writeUTF(axisType);
      output.writeBoolean(secondary);
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      boolean horizontal = GTool.isHorizontal(vobj.getScreenTransform());
      boolean tickDown = ((DefaultAxis) vobj).isTickDown();
      boolean tickVisible = ((DefaultAxis) vobj).isTickVisible();
      Rectangle2D bounds1 = getBounds(vobj);
      AxisLine line = ((DefaultAxis) vobj).getAxisLine();
      Rectangle2D bounds2 = line == null ? new Rectangle2D.Double(0, 0, 0, 0) :
         line.getBounds();
      Rectangle2D rect;

      if(!horizontal) {
         double width = tickDown && tickVisible ? bounds1.getWidth() - bounds2.getWidth() :
                                   bounds1.getWidth();
         rect = new Rectangle2D.Double(
            bounds1.getX(), bounds1.getY(), width, bounds1.getHeight());
      }
      else {
         double height = tickDown && tickVisible ? bounds1.getHeight() - bounds2.getHeight() :
                                    bounds1.getHeight();
         rect = new Rectangle2D.Double(
            bounds1.getX(), bounds1.getY(), bounds1.getWidth(), height);
      }

      Rectangle2D.Double rect2d =
         (Rectangle2D.Double) GTool.transform(rect, trans);
      Point2D p = getRelPos();
      rect2d.x = rect2d.x - p.getX();
      rect2d.y = rect2d.y - p.getY();

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
    * Get the measure name.
    * @return the measure name.
    */
   public String getMeasureName() {
      if(measureNameIdx < 0) {
         return null;
      }

      return palette.get(measureNameIdx);
   }

   /**
    * Get the measure name index in palette.
    * @return the measure name index.
    */
   public int getMeasureNameIdx() {
      return measureNameIdx;
   }

   /**
    * Paint the region.
    * @param g the graphics.
    */
   @Override
   public void paintArea(Graphics g, Color color) {
      if(getRegion() instanceof RectangleRegion) {
         ((RectangleRegion) getRegion()).fill(g, color);
      }
      else if(getRegion() instanceof PolygonRegion){
         getRegion().drawBorder(g, color);
         Color oldColor = g.getColor();
         g.setColor(color);
         ((PolygonRegion) getRegion()).fillPolygon(g);
         g.setColor(oldColor);
      }
   }

   /**
    * Get the data info for this area.
    */
   @Override
   public ChartAreaInfo getChartAreaInfo() {
      ChartAreaInfo info = ChartAreaInfo.createMeasureLabel();
      info.setProperty("axisType", axisType);
      info.setProperty("column", getMeasureName());
      info.setProperty("secondary", secondary);
      info.setProperty("linear", true);

      return info;
   }

    /**
    * Check if this is a secondary axis.
    */
   public boolean isSecondary() {
      return secondary;
   }

   private int measureNameIdx;
   private boolean secondary;
   protected String axisType;
}
