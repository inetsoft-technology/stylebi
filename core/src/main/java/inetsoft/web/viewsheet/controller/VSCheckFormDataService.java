/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.model.CheckFormTableDataModel;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSCheckFormDataService {

   public VSCheckFormDataService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean checkFormData(@ClusterProxyKey String runtimeId, boolean checkCond,
                                CheckFormTableDataModel model, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(model.name());
      boolean changed = FormUtil.checkFormData(rvs, model.name());

      if(assembly instanceof DataVSAssembly && checkCond && changed) {
         changed = !VSUtil.sameCondition(
            rvs, model.name(), model.selection());
      }

      return changed;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean formTableModified(@ClusterProxyKey String runtimeId, CheckFormTableDataModel model,
                                    Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(model.name());

      if(box == null) {
         return false;
      }

      VSAssemblyInfo assemblyInfo = (VSAssemblyInfo) assembly.getInfo();

      if(assemblyInfo instanceof TableVSAssemblyInfo &&
         ((TableVSAssemblyInfo) assemblyInfo).isForm()) {
         FormTableLens flens = box.getFormTableLens(model.name());
         return FormUtil.formDataChanged(flens);
      }

      return false;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean checkTables(@ClusterProxyKey String runtimeId, Principal principal) throws Exception
   {
      try {
         RuntimeSheet sheet = viewsheetService.getSheet(runtimeId, principal);

         if(!(sheet instanceof RuntimeViewsheet rvs)) {
            return false;
         }

         Viewsheet viewsheet = rvs.getViewsheet();
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         Assembly[] assemblies = viewsheet.getAssemblies();

         int added = 0;
         int changed = 0;
         int deleted = 0;

         // Go through each table,
         for(int i = 0; i < assemblies.length; i++) {
            String assemblyName = assemblies[i].getAbsoluteName();
            FormTableLens lens = box.getFormTableLens(assemblyName);

            // could be cancelled
            if(lens != null) {
               // and accumulate the writeback edits which are pending,
               added += lens.rows(FormTableRow.ADDED).length;
               changed += lens.rows(FormTableRow.CHANGED).length;
               deleted += lens.rows(FormTableRow.DELETED).length;
            }
         }

         return added != 0 || changed != 0 || deleted != 0;
      }
      catch(ExpiredSheetException ex) {
         return false;
      }
   }


   private ViewsheetService viewsheetService;
}
