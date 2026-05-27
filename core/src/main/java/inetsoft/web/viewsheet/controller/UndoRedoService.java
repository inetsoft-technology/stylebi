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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.command.UpdateUndoStateCommand;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.service.WizardViewsheetService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class UndoRedoService {

   public UndoRedoService(CoreLifecycleService coreLifecycleService,
                          VSObjectTreeService vsObjectTreeService,
                          VSAssemblyInfoHandler infoHandler, ViewsheetService viewsheetService,
                          WizardViewsheetService wizardViewsheetService)
   {
      this.coreLifecycleService = coreLifecycleService;
      this.vsObjectTreeService = vsObjectTreeService;
      this.infoHandler = infoHandler;
      this.viewsheetService = viewsheetService;
      this.wizardViewsheetService = wizardViewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void undo(@ClusterProxyKey String vsId, Principal principal, String linkUri,
                    CommandDispatcher dispatcher) throws Exception
   {
      RuntimeSheet rs =
         viewsheetService.getSheet(vsId, principal);

      try {
         if(rs instanceof RuntimeViewsheet) {
            RuntimeViewsheet rvs = (RuntimeViewsheet) rs;
            Viewsheet ovs = rvs.getViewsheet().clone();
            ChangedAssemblyList clist =
               this.coreLifecycleService.createList(true, dispatcher, rvs, linkUri);
            boolean undone = rvs.undo(clist);

            if(undone) {
               Viewsheet nvs = rvs.getViewsheet().clone();
               coreLifecycleService.checkAndRemoveAssemblies(ovs, nvs, dispatcher);
               updateViewsheet(rvs, linkUri, clist, dispatcher);

               if(rvs.isWizardViewsheet()) {
                  updateViewsheetWizard(rvs, dispatcher);
               }
            }
         }
         else {
            RuntimeWorksheet rws = (RuntimeWorksheet) rs;
            boolean undone = rws.undo(null);

            if(undone) {
               WorksheetEventUtil.refreshWorksheet(
                  rws, viewsheetService, true, false, dispatcher, principal);
               WorksheetEventUtil.refreshDateRange(rws.getWorksheet());
            }
         }
      }
      finally {
         updateUndoState(rs, dispatcher);
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void redo(@ClusterProxyKey String vsId, Principal principal, String linkUri,
                    CommandDispatcher dispatcher) throws Exception
   {
      RuntimeSheet rs =
         viewsheetService.getSheet(vsId, principal);

      try {
         if(rs instanceof RuntimeViewsheet) {
            RuntimeViewsheet rvs = (RuntimeViewsheet) rs;
            Viewsheet ovs = (Viewsheet) rvs.getViewsheet().clone();
            ChangedAssemblyList clist =
               this.coreLifecycleService.createList(true, dispatcher, rvs, linkUri);
            boolean redone = rvs.redo(clist);

            if(redone) {
               Viewsheet nvs = (Viewsheet) rvs.getViewsheet().clone();
               coreLifecycleService.checkAndRemoveAssemblies(ovs, nvs, dispatcher);
               updateViewsheet(rvs, linkUri, clist, dispatcher);

               if(rvs.isWizardViewsheet()) {
                  updateViewsheetWizard(rvs, dispatcher);
               }
            }
         }
         else {
            RuntimeWorksheet rws = (RuntimeWorksheet) rs;
            boolean redone = rws.redo(null);

            if(redone) {
               WorksheetEventUtil.refreshWorksheet(
                  rws, viewsheetService, false, false, dispatcher, principal);
               WorksheetEventUtil.refreshDateRange(rws.getWorksheet());
            }
         }
      }
      finally {
         updateUndoState(rs, dispatcher);
      }

      return null;
   }

   private void updateViewsheet(RuntimeViewsheet rvs, String linkUri, ChangedAssemblyList clist,
                                CommandDispatcher dispatcher) throws Exception
   {
      this.coreLifecycleService.refreshViewsheet(
         rvs, rvs.getID(), linkUri, dispatcher, false, false, true, clist);

      VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
      PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      dispatcher.sendCommand(treeCommand);
      infoHandler.getGrayedOutFields(rvs, dispatcher);
   }

   private void updateViewsheetWizard(RuntimeViewsheet rs, CommandDispatcher dispatcher) {
      Assembly[] assemblies = this.wizardViewsheetService.getAssemblies(rs, false);
      this.wizardViewsheetService.updateGridRowsAndNewBlock(assemblies, dispatcher);
   }

   private void updateUndoState(RuntimeSheet rs, CommandDispatcher dispatcher) {
      UpdateUndoStateCommand command = new UpdateUndoStateCommand();
      command.setPoints(rs.size());
      command.setCurrent(rs.getCurrent());
      command.setSavePoint(rs.getSavePoint());
      dispatcher.sendCommand(command);
   }


   private final CoreLifecycleService coreLifecycleService;
   private final VSObjectTreeService vsObjectTreeService;
   private final VSAssemblyInfoHandler infoHandler;
   private final ViewsheetService viewsheetService;
   private final WizardViewsheetService wizardViewsheetService;
}
