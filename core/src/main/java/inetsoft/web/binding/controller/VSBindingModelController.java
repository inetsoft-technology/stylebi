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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.BoundTableNotFoundException;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.event.RefreshVSBindingEvent;
import inetsoft.web.binding.handler.CrosstabBindingHandler;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.CloseBindingPaneCommand;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.controller.table.BaseTableController;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.model.table.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.model.VSWizardOriginalModel;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSBindingModelController {
   /**
    * Creates a new instance of <tt>ViewsheetBindingController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public VSBindingModelController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSBindingService bfactory,
      VSAssemblyInfoHandler assemblyInfoHandler,
      CrosstabBindingHandler crosstabHandler,
      VSObjectModelFactoryService objectModelService,
      VSWizardTemporaryInfoService temporaryInfoService,
      ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.bfactory = bfactory;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.crosstabHandler = crosstabHandler;
      this.objectModelService = objectModelService;
      this.viewsheetService = viewsheetService;
      this.temporaryInfoService = temporaryInfoService;
   }

   @MessageMapping("/vs/binding/getbinding")
   public void getBinding(@Payload RefreshVSBindingEvent event,
      Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      String name = event.getName();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet viewsheet = rvs.getViewsheet();

      box.lockRead();

      try {
         VSAssembly assembly = viewsheet.getAssembly(name);
         VSTemporaryInfo tempInfo = temporaryInfoService.getVSTemporaryInfo(rvs);

         // for some reason binding pane may be opened immediately after wizard is closed (50558):
         // vswizard/object/close/save -> api/vsbinding/open
         // instead of np exception, we just close the binding pane to return to vspane/wizard.
         if(assembly == null) {
            dispatcher.sendCommand(new CloseBindingPaneCommand());
            return;
         }

         if(tempInfo != null && assembly instanceof ChartVSAssembly) {
            VSWizardOriginalModel originalModel = tempInfo.getOriginalModel();
            String originalName = originalModel != null ? originalModel.getOriginalName() : null;
            VSAssembly originalAssembly = rvs.getViewsheet().getAssembly(originalName);

            // VSWizardBindingHandler sets inPlot to false for optimization, we change it to
            // the default true value when switching to full editor so the tree chart would
            // be centered. (58165)
            if(!(originalAssembly instanceof ChartVSAssembly)) {
               ((ChartVSAssembly) assembly).getChartDescriptor().getPlotDescriptor().setInPlot(true);
            }
         }

         BindingModel binding = bfactory.createModel(assembly);
         SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);

         if(!bfactory.isSourceVisible(rvs, assembly)) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setType(MessageCommand.Type.INFO);
            messageCommand.setMessage(Catalog.getCatalog()
                                      .getString("composer.ws.sourceTableHidden"));
            dispatcher.sendCommand(messageCommand);
         }

         AddVSObjectCommand command = new AddVSObjectCommand();
         command.setName(assembly.getAbsoluteName());
         command.setMode(AddVSObjectCommand.Mode.DESIGN_MODE);
         VSObjectModel model = null;

         try {
            model = objectModelService.createModel(assembly, rvs);

            if(model instanceof BaseTableModel && !(assembly instanceof CalcTableVSAssembly)) {
               BaseTableController.loadTableModelProperties(
                  rvs, (TableDataVSAssembly) assembly, (BaseTableModel) model);
            }

            command.setModel(model);
         }
         catch(BoundTableNotFoundException | ColumnNotFoundException e) {
            MessageCommand msgCom = new MessageCommand();
            msgCom.setMessage(e.getMessage());
            msgCom.setType(MessageCommand.Type.ERROR);
            dispatcher.sendCommand(msgCom);

            if(assembly instanceof CrosstabVSAssembly) {
               command.setModel(new VSCrosstabModel((CrosstabVSAssembly) assembly, rvs));
            }
            else if(assembly instanceof CalcTableVSAssembly) {
               command.setModel(new VSCalcTableModel((CalcTableVSAssembly) assembly, rvs));
            }
         }

         dispatcher.sendCommand(command);

         if(assembly instanceof TableDataVSAssembly && !(assembly instanceof CalcTableVSAssembly)) {
            BaseTableController.loadTableData(rvs, event.getName(), 0, 0, 100, null, dispatcher);
         }
      }
      finally {
         box.unlockRead();
      }
   }

   @MessageMapping("/vs/binding/setbinding")
   public void setBinding(@Payload ApplyVSAssemblyInfoEvent event,
      Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      String name = event.getName();
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(name);
      BindingModel binding = event.getBinding();

      if(assembly instanceof CrosstabVSAssembly && binding instanceof CrosstabBindingModel) {
         CrosstabBindingHandler.applyDLevelChanges((CrosstabBindingModel) binding);
      }

      BindingModel obinding = bfactory.createModel(assembly);
      VSAssemblyInfo oinfo = (VSAssemblyInfo) assembly.getInfo().clone();
      VSAssembly clone = (VSAssembly) assembly.clone();
      clone = bfactory.updateAssembly(binding, clone);
      VSAssemblyInfo ninfo = (VSAssemblyInfo) clone.getInfo();
      assemblyInfoHandler.apply(rvs, ninfo, engine, false, false, false, false, dispatcher);

      if(assembly instanceof CrosstabVSAssembly) {
         refreshDrillFilters(assembly, oinfo, ninfo);
      }

      binding = bfactory.createModel(assembly);
      SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);
      VSObjectModel model = objectModelService.createModel(assembly, rvs);
      RefreshVSObjectCommand command = new RefreshVSObjectCommand();
      command.setInfo(model);
      dispatcher.sendCommand(command);
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      assemblyInfoHandler.getGrayedOutFields(rvs, dispatcher);

      if(assembly instanceof TableDataVSAssembly) {
         BaseTableController.loadTableData(
            rvs, event.getName(), 0, 0, 100, null, dispatcher);
      }

      if(event.isCheckTrap()) {
         assemblyInfoHandler.checkTrap(oinfo, ninfo, obinding, dispatcher, rvs);
      }
   }

   private void refreshDrillFilters(VSAssembly assembly, VSAssemblyInfo oinfo,
                                       VSAssemblyInfo ninfo)
   {
      if(oinfo instanceof CrosstabVSAssemblyInfo) {
         CrosstabVSAssembly cross = (CrosstabVSAssembly) assembly;
         DrillFilterInfo drillInfo = cross.getDrillFilterInfo();
         VSCrosstabInfo ocross = ((CrosstabVSAssemblyInfo) oinfo).getVSCrosstabInfo();
         VSCrosstabInfo ncross = ((CrosstabVSAssemblyInfo) ninfo).getVSCrosstabInfo();
         assemblyInfoHandler.dateLevelChanged(drillInfo, ocross.getRowHeaders(),
            ncross.getRowHeaders());
         assemblyInfoHandler.dateLevelChanged(drillInfo, ocross.getColHeaders(),
            ncross.getColHeaders());
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSBindingService bfactory;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final CrosstabBindingHandler crosstabHandler;
   private final VSObjectModelFactoryService objectModelService;
   private final ViewsheetService viewsheetService;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
