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
package inetsoft.graph.mxgraph.view;

import inetsoft.graph.mxgraph.util.mxConstants;

import java.util.*;

/**
 * Singleton class that acts as a global converter from string to object values
 * in a style. This is currently only used to perimeters and edge styles.
 */
public class mxStyleRegistry {

   /**
    * Maps from strings to objects.
    */
   protected static Map<String, Object> values = new Hashtable<String, Object>();

   // Registers the known object styles
   static {
      putValue(mxConstants.EDGESTYLE_ELBOW, mxEdgeStyle.ElbowConnector);
      putValue(mxConstants.EDGESTYLE_ENTITY_RELATION,
               mxEdgeStyle.EntityRelation);
      putValue(mxConstants.EDGESTYLE_LOOP, mxEdgeStyle.Loop);
      putValue(mxConstants.EDGESTYLE_SIDETOSIDE, mxEdgeStyle.SideToSide);
      putValue(mxConstants.EDGESTYLE_TOPTOBOTTOM, mxEdgeStyle.TopToBottom);
      putValue(mxConstants.EDGESTYLE_ORTHOGONAL, mxEdgeStyle.OrthConnector);
      putValue(mxConstants.EDGESTYLE_SEGMENT, mxEdgeStyle.SegmentConnector);

      putValue(mxConstants.PERIMETER_ELLIPSE, mxPerimeter.EllipsePerimeter);
      putValue(mxConstants.PERIMETER_RECTANGLE,
               mxPerimeter.RectanglePerimeter);
      putValue(mxConstants.PERIMETER_RHOMBUS, mxPerimeter.RhombusPerimeter);
      putValue(mxConstants.PERIMETER_TRIANGLE, mxPerimeter.TrianglePerimeter);
      putValue(mxConstants.PERIMETER_HEXAGON, mxPerimeter.HexagonPerimeter);
   }

   /**
    * Puts the given object into the registry under the given name.
    */
   public static void putValue(String name, Object value)
   {
      values.put(name, value);
   }

   /**
    * Returns the value associated with the given name.
    */
   public static Object getValue(String name)
   {
      return values.get(name);
   }

   /**
    * Returns the name for the given value.
    */
   public static String getName(Object value)
   {
      Iterator<Map.Entry<String, Object>> it = values.entrySet().iterator();

      while(it.hasNext()) {
         Map.Entry<String, Object> entry = it.next();

         if(entry.getValue() == value) {
            return entry.getKey();
         }
      }

      return null;
   }

}
