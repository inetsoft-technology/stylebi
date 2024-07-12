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
package inetsoft.uql.viewsheet.vslayout;

import java.util.Comparator;

/**
 * The comparator to sort the vs assembly layout by ascending in y.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class VSAssemblyLayoutComparator implements Comparator<VSAssemblyLayout> {
   @Override
   public int compare(VSAssemblyLayout layout1, VSAssemblyLayout layout2) {
      if(layout1 == null) {
         return -1;
      }

      if(layout2 == null) {
         return 1;
      }

      return layout1.getPosition().y - layout2.getPosition().y;
   }
}
