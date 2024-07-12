/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.region;

import inetsoft.graph.VGraph;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.*;

/**
 * IndexContainerArea defines the method of write data to an OutputStream
 * and parse it from an InputStream.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class IndexContainerArea extends DefaultArea implements ContainerArea {
   /**
    * Constructor.
    * @param graph VGraph.
    */
   public IndexContainerArea(VGraph graph, AffineTransform trans) {
      super(graph, trans);

      indexes = new ArrayList<>();
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);

      int len = indexes.size();
      output.writeInt(len);

      for(int i = 0; i < len; i++) {
         output.writeInt(indexes.get(i));
      }
   }

   /**
    * Add index.
    * @param index the specified area index.
    */
   public void addIndex(int index) {
      indexes.add(index);
   }

   /**
    * Set bounds for this area.
    */
   public void setBounds(double x, double y, double w, double h) {
      bounds = new Rectangle2D.Double(x, y, w, h);
   }

   /**
    * Get region of this area.
    */
   @Override
   public Region[] getRegions() {
      return new Region[] {new RectangleRegion(bounds)};
   }

   /**
    * Set all childs area of GridContainerArea.
    */
   public void setAllChildAreas(DefaultArea[] allChildAreas) {
      this.allChildAreas = allChildAreas;
   }

   /**
    * Get all areas.
    */
   @Override
   public DefaultArea[] getAllAreas() {
      if(allChildAreas == null || allChildAreas.length == 0) {
         return new DefaultArea[0];
      }

      return getChildAreas(allChildAreas);
   }

   private DefaultArea[] allChildAreas;
   private List<Integer> indexes;
   private Rectangle2D bounds = new Rectangle2D.Double(0, 0, 0, 0);
}
