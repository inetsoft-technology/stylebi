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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.internal.SubmitVSAssemblyInfo;

/**
 * The submit viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class SubmitVSAScriptable extends OutputVSAScriptable {
   /**
    * Create submit viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public SubmitVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "SubmitVSA";
   }

   /**
    * Initialize the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      SubmitVSAssemblyInfo info = getInfo();

      addProperty("refreshAfterSubmit", "isRefresh", "setRefresh",
                  boolean.class, info.getClass(), info);

   }

   /**
    * Check if this property should be exposed in script editor.
    */
   @Override
   protected boolean isPublicProperty(Object name) {
      return !"hyperlink".equals(name) &&
         !"value".equals(name) && !"shadow".equals(name);
   }

   /**
    * Add output properties.
    */
   @Override
   protected void addOutputProperties() {
      // do nothing since submit has no output property
   }

   private SubmitVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof SubmitVSAssemblyInfo) {
         return (SubmitVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new SubmitVSAssemblyInfo();
   }
}
