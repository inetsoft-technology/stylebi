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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Catalog;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class EMScheduleTaskActionController {
   @Autowired
   public EMScheduleTaskActionController(ScheduleTaskActionService actionService,
                                         SecurityProvider securityProvider,
                                         AssetRepository assetRepository)
   {
      this.actionService = actionService;
      this.securityProvider = securityProvider;
      this.assetRepository = assetRepository;
   }

   @GetMapping("/api/em/schedule/task/action/emails")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.EM, resource = "*", actions = ResourceAction.ACCESS
      )
   })
   public EmailTreeModel getEmailTree(Principal principal) {
      AuthenticationProvider authc = securityProvider.getAuthenticationProvider();
      Catalog catalog = Catalog.getCatalog(principal);
      EmailTreeModel.Builder builder = EmailTreeModel.builder();

      Arrays.stream(authc.getUsers())
         .filter(u -> securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER, u.convertToKey(), ResourceAction.ADMIN))
         .map(u -> UserEmailModel.builder().from(u, authc).build())
         //.sorted(Comparator.<UserEmailModel, IdentityID>comparing(UserEmailModel::userID))
         .forEach(builder::addUsers);

      Arrays.stream(authc.getGroups())
         .filter(g -> securityProvider.checkPermission(
            principal, ResourceType.SECURITY_GROUP, g.convertToKey(), ResourceAction.ADMIN))
         .map(g -> GroupEmailModel.builder().from(g, authc, catalog).build())
         .sorted(Comparator.comparing(GroupEmailModel::name))
         .forEach(builder::addGroups);

      return builder.build();
   }

   @GetMapping("/api/em/schedule/task/action/emails/user")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER, resource = "*", actions = ResourceAction.ACCESS
      )
   })
   public UserEmailsModel getEmbeddedUsers(Principal principal) {
      AuthenticationProvider authc = securityProvider.getAuthenticationProvider();
      UserEmailsModel.Builder builder = UserEmailsModel.builder();

      Arrays.stream(authc.getUsers())
         .filter(u -> securityProvider.checkPermission(principal, ResourceType.SECURITY_USER, u.convertToKey(), ResourceAction.ADMIN))
         .map(user -> UserEmailModel.builder().from(user, authc).build())
         .forEach(builder::addUsers);

      return builder.build();
   }

   @GetMapping("/api/em/schedule/task/action/email-browser-enabled")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER, resource = "*", actions = ResourceAction.ACCESS
      )
   })
   public boolean isEmailBrowserEnabled() {
      return SreeEnv.getBooleanProperty("schedule.options.emailBrowserEnable",
                                        "true", "CHECKED");
   }

   /**
    * Gets a table of scheduled tasks.
    *
    * @param taskName  the name of the task
    * @param index     the index of the action
    * @param principal the user
    *
    * @return the action model
    *
    * @throws Exception if could not get task or action
    */
   @GetMapping("/api/em/schedule/task/action")
   public ScheduleActionModel getTaskAction(@RequestParam("name") String taskName,
                                            @RequestParam("index") int index,
                                            Principal principal)
      throws Exception
   {
      return actionService.getTaskAction(taskName, index, principal, true);
   }

   /**
    * Removes an action from the task.
    *
    * @param taskName  the name of the task
    * @param items     the indexes of the actions to remove (in reverse sort)
    * @param principal the user
    *
    * @throws Exception if could not get task or actions
    */
   @GetMapping("/api/em/schedule/task/action/delete")
   public void deleteTaskActions(@RequestParam("name") String taskName,
                                 @RequestParam("owner") String taskOwner,
                                 @RequestBody int[] items,
                                 Principal principal)
      throws Exception
   {
      actionService.deleteTaskActions(taskName, taskOwner, items, principal);
   }

   /**
    * Saves the specified schedule task action
    *
    * @param taskName  the name of the task
    * @param index     the index of the action
    * @param principal the user
    *
    * @throws Exception if could not get task or action
    */
   @PostMapping("/api/em/schedule/task/action")
   public TaskActionListModel saveTaskAction(@RequestParam("name") String taskName,
                                             @RequestParam("oldTaskName") String oldTaskName,
                                             @RequestParam("owner") String owner,
                                             @RequestParam("index") int index,
                                             @RequestBody ScheduleActionModel model,
                                             @LinkUri String linkURI,
                                             Principal principal)
      throws Exception
   {
      IdentityID ownerID = IdentityID.getIdentityIDFromKey(owner);
      ScheduleActionModel[] taskActionList = actionService.saveTaskAction(
         taskName, oldTaskName, ownerID, index, model, linkURI, principal, true);

      return TaskActionListModel.builder()
         .actionList(Arrays.stream(taskActionList)
                        .map(ScheduleActionModel::label)
                        .toArray(String[]::new))
         .build();

   }

   /**
    * Get the bookmarks of a specific sheet.
    *
    * @param id        the id of the viewsheet.
    * @param principal the user information.
    *
    * @return the list of bookmarks.
    */
   @GetMapping("/api/em/schedule/task/action/bookmarks")
   public BookmarkListModel getBookmarks(@DecodeParam("id") String id, Principal principal) {
      return BookmarkListModel.builder()
         .bookmarks(actionService.getBookmarks(id, true, principal))
         .build();
   }

   /**
    * Whether the sheet has print layout.
    *
    * @param id        the id of the viewsheet.
    * @param principal the user information.
    *
    * @return the list of bookmarks.
    */
   @GetMapping("/api/em/schedule/task/action/hasPrintLayout")
   public boolean hasPrintLayout(@DecodeParam("id") String id, Principal principal)
      throws Exception
   {
      return actionService.hasPrintLayout(id, principal);
   }

   /**
    * Gets the highlights for a specified dashboard,
    *
    * @param identifier the id of the viewsheet entry
    * @param principal  the user
    *
    * @return a table of highlights
    *
    * @throws Exception if could not get report or dashboard
    */
   @GetMapping("/api/em/schedule/task/action/viewsheet/highlights")
   public HighlightListModel getViewsheetHighlights(@DecodeParam("id") String identifier,
                                                    Principal principal)
      throws Exception
   {
      return HighlightListModel.builder()
         .highlights(actionService.getViewsheetHighlights(identifier, principal))
         .build();
   }

   /**
    * Gets the parameters for a specified viewsheet
    *
    * @param identifier the id of the vs
    * @param principal  the user
    *
    * @return a list of parameters
    *
    * @throws Exception if could not get report or dashboard
    */
   @GetMapping("/api/em/schedule/task/action/viewsheet/parameters")
   public ViewsheetParametersModel getViewsheetParameters(@DecodeParam("id") String identifier,
                                                          Principal principal)
      throws Exception
   {
      return ViewsheetParametersModel.builder()
         .parameters(actionService.getViewsheetParameters(identifier, principal))
         .build();

   }

   /**
    * Gets all table data assemblies for a specified viewsheet
    *
    * @param identifier the id of the vs
    * @param principal  the user
    *
    * @return a list of parameters
    *
    * @throws Exception if could not get report or dashboard
    */
   @GetMapping("/api/em/schedule/task/action/viewsheet/tableDataAssemblies")
   public List<String> getViewsheetTableDataAssemblies(
      @DecodeParam("id") String identifier,
      Principal principal) throws Exception
   {
      return actionService.getViewsheetTableDataAssemblies(identifier, principal);
   }

   @GetMapping("/api/em/schedule/task/action/viewsheet/folders")
   public ViewsheetTreeListModel getViewsheetFolders(Principal principal) throws Exception {
      return actionService.getViewsheetTree(principal);
   }

   @GetMapping("/api/em/schedule/task/action/viewsheets")
   public Map<String, String> getViewsheets(Principal principal) throws Exception {
      return actionService.getViewsheets(principal);
   }

   private LabeledAssetEntryModel createAssetEntryModel(AssetEntry entry,
                                                        AssetEntry.Selector selector,
                                                        Principal principal, Catalog catalog)
   {
      return LabeledAssetEntryModel.builder()
         .label(AssetUtil.getEntryLabel(entry, catalog))
         .entry(entry)
         .children(getAssetChildren(entry, selector, principal, catalog))
         .build();
   }

   private List<LabeledAssetEntryModel> getAssetChildren(AssetEntry entry,
                                                         AssetEntry.Selector selector,
                                                         Principal principal, Catalog catalog)
   {
      if(entry.getType().isQuery() || entry.getType().isLogicModel()) {
         return Collections.emptyList();
      }

      List<AssetEntry> inFolders = new ArrayList<>();
      List<AssetEntry> inFolderModels = new ArrayList<>();
      List<AssetEntry> roots = new ArrayList<>();

      try {
         AssetEntry[] entries =
            assetRepository.getEntries(entry, principal, ResourceAction.READ, selector);

         for(AssetEntry childEntry : entries) {
            if(childEntry.isQuery() && childEntry.getProperty("folder") != null &&
               !childEntry.getProperty("folder").isEmpty())
            {
               inFolders.add(childEntry);
            }
            else if(childEntry.isLogicModel() && childEntry.getProperty("folder") != null &&
               !childEntry.getProperty("folder").isEmpty())
            {
               inFolderModels.add(childEntry);
            }
            else {
               roots.add(childEntry);
            }
         }
      }
      catch(Exception e) {
        throw new RuntimeException("Failed to list assets", e);
      }

      List<LabeledAssetEntryModel> list = new ArrayList<>();
      Collection<LabeledAssetEntryModel.Builder> folders = getFolderItems(inFolders, selector,
         principal, catalog);
      folders.stream()
         .map(LabeledAssetEntryModel.Builder::build)
         .forEach(list::add);

      folders = getFolderItems(inFolderModels, selector, principal, catalog);
      folders.stream()
         .map(LabeledAssetEntryModel.Builder::build)
         .forEach(list::add);

      roots.stream()
         .map(e -> createAssetEntryModel(e, selector, principal, catalog))
         .forEach(list::add);

      return list;
   }

   /**
    * Get the the query folder models or data model folder models by entries.
    * @param inFolders entries in folder.
    */
   private Collection<LabeledAssetEntryModel.Builder> getFolderItems(List<AssetEntry> inFolders,
                                                                     AssetEntry.Selector selector,
                                                                     Principal principal,
                                                                     Catalog catalog)
   {
      Map<String, LabeledAssetEntryModel.Builder> folders = new LinkedHashMap<>();

      for(AssetEntry entry : inFolders) {
         AssetEntry parentEntry = entry.getParent();
         String path = parentEntry.getName();
         LabeledAssetEntryModel.Builder builder = folders.computeIfAbsent(
            path, p -> LabeledAssetEntryModel.builder()
               .label(AssetUtil.getEntryLabel(parentEntry, catalog))
               .entry(parentEntry));
         builder.addChildren(createAssetEntryModel(entry, selector, principal, catalog));
      }

      return folders.values();
   }

   private final ScheduleTaskActionService actionService;
   private final SecurityProvider securityProvider;
   private final AssetRepository assetRepository;
}
