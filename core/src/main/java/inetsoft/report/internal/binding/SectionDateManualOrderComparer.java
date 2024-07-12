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
package inetsoft.report.internal.binding;

import inetsoft.graph.internal.ManualOrderComparer;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.util.DefaultComparator;

import java.util.Comparator;
import java.util.List;

public class SectionDateManualOrderComparer extends ManualOrderComparer {
   public SectionDateManualOrderComparer(List<ConditionGroup> list) {
      super(new Object[0]);

      this.list = list;
   }

   @Override
   public int compare(Object v1, Object v2) {
      v1 = "".equals(v1) ? null : v1;
      v2 = "".equals(v2) ? null : v2;
      int idx1 = this.indexOfValue(v1);
      int idx2 = this.indexOfValue(v2);

      if(idx1 >= 0 && idx2 >= 0) {
         int val = idx1 - idx2;
         return val == 0 ? 0 : (val < 0 ? -1 : 1);
      }
      else if (idx1 >= 0) {
         return -1;
      }
      else {
         return idx2 >= 0 ? 1 : this.comp.compare(v1, v2);
      }
   }

   private int indexOfValue(Object v) {
      if(list == null || list.size() == 0) {
         return -1;
      }

      for(int i = 0; i < list.size(); i++) {
         ConditionGroup condition = list.get(i);
         boolean result = condition.evaluate(v);

         if(result) {
            return i;
         }
      }

      return -1;
   }

   private List<ConditionGroup> list;
   private Comparator comp = new DefaultComparator();
}