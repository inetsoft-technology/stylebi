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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;

public interface DateCompareAbleAssemblyInfo {
   /**
    * Get the date comparison info.
    */
   DateComparisonInfo getDateComparisonInfo();

   /**
    * set the date comparison info
    * @param info date comparison info.
    */
   void setDateComparisonInfo(DateComparisonInfo info);

   /**
    * Get the share from assembly full name.
    */
   String getComparisonShareFrom();

   /**
    * Set the share from assembly full name.
    */
   void setComparisonShareFrom(String assemblyFullName);

   /**
    * Set the ref to compare.
    * @param ref
    */
   void setDateComparisonRef(VSDataRef ref);

   /**
    * Get the compare date ref to do condition.
    */
   VSDataRef getDateComparisonRef();

   /**
    * Return fields which are temporarily generated for expand the data as dc required,
    * and this part of temp fields also used to date compare(other temp fields are not used
    * in date compare, just used to expand the data).
    */
   public XDimensionRef[] getTempDateGroupRef();

   /**
    * Can apply the date comparison.
    */
   boolean supportDateComparison();

   /**
    * Get the date comparison binding ref.
    */
   DataRef getDCBIndingRef(String refName);

   /**
    * Since date comparison of the target assembly has already been removed, so clear
    * the share from date comparison for assemblies which depends on the target assembly.
    *
    * @param name the target assembly name which date comparison have been removed.
    * @param vs the target viewsheet which need to refresh the share dependencies.
    */
   static void cleanShareDependencies(String name, Viewsheet vs) {
      if(name == null || vs == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(!(assembly.getInfo() instanceof DateCompareAbleAssemblyInfo)) {
            continue;
         }

         DateCompareAbleAssemblyInfo vinfo = (DateCompareAbleAssemblyInfo) assembly.getInfo();

         if(Tool.equals(vinfo.getComparisonShareFrom(), name)) {
            vinfo.setComparisonShareFrom(null);
         }
      }
   }
}
