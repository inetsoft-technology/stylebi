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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.composer.tablestyle.TableStyleFormatModel;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import inetsoft.web.viewsheet.service.VSDialogService;
import inetsoft.web.wiz.service.WizTableStylePreviewRenderer;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.security.Principal;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;

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
                               SecurityEngine securityEngine,
                               WizTableStylePreviewRenderer previewRenderer)
   {
      this.libManagerProvider = libManagerProvider;
      this.tableStyleService = tableStyleService;
      this.securityEngine = securityEngine;
      this.previewRenderer = previewRenderer;
   }

   public record TableStyleSummary(String styleId, String name, String folder) {}

   public record TableStyleDetail(String styleId, String name, String folder,
                                  TableStyleFormatModel format) {}

   /**
    * @param createFolders opt-in: register any missing ancestor of {@code folder} (the explicit
    *                      counterpart of the Composer's Add Folder). Without it a folder that is
    *                      not already registered is refused.
    */
   public record CreateTableStyleRequest(String name, String folder, TableStyleFormatModel format,
                                         Boolean createFolders) {}

   public record UpdateTableStyleRequest(String name, String folder, TableStyleFormatModel format,
                                         Boolean createFolders) {}

   /**
    * @param styleId a saved style to render; mutually exclusive with {@code format}.
    * @param format  a COMPLETE unsaved format draft to render; mutually exclusive with
    *                {@code styleId}.
    */
   public record PreviewTableStyleRequest(String styleId, TableStyleFormatModel format) {}

   /** @param image the PNG, base64-encoded. */
   public record TablePreview(String image, int width, int height) {}

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

   /**
    * {@code read_table_style}. The styleId is a query parameter, so a style whose styleId is
    * "defaults" is not shadowed by {@link #defaults()}.
    */
   @GetMapping(params = "styleId")
   public TableStyleDetail readById(@RequestParam("styleId") String styleId,
                                    Principal principal)
   {
      return read(styleId, principal);
   }

   /**
    * {@code read_table_style} of older plugins. A style whose styleId is "defaults" can't be
    * read here, because {@link #defaults()} takes that path.
    */
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

   /**
    * {@code preview_table_style}. Renders a saved style or an unsaved format draft over the same
    * sample table the Composer's style editor uses, and persists nothing.
    */
   @PostMapping("/preview")
   public TablePreview preview(@RequestBody PreviewTableStyleRequest request, Principal principal)
      throws Exception
   {
      boolean hasId = request.styleId() != null && !request.styleId().isBlank();

      if(hasId == (request.format() != null)) {
         throw new IllegalArgumentException(
            "preview_table_style requires exactly one of 'styleId' or 'format'.");
      }

      XTableStyle style;

      if(hasId) {
         LibManager lib = libManagerProvider.getManager(principal);
         XTableStyle saved = requireExists(lib, request.styleId());
         requirePermission(principal, saved.getName(), ResourceAction.READ);
         style = saved.clone();
         style.setTable(tableStyleService.getTableModel());
         tableStyleService.initTableStyle(style);
      }
      else {
         requireCompleteFormat(request.format());
         style = new XTableStyle(tableStyleService.getTableModel());
         tableStyleService.initTableStyle(style);
         request.format().updateTableStyle(style);
      }

      byte[] png = previewRenderer.renderPng(style);
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
      return new TablePreview(Base64.getEncoder().encodeToString(png),
                              img.getWidth(), img.getHeight());
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

      String folder = request.folder() == null ? null : normalizeFolder(request.folder(), "folder");
      String internalFolder = toInternalFolder(folder);
      LibManager lib = libManagerProvider.getManager(principal);
      List<String> missingFolders =
         requireFoldersAllowed(lib, internalFolder, request.createFolders(), folder);
      String styleName = tableStyleService.getTableStyleLabel(internalFolder, request.name());
      requirePermission(principal, styleName, ResourceAction.WRITE);

      if(tableStyleService.contains(internalFolder, request.name())) {
         throw new IllegalArgumentException(
            "A table style named '" + request.name() + "' already exists" +
            (folder != null && !folder.isEmpty() ? " in folder '" + folder + "'" : "") +
            ". Use update_table_style to change it, or pick a different name/folder.");
      }

      requireNameFree(lib, tableStyleService, styleName, request.name(), null);
      requireRegistrable(lib, tableStyleService, missingFolders, null);

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

      registerFolders(lib, principal, missingFolders);
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
      String oldFullName = style.getName();
      requirePermission(principal, oldFullName, ResourceAction.WRITE);

      if(request.format() != null) {
         requireCompleteFormat(request.format());
      }

      if(request.name() != null || request.folder() != null) {
         if(request.name() != null && request.name().isBlank()) {
            throw new IllegalArgumentException("'name' must not be blank.");
         }

         if(request.name() != null && request.name().contains(LibManager.SEPARATOR)) {
            throw new IllegalArgumentException(
               "'name' must not contain '" + LibManager.SEPARATOR + "' -- that is LibManager's " +
               "own internal folder separator, not a valid character in a table style's plain " +
               "name. Use 'folder' (a plain \"/\"-joined path) to move it instead.");
         }

         if(request.folder() != null && request.folder().contains(LibManager.SEPARATOR)) {
            throw new IllegalArgumentException(
               "'folder' must not contain '" + LibManager.SEPARATOR + "' -- that is LibManager's " +
               "own internal folder separator; join nested folder segments with '/' instead " +
               "(e.g. 'User Defined/Sales').");
         }

         String folder = request.folder() == null ? null : normalizeFolder(request.folder(), "folder");
         String oldInternalFolder = internalFolderOf(oldFullName);
         String newLeaf = request.name() != null ? request.name() : leafName(oldFullName);
         String newInternalFolder = folder != null ?
            toInternalFolder(folder) : oldInternalFolder;
         String newFullName = newInternalFolder == null ?
            newLeaf : newInternalFolder + LibManager.SEPARATOR + newLeaf;

         if(!newFullName.equals(oldFullName)) {
            // LibManager.renameTableStyle/TableStyleLogicalLibrary.rename do no duplicate-name
            // check themselves (that's a controller-layer responsibility in the composer flows
            // this mirrors -- see AssetEventUtil.isRenameDuplicate/isChangeDuplicate), so a
            // rename-to-an-existing-name would otherwise silently collide.
            if(tableStyleService.contains(newInternalFolder, newLeaf)) {
               throw new IllegalArgumentException(
                  "A table style named '" + newLeaf + "' already exists" +
                  (newInternalFolder != null ?
                     " in folder '" + toDisplayFolder(newInternalFolder) + "'" : "") +
                  ". Pick a different name/folder.");
            }

            requireNameFree(lib, tableStyleService, newFullName, newLeaf, oldFullName);
            requirePermission(principal, newFullName, ResourceAction.WRITE);
            List<String> missingFolders = List.of();

            if(!Objects.equals(newInternalFolder, oldInternalFolder)) {
               missingFolders = requireFoldersAllowed(
                  lib, newInternalFolder, request.createFolders(), folder);
               requireRegistrable(lib, tableStyleService, missingFolders, oldFullName);
               requireFolderWritePermission(principal, newInternalFolder);
            }

            registerFolders(lib, principal, missingFolders);

            lib.renameTableStyle(oldFullName, newFullName, styleId);

            // Critical: lib.setTableStyle(style.getID(), style) below runs unconditionally and
            // persists THIS clone, not the stored entry renameTableStyle just mutated -- without
            // this, that call silently reverts the rename on every request.
            style.setName(newFullName);
         }
      }

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

   /**
    * Ancestors of {@code internalFolder} (outermost first, the folder itself last) that are not a
    * registered, non-audit table style folder. {@code list()} only descends registered folders, so
    * a style stored under an unregistered one is an invisible orphan. Uses {@code containsFolder}/
    * {@code isAuditStyleFolder} like the table style import does, on the principal's own library
    * (exact, case-sensitive match).
    */
   private List<String> missingFolders(LibManager lib, String internalFolder) {
      List<String> missing = new ArrayList<>();

      if(internalFolder == null) {
         return missing;
      }

      String path = null;

      for(String segment : internalFolder.split(java.util.regex.Pattern.quote(LibManager.SEPARATOR), -1)) {
         path = path == null ? segment : path + LibManager.SEPARATOR + segment;

         if(!lib.containsFolder(path) || lib.isAuditStyleFolder(path)) {
            missing.add(path);
         }
      }

      return missing;
   }

   /**
    * Refuses a folder that is not registered unless the caller opted in with
    * {@code createFolders}; returns the folders {@link #registerFolders} must register.
    */
   private List<String> requireFoldersAllowed(LibManager lib, String internalFolder,
                                              Boolean createFolders, String displayFolder)
   {
      List<String> missing = missingFolders(lib, internalFolder);

      if(missing.isEmpty() || Boolean.TRUE.equals(createFolders)) {
         return missing;
      }

      List<String> existing = new ArrayList<>();
      collectFolders(lib, null, existing);

      throw new IllegalArgumentException(
         "'folder' '" + displayFolder + "' does not exist: '" + toDisplayFolder(missing.get(0)) +
         "' is not a registered table style folder (folder names are case-sensitive). " +
         "Existing table style folders: " + (existing.isEmpty() ? "(none)" : String.join(", ", existing)) +
         ". Pass createFolders: true to create the missing folder(s), or use an existing folder.");
   }

   private void collectFolders(LibManager lib, String internalFolder, List<String> out) {
      for(String sub : lib.getTableStyleFolders(internalFolder, true)) {
         out.add(toDisplayFolder(sub));
         collectFolders(lib, sub, out);
      }
   }

   private void registerFolders(LibManager lib, Principal principal, List<String> missing) {
      for(String folder : missing) {
         requireFolderWritePermission(principal, internalFolderOf(folder));
         lib.addTableStyleFolder(folder);
      }
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
         if(VSDialogService.CROSSTAB_STYLE_ID.equals(styleId)) {
            throw new IllegalArgumentException(
               "'" + styleId + "' is a built-in table style, not a library style, so it cannot be " +
               "read, edited, previewed or deleted. Apply it by writing it to an assembly's " +
               "'tableStyle'.");
         }

         throw new IllegalArgumentException(
            "No table style with styleId '" + styleId + "'. list_table_styles reports what exists.");
      }

      return style;
   }

   private static String leafName(String name) {
      int sep = name.lastIndexOf(LibManager.SEPARATOR);
      return sep < 0 ? name : name.substring(sep + LibManager.SEPARATOR.length());
   }

   /** {@code leafName}'s counterpart -- the display folder a full internal name lives under. */
   private String folderOf(String name) {
      int sep = name.lastIndexOf(LibManager.SEPARATOR);
      return sep < 0 ? "" : toDisplayFolder(name.substring(0, sep));
   }

   /** {@code internalFolderOf}'s counterpart to {@code folderOf} -- no display conversion, so
    * the result is directly comparable against {@code toInternalFolder}'s output. */
   private static String internalFolderOf(String name) {
      int sep = name.lastIndexOf(LibManager.SEPARATOR);
      return sep < 0 ? null : name.substring(0, sep);
   }

   /**
    * Canonicalizes a caller-supplied display folder before any validation, folder registration or
    * storage: trims it and strips a single trailing {@code "/"} (so {@code "Bands/"} is
    * {@code "Bands"} and a lone {@code "/"} is the root, {@code ""}). Any empty segment left
    * after that ({@code "/X"}, {@code "Bands//X"}, {@code "Bands//"}, {@code "//"}) is ambiguous
    * and refused, so it can never be stored as an unlistable {@code "Bands~"}-style parent or
    * registered as an empty-named folder. Only {@code ""} and {@code "/"} mean the root: a value
    * that merely normalizes to empty ({@code "  "}, {@code " / "}, a control character) is refused
    * rather than silently read as the root. Segments are NOT trimmed here -- this is also the
    * lookup canonicalizer, and an existing folder may legitimately carry edge whitespace.
    *
    * @param field the request field the value came from, named in the errors.
    */
   static String normalizeFolder(String folder, String field) {
      String f = folder.trim();

      if(f.endsWith("/")) {
         f = f.substring(0, f.length() - 1);
      }

      if(f.isEmpty() && !folder.isEmpty() && !folder.equals("/")) {
         throw new IllegalArgumentException(
            "'" + field + "' is blank -- pass '' (or omit it) for the library root, or a real " +
            "folder path such as 'User Defined/Sales'.");
      }

      if(!f.isEmpty()) {
         for(String segment : f.split("/", -1)) {
            if(segment.isEmpty()) {
               throw new IllegalArgumentException(
                  "'" + field + "' '" + folder + "' contains an empty path segment -- join " +
                  "folder names with a single '/' (e.g. 'User Defined/Sales'), with no leading " +
                  "or doubled '/'; omit it or pass '' for the library root.");
            }
         }
      }

      return f;
   }

   /**
    * Table styles and folders share one full-name namespace in {@code LibManager} (and the same
    * TABLE_STYLE permission key), so a name taken by either blocks the other.
    *
    * @param exceptStyleFullName a style full name to ignore (the style being moved/renamed), or null.
    * @return {@code "folder"}, {@code "table style"}, or null when {@code full} is free.
    */
   static String nameClash(LibManager lib, TableStyleService svc, String full,
                           String exceptStyleFullName)
   {
      if(lib.containsFolder(full)) {
         return "folder";
      }

      if(!full.equals(exceptStyleFullName) && svc.containsTableStyle(full)) {
         return "table style";
      }

      return null;
   }

   private static void requireNameFree(LibManager lib, TableStyleService svc, String full,
                                       String leaf, String exceptStyleFullName)
   {
      String kind = nameClash(lib, svc, full, exceptStyleFullName);

      if(kind != null) {
         throw new IllegalArgumentException(
            "'" + leaf + "' already exists as a " + kind + " at '" + toDisplayFolder(full) +
            "' -- table styles and folders share one name space. Pick a different name.");
      }
   }

   /**
    * Checks every folder {@link #registerFolders} is about to register, before anything is
    * written: its name must be free (intermediate folders included) and its leaf segment must have
    * no edge whitespace or control characters, matching what the folder tool itself would create.
    * Already registered folders are looked up verbatim and never checked here.
    */
   private static void requireRegistrable(LibManager lib, TableStyleService svc,
                                          List<String> missing, String exceptStyleFullName)
   {
      for(String path : missing) {
         if(lib.containsFolder(path)) {
            continue;
         }

         String leaf = leafName(path);
         requireNameFree(lib, svc, path, leaf, exceptStyleFullName);

         if(!isCleanFolderSegment(leaf)) {
            throw new IllegalArgumentException(
               "'folder' segment '" + leaf.replaceAll("\\p{Cntrl}", "?") + "' would be created " +
               "as a new folder name with leading/trailing whitespace, a control character or no " +
               "text; use a plain name such as '" +
               SUtil.removeControlChars(leaf).strip() + "'.");
         }
      }
   }

   private static boolean isCleanFolderSegment(String segment) {
      if(segment.isEmpty() || !segment.equals(SUtil.removeControlChars(segment))) {
         return false;
      }

      return !isEdgeSpace(segment.charAt(0)) && !isEdgeSpace(segment.charAt(segment.length() - 1));
   }

   private static boolean isEdgeSpace(char c) {
      return Character.isWhitespace(c) || Character.isSpaceChar(c);
   }

   /** {@code null}/blank means the library root, {@code LibManager}'s own root sentinel. */
   static String toInternalFolder(String displayFolder) {
      if(displayFolder == null || displayFolder.isBlank()) {
         return null;
      }

      return displayFolder.replace("/", LibManager.SEPARATOR);
   }

   static String toDisplayFolder(String internalFolder) {
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

   /**
    * Mirrors {@code ChangeAssetController.changeTableStyle}'s destination-folder WRITE check --
    * the name-level check in {@link #requirePermission} only covers the full style name, not the
    * folder itself, and {@code TABLE_STYLE_LIBRARY}/{@code "*"} (root) vs. {@code TABLE_STYLE}/
    * folder-path (non-root) use different resource types, same as that controller's
    * {@code getTableStyleResourceType}.
    */
   private void requireFolderWritePermission(Principal principal, String internalFolder) {
      ResourceType type = internalFolder == null ? ResourceType.TABLE_STYLE_LIBRARY : ResourceType.TABLE_STYLE;
      String resource = internalFolder == null ? "*" : internalFolder;
      boolean allowed;

      try {
         allowed = securityEngine.checkPermission(principal, type, resource, ResourceAction.WRITE);
      }
      catch(Exception e) {
         allowed = false;
      }

      if(!allowed) {
         throw new SecurityException(
            "No WRITE permission on table style folder '" + toDisplayFolder(internalFolder) + "'.");
      }
   }

   private final LibManagerProvider libManagerProvider;
   private final TableStyleService tableStyleService;
   private final SecurityEngine securityEngine;
   private final WizTableStylePreviewRenderer previewRenderer;
}
