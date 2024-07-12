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
package inetsoft.graph.element;

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.GeoCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geo.*;
import inetsoft.graph.geometry.PolygonGeometry;
import inetsoft.graph.internal.PolyShape;

import java.util.*;

/**
 * A polygon element partitions the coordinate space into non-overlapping
 * regions.
 *
 * @hidden experimental
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class PolygonElement extends GraphElement {
   /**
    * Create an empty element. Dims and vars must be added explicitly.
    */
   public PolygonElement() {
   }

   /**
    * Create a polygon element for a single column (1d).
    */
   public PolygonElement(String field1) {
      super(field1);
   }

   /**
    * Create a polygon element for two columns (2d).
    */
   public PolygonElement(String field1, String field2) {
      super(field1, field2);
   }

   /**
    * Create a polygon element for three columns (3d).
    */
   public PolygonElement(String field1, String field2, String field3) {
      super(field1, field2, field3);
   }

   /**
    * Create the geometry objects for the chartLens.
    * @hidden
    * @param data the chartLens to plot using this element.
    * @param graph the containing graph.
    */
   @Override
   public void createGeometry(DataSet data, GGraph graph) {
      VisualModel vmodel = createVisualModel(data);
      Coordinate coord = graph.getCoordinate();
      GeoCoord gcoord = coord instanceof GeoCoord ? (GeoCoord) coord : null;
      int max = getEndRow(data);
      Set<String> added = new HashSet<>();

      for(int i = getStartRow(data); i < max; i++) {
         if(!isAccepted(data, i)) {
            continue;
         }

         for(int v = 0; v < getVarCount(); v++) {
            double[] tuple = scale(data, i, v, graph);

            if(tuple == null) {
               continue;
            }

            String tupleKey = Arrays.toString(tuple);

            // if a map binds both city and state, state may be repeated many times, and we
            // should only add one.
            if(added.contains(tupleKey)) {
               continue;
            }

            added.add(tupleKey);
            String vname = getVar(v);
            PolygonGeometry gobj = new PolygonGeometry(this, graph, vname, i, vmodel, tuple) {
               @Override
               public Object getText(int idx) {
                  final Object text = super.getText(idx);

                  if(text == null || "\n".equals(text)) {
                     TextFrame textFrame = getTextFrame();

                     if(textFrame instanceof ValueTextFrame) {
                        textFrame = ((ValueTextFrame) textFrame).getValueFrame();
                     }

                     final GShape shape = this.getShape(0);

                     if(textFrame instanceof GeoTextFrame &&
                        data instanceof GeoDataSet && gcoord != null && shape instanceof PolyShape)
                     {
                        final GeoShape geoShape = ((PolyShape) shape).getShape();
                        return ((GeoTextFrame) textFrame).getMapText(geoShape);
                     }
                  }

                  return text;
               }
            };

            gobj.setSubRowIndex(i);
            gobj.setRowIndex(getRootRowIndex(data, i));
            gobj.setColIndex(getRootColIndex(data, vname));
            graph.addGeometry(gobj);
         }
      }
   }

   /**
    * Get max geometry count.
    * @hidden
    */
   @Override
   public int getEndRow(DataSet data) {
      if(data instanceof GeoDataSet) {
         return ((GeoDataSet) data).getFullRowCount() - getStartRow(data);
      }

      return super.getEndRow(data);
   }

   private static final long serialVersionUID = 1L;
}
