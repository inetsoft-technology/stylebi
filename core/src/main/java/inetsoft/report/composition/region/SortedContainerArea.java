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

import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * SortedContainerArea Class.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class SortedContainerArea extends DefaultArea implements ContainerArea {
   /**
    * Constructor.
    */
   public SortedContainerArea(AbstractArea[] areas, String direction,
                              boolean isRotated, AffineTransform trans) {
      super(null, trans);

      this.direction = direction;
      this.isRotated = isRotated;

      ArrayList alist = new ArrayList();

      for(Object v : areas) {
         if(v != null) {
            alist.add(v);
         }
      }

      this.areas = (AbstractArea[])
         alist.toArray(new AbstractArea[alist.size()]);
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      double x = Double.MAX_VALUE, y = Double.MAX_VALUE;
      double right = 0, bottom = 0;

      for(int i = 0; i < areas.length; i++) {
         AbstractArea area = areas[i];
         Region[] regions = area.getRegions();

         if(regions.length == 0 || regions[0] == null) {
            continue;
         }

         Rectangle rect = regions[0].getBounds();
         x = Math.min(x, rect.getX());
         y = Math.min(y, rect.getY());
         right = Math.max(right, rect.getX() + rect.getWidth());
         bottom = Math.max(bottom, rect.getY() + rect.getHeight());
      }

      Rectangle2D rect = new Rectangle2D.Double(x, y, right - x, bottom - y);
      return new Region[] {new RectangleRegion(rect)};
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeInt(areas.length);

      for(int i = 0; i < areas.length; i++) {
         output.writeUTF(areas[i].getClassName());
         areas[i].writeData(output);
      }

      output.writeUTF(direction);
      output.writeBoolean(isRotated);
   }

   /**
    * Set the relative position. The position is after transform.
    * For example, if AxisLineArea, the relative position is AxisArea's top left
    * corner position.
    */
   @Override
   public void setRelPos(Point2D pos) {
      for(int i = 0; i < areas.length; i++) {
         areas[i].setRelPos(pos);
      }
   }

   /**
    * Get all areas.
    */
   @Override
   public DefaultArea[] getAllAreas() {
      return getChildAreas(areas);
   }

   private AbstractArea[] areas;
   private String direction;
   private boolean isRotated;
   private SortedAreaLocator locator;
}
