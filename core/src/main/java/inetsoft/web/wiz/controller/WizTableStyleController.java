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
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.composer.tablestyle.TableStyleFormatModel;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wraps StyleBI's Table Style asset (LibManager, AssetEntry.Type.TABLE_STYLE) -- a reusable,
 * named table/crosstab format (fonts, borders, row/col banding, per-region rules) created once
 * and applied to any table/crosstab by name (via {@code set_assembly_properties}'s
 * {@code tableStyle} short name) -- under wiz's own JWT-authenticated, CSRF-exempt {@code /api/wiz}
 * namespace (see {@code WizServiceAuthenticationFilter} / {@code CSRFFilter#isWizApi}). Same shape
 * {@code ScriptLibraryController} already established for the sibling repository-level, session-less
 * asset (Redmine #76516 item 4).
 *
 * <p>Talks to {@code LibManager}/{@code TableStyleService} directly -- the same services
 * {@code inetsoft.web.composer.tablestyle.TableStyleController} already uses internally -- rather
 * than proxying that controller or {@code AssetTreeController}/{@code RemoveAssetController}.
 * Those are StyleBI's own internal Composer-SPA REST paths ({@code /api/composer/*}), session-cookie
 * + CSRF protected and not reachable by a stateless bearer-token client.
 *
 * <p>{@code TableStyleFormatModel} (the same class the internal controller's own
 * {@code TableStyleModel.styleFormat} carries) is reused verbatim as this controller's request/
 * response body for a style's formatting -- its field names ({@code topBorderFormat},
 * {@code bodyRegionFormat}, {@code specList}, ...) are exactly the wire shape
 * {@code plugin/composer}'s existing {@code toWireFormat}/{@code fromWireFormat} conversion already
 * targets, so that client-side translation layer needs no changes here, only the endpoints it calls.
 *
 * <p>A folder path is a plain {@code "/"}-joined string at this API boundary (e.g.
 * {@code "User Defined/Sales"}), converted to/from {@code LibManager}'s own {@code "~"}-joined
 * internal separator ({@link LibManager#SEPARATOR}) at the edge -- the internal wire detail stays
 * internal. The library root is the empty string here, {@code null} to every {@code LibManager}/
 * {@code TableStyleService} call that takes a folder (their own root sentinel).
 */
@RestController
@RequestMapping("/api/wiz/v1/table-style")
public class WizTableStyleController {
   public WizTableStyleController(LibManagerProvider libManagerProvider,
                               TableStyleService tableStyleService,
                               SecurityEngine securityEngine)
   {
      this.libManagerProvider = libManagerProvider;
      this.tableStyleService = tableStyleService;
      this.securityEngine = securityEngine;
   }

   public record TableStyleSummary(String styleId, String name, String folder) {}

   public record TableStyleDetail(String styleId, String name, String folder,
                                  TableStyleFormatModel format) {}

   public record CreateTableStyleRequest(String name, String folder, TableStyleFormatModel format) {}

   public record UpdateTableStyleRequest(TableStyleFormatModel format) {}

   @GetMapping
   public List<TableStyleSummary> list(Principal principal) {
      LibManager lib = libManagerProvider.getManager(principal);
      List<TableStyleSummary> out = new ArrayList<>();
      collectStyles(lib, null, principal, out);
      out.sort(Comparator.comparing(TableStyleSummary::folder)
                  .thenComparing(TableStyleSummary::name));
      return out;
   }

   private void collectStyles(LibManager lib, String internalFolder, Principal principal,
                               List<TableStyleSummary> out)
   {
      String displayFolder = toDisplayFolder(internalFolder);

      for(XTableStyle style : lib.getTableStyles(internalFolder, true)) {
         if(!hasPermission(principal, style.getName(), ResourceAction.READ)) {
            continue;
         }

         out.add(new TableStyleSummary(style.getID(), leafName(style.getName()), displayFolder));
      }

      for(String sub : lib.getTableStyleFolders(internalFolder, true)) {
         collectStyles(lib, sub, principal, out);
      }
   }

   /**
    * A freshly-initialized default format (StyleBI's own new-style defaults) with no styleId --
    * mirrors {@code inetsoft.web.composer.tablestyle.TableStyleController.newTableStyle}.
    * {@code create_table_style}/
    * {@code update_table_style} both need a COMPLETE {@code TableStyleFormatModel} (every region
    * populated -- see {@link #requireCompleteFormat}), so a caller changing only one region reads
    * this (or an existing style's own current detail) first and merges its own override on top,
    * the same read-modify-write shape {@code update_table_style} already needs against an
    * existing style.
    */
   @GetMapping("/defaults")
   public TableStyleDetail defaults() {
      XTableStyle style = new XTableStyle(tableStyleService.getTableModel());
      tableStyleService.initTableStyle(style);
      return new TableStyleDetail(null, "", "", new TableStyleFormatModel(style));
   }

   @GetMapping("/{styleId}")
   public TableStyleDetail read(@PathVariable String styleId, Principal principal) {
      LibManager lib = libManagerProvider.getManager(principal);
      XTableStyle style = requireExists(lib, styleId);
      requirePermission(principal, style.getName(), ResourceAction.READ);

      // Matches inetsoft.web.composer.tablestyle.TableStyleController.openTableStyle -- a live
      // TableLens is needed before
      // TableStyleFormatModel's constructor can read meaningful derived region values.
      style.setTable(tableStyleService.getTableModel());
      tableStyleService.initTableStyle(style);

      return toDetail(style);
   }

   @PostMapping
   public TableStyleDetail create(@RequestBody CreateTableStyleRequest request, Principal principal)
      throws Exception
   {
      if(request.name() == null || request.name().isBlank()) {
         throw new IllegalArgumentException("create_table_style requires 'name'.");
      }

      // 'name'/'folder' are plain strings at this API boundary -- LibManager.SEPARATOR ("~") is
      // an internal implementation detail (see toInternalFolder/toDisplayFolder) that must never
      // reach it embedded in a caller-supplied value. Left unchecked, a name like "Foo~Bar"
      // synthesizes a full label ("Foo~Bar", or "<folder>~Foo~Bar") one nesting level deeper than
      // the folder the caller actually asked for -- TableStyleService.contains() only compares
      // against DIRECT children of that folder, so its "already exists" guard silently never
      // fires for this synthesized deeper name, and the created style can land as an orphaned
      // leaf list() may not even discover (getTableStyles/getTableStyleFolders are themselves
      // direct-children-only, per the class-level note on collectStyles()).
      if(request.name().contains(LibManager.SEPARATOR)) {
         throw new IllegalArgumentException(
            "'name' must not contain '" + LibManager.SEPARATOR + "' -- that is LibManager's own " +
            "internal folder separator, not a valid character in a table style's plain name. " +
            "Use 'folder' (a plain \"/\"-joined path) to place the style in a folder instead.");
      }

      if(request.folder() != null && request.folder().contains(LibManager.SEPARATOR)) {
         throw new IllegalArgumentException(
            "'folder' must not contain '" + LibManager.SEPARATOR + "' -- that is LibManager's " +
            "own internal folder separator; join nested folder segments with '/' instead " +
            "(e.g. 'User Defined/Sales').");
      }

      String internalFolder = toInternalFolder(request.folder());
      String styleName = tableStyleService.getTableStyleLabel(internalFolder, request.name());
      requirePermission(principal, styleName, ResourceAction.WRITE);

      if(tableStyleService.contains(internalFolder, request.name())) {
         throw new IllegalArgumentException(
            "A table style named '" + request.name() + "' already exists" +
            (request.folder() != null && !request.folder().isBlank()
                ? " in folder '" + request.folder() + "'" : "") +
            ". Use update_table_style to change it, or pick a different name/folder.");
      }

      LibManager lib = libManagerProvider.getManager(principal);
      XTableStyle style = new XTableStyle(tableStyleService.getTableModel());
      tableStyleService.initTableStyle(style);
      style.setID(lib.getNextStyleID(request.name()));
      style.setName(styleName);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      long now = System.currentTimeMillis();
      style.setCreated(now);
      style.setCreatedBy(pId.getName());
      style.setLastModified(now);
      style.setLastModifiedBy(pId.getName());

      if(request.format() != null) {
         requireCompleteFormat(request.format());
         request.format().updateTableStyle(style);
      }

      lib.setTableStyle(style.getID(), style);
      lib.save();

      return toDetail(style);
   }

   @PutMapping("/{styleId}")
   public TableStyleDetail update(@PathVariable String styleId,
                                  @RequestBody UpdateTableStyleRequest request, Principal principal)
      throws Exception
   {
      LibManager lib = libManagerProvider.getManager(principal);
      XTableStyle style = requireExists(lib, styleId).clone();
      requirePermission(principal, style.getName(), ResourceAction.WRITE);

      if(request.format() != null) {
         requireCompleteFormat(request.format());
         request.format().updateTableStyle(style);
      }

      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      style.setLastModified(System.currentTimeMillis());
      style.setLastModifiedBy(pId.getName());

      lib.setTableStyle(style.getID(), style);
      lib.save();

      return toDetail(style);
   }

   /**
    * No dependency-conflict check on delete -- {@code RemoveAssetController}'s own
    * {@code entry.isTableStyle()} branch has none either (unlike its {@code entry.isScript()}
    * branch's {@code checkScriptRemoveable}), so a table/crosstab left referencing a deleted
    * style is exactly what the human Composer's own delete already allows; this does not
    * introduce a new gap.
    */
   @DeleteMapping("/{styleId}")
   public void delete(@PathVariable String styleId, Principal principal) throws Exception {
      LibManager lib = libManagerProvider.getManager(principal);
      XTableStyle style = requireExists(lib, styleId);
      requirePermission(principal, style.getName(), ResourceAction.DELETE);
      lib.removeTableStyle(styleId);
      lib.save();
   }

   private TableStyleDetail toDetail(XTableStyle style) {
      String name = style.getName();
      return new TableStyleDetail(style.getID(), leafName(name), folderOf(name),
                                   new TableStyleFormatModel(style));
   }

   /**
    * {@code TableStyleFormatModel.updateTableStyle} calls {@code .updateTableStyle()}
    * unconditionally on all nine region fields with no null guard -- a caller-supplied format
    * missing one (e.g. sending only {@code bodyRegionFormat} after reading a partial shape
    * somewhere) would otherwise fail as a bare {@code NullPointerException} (a 500 that gives no
    * hint which field was missing) instead of naming the gap. {@code GET .../defaults} or
    * {@code GET .../{styleId}} is where a caller gets a COMPLETE format to merge its own change
    * onto before writing it back here.
    */
   private void requireCompleteFormat(TableStyleFormatModel format) {
      List<String> missing = new ArrayList<>();
      if(format.getTopBorderFormat() == null) missing.add("topBorderFormat");
      if(format.getBottomBorderFormat() == null) missing.add("bottomBorderFormat");
      if(format.getLeftBorderFormat() == null) missing.add("leftBorderFormat");
      if(format.getRightBorderFormat() == null) missing.add("rightBorderFormat");
      if(format.getHeaderRowFormat() == null) missing.add("headerRowFormat");
      if(format.getTrailerRowFormat() == null) missing.add("trailerRowFormat");
      if(format.getHeaderColFormat() == null) missing.add("headerColFormat");
      if(format.getTrailerColFormat() == null) missing.add("trailerColFormat");
      if(format.getBodyRegionFormat() == null) missing.add("bodyRegionFormat");

      if(!missing.isEmpty()) {
         throw new IllegalArgumentException(
            "'format' is missing " + String.join(", ", missing) + ". It must be a COMPLETE " +
            "TableStyleFormatModel -- read one from GET .../defaults or GET .../{styleId} and " +
            "merge your change onto it, rather than sending a partial object.");
      }
   }

   private XTableStyle requireExists(LibManager lib, String styleId) {
      XTableStyle style = lib.getTableStyle(styleId);

      if(style == null) {
         throw new IllegalArgumentException(
            "No table style with styleId '" + styleId + "'. list_table_styles reports what exists.");
      }

      return style;
   }

   private String leafName(String name) {
      int sep = name.lastIndexOf(LibManager.SEPARATOR);
      return sep < 0 ? name : name.substring(sep + LibManager.SEPARATOR.length());
   }

   /** {@code leafName}'s counterpart -- the display folder a full internal name lives under. */
   private String folderOf(String name) {
      int sep = name.lastIndexOf(LibManager.SEPARATOR);
      return sep < 0 ? "" : toDisplayFolder(name.substring(0, sep));
   }

   /** {@code null}/blank means the library root, {@code LibManager}'s own root sentinel. */
   private static String toInternalFolder(String displayFolder) {
      if(displayFolder == null || displayFolder.isBlank()) {
         return null;
      }

      return displayFolder.replace("/", LibManager.SEPARATOR);
   }

   private static String toDisplayFolder(String internalFolder) {
      return internalFolder == null ? "" : internalFolder.replace(LibManager.SEPARATOR, "/");
   }

   private void requirePermission(Principal principal, String styleName, ResourceAction action) {
      if(!hasPermission(principal, styleName, action)) {
         throw new SecurityException("No " + action + " permission on table style '" + styleName + "'.");
      }
   }

   private boolean hasPermission(Principal principal, String styleName, ResourceAction action) {
      try {
         return securityEngine.checkPermission(principal, ResourceType.TABLE_STYLE, styleName, action);
      }
      catch(Exception e) {
         return false;
      }
   }

   private final LibManagerProvider libManagerProvider;
   private final TableStyleService tableStyleService;
   private final SecurityEngine securityEngine;
}
