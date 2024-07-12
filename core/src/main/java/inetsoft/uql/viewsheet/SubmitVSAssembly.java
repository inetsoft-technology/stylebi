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

import inetsoft.uql.viewsheet.internal.SubmitVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

/**
 * SubmitVSAssembly represents one submit assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class SubmitVSAssembly extends OutputVSAssembly {
   /**
    * Constructor.
    */
   public SubmitVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public SubmitVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new SubmitVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.SUBMIT_ASSET;
   }

   /**
    * Get the value of this output viewsheet assembly.
    * @return the value of this output viewsheet assembly.
    */
   @Override
   public Object getValue() {
      return null;
   }

   /**
    * Set the value to this output viewsheet assembly.
    * @param val the specified value.
    */
   @Override
   public void setValue(Object val) {
      // do nothing
   }

   /**
    * Get submit assembly info.
    * @return the submit assembly info.
    */
   protected SubmitVSAssemblyInfo getSubmitInfo() {
      return (SubmitVSAssemblyInfo) getInfo();
   }
}