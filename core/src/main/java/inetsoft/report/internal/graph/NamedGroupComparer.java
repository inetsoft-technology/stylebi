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
package inetsoft.report.internal.graph;

import inetsoft.report.filter.SortOrder;
import inetsoft.util.Tool;

import java.io.Serializable;
import java.util.Comparator;

public class NamedGroupComparer implements Comparator<Object>, Serializable {
   public NamedGroupComparer(SortOrder order) {
      this.order = order;
   }

   @Override
   public int compare(Object v1, Object v2) {
      boolean c1 = order.containsGroup(Tool.getStringData(v1));
      boolean c2 = order.containsGroup(Tool.getStringData(v2));

      if(c1 && c2) {
         return comp.compare(v1, v2);
      }
      else if(c1 && !c2) {
         return -1;
      }
      else if(!c1 && c2) {
         return 1;
      }

      return comp.compare(v1, v2);
   }

   public void setComparor(Comparator comp) {
      this.comp = comp;
   }

   private SortOrder order;
   private boolean noneSort;
   private Comparator comp;
}
