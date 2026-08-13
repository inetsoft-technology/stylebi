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
 *
 * <h3>The connector session</h3>
 *
 * <p>Every endpoint that can reach {@code TabularUtil.refreshView} brackets the call with
 * {@link #beginConnectorSession(Principal)} and {@link #endConnectorSession()} — see those methods
 * for why leaving the thread-local alone is not the neutral option it looks like.</p>
 */
@RestController
@RequestMapping("/api/wiz")
public class WizTabularController {
   public WizTabularController(DatasourcesService datasourcesService,
                               SecurityEngine securityEngine,
                               XRepository xrepository)
   {
      this.datasourcesService = datasourcesService;
      this.securityEngine = securityEngine;
      this.xrepository = xrepository;
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

      beginConnectorSession(principal);

      try {
         return datasourcesService.getDataSourceFromListing(listing.getDisplayName());
      }
      finally {
         endConnectorSession();
      }
   }

   /**
    * Loads an existing data source for editing.
    *
    * <h4>Secrets come back in clear text, and that is the contract</h4>
    *
    * <p>Passwords, API keys and tokens are returned inside {@code tabularView} as ordinary
    * {@code editor.value} strings — the opposite of the JDBC endpoints, where the stored password
    * never leaves the server and null means "keep it". Confirmed as intended rather than
    * overlooked, and written down here because the reasoning otherwise lives only in the wiz
    * client and reads like a defect from this side.</p>
    *
    * <p>The refresh protocol forces it. The client posts the whole tree back and the server
    * recomputes it, so a masked value would be handed to the connector exactly as if the user had
    * typed the mask: the recomputed form would be wrong — a connector authenticating against the
    * mask populates nothing — and the next save would persist the mask over the real secret.
    * Masking would therefore mean holding per-node secrets server-side, keyed to an editing
    * session, which replaces the stateless refresh protocol rather than adding to it.</p>
    *
    * <p>The exposure is bounded: this endpoint is gated on WRITE, so a caller who receives a secret
    * could already overwrite it, and it is the same readback the native Angular portal has always
    * done into the browser.</p>
    *
    * @param path      the data source's full repository path.
    * @param principal the current user.
    *
    * @return the stored definition and its form.
    *
    * @throws UnsupportedDatasourceException if the path names a data source that is not tabular —
    *                                        a JDBC database has no form here and would render as an
    *                                        empty one.
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
      requireTabularDataSource(path);
      beginConnectorSession(principal);

      try {
         return datasourcesService.getDataSourceDefinition(path, principal);
      }
      finally {
         endConnectorSession();
      }
   }

   /**
    * Recomputes the form after a field the rest of it depends on changed.
    *
    * <p>No path-level permission, because there is no path: the definition arrives whole from the
    * caller and nothing stored is read. That answers data exposure but not side effects — refresh
    * invokes the connector's own button and editor methods with a caller-controlled type, property
    * values and {@code clicked} flag, and those are what reach a remote endpoint or a file. So the
    * gate is on the capability instead of on a resource; see
    * {@link #requireConnectorPermission(Principal)}.</p>
    *
    * <p>The sequence number is echoed back deliberately. The caller increments it before each
    * refresh and discards any response carrying a lower one; drop the echo and a slow response
    * silently overwrites a newer form. The portal does the same.</p>
    *
    * @param definition the current form state.
    * @param principal  the current user.
    *
    * @return the recomputed definition.
    */
   @PostMapping(value = "/tabular/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
   public DataSourceDefinition refreshTabularView(@RequestBody DataSourceDefinition definition,
                                                  Principal principal)
      throws Exception
   {
      requireConnectorPermission(principal);
      beginConnectorSession(principal);

      try {
         DataSourceDefinition refreshed = datasourcesService.refreshTabularView(definition);
         refreshed.setSequenceNumber(definition.getSequenceNumber());

         return refreshed;
      }
      finally {
         endConnectorSession();
      }
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
      // Read off the record itself rather than through it: a body that deserializes to a literal
      // null would otherwise throw before the guard below could answer.
      DataSourceDefinition definition = request == null ? null : request.definition();

      if(definition == null) {
         return WizTabularSaveResult.failed(WizTabularSaveResult.UNKNOWN);
      }

      String name = requireName(definition);

      // The request field is the one the portal sends, and the one the gate below needs before
      // anything is written. The definition's own value is a fallback rather than an error: a
      // caller that set the folder only there would otherwise land at the root with no complaint.
      String parentPath = emptyToNull(request.parentPath());

      if(parentPath == null) {
         parentPath = emptyToNull(definition.getParentPath());
      }

      requireCreatePermission(parentPath, principal);

      // Carried on the definition because that is where createNewDataSource reads it from; the
      // request field exists so the gate above can run before anything is written.
      definition.setParentPath(parentPath == null ? "" : parentPath);
      String fullPath = fullPath(parentPath, name);

      // Checked up front rather than inferred from the failure. A duplicate otherwise surfaces as a
      // translated sentence that has to be string-matched, which breaks in any other locale.
      if(datasourcesService.checkDuplicate(fullPath)) {
         return WizTabularSaveResult.failed(WizTabularSaveResult.DUPLICATE_NAME);
      }

      beginConnectorSession(principal);

      try {
         datasourcesService.createNewDataSource(definition, false, principal);
      }
      catch(MessageException e) {
         return toFailure(e, "create", fullPath);
      }
      finally {
         endConnectorSession();
      }

      // createNewDataSource writes nothing and throws nothing when the type is unknown or its
      // connector is unavailable — createDataSource answers null and the whole body is skipped. The
      // duplicate check above already proved the path was free, so an absence here is that no-op,
      // and reporting it as a success would send the editor to a data source that does not exist.
      if(!datasourcesService.checkDuplicate(fullPath)) {
         LOG.warn("Tabular data source \"{}\" was not created; type \"{}\" is unknown or its " +
                     "connector is unavailable", fullPath, definition.getType());

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
    *
    * @throws UnsupportedDatasourceException if the path names a data source that is not tabular —
    *                                        the save would rewrite it from a tabular definition.
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

      String name = requireName(definition);

      // It vanished between load and save. A 200 carrying this reason, not a 404: the request was
      // well formed and the caller was authorized; what failed is the save.
      if(!datasourcesService.checkDuplicate(path)) {
         return WizTabularSaveResult.failed(WizTabularSaveResult.DATASOURCE_LOST);
      }

      requireTabularDataSource(path);

      String parentPath = parentOf(path);
      String oldName = lastSegment(path);
      definition.setParentPath(parentPath == null ? "" : parentPath);
      String fullPath = fullPath(parentPath, name);

      // A rename onto an existing name, not the source's own path.
      if(!fullPath.equals(path) && datasourcesService.checkDuplicate(fullPath)) {
         return WizTabularSaveResult.failed(WizTabularSaveResult.DUPLICATE_NAME);
      }

      // A rename removes the source from its old name, which is why the service demands DELETE on
      // top of the WRITE the annotation checks. It signals that denial with a MessageException,
      // which the catch below would map to UNKNOWN — a 200 reading "could not save" for what is
      // really "not allowed". Checked here so the denial leaves as the SecurityException it is.
      if(!name.equals(oldName)) {
         requireDataSourcePermission(path, ResourceAction.DELETE, principal);
      }

      beginConnectorSession(principal);

      try {
         // The bare name, not the full path: updateDataSource builds its own lookup key by
         // re-prefixing the definition's parent path, so a full path here sends it after
         // "folder/folder/mongo", which finds nothing and fails as DATASOURCE_LOST.
         datasourcesService.updateDataSource(oldName, definition, principal);
      }
      catch(MessageException e) {
         return toFailure(e, "update", fullPath);
      }
      finally {
         endConnectorSession();
      }

      return WizTabularSaveResult.ok(fullPath);
   }

   /**
    * Whether a data source already exists at that path, so the editor can reject a name before the
    * server has to.
    *
    * <p>Scoped to the folder the caller may create in, rather than answering for any path at all.
    * The answer is an existence oracle — unscoped it would let any logged-in user enumerate what
    * exists anywhere, including sources they cannot read — and the editor only ever asks about the
    * folder it is about to write into, so the create rule costs it nothing.</p>
    *
    * @param name      the full repository path to test.
    * @param principal the current user.
    *
    * @return a single-key object rather than a bare boolean, so the response stays extensible.
    */
   @GetMapping(value = "/tabular/check-duplicate", produces = MediaType.APPLICATION_JSON_VALUE)
   public Map<String, Boolean> checkDuplicate(@RequestParam("name") String name,
                                              Principal principal)
      throws Exception
   {
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
    * Fails unless the path names a tabular data source.
    *
    * <p>Nothing downstream checks this, and both ends of the editor need it. On the write path,
    * pointed at a JDBC database, the save rebuilds it from the tabular definition — renaming it and
    * discarding every connection setting it has — and still reports success. On the read path the
    * same database comes back as a 200 carrying the empty form {@code LayoutCreator} produces for a
    * source that has no {@code TabularView}, which the editor renders as a blank one. A 422 rather
    * than a 400: the request is well formed, the data source is simply not one this editor owns.</p>
    *
    * <p>A path with nothing behind it is left alone rather than reported as untypeable — the two
    * callers already answer that case, one with {@code DATASOURCE_LOST} and one by letting the
    * service's own lookup fail.</p>
    */
   private void requireTabularDataSource(String path) throws Exception {
      XDataSource dataSource = xrepository.getDataSource(path);

      if(dataSource != null && !(dataSource instanceof TabularDataSource)) {
         throw new UnsupportedDatasourceException(path, dataSource.getType());
      }
   }

   /**
    * Fails unless the caller holds the action on a data source.
    *
    * <p>Throws {@code java.lang.SecurityException}, which is what {@code SecuredAspect} throws for
    * the annotated endpoints, so a denial from here and a denial from an annotation reach
    * {@code WizControllerErrorHandler} — and the client — as the same 403.</p>
    */
   private void requireDataSourcePermission(String path, ResourceAction action, Principal principal)
      throws Exception
   {
      if(!securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE, path, action)) {
         throw new SecurityException(
            "Unauthorized access to data source \"" + path + "\" by user " + principal);
      }
   }

   /**
    * Fails unless the caller may configure data sources at all.
    *
    * <p>A capability check rather than a path check, because refresh has no path: it recomputes a
    * view from a definition the caller posted, and nothing stored is involved. What it does have is
    * reach — the connector's button and editor methods run with a caller-supplied type, property
    * values and {@code clicked} flag, and those are what dial a remote endpoint or touch a file. So
    * the only question this endpoint can answer is "may this user configure data sources at all",
    * not "may they touch this one", and the capability that means is the creating one.</p>
    *
    * <p>Which is why it is exactly the root create rule and not a narrower reading of it: refresh
    * is a step inside the flow create and update already authorize, so anything those accept at the
    * root has to be accepted here, or the editor 403s halfway through a save it is going to be
    * allowed to finish.</p>
    */
   private void requireConnectorPermission(Principal principal) throws Exception {
      requireCreatePermission(null, principal);
   }

   /**
    * Binds this request's connector session before anything can reach
    * {@code TabularUtil.refreshView}.
    *
    * <p>{@code TabularUtil.sessionId} is a static {@code ThreadLocal} that is only ever set, never
    * cleared. On a pooled request thread, leaving it alone therefore does not mean "no session" —
    * it means whatever the previous request on that thread left behind, which may be another
    * user's, and a connector that resolves an OAuth token by session id would resolve theirs.</p>
    *
    * <p>The value is synthesized from the principal rather than taken from the HTTP session, which
    * is what the portal does: a wiz caller authenticates with a bearer token and carries no cookie
    * jar, so {@code getSession()} would mint a throwaway session per call — a per-request identity
    * where a per-user one is what a connector is looking for.</p>
    */
   private static void beginConnectorSession(Principal principal) {
      TabularUtil.setSessionId(
         principal == null ? null : SESSION_PREFIX + principal.getName());
   }

   /** Clears what {@link #beginConnectorSession(Principal)} set. Always from a {@code finally}. */
   private static void endConnectorSession() {
      TabularUtil.setSessionId(null);
   }

   /**
    * The name a data source is saved under.
    *
    * <p>Mirrors {@code WizDatabaseController.requireName}, with one rule the JDBC side has no use
    * for: a name carrying a slash is a path, and letting one through relocates the source into
    * another folder silently. Both cases otherwise fail somewhere downstream and come back as a 200
    * carrying {@code UNKNOWN}, which tells the user nothing about which field is wrong.</p>
    */
   private static String requireName(DataSourceDefinition definition) {
      String name = definition.getName() == null ? null : definition.getName().trim();

      if(name == null || name.isEmpty()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
      }

      if(name.indexOf('/') >= 0) {
         throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "name must not contain '/': " + name);
      }

      // Written back so the path reported to the caller and the one the save writes cannot
      // disagree over the trimming.
      definition.setName(name);

      return name;
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

   private static String lastSegment(String path) {
      int i = path == null ? -1 : path.lastIndexOf('/');

      return i < 0 ? path : path.substring(i + 1);
   }

   private static String emptyToNull(String value) {
      return value == null || value.isEmpty() || "/".equals(value) ? null : value;
   }

   /** Marks a connector session as this controller's, so it cannot collide with the portal's. */
   private static final String SESSION_PREFIX = "wiz:";

   private final DatasourcesService datasourcesService;
   private final SecurityEngine securityEngine;
   private final XRepository xrepository;
   private static final Logger LOG = LoggerFactory.getLogger(WizTabularController.class);
}
