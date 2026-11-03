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
package inetsoft.graph.element;

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.GeoCoord;
import inetsoft.graph.data.AbstractDataSetFilter;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geo.*;
import inetsoft.graph.geometry.PolygonGeometry;
import inetsoft.graph.internal.PolyShape;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;

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
    * Hint for the aggregate formula ("Sum", "Count", "Max" or "Min") used to combine the color
    * measure of all rows that share one polygon.
    * @hidden
    */
   public static final String HINT_COLOR_AGGREGATE = "polygon_color_aggregate";

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
      VisualModel vmodel = createAggregatedColorModel(data, graph);
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
    * Create the visual model. If the polygon color is a measure and several rows share the same
    * polygon (e.g. a state repeated for each city on a map that also binds a finer layer), only
    * the first row of a polygon gets a geometry, so its color would reflect that single row. In
    * that case (requested through the HINT_COLOR_AGGREGATE hint, an additive formula) the color
    * is computed from the aggregate of all rows of the polygon instead, and the shared color
    * scale is widened to cover the aggregated values so one legend covers all elements.
    */
   private VisualModel createAggregatedColorModel(DataSet data, GGraph graph) {
      VisualModel vmodel = createVisualModel(data);
      Object formula = getHint(HINT_COLOR_AGGREGATE);
      ColorFrame cframe = getColorFrame();
      String field = cframe == null ? null : cframe.getField();

      // multiple vars would need one aggregate per var
      if(!(formula instanceof String) || field == null || getVarCount() != 1) {
         return vmodel;
      }

      int col = data.indexOfHeader(field);

      if(col < 0) {
         return vmodel;
      }

      int max = getEndRow(data);
      Map<Integer, String> rowKeys = new HashMap<>();
      Map<String, Double> aggs = new HashMap<>();

      for(int i = getStartRow(data); i < max; i++) {
         if(!isAccepted(data, i)) {
            continue;
         }

         double[] tuple = scale(data, i, 0, graph);
         Object val = data.getData(col, i);

         if(tuple == null || !(val instanceof Number)) {
            continue;
         }

         String key = Arrays.toString(tuple);
         rowKeys.put(i, key);
         aggs.merge(key, ((Number) val).doubleValue(), (a, b) -> "Max".equals(formula) ?
            Math.max(a, b) : "Min".equals(formula) ? Math.min(a, b) : a + b);
      }

      if(aggs.isEmpty()) {
         return vmodel;
      }

      DataSet adata = new AbstractDataSetFilter(data) {
         @Override
         protected Object getData0(int c, int row) {
            String key = c == col ? rowKeys.get(row) : null;
            return key != null ? aggs.get(key) : super.getData0(c, row);
         }
      };

      Scale cscale = cframe.getScale();

      if(cscale instanceof LinearScale) {
         cscale.init(adata);
      }

      VisualModel cmodel = createVisualModel(adata);

      return new VisualModel(data, cframe, getSizeFrame(), getShapeFrame(), getTextureFrame(),
                             getLineFrame(), getTextFrame())
      {
         @Override
         public java.awt.Color getColor(String var, int idx) {
            return cmodel.getColor(var, idx);
         }
      };
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
