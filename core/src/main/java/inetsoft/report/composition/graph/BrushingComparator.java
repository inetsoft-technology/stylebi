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
package inetsoft.report.composition.graph;

import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.visual.PointVO;
import inetsoft.uql.viewsheet.graph.aesthetic.BrushingColor;

import java.awt.*;
import java.util.Comparator;

/**
 * A comparator to make sure brushed vo is on top of the base vo.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class BrushingComparator implements Comparator {
   @Override
   public int compare(Object v1, Object v2) {
      if(!(v1 instanceof PointVO) || !(v2 instanceof PointVO)) {
         return 0;
      }

      PointVO p1 = (PointVO) v1;
      PointVO p2 = (PointVO) v2;

      ElementGeometry gobj1 = (ElementGeometry) p1.getGeometry();
      ElementGeometry gobj2 = (ElementGeometry) p2.getGeometry();

      if(gobj1.getElement() != gobj2.getElement()) {
         return 0;
      }

      Color c1 = gobj1.getColor(0);
      Color c2 = gobj2.getColor(0);

      if(c1.equals(c2)) {
         return 0;
      }

      if(c1.equals(hlcolor)) {
         return 1;
      }
      else if(c2.equals(hlcolor)) {
         return -1;
      }

      return 0;
   }

   private Color hlcolor = BrushingColor.getHighlightColor();
}
