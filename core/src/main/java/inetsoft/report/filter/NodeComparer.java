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
package inetsoft.report.filter;

import inetsoft.util.Tool;

import java.util.Comparator;

/**
 * A comparator compares two nodes.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public abstract class NodeComparer<T> implements Comparator<T> {
   /**
    * Constructor.
    *
    * @param scol ths specified summary col
    * @param asc true if ascending, false otherwise
    */
   public NodeComparer(int scol, boolean asc) {
      this.scol = scol;
      this.asc = asc ? 1 : -1;
   }

   /**
    * Constructor.
    *
    * @param asc true if ascending, false otherwise
    */
   public NodeComparer(boolean asc) {
      this.asc = asc ? 1 : -1;
   }

   /**
    * Compare two group nodes.
    */
   @Override
   public int compare(T v1, T v2) {
      Object val1 = getResult(v1);
      Object val2 = getResult(v2);

      if(val1 == null && val2 == null) {
         return 0;
      }
      else if(val1 == null) {
         return -1 * asc;
      }
      else if(val2 == null) {
         return asc;
      }
      else {
         return Tool.compare(val1, val2) * asc;
      }
   }

   /**
    * Get specified node summary value.
    */
   protected abstract Object getResult(Object obj);

   int scol;
   int asc;
}