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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AssemblyRef;
import inetsoft.uql.viewsheet.internal.PageBreakVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

public class PageBreakVSAssembly extends AbstractVSAssembly {
   /**
    * Constructor.
    */
   public PageBreakVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   @Override
   protected VSAssemblyInfo createInfo() {
      return new PageBreakVSAssemblyInfo();
   }

   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      return new AssemblyRef[0];
   }

   @Override
   public AssemblyRef[] getDependingWSAssemblies() {
      return new AssemblyRef[0];
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return AbstractSheet.PAGEBREAK_ASSET;
   }
}
