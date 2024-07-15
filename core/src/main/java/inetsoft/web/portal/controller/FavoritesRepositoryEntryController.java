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

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Tool;
import inetsoft.web.portal.model.FavoritesEntryEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Date;

/**
 * Controller that provides a REST endpoint used to modify the contents of the
 * Favorites tree.
 *
 * @since 13.1
 */
@RestController
public class FavoritesRepositoryEntryController {
   /**
    * Creates a new instance of <tt>FavoritesRepositoryEntryController</tt>.
    */
   @Autowired
   public FavoritesRepositoryEntryController(RepositoryTreeService repositoryTreeService) {
      this.repositoryTreeService = repositoryTreeService;
   }

   /**
   * Add repository entry to Favortes
   *
   * @Param FavoritesEntryEvent that contains repository entry
   **/
   @RequestMapping(value = "/api/portal/tree/favorites/add", method = RequestMethod.PUT)
   public void addEntryToFavortes(@RequestBody FavoritesEntryEvent event, Principal principal)
      throws Exception
   {
      RepositoryEntry entry = event.entry().createRepositoryEntry();
      addFavoritesUser(entry, event.confirmed(), principal);
   }

   private void addFavoritesUser(RepositoryEntry entry, boolean confirmed, Principal principal)
      throws Exception
   {
      AssetEntry assetEntry = entry.getAssetEntry();

      if(assetEntry != null) {
         addVSFavorites(assetEntry, entry, confirmed, principal);
      }
      else {
         addFolderFavorites(entry, confirmed, principal);
      }
   }

   /**
   * delete repository entry to Favortes
   *
   * @Param FavoritesEntryEvent that contains repository entry
   *
   **/
   @RequestMapping(value = "/api/portal/tree/favorites/remove", method = RequestMethod.PUT)
   public void removeEntryFromFavorites(@RequestBody FavoritesEntryEvent event, Principal principal)
      throws Exception
   {
      RepositoryEntry entry = event.entry().createRepositoryEntry();
      deleteFavoritesUser(entry, event.confirmed(), principal);
   }

   private void deleteFavoritesUser(RepositoryEntry entry, boolean confirmed, Principal principal)
      throws Exception
   {
      AssetEntry assetEntry = entry.getAssetEntry();

      if(assetEntry != null) {
         deleteVSFavorites(assetEntry, entry, confirmed, principal);
      }
      else {
         deleteFolderFavorites(entry, confirmed, principal);
      }
   }

   /**
    * add folder to favorite
    */
   private void addFolderFavorites(RepositoryEntry entry, boolean confirmed, Principal principal)
      throws Exception
   {
      RepletRegistry registry = repositoryTreeService.getRegistry(entry.getPath(), principal);
      boolean registryChanged = false;

      if(entry.isFolder()) {
         if(registry != null) {
            registry.addFolderFavoritesUser(entry.getPath(), principal.getName());
            registryChanged = true;
         }

         RepositoryEntry[] entries = repositoryTreeService.getAllEntries(
            entry, ResourceAction.READ, RepositoryEntry.ALL, principal);

         if(entries == null) {
            return;
         }

         for(RepositoryEntry entry0: entries) {
            //add principals for entry's all child
            addFavoritesUser(entry0, confirmed, principal);
         }
      }

      if(registry != null && registryChanged) {
         registry.save();
      }
   }

   /**
    * add VS
    */
   private void addVSFavorites(AssetEntry assetEntry, RepositoryEntry entry,
                               boolean confirmed, Principal principal)
      throws Exception
   {
      AssetEntry nentry = getAssetEntry(assetEntry, entry);
      AssetRepository rep = AssetUtil.getAssetRepository(false);
      nentry.addFavoritesUser(principal.getName());
      rep.changeSheet(assetEntry, nentry, principal, confirmed, false, true);
   }

   /**
    * delete VS
    */
   private void deleteVSFavorites(AssetEntry assetEntry, RepositoryEntry entry, boolean confirmed,
                                  Principal principal)
      throws Exception
   {
      AssetEntry nentry = getAssetEntry(assetEntry, entry);
      AssetRepository rep = AssetUtil.getAssetRepository(false);
      nentry.deleteFavoritesUser(principal.getName());
      rep.changeSheet(assetEntry, nentry, principal, confirmed, false, true);
   }

   /**
    * delete folder from favorite
    */
   private void deleteFolderFavorites(RepositoryEntry entry, boolean confirmed, Principal principal)
      throws Exception
   {
      RepletRegistry registry = repositoryTreeService.getRegistry(entry.getPath(), principal);

      if(entry.isFolder()) {
         if(registry != null) {
            registry.deleteFolderFavoritesUser(entry.getPath(), principal.getName());
         }

         RepositoryEntry[] entries = repositoryTreeService.getAllEntries(
            entry, ResourceAction.READ, RepositoryEntry.ALL, principal);

         if(entries == null) {
            return;
         }

         for(RepositoryEntry entry0: entries) {
            deleteFavoritesUser(entry0, confirmed, principal);
         }
      }

      if(registry != null) {
         registry.save();
      }
   }

   private AssetEntry getAssetEntry(AssetEntry assetEntry, RepositoryEntry entry) {
      boolean reAlias = assetEntry.getAlias() != null;
      String name = assetEntry.getScope() == AssetRepository.USER_SCOPE ?
         entry.getPath().substring(Tool.MY_DASHBOARD.length() + 1) : entry.getPath();
      AssetEntry nentry = new AssetEntry(assetEntry.getScope(), assetEntry.getType(),
                                         name, assetEntry.getUser());
      nentry.copyProperties(assetEntry);

      if(reAlias) {
         assetEntry.setAlias(assetEntry.getProperty("localStr"));
         nentry = assetEntry;
      }

      return nentry;
   }

   private final RepositoryTreeService repositoryTreeService;
}