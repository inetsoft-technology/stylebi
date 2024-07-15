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
package inetsoft.report.internal;

import inetsoft.report.Comparer;

import java.util.Comparator;

/**
 * This class turns a comparator into a comparer.
 *
 * @version 11.3, 10/20/2011
 * @author InetSoft Technology Corp
 */
public class ComparatorComparer implements Comparer {
   public ComparatorComparer(Comparator comp) {
      this.comp = comp;
   }

   @Override
   public int compare(Object v1, Object v2) {
      return comp.compare(v1, v2);
   }

   @Override
   public int compare(double v1, double v2) {
      return compare(Double.valueOf(v1), Double.valueOf(v2));
   }

   @Override
   public int compare(float v1, float v2) {
      return compare(Float.valueOf(v1), Float.valueOf(v2));
   }

   @Override
   public int compare(long v1, long v2) {
      return compare(Long.valueOf(v1), Long.valueOf(v2));
   }

   @Override
   public int compare(int v1, int v2) {
      return compare(Integer.valueOf(v1), Integer.valueOf(v2));
   }

   @Override
   public int compare(short v1, short v2) {
      return compare(Short.valueOf(v1), Short.valueOf(v2));
   }

   public Comparator getComparor() {
      return comp;
   }

   private Comparator comp;
}
