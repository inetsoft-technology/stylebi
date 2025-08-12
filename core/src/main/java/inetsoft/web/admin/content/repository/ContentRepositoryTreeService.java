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
package inetsoft.web.admin.content.repository;

import inetsoft.mv.MVManager;
import inetsoft.report.LibManager;
import inetsoft.report.lib.logical.LogicalLibraryEntry;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.uql.xmla.*;
import inetsoft.util.*;
import inetsoft.web.*;
import inetsoft.web.admin.content.repository.model.LicensedComponents;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.admin.security.SSOType;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static inetsoft.sree.RepositoryEntry.*;

@Service
public class ContentRepositoryTreeService {
   @Autowired
   public ContentRepositoryTreeService(SecurityProvider securityProvider, XRepository repository,
                                       ResourcePermissionService permissionService,
                                       RepletRegistryManager registryManager,
                                       ScheduleTaskFolderService scheduleTaskFolderService)
   {
      this.securityProvider = securityProvider;
      this.repository = repository;
      this.permissionService = permissionService;
      this.registryManager = registryManager;
      this.scheduleTaskFolderService = scheduleTaskFolderService;
   }

   public LicensedComponents getLicensedComponents() {
      return LicensedComponents.builder()
         .reports(true)
         .dashboards(true)
         .worksheets(true)
         .build();
   }

   List<ContentRepositoryTreeNode> searchNodes(Principal principal, String filter) throws Exception {
      List<String> users = getUsers(principal).stream()
         .map(IdentityID::convertToKey)
         .collect(Collectors.toList());
      return getRootNodes(principal, users).stream()
         .map(n -> searchNode(n, filter, Tool.equals(n.label(), filter, false)))
         .filter(Objects::nonNull)
         .collect(Collectors.toList());
   }

   private ContentRepositoryTreeNode searchNode(ContentRepositoryTreeNode node, String filter,
                                                boolean fullMatched)
   {
      List<ContentRepositoryTreeNode> matchedChildren = new ArrayList<>();

      if(node.children() != null) {
         fullMatched = Tool.equals(node.label(), filter, false) || fullMatched;

         for(ContentRepositoryTreeNode child : node.children()) {
            ContentRepositoryTreeNode matchedChild = searchNode(child, filter, fullMatched);

            if(matchedChild != null) {
               matchedChildren.add(matchedChild);
            }
         }
      }

      if(node.label() != null && node.label().toLowerCase().contains(filter.toLowerCase()) ||
         !matchedChildren.isEmpty() || fullMatched)
      {
         return ContentRepositoryTreeNode.builder()
            .from(node)
            .children(matchedChildren)
            .build();
      }

      return null;
   }

   List<ContentRepositoryTreeNode> getRootNodes(Principal principal, List<String> usersToLoad) throws Exception {
      List<UserNodes> userNodes = createUserNodes(principal, usersToLoad);
      RegistrySupplier registryFn = new RegistrySupplier();
      Catalog catalog = Catalog.getCatalog();
      Principal contextPrincipal = ThreadContext.getContextPrincipal();
      ExecutorService executor =
         Executors.newFixedThreadPool(Math.min(Runtime.getRuntime().availableProcessors(), 6));

      try {
         // global viewsheets/worksheets/reports
         CompletableFuture<List<ContentRepositoryTreeNode>> assetNodesFuture =
            getNodesCompletableFuture(contextPrincipal, "Failed to get asset nodes", () -> {
               try {
                  return getAssetNodes(principal, registryFn);
               }
               catch(Exception e) {
                  throw new RuntimeException(e);
               }
            }, executor);

         // data source/library
         CompletableFuture<List<ContentRepositoryTreeNode>> objectNodesFuture =
            getNodesCompletableFuture(contextPrincipal, "Failed to get object nodes", this::getObjectNodes, executor);

         // prototype/trashcan/my reports
         CompletableFuture<List<ContentRepositoryTreeNode>> repositoryNodesFuture =
            getNodesCompletableFuture(contextPrincipal, "Failed to get asset nodes", () -> {
               try {
                  return getRepositoryNodes(userNodes, principal, registryFn);
               }
               catch(Exception e) {
                  throw new RuntimeException(e);
               }
            }, executor);

         // global dashboards/user dashboards
         CompletableFuture<List<ContentRepositoryTreeNode>> dashboardNodesFuture =
            getNodesCompletableFuture(contextPrincipal, "Failed to get dashboard nodes", () -> {
               try {
                  return getDashboardNodes(userNodes, principal);
               }
               catch(Exception e) {
                  throw new RuntimeException(e);
               }
            }, executor);

         CompletableFuture<List<ContentRepositoryTreeNode>> schedulerTaskNodesFuture =
            getNodesCompletableFuture(contextPrincipal, "Failed to get scheduler task nodes", () -> {
               try {
                  return getSchedulerNodes(principal);
               }
               catch(Exception e) {
                  throw new RuntimeException(e);
               }
            }, executor);

         //recycle bin node
         CompletableFuture<List<ContentRepositoryTreeNode>> recycleNodesFuture =
            getNodesCompletableFuture(contextPrincipal, catalog.getString("Failed to get asset nodes"), () -> {
               try {
                  return getRecycleNodes(userNodes, principal, registryFn);
               }
               catch(Exception e) {
                  throw new RuntimeException(e);
               }
            }, executor);

         CompletableFuture.allOf(assetNodesFuture, objectNodesFuture, repositoryNodesFuture,
                                 dashboardNodesFuture, schedulerTaskNodesFuture, recycleNodesFuture).join();
         List<ContentRepositoryTreeNode> assetNodes = assetNodesFuture.join();
         List<ContentRepositoryTreeNode> objectNodes = objectNodesFuture.join();
         List<ContentRepositoryTreeNode> repositoryNodes = repositoryNodesFuture.join();
         List<ContentRepositoryTreeNode> dashboardNodes = dashboardNodesFuture.join();
         List<ContentRepositoryTreeNode> schedulerTaskNodes = schedulerTaskNodesFuture.join();
         List<ContentRepositoryTreeNode> recycleNodes = recycleNodesFuture.join();

         // merge all and return
         return Stream.of(assetNodes.stream(), objectNodes.stream(), repositoryNodes.stream(),
                          dashboardNodes.stream(), schedulerTaskNodes.stream(), recycleNodes.stream())
            .flatMap(Function.identity())
            .map(n -> applyPermissions(n, principal))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
      }
      finally {
         if(!executor.isShutdown()) {
            executor.shutdown();
         }
      }
   }

   private CompletableFuture<List<ContentRepositoryTreeNode>> getNodesCompletableFuture(
      Principal contextPrincipal, String errorMessage,
      Supplier<List<ContentRepositoryTreeNode>> nodesSupplier, ExecutorService executor)
   {
      return CompletableFuture.supplyAsync(() -> {
            Principal oprincipal = ThreadContext.getContextPrincipal();

            try {
               ThreadContext.setContextPrincipal(contextPrincipal);

               if(contextPrincipal instanceof SRPrincipal) {
                  ThreadContext.setLocale(((SRPrincipal) contextPrincipal).getLocale());
               }

               return nodesSupplier.get();
            }
            catch(Exception ex) {
               throw new RuntimeException(errorMessage, ex);
            }
            finally {
               ThreadContext.setContextPrincipal(oprincipal);
            }
         }, executor)
         .handle((result, ex) -> {
            if(ex != null) {
               throw new RuntimeException(ex.getMessage(), ex);
            }

            return result;
         });
   }

   private List<IdentityID> getOrgUsers(Principal principal) {
      String ssoType = SreeEnv.getProperty("sso.protocol.type");
      boolean sso = "Auth0".equals(ssoType) || SSOType.OPENID.getName().equals(ssoType) ||
         SSOType.SAML.getName().equals(ssoType) || SSOType.CUSTOM.getName().equals(ssoType);
      String org = OrganizationManager.getInstance().getInstance().getCurrentOrgID(principal);

      if(!sso) {
         return getOrgUsers(principal, org);
      }

      Set<String> keys = IndexedStorage.getIndexedStorage().getKeys(null);

      if(keys == null || keys.isEmpty()) {
         return new ArrayList<>();
      }

      return keys.stream()
         .map(key -> AssetEntry.createAssetEntry(key).getUser())
         .filter(user -> user != null && Tool.equals(user.getOrgID(), org))
         .collect(Collectors.toCollection(HashSet::new))
         .stream()
         .collect(Collectors.toList());
   }

   private List<UserNodes> createUserNodes(Principal principal, List<String> usersToLoad)
      throws Exception
   {
      List<UserNodes> list = new ArrayList<>();
      RecycleBin recycleBin = RecycleBin.getRecycleBin();
      Set<IdentityID> recycleBinUsers = recycleBin.getEntries().stream()
         .map(RecycleBin.Entry::getOriginalUser)
         .filter(Objects::nonNull)
         .collect(Collectors.toSet());

      List<IdentityID> users = getOrgUsers(principal);

      for(IdentityID user : users) {
         UserNodes nodes = new UserNodes(user);
         RepletRegistry registry = null;
         boolean loadUser = usersToLoad.contains(user.convertToKey());

         if(loadUser || recycleBinUsers.contains(user)) {
            registry = RepletRegistry.getRegistry(user);
         }

         nodes.reports = getUserReports(user, loadUser ? registry : null, principal);
         nodes.dashboards = getUserDashboardNode(user, loadUser ? registry : null, principal);

         if(recycleBinUsers.contains(user)) {
            addRecycleFolders(user, recycleBin, nodes.recycled, registry, principal);
            getRecycleNodeFromUserAssets(
               user, nodes.recycled, principal, recycleBin, FOLDER, registry);
            getRecycleNodeFromUserAssets(
               user, nodes.recycled, principal, recycleBin,
               RepositoryEntry.WORKSHEET_FOLDER | RepositoryEntry.USER, registry);
         }

         list.add(nodes);
      }

      return list;
   }

   private void addRecycleFolders(IdentityID user, RecycleBin recycleBin,
                                  List<ContentRepositoryTreeNode> nodes, RepletRegistry registry,
                                  Principal principal) throws Exception
   {
      RepositoryEntry[] entries = registryManager.getRepositoryEntries(
         RecycleUtils.MY_REPORT_RECYCLE, 63, user, 1, registry, principal, false);
      nodes.addAll(addRecycleEntries(entries, recycleBin));
   }

   private List<ContentRepositoryTreeNode> addRecycleEntries(RepositoryEntry[] entries,
                                                             RecycleBin recycleBin)
   {
      List<ContentRepositoryTreeNode> nodes = new ArrayList<>();

      for(RepositoryEntry entry : entries) {
         RecycleBin.Entry binEntry = recycleBin.getEntry(entry.getPath());

         if(binEntry == null){
            continue;
         }

         IdentityID owner = SUtil.isMyReport(binEntry.getOriginalPath()) ?
            binEntry.getOriginalUser() : null;
         nodes.add(ContentRepositoryTreeNode.builder()
                      .label(binEntry.getName())
                      .path(binEntry.getPath())
                      .type(binEntry.getType())
                      .owner(owner)
                      .build());
      }

      return nodes;
   }

   private List<ContentRepositoryTreeNode> getRecycleNodes(List<UserNodes> userNodes,
                                                           Principal principal,
                                                           RegistrySupplier registryFn)
      throws Exception
   {
      // No path because this node is a placeholder who's visibility should be
      // controlled by it's children's permissions
      ContentRepositoryTreeNode.Builder recycleRoot = ContentRepositoryTreeNode.builder()
         .label(Catalog.getCatalog(principal).getString("Recycle Bin"))
         .path("")
         .owner(null)
         .type(RepositoryEntry.RECYCLEBIN_FOLDER);

      RecycleBin recycleBin = RecycleBin.getRecycleBin();
      List<ContentRepositoryTreeNode> userRecycled = userNodes.stream()
         .flatMap(n -> n.recycled.stream())
         .collect(Collectors.toList());

      final ContentRepositoryTreeNode recycleBinRepository =
         ContentRepositoryTreeNode.builder()
            .label(Catalog.getCatalog().getString("Repository"))
            .path(RecycleUtils.RECYCLE_BIN_FOLDER)
            .type(RepositoryEntry.RECYCLEBIN_FOLDER)
            .addAllChildren(addRecycleSheets(principal, recycleBin))
            .addAllChildren(userRecycled)
            .build();

      recycleRoot.addChildren(recycleBinRepository);

      // if auto save has administrator, support import auto save assets.
      // if no security, only can login em by admin.
      if (SecurityEngine.getSecurity().isSecurityEnabled() && !(OrganizationManager.getInstance().isSiteAdmin(principal) || OrganizationManager.getInstance().isOrgAdmin(principal))) {
         return Collections.singletonList(recycleRoot.build());
      }

      ContentRepositoryTreeNode recycleBinAutoSave =
              ContentRepositoryTreeNode.builder()
                      .label(Catalog.getCatalog().getString("Auto Saved Files"))
                      .path("Auto Save Files")
                      .type(RepositoryEntry.AUTO_SAVE_FOLDER)
                      .addAllChildren(addRecycleAutoSaved(userNodes, principal))
                      .build();

      recycleRoot.addChildren(recycleBinAutoSave);

      return Collections.singletonList(recycleRoot.build());
   }

   private List<ContentRepositoryTreeNode> addRecycleAutoSaved(List<UserNodes> userNodesList,
                                                               Principal principal)
   {
      List<ContentRepositoryTreeNode> userNodes = new ArrayList<>();
      List<String> list = AutoSaveUtils.getAutoSavedFiles(principal, true);

      if(list.isEmpty()) {
         return userNodes;
      }

      Calendar cal = Calendar.getInstance();
      Date date = new Date();
      cal.setTime(date);
      cal.add(Calendar.DATE, -7);
      long lastWeek = cal.getTime().getTime();

      HashMap<String, List<ContentRepositoryTreeNode>> map = new HashMap();
      List<String> users = new ArrayList();

      // auto save file name likes: 8^VIEWSHEET^_NULL_^Untitled-1^0_0_0_0_0_0_0_1~
      // split by ^ then we can get values: scope, type, user, asssetname, ip
      // get user to create folder and add asset under its user.
      for(int j = 0; j < userNodesList.size(); j++) {
         UserNodes userNode = userNodesList.get(j);

         for(int i = 0; i < list.size(); i++) {
            String file = list.get(i);
            String asset = AutoSaveUtils.getName(file);
            long lastModify = AutoSaveUtils.getLastModified(file, principal);

            // if the auto save file in recycle bin is large than one week, remove it and do not show
            // it.
            if(lastWeek > lastModify) {
               AutoSaveUtils.deleteAutoSaveFile(file, principal);
               continue;
            }

            String[] attrs = Tool.split(asset, '^');
            String nullUser = new IdentityID("_NULL_",Organization.getDefaultOrganizationID()).convertToKey();

            if(attrs.length > 3) {
               String type = attrs[1];
               String user = attrs[2];
               String name = attrs[3];
               user = "anonymous".equals(user) ? nullUser : user;

               if(!Tool.equals(user, nullUser) && userNode.user != null &&
                  !user.equals(userNode.user.convertToKey()))
               {
                  continue;
               }

               String key = user + "^" + type;
               int typeValue = "VIEWSHEET".equals(type) ? AUTO_SAVE_VS :  AUTO_SAVE_WS;
               String icon = "VIEWSHEET".equals(type) ? "viewsheet-icon" : "worksheet-icon";

               if(!users.contains(user)) {
                  users.add(user);
               }

               if(name.startsWith(Catalog.getCatalog().getString("Untitled"))) {
                  String left = name.substring(Catalog.getCatalog().getString("Untitled").length());
                  name = Catalog.getCatalog().getString("Untitled") + left;
               }

               ContentRepositoryTreeNode node = ContentRepositoryTreeNode.builder()
                  .label(name)
                  .path(asset)
                  .fullPath(asset)
                  .type(typeValue)
                  .icon(icon)
                  .owner(null)
                  .build();

               // Map will store two list for one user.
               // one is user_worksheet to store all auto save ws of user.
               // the other is user_viewsheet to store all auto save vs for user.
               if(map.get(key) == null) {
                  List<ContentRepositoryTreeNode> nodes = new ArrayList<>();
                  nodes.add(node);
                  map.put(key, nodes);
               }
               else if(!map.get(key).contains(node)) {
                  map.get(key).add(node);
               }
            }
         }
      }

      addUserNodes(users, map, userNodes);

      return userNodes;
   }

   private void addUserNodes(List<String> users, HashMap<String,
      List<ContentRepositoryTreeNode>> map, List<ContentRepositoryTreeNode> userNodes)
   {
      for(int i = 0; i < users.size(); i++) {
         String user = users.get(i);
         List<ContentRepositoryTreeNode> wsnodes = map.get(user + "^WORKSHEET");
         ContentRepositoryTreeNode ws = ContentRepositoryTreeNode.builder()
                 .label(Catalog.getCatalog().getString("Worksheet"))
                 .path(user + "/worksheet")
                 .type(AUTO_SAVE_WS_FOLDER)
                 .owner(null)
                 .children(wsnodes)
                 .build();
         List<ContentRepositoryTreeNode> vsnodes = map.get(user + "^VIEWSHEET");
         ContentRepositoryTreeNode vs = ContentRepositoryTreeNode.builder()
                 .label(Catalog.getCatalog().getString("Dashboard"))
                 .path(user + "/dashboard")
                 .type(AUTO_SAVE_VS_FOLDER)
                 .owner(null)
                 .children(vsnodes)
                 .build();

         List<ContentRepositoryTreeNode> unodes = new ArrayList<ContentRepositoryTreeNode>();
         unodes.add(ws);
         unodes.add(vs);
         IdentityID userID = IdentityID.getIdentityIDFromKey(user);
         String nullUser = new IdentityID("_NULL_",Organization.getDefaultOrganizationID()).convertToKey();

         userNodes.add(ContentRepositoryTreeNode.builder()
                 .label(nullUser.equals(user) ? "anonymous" : userID == null ? user : userID.getName())
                 .path(nullUser.equals(user) ? "anonymous" : user)
                 .type(AUTO_SAVE_FOLDER)
                 .owner(null)
                 .children(unodes)
                 .build());
      }
   }

   private List<ContentRepositoryTreeNode> addRecycleSheets(Principal principal,
                                                            RecycleBin recycleBin)
      throws Exception
   {
      List<ContentRepositoryTreeNode> nodes = new ArrayList<>();
      //get vs entries
      AssetEntry.Selector vsSelector = new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET_SNAPSHOT,
            AssetEntry.Type.REPOSITORY_FOLDER);
      AssetEntry.Type vsEntryType = AssetEntry.Type.REPOSITORY_FOLDER;
      nodes.addAll(getRecycleNodeFromAssets(principal, recycleBin, vsSelector, vsEntryType));

      //get ws entries
      AssetEntry.Selector wsSelector = new AssetEntry.Selector(AssetEntry.Type.WORKSHEET,
            AssetEntry.Type.FOLDER);
      AssetEntry.Type wsEntryType = AssetEntry.Type.FOLDER;
      nodes.addAll(getRecycleNodeFromAssets(principal, recycleBin, wsSelector, wsEntryType));

      return nodes;
   }

   private List<ContentRepositoryTreeNode> getRecycleNodeFromAssets(Principal principal,
                                         RecycleBin recycleBin, AssetEntry.Selector selector,
                                         AssetEntry.Type entryType)
      throws Exception
   {
      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
      List<ContentRepositoryTreeNode> nodes = new ArrayList<>();

      if(assetRepository != null) {
         AssetEntry pentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                 entryType, RecycleUtils.getRecycleBinPath(null), null);
         AssetEntry[] entries = assetRepository.getEntries(
            pentry, principal, ResourceAction.READ, selector);

         for(AssetEntry entry : entries) {
            RecycleBin.Entry binEntry = recycleBin.getEntry(entry.getPath());

            if(binEntry == null) {
               continue;
            }

            final ContentRepositoryTreeNode recycleBinNode = new ContentRepositoryTreeNode.Builder()
               .label(binEntry.getName())
               .path(entry.getPath())
               .type(binEntry.getType())
               .owner(binEntry.getOriginalUser())
               .build();

            nodes.add(recycleBinNode);
         }
      }

      return nodes;
   }

   private void getRecycleNodeFromUserAssets(IdentityID user, List<ContentRepositoryTreeNode> nodes,
                                             Principal principal, RecycleBin recycleBin, int type,
                                             RepletRegistry registry)
      throws Exception
   {
      String recycleBinFolder = type == (RepositoryEntry.WORKSHEET_FOLDER | RepositoryEntry.USER) ?
         "My Dashboards/Worksheets/" + RECYCLE_BIN_FOLDER : RECYCLE_BIN_FOLDER;

      RepositoryEntry[] entries = registryManager.getRepositoryEntries(
         recycleBinFolder, 63, user, type, registry, principal, false);

      for(RepositoryEntry entry : entries) {
         String path =
            entry.getPath().substring(entry.getPath().indexOf(RecycleUtils.RECYCLE_BIN_FOLDER));
         RecycleBin.Entry binEntry = recycleBin.getEntry(path);

         if(binEntry == null) {
            continue;
         }

         final ContentRepositoryTreeNode recycleBinNode = new ContentRepositoryTreeNode.Builder()
            .label(binEntry.getName())
            .path(path)
            .type(binEntry.getType())
            .owner(binEntry.getOriginalUser())
            .build();

         nodes.add(recycleBinNode);
      }
   }

   private Optional<ContentRepositoryTreeNode> applyPermissions(ContentRepositoryTreeNode node,
                                                                Principal principal)
   {
      boolean isUser = SUtil.isMyDashboard(node.path());
      boolean userFolder = node.type() == (RepositoryEntry.USER | FOLDER);
      boolean userOrDashboardRoot = userFolder &&
         (RepositoryEntry.USERS_FOLDER.equals(node.path()) ||
            RepositoryEntry.USERS_DASHBOARD_FOLDER.equals(node.path()) ||
            SUtil.MY_DASHBOARD.equals(node.path()));

      if(isUser && node.owner() != null && !userOrDashboardRoot &&
         !securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER, node.owner().convertToKey(), ResourceAction.ADMIN) &&
         !node.owner().equals(IdentityID.getIdentityIDFromKey(principal.getName())))
      {
         return Optional.empty();
      }

      List<ContentRepositoryTreeNode> children = null;

      if(node.children() != null) {
         children = Objects.requireNonNull(node.children()).stream()
            .map(n -> applyPermissions(n, principal))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
      }

      if(node.type() == AUTO_SAVE_VS_FOLDER || node.type() == AUTO_SAVE_WS_FOLDER ||
         node.type() == AUTO_SAVE_VS || node.type() == AUTO_SAVE_WS ||
         node.type() == AUTO_SAVE_FOLDER)
      {
         return Optional.of(ContentRepositoryTreeNode.builder()
                 .from(node)
                 .readOnly(false)
                 .builtIn(false)
                 .description(node.description())
                 .children(children)
                 .build());
      }

      boolean readOnly = false;
      boolean userRecycleFile = node.owner() != null &&
         node.path().startsWith(RecycleUtils.RECYCLE_BIN_FOLDER);

      if(userOrDashboardRoot) {
         readOnly = true;
      }
      else if(!SUtil.isMyReport(node.path()) && !SUtil.isMyDashboard(node.path()) &&
         !userRecycleFile)
      {
         String path = node.path();

         if(path.startsWith(RepositoryEntry.TRASHCAN_FOLDER + "/")) {
            path = path.substring(RepositoryEntry.TRASHCAN_FOLDER.length() + 1);
         }

         Resource resource = permissionService.getRepositoryResourceType(node.type(), path);

         if(resource != null) {
            if(resource.getType() == null) {
               if(node.type() != FOLDER) {
                  LOG.error("Invalid resource, type={}, path={}",  node.type(), node.path());
               }

               readOnly = true;
            }
            else if(node.type() == RepositoryEntry.RECYCLEBIN_FOLDER) {
               readOnly = !(securityProvider.checkPermission(
                  principal, resource.getType(), resource.getPath(), ResourceAction.ADMIN) &&
                  securityProvider.checkPermission(
                     principal, ResourceType.ASSET, resource.getPath(), ResourceAction.ADMIN));
            }
            else if(Objects.equals(path, RepositoryEntry.TRASHCAN_FOLDER)) {
               readOnly = children.isEmpty();
            }
            else if(node.type() == RepositoryEntry.SCHEDULE_TASK) {
               readOnly = !ScheduleManager.hasTaskPermission(node.owner(), principal, ResourceAction.READ);
            }
            else if(node.type() == (RepositoryEntry.SCHEDULE_TASK | FOLDER)) {
               readOnly = !securityProvider.checkPermission(
                  principal, resource.getType(), resource.getPath(), ResourceAction.ADMIN);
            }
            else {
               readOnly = !securityProvider.checkPermission(
                  principal, resource.getType(), resource.getPath(), ResourceAction.ADMIN);
            }
         }
      }

      if(readOnly && !userOrDashboardRoot && (children == null || children.isEmpty())) {
         return Optional.empty();
      }

      return Optional.of(ContentRepositoryTreeNode.builder()
                            .from(node)
                            .readOnly(readOnly ? true : null)
                            .builtIn(null)
                            .description(node.description())
                            .children(children)
                            .build());
   }

   /**
    * Get root nodes for prototype, trashcan, and my reports
    */
   private List<ContentRepositoryTreeNode> getRepositoryNodes(List<UserNodes> userNodes,
                                                              Principal principal,
                                                              RegistrySupplier registryFn)
      throws Exception
   {
      List<ContentRepositoryTreeNode> nodes = new ArrayList<>();

      final ContentRepositoryTreeNode userReports =
         getUserReports(userNodes, principal, registryFn);
      nodes.add(userReports);

      return nodes;
   }

   private ContentRepositoryTreeNode getUserReports(List<UserNodes> userNodes, Principal principal,
                                                    RegistrySupplier registryFn)
   {
      final DefaultFolderEntry userReportsFolder = new DefaultFolderEntry(
         RepositoryEntry.USERS_FOLDER, RepositoryEntry.USER | FOLDER, null);
      userReportsFolder.setLabel(Catalog.getCatalog(principal).getString("User Private Assets"));

      final List<ContentRepositoryTreeNode> reportNodes = userNodes.stream()
         .map(n -> n.reports)
         .collect(Collectors.toList());

      return createTreeNode(userReportsFolder, reportNodes, registryFn, principal);
   }

   public ContentRepositoryTreeNode getUserReports(IdentityID user, RepletRegistry registry,
                                                   Principal principal)
      throws Exception
   {
      final DefaultFolderEntry fentry = new DefaultFolderEntry(Tool.MY_DASHBOARD, user);
      fentry.setLabel(user.name);
      fentry.setType(RepositoryEntry.USER | FOLDER);
      List<ContentRepositoryTreeNode> children = null;

      if(registry != null) {
         children = getReportEntries(fentry.getPath(), 63, user, FOLDER, registry, principal);
      }

      return createTreeNode(fentry, children, registry, principal);
   }

   /**
    * Get tree nodes for items on the objects tree
    */
   private List<ContentRepositoryTreeNode> getObjectNodes() {
      final Set<XQuery> xQueries = new HashSet<>();
      final Catalog catalog = Catalog.getCatalog();

      final List<ContentRepositoryTreeNode> dataSources = getDataSources(null, xQueries);
      final ContentRepositoryTreeNode dataSource = ContentRepositoryTreeNode.builder()
         .label(catalog.getString("Data Source"))
         .path("/")
         .type(RepositoryEntry.DATA_SOURCE_FOLDER)
         .children(dataSources)
         .build();

      final String currentPath = catalog.getString("Library");
      final List<ContentRepositoryTreeNode> libraries = getLibraries(currentPath);
      final ContentRepositoryTreeNode library = ContentRepositoryTreeNode.builder()
         .label(catalog.getString("Library"))
         .path("*")
         .fullPath(currentPath)
         .type(RepositoryEntry.LIBRARY_FOLDER)
         .icon("books-icon")
         .children(libraries)
         .build();

      return Arrays.asList(dataSource, library);
   }

   /**
    * assets under repository and worksheets are global scoped.
    * assets under users reports are user scoped
    *
    * @return the type of scope given the path
    */
   public int getAssetScope(String path) {
      return path.startsWith(Tool.MY_DASHBOARD + "/") ?
         AssetRepository.USER_SCOPE :
         AssetRepository.GLOBAL_SCOPE;
   }

   /**
    * Get the non-prefixed portion of the scoped path.
    * For a repository entry it would return the path of its asset entry
    * <p>
    * For example,
    * Repository/Examples/Census -> Examples/Census
    */
   public String getUnscopedPath(String path) {
      return SUtil.getUnscopedPath(path);
   }

   private ContentRepositoryTreeNode createTreeNode(RepositoryEntry entry,
                                                    RegistrySupplier registryFn, Principal principal)
   {
      return createTreeNode(entry, null, registryFn, principal);
   }

   private ContentRepositoryTreeNode createTreeNode(RepositoryEntry entry,
                                                    List<ContentRepositoryTreeNode> children,
                                                    RegistrySupplier registryFn, Principal principal)
   {
      RepletRegistry registry = registryFn.get(entry.getOwner());
      return createTreeNode(entry, children, registry, principal);
   }

   private IndexedStorage getIndexStorage() {
      return IndexedStorage.getIndexedStorage();
   }

   private ContentRepositoryTreeNode createTreeNode(RepositoryEntry entry,
                                                    List<ContentRepositoryTreeNode> children,
                                                    RepletRegistry registry, Principal principal)
   {
      String description = "";
      String icon = null;
      String label = shouldShowEntryAlias(entry) ? entry.getLabel() : entry.getName();
      long lastModifiedTime = 0;

      if(entry.getType() == RepositoryEntry.WORKSHEET) {
         description = ((WorksheetEntry)entry).getDescription();
         AssetEntry assetEntry = entry.getAssetEntry();
         icon = getWorksheetTypeIcon(assetEntry);
         lastModifiedTime = getSheetLastModifiedTime(assetEntry, principal);
      }
      else if(entry.getType() == RepositoryEntry.VIEWSHEET) {
         description = ((ViewsheetEntry)entry).getDescription();
         lastModifiedTime = 0;

         if(isMaterializedViewsheet(entry)) {
            icon = "materialized-viewsheet-icon";
         }
         else if(((ViewsheetEntry)entry).isSnapshot()) {
            icon = "snapshot-icon";
         }
         else {
            icon = "viewsheet-icon";
         }
      }

      return ContentRepositoryTreeNode.builder()
         .label(label)
         .path(entry.getPath())
         .owner(entry.getOwner())
         .type(entry.getType())
         .icon(icon)
         .description(description)
         .lastModifiedTime(lastModifiedTime)
         .children(children)
         .build();
   }

   private List<ContentRepositoryTreeNode> getReportEntries(String path, int filter, IdentityID owner,
                                                            int type, RepletRegistry registry,
                                                            Principal principal) throws Exception
   {
      final RepositoryEntry[] entries = registryManager.getRepositoryEntries(
         path, filter, owner, type, registry, principal, false);
      final List<ContentRepositoryTreeNode> nodes = new ArrayList<>();

      for(RepositoryEntry entry : entries) {
         if(RecycleUtils.isInRecycleBin(entry.getPath())) {
            continue;
         }

         final List<ContentRepositoryTreeNode> children = getReportEntries(
            entry.getPath(), filter, entry.getOwner(), entry.getType(), registry,
            principal);
         nodes.add(createTreeNode(entry, children, registry, principal));
      }

      return nodes;
   }

   /**
    * Get tree nodes for global viewsheets/worksheets/reports
    */
   private List<ContentRepositoryTreeNode> getAssetNodes(Principal principal,
                                                         RegistrySupplier registryFn)
      throws Exception
   {
      final RepletRegistry registry = RepletRegistry.getRegistry();
      Map<AssetEntry, List<AssetEntry>> parentEntries = getParentAssetEntryMap();

      // build tree from root nodes (repository/worksheet)
      return parentEntries.keySet().stream()
         .filter(AssetEntry::isRoot)
         .filter(entry -> entry.getScope() == AssetRepository.GLOBAL_SCOPE)
         .filter(((Predicate<AssetEntry>)
            AssetEntry::isRepositoryFolder).or(AssetEntry::isWorksheetFolder))
         .sorted(Comparator.comparing(AssetEntry::getType).reversed())
         .map(parent -> getSubTree(parent, parentEntries))
         .collect(Collectors.toList());
   }

   public Map<AssetEntry, List<AssetEntry>> getParentAssetEntryMap() throws Exception {
      final AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
      assetRepository.syncFolders(null);
      final RepletRegistry registry = RepletRegistry.getRegistry();
      final IndexedStorage indexedStorage = IndexedStorage.getIndexedStorage();
      final AssetEntry[] entries = indexedStorage.getKeys(Objects::nonNull)
         .stream()
         .filter(key -> key != null && !key.contains("^" + Tool.MY_DASHBOARD + "/"))
         .map(AssetEntry::createAssetEntry)
         .filter(Objects::nonNull)
         .filter(e -> e.getType() != AssetEntry.Type.MV_DEF && e.getType() != AssetEntry.Type.MV_DEF_FOLDER)
         // Bug #60134, don't include invalid assets created by customers messing around with internals
         .filter(AssetEntry::isValid)
         .toArray(AssetEntry[]::new);

      // get map of parents -> children
      final Map<AssetEntry, List<AssetEntry>> parentEntries =
         Arrays.stream(entries)
            .filter(entry -> entry.getParent() != null)
            .collect(Collectors.groupingBy(AssetEntry::getParent));

      boolean repositoryFolderExists = false;
      boolean worksheetFolderExists = false;

      // add the folders from the replet registry
      for(Map.Entry<AssetEntry, List<AssetEntry>> parentEntry : parentEntries.entrySet()) {
         final AssetEntry parent = parentEntry.getKey();

         if(parent.isRepositoryFolder()) {
            final List<AssetEntry> childEntries = parentEntry.getValue();
            final Set<String> children = childEntries.stream()
               .map(AssetEntry::getPath)
               .collect(Collectors.toSet());
            final String path = parent.getPath();
            Arrays.stream(registry.getFolders(path, true))
               .filter(folder -> !children.contains(folder))
               .map(folder -> new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                             AssetEntry.Type.REPOSITORY_FOLDER,
                                             folder,
                                             null))
               .forEachOrdered(childEntries::add);
            if(parent.getUser() == null) {
               repositoryFolderExists = true;
            }
         }
         else if(parent.isWorksheetFolder() && parent.isRoot() && parent.getUser() == null) {
            worksheetFolderExists = true;
         }
      }

      if(!repositoryFolderExists) {
         Arrays.stream(entries)
            .filter(AssetEntry::isRoot)
            .filter(entry -> entry.getScope() == AssetRepository.GLOBAL_SCOPE)
            .filter(AssetEntry::isRepositoryFolder)
            .findFirst()
            .ifPresent(f -> parentEntries.put(f, new ArrayList<>()));
      }

      if(!worksheetFolderExists) {
         Arrays.stream(entries)
            .filter(AssetEntry::isRoot)
            .filter(entry -> entry.getScope() == AssetRepository.GLOBAL_SCOPE)
            .filter(AssetEntry::isWorksheetFolder)
            .filter((AssetEntry a) -> a.getType() == AssetEntry.Type.FOLDER)
            .findFirst()
            .ifPresent(f -> parentEntries.put(f, new ArrayList<>()));
      }

      for(Map.Entry<AssetEntry, List<AssetEntry>> parentEntry : parentEntries.entrySet()) {
         final List<AssetEntry> childEntries = parentEntry.getValue();
         childEntries.removeIf(
            entry -> RecycleUtils.isInRecycleBin(entry.getPath()));
      }

      return parentEntries;
   }

   private ContentRepositoryTreeNode getSubTree(AssetEntry entry,
                                                Map<AssetEntry, List<AssetEntry>> entries)
   {
      final List<ContentRepositoryTreeNode> children =
         entries.getOrDefault(entry, Collections.emptyList())
                .stream()
                .map(child -> getSubTree(child, entries))
                .collect(Collectors.toList());

      children.sort(getNodeComparator0());

      final int type;
      String name = entry.getName();
      String icon = null;
      IndexedStorage indexedStorage = IndexedStorage.getIndexedStorage();
      long lastModifiedTime = indexedStorage.lastModified(entry.toIdentifier());

      // worksheets are just folders, viewsheets are repository folders
      switch(entry.getType()) {
         case VIEWSHEET:
         case VIEWSHEET_SNAPSHOT:
            type = VIEWSHEET;
//            lastModifiedTime = getSheetLastModifiedTime(entry, principal);

            if(isMaterializedViewsheet(entry)) {
               icon = "materialized-viewsheet-icon";
            }
            else if(entry.isVSSnapshot()) {
               icon = "snapshot-icon";
            }

            break;
         case WORKSHEET:
            type = RepositoryEntry.WORKSHEET;
            icon = getWorksheetTypeIcon(entry);
//            lastModifiedTime = getSheetLastModifiedTime(entry, principal);
            break;
         case FOLDER:
            if(entry.isRoot()) {
               name = Catalog.getCatalog().getString("Worksheets");
               icon = "shared-worksheet-icon";
            }

            type = RepositoryEntry.WORKSHEET_FOLDER;
            break;
         case REPOSITORY_FOLDER:
            if(entry.isRoot()) {
               name = Catalog.getCatalog().getString("Repository");
               icon = "shared-report-icon";
            }

            type = RepositoryEntry.REPOSITORY | FOLDER;
            break;
         default:
            type = -1;
      }

      String description = entry.getProperty("Tooltip");
      description = description != null && !"".equals(description) ?
         description : entry.getDescription();

      return ContentRepositoryTreeNode.builder()
         .label(name)
         .path(entry.getPath())
         .type(type)
         .icon(icon)
         .description(description)
         .lastModifiedTime(lastModifiedTime)
         .children(children)
         .build();
   }

   private long getSheetLastModifiedTime(AssetEntry entry, Principal principal) {
      if(entry == null) {
         return 0;
      }

      return getIndexStorage().lastModified(entry.toIdentifier());
   }

   /**
    * Get the icon for a specific worksheet asset entry.
    */
   private String getWorksheetTypeIcon(AssetEntry entry) {
      if(entry == null) {
         return null;
      }

      if(isMaterializedWorksheet(entry)) {
         return "materialized-worksheet-icon";
      }

      String val = entry.getProperty(AssetEntry.WORKSHEET_TYPE);
      val = val == null ? Worksheet.TABLE_ASSET + "" : val;
      int wstype = Integer.parseInt(val);

      switch(wstype) {
      case Worksheet.CONDITION_ASSET:
         return "condition-icon";
      case Worksheet.NAMED_GROUP_ASSET:
         return "grouping-icon";
      case Worksheet.VARIABLE_ASSET:
         return "variable-icon";
      case Worksheet.TABLE_ASSET:
         return "worksheet-icon";
      case Worksheet.DATE_RANGE_ASSET:
         return "date-range-icon";
      default:
         return null;
      }
   }

   /**
    * Get the icon for a specific report asset entry.
    */
   private String getReportTypeIcon(RepletEntry entry) {
      if(entry == null) {
         return null;
      }

      String icon = null;

      if(entry.isParamOnly()) {
         icon = "report-param-only-icon";
      }
      else if(entry.isPregenerated()) {
         icon = "report-pregenerated-icon";
      }

      return icon;
   }

   private boolean isMaterializedViewsheet(AssetEntry entry) {
      return MVManager.getManager().isMaterialized(entry.toIdentifier(), false);
   }

   private boolean isMaterializedWorksheet(AssetEntry entry) {
      return MVManager.getManager().isMaterialized(entry.toIdentifier(), true);
   }

   private boolean isMaterializedViewsheet(RepositoryEntry entry) {
      return isMaterializedViewsheet(entry.getAssetEntry());
   }

   ContentRepositoryTreeNode getFolderNode(String name, int type, IdentityID owner) throws Exception {
      boolean global = owner == null;
      final String path = global ?  name : getUnscopedPath(name);

      return streamEntries()
         .filter(entry -> path.equals(entry.getPath()) && (global || Objects.equals(owner, entry.getUser())))
         .map(entry -> ContentRepositoryTreeNode.builder()
            .label(entry.getName())
            .path(name)
            .type(type)
            .owner(owner)
            .description(entry.getDescription())
            .build())
         .findFirst()
         .orElse(null);
   }

   ContentRepositoryTreeNode getDashboardNode(String name, IdentityID owner) {
      return owner == null || owner.name.isEmpty() ? createGlobalDashboardNode(name) :
         createUserDashboardNode(name, owner);
   }

   /**
    * Get tree nodes for global and user dashboards
    */
   private List<ContentRepositoryTreeNode> getDashboardNodes(List<UserNodes> userNodes,
                                                             Principal principal)
   {
      final Catalog catalog = Catalog.getCatalog();
      List<ContentRepositoryTreeNode> globalDashboards = getGlobalDashboardNodes(principal);
      final ContentRepositoryTreeNode globalDashboardRoot = ContentRepositoryTreeNode.builder()
         .label(catalog.getString("Portal Dashboard Tab"))
         .path("/")
         .type(RepositoryEntry.DASHBOARD_FOLDER)
         .children(globalDashboards)
         .build();
      List<ContentRepositoryTreeNode> userDashboards = getUserDashboardNodes(userNodes);
      final ContentRepositoryTreeNode userDashboardRoot = ContentRepositoryTreeNode.builder()
         .label(catalog.getString("User Portal Dashboard Tab"))
         .path(RepositoryEntry.USERS_DASHBOARD_FOLDER)
         .type(RepositoryEntry.USER | FOLDER)
         .children(userDashboards)
         .build();
      return Arrays.asList(globalDashboardRoot, userDashboardRoot);
   }

   /**
    * Get tree nodes for global dashboards.
    */
   private List<ContentRepositoryTreeNode> getGlobalDashboardNodes(Principal principal) {
      DashboardRegistry registry = DashboardRegistry.getRegistry();
      List<String> dashboardNames = Arrays.asList(registry.getDashboardNames());
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      List<IdentityID> adminUsers = OrganizationManager.getInstance().orgAdminUsers(orgID);
      Identity identity = new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER);

      if(SecurityEngine.getSecurity().isSecurityEnabled()) {
         if(OrganizationManager.getInstance().isSiteAdmin(principal) &&
            !orgID.equals(Organization.getDefaultOrganizationID()))
         {
            identity =adminUsers.isEmpty() ? null :
               new DefaultIdentity(adminUsers.getFirst().name, Identity.USER);
         }
         else {
            identity = getIdentity((XPrincipal) principal);
         }
      }

      List<String> sortedDashboards = Arrays.asList(DashboardManager.getManager().getDashboards(identity));
      dashboardNames.sort(Comparator.comparingInt(
         d -> !sortedDashboards.contains(d) ? Integer.MAX_VALUE : sortedDashboards.indexOf(d)));
      return dashboardNames.stream()
         .filter(Objects::nonNull)
         .map(this::createGlobalDashboardNode)
         .collect(Collectors.toList());
   }

   private ContentRepositoryTreeNode createGlobalDashboardNode(String dashboardName) {
      DashboardRegistry registry = DashboardRegistry.getRegistry();
      VSDashboard dashboard = (VSDashboard) registry.getDashboard(dashboardName);
      String label = dashboardName.replaceFirst("__GLOBAL", "");

      return dashboard == null ? null : ContentRepositoryTreeNode.builder()
         .label(label)
         .path(dashboardName)
         .type(RepositoryEntry.DASHBOARD)
         .description(dashboard.getDescription())
         .lastModifiedTime(dashboard.getLastModified())
         .children(null)
         .build();
   }

   private ContentRepositoryTreeNode createUserDashboardNode(String dashboardName, IdentityID user) {
      DashboardRegistry registry = DashboardRegistry.getRegistry(user);
      VSDashboard dashboard = (VSDashboard) registry.getDashboard(dashboardName);

      return dashboard == null ? null : ContentRepositoryTreeNode.builder()
         .label(dashboardName)
         .path(dashboardName)
         .owner(user)
         .type(RepositoryEntry.DASHBOARD)
         .description(dashboard.getDescription())
         .lastModifiedTime(dashboard.getLastModified())
         .children(null)
         .build();
   }

   /**
    * Get tree nodes for user dashboards.
    */
   private List<ContentRepositoryTreeNode> getUserDashboardNodes(List<UserNodes> userNodes) {
      return userNodes.stream()
         .map(n -> n.dashboards)
         .collect(Collectors.toList());
   }

   public ContentRepositoryTreeNode getUserDashboardNode(IdentityID user, RepletRegistry registry, Principal principal) {
      final DefaultFolderEntry fentry = new DefaultFolderEntry(SUtil.MY_DASHBOARD, user);
      fentry.setLabel(user.name);
      fentry.setType(RepositoryEntry.USER | FOLDER);
      List<ContentRepositoryTreeNode> children = null;

      if(registry != null) {
         children = getUserDashboardNodeChildren(user);
      }

      return createTreeNode(fentry, children, registry, principal);
   }

   private List<ContentRepositoryTreeNode> getUserDashboardNodeChildren(IdentityID user) {
      DashboardRegistry registry = DashboardRegistry.getRegistry(user);
      return Arrays.stream(registry.getDashboardNames())
         .map(name -> ContentRepositoryTreeNode.builder()
            .label(name)
            .path(SUtil.MY_DASHBOARD + "/" + name)
            .type(RepositoryEntry.DASHBOARD)
            .owner(user)
            .children(null)
            .lastModifiedTime(getUserDashboardModified(registry, name))
            .build())
         .collect(Collectors.toList());
   }

   private long getUserDashboardModified(DashboardRegistry registry, String name) {
      Dashboard dashboard = registry.getDashboard(name);

      if(dashboard == null) {
         return 0;
      }

      return ((VSDashboard) dashboard).getLastModified();
   }

   List<ContentRepositoryTreeNode> getScheduleTaskNodeChildren(Principal principal) {
      ScheduleManager manager = ScheduleManager.getScheduleManager();
      String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

      List<String> taskNames = manager.getScheduleTasks(orgID).stream()
         .filter(task -> !ScheduleManager.isInternalTask(task.getTaskId()))
         .map(task -> Tool.isEmptyString(task.getPath()) || task.getPath().equals("/")  ?
            task.getTaskId() : task.getPath() + "/" + task.getTaskId())
         .sorted()
         .collect(Collectors.toList());

      return getSchedulerRepositoryFolder(taskNames, "", manager, principal);
   }

   List<ContentRepositoryTreeNode> getSchedulerRepositoryFolder(List<String> taskNames, String path,
                                                                ScheduleManager manager,
                                                                Principal principal)
   {
      Map<String, ArrayList<String>> folderContents = getScheduleFolders(path, principal);
      ArrayList<String> rootTasks = new ArrayList<>();

      for(String taskName : taskNames) {
         int idx = taskName.indexOf("/");

         if(idx == -1) {
            rootTasks.add(taskName);
            continue;
         }

         String folderName = taskName.substring(0, idx);

         if(folderContents.containsKey(folderName)) {
            folderContents.get(folderName).add(taskName.substring(idx + 1));
         }
      }

      ArrayList<ContentRepositoryTreeNode> nodes = new ArrayList<>();

      for(String folderName : folderContents.keySet()) {
         String newPath = path.isEmpty() ? folderName : path + "/" + folderName;
         List<ContentRepositoryTreeNode> children =
            getSchedulerRepositoryFolder(folderContents.get(folderName), newPath, manager, principal);

         ContentRepositoryTreeNode folderNode = ContentRepositoryTreeNode.builder()
            .label(folderName)
            .path(newPath)
            .type(SCHEDULE_TASK | FOLDER)
            .children(children)
            .build();
         nodes.add(folderNode);
      }

      nodes.sort(Comparator.comparing(ContentRepositoryTreeNode::label));
      nodes.addAll(rootTasks.stream()
                            .filter(Objects::nonNull)
                            .map(taskName -> createScheduleTaskNode(taskName, path, manager, principal))
                            .collect(Collectors.toList()));

      return nodes;
   }

   private Map<String, ArrayList<String>> getScheduleFolders(String path, Principal principal) {
      Map<String, ArrayList<String>> folderContents = new HashMap<>();
      AssetEntry assetEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
         !Tool.isEmptyString(path) ? path : "/", null);
      AssetFolder taskFolder = null;

      try {
         taskFolder = scheduleTaskFolderService.getTaskFolder(assetEntry.toIdentifier());
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      if(taskFolder == null) {
         return folderContents;
      }

      AssetEntry[] entries = taskFolder.getEntries();

      for(AssetEntry entry : entries) {
         if(entry.getType().isScheduleTaskFolder()) {
            folderContents.put(entry.getName(), new ArrayList<>());
         }
      }

      return folderContents;
   }

   private static Stream<AssetEntry> streamEntries() {
      final IndexedStorage indexedStorage = IndexedStorage.getIndexedStorage();
      return indexedStorage.getKeys(Objects::nonNull)
         .stream()
         .map(AssetEntry::createAssetEntry)
         .filter(Objects::nonNull);
   }

   private List<ContentRepositoryTreeNode> getSchedulerNodes(Principal principal) {
      List<ContentRepositoryTreeNode> result = new ArrayList<>();
      List<ContentRepositoryTreeNode> children = getScheduleTaskNodeChildren(principal);

      result.add(ContentRepositoryTreeNode.builder()
                    .label(Catalog.getCatalog(principal).getString("Schedule Tasks"))
                    .path("/")
                    .type(SCHEDULE_TASK | FOLDER)
                    .children(children)
                    .build());

      return result;
   }

   private ContentRepositoryTreeNode createScheduleTaskNode(String taskFullName, String parentPath,
                                                            ScheduleManager manager, Principal principal)
   {
      int idx = taskFullName.indexOf(":");
      String taskName = taskFullName.substring(idx + 1 );
      String path = parentPath.isEmpty() ? taskFullName : parentPath + "/" + taskFullName;
      ScheduleTask task =  manager.getScheduleTask(taskFullName,
         OrganizationManager.getInstance().getCurrentOrgID(principal));
      long lastModified = task != null ? task.getLastModified() : 0;
      String ownerKey = taskFullName.substring(0, idx);
      idx = ownerKey.indexOf(IdentityID.KEY_DELIMITER);
      String user = idx != -1 ? ownerKey.substring(0, idx) : ownerKey;
      IdentityID ownerId = new IdentityID(user, OrganizationManager.getInstance().getCurrentOrgID());

      if(!Tool.isEmptyString(ownerId.getName())) {
         taskName = ownerId.getName() + ":" + taskName;
      }

      return ContentRepositoryTreeNode.builder()
         .label(taskName)
         .path(path)
         .owner(ownerId)
         .lastModifiedTime(lastModified)
         .type(SCHEDULE_TASK)
         .children(null)
         .icon("datetime-field-icon")
         .build();
   }

   private List<ContentRepositoryTreeNode> getLibraries(String rootPath) {
      final LibManager manager = LibManager.getManager();
      final Catalog catalog = Catalog.getCatalog();
      Map<Integer, List<ContentRepositoryTreeNode>> libraries = new HashMap<>();
      addLibraryFolder(libraries, RepositoryEntry.SCRIPT, getScripts(manager), rootPath);
      getLibraryStyles(libraries, null,
            rootPath + "/" + getLibraryLabel(Catalog.getCatalog(), RepositoryEntry.TABLE_STYLE));

      return libraries.entrySet().stream()
                      .map(entry -> {
                         final int type = entry.getKey();
                         final List<ContentRepositoryTreeNode> children = entry.getValue();
                         return ContentRepositoryTreeNode.builder()
                            .label(getLibraryLabel(catalog, type))
                            .path("*")
                            .fullPath(rootPath + "/" + getLibraryLabel(catalog, type))
                            .type(type | FOLDER)
                            .children(children)
                            .build();
                      })
                      .collect(Collectors.toList());
   }

   private String getLibraryLabel(Catalog catalog, int type) {
      switch(type) {
         case RepositoryEntry.SCRIPT:
            return catalog.getString("Scripts");
         case RepositoryEntry.TABLE_STYLE:
            return catalog.getString("Table Styles");
         default:
            return "";
      }
   }

   private void addLibraryFolder(Map<Integer, List<ContentRepositoryTreeNode>> libraries,
                                 int type,
                                 Enumeration<String> entries, String rootPath)
   {
      libraries.put(type, getLibraryEntry(entries, type,
            rootPath + "/" + getLibraryLabel(Catalog.getCatalog(), type)));
   }

   private List<ContentRepositoryTreeNode> getLibraryEntry(Enumeration<String> entries,
                                                           int type, String rootPath)
   {
      final List<ContentRepositoryTreeNode> nodes = new ArrayList<>();
      final LibManager manager = LibManager.getManager();

      while(entries.hasMoreElements()) {
         final String name = entries.nextElement();
         String description = "";
         long lastModifiedTime = 0;

         if(type == RepositoryEntry.SCRIPT) {
            description = manager.getScriptComment(name);
            LogicalLibraryEntry logicalLibraryEntry = manager.getLogicalLibraryEntry(name);
            lastModifiedTime = logicalLibraryEntry.modified();
         }

         final ContentRepositoryTreeNode node = ContentRepositoryTreeNode.builder()
            .label(name)
            .path(name)
            .fullPath(rootPath + "/" + name)
            .type(type)
            .description(description)
            .lastModifiedTime(lastModifiedTime)
            .build();
         nodes.add(node);
      }

      nodes.sort(Comparator.comparing(ContentRepositoryTreeNode::label));

      return nodes;
   }

   private List<ContentRepositoryTreeNode> getLibraryStyles(
      Map<Integer, List<ContentRepositoryTreeNode>> libraries, String name,
      String rootPath)
   {
      final LibManager manager = LibManager.getManager();
      Catalog catalog = Catalog.getCatalog();
      List<ContentRepositoryTreeNode> nodes = new ArrayList<>();
      List<ContentRepositoryTreeNode> tableStyles = new ArrayList<>();

      for(String folder : manager.getTableStyleFolders(name, true)) {
         String label = getLibraryStyleLabel(catalog, folder);
         String fullPath = rootPath + "/" + label;

         nodes.add(ContentRepositoryTreeNode.builder()
            .label(label)
            .path(folder)
            .fullPath(fullPath)
            .type(RepositoryEntry.TABLE_STYLE | FOLDER)
            .children(getLibraryStyles(libraries, folder, fullPath))
            .build());
      }

      for(XTableStyle style : manager.getTableStyles(name, true)) {
         if(style.getName() == null || style.getName().isEmpty()) {
            //populate style label
            XTableStyle tableStyle = manager.getTableStyle(style.getID());
            String styleName = tableStyle == null ? "" : tableStyle.getName();
            style.setName(styleName);
         }

         String label = getLibraryStyleLabel(catalog, style.getName());
         tableStyles.add(ContentRepositoryTreeNode.builder()
                      .label(label)
                      .path(style.getName())
                      .fullPath(rootPath + "/" + label)
                      .type(RepositoryEntry.TABLE_STYLE)
                      .lastModifiedTime(style.getLastModified())
                      .build());
      }

      nodes.sort(Comparator.comparing(node -> node.label().toUpperCase()));
      tableStyles.sort(Comparator.comparing(style -> style.label().toUpperCase()));
      nodes.addAll(tableStyles);

      if(name == null) {
         libraries.put(RepositoryEntry.TABLE_STYLE, nodes);
      }

      return nodes;
   }

   private String getLibraryStyleLabel(Catalog catalog, String name) {
      if(name == null) {
         return catalog.getString("Table Styles");
      }

      int index = name.lastIndexOf(LibManager.SEPARATOR);
      return index < 0 ? name : name.substring(index + 1);
   }

   public static String getDataSourceIconClass(String sourceType) {
      switch(sourceType) {
      case XDataSource.JDBC:
         return "database-icon";
      case XDataSource.XMLA:
         return "cube-icon";
      default:
         return "tabular-data-icon";
      }
   }

   /**
    * Get the top level data source nodes/folders
    */
   private List<ContentRepositoryTreeNode> getDataSources(String name, Set<XQuery> queries) {
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      final List<String> subDataSourceNames = registry.getSubDataSourceNames(name);
      final List<String> subfolderNames = registry.getSubfolderNames(name);

      Collections.sort(subDataSourceNames);
      Collections.sort(subfolderNames);

      // get data sources
      final List<ContentRepositoryTreeNode> dataSourceEntries =
         subDataSourceNames.stream()
                           .map(registry::getDataSource)
                           .filter(Objects::nonNull)
                           .map(dataSource -> ContentRepositoryTreeNode.builder()
                              .label(dataSource.getName())
                              .path(dataSource.getFullName())
                              .icon(getDataSourceIconClass(dataSource.getType()))
                              .description(dataSource.getDescription())
                              .lastModifiedTime(dataSource.getLastModified())
                              .type(RepositoryEntry.DATA_SOURCE | FOLDER)
                              .children(getDataSourceChildren(dataSource, queries))
                              .build()
                           )
                           .collect(Collectors.toList());

      // get data source folders
      final List<ContentRepositoryTreeNode> folders =
         subfolderNames.stream()
                       .map(folder -> {
                          String name2 = folder.substring(folder.lastIndexOf('/') + 1);
                          return ContentRepositoryTreeNode.builder()
                             .label(DataSourceFolder.getDisplayName(name2))
                             .path(folder)
                             .type(RepositoryEntry.DATA_SOURCE_FOLDER)
                             .children(getDataSources(folder, queries))
                             .build();
                       }).collect(Collectors.toList());

      final ArrayList<ContentRepositoryTreeNode> dataSources = new ArrayList<>();
      dataSources.addAll(folders);
      dataSources.addAll(dataSourceEntries);
      return dataSources;
   }

   private List<ContentRepositoryTreeNode> getDataSourceChildren(XDataSource dataSource,
                                                                 Set<XQuery> queries)
   {
      final List<ContentRepositoryTreeNode> nodes = new ArrayList<>();

      final List<ContentRepositoryTreeNode> dsModels = getDataSourceModels(dataSource);
      dsModels.sort(nodeComparator);
      nodes.addAll(dsModels);

      final List<ContentRepositoryTreeNode> dsQueries = getDataSourceQueries(dataSource, queries);
      dsQueries.sort(nodeComparator);
      nodes.addAll(dsQueries);

      final List<ContentRepositoryTreeNode> additionalDS =
         getAdditionalDataSources(dataSource, queries);
      additionalDS.sort(nodeComparator);
      nodes.addAll(additionalDS);

      final List<ContentRepositoryTreeNode> dsCubes = getDataSourceCubes(dataSource);
      dsCubes.sort(nodeComparator);
      nodes.addAll(dsCubes);

      return nodes;
   }

   private List<ContentRepositoryTreeNode> getDataSourceCubes(XDataSource dataSource) {
      if(dataSource instanceof XMLADataSource) {
         XDomain xDomain = DataSourceRegistry.getRegistry().getDomain(dataSource.getFullName());

         if(xDomain instanceof Domain) {
            Domain domain = (Domain) xDomain;
            List<ContentRepositoryTreeNode> nodes = new ArrayList<>();

            for(Enumeration<?> e = domain.getCubes(); e.hasMoreElements();) {
               Cube cube = (Cube) e.nextElement();
               nodes.add(ContentRepositoryTreeNode.builder()
                  .label(cube.getName())
                  .path(dataSource.getFullName() + "/" + cube.getName())
                  .type(RepositoryEntry.CUBE)
                  .icon("cube-icon")
                  .description(cube.getCaption())
                  .build());
            }

            return nodes;
         }
      }

      return Collections.emptyList();
   }

   private List<ContentRepositoryTreeNode> getAdditionalDataSources(XDataSource dataSource, Set<XQuery> queries) {
      if(dataSource instanceof AdditionalConnectionDataSource &&
         ((AdditionalConnectionDataSource<?>) dataSource).getBaseDatasource() == null)
      {
         return Arrays.stream(((AdditionalConnectionDataSource<?>) dataSource).getDataSourceNames())
            .map(s -> getAdditionalConnection(dataSource, s))
            .filter(Objects::nonNull)
            .map(pair -> ContentRepositoryTreeNode.builder()
               .label(pair.getLeft())
               .path(dataSource.getFullName() + "/" + pair.getRight().getFullName())
               .type(RepositoryEntry.DATA_SOURCE)
               .icon(getDataSourceIconClass(pair.getRight().getType()))
               .description(pair.getRight().getDescription())
               .children(getDataSourceChildren(pair.getRight(), queries))
               .lastModifiedTime(pair.getRight().getLastModified())
               .build())
            .collect(Collectors.toList());
      }

      return Collections.emptyList();
   }

   private Pair<String, AdditionalConnectionDataSource<?>> getAdditionalConnection(XDataSource parent, String name) {
      AdditionalConnectionDataSource<?> ds = ((AdditionalConnectionDataSource<?>) parent).getDataSource(name);
      return ds == null ? null : ImmutablePair.of(name, ds);
   }

   /**
    * Get the queries within a data source. Can be either a query or folder that contains queries
    */
   private List<ContentRepositoryTreeNode> getDataSourceQueries(XDataSource dataSource,
                                                                Set<XQuery> queries)
   {
      final List<ContentRepositoryTreeNode> folders =
         Arrays.stream(dataSource.getFolders())
               .map(folder -> ContentRepositoryTreeNode.builder()
                  .label(folder)
                  .path(folder + "::" + dataSource.getFullName())
                  .type(RepositoryEntry.QUERY | FOLDER)
                  .description(dataSource.getDescription())
                  .children(getFolderQueries(dataSource, folder, queries))
                  .build()
               )
               .collect(Collectors.toList());

      final List<ContentRepositoryTreeNode> subQueries =
         queries.stream()
                .filter(query -> query.getFolder() == null &&
                   dataSource.equals(query.getDataSource()) &&
                   dataSource.getName().equals(query.getDataSource().getName()))
                .map(query -> ContentRepositoryTreeNode.builder()
                   .label(query.getName())
                   .path(query.getName())
                   .type(RepositoryEntry.QUERY)
                   .description(query.getDescription())
                   .lastModifiedTime(query.getLastModified())
                   .build()
                )
                .collect(Collectors.toList());

      final List<ContentRepositoryTreeNode> nodes = new ArrayList<>();
      nodes.addAll(folders);
      nodes.addAll(subQueries);
      return nodes;
   }

   /**
    * Get the queries in a folder for a given data source
    */
   private List<ContentRepositoryTreeNode> getFolderQueries(XDataSource dataSource,
                                                            String folder,
                                                            Set<XQuery> queries)
   {
      return queries.stream()
                    .filter(query -> folder != null && folder.equals(query.getFolder()) &&
                       dataSource != null && dataSource.equals(query.getDataSource()) &&
                       dataSource.getName().equals(query.getDataSource().getName()))
                    .map(query -> ContentRepositoryTreeNode.builder()
                       .label(query.getName())
                       .path(query.getName())
                       .type(RepositoryEntry.QUERY)
                       .lastModifiedTime(query.getLastModified())
                       .description(query.getDescription())
                       .build()
                    )
                    .collect(Collectors.toList());
   }

   /**
    * @return Logical Models, Partitions, and Virtual Private Models for the datasource
    */
   private List<ContentRepositoryTreeNode> getDataSourceModels(XDataSource dataSource)
   {
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      final String dataSourceName = dataSource.getFullName();
      final XDataModel dataModel = registry.getDataModel(dataSourceName);
      final List<ContentRepositoryTreeNode> nodes = new ArrayList<>();

      if(dataModel != null) {
         List<ContentRepositoryTreeNode> rootDataModelItems = new ArrayList<>();

         String[] dataModelFolders = dataModel.getFolders();
         Map<String, List<ContentRepositoryTreeNode>> folderMap = new HashMap<>();

         if(dataModelFolders != null) {
            for(String dataModelFolder : dataModelFolders) {
               folderMap.put(dataModelFolder, new ArrayList<>());
            }
         }

         Arrays.stream(dataModel.getPartitionNames())
            .forEachOrdered(name -> {
               XPartition partition = dataModel.getPartition(name);

               if(partition == null) {
                  return;
               }

               ContentRepositoryTreeNode node =
                  getPartitionNode(name, partition.getFolder(), dataSourceName, dataModel);

               if(partition.getFolder() != null && folderMap.get(partition.getFolder()) != null) {
                  folderMap.get(partition.getFolder()).add(node);
               }
               else {
                  rootDataModelItems.add(node);
               }
            });

         for(String next : dataModel.getLogicalModelNames()) {
            XLogicalModel logicalModel = dataModel.getLogicalModel(next);

            if(logicalModel == null) {
               continue;
            }

            String folder = logicalModel.getFolder();
            String path = XUtil.getDataModelDisplayPath(dataSourceName, folder, null, next);

            ContentRepositoryTreeNode node = ContentRepositoryTreeNode.builder()
               .label(next)
               .path(path)
               .type(RepositoryEntry.LOGIC_MODEL | FOLDER)
               .icon("logical-model-icon")
               .description(logicalModel.getDescription())
               .lastModifiedTime(logicalModel.getLastModified())
               .children(getExtendedModels(logicalModel, path))
               .build();

            if(folder != null && folderMap.get(folder) != null) {
               folderMap.get(logicalModel.getFolder()).add(node);
            }
            else {
               rootDataModelItems.add(node);
            }
         }

         List<ContentRepositoryTreeNode> dataModelChildren = new ArrayList<>();

         folderMap.keySet().stream()
            .map(name ->  ContentRepositoryTreeNode.builder()
               .label(name)
               .path(dataSourceName + "/" + name)
               .type(RepositoryEntry.DATA_MODEL | FOLDER)
               .icon("folder-icon")
               .children(folderMap.get(name))
               .build())
            .forEach(dataModelChildren::add);

         dataModelChildren.addAll(rootDataModelItems);
         nodes.add(ContentRepositoryTreeNode.builder()
                      .label(Catalog.getCatalog().getString("Data Model"))
                      .path(dataSourceName)
                      .type(RepositoryEntry.DATA_SOURCE | FOLDER)
                      .icon("db-model-icon")
                      .description(dataSource.getDescription())
                      .children(dataModelChildren)
                      .build());

         Arrays.stream(dataModel.getVirtualPrivateModelNames())
            .filter(vpm -> dataModel.getVirtualPrivateModel(vpm) != null)
            .map(vpm -> ContentRepositoryTreeNode.builder()
               .label(vpm)
               .path(dataSourceName + "^" + vpm)
               .description(dataModel.getVirtualPrivateModel(vpm).getDescription())
               .lastModifiedTime(dataModel.getVirtualPrivateModel(vpm).getLastModified())
               .type(RepositoryEntry.VPM)
               .build())
            .forEachOrdered(nodes::add);
      }

      return nodes;
   }

   private ContentRepositoryTreeNode getPartitionNode(String partitionName,
                                                      String folder,
                                                      String dataSourceName,
                                                      XDataModel dataModel)
   {
      final String path = XUtil.getDataModelDisplayPath(dataSourceName, folder, null, partitionName);
      XPartition partition = dataModel.getPartition(partitionName);

      return ContentRepositoryTreeNode.builder()
         .label(partitionName)
         .path(path)
         .type(RepositoryEntry.PARTITION | FOLDER)
         .icon("partition-icon")
         .description(partition.getDescription())
         .lastModifiedTime(partition.getLastModified())
         .children(getExtendedPartitions(partition, path))
         .build();
   }

   private List<ContentRepositoryTreeNode> getExtendedModels(XLogicalModel xDataModel, String parentPath) {
      final String[] logicalModels = xDataModel.getLogicalModelNames();

      return Arrays.stream(logicalModels)
         .map(next -> ContentRepositoryTreeNode.builder()
            .label(next)
            .path(parentPath + "^" + next)
            .type(RepositoryEntry.LOGIC_MODEL)
            .description(xDataModel.getLogicalModel(next).getDescription())
            .lastModifiedTime(xDataModel.getLogicalModel(next).getLastModified())
            .build())
         .collect(Collectors.toList());
   }

   private List<ContentRepositoryTreeNode> getExtendedPartitions(XPartition xPartition, String parentPath) {
      final String[] partitionNames = xPartition.getPartitionNames();
      String folder = xPartition.getFolder();

      return Arrays.stream(partitionNames)
         .map(name -> ContentRepositoryTreeNode.builder()
            .label(name)
            .path(parentPath + "^" + name)
            .type(RepositoryEntry.PARTITION)
            .description(xPartition.getPartition(name).getDescription())
            .lastModifiedTime(xPartition.getPartition(name).getLastModified())
            .build())
         .collect(Collectors.toList());
   }

   private Enumeration<String> getScripts(LibManager manager) {
      Enumeration<String> managerScripts = manager.getScripts();
      List<String> scriptBeans = new ArrayList<>();

      while(managerScripts.hasMoreElements()) {
         String scriptName = managerScripts.nextElement();

         if(!manager.isAuditScript(scriptName)) {
            scriptBeans.add(scriptName);
         }
      }

      return Collections.enumeration(scriptBeans);
   }

   /**
    * Get the user identity for dashboard.
    */
   public Identity getIdentity(XPrincipal principal) {
      boolean securityEnabled = SecurityEngine.getSecurity().isSecurityEnabled();
      IdentityID userID = principal.getIdentityID();
      Identity identity;

      // @by billh, fix customer bug1303944306880
      // handle SSO problem
      User user = securityProvider.getUser(userID);

      if(securityEnabled && user != null) {
         identity = new User(userID, new String[0], principal.getGroups(),
                             principal.getRoles(), null, null);
      }
      else {
         identity = securityEnabled || !XPrincipal.ANONYMOUS.equals(userID.name) ?
            new DefaultIdentity(userID, Identity.USER) :
            new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER);
      }

      return identity;
   }

   private List<IdentityID> getUsers(Principal principal) {
      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

      return Arrays.stream(securityProvider.getUsers())
         .filter(u -> Tool.equals(u.getOrgID(), currentOrgID))
         .filter(u -> checkUserPermission(u, principal))
         .sorted()
         .collect(Collectors.toList());
   }

   private List<IdentityID> getOrgUsers(Principal principal, String orgID) {
      return Arrays.stream(securityProvider.getUsers())
         .filter(u -> u.orgID.equals(orgID))
         .filter(u -> checkUserPermission(u, principal))
         .sorted()
         .collect(Collectors.toList());
   }

   private List<String> getOrganizations(Principal principal) {
      return Arrays.stream(securityProvider.getOrganizationIDs())
         .filter(o -> checkUserPermission(new IdentityID(securityProvider.getOrgNameFromID(o) , o), principal))
         .sorted()
         .collect(Collectors.toList());
   }

   private Comparator<ContentRepositoryTreeNode> getNodeComparator() {
      return (a, b) -> "Ascending".equalsIgnoreCase(SreeEnv.getProperty("repository.tree.sort")) ?
         a.label().compareToIgnoreCase(b.label()) : b.label().compareToIgnoreCase(a.label());
   }

   private Comparator<ContentRepositoryTreeNode> getNodeComparator0() {
      boolean asc = "Ascending".equalsIgnoreCase(SreeEnv.getProperty("repository.tree.sort"));

      return (a, b) -> {
         int result = (b.type() & FOLDER) - (a.type() & FOLDER);

         if(result == 0) {
            result = asc ?
               a.label().compareToIgnoreCase(b.label()) : b.label().compareToIgnoreCase(a.label());
         }

         return result;
      };
   }

   private boolean checkUserPermission(IdentityID user, Principal principal) {
      if(principal == null) {
         return false;
      }

      if(user.equals(IdentityID.getIdentityIDFromKey(principal.getName()))) {
         return true;
      }

      return securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, user.convertToKey(), ResourceAction.ADMIN);
   }

   private boolean shouldShowEntryAlias(RepositoryEntry entry) {
      String name = entry.getName();
      return SUtil.MY_DASHBOARD.equals(name) || Tool.MY_DASHBOARD.equals(name) ||
         RepositoryEntry.USERS_FOLDER.equals(name) || RepositoryEntry.WORKSHEETS_FOLDER.equals(name);
   }

//   private boolean checkOrgID(String user, String orgID) {
//      String org = securityProvider.getUser(user).getOrganization();
//      String userOrgID = securityProvider.getOrganization(org).getId();
//      return orgID.equals(userOrgID);
//   }

   private final SecurityProvider securityProvider;
   private final RepletRegistryManager registryManager;
   private final XRepository repository;
   private final ResourcePermissionService permissionService;

   private final ScheduleTaskFolderService scheduleTaskFolderService;
   private final Comparator<ContentRepositoryTreeNode> nodeComparator = getNodeComparator();

   public static final String RECYCLE_BIN_FOLDER = "Recycle Bin";
   private static final Logger LOG = LoggerFactory.getLogger(ContentRepositoryTreeService.class);

   private static final class UserNodes {
      UserNodes(IdentityID user) {
         this.user = user;
      }

      final IdentityID user;
      final List<ContentRepositoryTreeNode> recycled = new ArrayList<>();
      ContentRepositoryTreeNode reports;
      ContentRepositoryTreeNode dashboards;
      ContentRepositoryTreeNode tasks;
   }

   /**
    * Optimization to reduce the number of calls to RepletRegistry.getRegistry(). Since most calls
    * are in sequence, it is most likely that the next is the same as the previous. If that's the
    * case, reuse the previous instead of calling getRegistry() again.
    * RepletRegistry.getRegistry(user) takes about 500ms per invocation. When there are thousands
    * of users, this adds up quickly.
    */
   private static final class RegistrySupplier {
      public RepletRegistry get(IdentityID user) {
         if(!Objects.equals(user, this.user) || registry == null) {
            try {
               if(user == null) {
                  registry = RepletRegistry.getRegistry();
               }
               else {
                  registry = RepletRegistry.getRegistry(user);
               }
            }
            catch(Exception e) {
               throw new RuntimeException("Failed to get replet registry", e);
            }

            this.user = user;
         }

         return registry;
      }

      private IdentityID user;
      private RepletRegistry registry;
   }
}
