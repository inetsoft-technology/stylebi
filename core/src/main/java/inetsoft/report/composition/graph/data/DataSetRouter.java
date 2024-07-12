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
package inetsoft.report.composition.graph.data;

import inetsoft.graph.data.DataSet;

import java.util.*;

/**
 * A map for data compare, it is used for data calculation,
 * like Change, RunningTotal or Moving.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class DataSetRouter extends AbstractRouter {
   /**
    * Default constructor.
    */
   public DataSetRouter() {
      super();
   }

   /**
    * Constructor.
    */
   public DataSetRouter(DataSet data, String field) {
      super();
      keyhash = data.hashCode();
      List v = new ArrayList<>();
      Object val = null;

      for(int i = 0; i < data.getRowCount(); i++) {
         val = data.getData(field, i);

         if(!v.contains(val)) {
            v.add(val);
         }
      }

      comp = data.getComparator(field);

      if(comp != null) {
         Collections.sort(v, comp);
      }

      values = new Object[v.size()];
      v.toArray(values);
   }

   @Override
   public Object[] getValues() {
      return values;
   }

   @Override
   public boolean isValidFor(DataSet dataSet) {
      return keyhash == dataSet.hashCode();
   }

   private Object[] values;
   private int keyhash;
}
