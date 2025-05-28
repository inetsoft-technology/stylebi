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
package inetsoft.web.portal.controller;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.sree.*;
import inetsoft.sree.internal.AnalyticEngine;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.model.RepositoryEntryModel;
import inetsoft.web.portal.model.RepositoryEntryModelFactoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;

/**
 * Controller that provides a REST endpoint used to get the contents of the
 * repository tree.
 *
 * @since 12.3
 */
@RestController
public class RepositoryTreeSearchController {
   /**
    * Creates a new instance of <tt>RepositoryTreeSearchController</tt>.
    *
    * @param analyticRepository the analytic repository.
    */
   @Autowired
   public RepositoryTreeSearchController(AnalyticRepository analyticRepository,
      RepositoryTreeService repositoryTreeService,
      RepositoryEntryModelFactoryService repositoryEntryModelFactoryService)
   {
      this.analyticRepository = analyticRepository;
      this.repositoryTreeService = repositoryTreeService;
      this.repositoryEntryModelFactoryService = repositoryEntryModelFactoryService;
   }

   /**
    * Gets results for provided search query.
    *
    * @param searchString the term being searched for.
    * @param principal    the user
    *
    * @return the tree root.
    */
   @RequestMapping(
      value = "/api/portal/tree/search",
      method = RequestMethod.GET
   )
   public TreeNodeModel getSearchResults(
      @RequestParam(value = "searchString", defaultValue = "") String searchString,
      @RequestParam(value = "favoritesMode", defaultValue = "false") boolean favoritesMode,
      Principal principal)
      throws Exception
   {
      searchString = searchString.toLowerCase();
      SearchResultFolder rootFolder =
         getRootFolder(searchString, favoritesMode, false, principal);
      TreeNodeModel node = null;

      if(!isHostOrgGlobalRepoVisible(principal)) {
         node = convertToTreeNode(rootFolder, principal, searchString);
      }
      else {
         SearchResultFolder globalRootFolder =
            getRootFolder(searchString, favoritesMode, true, principal);
         SearchResultFolder resultFolder = new SearchResultFolder("");

         if(!rootFolder.isEmpty()) {
            resultFolder.addFolder(rootFolder);
         }

         if(!globalRootFolder.isEmpty()) {
            resultFolder.addFolder(globalRootFolder);
         }

         node = convertToTreeNode(resultFolder, principal, searchString);
      }

      return node;
   }

   private SearchResultFolder getRootFolder(String searchString, boolean favoritesMode,
                                            boolean hostOrgGlobalRepo, Principal principal)
      throws RemoteException
   {
      Map<String, RepositoryEntry> searchResults = new LinkedHashMap<>();
      searchRepositoryFolder(new DefaultFolderEntry("/"), principal,
                             searchString, searchResults, favoritesMode, hostOrgGlobalRepo, hostOrgGlobalRepo);

      // create a folder structure for the results
      String fPath = !hostOrgGlobalRepo ? "/" : OrganizationManager.getGlobalDefOrgFolderName();
      SearchResultFolder rootFolder = new SearchResultFolder(fPath);
      List<String> list = new ArrayList<>(searchResults.keySet());

      for(String path : list) {
         addSearchResult(searchResults.get(path), rootFolder, hostOrgGlobalRepo);
      }

      return sortSearchResult(rootFolder, searchString);
   }

   private SearchResultFolder sortSearchResult(SearchResultFolder rootFolder, String searchString) {
      if(rootFolder.folders.size() == 1) {
         sortSearchResult(rootFolder.folders.get(0), searchString);
      }
      else {
         rootFolder.folders.sort((a,  b) -> {
            sortSearchResult(a, searchString);

            return new SearchComparator.StringComparator(searchString).compare(a.path, b.path);
         });
      }

      rootFolder.files.sort((a,  b) -> {
         String apth = a.getPath().startsWith(a.getParentPath())
            ? a.getPath().substring(a.getParentPath().length() + 1) : a.getPath();
         String bpth = b.getPath().startsWith(b.getParentPath())
            ? b.getPath().substring(b.getParentPath().length() + 1) : b.getPath();

         return new SearchComparator.StringComparator(searchString).compare(apth, bpth);
      });

      return rootFolder;
   }

   private void searchRepositoryFolder(RepositoryEntry parentEntry, Principal principal,
                                       String searchString, Map<String, RepositoryEntry> searchResults,
                                       boolean favoritesMode, boolean hostOrgGlobalRepo, boolean isDefaultOrgAsset)
      throws RemoteException
   {
      RepositoryEntry[] entries = null;

      if(hostOrgGlobalRepo) {
         entries = ((AnalyticEngine) analyticRepository).getDefaultOrgRepositoryEntries(principal);
      }
      else {
         entries = VSEventUtil.getRepositoryEntries(
            (AnalyticEngine) analyticRepository, principal, ResourceAction.READ,
            RepositoryEntry.ALL, "", new RepositoryEntry[]{parentEntry}, isDefaultOrgAsset);
      }

      for(RepositoryEntry entry : entries) {
         if(isHiddenEntry(entry, favoritesMode, principal)) {
            continue;
         }

         boolean match = entry.getPath().toLowerCase().contains(searchString) ||
            getEntryLabel(entry).toLowerCase().contains(searchString);
         boolean exactMatch = entry.getName().toLowerCase().equals(searchString) ||
            getEntryLabel(entry).toLowerCase().equals(searchString);

         if(entry.isFolder()) {
            int oldMatchCount = searchResults.size();

            if(exactMatch) {
               // add all the files from this folder
               RepositoryEntry[] childEntries = VSEventUtil.getRepositoryEntries(
                  (AnalyticEngine) analyticRepository, principal, ResourceAction.READ,
                  RepositoryEntry.ALL & (~RepositoryEntry.FOLDER),
                  "", new RepositoryEntry[]{entry}, isDefaultOrgAsset);

               Arrays.stream(childEntries)
                  .filter(childEntry -> {
                     if(isHiddenEntry(childEntry, favoritesMode, principal)) {
                        return false;
                     }

                     boolean exist = searchResults.containsKey(childEntry.getPath());
                     return !favoritesMode ? !exist :
                        !exist && repositoryTreeService.isFavoritesEntry(childEntry, principal);
                  })
                  .forEach(childEntry -> searchResults.put(childEntry.getPath(), childEntry));
            }

            this.searchRepositoryFolder(entry, principal, searchString,
               searchResults, favoritesMode, false, isDefaultOrgAsset);

            if(match &&
               oldMatchCount == searchResults.size() && !searchResults.containsKey(entry.getPath()))
            {
               searchResults.put(entry.getPath(), entry);
            }
         }
         else {
            if(match && !searchResults.containsKey(entry.getPath())) {
               searchResults.put(entry.getPath(), entry);
            }
         }
      }
   }

   private boolean isMatchedEntry(String searchString, RepositoryEntry entry) {
      return entry.getName().toLowerCase().contains(searchString) ||
         getEntryLabel(entry).toLowerCase().contains(searchString);
   }

   private boolean isHiddenEntry(RepositoryEntry entry, boolean favoritesMode, Principal principal)
   {
      return (entry instanceof RepletEntry && !((RepletEntry) entry).isVisible()) ||
         (entry instanceof ViewsheetEntry && !((ViewsheetEntry) entry).isOnReport()) ||
         favoritesMode && !repositoryTreeService.isFavoritesEntry(entry, principal);
   }

   /**
    * Convert result to a tree node.
    */
   private TreeNodeModel convertToTreeNode(SearchResultFolder folder,
                                           Principal principal, String searchString)
      throws Exception
   {
      List<TreeNodeModel> folderNodes = new ArrayList<>();
      List<TreeNodeModel> fileNodes = new ArrayList<>();

      for(SearchResultFolder childFolder : folder.folders) {
         folderNodes.add(convertToTreeNode(childFolder, principal,
                                           searchString.toLowerCase()));
      }

      for(RepositoryEntry entry : folder.files) {
         String label = getEntryLabel(entry);

         // Bug #58885 - partial folder matches should show contents. The old behavior doesn't make
         // sense for the portal.
         boolean folderPartialMatch = folder.path.toLowerCase().contains(searchString);

         if(!(label.toLowerCase().contains(searchString) || isMatchedEntry(searchString, entry))
            && !folderPartialMatch)
         {
            continue;
         }

         entry.setFavoritesUser(repositoryTreeService.hasFavoritesUser(entry, principal));
         RepositoryEntryModel entryModel = repositoryEntryModelFactoryService.createModel(entry);
         entryModel.setOp(repositoryTreeService.getSupportedOperations(entry, principal));
         IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

         String dragName = null;

         RepletRegistry registry = SUtil.isMyReport(folder.path) ?
            RepletRegistry.getRegistry(pId) :
            RepletRegistry.getRegistry();

         dragName = "RepositoryEntry";

         TreeNodeModel node = TreeNodeModel.builder()
            .label(label)
            .data(entryModel)
            .leaf(true)
            .dragName(dragName)
            .build();

         fileNodes.add(node);
      }

      String label;
      RepletFolderEntry folderEntry = new RepletFolderEntry(folder.path);
      folderEntry.setDefaultOrgAsset(folder.isDefaultOrgAsset());
      RepositoryEntryModel folderModel = repositoryEntryModelFactoryService.createModel(folderEntry);
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      boolean hostOrgGlobalRepoVisible = isHostOrgGlobalRepoVisible(principal);

      if(!hostOrgGlobalRepoVisible && "/".equals(folder.path) ||
         hostOrgGlobalRepoVisible && "".equals(folder.path))
      {
         label = Catalog.getCatalog().getString("Search Results");
      }
      else if(hostOrgGlobalRepoVisible && "/".equals(folder.path)) {
         label = Catalog.getCatalog().getString("Repository");
      }
      else if(hostOrgGlobalRepoVisible &&
         OrganizationManager.getGlobalDefOrgFolderName().equals(folder.path))
      {
         label = Catalog.getCatalog().getString(OrganizationManager.getGlobalDefOrgFolderName());
      }
      else {
         RepletRegistry registry = SUtil.isMyReport(folder.path) ?
            RepletRegistry.getRegistry(pId) :
            RepletRegistry.getRegistry();

         label = registry.getFolderAlias(folder.path);

         if(label == null) {
            label = folder.path.substring(folder.path.lastIndexOf("/") + 1);
         }

         folderModel.setFavoritesUser(repositoryTreeService.isFavoritesEntry(folderEntry, principal));
         folderModel.setOp(repositoryTreeService.getSupportedOperations(folderEntry, principal));
      }

      return TreeNodeModel.builder()
         .label(label)
         .data(folderModel)
         .addAllChildren(folderNodes)
         .addAllChildren(fileNodes)
         .leaf(false)
         .expanded(true)
         .build();
   }

   /**
    * Get the suffix of the archive entry.
    *
    * @return the suffix of the archive entry.
    */
   public String getSuffix(String name) {
      int index = name.lastIndexOf(".");
      return name.substring(index + 1);
   }

   private void addSearchResult(RepositoryEntry entry, SearchResultFolder folder, boolean hostOrgGlobalRepo) {
      String filePath = entry.getPath();
      String folderPath = entry.getParentPath();

      if(Tool.equals(folderPath, folder.path) ||
         Tool.equals("/", folderPath) &&
            Tool.equals(OrganizationManager.getGlobalDefOrgFolderName(), folder.path))
      {
         folder.files.add(entry);
      }
      else {
         int fromIndex = OrganizationManager.getGlobalDefOrgFolderName().equals(folder.path) ||
            folder.path == null ? 0 : folder.path.length() + 1;
         int nextSlash = filePath.indexOf("/", fromIndex);
         String nextPath = filePath.substring(0, nextSlash);
         SearchResultFolder nextFolder = folder.getFolder(nextPath);

         if(nextFolder == null) {
            nextFolder = new SearchResultFolder(nextPath);
            nextFolder.setDefaultOrgAsset(hostOrgGlobalRepo);
            folder.folders.add(nextFolder);
         }

         addSearchResult(entry, nextFolder, hostOrgGlobalRepo);
      }
   }

   private String getEntryLabel(RepositoryEntry entry) {
      String label = null;

      if(entry instanceof RepletEntry) {
         label = ((RepletEntry) entry).getAlias();
      }
      else if(entry instanceof DefaultFolderEntry) {
         label = ((DefaultFolderEntry) entry).getAlias();
      }
      else if(entry instanceof ViewsheetEntry) {
         label = ((ViewsheetEntry) entry).getAlias();
      }

      if(label == null || label.isEmpty()) {
         label = entry.toString();
      }

      return label;
   }

   private boolean isHostOrgGlobalRepoVisible(Principal principal) {
      String orgId = ((XPrincipal) principal).getCurrentOrgId();
      return SUtil.isDefaultVSGloballyVisible(principal) && !orgId.equals(Organization.getDefaultOrganizationID());
   }

   private final AnalyticRepository analyticRepository;
   private final RepositoryTreeService repositoryTreeService;
   private final RepositoryEntryModelFactoryService repositoryEntryModelFactoryService;
   private static final Logger LOG =
      LoggerFactory.getLogger(RepositoryTreeSearchController.class);

   private static final class SearchResultFolder {
      public SearchResultFolder(String path) {
         this.path = path;
         this.folders = new ArrayList<>();
         this.files = new ArrayList<>();
      }

      private SearchResultFolder getFolder(String path) {
         for(SearchResultFolder folder : folders) {
            if(Tool.equals(folder.path, path)) {
               return folder;
            }
         }

         return null;
      }

      private void addFolder(SearchResultFolder folder) {
         if(folders == null) {
            folders = new ArrayList<>();
         }

         folders.add(folder);
      }

      private boolean isEmpty() {
         return folders.isEmpty() && files.isEmpty();
      }

      public boolean isDefaultOrgAsset() {
         return defaultOrgAsset;
      }

      public void setDefaultOrgAsset(boolean defaultOrgAsset) {
         this.defaultOrgAsset = defaultOrgAsset;
      }

      private String path;
      private boolean defaultOrgAsset = false;
      private List<SearchResultFolder> folders;
      private List<RepositoryEntry> files;
   }
}
