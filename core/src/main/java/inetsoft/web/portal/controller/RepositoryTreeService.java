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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Repository tree service used for generate repository root node
 * and relevant related methods.
 *
 * @since 12.3
 */
@Service
public class RepositoryTreeService {
   /**
    * Creates a new instance of <tt>RepositoryTreeService</tt>.
    *
    * @param analyticRepository the analytic component of the report server
    * @param repositoryEntryModelFactoryService the repository entry model factory.
    */
   @Autowired
   public RepositoryTreeService(
      AnalyticRepository analyticRepository,
      SecurityProvider securityProvider,
      RepositoryEntryModelFactoryService repositoryEntryModelFactoryService,
      RepletRegistryManager repletRegistryManager)
   {
      this.analyticRepository = analyticRepository;
      this.securityProvider = securityProvider;
      this.repositoryEntryModelFactoryService = repositoryEntryModelFactoryService;
      this.repletRegistryManager = repletRegistryManager;
   }

   /**
    * Get the repository folder node in composer.
    * @param isOnPortal true if get nodes for portal, else not.
    */
   public TreeNodeModel getRootFolder(String path, ResourceAction action, int selector,
                                      String detailType, Principal principal,
                                      boolean isOnPortal)
      throws Exception
   {
      try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
         return getRootFolder0(path, action, selector, detailType, principal, isOnPortal);
      }
   }

   private TreeNodeModel getRootFolder0(String path, ResourceAction action, int selector,
                                        String detailType, Principal principal,
                                        boolean isOnPortal)
      throws Exception
   {
      RepositoryEntry parentEntry = new RepositoryEntry(path, RepositoryEntry.FOLDER);
      RepositoryEntry[] entries = VSEventUtil.getRepositoryEntries(
         analyticRepository.unwrap(AnalyticEngine.class), principal, action, selector, detailType,
         null, false);
      SupportedOperationsContext ctx = new SupportedOperationsContext(principal);
      List<TreeNodeModel> folderNodes = new ArrayList<>();
      List<TreeNodeModel> fileNodes = new ArrayList<>();

      for(RepositoryEntry entry : entries) {
         if(entry instanceof RepletEntry && !((RepletEntry) entry).isVisible() && !isOnPortal ||
            isOnPortal && entry instanceof ViewsheetEntry && !((ViewsheetEntry) entry).isOnReport())
         {
            continue;
         }

         if(isOnPortal && "Built-in Admin Reports".equals(entry.getPath())) {
            continue;
         }

         RepositoryEntryModel entryModel = repositoryEntryModelFactoryService
            .createModel(entry);
         entryModel.setOp(getSupportedOperations(entry, principal, ctx));

         TreeNodeModel node = TreeNodeModel.builder()
            .label(getEntryLabel(entry, principal))
            .data(entryModel)
            .leaf(isLeafEntry(entry))
            .tooltip(getEntryTooltip(entry))
            .dragName("RepositoryEntry")
            .build();

         if(entry.isFolder()) {
            folderNodes.add(node);
         }
         else {
            fileNodes.add(node);
         }
      }

      RepositoryEntryModel parentEntryModel = repositoryEntryModelFactoryService
         .createModel(parentEntry);
      parentEntryModel.setOp(getSupportedOperations(parentEntry, principal));

      return TreeNodeModel.builder()
         .label(getEntryLabel(parentEntry, principal))
         .data(parentEntryModel)
         .addAllChildren(folderNodes)
         .addAllChildren(fileNodes)
         .build();
   }

   public RepositoryEntry[] getAllEntries(RepositoryEntry entry, ResourceAction action,
                                          int selector, Principal principal)
      throws Exception
   {
      RepositoryEntry[] entries;

      if(!entry.isFolder()) {
         return null;
      }

      AnalyticEngine engine = analyticRepository.unwrap(AnalyticEngine.class);
      entries = engine.getRepositoryEntries(entry.getPath(), principal, action, selector, false);

      return entries;
   }

   /**
    * Get replet registry.
    * @param name the specified folder or replet name.
    * @param principal the specified user.
    * @return replet registry contains the specified folder or replet name.
    */
   public RepletRegistry getRegistry(String name, Principal principal)
      throws Exception
   {
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      return SUtil.isMyReport(name) && !Tool.MY_DASHBOARD.equals(name) ?
         repletRegistryManager.getRegistry(pId) :
         repletRegistryManager.getRegistry();
   }

   public final EnumSet<RepositoryTreeAction> getSupportedOperations(RepositoryEntry entry,
                                                                     Principal principal)
      throws Exception
   {
      return getSupportedOperations(entry, principal, null);
   }

   /**
    * Gets the operations the principal may perform on an entry. The two checks that do not depend
    * on the entry are answered once per {@code ctx} instead of once per entry.
    *
    * @param ctx the context shared by one listing for one principal, or {@code null}.
    */
   public final EnumSet<RepositoryTreeAction> getSupportedOperations(
      RepositoryEntry entry, Principal principal, SupportedOperationsContext ctx)
      throws Exception
   {
      if(ctx != null && ctx.principal != principal) {
         ctx = null;
      }

      EnumSet<RepositoryTreeAction> op = EnumSet.noneOf(RepositoryTreeAction.class);

      boolean hasDelete = analyticRepository.checkPermission(
         principal, ResourceType.REPORT, entry.getPath(), ResourceAction.DELETE);
      boolean hasWrite = analyticRepository.checkPermission(
         principal, ResourceType.REPORT, entry.getPath(), ResourceAction.WRITE);

      // user has delete permission in My Reports
      if(entry.getType() == RepositoryEntry.VIEWSHEET) {
         if(entry.isMyReport()) {
            hasDelete = true;
            hasWrite = true;
         }

         boolean canMaterialize = ctx != null ? ctx.canMaterialize() :
            securityProvider.checkPermission(
               principal, ResourceType.MATERIALIZATION, "*", ResourceAction.ACCESS);

         if(hasWrite && canMaterialize) {
            op.add(RepositoryTreeAction.MATERIALIZE);
         }

         boolean composer = ctx != null ? ctx.canUseComposer() :
            securityProvider.checkPermission(
               principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS);

         if(composer) {
            op.add(RepositoryTreeAction.EDIT);
         }
      }

      if(entry.supportsOperation(RepositoryEntry.RENAME_OPERATION)
         && hasWrite && hasDelete)
      {
         op.add(RepositoryTreeAction.RENAME);
      }

      if(entry.supportsOperation(RepositoryEntry.CHANGE_FOLDER_OPERATION)
         && hasWrite && hasDelete)
      {
         op.add(RepositoryTreeAction.CHANGE_FOLDER);
      }

      if(entry.supportsOperation(RepositoryEntry.REMOVE_OPERATION) && hasDelete) {
         op.add(RepositoryTreeAction.DELETE);
      }

      if(entry.isFolder() && hasWrite) {
         op.add(RepositoryTreeAction.NEW_FOLDER);
      }

      return op;
   }

   public final String getEntryLabel(RepositoryEntry entry, Principal principal) {
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


      // some built-in labels should be localizable using main catalog too
      label = entry.isRoot() || entry.getPath().equals(Tool.MY_DASHBOARD) ||
         entry.getPath().startsWith("Built-in Admin Reports") ?
         Catalog.getCatalog(principal).getString(label) :
         Catalog.getCatalog(principal, Catalog.REPORT).getString(label);

      return label;
   }

   public final String getEntryTooltip(RepositoryEntry entry) {
      String tooltip = null;

      if(entry instanceof RepletEntry) {
         tooltip = ((RepletEntry) entry).getDescription();

         if(tooltip != null && tooltip.length() > 0) {
            return tooltip.replace('\n', ' ');
         }
      }
      else if(entry instanceof DefaultFolderEntry) {
         tooltip = ((DefaultFolderEntry) entry).getDescription();
      }
      else if(entry instanceof ViewsheetEntry) {
         tooltip = ((ViewsheetEntry) entry).getDescription();
      }

      return tooltip;
   }

   public final boolean isLeafEntry(RepositoryEntry entry) {
      return !entry.isFolder();
   }

   /**
    * Return if the target entry is in the user favorites.
    */
   public boolean isFavoritesEntry(RepositoryEntry entry, Principal principal) {
      if(hasFavoritesUser(entry, principal)) {
         return true;
      }

      if(!entry.isFolder()) {
         return false;
      }

      try{
         RepositoryEntry[] entries = getAllEntries(
            entry, ResourceAction.READ, RepositoryEntry.ALL, principal);

         for(RepositoryEntry entry0 : entries) {
            if(isFavoritesEntry(entry0, principal)) {
               return true;
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to get entries array", ex);
      }

      return false;
   }

   /**
    * Return if the target entry is a favorite entry for any user.
    */
   public boolean hasFavoritesUser(RepositoryEntry entry, Principal principal) {
      AssetEntry assetEntry = entry.getAssetEntry();
      String favoritesUser = "";

      try{
         if(assetEntry == null) {
            RepletRegistry registry = getRegistry(entry.getPath(), principal);

            if(entry.isFolder()) {
               favoritesUser = registry.getFolderFavoritesUser(entry.getPath());
            }
         }
         else {
            favoritesUser = assetEntry.getFavoritesUser();
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to get Favorites info", ex);
      }

      return favoritesUser.contains(principal.getName());
   }

   private final AnalyticRepository analyticRepository;
   private final SecurityProvider securityProvider;
   private final RepositoryEntryModelFactoryService repositoryEntryModelFactoryService;
   private final RepletRegistryManager repletRegistryManager;
   private static final Logger LOG = LoggerFactory.getLogger(RepositoryTreeController.class);

   /**
    * The answers to the checks of {@code getSupportedOperations} that do not depend on the entry,
    * for one principal. Create one per listing and pass it for every entry of that listing; each
    * check runs at most once, and only when a viewsheet entry needs it.
    */
   public final class SupportedOperationsContext {
      public SupportedOperationsContext(Principal principal) {
         this.principal = principal;
      }

      private boolean canMaterialize() {
         if(canMaterialize == null) {
            canMaterialize = securityProvider.checkPermission(
               principal, ResourceType.MATERIALIZATION, "*", ResourceAction.ACCESS);
         }

         return canMaterialize;
      }

      private boolean canUseComposer() {
         if(canUseComposer == null) {
            canUseComposer = securityProvider.checkPermission(
               principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS);
         }

         return canUseComposer;
      }

      private final Principal principal;
      private Boolean canMaterialize;
      private Boolean canUseComposer;
   }
}
