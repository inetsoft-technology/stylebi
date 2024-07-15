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
package inetsoft.web.admin.schedule;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.portal.model.QueryColumnsModel;
import inetsoft.web.security.PermissionUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class EMScheduleBatchActionController {

   @Autowired
   public EMScheduleBatchActionController(AssetRepository assetRepository,
                                          ScheduleService scheduleService,
                                          ScheduleManager scheduleManager,
                                          ScheduleTaskActionService actionService,
                                          ContentRepositoryTreeService contentRepositoryTreeService,
                                          SecurityEngine securityEngine)
   {
      this.assetRepository = assetRepository;
      this.scheduleService = scheduleService;
      this.scheduleManager = scheduleManager;
      this.actionService = actionService;
      this.contentRepositoryTreeService = contentRepositoryTreeService;
      this.securityEngine = securityEngine;
   }

   @GetMapping("/api/em/schedule/batch-action/scheduled-tasks")
   public ScheduleTaskList getScheduledTasks(
      @RequestParam("taskName") String taskName,
      @PermissionUser Principal principal) throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      ScheduleTaskList.Builder builder = ScheduleTaskList.builder();
      boolean securityEnabled = securityEngine.isSecurityEnabled();
      builder.defaultTimeZone(principal);
      builder.timeFormat("yyyy-MM-dd HH:mm:ss");
      builder.showOwners(securityEnabled);
      Vector<ScheduleTask> tasks = scheduleService.getScheduleTasks("", "", true, principal);
      List<ScheduleTaskModel> taskModels = new ArrayList<>();

      for(ScheduleTask task : tasks) {
         if(Tool.equals(taskName, task.getName())) {
            continue;
         }

         if(task.getActionStream()
            .anyMatch((action) -> action instanceof ViewsheetAction))
         {
            ScheduleTaskModel.Builder taskBuilder = ScheduleTaskModel.builder()
               .fromTask(task, scheduleService, catalog);
            String name = task.getName();
            String label = name;

            if(!LicenseManager.getInstance().isEnterprise()) {
               int index = name.indexOf(":");

               if(index != -1) {
                  String userId = name.substring(0, index);

                  if(userId != null && userId.contains(IdentityID.KEY_DELIMITER)) {
                     IdentityID identityID = IdentityID.getIdentityIDFromKey(userId);
                     label = identityID.getName() + ":" + name.substring(index + 1);
                  }
               }
               else {
                  label = name;
               }
            }

            taskBuilder.label(label);
            taskModels.add(taskBuilder.build());
         }
      }

      return builder.addAllTasks(taskModels).build();
   }

   @GetMapping("/api/em/schedule/batch-action/query-tree")
   public LabeledAssetEntries getQueryTree(Principal principal) throws Exception {
      return getWorksheets(principal);
   }

   @GetMapping("/api/em/schedule/batch-action/parameters")
   public BatchParameterListModel getParameters(@RequestParam("taskName") String taskName,
                                                Principal principal) throws Exception
   {
      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task == null) {
         throw new RuntimeException("Selected Schedule Task does not exist: " + taskName);
      }

      Set<String> parameterNames = new LinkedHashSet<>();

      for(int i = 0; i < task.getActionCount(); i++) {
         ScheduleAction action = task.getAction(i);

         if(action instanceof ViewsheetAction) {
            List<String> vsParameters =
               actionService.getViewsheetParameters(((ViewsheetAction) action).getViewsheet(), principal);
            parameterNames.addAll(vsParameters);
            parameterNames.addAll(findVariablesInScheduleAction((ViewsheetAction) action));
         }
      }

      return BatchParameterListModel.builder()
         .parameterNames(parameterNames)
         .build();
   }

   /**
    * Find parameters in the subject, message and etc
    */
   private Set<String> findVariablesInScheduleAction(AbstractAction action) {
      Set<String> parameters = new LinkedHashSet<>();
      addVariablesFromString(action.getEmails(), parameters);
      addVariablesFromString(action.getCCAddresses(), parameters);
      addVariablesFromString(action.getBCCAddresses(), parameters);
      addVariablesFromString(action.getSubject(), parameters);
      addVariablesFromString(action.getAttachmentName(), parameters);
      addVariablesFromString(action.getMessage(), parameters);
      return parameters;
   }

   private void addVariablesFromString(String str, Set<String> parameters) {
      List<UserVariable> vars = XUtil.findVariables(str);

      if(vars.size() > 0) {
         vars.forEach((var) -> parameters.add(var.getName()));
      }
   }

   @PostMapping("/api/em/schedule/batch-action/query-columns")
   public QueryColumnsModel getQueryColumns(@RequestBody AssetEntry entry,
                                            Principal principal) throws Exception
   {
      TableAssembly tableAssembly = null;

      if(entry.isTable()) {
         AssetEntry wsEntry = new AssetEntry(entry.getScope(), AssetEntry.Type.WORKSHEET,
                                             entry.getParentPath(), entry.getUser());
         AbstractSheet sheet = assetRepository.getSheet(wsEntry, principal, true,
                                                        AssetContent.ALL);

         if(sheet != null) {
            Assembly assembly = sheet.getAssembly(entry.getName());

            if(assembly instanceof TableAssembly) {
               tableAssembly = (TableAssembly) assembly;
            }
         }
      }
      else if(entry.isWorksheet()) {
         Worksheet sheet = (Worksheet) assetRepository.getSheet(entry, principal, true,
                                                                AssetContent.ALL);
         Assembly assembly = sheet.getPrimaryAssembly();

         if(assembly instanceof TableAssembly) {
            tableAssembly = (TableAssembly) assembly;
         }
      }

      List<String> columns = new ArrayList<>();

      if(tableAssembly != null) {
         ColumnSelection columnSelection =
            tableAssembly.getColumnSelection(tableAssembly instanceof RelationalJoinTableAssembly);

         for(int i = 0; i < columnSelection.getAttributeCount(); i++) {
            DataRef col = columnSelection.getAttribute(i);
            columns.add(col.getName());
         }
      }

      return QueryColumnsModel.builder()
         .columns(columns)
         .columnLabels(columns)
         .build();
   }

   private LabeledAssetEntries getWorksheets(Principal principal) throws Exception {
      Map<AssetEntry, List<AssetEntry>> parentEntries =
         contentRepositoryTreeService.getParentAssetEntryMap();

      IdentityID userId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      AssetEntry globalWorksheetRoot = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "/", null);
      List<LabeledAssetEntryModel> globalChildren =
         getWorksheetChildren(globalWorksheetRoot, parentEntries, principal, null);

      AssetEntry userWorksheetRoot = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER, "/", userId);
      List<LabeledAssetEntryModel> userChildren =
         getWorksheetChildren(userWorksheetRoot, parentEntries, principal, userId);
      LabeledAssetEntryModel userFolder = LabeledAssetEntryModel.builder()
         .label(Catalog.getCatalog(principal).getString("User Worksheet"))
         .children(userChildren)
         .entry(userWorksheetRoot)
         .build();

      return LabeledAssetEntries.builder()
         .addEntries(userFolder)
         .addAllEntries(globalChildren)
         .build();
   }

   private List<LabeledAssetEntryModel> getWorksheetChildren(AssetEntry parentEntry,
                                                             Map<AssetEntry, List<AssetEntry>> entries,
                                                             Principal principal, IdentityID user)
   {
      if(parentEntry.isWorksheet()) {
         AbstractSheet sheet = null;

         try {
            sheet = assetRepository.getSheet(parentEntry, principal, true,
                                             AssetContent.ALL);
            List<LabeledAssetEntryModel> tableModels = new ArrayList<>();

            for(Assembly assembly : sheet.getAssemblies()) {
               if(assembly instanceof TableAssembly && !((TableAssembly) assembly).isOuter()) {
                  AssetEntry tableEntry =
                     new AssetEntry(user != null ? AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE,
                                    AssetEntry.Type.TABLE,
                                    parentEntry.getPath() + "/" + assembly.getName(),
                                    user);

                  tableModels.add(LabeledAssetEntryModel.builder()
                                     .label(assembly.getName())
                                     .entry(tableEntry)
                                     .build());
               }
            }

            return tableModels;
         }
         catch(Exception e) {
            // do nothing
         }

         return Collections.emptyList();
      }
      else {
         return entries.getOrDefault(parentEntry, Collections.emptyList())
            .stream()
            .filter(entry -> checkPermission(entry, principal) && (entry.getType().isFolder() || entry.getType().isWorksheet()))
            .sorted()
            .map(entry -> {
               List<LabeledAssetEntryModel> children = getWorksheetChildren(entry, entries, principal, user);

               // if the ws has no table assemblies then don't show it
               if(entry.isWorksheet() && children.isEmpty()) {
                  return null;
               }

               return LabeledAssetEntryModel.builder()
                  .label(entry.getName())
                  .entry(entry)
                  .children(children)
                  .build();
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
      }
   }

   /**
    * Check access permisson for target entry with target user.
    */
   private boolean checkPermission(AssetEntry entry, Principal principal) {
      try {
         return securityEngine.checkPermission(
            principal, ResourceType.ASSET, entry.getPath(), ResourceAction.READ);
      }
      catch(inetsoft.sree.security.SecurityException ex) {
         LOG.error("Failed to check permission for: " + entry.getPath(), ex);
      }

      return false;
   }

   private final AssetRepository assetRepository;
   private ScheduleService scheduleService;
   private final ScheduleManager scheduleManager;
   private final ScheduleTaskActionService actionService;
   private final ContentRepositoryTreeService contentRepositoryTreeService;
   private final SecurityEngine securityEngine;
   private static final Logger LOG = LoggerFactory.getLogger(EMScheduleBatchActionController.class);
}
