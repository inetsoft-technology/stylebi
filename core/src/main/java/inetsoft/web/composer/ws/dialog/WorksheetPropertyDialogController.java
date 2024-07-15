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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.uql.asset.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.WorksheetOptionPaneModel;
import inetsoft.web.composer.model.ws.WorksheetPropertyDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.SetWorksheetInfoCommand;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides the REST endpoints for the worksheet property
 * dialog.
 *
 * @since 12.3
 */
@Controller
public class WorksheetPropertyDialogController extends WorksheetController {
   /**
    * Gets the top-level descriptor of the specified worksheet.
    *
    * @param runtimeId the runtime identifier of the worksheet.
    *
    * @return the worksheet descriptor.
    */
   @RequestMapping(
      value = "/api/composer/ws/dialog/worksheet-property-dialog-model/{runtimeId}",
      method = RequestMethod.GET)
   @ResponseBody
   public WorksheetPropertyDialogModel getWorksheetInfo(
      @PathVariable("runtimeId") String runtimeId, Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(Tool.byteDecode(runtimeId), principal);
      WorksheetPropertyDialogModel result = new WorksheetPropertyDialogModel();
      WorksheetOptionPaneModel worksheetOptionPaneModel = new WorksheetOptionPaneModel(
         rws);
      result.setWorksheetOptionPaneModel(worksheetOptionPaneModel);
      return result;
   }


   /**
    * Sets the top-level descriptor of the specified worksheet.
    *
    * @param runtimeId the runtime identifier of the worksheet.
    * @param model     the worksheet descriptor.
    * @param principal the current user principal
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/ws/dialog/worksheet-property-dialog-model/{runtimeId}")
   public void setWorksheetInfo(
      @DestinationVariable("runtimeId") String runtimeId,
      @Payload WorksheetPropertyDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(
         Tool.byteDecode(runtimeId), null);
      boolean success = process(rws, model, principal, commandDispatcher);

      if(success) {
         SetWorksheetInfoCommand command = SetWorksheetInfoCommand.builder()
            .label(rws.getEntry().toView())
            .build();
         commandDispatcher.sendCommand(command);
      }
   }

   /**
    * Process save worksheet event.
    *
    * @return true if property was updated, false otherwise.
    */
   public boolean process(
      RuntimeWorksheet rws, WorksheetPropertyDialogModel model,
      Principal user, CommandDispatcher commandDispatcher) throws Exception
   {
      boolean reportSource = model.getWorksheetOptionPaneModel().getDataSource();
      WorksheetInfo winfo = new WorksheetInfo();
      winfo.setAlias(model.getWorksheetOptionPaneModel().getAlias());
      winfo.setDescription(model.getWorksheetOptionPaneModel().getDescription());
      AssetEntry entry = rws.getEntry();
      Worksheet ws = rws.getWorksheet();

      entry.setReportDataSource(reportSource);

      boolean refresh = ws.setWorksheetInfo(winfo);

      if(refresh) {
         rws.getAssetQuerySandbox().resetTableLens();
         WorksheetEventUtil.refreshWorksheet(
            rws, super.getWorksheetEngine(), commandDispatcher, user);
      }

      WorksheetService wengine = super.getWorksheetEngine();
      AssetRepository engine = wengine.getAssetRepository();
      String alias = model.getWorksheetOptionPaneModel().getAlias();
      String desc0 = model.getWorksheetOptionPaneModel().getDescription();

      if(engine.containsEntry(entry)) {
         entry.setAlias(alias != null ? alias : "");
         entry.setProperty("description", desc0);
         String desc = entry.getDescription();
         desc = desc.substring(0, desc.indexOf("/") + 1);
         desc += wengine.localizeAssetEntry(entry.getPath(), user,
                                            true, entry, entry
                                               .getScope() == AssetRepository.USER_SCOPE);
         entry.setProperty("_description_", desc);
         entry.setProperty("localStr",
                           desc.substring(desc.lastIndexOf("/") + 1));
         rws.setEntry(entry);
         rws.setEditable(true);

         return true;
      }

      return false;
   }
}
