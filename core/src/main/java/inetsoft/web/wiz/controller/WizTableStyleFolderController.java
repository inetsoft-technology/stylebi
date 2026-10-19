/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.controller;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AbstractAssetEngine;
import inetsoft.util.Tool;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Table Style folder management (list, create, rename/move, delete) under wiz's JWT-authenticated
 * {@code /api/wiz} namespace -- the counterpart of the Composer asset tree's Add Folder, Rename,
 * Change (move) and Remove for a table style folder, whose controllers are session-cookie + CSRF
 * protected. A folder path is a plain {@code "/"}-joined string here, converted to
 * {@code LibManager}'s {@code "~"}-joined internal form at the edge (see
 * {@link WizTableStyleController}).
 *
 * <p>Folders are explicit persisted library entries and do not structurally contain styles, so a
 * rename or move rewrites every style and subfolder name under it
 * ({@link AssetEventUtil#renameTableStyleFolder}); a move is the same call with a new parent.
 * The library does not check for an existing destination (it overwrites), and it throws
 * {@code SecurityException} mid-walk, so every duplicate/ancestor/permission check runs over the
 * whole subtree before anything is mutated. Library changes are in memory until {@code save()};
 * on a failure the controller never saves.
 *
 * <p>Limitation shared with the Composer: the library lists only what the caller can READ, so a
 * subfolder or style the caller cannot read is invisible to the counts, the permission pre-check
 * and the recursive rename/delete.
 */
@RestController
@RequestMapping("/api/wiz/v1/table-style-folder")
public class WizTableStyleFolderController {
   public WizTableStyleFolderController(LibManagerProvider libManagerProvider,
                                        TableStyleService tableStyleService,
                                        SecurityEngine securityEngine)
   {
      this.libManagerProvider = libManagerProvider;
      this.tableStyleService = tableStyleService;
      this.securityEngine = securityEngine;
   }

   /**
    * @param path           the display path, e.g. "User Defined/Sales".
    * @param parent         the display path of the parent folder, "" for the library root.
    * @param styleCount     table styles directly in the folder that the caller can read.
    * @param subfolderCount folders directly in the folder.
    */
   public record TableStyleFolderSummary(String path, String name, String parent, int styleCount,
                                         int subfolderCount) {}

   public record CreateFolderRequest(String name, String parent) {}

   /**
    * @param path      the folder to change.
    * @param newName   the new leaf name; omit to keep it.
    * @param newParent the new parent folder ("" for the library root); omit to keep it.
    */
   public record UpdateFolderRequest(String path, String newName, String newParent) {}

   @GetMapping
   public List<TableStyleFolderSummary> list(Principal principal) {
      LibManager lib = libManagerProvider.getManager(principal);
      List<TableStyleFolderSummary> out = new ArrayList<>();
      collectFolders(lib, null, principal, out);
      out.sort(Comparator.comparing(TableStyleFolderSummary::path));
      return out;
   }

   @PostMapping
   public TableStyleFolderSummary create(@RequestBody CreateFolderRequest request,
                                         Principal principal) throws Exception
   {
      String name = requireFolderName(request.name(), "name");
      String parent = request.parent() == null ? "" : WizTableStyleController.normalizeFolder(request.parent());
      String internalParent = WizTableStyleController.toInternalFolder(parent);
      LibManager lib = libManagerProvider.getManager(principal);
      requireParentExists(lib, internalParent, parent);
      requireFolderWritePermission(principal, internalParent);

      String full = internalParent == null ? name : internalParent + LibManager.SEPARATOR + name;
      requireNoDuplicate(lib, full, name);

      lib.addTableStyleFolder(full);
      lib.save();
      return summary(lib, full, principal);
   }

   @PutMapping
   public TableStyleFolderSummary update(@RequestBody UpdateFolderRequest request,
                                         Principal principal) throws Exception
   {
      if(request.newName() == null && request.newParent() == null) {
         throw new IllegalArgumentException(
            "Give 'newName' to rename the folder and/or 'newParent' to move it.");
      }

      LibManager lib = libManagerProvider.getManager(principal);
      String oldFull = requireExistingFolder(lib, request.path());
      String oldParent = parentOf(oldFull);
      String leaf = request.newName() != null ?
         requireFolderName(request.newName(), "newName") : leafOf(oldFull);
      String newParent = oldParent;

      if(request.newParent() != null) {
         String display = WizTableStyleController.normalizeFolder(request.newParent());
         newParent = WizTableStyleController.toInternalFolder(display);
         requireParentExists(lib, newParent, display);
      }

      String newFull = newParent == null ? leaf : newParent + LibManager.SEPARATOR + leaf;

      if(newFull.equals(oldFull)) {
         return summary(lib, oldFull, principal);
      }

      if(newFull.startsWith(oldFull + LibManager.SEPARATOR)) {
         throw new IllegalArgumentException(
            "A folder cannot be moved into itself or one of its own subfolders ('" +
            WizTableStyleController.toDisplayFolder(oldFull) + "' -> '" +
            WizTableStyleController.toDisplayFolder(newFull) + "').");
      }

      requireNoDuplicate(lib, newFull, leaf);

      List<String> folders = new ArrayList<>();
      List<XTableStyle> styles = new ArrayList<>();
      collectSubtree(lib, oldFull, folders, styles);

      for(String folder : folders) {
         requireAll(principal, folder, ResourceAction.DELETE, ResourceAction.WRITE);
      }

      for(XTableStyle style : styles) {
         requireAll(principal, style.getName(), ResourceAction.DELETE, ResourceAction.WRITE);
      }

      if(!Objects.equals(newParent, oldParent)) {
         requireFolderWritePermission(principal, newParent);
      }

      AssetEventUtil.renameTableStyleFolder(oldFull, newFull, lib);
      lib.save();
      return summary(lib, newFull, principal);
   }

   /**
    * @param recursive required when the folder is not empty, because the delete removes every
    *                  table style and subfolder under it.
    */
   @DeleteMapping
   public void delete(@RequestParam("path") String path,
                      @RequestParam(value = "recursive", required = false) Boolean recursive,
                      Principal principal) throws Exception
   {
      LibManager lib = libManagerProvider.getManager(principal);
      String full = requireExistingFolder(lib, path);

      List<String> folders = new ArrayList<>();
      List<XTableStyle> styles = new ArrayList<>();
      collectSubtree(lib, full, folders, styles);

      if((folders.size() > 1 || !styles.isEmpty()) && !Boolean.TRUE.equals(recursive)) {
         throw new IllegalArgumentException(
            "Table style folder '" + WizTableStyleController.toDisplayFolder(full) +
            "' is not empty: it holds " + styles.size() + " table style(s) and " +
            (folders.size() - 1) + " subfolder(s), and deleting it removes all of them. Pass " +
            "recursive: true to delete the folder with its contents.");
      }

      for(String folder : folders) {
         requirePermission(principal, folder, ResourceAction.DELETE);
      }

      for(XTableStyle style : styles) {
         requirePermission(principal, style.getName(), ResourceAction.DELETE);
      }

      AssetEventUtil.removeStyleFolder(full, lib);
      lib.save();

      // The library removes no permission, so a later folder/style of the same name would
      // inherit the grants of every deleted one.
      for(String folder : folders) {
         AbstractAssetEngine.removePermissionBestEffort(
            securityEngine, ResourceType.TABLE_STYLE, folder);
      }

      for(XTableStyle style : styles) {
         AbstractAssetEngine.removePermissionBestEffort(
            securityEngine, ResourceType.TABLE_STYLE, style.getName());
      }
   }

   private void collectFolders(LibManager lib, String internalParent, Principal principal,
                               List<TableStyleFolderSummary> out)
   {
      for(String folder : lib.getTableStyleFolders(internalParent, true)) {
         out.add(summary(lib, folder, principal));
         collectFolders(lib, folder, principal, out);
      }
   }

   private TableStyleFolderSummary summary(LibManager lib, String internalFolder,
                                           Principal principal)
   {
      int styleCount = 0;

      for(XTableStyle style : lib.getTableStyles(internalFolder, true)) {
         if(hasPermission(principal, style.getName(), ResourceAction.READ)) {
            styleCount++;
         }
      }

      return new TableStyleFolderSummary(
         WizTableStyleController.toDisplayFolder(internalFolder), leafOf(internalFolder),
         WizTableStyleController.toDisplayFolder(parentOf(internalFolder)), styleCount,
         lib.getTableStyleFolders(internalFolder, true).length);
   }

   /** Every folder (the root of the subtree first) and style under it, audit content included. */
   private void collectSubtree(LibManager lib, String folder, List<String> folders,
                               List<XTableStyle> styles)
   {
      folders.add(folder);
      styles.addAll(List.of(lib.getTableStyles(folder)));

      for(String sub : lib.getTableStyleFolders(folder)) {
         collectSubtree(lib, sub, folders, styles);
      }
   }

   /** The internal name of an existing, non-audit folder, or a refusal naming what exists. */
   private String requireExistingFolder(LibManager lib, String path) {
      if(path == null || path.isBlank()) {
         throw new IllegalArgumentException("'path' is required: the table style folder to change.");
      }

      String display = WizTableStyleController.normalizeFolder(path);
      String internal = WizTableStyleController.toInternalFolder(display);

      if(internal == null) {
         throw new IllegalArgumentException("The library root is not a folder and cannot be changed.");
      }

      if(!lib.containsFolder(internal)) {
         throw new IllegalArgumentException(
            "No table style folder '" + display + "' (folder names are case-sensitive). " +
            "Existing table style folders: " + existingFolders(lib) + ".");
      }

      if(lib.isAuditStyleFolder(internal)) {
         throw new IllegalArgumentException(
            "'" + display + "' is an audit table style folder and cannot be changed.");
      }

      return internal;
   }

   private void requireParentExists(LibManager lib, String internalParent, String displayParent) {
      if(internalParent == null) {
         return;
      }

      if(!lib.containsFolder(internalParent) || lib.isAuditStyleFolder(internalParent)) {
         throw new IllegalArgumentException(
            "Parent folder '" + displayParent + "' does not exist. Existing table style " +
            "folders: " + existingFolders(lib) + ". Create the parent first.");
      }
   }

   /** The library overwrites on add/rename, so a duplicate must be refused before mutating. */
   private void requireNoDuplicate(LibManager lib, String full, String name) {
      if(lib.containsFolder(full) || tableStyleService.containsTableStyle(full)) {
         throw new IllegalArgumentException(
            "'" + name + "' already exists here as a folder or a table style. Pick a different name.");
      }
   }

   private String existingFolders(LibManager lib) {
      List<String> existing = new ArrayList<>();
      collectNames(lib, null, existing);
      return existing.isEmpty() ? "(none)" : String.join(", ", existing);
   }

   private void collectNames(LibManager lib, String internalParent, List<String> out) {
      for(String folder : lib.getTableStyleFolders(internalParent, true)) {
         out.add(WizTableStyleController.toDisplayFolder(folder));
         collectNames(lib, folder, out);
      }
   }

   private static String requireFolderName(String name, String field) {
      String clean = name == null ? "" : SUtil.removeControlChars(name).trim();

      if(clean.isEmpty()) {
         throw new IllegalArgumentException("'" + field + "' is required and cannot be blank.");
      }

      if(Tool.containsPathSeparator(clean, LibManager.SEPARATOR.charAt(0))) {
         throw new IllegalArgumentException(
            "'" + field + "' must not contain '/' or '" + LibManager.SEPARATOR + "'. Use " +
            "'parent' (a plain \"/\"-joined path) to place the folder inside another folder.");
      }

      return clean;
   }

   private static String leafOf(String internal) {
      int sep = internal.lastIndexOf(LibManager.SEPARATOR);
      return sep < 0 ? internal : internal.substring(sep + LibManager.SEPARATOR.length());
   }

   private static String parentOf(String internal) {
      int sep = internal.lastIndexOf(LibManager.SEPARATOR);
      return sep < 0 ? null : internal.substring(0, sep);
   }

   private void requireAll(Principal principal, String name, ResourceAction... actions) {
      for(ResourceAction action : actions) {
         requirePermission(principal, name, action);
      }
   }

   private void requirePermission(Principal principal, String name, ResourceAction action) {
      if(!hasPermission(principal, name, action)) {
         throw new SecurityException("No " + action + " permission on table style '" + name + "'.");
      }
   }

   private boolean hasPermission(Principal principal, String name, ResourceAction action) {
      try {
         return securityEngine.checkPermission(principal, ResourceType.TABLE_STYLE, name, action);
      }
      catch(Exception e) {
         return false;
      }
   }

   /** Root is {@code TABLE_STYLE_LIBRARY "*"}, any other folder {@code TABLE_STYLE <path>}. */
   private void requireFolderWritePermission(Principal principal, String internalFolder) {
      ResourceType type = internalFolder == null ?
         ResourceType.TABLE_STYLE_LIBRARY : ResourceType.TABLE_STYLE;
      String resource = internalFolder == null ? "*" : internalFolder;
      boolean allowed;

      try {
         allowed = securityEngine.checkPermission(principal, type, resource, ResourceAction.WRITE);
      }
      catch(Exception e) {
         allowed = false;
      }

      if(!allowed) {
         throw new SecurityException("No WRITE permission on table style folder '" +
            WizTableStyleController.toDisplayFolder(internalFolder) + "'.");
      }
   }

   private final LibManagerProvider libManagerProvider;
   private final TableStyleService tableStyleService;
   private final SecurityEngine securityEngine;
}
