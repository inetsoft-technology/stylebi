/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.region;

import inetsoft.graph.visual.LabelFormVO;
import inetsoft.report.internal.RectangleRegion;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * LabelArea is the bounds for a non-interactive label, e.g. target line label.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class LabelArea extends DefaultArea implements RollOverArea {
   /**
    * Constructor.
    */
   public LabelArea(LabelFormVO text, AffineTransform trans,
                    IndexedSet<String> palette)
   {
      super(text, trans);
      Object indexObj = text.getForm().getHint("target.index");

      if(indexObj instanceof Integer) {
         targetIndex = (Integer) indexObj;
      }

      labelIdx = palette.put(text.getLabel());
      this.palette = palette;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeInt(labelIdx);
      output.writeInt(targetIndex);
   }

   /**
    * Get the data info for this area.
    */
   @Override
   public ChartAreaInfo getChartAreaInfo() {
      return ChartAreaInfo.createLabel(targetIndex);
   }

   /**
    * Paint area.
    * @param g the graphic of the area.
    */
   @Override
   public void paintArea(Graphics g, Color color) {
      ((RectangleRegion) getRegion()).fill(g, color);
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
    * Get target index of this area.
    */
   public int getTargetIndex() {
      return targetIndex;
   }

   /**
    * Get the label of this area.
    */
   public String getLabel() {
      if(labelIdx < 0) {
         return null;
      }

      return palette.get(labelIdx);
   }

   /**
    * Target index which will be used to map this area to the original graph
    * target object
    */
   private int targetIndex;
   private int labelIdx;
}
