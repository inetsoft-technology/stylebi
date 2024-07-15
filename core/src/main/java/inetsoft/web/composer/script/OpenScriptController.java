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
package inetsoft.web.composer.script;

import inetsoft.report.LibManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptEnvRepository;
import inetsoft.web.composer.model.script.*;
import inetsoft.web.composer.script.service.ScriptService;
import org.mozilla.javascript.EvaluatorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class OpenScriptController {
   @Autowired
   public OpenScriptController(AssetRepository assetRepository,
                               ScriptService scriptService) {
      this.assetRepository = assetRepository;
      this.scriptService = scriptService;
   }

   @PostMapping(value = "/api/composer/save/script")
   public String saveScript(@RequestBody ScriptModel scriptModel, Principal principal) throws Exception {
      String name = scriptModel.getLabel();
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_EDIT,
         "Script Function/" + name, ActionRecord.OBJECT_TYPE_SCRIPT);

      try {
         LibManager lib = LibManager.getManager();
         String function = lib.getScript(name);
         String comment = scriptModel.getComment();
         boolean change = false;
         Catalog catalog = Catalog.getCatalog();
         String permissionDenied = "";
         AssetEntry entry = AssetEntry.createAssetEntry(scriptModel.getId());
         this.scriptService.updateScriptDependencies(scriptModel.getText(), function, entry);

         if(!function.equals(scriptModel.getText())) {
            change = true;
         }

         if(!Tool.isEmptyString(comment) && !Tool.equals(comment, lib.getScriptComment(name))) {
            lib.setScriptComment(name, comment);
            change = true;
         }

         if(change) {
            try {
               assetRepository.checkAssetPermission(principal, entry, ResourceAction.WRITE);
            }
            catch(Exception ex) {
               permissionDenied = catalog.getString(
                  "security.nopermission.create", scriptModel.getLabel());
               return permissionDenied;
            }

            lib.setScript(name, scriptModel.getText());
            lib.save();
         }

         return permissionDenied;
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

   @PostMapping(value = "/api/composer/save/as/script")
   public String saveScriptAs(@RequestBody ScriptRequestModel model, Principal principal) throws Exception {
      if(model == null) {
         return null;
      }

      ScriptModel scriptModel = model.getScriptModel();
      SaveScriptDialogModel saveModel = model.getSaveModel();
      AssetEntry parentEntry = AssetEntry.createAssetEntry(saveModel.getIdentifier());
      String name = saveModel.getName();
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry entry = new AssetEntry(parentEntry.getScope(), AssetEntry.Type.SCRIPT,
         name, pId, null);
      Catalog catalog = Catalog.getCatalog();
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_CREATE,
         "Script Function/" + name, ActionRecord.OBJECT_TYPE_SCRIPT);

      try {
         LibManager lib = LibManager.getManager();
         String scriptText = scriptModel.getText();
         scriptText = scriptText == null ? "" : scriptText;
         lib.setScript(saveModel.getName(), scriptText);

         if(!Tool.isEmptyString(scriptModel.getComment())) {
            lib.setScriptComment(saveModel.getName(), scriptModel.getComment());
         }

         lib.save();
         return entry.toIdentifier();
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

   @RequestMapping(value = "/api/composer/check/script", method=RequestMethod.POST)
   public String checkScript(@RequestBody String script) {
      ScriptEnv env = ScriptEnvRepository.getScriptEnv();

      try {
         env.compile(script);
      }
      catch(Exception e) {
         return "row:" + ((EvaluatorException) e).lineNumber() +
            ",col:" + ((EvaluatorException) e).columnNumber() +
            ",error:" + e.getMessage();
      }

      return null;
   }

   @RequestMapping(value = "/api/composer/script/save-script-dialog/", method=RequestMethod.POST)
   public SaveScriptDialogValidator validateSaveScript(@RequestBody SaveScriptDialogModel model, Principal principal) {
      LibManager lib = LibManager.getManager();
      Enumeration<String> e = lib.getScripts();
      List<String> list = new ArrayList<>();
      Catalog catalog = Catalog.getCatalog();
      AssetEntry entry = AssetEntry.createAssetEntry(model.getIdentifier(), ((XPrincipal) principal).getOrgId());
      String msg;
      boolean allowOverwrite;
      SaveScriptDialogValidator validator;
      String permissionDenied = "";

      try {
         assetRepository.checkAssetPermission(principal, entry, ResourceAction.WRITE);
      }
      catch(Exception ex) {
         permissionDenied = catalog.getString("security.nopermission.create",
            model.getName());
      }

      while(e.hasMoreElements()) {
         String scriptName = e.nextElement();

         if(!lib.isAuditScript(scriptName)) {
            list.add(scriptName);
         }
      }

      String[] scripts = new String[list.size()];
      list.toArray(scripts);

      for(int i = 0; i < scripts.length; i++) {
         if(model.getName().equals(scripts[i])) {
            msg = model.getName() + " " + catalog.getString("common.alreadyExists") +
               ", " + catalog.getString("common.overwriteIt") + "?";
            allowOverwrite = true;

            validator = SaveScriptDialogValidator.builder()
               .alreadyExists(msg)
               .allowOverwrite(allowOverwrite)
               .permissionDenied(permissionDenied)
               .build();

            return validator;
         }
      }

      validator = SaveScriptDialogValidator.builder()
         .permissionDenied(permissionDenied)
         .build();

      return validator;
   }

   private final AssetRepository assetRepository;
   private final ScriptService scriptService;
}
