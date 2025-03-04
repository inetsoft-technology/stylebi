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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.log.LogLevel;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.CloseSheetCommand;
import inetsoft.web.viewsheet.command.SaveSheetCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Vector;

@Controller
public class SaveViewsheetDialogController {
   @Autowired
   public SaveViewsheetDialogController(
      CoreLifecycleService coreLifecycleService,
      RuntimeViewsheetRef runtimeViewsheetRef,
      AssetRepository assetRepository,
      ViewsheetService viewsheetService,
      ViewsheetSettingsService viewsheetSettingsService)
   {
      this.coreLifecycleService = coreLifecycleService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.viewsheetSettingsService = viewsheetSettingsService;
      this.assetRepository = assetRepository;
   }

   @RequestMapping(
      value = "/api/composer/vs/save-viewsheet-dialog-model/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public SaveViewsheetDialogModel getSaveViewsheetInfo(
      @RemainingPath String runtimeId, Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetInfo info = viewsheet.getViewsheetInfo();
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      if(SUtil.isDefaultVSGloballyVisible() && rvs.getEntry() != null &&
         !Tool.equals(pId.orgID, rvs.getEntry().getOrgID())) {

         try {
            assetRepository.checkAssetPermission(principal, rvs.getEntry(), ResourceAction.WRITE);
         }
         catch(Exception e) {
            throw new MessageException(Catalog.getCatalog().getString("deny.access.write.globally.visible"));
         }
      }

      SaveViewsheetDialogModel model = new SaveViewsheetDialogModel();
      model.setName(viewsheet.getRuntimeEntry() == null ? "":
         (viewsheet.getRuntimeEntry().getName() == null ? "": viewsheet.getRuntimeEntry().getName()));
      AssetEntry parent = viewsheet.getRuntimeEntry() == null ? null
         : viewsheet.getRuntimeEntry().getParent();

      if(parent != null) {
         // test drive creating vs with wsWizard=true has the parent scope as TEMPORARY_SCOPE,
         // and it would cause an invalid identifier when trying to save the vs without
         // selecting a parent folder.
         if(parent.getScope() == AssetRepository.TEMPORARY_SCOPE) {
            model.setParentId(AssetEntry.createGlobalRoot().toIdentifier());
         }
         else {
            model.setParentId(parent.toIdentifier());
         }
      }

      VSOptionsPaneModel vsOptionsPaneModel = new VSOptionsPaneModel();
      vsOptionsPaneModel.setUseMetaData(info.isMetadata());
      vsOptionsPaneModel.setPromptForParams(!info.isDisableParameterSheet());
      vsOptionsPaneModel.setSelectionAssociation(info.isAssociationEnabled());
      vsOptionsPaneModel.setMaxRowsWarning(info.isMaxRowsWarning());
      vsOptionsPaneModel.setCreateMv(info.isMVOnDemand());
      vsOptionsPaneModel.setAlias(viewsheet.getRuntimeEntry() == null ? null:
         viewsheet.getRuntimeEntry().getAlias());
      vsOptionsPaneModel.setDesc(info.getDescription());
      vsOptionsPaneModel.setServerSideUpdate(info.isUpdateEnabled());
      vsOptionsPaneModel.setTouchInterval(info.getTouchInterval());
      vsOptionsPaneModel.setListOnPortalTree(info.isOnReport());

      //use worksheet data size if datasource is worksheet
      if(viewsheet.getBaseEntry() != null && viewsheet.getBaseEntry().isWorksheet()) {
         int datasize = viewsheet.getBaseWorksheet().getWorksheetInfo().getDesignMaxRows();
         vsOptionsPaneModel.setMaxRows(datasize);
      }
      else {
         vsOptionsPaneModel.setMaxRows(info.getDesignMaxRows());
      }

      model.setViewsheetOptionsPaneModel(vsOptionsPaneModel);
      vsOptionsPaneModel.setViewsheetParametersDialogModel(
         viewsheetSettingsService.getViewsheetParameterInfo(rvs));

      SelectDataSourceDialogModel newVSDialogModel = new SelectDataSourceDialogModel();
      newVSDialogModel.setDataSource(viewsheet.getBaseEntry());
      vsOptionsPaneModel.setWorksheet(viewsheet.getBaseEntry() != null &&
                                         viewsheet.getBaseEntry().isWorksheet());
      vsOptionsPaneModel.setSelectDataSourceDialogModel(newVSDialogModel);

      return model;
   }

   @RequestMapping(
      value = "/api/composer/vs/save-viewsheet-dialog-model/{runtimeId}",
      method = RequestMethod.POST)
   @ResponseBody
   public SaveViewsheetDialogModelValidator validateSaveViewSheet(
      @PathVariable("runtimeId") String runtimeId,
      @RequestBody SaveViewsheetDialogModel model, Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      ViewsheetService vsService = viewsheetService;
      RuntimeViewsheet rvs = vsService.getViewsheet(runtimeId, principal);
      AssetEntry parent = AssetEntry.createAssetEntry(model.getParentId(), ((XPrincipal) principal).getOrgId());
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      if(parent == null) {
         parent = AssetEntry.createGlobalRoot();
      }

      String permissionDenied = "";

      try {
         assetRepository.checkAssetPermission(principal, parent, ResourceAction.WRITE);
      }
      catch(Exception ex) {
         permissionDenied = catalog.getString("security.nopermission.create",
                                              model.getName());
      }

      String nname = parent.isRoot() ? model.getName() : parent.getPath() +
         "/" + model.getName();
      AssetEntry entry = new AssetEntry(parent.getScope(), AssetEntry.Type.VIEWSHEET,
                             nname, pId, parent.getOrgID());
      entry.copyProperties(parent);
      String msg;
      boolean allowOverwrite = false;
      SaveViewsheetDialogModelValidator validator;

      if(vsService.isDuplicatedEntry(vsService.getAssetRepository(), entry)) {
         // do not use itself as viewsheet assemblies
         AssetEntry[] entries = rvs.getViewsheet().getOuterDependents();
         Vector vec = Tool.toVector(entries);
         boolean containsSelf = vec.contains(entry);
         Catalog catalog = Catalog.getCatalog();

         if(vsService.getAssetRepository().containsEntry(entry) && !containsSelf) {
            msg = model.getName() + " " + catalog.getString("common.alreadyExists") +
               ", " + catalog.getString("common.overwriteIt") + "?";
            allowOverwrite = true;
         }
         else {
            msg = catalog.getString("replet.duplicated");
         }

         try {
            assetRepository.checkAssetPermission(principal, entry, ResourceAction.WRITE);
         }
         catch(Exception ex) {
            allowOverwrite = false;
         }

         validator = SaveViewsheetDialogModelValidator.builder()
            .alreadyExists(msg)
            .allowOverwrite(allowOverwrite)
            .permissionDenied(permissionDenied)
            .build();
      }
      else {
         validator = SaveViewsheetDialogModelValidator.builder()
            .permissionDenied(permissionDenied)
            .build();
      }

      return validator;
   }

   @MessageMapping("/composer/vs/save-viewsheet-dialog-model")
   public void saveViewsheet(
      @Payload SaveViewsheetDialogModel model, @LinkUri String linkUri,
      Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      String runtimeId = runtimeViewsheetRef.getRuntimeId();
      ViewsheetService vsService = viewsheetService;
      RuntimeViewsheet rvs = vsService.getViewsheet(runtimeId, principal);
      AssetEntry oentry = rvs.getEntry();
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      if(oentry.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         AutoSaveUtils.deleteAutoSaveFile(oentry, principal);
      }

      rvs.setEditable(true);
      Viewsheet viewsheet = rvs.getViewsheet();
      AssetEntry entry;
      AssetEntry parent = AssetEntry.createAssetEntry(model.getParentId(), ((XPrincipal) principal).getOrgId());

      if(parent == null) {
         parent = AssetEntry.createGlobalRoot();
      }

      model.setName(model.getName() == null ?
         model.getName() : SUtil.removeControlChars(model.getName()));
      String nname = parent.isRoot() ? model.getName() : parent.getPath() +
         "/" + model.getName();
      entry = new AssetEntry(parent.getScope(), AssetEntry.Type.VIEWSHEET,
                             nname, pId, parent.getOrgID());
      entry.copyProperties(parent);

      String desc = entry.getDescription();
      desc = desc.substring(0, desc.indexOf("/") + 1);
      desc += vsService.localizeAssetEntry(entry.getPath(), principal,
         true, entry, entry.getScope() == AssetRepository.USER_SCOPE);
      entry.setProperty("_description_", desc);

      String objectName = parent.getDescription() + "/" + model.getName();
      ActionRecord actionRecord = SUtil.getActionRecord(principal,
                                                        ActionRecord.ACTION_NAME_CREATE,
                                                        objectName,
                                                        AssetEventUtil.getObjectType(entry));
      AssetRepository assetRepository = vsService.getAssetRepository();

      try {
         if(vsService.isDuplicatedEntry(assetRepository, entry)) {
            RuntimeViewsheet[] openSheets = vsService.getRuntimeViewsheets(null);

            for(int i = 0; openSheets != null && i < openSheets.length; i++) {
               AssetEntry tentry = openSheets[i].getEntry();

               if(!assetRepository.containsEntry(tentry)) {
                  continue;
               }

               if(openSheets[i].getMode() == Viewsheet.SHEET_DESIGN_MODE &&
                  !Tool.equals(runtimeId, openSheets[i].getID()) &&
                  Tool.equals(entry, tentry)) {
                  throw new MessageException(catalog.getString(
                     "common.overwriteForbidden"), LogLevel.DEBUG, false);
               }
            }
         }

         AssetEntry wentry = model.getViewsheetOptionsPaneModel()
            .getSelectDataSourceDialogModel().getDataSource();
         viewsheet.setBaseEntry(wentry);

         ViewsheetInfo info = viewsheet.getViewsheetInfo();

         VSOptionsPaneModel vsOptionsPaneModel = model.getViewsheetOptionsPaneModel();
         info.setMetadata(vsOptionsPaneModel.isUseMetaData());
         info.setDisableParameterSheet(!vsOptionsPaneModel.isPromptForParams());
         info.setDesignMaxRows(vsOptionsPaneModel.getMaxRows());
         info.setSnapGrid(vsOptionsPaneModel.getSnapGrid());
         info.setAssociationEnabled(vsOptionsPaneModel.isSelectionAssociation());
         info.setMaxRowsWarning(vsOptionsPaneModel.isMaxRowsWarning());
         info.setMVOnDemand(vsOptionsPaneModel.isCreateMv());
         info.setDescription(vsOptionsPaneModel.getDesc());
         info.setUpdateEnabled(vsOptionsPaneModel.isServerSideUpdate());
         info.setTouchInterval(vsOptionsPaneModel.getTouchInterval());
         info.setOnReport(vsOptionsPaneModel.isListOnPortalTree());
         entry.setAlias(vsOptionsPaneModel.getAlias());

         ViewsheetParametersDialogModel vsParametersDialogModel = model
            .getViewsheetOptionsPaneModel().getViewsheetParametersDialogModel();
         viewsheetSettingsService.setViewsheetParameterInfo(info, vsParametersDialogModel);

         vsService.setViewsheet(viewsheet, entry, principal, true, true);

         if(model.isUpdateDepend()) {
            vsService.fixRenameDepEntry(rvs.getID(), entry);
            vsService.renameDep(rvs.getID());
         }

         rvs.setSavePoint(rvs.getCurrent());
         rvs.setEntry(entry);
         rvs.setEditable(true);

         coreLifecycleService.setViewsheetInfo(rvs, linkUri, commandDispatcher);
         SaveSheetCommand command = SaveSheetCommand.builder()
            .savePoint(rvs.getSavePoint())
            .id(rvs.getEntry().toIdentifier())
            .build();
         commandDispatcher.sendCommand(command);
      }
      catch(Exception ex) {
         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(ex.getMessage());
         }

         throw ex;
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }
   }

   @LoadingMask
   @MessageMapping("/composer/vs/save-viewsheet-dialog-model/save-and-close")
   public void saveAndClose(
      @Payload SaveViewsheetDialogModel model, @LinkUri String linkUri,
      Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      saveViewsheet(model, linkUri, principal, commandDispatcher);
      commandDispatcher.sendCommand(CloseSheetCommand.builder().build());
   }

   @GetMapping("/api/composer/viewsheet/recycleAutoSave")
   @ResponseBody
   public void recycleAutoSave(Principal user) {
      AutoSaveUtils.recycleUserAutoSave(user);
   }

   private final CoreLifecycleService coreLifecycleService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final ViewsheetSettingsService viewsheetSettingsService;
   private final Catalog catalog = Catalog.getCatalog();
   private final AssetRepository assetRepository;
}
