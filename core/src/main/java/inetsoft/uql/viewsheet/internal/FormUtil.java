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

import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility functions for forms.
 *
 * @version 12.0
 * @author InetSoft Technology Corp
 */
public final class FormUtil {
   /**
    * Check the form table data whether is changed.
    */
   public static boolean checkFormData(RuntimeViewsheet rvs, String name) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null || name == null) {
         return false;
      }

      Viewsheet vs = rvs.getViewsheet();
      Assembly[] vass = vs.getAssemblies();

      try {
         for(int i = 0; i < vass.length; i++) {
            VSAssemblyInfo afino = (VSAssemblyInfo) vass[i].getInfo();

            if(afino instanceof TableVSAssemblyInfo &&
               ((TableVSAssemblyInfo) afino).isForm())
            {
               FormTableLens flens =
                  box.getFormTableLens(vass[i].getAbsoluteName());

               if(isDepended(vs, vass[i], name) && formDataChanged(flens)) {
                  return true;
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.info("Failed to check form data", ex);
      }

      return false;
   }

   /**
    * Check the form table data whether is changed.
    */
   public static boolean formDataChanged(FormTableLens flens) {
      if(flens == null) {
         return false;
      }

      if(flens.rows(FormTableRow.ADDED).length > 0 ||
         flens.rows(FormTableRow.CHANGED).length > 0 ||
         flens.rows(FormTableRow.DELETED).length > 0)
      {
         return true;
      }

      return false;
   }

   /**
    * Check the form table whether depend on other assembly.
    */
   private static boolean isDepended(Viewsheet vs, Assembly ass, String name) {
      Assembly[] arr =
         AssetUtil.getDependedAssemblies(vs, ass, false, false, true);

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof VSAssembly &&
            Tool.equals(arr[i].getAbsoluteName(), name))
         {
            return true;
         }
      }

      VSAssembly vass = (VSAssembly) vs.getAssembly(name);
      VSAssemblyInfo vinfo = vass.getVSAssemblyInfo();

      if(vinfo instanceof TipVSAssemblyInfo &&
         VSUtil.sameSource((VSAssembly) ass, vass))
      {
         String[] views = ((TipVSAssemblyInfo) vinfo).getFlyoverViews();

         for(int i = 0; views != null && i < views.length; i++) {
            if(Tool.equals(views[i], ass.getName())) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if the viewsheet contains input form.
    * @param tblonly true to only check for form table.
    */
   public static boolean containsForm(Viewsheet viewsheet, boolean tblonly) {
      return LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM) &&
         checkContainsForm(viewsheet, tblonly);
   }

   /**
    * Check if viewsheet contains form element.
    * Return <tt>true </tt> if viewsheet or embedded viewsheet contains form
    * element, otherwise <tt>false</tt>
    */
   private static boolean checkContainsForm(Viewsheet viewsheet, boolean tblonly) {
      if(viewsheet == null) {
         return false;
      }

      Assembly[] assemblies = viewsheet.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof Viewsheet) {
            if(checkContainsForm((Viewsheet) assembly, tblonly)) {
               return true;
            }
         }
         else if(isForm((VSAssembly) assembly, tblonly)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check is form element.
    */
   private static boolean isForm(VSAssembly ass, boolean tblonly) {
      if(tblonly && !(ass instanceof TableVSAssembly)) {
	 return false;
      }
      
      return ass.isVisible() && ass.isEnabled() &&
         (ass instanceof TextInputVSAssembly ||
          ass instanceof ListInputVSAssembly ||
          (ass instanceof TableVSAssembly &&
           ((TableVSAssemblyInfo) ass.getInfo()).isForm()));
   }

   private static final Logger LOG = LoggerFactory.getLogger(FormUtil.class);
}
