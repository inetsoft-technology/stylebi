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

import inetsoft.graph.data.DataSet;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendItem;
import inetsoft.graph.internal.GTool;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

/**
 * ListLegendContentArea defines the method of write data to
 *  an OutputStream and parse it from an InputStream.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ListLegendContentArea extends LegendContentArea implements RollOverArea {
   /**
    * Constructor.
    */
   public ListLegendContentArea(Legend legend, List<String> targetFields,
                                boolean sharedColor, AffineTransform trans,
                                DataSet data, IndexedSet<String> palette)
   {
      super(legend, targetFields, sharedColor, trans, palette);
      this.itemAreas = getAreas(legend, data, palette);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      int rowCount = itemAreas.length;
      int colCount = rowCount > 0 ? itemAreas[0].length : 0;
      output.writeDouble(rowCount);
      output.writeDouble(colCount);

      for(int i = 0; i < rowCount; i++) {
         for(int j = 0; j < colCount; j++) {
            output.writeBoolean(itemAreas[i][j] == null);

            if(itemAreas[i][j] != null) {
               output.writeUTF(itemAreas[i][j].getClassName());
               itemAreas[i][j].setRelPos(getRelPos());
               itemAreas[i][j].writeData(output);
            }
         }
      }
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      Rectangle2D bounds = ((Legend) vobj).getContentPreferredBounds();
      Rectangle2D.Double rect2d = (Rectangle2D.Double) GTool.transform(bounds, trans);
      Point2D p = getRelPos();
      rect2d.x = rect2d.x - p.getX();
      rect2d.y = rect2d.y - p.getY();

      return new Region[] {new RectangleRegion(rect2d)};
   }

   /**
    * Paint area.
    * @param g the graphic of the area.
    */
   @Override
   public void paintArea(Graphics g, Color color) {
      RectangleRegion region = (RectangleRegion) getRegion();

      if(region != null) {
         region.fill(g, color);
      }
   }

   /**
    * Set aesthetic type.
    */
   @Override
   public void setAestheticType(String aestheticType) {
      this.aestheticType = aestheticType;

      if(itemAreas == null) {
         return;
      }

      int rowCount = itemAreas.length;
      int colCount = rowCount > 0 ? itemAreas[0].length : 0;

      for(int i = 0; i < rowCount; i++) {
         for(int j = 0; j < colCount; j++) {
            if(itemAreas[i][j] != null) {
               ((LegendItemArea) itemAreas[i][j]).setAestheticType(aestheticType);
            }
         }
      }
   }

   /**
    * Get legend item areas.
    */
   private DefaultArea[][] getAreas(Legend legend, DataSet data, IndexedSet<String> palette) {
      LegendItem[][] items = legend.getItems();
      int rowCount = items.length;
      int colCount = rowCount > 0 ? items[0].length : 0;
      DefaultArea[][] lareas = new DefaultArea[rowCount][colCount];

      for(int i = 0; i < rowCount; i++) {
         for(int j = 0; j < colCount; j++) {
            if(items[i][j] == null) {
               continue;
            }

            lareas[i][j] = new LegendItemArea(items[i][j], legend, targetFields, sharedColor,
                                              trans, data, palette);
         }
      }

      return lareas;
   }

   /**
    * Get all child areas.
    */
   @Override
   public DefaultArea[] getAllAreas() {
      if(itemAreas == null) {
         return new DefaultArea[0];
      }

      Vector vec = new Vector();

      for(int i = 0; i < itemAreas.length; i++) {
         for(int j = 0; j < itemAreas[0].length; j++) {
            if(itemAreas[i][j] != null) {
               vec.add(itemAreas[i][j]);
            }
         }
      }

      DefaultArea[] areas = new DefaultArea[vec.size()];

      for(int i = 0; i < vec.size(); i++) {
         areas[i] = (DefaultArea) vec.get(i);
      }

      return areas;
   }

   /**
    * Set the relative position. The position is after transform.
    * For example, if AxisLineArea, the relative position is AxisArea's top left
    * corner position.
    */
   @Override
   public void setRelPos(Point2D pos) {
      super.setRelPos(pos);

      Legend legend = (Legend) getVisualizable();
      Rectangle2D bounds = legend.getBounds();
      Rectangle2D contentBounds = legend.getContentBounds();

      Point2D pos2 = new Point2D.Double(pos.getX() + contentBounds.getX() - bounds.getX(),
                                        pos.getY() + contentBounds.getY() - bounds.getY());

      for(int i = 0; i < itemAreas.length; i++) {
         for(int j = 0; j < itemAreas[0].length; j++) {
            if(itemAreas[i][j] != null) {
               itemAreas[i][j].setRelPos(pos2);
            }
         }
      }
   }

   private DefaultArea[][] itemAreas;
}
