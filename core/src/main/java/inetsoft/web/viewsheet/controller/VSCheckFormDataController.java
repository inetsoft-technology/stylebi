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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.viewsheet.model.CheckFormTableDataModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Check whether an action is interfering with a changed form table input.
 */
@Controller
public class VSCheckFormDataController {
   /**
    * Creates a new instance of <tt>VSCheckFormDataController</tt>.
    */
   @Autowired
   public VSCheckFormDataController(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @PostMapping("/api/formDataCheck")
   @ResponseBody
   public boolean checkFormData(@DecodeParam("runtimeId") String runtimeId,
                                @RequestParam("checkCondition") boolean checkCond,
                                @RequestBody CheckFormTableDataModel model,
                                Principal principal)
      throws Exception
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

   @PostMapping("/api/formTableModified")
   @ResponseBody
   public boolean formTableModified(@DecodeParam("runtimeId") String runtimeId,
                                  @RequestBody CheckFormTableDataModel model,
                                  Principal principal)
      throws Exception
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

   /**
    * Before closing the viewsheet, check if any form tables were edited.
    *
    * @param runtimeId the runtime id of the preview sheet.
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @GetMapping("/api/vs/checkFormTables")
   @ResponseBody
   public boolean checkTables(@DecodeParam("runtimeId") String runtimeId,
                              Principal principal)
      throws Exception
   {
      try {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
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

   private final ViewsheetService viewsheetService;
}
