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
import inetsoft.uql.tabular.TabularDataSource;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

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
                               SecurityEngine securityEngine)
   {
      this.datasourcesService = datasourcesService;
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
    *
    * @throws java.io.FileNotFoundException if no tabular listing has that name.
    */
   @GetMapping(value = "/tabular/listing", produces = MediaType.APPLICATION_JSON_VALUE)
   public DataSourceDefinition getTabularListing(@RequestParam("name") String name,
                                                 Principal principal)
      throws Exception
   {
      // Resolved here rather than passed straight through: the service looks listings up by display
      // name, and this endpoint's key is the stable one.
      DataSourceListing listing = findTabularListing(name);

      if(listing == null) {
         throw new java.io.FileNotFoundException(
            "No tabular data source listing named \"" + name + "\"");
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
   public DataSourceDefinition refreshTabularView(@RequestBody DataSourceDefinition definition) {
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
      DataSourceDefinition definition = request.definition();

      if(definition == null) {
         return WizTabularSaveResult.failed(WizTabularSaveResult.UNKNOWN);
      }

      String parentPath = emptyToNull(request.parentPath());
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

      try {
         datasourcesService.updateDataSource(path, definition, principal);
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
   public Map<String, Boolean> checkDuplicate(@RequestParam("name") String name) throws Exception {
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

   private static String emptyToNull(String value) {
      return value == null || value.isEmpty() || "/".equals(value) ? null : value;
   }

   private final DatasourcesService datasourcesService;
   private final SecurityEngine securityEngine;
   private static final Logger LOG = LoggerFactory.getLogger(WizTabularController.class);
}
