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

import inetsoft.graph.VGraph;
import inetsoft.graph.internal.GTool;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;

import java.awt.geom.AffineTransform;

/**
 * ContentArea defines the method of write information to an OutputStream and
 * parse it from an InputStream.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ContentArea extends AbstractArea {
   /**
    * Constructor.
    * @param vobj Visualizable object.
    */
   public ContentArea(VGraph graph, AffineTransform trans) {
      super(trans);
      this.graph = graph;
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      GraphBounds bounds = new GraphBounds(graph, graph, null);
      return new Region[] {new RectangleRegion(
         GTool.transform(bounds.getContentBounds(), trans))};
   }

   private VGraph graph;
}
