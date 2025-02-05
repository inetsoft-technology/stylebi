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
package inetsoft.web.composer.tablestyle;

import inetsoft.report.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.adhoc.model.AlignmentInfo;
import inetsoft.web.adhoc.model.FontInfo;
import inetsoft.web.composer.tablestyle.css.CSSTableStyleModel;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;

@RestController
public class TableStyleController {
   @Autowired
   public TableStyleController(TableStyleService tableStyleService) {
      this.tableStyleService = tableStyleService;
   }

   @GetMapping("/api/composer/table-style/new")
   public TableStyleModel newTableStyle(Principal principal) {
      AssetEntry entry = tableStyleService.getTemporaryAssetEntry(principal);
      TableLens model = tableStyleService.getTableModel();
      XTableStyle style = new XTableStyle(model);
      tableStyleService.initTableStyle(style);

      TableStyleFormatModel styleFormat = new TableStyleFormatModel(style);
      TableStyleModel tableStyleModel = new TableStyleModel();
      tableStyleModel.setStyleFormat(styleFormat);
      tableStyleModel.setLabel(entry.getName());
      tableStyleModel.setId(entry.toIdentifier());
      tableStyleModel.setStyleName(null);
      tableStyleModel.setCssStyleFormat(tableStyleService.getCSSTableStyleModel(style));

      return tableStyleModel;
   }

   @PostMapping("/api/composer/table-style/css-format")
   @ResponseBody
   public CSSTableStyleModel getCSSTableStyleModel(@RequestBody TableStyleFormatModel model,
                                                   @RequestParam("styleId") String styleId)
   {
      LibManager manager = LibManager.getManager();
      TableLens lens = tableStyleService.getTableModel();
      XTableStyle style = null;

      if(manager.getTableStyle(styleId) == null) {
         style = new XTableStyle(lens);
      }
      else {
         style = manager.getTableStyle(styleId).clone();
         style.setTable(lens);
      }

      tableStyleService.initTableStyle(style);
      model.updateTableStyle(style);

      return tableStyleService.getCSSTableStyleModel(style);
   }

   @GetMapping("/api/composer/table-style/open")
   public TableStyleModel openTableStyle(@RequestParam("id") String id,
                                         @RequestParam("styleId") String styleId)
   {
      LibManager manager = LibManager.getManager();
      XTableStyle style = manager.getTableStyle(styleId);
      style.setTable(tableStyleService.getTableModel());
      tableStyleService.initTableStyle(style);

      TableStyleFormatModel styleFormat = new TableStyleFormatModel(style);
      TableStyleModel tableStyleModel = new TableStyleModel();
      tableStyleModel.setStyleFormat(styleFormat);
      tableStyleModel.setLabel(tableStyleService.getTableStyleLabel(style.getName()));
      tableStyleModel.setId(id);
      tableStyleModel.setStyleId(style.getID());
      tableStyleModel.setStyleName(style.getName());
      tableStyleModel.setCssStyleFormat(tableStyleService.getCSSTableStyleModel(style));

      return tableStyleModel;
   }

   @PostMapping("/api/composer/table-style/save")
   @ResponseBody
   public void saveTableStyle(@RequestBody TableStyleModel tableStyleModel, Principal principal)
      throws Exception
   {
      if(tableStyleModel == null) {
         return;
      }

      LibManager manager = LibManager.getManager();
      TableStyleFormatModel styleFormat = tableStyleModel.getStyleFormat();

      if(tableStyleModel.getStyleId() == null) {
         return;
      }

      XTableStyle style = manager.getTableStyle(tableStyleModel.getStyleId()).clone();
      String objectName = tableStyleService.getObjectName(Tool.getTableStyleLabel(style.getName()));
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_EDIT,
         objectName, ActionRecord.OBJECT_TYPE_TABLE_STYLE);

      try {
         styleFormat.updateTableStyle(style);
         IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
         style.setLastModified(System.currentTimeMillis());
         style.setLastModifiedBy(pId.getName());
         manager.setTableStyle(style.getID(), style);
         manager.save();
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

   @GetMapping("/api/composer/table-style/check-save-permission")
   public SaveLibraryDialogModelValidator saveCheckDialog(@RequestParam("styleName") String name,
                                                          @RequestParam("identifier") String identifier,
                                                          Principal principal)
   {
      SaveLibraryDialogModelValidator validator = new SaveLibraryDialogModelValidator();
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);

      if(entry == null) {
         entry = AssetEntry.createGlobalRoot();
      }

      entry.setProperty("styleName", name);
      tableStyleService.checkTableStylePermission(entry, name, validator, principal, true);

      return validator;
   }

   @GetMapping("/api/composer/table-style/check-save-as-permission")
   public SaveLibraryDialogModelValidator saveAsCheckDialog(
                                    @RequestParam("name") String name,
                                    @RequestParam("identifier") String identifier,
                                    @RequestParam(name = "folder", required = false) String folder,
                                    Principal principal)
   {
      SaveLibraryDialogModelValidator validator = new SaveLibraryDialogModelValidator();
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);

      if(entry == null) {
         entry = AssetEntry.createGlobalRoot();
      }

      entry.setProperty("folder", folder);
      tableStyleService.checkTableStylePermission(entry, name, validator, principal, false);
      tableStyleService.checkDuplicateTableStyle(folder, name, validator);
      return validator;
   }

   @PostMapping("/api/composer/table-style/save-as")
   @ResponseBody
   public String saveAsTableStyle(@RequestBody TableStyleRequestModel requestModel, Principal principal)
      throws Exception
   {
      if(requestModel == null) {
         return null;
      }

      SaveTableStyleDialogModel saveModel = requestModel.getSaveModel();
      String name = saveModel.getName();
      String styleName = tableStyleService.getTableStyleLabel(saveModel.getFolder(), name);
      String objectName = tableStyleService.getObjectName(Tool.getTableStyleLabel(styleName));
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_CREATE,
         objectName, ActionRecord.OBJECT_TYPE_TABLE_STYLE);
      XTableStyle style = null;

      try {
         LibManager manager = LibManager.getManager();
         style = manager.getTableStyle(styleName);
         String styleId = style == null ? manager.getNextStyleID(name) : style.getID();
         style = new XTableStyle(tableStyleService.getTableModel());
         style.setID(styleId);
         style.setName(styleName);
         IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
         style.setLastModified(System.currentTimeMillis());
         style.setLastModifiedBy(pId.getName());
         style.setCreated(System.currentTimeMillis());
         style.setCreatedBy(pId.getName());

         if(requestModel.getTableStyleModel() != null &&
            requestModel.getTableStyleModel().getStyleFormat() != null)
         {
            requestModel.getTableStyleModel().getStyleFormat().updateTableStyle(style);
         }

         manager.setTableStyle(style.getID(), style);
         manager.save();
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

      return style.getID();
   }

   /**
    * Gets the label of the specitionmodel
    */
   @PostMapping("/api/composer/table-style/spec-model")
   @ResponseBody
   public ArrayList<SpecificationModel> getCustomFormat(@RequestBody TableStyleModel tableStyleModel) {
      if(tableStyleModel == null || tableStyleModel.getStyleFormat() == null) {
         return null;
      }

      TableLens model = tableStyleService.getTableModel();
      XTableStyle style = new XTableStyle(model);
      ArrayList<SpecificationModel> specList = tableStyleModel.getStyleFormat().getSpecList();

      for(int i = 0; i < specList.size(); i++) {
         SpecificationModel specModel = specList.get(i);

         if(specModel != null && specModel.getSpecFormat() == null) {
            BodyRegionFormat specFmt = new BodyRegionFormat();
            specFmt.setFont(new FontInfo(BodyRegionFormat.defFont));
            specFmt.setAlignment(new AlignmentInfo(StyleConstants.FILL));
            specFmt.setRowBorder(-1);
            specFmt.setColBorder(-1);
            specModel.setSpecFormat(specFmt);
         }

         XTableStyle.Specification spec = style.new Specification();
         specModel.updateSpecification(spec);
         specModel.setLabel(spec.toString());
      }

      return specList;
   }

   private final TableStyleService tableStyleService;
}