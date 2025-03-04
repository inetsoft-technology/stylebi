/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.binding.dnd.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.controller.VSTableLayoutController;
import inetsoft.web.binding.dnd.CalcDropTarget;
import inetsoft.web.binding.dnd.CalcTableTransfer;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSCalcTableBindingHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.Set;

/**
 * This class handles get vsobjectmodel from the server.
 */
@Controller
public class VSCalcTableDndController extends VSAssemblyDndController {
   /**
    * Creates a new instance of <tt>VSViewController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
   @Autowired
   public VSCalcTableDndController(RuntimeViewsheetRef runtimeViewsheetRef,
                                   VSBindingService bfactory,
                                   VSAssemblyInfoHandler assemblyInfoHandler,
                                   VSTableLayoutController tableLayoutController,
                                   VSCalcTableBindingHandler calcTableHandler,
                                   VSObjectModelFactoryService objectModelService,
                                   ViewsheetService viewsheetService,
                                   CoreLifecycleService coreLifecycleService)
   {
      super(runtimeViewsheetRef, bfactory, assemblyInfoHandler, objectModelService,
            viewsheetService, coreLifecycleService);
      this.calcTableHandler = calcTableHandler;
      this.tableLayoutController = tableLayoutController;
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    *
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vscalctable/dnd/addRemoveColumns")
   public void addRemoveColumns(@Payload VSDndEvent event, Principal principal,
      @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = getRuntimeVS(principal);

      if(rvs == null) {
         return;
      }

      CalcTableVSAssembly assembly = (CalcTableVSAssembly)
         getVSAssembly(rvs, event.name());
      CalcTableVSAssembly clone = (CalcTableVSAssembly) assembly.clone();
      CalcTableTransfer transfer = (CalcTableTransfer)event.getTransfer();
      CalcDropTarget target = (CalcDropTarget)event.getDropTarget();
      calcTableHandler.addRemoveColumns(clone, transfer.getDragRect(), target.getDropRect());
      applyAssemblyInfo(rvs, assembly, (VSAssemblyInfo)clone.getInfo(), dispatcher,
         event, linkUri, null);
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    *
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vscalctable/dnd/addColumns")
   public void addColumns(@Payload VSDndEvent event, Principal principal,
      @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = getRuntimeVS(principal);

      if(rvs == null) {
         return;
      }

      CalcTableVSAssembly assembly =
         (CalcTableVSAssembly) getVSAssembly(rvs, event.name());
      CalcTableVSAssembly nassembly = (CalcTableVSAssembly) assembly.clone();
      CalcTableVSAssemblyInfo ninfo = (CalcTableVSAssemblyInfo) nassembly.getInfo();

      // Handle source changed.
      if(sourceChanged(assembly, event.getTable())) {
         changeSource(nassembly, event.getTable(), event.getSourceType());
         CalcTableVSAssemblyInfo vsCalcTableInfo =
            (CalcTableVSAssemblyInfo) nassembly.getVSAssemblyInfo();

         if(vsCalcTableInfo != null) {
            AggregateInfo ainfo =  vsCalcTableInfo.getAggregateInfo();

            if(ainfo != null) {
               List<DataRef> calcFields = ainfo.getFormulaFields();
               Set<String> calcFieldsRefs = ainfo.removeFormulaFields(calcFields);
               nassembly.getTableLayout().clearFormulaBinding(calcFieldsRefs);
            }
         }
      }

      if(ninfo.getSourceInfo() == null) {
         ninfo.setSourceInfo(new SourceInfo(event.getSourceType(), null, event.getTable()));
      }

      CalcDropTarget target = (CalcDropTarget) event.getDropTarget();
      calcTableHandler.addColumns(nassembly, event.getEntries(), target.getDropRect(), rvs);
      applyAssemblyInfo(rvs, assembly, nassembly, dispatcher, event,
         "/events/vscalctable/dnd/addColumns", linkUri);
   }

   @Override
   protected boolean sourceChanged(VSAssembly assembly, String table) {
      SourceInfo sinfo = ((DataVSAssemblyInfo) assembly.getInfo()).getSourceInfo();
      return sinfo != null && !sinfo.getSource().equals(table);
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    *
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vscalctable/dnd/removeColumns")
   public void removeColumns(@Payload VSDndEvent event, Principal principal,
      @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = getRuntimeVS(principal);
      if(rvs == null) {
         return;
      }

      CalcTableVSAssembly assembly =
         (CalcTableVSAssembly) getVSAssembly(rvs, event.name());
      CalcTableVSAssembly clone = (CalcTableVSAssembly) assembly.clone();
      CalcTableTransfer transfer = (CalcTableTransfer) event.getTransfer();
      calcTableHandler.removeColumns(clone, transfer.getDragRect());
      applyAssemblyInfo(rvs, assembly, (VSAssemblyInfo) clone.getInfo(), dispatcher, event,
                        linkUri, null);
   }

   protected void createDndCommands(RuntimeViewsheet rvs, VSAssembly assembly,
      CommandDispatcher dispatcher, VSDndEvent event, String linkUri) throws Exception
   {
      super.createDndCommands(rvs, assembly, dispatcher, event, linkUri);

      String name = assembly.getInfo().getAbsoluteName();
      Rectangle rect = null;

      if(event.getDropTarget() != null) {
         rect = ((CalcDropTarget) event.getDropTarget()).getDropRect();
      }
      else {
         rect = ((CalcTableTransfer) event.getTransfer()).getDragRect();
      }

      CalcTableVSAssembly calc = (CalcTableVSAssembly) assembly;
      dispatcher.sendCommand(name,
         tableLayoutController.createCellBindingCommand(rvs, calc, rect.y, rect.x));
      dispatcher.sendCommand(name, tableLayoutController.createTableLayoutCommand(rvs, calc));
   }

   private VSCalcTableBindingHandler calcTableHandler;
   private VSTableLayoutController tableLayoutController;
   private static final Logger LOG = LoggerFactory.getLogger(VSCalcTableDndController.class);
}
