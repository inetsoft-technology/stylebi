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
package inetsoft.web.binding;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSModelTrapContext;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.erm.AbstractModelTrapContext.TrapInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSTableDataHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.SourceInfo;
import inetsoft.web.binding.model.table.BaseTableBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides a REST endpoint for composer viewsheet actions.
 */
@RestController
@RequestMapping("/vstable")
public class VSTableBindingController {
   @RequestMapping(value = "/convert", method = RequestMethod.PUT)
   public BindingModel convertTableRef(
      @RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("refName") String refName, @RequestParam("type") int type,
      @RequestParam("sourceChange") boolean sourceChange,
      @RequestBody SourceInfo sinfo, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet vs = rvs.getViewsheet();
      TableDataVSAssembly assembly =
         (TableDataVSAssembly) vs.getAssembly(assemblyName);
      inetsoft.uql.asset.SourceInfo sourceInfo =
         sinfo.toSourceAttr(assembly.getSourceInfo());

      tableDataHandler.convertTableRef(engine.getAssetRepository(),
         rvs, assemblyName, refName, type, sourceChange, sourceInfo, principal);

      return factory.createModel(assembly);
   }

   @RequestMapping(value = "/updateTableAssembly/checktrap", method = RequestMethod.PUT)
   public BindingModel checkTrap(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestBody BaseTableBindingModel cmodel, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(assemblyName);
      VSAssemblyInfo oinfo = (VSAssemblyInfo) assembly.getInfo().clone();
      factory.updateAssembly(cmodel, assembly);
      VSAssemblyInfo ninfo = (VSAssemblyInfo) assembly.getInfo().clone();

      try {
         box.updateAssembly(assembly.getAbsoluteName());
         assembly.setVSAssemblyInfo(oinfo);
         box.updateAssembly(assembly.getAbsoluteName());
      }
      catch(Exception ex) {
         // ignore it
      }

      boolean containsTrap = checkTrap0(rvs, oinfo, ninfo);

      return containsTrap ? factory.createModel(assembly) : null;
   }

   private boolean checkTrap0(RuntimeViewsheet rvs,
      VSAssemblyInfo oinfo,  VSAssemblyInfo ninfo)
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSModelTrapContext mtc = new VSModelTrapContext(rvs, true);
      boolean warning = false;
      boolean check = mtc.isCheckTrap();

      if(check) {
         TrapInfo trapInfo = mtc.checkTrap(oinfo, ninfo);
         warning = trapInfo.showWarning();
      }

      return warning;
   }

   @RequestMapping(value = "/updateTableAssembly", method = RequestMethod.PUT)
   public BindingModel updateTableAssembly(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestBody BindingModel cmodel, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(assemblyName);
      factory.updateAssembly(cmodel, assembly);
      box.updateAssembly(assembly.getAbsoluteName());

      return factory.createModel(assembly);
   }

   @Autowired
   public void setViewsheetService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @Autowired
   private VSBindingService factory;
   @Autowired
   private VSAssemblyInfoHandler assemblyInfoHandler;
   @Autowired
   private VSTableDataHandler tableDataHandler;
   private ViewsheetService viewsheetService;
}
