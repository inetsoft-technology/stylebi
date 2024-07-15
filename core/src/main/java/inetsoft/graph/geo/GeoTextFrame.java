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
package inetsoft.graph.geo;

import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.data.DataSet;

import java.util.*;

public class GeoTextFrame extends DefaultTextFrame {
   public GeoTextFrame(String fld, Map<GeoShape, String> tuples) {
      this.fld = fld;
      this.tuples = tuples;
      tupleValues = new HashSet<>(tuples.values());
   }

   @Override
   public Object getText(DataSet data, String col, int row) {
      final Object text = super.getText(data, fld, row);
      return tupleValues.contains(text) ? text : null;
   }

   /**
    * Find the matching shape in the data set and return the associated label
    */
   public Object getMapText(GeoShape shape) {
      return tuples.get(shape);
   }

   private final String fld;
   private final Map<GeoShape, String> tuples;
   private final Set<String> tupleValues;
}
