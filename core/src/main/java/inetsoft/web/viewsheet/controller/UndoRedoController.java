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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.HandleAssetExceptions;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.UpdateUndoStateCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.service.WizardViewsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller that provides a REST endpoint for undo and redo actions.
 */
@Controller
public class UndoRedoController {
   /**
    * Creates a new instance of <tt>ComposerUndoRedoController</tt>.
    */
   @Autowired
   public UndoRedoController(RuntimeViewsheetRef runtimeViewsheetRef,
                             PlaceholderService placeholderService,
                             VSObjectTreeService vsObjectTreeService,
                             VSAssemblyInfoHandler infoHandler, ViewsheetService viewsheetService,
                             WizardViewsheetService wizardViewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.vsObjectTreeService = vsObjectTreeService;
      this.infoHandler = infoHandler;
      this.viewsheetService = viewsheetService;
      this.wizardViewsheetService = wizardViewsheetService;
   }

   /**
    * Undo/revert to a previous viewsheet state.
    *
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get or refresh viewsheet
    */
   @LoadingMask(true)
   @MessageMapping("undo")
   @HandleAssetExceptions
   public void undo(Principal principal, @LinkUri String linkUri,
                    CommandDispatcher dispatcher) throws Exception
   {
      RuntimeSheet rs =
         viewsheetService.getSheet(this.runtimeViewsheetRef.getRuntimeId(), principal);

      try {
         if(rs instanceof RuntimeViewsheet) {
            RuntimeViewsheet rvs = (RuntimeViewsheet) rs;
            Viewsheet ovs = rvs.getViewsheet().clone();
            ChangedAssemblyList clist =
               this.placeholderService.createList(true, dispatcher, rvs, linkUri);
            boolean undone = rvs.undo(clist);

            if(undone) {
               Viewsheet nvs = rvs.getViewsheet().clone();
               placeholderService.checkAndRemoveAssemblies(ovs, nvs, dispatcher);
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
   }

   /**
    * Redo/change to a future viewsheet state.
    *
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get or refresh viewsheet
    */
   @LoadingMask(true)
   @MessageMapping("redo")
   @HandleAssetExceptions
   public void redo(Principal principal, @LinkUri String linkUri,
                    CommandDispatcher dispatcher) throws Exception
   {
      RuntimeSheet rs =
         viewsheetService.getSheet(this.runtimeViewsheetRef.getRuntimeId(), principal);

      try {
         if(rs instanceof RuntimeViewsheet) {
            RuntimeViewsheet rvs = (RuntimeViewsheet) rs;
            Viewsheet ovs = (Viewsheet) rvs.getViewsheet().clone();
            ChangedAssemblyList clist =
               this.placeholderService.createList(true, dispatcher, rvs, linkUri);
            boolean redone = rvs.redo(clist);

            if(redone) {
               Viewsheet nvs = (Viewsheet) rvs.getViewsheet().clone();
               placeholderService.checkAndRemoveAssemblies(ovs, nvs, dispatcher);
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
   }

   private void updateViewsheet(RuntimeViewsheet rvs, String linkUri, ChangedAssemblyList clist,
                                CommandDispatcher dispatcher) throws Exception
   {
      this.placeholderService.refreshViewsheet(
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

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final VSObjectTreeService vsObjectTreeService;
   private final VSAssemblyInfoHandler infoHandler;
   private final ViewsheetService viewsheetService;
   private final WizardViewsheetService wizardViewsheetService;
}
