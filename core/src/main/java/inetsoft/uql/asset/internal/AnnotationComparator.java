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
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.internal.*;

import java.util.Comparator;

/**
 * Assembly comparator compares two assembly dependency.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class AnnotationComparator implements Comparator<Assembly> {
   /**
    * Constructor.
    */
   public AnnotationComparator() {
      this(true);
   }

   /**
    * Constructor.
    * @param asc the specified sort order.
    */
   public AnnotationComparator(boolean asc) {
      this.asc = asc;
   }

   /**
    * Compare two object.
    */
   @Override
   public int compare(Assembly a, Assembly b) {
      if(a == null) {
         return (b == null) ? 0 : -1;
      }
      else if(b == null) {
         return 1;
      }

      AssemblyInfo ainfo = a.getInfo();
      AssemblyInfo binfo = b.getInfo();

      // make sure the annotation components is processed before other
      // related assemblies
      if(isAnnotationVSAssemblyInfo(ainfo)) {
         return asc ? -1 : 1;
      }
      else if(ainfo instanceof BaseAnnotationVSAssemblyInfo &&
         isAnnotationVSAssemblyInfo(binfo))
      {
         return asc ? 1 : -1;
      }
      else {
         return 0;
      }
   }

   /**
    * Check if an assembly info is related annotation assembly info.
    * @param info the specified assembly info.
    * @return <tt>true</tt> if is the related annotation assembly info,
    * <tt>false</tt> otherwise.
    */
   private boolean isAnnotationVSAssemblyInfo(AssemblyInfo info) {
      if(info instanceof AnnotationVSAssemblyInfo ||
         info instanceof AnnotationLineVSAssemblyInfo ||
         info instanceof AnnotationRectangleVSAssemblyInfo)
      {
         return true;
      }

      return false;
   }

   private boolean asc;
}
