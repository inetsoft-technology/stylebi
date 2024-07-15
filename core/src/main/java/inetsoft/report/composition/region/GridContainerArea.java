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

import inetsoft.graph.VGraph;
import inetsoft.graph.internal.GTool;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * GridContainerArea defines the method of write data to an OutputStream
 * and parse it from an InputStream.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class GridContainerArea extends DefaultArea implements ContainerArea {
   /**
    * Constructor.
    * @param graph VGraph.
    */
   protected GridContainerArea(VGraph graph, AffineTransform trans,
                               IndexedSet<String> palette) {
      super(graph, trans);
      this.palette = palette;
   }

   /**
    * Init the area.
    */
   protected void init() {
      this.childAreas = getChildAreas();
      initIndexContainerAreas();
      processIndexes(childAreas);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);

      int len = childAreas == null ? 0 : childAreas.length;
      output.writeInt(len);

      for(int i = 0; i < len; i++) {
         output.writeUTF(childAreas[i].getClassName());
         childAreas[i].writeData(output);
      }

      int rowCount = indexContainerAreas == null ? 0
         : indexContainerAreas.length;
      int colCount = rowCount > 0 ? indexContainerAreas[0].length : 0;
      output.writeInt(rowCount);
      output.writeInt(colCount);

      for(int i = 0; i < rowCount; i++) {
         for(int j = 0; j < colCount; j++) {
            indexContainerAreas[i][j].writeData(output);
         }
      }
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      double x = 0, y = 0, right = 0, bottom = 0;

      for(int i = 0; i < childAreas.length; i++) {
         DefaultArea area = childAreas[i];
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
      return new Region[] {new RectangleRegion(GTool.transform(rect, trans))};
   }

   /**
    * Get child areas.
    */
   protected abstract DefaultArea[] getChildAreas();

   /**
    * Process index container areas.
    */
   protected void processIndexes(DefaultArea[] areas) {
      if(indexContainerAreas == null) {
         return;
      }

      GraphBounds gbounds = new GraphBounds((VGraph) vobj, (VGraph) vobj, null);
      Rectangle2D plotBounds = gbounds.getPlotBounds();
      int rowCnt = indexContainerAreas.length;
      int colCnt = indexContainerAreas[0].length;
      double w = plotBounds.getWidth() / rowCnt;
      double h = plotBounds.getHeight() / colCnt;
      Region[][] areaRegions = new Region[areas.length][];

      for(int i = 0; i < areas.length; i++) {
         areaRegions[i] = areas[i].getRegions();
      }

      for(int j = 0; j < rowCnt; j++) {
         for(int k = 0; k < colCnt; k++) {
            double x = k * w;
            double y = j * h;

            indexContainerAreas[j][k].setBounds(x, y, w, h);

            for(int i = 0; i < areas.length; i++) {
               Region[] regions = areaRegions[i];

               if(regions == null || regions.length == 0) {
                  continue;
               }

               // must test all the regions, or will be cause error, such as
               // vo is Line
               for(int t = 0; t < regions.length; t++) {
                  Rectangle rect = regions[t].getBounds();

                  if(rect.intersects(x, y, w, h)) {
                     indexContainerAreas[j][k].addIndex(i);
                     break;
                  }
               }
            }
         }
      }
   }

   /**
    * Init index container areas.
    */
   private void initIndexContainerAreas() {
      if(indexContainerAreas == null) {
         indexContainerAreas = new IndexContainerArea[3][3];
      }

      for(int i = 0; i < indexContainerAreas.length; i++) {
         for(int j = 0; j < indexContainerAreas[0].length; j++) {
            indexContainerAreas[i][j] =
               new IndexContainerArea((VGraph) vobj, trans);
            indexContainerAreas[i][j].setAllChildAreas(childAreas);
         }
      }
   }

   /**
    * Get all areas.
    */
   @Override
   public DefaultArea[] getAllAreas() {
      return getChildAreas();
   }

   /**
    * Get all original areas.
    */
   public DefaultArea[] getOriginalAreas() {
      return childAreas;
   }

   protected IndexedSet<String> palette;
   private DefaultArea[] childAreas;
   private IndexContainerArea[][] indexContainerAreas;
}
