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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.web.portal.model.FavoritesEntryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

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
      addFavoritesUser(entry, event.confirmed(), principal, false);
   }

   /**
    * @param child <tt>true</tt> if the server listed the entry below a folder the user asked
    *              for, <tt>false</tt> if it came from the request.
    */
   private void addFavoritesUser(RepositoryEntry entry, boolean confirmed, Principal principal,
                                 boolean child)
      throws Exception
   {
      AssetEntry assetEntry = entry.getAssetEntry();

      if(assetEntry != null) {
         changeVSFavorites(assetEntry, confirmed, principal, child, true);
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
      deleteFavoritesUser(entry, event.confirmed(), principal, false);
   }

   /**
    * @param child <tt>true</tt> if the server listed the entry below a folder the user asked
    *              for, <tt>false</tt> if it came from the request.
    */
   private void deleteFavoritesUser(RepositoryEntry entry, boolean confirmed, Principal principal,
                                    boolean child)
      throws Exception
   {
      AssetEntry assetEntry = entry.getAssetEntry();

      if(assetEntry != null) {
         changeVSFavorites(assetEntry, confirmed, principal, child, false);
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
         checkFolderReadPermission(entry, principal);

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
            addFavoritesUser(entry0, confirmed, principal, true);
         }
      }

      if(registry != null && registryChanged) {
         registry.save();
      }
   }

   /**
    * delete folder from favorite
    */
   private void deleteFolderFavorites(RepositoryEntry entry, boolean confirmed, Principal principal)
      throws Exception
   {
      RepletRegistry registry = repositoryTreeService.getRegistry(entry.getPath(), principal);

      if(entry.isFolder()) {
         checkFolderReadPermission(entry, principal);

         if(registry != null) {
            registry.deleteFolderFavoritesUser(entry.getPath(), principal.getName());
         }

         RepositoryEntry[] entries = repositoryTreeService.getAllEntries(
            entry, ResourceAction.READ, RepositoryEntry.ALL, principal);

         if(entries == null) {
            return;
         }

         for(RepositoryEntry entry0: entries) {
            deleteFavoritesUser(entry0, confirmed, principal, true);
         }
      }

      if(registry != null) {
         registry.save();
      }
   }

   /**
    * Adds or removes the user's favorite on a viewsheet or viewsheet snapshot.
    *
    * The entry in the request, or in a folder listing, only identifies the sheet. What is
    * written back is the stored entry with only this user added to or removed from its
    * favorites, so the sheet's path, scope, alias and properties, and the other users'
    * favorites, never come from the client (Bug #78491). The change skips the WRITE and DELETE
    * checks so that a user who can only read a viewsheet can mark it. That is safe only because
    * the identifier never changes, and READ is checked here instead.
    *
    * @param requested the entry that identifies the sheet.
    * @param child     <tt>true</tt> to skip a sheet that no longer exists, as one listed below
    *                  a folder may have been removed since; <tt>false</tt> to report it.
    * @param add       <tt>true</tt> to add the favorite, <tt>false</tt> to remove it.
    */
   private void changeVSFavorites(AssetEntry requested, boolean confirmed, Principal principal,
                                  boolean child, boolean add)
      throws Exception
   {
      AssetRepository rep = AssetUtil.getAssetRepository(false);
      // getAssetEntry returns the given object itself for some scopes and types, so only a
      // sheet, which it reads from storage, is accepted. A sheet of another organization, such
      // as a host organization viewsheet shared read-only, is not offered for favorites in the
      // portal and is not changed from this one.
      String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);
      AssetEntry stored = (requested.isViewsheet() || requested.isVSSnapshot()) &&
         orgID.equalsIgnoreCase(requested.getOrgID()) ? rep.getAssetEntry(requested) : null;

      if(stored == null) {
         if(child) {
            LOG.debug("Skipping a missing sheet below a favorites folder: {}", requested);
            return;
         }

         throw new MessageException(Catalog.getCatalog().getString(
            "common.sheetNotFound", requested.getPath()), LogLevel.INFO, false);
      }

      rep.checkAssetPermission(principal, stored, ResourceAction.READ);
      AssetEntry oentry = AssetEntry.createAssetEntry(stored.toIdentifier());

      if(add) {
         stored.addFavoritesUser(principal.getName());
      }
      else {
         stored.deleteFavoritesUser(principal.getName());
      }

      rep.changeSheet(oentry, stored, principal, confirmed, false, true);
   }

   /**
    * Checks that the user can read a portal folder before the favorites on it and on the
    * sheets below it are changed. The folder is checked as the asset engine checks a repository
    * folder of the user's organization; one under My Dashboards belongs to this user.
    */
   private void checkFolderReadPermission(RepositoryEntry entry, Principal principal)
      throws Exception
   {
      String path = entry.getPath();
      boolean myReport = SUtil.isMyReport(path);
      String folderPath = !myReport ? path :
         Tool.MY_DASHBOARD.equals(path) ? "/" : path.substring(Tool.MY_DASHBOARD.length() + 1);
      AssetEntry folder = new AssetEntry(
         myReport ? AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.REPOSITORY_FOLDER, folderPath,
         myReport ? IdentityID.getIdentityIDFromKey(principal.getName()) : null,
         OrganizationManager.getInstance().getCurrentOrgID(principal));
      AssetUtil.getAssetRepository(false).checkAssetPermission(
         principal, folder, ResourceAction.READ);
   }

   private final RepositoryTreeService repositoryTreeService;
   private static final Logger LOG =
      LoggerFactory.getLogger(FavoritesRepositoryEntryController.class);
}