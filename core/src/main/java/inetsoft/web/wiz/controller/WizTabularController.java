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

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.DataSourceListing;
import inetsoft.uql.DataSourceListingService;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.portal.data.DataSourceDefinition;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.wiz.model.WizTabularListing;
import inetsoft.web.wiz.model.WizTabularListings;
import inetsoft.web.wiz.model.WizTabularSaveResult;
import inetsoft.web.wiz.request.WizTabularCreateRequest;
import inetsoft.web.wiz.service.UnsupportedDatasourceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.*;

/**
 * The wiz API for creating and editing tabular (non-JDBC) data sources.
 *
 * <p>Separate from {@code WizDatabaseController} because tabular and JDBC are two unrelated models;
 * merging them would make both harder to read. What they share is the authorization stance.</p>
 *
 * <h3>Pass-through</h3>
 *
 * <p>{@code DataSourceDefinition} and its {@code TabularView} are returned <em>as they are</em>,
 * with no flattening — the opposite of the JDBC endpoints. The reason is that {@code tabularView}
 * <em>is</em> the UI description; there is no dimension to reduce, and any trimming removes
 * information the renderer needs.</p>
 *
 * <h3>Authorization</h3>
 *
 * <p>Calling the service layer directly is not a permission gate — the equivalent gates the portal
 * controllers apply are repeated here. Denials leave as {@code SecurityException}, which
 * {@code WizControllerErrorHandler} turns into a 403; this class must therefore never declare a
 * local {@code @ExceptionHandler}, which would take precedence over that advice and degrade a 403
 * into a 400.</p>
 *
 * <h3>Keys are not labels</h3>
 *
 * <p>{@code DataSourceListing.getDisplayName()} is {@code getName()} translated for the caller's
 * locale, and StyleBI's own listing lookup matches on the display name — so the portal's key set
 * shifts when the locale does. This controller keys on the stable {@code getName()} and translates
 * only at the edge, so a stored choice survives a locale switch.</p>
 */
@RestController
@RequestMapping("/api/wiz")
public class WizTabularController {
   public WizTabularController(DatasourcesService datasourcesService,
                               XRepository xrepository, SecurityEngine securityEngine)
   {
      this.datasourcesService = datasourcesService;
      this.xrepository = xrepository;
      this.securityEngine = securityEngine;
   }

   /**
    * Lists the data source types the tabular editor offers.
    *
    * <p>JDBC listings are excluded: they have no {@code TabularView} to render and belong to the
    * {@code /databases} editor. The only way to tell is to instantiate the listing's data source and
    * look at its type, which is why this builds one per listing — they are plain objects with
    * default fields, not connections.</p>
    *
    * @param principal the current user.
    *
    * @return the offered listings and the raw categories to group them by.
    */
   @GetMapping(value = "/tabular/listings", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizTabularListings getTabularListings(Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      List<WizTabularListing> listings = new ArrayList<>();

      for(DataSourceListing listing : DataSourceListingService.getAllDataSourceListings(true)) {
         if(!isTabular(listing)) {
            continue;
         }

         String category = listing.getCategory();
         listings.add(new WizTabularListing(
            listing.getName(),
            listing.getDisplayName(),
            category,
            category == null ? null : catalog.getString(category),
            listing.getIcon(),
            listing.getKeywords() == null
               ? List.of() : Arrays.asList(listing.getKeywords())));
      }

      listings.sort(Comparator.comparing(WizTabularListing::name, String.CASE_INSENSITIVE_ORDER));

      List<String> categories = listings.stream()
         .map(WizTabularListing::category)
         .filter(Objects::nonNull)
         .distinct()
         .sorted()
         .toList();

      return new WizTabularListings(listings, categories);
   }

   /**
    * Seeds a NEW data source of the given type.
    *
    * <p>This is the only way to obtain a usable blank form. Posting a bare name and type to
    * {@code /tabular/refresh} yields {@code tabularView: null}, because refresh recomputes an
    * existing view rather than creating one.</p>
    *
    * @param name      the listing's stable {@link DataSourceListing#getName()}, not its display name.
    * @param principal the current user.
    *
    * @return the seeded definition, including the form to render.
    */
   @GetMapping(value = "/tabular/listing", produces = MediaType.APPLICATION_JSON_VALUE)
   public DataSourceDefinition getTabularListing(@RequestParam("name") String name,
                                                 Principal principal)
      throws Exception
   {
      // Resolved here rather than passed straight through: the service looks listings up by display
      // name, and this endpoint's key is the stable one.
      DataSourceListing listing = findTabularListing(name);

      // 404 rather than a bare exception: unhandled ones fall through to a generic 500, and asking
      // for a type that does not exist is the caller's mistake, not a server fault. Measured — a
      // JDBC name such as "MySQL" reached here and returned 500.
      if(listing == null) {
         throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "No tabular data source listing named \"" + name + "\"");
      }

      return datasourcesService.getDataSourceFromListing(listing.getDisplayName());
   }

   /**
    * Loads an existing data source for editing.
    *
    * @param path      the data source's full repository path.
    * @param principal the current user.
    *
    * @return the stored definition and its form.
    */
   @GetMapping(value = "/tabular/definition", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public DataSourceDefinition getTabularDefinition(
      @PermissionPath @RequestParam("path") String path, Principal principal) throws Exception
   {
      return datasourcesService.getDataSourceDefinition(path, principal);
   }

   /**
    * Recomputes the form after a field the rest of it depends on changed.
    *
    * <p>No path-level permission: this touches no stored data. The definition arrives whole from the
    * caller and the server recomputes a view from it. A login is still required, which
    * {@code WizServiceAuthenticationFilter} enforces.</p>
    *
    * <p>The sequence number is echoed back deliberately. The caller increments it before each
    * refresh and discards any response carrying a lower one; drop the echo and a slow response
    * silently overwrites a newer form. The portal does the same.</p>
    *
    * <p>Unlike the portal this does not bind the call to an HTTP session. Refresh was verified
    * stateless, and wiz reaches this server-to-server where a session would be per-call noise
    * rather than the user's. Worth revisiting if a source that keeps session-scoped credentials
    * misbehaves under refresh.</p>
    *
    * @param definition the current form state.
    *
    * @return the recomputed definition.
    */
   @PostMapping(value = "/tabular/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
   public DataSourceDefinition refreshTabularView(@RequestBody DataSourceDefinition definition,
                                                  HttpServletRequest request, Principal principal)
      throws Exception
   {
      if(definition == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definition is required");
      }

      requireEditorPermission(definition, principal);

      // TabularUtil.sessionId is a static ThreadLocal that is only ever set, never cleared, and is
      // handed to connector button methods. Leaving it alone does not mean "no session" on a pooled
      // thread -- it means whatever the PREVIOUS request left there, possibly another user's. This
      // always overwrites it, so a connector sees this caller's session or null, never a stranger's.
      // getSession(false) deliberately: a server-to-server caller should not mint sessions.
      HttpSession session = request == null ? null : request.getSession(false);
      TabularUtil.setSessionId(session == null ? null : session.getId());

      DataSourceDefinition refreshed = datasourcesService.refreshTabularView(definition);
      refreshed.setSequenceNumber(definition.getSequenceNumber());

      return refreshed;
   }

   /**
    * Creates a data source.
    *
    * @param request   the target folder and the definition to save.
    * @param principal the current user.
    *
    * @return the saved path, or the reason the save was rejected.
    */
   @PostMapping(value = "/tabular/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizTabularSaveResult createTabularDataSource(
      @RequestBody WizTabularCreateRequest request, Principal principal) throws Exception
   {
      // On the record itself, not on a field of it: a body that deserializes to literal null would
      // otherwise NPE here and become a 500 before reaching the guard below.
      DataSourceDefinition definition = request == null ? null : request.definition();

      if(definition == null) {
         return WizTabularSaveResult.failed(WizTabularSaveResult.UNKNOWN);
      }

      requireName(definition);

      // The definition's own parentPath is honored when the request omits one. Reading only the
      // request field silently created at the root for any client that set the parent on the
      // definition -- which is the field update DOES read, and the one this method overwrites two
      // lines down. Disagreement is rejected rather than silently resolved.
      String parentPath = emptyToNull(request.parentPath());
      String definitionParent = emptyToNull(definition.getParentPath());

      if(parentPath == null) {
         parentPath = definitionParent;
      }
      else if(definitionParent != null && !parentPath.equals(definitionParent)) {
         throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "parentPath disagrees with definition.parentPath: \"" +
               parentPath + "\" vs \"" + definitionParent + "\"");
      }

      requireCreatePermission(parentPath, principal);

      // Carried on the definition because that is where createNewDataSource reads it from; the
      // request field exists so the gate above can run before anything is written.
      definition.setParentPath(parentPath == null ? "" : parentPath);
      String fullPath = fullPath(parentPath, definition.getName());

      // Checked up front rather than inferred from the failure. A duplicate otherwise surfaces as a
      // translated sentence that has to be string-matched, which breaks in any other locale.
      if(datasourcesService.checkDuplicate(fullPath)) {
         return WizTabularSaveResult.failed(WizTabularSaveResult.DUPLICATE_NAME);
      }

      try {
         datasourcesService.createNewDataSource(definition, false, principal);
      }
      catch(MessageException e) {
         return toFailure(e, "create", fullPath);
      }

      // createNewDataSource no-ops silently when the type is unknown or its connector is
      // unavailable: createDataSource returns null and nothing is thrown. Without this the editor
      // reports success and navigates to a data source that does not exist. The duplicate check
      // above already proved it was absent beforehand, so presence now means the write landed.
      if(!datasourcesService.checkDuplicate(fullPath)) {
         LOG.info("Tabular create for \"{}\" wrote nothing; type \"{}\" may be unavailable",
                  fullPath, definition.getType());

         return WizTabularSaveResult.failed(WizTabularSaveResult.UNKNOWN);
      }

      return WizTabularSaveResult.ok(fullPath);
   }

   /**
    * Updates an existing data source.
    *
    * <p>Gated at the entry point rather than relying on the service's own check, which happens only
    * after the stored definition has been read.</p>
    *
    * @param path       the data source's current full repository path, and the sole source of the
    *                   name to update. A client should leave whatever {@code oldName} it received
    *                   untouched rather than compute one.
    * @param definition the new settings.
    * @param principal  the current user.
    *
    * @return the saved path, which differs from {@code path} when the source was renamed, or the
    *         reason the save was rejected.
    */
   @PostMapping(value = "/tabular/update", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public WizTabularSaveResult updateTabularDataSource(
      @PermissionPath @RequestParam("path") String path,
      @RequestBody DataSourceDefinition definition, Principal principal) throws Exception
   {
      if(definition == null) {
         return WizTabularSaveResult.failed(WizTabularSaveResult.UNKNOWN);
      }

      requireName(definition);

      // The write side is at least as strict as the read side: getTabularDefinition 422s on a
      // non-tabular path, and without this the same request would rewrite a JDBC database from a
      // tabular definition -- renaming it, discarding every JDBC setting -- and report ok.
      requireTabular(path);

      // It vanished between load and save. A 200 carrying this reason, not a 404: the request was
      // well formed and the caller was authorized; what failed is the save.
      if(!datasourcesService.checkDuplicate(path)) {
         return WizTabularSaveResult.failed(WizTabularSaveResult.DATASOURCE_LOST);
      }

      String parentPath = parentOf(path);
      definition.setParentPath(parentPath == null ? "" : parentPath);
      String fullPath = fullPath(parentPath, definition.getName());

      // A rename onto an existing name, not the source's own path.
      if(!fullPath.equals(path) && datasourcesService.checkDuplicate(fullPath)) {
         return WizTabularSaveResult.failed(WizTabularSaveResult.DUPLICATE_NAME);
      }

      // A rename additionally needs DELETE on the old path. The service checks it, but reports the
      // denial as a MessageException, which would land in toFailure and reach the caller as a 200
      // carrying UNKNOWN -- a permission denial disguised as a save failure. Checked here so it
      // leaves as a SecurityException and WizControllerErrorHandler turns it into a 403.
      if(!fullPath.equals(path) &&
         !securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE, path,
                                         ResourceAction.DELETE))
      {
         throw new SecurityException(
            "Unauthorized rename of data source \"" + path + "\" by user " + principal);
      }

      try {
         // The BARE name, not the full path. updateDataSource re-prefixes definition.parentPath
         // itself (`oldName = parentPath + name`), so passing the full path resolves a
         // folder-scoped source to "folder/folder/name", finds nothing, and fails as
         // saveDataSourceLost -> UNKNOWN. Root-level sources only worked because the prefix was
         // empty. Verified live: every folder-scoped edit was broken.
         datasourcesService.updateDataSource(baseName(path), definition, principal);
      }
      catch(MessageException e) {
         return toFailure(e, "update", fullPath);
      }

      return WizTabularSaveResult.ok(fullPath);
   }

   /**
    * Whether a data source already exists at that path, so the editor can reject a name before the
    * server has to.
    *
    * @param name the full repository path to test.
    *
    * @return a single-key object rather than a bare boolean, so the response stays extensible.
    */
   @GetMapping(value = "/tabular/check-duplicate", produces = MediaType.APPLICATION_JSON_VALUE)
   public Map<String, Boolean> checkDuplicate(@RequestParam("name") String name,
                                              Principal principal)
      throws Exception
   {
      // Scoped to a folder the caller may write to. Ungated this answers "does a data source exist
      // at this arbitrary path" for any logged-in user, which enumerates the whole tree including
      // sources they cannot read. The editor only ever asks about a name it is about to save.
      requireCreatePermission(emptyToNull(parentOf(name)), principal);

      return Map.of("duplicate", datasourcesService.checkDuplicate(name));
   }

   /**
    * Maps a business failure onto a stable code.
    *
    * <p>{@code MessageException} carries a Catalog-translated sentence, so the only handle on it is
    * to build the same string and compare. That works because both are produced by the same catalog
    * in the same locale, but it is the reason duplicate names are checked up front instead: the
    * fewer outcomes that depend on this comparison, the better. Anything unrecognized becomes
    * {@code UNKNOWN} and is logged verbatim rather than swallowed.</p>
    */
   private WizTabularSaveResult toFailure(MessageException e, String operation, String path) {
      String invalidFolder =
         Catalog.getCatalog().getString("data.datasources.invalidParentFolder");

      if(invalidFolder != null && invalidFolder.equals(e.getMessage())) {
         return WizTabularSaveResult.failed(WizTabularSaveResult.INVALID_FOLDER);
      }

      LOG.info("Tabular data source {} rejected for \"{}\": {}", operation, path, e.getMessage());

      return WizTabularSaveResult.failed(WizTabularSaveResult.UNKNOWN);
   }

   /**
    * Reject a missing, blank or path-bearing name with a 400 naming the field.
    *
    * Without this a null name builds the literal path "parent/null", a blank one builds "parent/",
    * and a name containing a slash silently relocates the source — each surfacing downstream as a
    * 200 carrying {@code UNKNOWN}, which tells the caller nothing. Mirrors
    * {@code WizDatabaseController.requireName}.
    */
   private void requireName(DataSourceDefinition definition) {
      String name = definition.getName();

      if(name == null || name.isBlank()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definition.name is required");
      }

      if(name.indexOf('/') >= 0) {
         throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "definition.name must not contain \"/\": " + name);
      }
   }

   /**
    * The gate for the two endpoints that are neither a plain read nor a save.
    *
    * "Touches no stored data" answers data exposure, not side effects: refreshing a view invokes
    * connector button and editor methods with a caller-supplied type, values and clicked flag, and
    * those are what reach a remote endpoint. Left ungated the effective gate is "is logged in".
    *
    * The definition tells us which of the two real flows this is. Editing something that exists
    * needs WRITE on it; anything else is the new-source flow and needs the create rule.
    */
   private void requireEditorPermission(DataSourceDefinition definition, Principal principal)
      throws Exception
   {
      String parentPath = emptyToNull(definition.getParentPath());
      String name = definition.getName();
      String path = name == null || name.isBlank() ? null : fullPath(parentPath, name);

      if(path != null && xrepository.getDataSource(path) != null) {
         if(!securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE, path,
                                            ResourceAction.WRITE))
         {
            throw new SecurityException(
               "Unauthorized edit of data source \"" + path + "\" by user " + principal);
         }

         return;
      }

      requireCreatePermission(parentPath, principal);
   }

   /** Reject a write aimed at a data source that is not tabular. */
   private void requireTabular(String path) throws Exception {
      XDataSource dataSource = xrepository.getDataSource(path);

      if(dataSource != null && !(dataSource instanceof TabularDataSource)) {
         throw new UnsupportedDatasourceException(path, dataSource.getType());
      }
   }

   /**
    * The same rule the JDBC create path applies: WRITE on the target folder, falling back at the
    * root to the permission that grants creating data sources at all.
    */
   private void requireCreatePermission(String parentPath, Principal principal) throws Exception {
      boolean root = parentPath == null;
      boolean allowed = securityEngine.checkPermission(
         principal, ResourceType.DATA_SOURCE_FOLDER, root ? "/" : parentPath, ResourceAction.WRITE);

      if(!allowed && root) {
         allowed = securityEngine.checkPermission(
            principal, ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS);
      }

      if(!allowed) {
         throw new SecurityException("Unauthorized access to data source folder \"" +
                                        (root ? "/" : parentPath) + "\" by user " + principal);
      }
   }

   private DataSourceListing findTabularListing(String name) {
      if(name == null) {
         return null;
      }

      for(DataSourceListing listing : DataSourceListingService.getAllDataSourceListings(true)) {
         if(name.equals(listing.getName()) && isTabular(listing)) {
            return listing;
         }
      }

      return null;
   }

   /**
    * Whether a listing produces a tabular data source.
    *
    * <p>A listing that cannot be instantiated is treated as not tabular rather than allowed to fail
    * the whole request: one broken plugin should cost its own entry in the picker, not every
    * entry.</p>
    */
   private boolean isTabular(DataSourceListing listing) {
      try {
         XDataSource dataSource = listing.createDataSource();

         return dataSource instanceof TabularDataSource;
      }
      catch(Exception e) {
         LOG.debug("Could not instantiate data source listing \"{}\": {}",
                   listing.getName(), e.getMessage());

         return false;
      }
   }

   private static String fullPath(String parentPath, String name) {
      return parentPath == null || parentPath.isEmpty() ? name : parentPath + "/" + name;
   }

   private static String parentOf(String path) {
      int i = path == null ? -1 : path.lastIndexOf('/');

      return i < 0 ? null : path.substring(0, i);
   }

   /** The last segment of a repository path — what the service layer means by a data source name. */
   private static String baseName(String path) {
      int i = path == null ? -1 : path.lastIndexOf('/');

      return i < 0 ? path : path.substring(i + 1);
   }

   private static String emptyToNull(String value) {
      return value == null || value.isEmpty() || "/".equals(value) ? null : value;
   }

   private final DatasourcesService datasourcesService;
   private final XRepository xrepository;
   private final SecurityEngine securityEngine;
   private static final Logger LOG = LoggerFactory.getLogger(WizTabularController.class);
}
