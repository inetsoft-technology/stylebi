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

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.gui.ObjectInfo;
import inetsoft.report.composition.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.SaveWorksheetController;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.SetWorksheetInfoCommand;
import inetsoft.web.viewsheet.HandleAssetExceptions;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.CloseSheetCommand;
import inetsoft.web.viewsheet.command.SaveSheetCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.Date;

@Controller
public class SaveWorksheetDialogController extends WorksheetController {
   @GetMapping("api/composer/ws/dialog/save-worksheet-dialog-model/{runtimeId}")
   @ResponseBody
   public SaveWorksheetDialogModel getSaveWorksheetInfo(
      @PathVariable("runtimeId") String runtimeId, Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(Tool.byteDecode(runtimeId), principal);
      WorksheetOptionPaneModel worksheetOptionPaneModel = new WorksheetOptionPaneModel(rws);
      AssetRepositoryPaneModel assetModel = new AssetRepositoryPaneModel();

      return SaveWorksheetDialogModel.builder()
         .worksheetOptionPaneModel(worksheetOptionPaneModel)
         .assetRepositoryPaneModel(assetModel)
         .build();
   }

   @PostMapping("api/composer/ws/dialog/save-worksheet-dialog-model/{runtimeId}")
   @ResponseBody
   public SaveWorksheetDialogModelValidator validateSaveWorksheet(
      @PathVariable("runtimeId") String runtimeId,
      @RequestBody SaveWorksheetDialogModel model, Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);

      try {
         SaveWorksheetDialogModelValidator validator = process(rws, model, principal, false);
         AssetEntry oentry = rws.getEntry();

         if((validator == null || Tool.isEmptyString(validator.alreadyExists())
            && Tool.isEmptyString(validator.permissionDenied())) &&
            oentry != null && oentry.getScope() == AssetRepository.TEMPORARY_SCOPE)
         {
            AutoSaveUtils.deleteAutoSaveFile(oentry, principal);
         }

         return validator;
      }
      catch(MessageException msgex) {
         String detailMessage = msgex.getMessage();
         String worksheetLabel = "worksheet";
         String permissionDenied = catalog.getString("security.nopermission.create",
                                                     worksheetLabel);

         if(detailMessage.equalsIgnoreCase(permissionDenied)) {
            return SaveWorksheetDialogModelValidator.builder()
               .permissionDenied(permissionDenied)
               .build();
         }
      }

      return null;
   }

   @LoadingMask
   @MessageMapping("/composer/ws/dialog/save-worksheet-dialog-model/")
   @HandleAssetExceptions
   public void saveWorksheet(
      @Payload SaveWorksheetDialogModel model, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      process(rws, model, principal, true);
      rws.setSavePoint(rws.getCurrent());

      final SetWorksheetInfoCommand setWorksheetInfoCommand = SetWorksheetInfoCommand.builder()
         .label(rws.getEntry().toView())
         .build();
      commandDispatcher.sendCommand(setWorksheetInfoCommand);

      final SaveSheetCommand saveSheetCommand = SaveSheetCommand.builder()
         .savePoint(rws.getSavePoint())
         .id(rws.getEntry().toIdentifier())
         .build();
      commandDispatcher.sendCommand(saveSheetCommand);
   }

   @LoadingMask
   @MessageMapping("/composer/ws/dialog/save-worksheet-dialog-model/save-and-close")
   public void saveAndCloseWorksheet(
      @Payload SaveWorksheetDialogModel model, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      this.saveWorksheet(model,principal,commandDispatcher);
      commandDispatcher.sendCommand(CloseSheetCommand.builder().build());
   }

   /**
    * Process save worksheet event.
    */
   public SaveWorksheetDialogModelValidator process(
      RuntimeWorksheet rws, SaveWorksheetDialogModel model, Principal principal,
      boolean confirmed) throws Exception
   {
      String name = model.assetRepositoryPaneModel().getName();
      name = name == null ? null : SUtil.removeControlChars(name);
      model.assetRepositoryPaneModel().setName(name);

      if(name == null) {
         AssetEntry entry = rws.getEntry();
         name = entry.getPath();
      }

      SaveWorksheetDialogModelValidator validator;

      try(DataSpace.Transaction tx = DataSpace.getDataSpace().beginTransaction()) {
         validator = process0(rws, model, principal, confirmed);
         tx.commit();
      }

      return validator;
   }

   private SaveWorksheetDialogModelValidator process0(
      RuntimeWorksheet rws, SaveWorksheetDialogModel model, Principal principal,
      boolean confirmed) throws Exception
   {
      String fullName = model.assetRepositoryPaneModel().getName();
      fullName = fullName == null ? null : SUtil.removeControlChars(fullName);
      model.assetRepositoryPaneModel().setName(fullName);
      String name = (fullName == null) ? null : fullName.substring(fullName.lastIndexOf('/') + 1);

      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      String wsName = box.getWSName();
      boolean reportSource = model.worksheetOptionPaneModel().getDataSource();
      UserVariable[] variables = rws.getWorksheet().getAllVariables();
      AssetEntry parent = model.assetRepositoryPaneModel().getParentEntry();

      // fix bug1283243523173, when parent is local worksheet, reset to root
      if(parent != null && ObjectInfo.REPORT.equals(parent.getProperty("mainType")))
      {
         AssetEntry pentry2 = new AssetEntry(
            AssetRepository.REPORT_SCOPE, AssetEntry.Type.FOLDER, "/", null);
         pentry2.copyProperties(parent);
         parent = pentry2;
      }

      AssetEntry entry = rws.getEntry();
      WorksheetService engine = super.getWorksheetEngine();
      AssetRepository assetRep = engine.getAssetRepository();

      // log save worksheet action
      String objectType = AssetEventUtil.getObjectType(entry);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), null, null, objectType,
                                         actionTimestamp,
                                         ActionRecord.ACTION_STATUS_FAILURE, null);

      WorksheetInfo winfo = rws.getWorksheet().getWorksheetInfo();
      winfo.setAlias(model.worksheetOptionPaneModel().getAlias());
      winfo.setDescription(model.worksheetOptionPaneModel().getDescription());

      try {
         if(name != null && parent != null) {
            actionRecord.setActionName(ActionRecord.ACTION_NAME_CREATE);
            String objectName = parent.getDescription() + "/" + name;
            actionRecord.setObjectName(objectName);

            try {
               parent.setProperty("_sheetType_", "worksheet");
               assetRep.checkAssetPermission(principal, parent, ResourceAction.WRITE);
            }
            catch(Exception ex) {
               if(ex instanceof ConfirmException) {
                  actionRecord = null;
               }

               if(actionRecord != null) {
                  actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
                  actionRecord.setActionError(ex.getMessage());

                  if(!confirmed) {
                     Audit.getInstance().auditAction(actionRecord, principal);
                  }
               }

               throw ex;
            }

            if(!parent.isFolder()) {
               String error = catalog.getString("common.invalidFolder");
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
               actionRecord.setActionError(error);
               throw new Exception(error);
            }

            String nname = parent.isRoot() ? name : parent.getPath() + "/" + name;
            IdentityID uname = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
            String alias = model.worksheetOptionPaneModel().getAlias();
            String description = model.worksheetOptionPaneModel().getDescription();
            entry = new AssetEntry(parent.getScope(), AssetEntry.Type.WORKSHEET, nname, uname, parent.getOrgID());
            entry.copyProperties(parent);
            entry.setReportDataSource(reportSource);
            entry.setProperty("description", description);
            String newName = entry.getSheetName();

            for(final UserVariable variable : variables) {
               String vname = variable.getName();
               Object varValue = engine.getCachedProperty(principal,
                                                          wsName + " variable : " + vname);
               engine.setCachedProperty(principal, newName + " variable : " + vname, varValue);
            }

            if(alias != null) {
               entry.setAlias(alias);
            }

            String desc = entry.getDescription();
            desc = desc.substring(0, desc.indexOf('/') + 1);
            desc += engine.localizeAssetEntry(entry.getPath(), principal, true, entry,
                                              entry.getScope() == AssetRepository.USER_SCOPE);
            entry.setProperty("_description_", desc);
            entry.setProperty("localStr", desc.substring(desc.lastIndexOf('/') + 1));
            actionRecord.setObjectName(entry.getDescription());

            RuntimeSheet[] opened = engine.getRuntimeSheets(principal);

            for(int i = 0; opened != null && i < opened.length; i++) {
               AssetEntry tentry = opened[i].getEntry();

               if(!assetRep.containsEntry(tentry)) {
                  continue;
               }

               if(Tool.equals(entry, tentry)) {
                  return SaveWorksheetDialogModelValidator.builder()
                  .alreadyExists(catalog.getString("common.overwriteForbidden"))
                  .build();
               }
            }

            // if select a leaf node, the user wants to overwrite the selected
            // worksheet, so do not check duplicate in this case.
            // fix bug1285570783088, consistent with others, check duplicate also
            if(engine.isDuplicatedEntry(assetRep, entry) && !confirmed) {
               actionRecord = null;
               String msg = assetRep.containsEntry(entry) ?
                  name + " " + catalog.getString("common.alreadyExists") +
                     ", " + catalog.getString("common.overwriteIt") + "?" :
                  catalog.getString("Duplicate Name");
               return SaveWorksheetDialogModelValidator.builder()
                  .alreadyExists(msg)
                  .build();
            }

            // Not confirmed, so do not set
            if(!confirmed) {
               return null;
            }

            if(!entry.equals(rws.getEntry())) {
               // make sure cleanup logic is run for old sheet
               if(engine.getAssetRepository().containsEntry(entry)) {
                  engine.getAssetRepository().removeSheet(entry, principal, true);
               }

               if(!rws.getEntry().getName().startsWith(catalog.getString("Untitled"))) {
                  rws.getWorksheet().fireEvent(AbstractSheet.SHEET_SAVE_AS, null);
               }
            }

            if(entry.equals(rws.getEntry()) ||
               rws.getEntry().getName().startsWith(Catalog.getCatalog().getString("Untitled")))
            {
               rws.getWorksheet().fireEvent(AbstractSheet.SHEET_SAVED, null);
            }

            WorksheetEventUtil.updateWorksheetMode(rws);
            engine.setWorksheet(rws.getWorksheet(), entry, principal, true, !model.updateDep());
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
            SaveWorksheetController.initWorksheetOldName(rws);

            if(model.updateDep()) {
               engine.fixRenameDepEntry(rws.getID(), entry);
               engine.renameDep(rws.getID());
            }
         }
         else {
            actionRecord.setActionName(ActionRecord.ACTION_NAME_EDIT);
            actionRecord.setObjectName(entry.getDescription());
            engine.setWorksheet(rws.getWorksheet(), entry, principal, true, true);
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         }

         // @by ankitmathur Fix bug1415292205565 If saving the Worksheet was
         // successful, check and remove any auto-saved versions of the
         // Worksheet. If this action was a "save as", we should also delete
         // the auto-saved version of the original Worksheet (if one exists).
         VSEventUtil.deleteAutoSavedFile(entry, principal);
      }
      catch(Exception ex) {
         if(ex instanceof ConfirmException) {
            actionRecord = null;
         }

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(ex.getMessage());
         }

         throw ex;
      }
      finally {
         if(actionRecord != null && confirmed) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }

      rws.setEntry(entry);
      rws.setEditable(true);

      long modified = rws.getWorksheet().getLastModified();
      Date date = new Date(modified);
      rws.getWorksheet().setLastModified(date.getTime());

      return null;
   }

   private final Catalog catalog = Catalog.getCatalog();
}
