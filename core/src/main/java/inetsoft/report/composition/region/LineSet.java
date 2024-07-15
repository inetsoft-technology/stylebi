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

import java.awt.geom.Point2D;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * IndexedSet defines the values and indices mapping to avoid redundance.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LineSet extends IndexedSet<List<Point2D>> {
   /**
    * Write value to output, default converts to string.
    */
   @Override
   protected void writeValue(DataOutputStream output, Object val)
      throws IOException 
   {
      List<Point2D> pts = (List<Point2D>) val;

      output.writeInt(pts.size());
      
      for(Point2D pt : pts) {
         output.writeDouble(pt.getX());
         output.writeDouble(pt.getY());
      }
   }
}
