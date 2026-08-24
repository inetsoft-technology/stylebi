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
import inetsoft.uql.tabular.LayoutCreator;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularEditor;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.TabularQuerySchema;
import inetsoft.uql.tabular.TabularSchemaExtractor;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.tabular.TabularView;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.portal.data.DataSourceDefinition;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.wiz.model.WizTabularBrowseResult;
import inetsoft.web.wiz.model.WizTabularListing;
import inetsoft.web.wiz.model.WizTabularListings;
import inetsoft.web.wiz.model.WizTabularSaveResult;
import inetsoft.web.wiz.model.WorksheetTableResponse;
import inetsoft.web.wiz.request.WizTabularBrowseRequest;
import inetsoft.web.wiz.request.WizTabularCreateRequest;
import inetsoft.web.wiz.request.WizTabularProbeCloseRequest;
import inetsoft.web.wiz.request.WizTabularProbeOpenRequest;
import inetsoft.web.wiz.request.WizTabularProbeTableRequest;
import inetsoft.web.wiz.service.UnsupportedDatasourceException;
import inetsoft.web.wiz.service.WorksheetTableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.nio.file.Files;
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
                               XRepository xrepository,
                               WorksheetTableService worksheetTableService)
   {
      this.datasourcesService = datasourcesService;
      this.securityEngine = securityEngine;
      this.xrepository = xrepository;
      this.worksheetTableService = worksheetTableService;
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
   /**
    * The parameters a data source's query takes, what each one means, and which of them apply when.
    *
    * <p>WHAT THIS ANSWERS THAT NOTHING ELSE DOES. A caller building a tabular table has to decide
    * what to put in {@code tabularSource.queryParams}, and until now there was nowhere to learn it.
    * {@code /tabular/definition} describes the DATA SOURCE's form — the connection — not the
    * query's. And the structure that does describe a query, {@code TabularView}, is a form layout:
    * two thirds of every node is row, column, span and padding, and nothing in it says that an
    * offset parameter is read under one pagination strategy and ignored under another.</p>
    *
    * <p>THE DEPENDENCY MATRIX IS THE PART WORTH READING. Most of a connector's parameters are
    * conditional, and a parameter that does not apply is not refused — it is stored on the query
    * and never looked at, so the request goes out as though it had never been given. The matrix
    * says which choice turns on which parameters, so that can be got right before the call rather
    * than diagnosed after it.</p>
    *
    * <p>DERIVED, NEVER STORED. Every field comes from the connector's own {@code @Property} and
    * {@code @View} declarations, so a connector that adds a parameter is described correctly with
    * no change here. Two things it does not carry: what each value of an enumerated parameter
    * actually does, which lives in the runner rather than in any annotation, and which parameters a
    * given choice makes mandatory, which the annotations have no way to express.</p>
    *
    * <p>Gated on READ, unlike {@code /tabular/definition} next to it. That one returns stored
    * credentials and so demands WRITE; this returns a description of the connector's shape and no
    * value the user configured.</p>
    *
    * @param path      the data source's full repository path.
    * @param principal the current user.
    *
    * @return the parameter contract for that data source's query.
    */
   @GetMapping(value = "/tabular/query-schema", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.READ
      )
   })
   public TabularQuerySchema getQuerySchema(
      @PermissionPath @RequestParam("path") String path, Principal principal) throws Exception
   {
      requireTabularDataSource(path);

      // Extraction runs the connector's own visibility methods, which is the same capability
      // /tabular/refresh gates on. Those methods are the connector's code and this decides how many
      // times they are invoked, so the session is bound the way every other such call binds it.
      beginConnectorSession(principal);

      try {
         // Built through createQuery rather than from the type alone, so the query carries the data
         // source it will actually run against. A visibility condition is free to read it — a
         // connector can offer different parameters for different accounts — and a schema extracted
         // from a datasource-less query would then describe a query nobody is going to run.
         TabularQuery query = TabularUtil.createQuery(path);

         if(query == null) {
            throw new ResponseStatusException(
               HttpStatus.BAD_REQUEST,
               "Could not create a query for '" + path + "' — its connector plugin may not be loaded.");
         }

         XDataSource dataSource = xrepository.getDataSource(path);
         TabularQuerySchema schema =
            new TabularSchemaExtractor().extract(query, dataSource == null ? null : dataSource.getType());

         if(schema == null) {
            throw new ResponseStatusException(
               HttpStatus.BAD_REQUEST, "No query parameter contract is available for '" + path + "'.");
         }

         return schema;
      }
      finally {
         endConnectorSession();
      }
   }

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

   // ─── Browsing a file-based connector, and probing what a target holds ─────────────────────

   /**
    * List one folder of a file-based connector.
    *
    * <p>The wiz twin of {@code TabularQueryDialogController.browse}, and deliberately a separate
    * method rather than a call into it. What is shared is the mechanism — a query created with
    * {@code TabularUtil.createQuery}, its layout, and the {@code relativeTo}/{@code foldersOnly}/
    * {@code acceptTypes} editor properties the connector declares on its file property. What is not
    * shared is the way in: {@code TabularQueryDialogController} extends {@code WorksheetController}
    * and authorizes through a composer session, which a wiz caller carrying a bearer token does not
    * have. So the gate here is the one every other method on this class uses.</p>
    *
    * <p>No {@code runtimeId}: the browse rules come off a freshly created query bean, which is why
    * the composer's own version does not need one either.</p>
    *
    * <p>Sheets of a workbook are NOT expanded here. Listing them would mean opening every workbook
    * in the tree just to answer "what is here", and the answer is needed only for the files that go
    * on to be probed — {@code probe/table} names them when it needs them.</p>
    *
    * @param request   which data source, which folder, and how much of it.
    * @param principal the current user.
    *
    * @return the folder's contents, with paths relative to the connector's root folder.
    */
   @PostMapping(value = "/tabular/browse", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizTabularBrowseResult browseTabularFiles(@RequestBody WizTabularBrowseRequest request,
                                                    Principal principal)
      throws Exception
   {
      String datasource = request == null ? null : emptyToNull(request.datasource());

      if(datasource == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datasource is required");
      }

      // READ, not WRITE: this reads what the data source points at, it does not reconfigure it.
      // The same action buildTable checks before a tabular table may reach a connector at all.
      requireDataSourcePermission(datasource, ResourceAction.READ, principal);
      requireTabularDataSource(datasource);

      String path = normalizeBrowsePath(request.path());

      // callEditorMethods below runs the connector's own editor methods, which is exactly the reach
      // refreshTabularView is bracketed for — TabularUtil.sessionId is a ThreadLocal nothing clears,
      // so on a pooled thread "leave it alone" means "inherit the previous request's", possibly
      // another user's.
      beginConnectorSession(principal);

      try {
         return browse(datasource, path, request);
      }
      finally {
         endConnectorSession();
      }
   }

   /**
    * Open a temporary worksheet to probe a data source's targets with.
    *
    * <p>One per annotation run, not one per file. The runtime never touches
    * {@code AssetRepository} — nothing is persisted at any point — so the only thing it costs is a
    * live session, and the only obligation it creates is {@code probe/close}.</p>
    *
    * @return a single-key object carrying the {@code runtimeId} to pass to the other two calls.
    */
   @PostMapping(value = "/tabular/probe/open", produces = MediaType.APPLICATION_JSON_VALUE)
   public Map<String, String> openProbeWorksheet(
      @RequestBody(required = false) WizTabularProbeOpenRequest request, Principal principal)
      throws Exception
   {
      String datasource = request == null ? null : emptyToNull(request.datasource());

      // Checked here rather than left to the first probe: a caller that may not read the data
      // source should not get a runtime to try it with, and the probe's own gate would only say so
      // after a session had been opened that the caller then has to remember to close.
      if(datasource != null) {
         requireDataSourcePermission(datasource, ResourceAction.READ, principal);
      }

      return Map.of("runtimeId", worksheetTableService.openProbeWorksheet(principal));
   }

   /**
    * Build one target in the probe worksheet and report its columns and sample rows.
    *
    * <p>Answers in the same {@code WorksheetTableResponse} {@code POST /api/wiz/ws/table} does,
    * including its {@code success}/{@code errorMessage} pair: a failure here is a 200 carrying the
    * reason, not a 4xx, because the caller is a loop over a directory and one unreadable file must
    * not end the walk. It is also how a multi-sheet workbook reports its sheets — the build refuses
    * to guess which one was meant and lists them in the message.</p>
    */
   @PostMapping(value = "/tabular/probe/table", produces = MediaType.APPLICATION_JSON_VALUE)
   public WorksheetTableResponse probeTable(@RequestBody WizTabularProbeTableRequest request,
                                            Principal principal)
      throws Exception
   {
      if(request == null || request.table() == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "table is required");
      }

      // The datasource READ gate itself lives in the service, on the same line createTables checks
      // it, so the probe and the real build cannot come to different answers about who may reach a
      // connector.
      return worksheetTableService.probeTable(request.runtimeId(), request.table(), principal);
   }

   /** Release the probe runtime. Nothing was persisted, so nothing is left behind to clean up. */
   @PostMapping("/tabular/probe/close")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   public void closeProbeWorksheet(@RequestBody WizTabularProbeCloseRequest request,
                                   Principal principal)
      throws Exception
   {
      if(request == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "runtimeId is required");
      }

      worksheetTableService.closeProbeWorksheet(request.runtimeId(), principal);
   }

   /**
    * The browse itself, with the connector session already bound.
    *
    * <p>Reads the browse rules off the file property's editor the way the composer dialog does:
    * {@code relativeTo} is the connector's root folder (a method on the query bean, which is why
    * the layout has to be built and its editor methods run first), {@code foldersOnly} hides files,
    * and {@code acceptTypes} is the connector's own extension whitelist — {@code .txt,.csv,.xls,
    * .xlsx} for ServerFile. Reusing the connector's list rather than hard-coding one is what keeps
    * this honest when a connector adds a format.</p>
    */
   private WizTabularBrowseResult browse(String datasource, String path,
                                         WizTabularBrowseRequest request)
   {
      TabularQuery query = TabularUtil.createQuery(datasource);

      if(query == null) {
         throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY, "Could not create a query for data source \"" +
            datasource + "\" — its connector plugin may not be loaded.");
      }

      TabularView view = new LayoutCreator().createLayout(query);
      TabularUtil.callEditorMethods(view.getViews(), query);

      TabularView fileView = findFileView(view, request.property());

      if(fileView == null) {
         throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY, "Data source \"" + datasource + "\" has no browsable " +
            "file property" + (request.property() == null ? "" : " named \"" + request.property() +
            "\"") + ", so there is nothing to browse. It is not a file-based connector.");
      }

      TabularEditor editor = fileView.getEditor();
      String[] names = editor.getEditorPropertyNames();
      String[] values = editor.getEditorPropertyValues();
      String relativeTo = null;
      boolean foldersOnly = false;
      List<String> acceptTypes = new ArrayList<>();

      if(names != null) {
         for(int i = 0; i < names.length; i++) {
            if("relativeTo".equals(names[i])) {
               relativeTo = values[i] == null || values[i].isEmpty() ? null : values[i];
            }
            else if("foldersOnly".equals(names[i])) {
               foldersOnly = Boolean.parseBoolean(values[i]);
            }
            else if("acceptTypes".equals(names[i]) && !request.all() && values[i] != null) {
               acceptTypes.addAll(Arrays.asList(values[i].split(",")));
            }
         }
      }

      // Unlike the composer's version, a missing root is refused rather than answered with a listing
      // of the server's drive roots. The dialog can do that because a human is configuring the
      // source and has to find one; here the root IS the grant, and reading outside it is the one
      // thing this endpoint must not do.
      if(relativeTo == null) {
         throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY, "Data source \"" + datasource + "\" has no root " +
            "folder configured, so there is nothing to browse.");
      }

      List<WizTabularBrowseResult.WizTabularBrowseEntry> entries = new ArrayList<>();
      boolean truncated = collect(new File(relativeTo), path, foldersOnly, acceptTypes,
                                  request.recursive(), entries);

      // Folders first, then files, each alphabetical — a stable order, so re-browsing an unchanged
      // directory produces an unchanged answer.
      entries.sort(Comparator.comparing(
            WizTabularBrowseResult.WizTabularBrowseEntry::folder, Comparator.reverseOrder())
         .thenComparing(WizTabularBrowseResult.WizTabularBrowseEntry::path,
                        String.CASE_INSENSITIVE_ORDER));

      return new WizTabularBrowseResult(datasource, path, entries, truncated);
   }

   /**
    * Collect one folder's entries, optionally walking into sub-folders.
    *
    * <p>Capped rather than unbounded. A recursive walk is what an annotation pass wants — the
    * alternative is one request per directory — but a root pointed at a large tree would otherwise
    * build a response nobody can use, so the walk stops and says it stopped.</p>
    *
    * @return true when the cap stopped the walk short.
    */
   private boolean collect(File root, String path, boolean foldersOnly, List<String> acceptTypes,
                           boolean recursive,
                           List<WizTabularBrowseResult.WizTabularBrowseEntry> entries)
   {
      File folder = path.isEmpty() ? root : new File(root, path);
      File[] children = folder.listFiles();

      if(children == null) {
         return false;
      }

      for(File child : children) {
         if(entries.size() >= MAX_BROWSE_ENTRIES) {
            return true;
         }

         if(child.isHidden() || !Files.isReadable(child.toPath())) {
            continue;
         }

         String childPath = path.isEmpty() ? child.getName() : path + "/" + child.getName();

         if(child.isDirectory()) {
            entries.add(new WizTabularBrowseResult.WizTabularBrowseEntry(
               childPath, child.getName(), true));

            if(recursive && collect(root, childPath, foldersOnly, acceptTypes, true, entries)) {
               return true;
            }
         }
         else if(!foldersOnly && accepts(acceptTypes, child.getName())) {
            entries.add(new WizTabularBrowseResult.WizTabularBrowseEntry(
               childPath, child.getName(), false));
         }
      }

      return false;
   }

   /** Whether a file name matches the connector's own extension whitelist; empty accepts all. */
   private static boolean accepts(List<String> acceptTypes, String name) {
      if(acceptTypes.isEmpty()) {
         return true;
      }

      for(String type : acceptTypes) {
         if(!type.isBlank() && name.toLowerCase().endsWith(type.trim().toLowerCase())) {
            return true;
         }
      }

      return false;
   }

   /**
    * The view of the named property, or — when none was named — the connector's file property.
    *
    * <p>Resolved by editor TYPE rather than by the name {@code "fileFolder"}, so a caller that does
    * not know the connector does not have to guess one, and the next file-based connector needs no
    * change here. {@code TabularUtil.getEditorType} keys {@code FILE} on a {@code java.io.File}
    * property, which is the same signal {@code WorksheetTableService.fileTargetProperty} resolves
    * the build target with — the two have to agree, or a file could be browsed under one property
    * and bound through another.</p>
    */
   private static TabularView findFileView(TabularView view, String property) {
      if(view.getEditor() != null && view.getValue() != null) {
         boolean match = property == null || property.isEmpty()
            ? view.getEditor().getType() == TabularEditor.Type.FILE
            : property.equals(view.getValue());

         if(match) {
            return view;
         }
      }

      for(TabularView child : view.getViews()) {
         TabularView found = findFileView(child, property);

         if(found != null) {
            return found;
         }
      }

      return null;
   }

   /**
    * The folder to list, relative to the connector's root and provably inside it.
    *
    * <p>The same rule {@code WorksheetTableService.resolveTargetFile} applies to a build target, and
    * for the same reason: the root folder is the whole of what a {@code ServerFileDataSource}
    * grants, so an absolute path or a {@code ".."} segment is not a path to resolve leniently — it
    * is a request to read outside the grant.</p>
    */
   private static String normalizeBrowsePath(String path) {
      if(path == null || path.isEmpty() || "/".equals(path)) {
         return "";
      }

      String normalized = path.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");

      if(new File(normalized).isAbsolute() || normalized.matches("^[A-Za-z]:.*")) {
         throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "path must be relative to the data source's root folder: \"" + path + "\"");
      }

      for(String segment : normalized.split("/")) {
         if("..".equals(segment)) {
            throw new ResponseStatusException(
               HttpStatus.BAD_REQUEST, "path must not contain '..': \"" + path + "\"");
         }
      }

      return normalized;
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

   /**
    * Ceiling on a single browse answer. A recursive walk of a root pointed at a large tree would
    * otherwise build a response no caller can use; the walk stops and reports that it stopped, so
    * the remainder stays reachable by browsing sub-folders individually.
    */
   private static final int MAX_BROWSE_ENTRIES = 2000;

   private final DatasourcesService datasourcesService;
   private final SecurityEngine securityEngine;
   private final XRepository xrepository;
   private final WorksheetTableService worksheetTableService;
   private static final Logger LOG = LoggerFactory.getLogger(WizTabularController.class);
}
