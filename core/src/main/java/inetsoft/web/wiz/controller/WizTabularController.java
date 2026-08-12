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
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.WizTabularCreateRequest;
import inetsoft.web.wiz.service.UnsupportedDatasourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;

/**
 * Creation and editing of tabular data sources — MongoDB, REST, cloud storage and everything else
 * that is not a JDBC database — for the wiz portal.
 *
 * <p>Separate from {@code WizDatabaseController} because the two models do not meet anywhere.
 * A database is described by a strongly typed {@code DatabaseDefinition} that both ends understand
 * field by field; a tabular data source is described by a {@code TabularView} tree that the server
 * builds and the client merely interprets. Folding both into one class would give a reader of either
 * half the other half's vocabulary to wade through.</p>
 *
 * <p><b>The definition is passed through verbatim, and that is the design.</b> Unlike the JDBC
 * endpoints, which reduce StyleBI's model to a wiz-specific one, these endpoints hand
 * {@code DataSourceDefinition} and its {@code TabularView} to the client unchanged and take them
 * back unchanged. There is nothing to reduce: the tree <em>is</em> the form description, every node
 * of it is something the renderer needs, and each editor's current value lives inside it. Only the
 * listing catalogue and the save outcome get wiz-specific shapes, because neither of those is part
 * of the tree. Note that {@code TabularView.password} is a rendering hint rather than a mask —
 * StyleBI has no placeholder mechanism here and the real secret travels in the editor's value in
 * both directions — so nothing in this class, and nothing downstream of it, may log a definition.</p>
 *
 * <p><b>Refreshing is stateless.</b> {@code refreshTabularView} constructs a brand new data source
 * bean on every call and recomputes the tree from the definition it was handed; it stores nothing
 * and reads nothing back. The whole editing session therefore lives in the client's copy of the
 * definition, which is why {@code /tabular/refresh} can be a plain stateless forward with no session
 * affinity, and why it needs no path-level permission.</p>
 *
 * <p>Authorization is this controller's own responsibility. {@code DatasourcesService} does check
 * the folder permission when creating and the data source permission when updating, but only after
 * it has resolved folders and read the repository, and it does not check the read at all. The gates
 * belong at the entry point and are repeated here. Every denial leaves as a {@code SecurityException}
 * — the same type the annotated endpoints' aspect throws — which {@code WizControllerErrorHandler}
 * turns into a 403; this class must therefore never declare a local {@code @ExceptionHandler}, which
 * would take precedence over that advice and downgrade the denial to a 400.</p>
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
    * Lists the kinds of tabular data source that can be created.
    *
    * <p>Filtered to the tabular ones. {@code DataSourceListingService} also offers the JDBC
    * databases, which the wiz portal creates through {@code /databases} instead: a database listing
    * has no meaningful {@code TabularView}, so a card for it would open an empty form that saved an
    * unusable connection.</p>
    *
    * @param principal the current user, whose locale decides the display text.
    *
    * @return the listings and the categories they fall into, keyed by raw identifiers — unlike the
    *         native selection view, which emits only the translated category and so leaves a client
    *         nothing stable to group by. The translated text is carried alongside rather than
    *         instead.
    */
   @GetMapping(value = "/tabular/listings", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizTabularListings getTabularListings(Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      List<WizTabularListing> listings = new ArrayList<>();

      for(DataSourceListing listing : DataSourceListingService.getAllDataSourceListings(true)) {
         if(listing == null || !isTabularListing(listing)) {
            continue;
         }

         String category = listing.getCategory();
         String[] keywords = listing.getKeywords();

         // Both spellings of the category travel: the raw one because it is the only value that is
         // the same in every locale and can therefore be grouped and translated on, and the
         // translated one because the set of categories is open — a plugin ships its own — so no
         // client-side table can be complete and something has to be displayable regardless.
         listings.add(new WizTabularListing(
            listing.getName(), listing.getDisplayName(), category,
            category == null ? null : catalog.getString(category), listing.getIcon(),
            keywords == null ? List.of() : Arrays.asList(keywords)));
      }

      listings.sort(Comparator.comparing(listing -> Objects.toString(listing.name(), "")));

      List<String> categories = listings.stream()
         .map(WizTabularListing::category)
         .filter(Objects::nonNull)
         .distinct()
         .sorted()
         .toList();

      return new WizTabularListings(listings, categories);
   }

   /**
    * Returns the blank definition a new data source of one listed kind starts from.
    *
    * <p>Nothing is stored by this call: the listing's template data source is constructed, laid out
    * and thrown away, so it needs no permission of its own beyond being signed in. The caller still
    * has to hold the folder permission to save the result.</p>
    *
    * @param name      the listing identifier, i.e. {@code WizTabularListing.name}. The listing's
    *                  display name is accepted too, since that is the only key the native lookup
    *                  knows and it is what a client copying the native contract would send.
    * @param principal the current user.
    *
    * @return the template definition, tabular view included.
    *
    * @throws UnsupportedDatasourceException if the listing describes a database rather than a
    *                                        tabular source — it has no form to render here.
    */
   @GetMapping(value = "/tabular/listing", produces = MediaType.APPLICATION_JSON_VALUE)
   public DataSourceDefinition getTabularListing(@RequestParam("name") String name,
                                                 Principal principal)
      throws Exception
   {
      String listingName = requireParam(name, "name");
      DataSourceListing listing = findListing(listingName);

      if(listing == null) {
         throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Unknown data source listing: " + listingName);
      }

      if(!isTabularListing(listing)) {
         throw new UnsupportedDatasourceException(listingName, listingTemplateType(listing));
      }

      bindTabularSession(principal);

      // Keyed by display name, which is what getDataSourceFromListing resolves. findListing above
      // has already established that a listing answers to it.
      return datasourcesService.getDataSourceFromListing(listing.getDisplayName());
   }

   /**
    * Reads an existing tabular data source's definition.
    *
    * <p>Gated on WRITE rather than READ, for the same reason the JDBC read is: the response is the
    * connection's configuration, and for a tabular source that includes every credential it holds in
    * clear text, since {@code TabularView} has no mask. {@code DatasourcesService} does not check
    * this itself — it consults the principal only to decide the {@code deletable} flag.</p>
    *
    * @param path      the data source's full repository path.
    * @param principal the current user.
    *
    * @return the definition, passed through verbatim.
    *
    * @throws UnsupportedDatasourceException if the path names a JDBC database or a cube, which have
    *                                        no tabular form and are edited elsewhere.
    */
   @GetMapping(value = "/tabular/definition", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public DataSourceDefinition getTabularDefinition(
      @PermissionPath @RequestParam("path") String path, Principal principal)
      throws Exception
   {
      String source = requireParam(path, "path");
      requireTabularDataSource(source);
      bindTabularSession(principal);

      return datasourcesService.getDataSourceDefinition(source, principal);
   }

   /**
    * Recomputes a definition's tabular view from the values it carries.
    *
    * <p>This is the heart of the editing protocol: changing an editor that other editors depend on
    * sends the whole definition here, and the server answers with a tree whose tags, labels,
    * visibility and enablement have been recalculated.</p>
    *
    * <p><b>Deliberately no path-level permission.</b> There is no path — the request names no stored
    * resource, and the service reaches none. It constructs a fresh data source bean from the type in
    * the body, applies the values the caller themselves supplied, and hands the result straight back;
    * nothing is read from the repository and nothing is written to it. Requiring a permission here
    * would mean inventing a resource to check it against. Being signed in is enough, and that
    * {@code WizServiceAuthenticationFilter} already guarantees. Do not "fix" this by adding a
    * gate.</p>
    *
    * @param definition the definition to recompute, passed through verbatim in both directions.
    * @param principal  the current user.
    *
    * @return the same definition with its tabular view refreshed, its sequence number preserved so a
    *         client that has since typed again can discard this answer as stale.
    */
   @PostMapping(value = "/tabular/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
   public DataSourceDefinition refreshTabularView(@RequestBody DataSourceDefinition definition,
                                                  Principal principal)
   {
      requireType(definition);
      bindTabularSession(principal);

      int sequenceNumber = definition.getSequenceNumber();
      DataSourceDefinition refreshed = datasourcesService.refreshTabularView(definition);
      refreshed.setSequenceNumber(sequenceNumber);

      // The native endpoint also drains CoreTool.getUserMessage() into a websocket notification.
      // The wiz portal has no such channel, and a message raised while recomputing a form is not
      // worth inventing one for; it is left in place rather than discarded here.
      return refreshed;
   }

   /**
    * Creates a tabular data source in a folder.
    *
    * @param request   the parent folder and the new data source.
    * @param principal the current user.
    *
    * @return the saved path, or the reason the save was rejected.
    */
   @PostMapping(value = "/tabular/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizTabularSaveResult createTabularDataSource(@RequestBody WizTabularCreateRequest request,
                                                       Principal principal)
      throws Exception
   {
      DataSourceDefinition definition = request == null ? null : request.definition();
      String name = requireName(definition);
      requireType(definition);
      String parentPath = normalizeFolder(request.parentPath());

      requireCreatePermission(parentPath, principal);

      String path = parentPath.isEmpty() ? name : parentPath + "/" + name;

      // Checked here rather than left to the save path, which reports it as a translated sentence
      // this class would then have to recognize by its wording. The service's own check is also
      // narrower than it looks: it compares the bare name against every data source name, so it
      // only ever catches a collision at the root.
      if(datasourcesService.checkDuplicate(path)) {
         return new WizTabularSaveResult(false, "DUPLICATE_NAME", null);
      }

      // The definition's own parentPath is overwritten, never trusted: the permission was checked
      // against the request's folder, and a definition claiming a different one would be saved
      // somewhere the caller was never authorized for.
      definition.setParentPath(parentPath);
      definition.setOldName(null);
      bindTabularSession(principal);

      try {
         datasourcesService.createNewDataSource(definition, false, principal);
      }
      catch(MessageException ex) {
         return toSaveResult(ex, path);
      }

      return new WizTabularSaveResult(true, null, path);
   }

   /**
    * Updates an existing tabular data source.
    *
    * <p>Gated the same way as the read, and the gate has to be here rather than only inside the
    * service: {@code updateDataSource} derives the data source it acts on from the definition's own
    * {@code parentPath}, so a check made against the request's path would not be a check against
    * what actually gets written. Hence the path is also the sole source of the parent folder and of
    * the name being replaced — both are overwritten below and neither is taken from the client.</p>
    *
    * @param path       the data source's current full repository path.
    * @param definition the new definition, passed through verbatim.
    * @param principal  the current user.
    *
    * @return the saved path, which differs from {@code path} when the connection was renamed, or the
    *         reason the save was rejected.
    *
    * @throws UnsupportedDatasourceException if the path names a JDBC database or a cube.
    */
   @PostMapping(value = "/tabular/update", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public WizTabularSaveResult updateTabularDataSource(
      @PermissionPath @RequestParam("path") String path,
      @RequestBody DataSourceDefinition definition,
      Principal principal)
      throws Exception
   {
      String source = requireParam(path, "path");
      String name = requireName(definition);
      requireType(definition);
      requireTabularDataSource(source);

      String oldName = lastSegment(source);
      String parentPath = parentFolder(source);
      DataSourceDefinition current;

      bindTabularSession(principal);

      try {
         current = datasourcesService.getDataSourceDefinition(source, principal);
      }
      catch(FileNotFoundException ex) {
         return new WizTabularSaveResult(false, "DATASOURCE_LOST", null);
      }

      // A rename is a delete of the old name as far as the repository is concerned, and the service
      // demands DELETE for it. Checked here so the denial arrives as a 403 rather than as the
      // translated MessageException the service raises, which would land in the save result as
      // UNKNOWN and read to the user like a validation failure.
      if(!name.equals(oldName)) {
         requireDataSourcePermission(source, ResourceAction.DELETE, principal);
      }

      definition.setParentPath(parentPath);
      definition.setOldName(oldName);
      carryOverAdditionalConnections(current, definition);

      try {
         datasourcesService.updateDataSource(oldName, definition, principal);
      }
      catch(MessageException ex) {
         return toSaveResult(ex, source);
      }

      return new WizTabularSaveResult(
         true, null, parentPath.isEmpty() ? name : parentPath + "/" + name);
   }

   /**
    * Reports whether a data source path is already taken.
    *
    * <p>No permission is required, matching the native endpoint. The answer is derivable anyway by
    * anyone allowed to attempt a create — the save refuses a duplicate — and demanding a permission
    * on a path whose whole point is that it should not exist yet has nothing to check against.</p>
    *
    * @param name the full path the new data source would occupy, i.e. the parent folder and the name
    *             joined by a slash, or just the name at the root.
    *
    * @return whether something already lives there.
    */
   @GetMapping(value = "/tabular/check-duplicate", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizTabularDuplicateResult checkDuplicate(@RequestParam("name") String name)
      throws Exception
   {
      return new WizTabularDuplicateResult(
         datasourcesService.checkDuplicate(requireParam(name, "name")));
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
    * Fails unless the caller may create a data source in a folder.
    *
    * <p>The same rule {@code WizDatabaseController} applies, because it is the same rule StyleBI
    * applies: WRITE on the parent folder, or, at the root only, the standalone
    * {@code CREATE_DATA_SOURCE} grant that lets a user own data sources without holding the root
    * folder. This is a guard in front of {@code createNewDataSource}, which enforces the real
    * thing.</p>
    */
   private void requireCreatePermission(String parentPath, Principal principal) throws Exception {
      boolean root = parentPath.isEmpty();
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

   /**
    * Rejects a path that does not hold a tabular data source.
    *
    * <p>A JDBC database or a cube would otherwise be opened in the tabular editor, where
    * {@code LayoutCreator} finds no annotated properties and produces an empty form. The read would
    * hand back a definition with nothing in it, and a save of that definition would rename and
    * re-describe a database through a form that never showed its settings.</p>
    */
   private void requireTabularDataSource(String path) throws Exception {
      XDataSource dataSource = xrepository.getDataSource(path);

      if(dataSource == null) {
         throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Data source not found: " + path);
      }

      if(!(dataSource instanceof TabularDataSource)) {
         throw new UnsupportedDatasourceException(path, dataSource.getType());
      }
   }

   /**
    * Binds the thread's tabular session id before anything can refresh a view.
    *
    * <p>{@code TabularUtil} keeps this in a thread local and passes it to the {@code METHOD} button
    * handlers a data source plugin declares — {@code method.invoke(bean, sessionId.get())}. It is
    * not session state: nothing else reads it, and refreshing a view is stateless without it. But it
    * is never cleared either, so leaving it alone is not the same as leaving it empty. A worker
    * thread that last served a native portal request still holds <em>that</em> request's session id,
    * and a button handler using it as a per-user key would then act under another user's identity.
    * Setting it on every entry point closes that.</p>
    *
    * <p>The value is derived from the caller rather than from {@code request.getSession()}, which is
    * what the native controllers use. wiz-services calls with a bearer token and keeps no cookie
    * jar, so there is no session to find and {@code getSession()} would mint a throwaway one per
    * request — a new id every call, and a session object in the store that nothing ever reuses. A
    * per-principal id is both stable across the calls of one editing session and distinct between
    * users, which is what a handler keying off it actually wants.</p>
    */
   private static void bindTabularSession(Principal principal) {
      TabularUtil.setSessionId(
         principal == null ? WIZ_SESSION_PREFIX : WIZ_SESSION_PREFIX + principal.getName());
   }

   /**
    * Carries the stored additional connections forward when the client did not send any.
    *
    * <p>The save path treats the definition's list as the complete one and removes every child not
    * in it, so a null list deletes them all. Additional connections are out of scope for the wiz
    * editor, which means it is exactly the client that never sends them that would silently destroy
    * them. Null therefore means "not edited" and is filled in from the stored definition; an empty
    * list still means "remove them all", which is the only way a client that does edit them can say
    * so.</p>
    */
   private static void carryOverAdditionalConnections(DataSourceDefinition current,
                                                      DataSourceDefinition updated)
   {
      if(current != null && updated.getAdditionalConnections() == null) {
         updated.setAdditionalConnections(current.getAdditionalConnections());
      }
   }

   /**
    * Turns a rejected save into a result the client can branch on.
    *
    * <p>The tabular save path signals a business failure by throwing {@code MessageException} with a
    * sentence already run through {@code Catalog}, so there is no status code to read — only the
    * wording, compared against the very same catalog lookups the service made. The comparison is
    * best effort by construction, which is why the cases a client is likely to hit are ruled out
    * before the save is attempted rather than recognized afterwards.</p>
    */
   private static WizTabularSaveResult toSaveResult(MessageException ex, String path) {
      Catalog catalog = Catalog.getCatalog();
      String message = ex.getMessage() == null ? "" : ex.getMessage();
      String reason;

      if(message.equals(catalog.getString("data.datasources.invalidParentFolder"))) {
         reason = "INVALID_FOLDER";
      }
      else if(message.equals(catalog.getString("data.datasources.saveDataSourceLost"))) {
         reason = "DATASOURCE_LOST";
      }
      else if(message.startsWith(catalog.getString("common.datasource.duplicateName"))) {
         reason = "DUPLICATE_NAME";
      }
      else {
         // Not swallowed: the client only ever sees UNKNOWN, so the original wording has to be
         // recoverable from the server log for anyone diagnosing it. An invalid name lands here.
         LOG.warn("Unrecognized data source save failure for {}: {}", path, message);
         reason = "UNKNOWN";
      }

      return new WizTabularSaveResult(false, reason, null);
   }

   /** The listing whose identifier — or, failing that, whose display name — is the given one. */
   private static DataSourceListing findListing(String name) {
      DataSourceListing byDisplayName = null;

      for(DataSourceListing listing : DataSourceListingService.getAllDataSourceListings(true)) {
         if(listing == null) {
            continue;
         }

         if(name.equals(listing.getName())) {
            return listing;
         }

         if(byDisplayName == null && name.equals(listing.getDisplayName())) {
            byDisplayName = listing;
         }
      }

      return byDisplayName;
   }

   private static boolean isTabularListing(DataSourceListing listing) {
      return listingTemplate(listing) instanceof TabularDataSource;
   }

   private static String listingTemplateType(DataSourceListing listing) {
      XDataSource template = listingTemplate(listing);

      return template == null ? null : template.getType();
   }

   /**
    * Instantiates a listing's template data source, or null if it cannot be instantiated.
    *
    * <p>The only way to tell a tabular listing from a database one, and the way the native selection
    * view tells them apart too. A listing whose plugin is half-installed must not take the whole
    * card wall down with it, so a failure drops that one card.</p>
    */
   private static XDataSource listingTemplate(DataSourceListing listing) {
      try {
         return listing.createDataSource();
      }
      catch(Throwable ex) {
         LOG.debug("Failed to create the template data source of listing {}: {}", listing.getName(),
                   ex.getMessage());
         return null;
      }
   }

   private static String requireParam(String value, String name) {
      if(value == null || value.isBlank()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required");
      }

      return value;
   }

   private static String requireName(DataSourceDefinition definition) {
      if(definition == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definition is required");
      }

      String name = definition.getName() == null ? null : definition.getName().trim();

      if(name == null || name.isEmpty()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
      }

      return name;
   }

   /**
    * Rejects a definition with no type.
    *
    * <p>Without it the service cannot look up a data source class, logs the failure and hands the
    * definition straight back unchanged — a refresh that appears to have worked and simply did
    * nothing, which is the hardest kind of failure to notice from the client.</p>
    */
   private static void requireType(DataSourceDefinition definition) {
      if(definition == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definition is required");
      }

      if(definition.getType() == null || definition.getType().isBlank()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
      }
   }

   private static String lastSegment(String path) {
      int index = path.lastIndexOf('/');

      return index < 0 ? path : path.substring(index + 1);
   }

   /**
    * The folder part of a path, as the empty string at the root.
    *
    * <p>Never null: the save path builds the stored name by concatenating this with a slash, so a
    * null would be written into the name as the four characters "null".</p>
    */
   private static String parentFolder(String path) {
      int index = path.lastIndexOf('/');

      return index <= 0 ? "" : path.substring(0, index);
   }

   /** Maps the several ways a client can spell "the root folder" onto the one the service takes. */
   private static String normalizeFolder(String path) {
      if(path == null || path.isBlank() || "/".equals(path)) {
         return "";
      }

      return path;
   }

   /**
    * Prefix of the synthetic tabular session id. Kept distinct from a servlet session id so that a
    * value seen in a plugin's logs is recognizably the wiz portal's.
    */
   private static final String WIZ_SESSION_PREFIX = "wiz:";

   private static final Logger LOG = LoggerFactory.getLogger(WizTabularController.class);

   private final DatasourcesService datasourcesService;
   private final SecurityEngine securityEngine;
   private final XRepository xrepository;
}
