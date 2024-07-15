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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.GShape;
import inetsoft.report.StyleConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * This class defines the common API for all shape frames.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class ShapeFrameWrapper extends VisualFrameWrapper {
   /**
    * Find the ID of a shape.
    */
   public static String getID(GShape shape) {
      if(shape == null) {
         return null;
      }

      for(String id: SHAPES.keySet()) {
         if(shape.equals(SHAPES.get(id))) {
            return id;
         }
      }

      for(String name : ImageShapes.getShapeNames()) {
         // should ignore other option setting (e.g. applyColor) when identifying id. (59673)
         if(shape.getLegendId().equals(ImageShapes.getShape(name).getLegendId())) {
            return name;
         }
      }

      return null;
   }

   /**
    * Get the shape according to shape id.
    */
   public static final GShape getGShape(String id) {
      GShape shape = SHAPES.get(id);

      if(shape == null) {
         shape = ImageShapes.getShape(id);
      }

      return shape;
   }

   private static final Map<String,GShape> SHAPES = new HashMap<>();

   static {
      SHAPES.put(StyleConstants.CIRCLE + "", GShape.CIRCLE);
      SHAPES.put(StyleConstants.TRIANGLE + "", GShape.TRIANGLE);
      SHAPES.put(StyleConstants.SQUARE + "", GShape.SQUARE);
      SHAPES.put(StyleConstants.CROSS + "", GShape.CROSS);
      SHAPES.put(StyleConstants.STAR + "", GShape.STAR);
      SHAPES.put(StyleConstants.DIAMOND + "", GShape.DIAMOND);
      SHAPES.put(StyleConstants.X + "", GShape.XSHAPE);
      SHAPES.put(StyleConstants.FILLED_CIRCLE + "", GShape.FILLED_CIRCLE);
      SHAPES.put(StyleConstants.FILLED_TRIANGLE + "", GShape.FILLED_TRIANGLE);
      SHAPES.put(StyleConstants.FILLED_SQUARE + "", GShape.FILLED_SQUARE);
      SHAPES.put(StyleConstants.FILLED_DIAMOND + "", GShape.FILLED_DIAMOND);
      SHAPES.put(StyleConstants.V_ANGLE + "", GShape.VSHAPE);
      SHAPES.put(StyleConstants.RIGHT_ANGLE + "", GShape.LSHAPE);
      SHAPES.put(StyleConstants.LT_ANGLE + "", GShape.ARROW);
      SHAPES.put(StyleConstants.V_LINE + "", GShape.LINE);
      SHAPES.put(StyleConstants.H_LINE + "", GShape.HYPHEN);
      SHAPES.put(StyleConstants.NIL + "", GShape.NIL);
   }
}
