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

      ElementGeometry gobj1 = (ElementGeometry) ((PointVO) v1).getGeometry();
      ElementGeometry gobj2 = (ElementGeometry) ((PointVO) v2).getGeometry();

      if(gobj1.getElement() != gobj2.getElement()) {
         return 0;
      }

      return BrushedMarks.order(BrushedMarks.isBrushed(gobj1), BrushedMarks.isBrushed(gobj2));
   }
}
