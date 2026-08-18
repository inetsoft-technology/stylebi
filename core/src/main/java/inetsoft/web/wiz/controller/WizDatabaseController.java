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

import inetsoft.report.internal.Util;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.tabular.ScriptedQuery;
import inetsoft.uql.tabular.SelectableTabularQuery;
import inetsoft.uql.util.Config;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.database.*;
import inetsoft.web.admin.content.database.types.*;
import inetsoft.web.admin.content.repository.DataSourceSettingsModel;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.data.CheckDuplicateResponse;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DataSourceConnectionStatusRequest;
import inetsoft.web.portal.data.DataSourceInfo;
import inetsoft.web.portal.data.DataSourceStatus;
import inetsoft.web.portal.data.ImmutableDataSourceConnectionStatusRequest;
import inetsoft.web.portal.data.PortalDataType;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.*;
import inetsoft.web.wiz.service.EndpointCatalogReader;
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
 * Repository browsing and JDBC database editing for the wiz portal's data source management pages.
 *
 * <p>Lives under {@code /api/wiz} rather than proxying the native {@code /api/data/**} endpoints
 * because the two filters that gate those prefixes disagree: {@code CSRFFilter} exempts only
 * {@code /api/wiz/**}, while {@code AbstractSecurityFilter} authenticates a wiz caller by its
 * cookie or its path. Calling a native endpoint with a wiz token therefore succeeds for GET and
 * fails with 403 for every POST — an asymmetry that is very hard to diagnose from the client
 * side.</p>
 *
 * <p>All work is delegated to the portal services, so permission filtering, folder resolution and
 * connection probing behave exactly as they do in the native portal. Only the response shape
 * differs: raw enum names and epoch millis instead of {@code Catalog}-translated labels and
 * server-formatted dates, because the wiz portal does its own i18n.</p>
 *
 * <p>The database editing endpoints likewise delegate to {@code DatabaseDatasourcesService}, the
 * same service the native editor and the EM use, but they absorb three contracts that service
 * inherits from its Angular caller and that no typed client can be expected to honour. The native
 * editor treats the settings model as an opaque envelope it receives and returns untouched; a client
 * that rebuilds the payload from an interface definition would drop exactly the fields that are not
 * in the interface. So: {@code oldName} is filled in here from the request path rather than trusted
 * from the client (without it a save cannot recover the stored password and overwrites it with an
 * empty one), a null password is translated here into the mask constant that triggers that recovery,
 * and {@code permissions} is always left null so a save can never write permissions as a side
 * effect.</p>
 *
 * <p>Authorization is this controller's own responsibility, not the services'. {@code
 * DataSourceBrowserService} filters what it lists by READ, but {@code DatabaseDatasourcesService}
 * and {@code DataSourceStatusService} do not gate their reads at all — the native controllers gate
 * them with {@code @Secured} before delegating, and the equivalent gates are repeated here. Where
 * the rule is conditional and an annotation cannot express it — the connection test, which only
 * needs a permission when it names an existing database — {@code SecurityEngine} is consulted
 * directly. Every denial leaves as a {@code SecurityException}, which
 * {@code WizControllerErrorHandler} turns into a 403; this class must therefore never declare a
 * local {@code @ExceptionHandler}, which would take precedence over that advice.</p>
 */
@RestController
@RequestMapping("/api/wiz")
public class WizDatabaseController {
   public WizDatabaseController(DataSourceBrowserService dataSourceBrowserService,
                                DataSourceStatusService dataSourceStatusService,
                                DatabaseDatasourcesService databaseDatasourcesService,
                                DatabaseTypeService databaseTypeService,
                                SecurityEngine securityEngine,
                                Config uqlConfig,
                                XRepository xrepository,
                                EndpointCatalogReader endpointCatalogReader)
   {
      this.dataSourceBrowserService = dataSourceBrowserService;
      this.dataSourceStatusService = dataSourceStatusService;
      this.databaseDatasourcesService = databaseDatasourcesService;
      this.databaseTypeService = databaseTypeService;
      this.securityEngine = securityEngine;
      this.uqlConfig = uqlConfig;
      this.xrepository = xrepository;
      this.endpointCatalogReader = endpointCatalogReader;
   }

   /**
    * Lists one folder of the data source repository.
    *
    * @param path      the folder to list. Omitted, empty or {@code "/"} lists the root.
    * @param principal the current user; entries they cannot read are omitted by the service.
    *
    * @return the folder contents plus the breadcrumb trail leading to it.
    */
   @GetMapping(value = "/datasources/browser", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizDatasourceBrowserModel getDatasourceBrowser(
      @RequestParam(value = "path", required = false) String path,
      Principal principal)
      throws Exception
   {
      String folder = normalizePath(path);

      // root=false always: root=true is the move-dialog mode, which returns the single synthetic
      // root folder entry instead of the folder contents.
      List<DataSourceInfo> infos =
         dataSourceBrowserService.getDataSources(folder, false, null, principal);

      // home=true suppresses the synthetic "/" root crumb, whose label would come pre-translated
      // from the server. The client renders its own root label.
      List<DataSourceInfo> crumbs = dataSourceBrowserService.getBreadcrumbs(folder, true, principal);
      List<String> breadcrumb = new ArrayList<>();

      for(DataSourceInfo crumb : crumbs) {
         if(crumb != null) {
            breadcrumb.add(crumb.path());
         }
      }

      return new WizDatasourceBrowserModel(toEntries(infos), breadcrumb, folder == null);
   }

   /**
    * Searches data source and folder names recursively below a folder.
    *
    * @param request   the search scope and the substring to match.
    * @param principal the current user.
    *
    * @return the matching entries, each carrying its full path, best match first. Empty when no
    *         query was supplied — an empty query would otherwise match the entire repository.
    */
   @PostMapping(value = "/datasources/search", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<WizDatasourceEntry> searchDatasources(
      @RequestBody WizDatasourceSearchRequest request,
      Principal principal)
      throws Exception
   {
      String query = request == null ? null : request.query();

      if(query == null || query.isBlank()) {
         return List.of();
      }

      String folder = normalizePath(request.path());

      return toEntries(dataSourceBrowserService.getSearchDataSources(folder, query, principal));
   }

   /**
    * Reports the recorded connection state of several data sources at once.
    *
    * <p>Never re-tests the connections: the underlying service would otherwise open a connection
    * to every listed data source and write the outcome back to the repository. Browsing a folder
    * must not have that side effect, so this reads the stored status only.</p>
    *
    * <p>{@code DataSourceStatusService} performs no permission check of its own, so the requested
    * paths are filtered to those the caller can read before it is handed anything. A path the
    * caller cannot read is dropped rather than rejected: the client pairs results with requests by
    * path, so a short answer degrades to "no status known" instead of failing the whole listing,
    * and a probe learns nothing it did not already supply.</p>
    *
    * @param request   the data source paths to report on.
    * @param principal the current user.
    *
    * @return one status per readable requested path, in the order requested.
    */
   @PostMapping(value = "/datasources/statuses", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<WizDatasourceStatus> getDatasourceStatuses(
      @RequestBody WizDatasourceStatusRequest request,
      Principal principal)
      throws Exception
   {
      List<String> requested = request == null ? null : request.paths();

      if(requested == null || requested.isEmpty()) {
         return List.of();
      }

      List<String> paths = new ArrayList<>();

      for(String path : requested) {
         if(path != null && securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, path, ResourceAction.READ))
         {
            paths.add(path);
         }
      }

      if(paths.isEmpty()) {
         return List.of();
      }

      DataSourceConnectionStatusRequest statusRequest =
         ImmutableDataSourceConnectionStatusRequest.builder()
            .paths(paths)
            .updateStatus(false)
            .timeZone(TimeZone.getDefault().getID())
            .build();

      List<DataSourceStatus> statuses =
         dataSourceStatusService.getDataSourceConnectionStatuses(statusRequest, principal);
      List<WizDatasourceStatus> result = new ArrayList<>();

      // The service answers positionally and drops the path, so re-attach it by input order. A
      // data source that has never been tested yields a null status rather than a missing element.
      for(int i = 0; i < paths.size(); i++) {
         DataSourceStatus status = i < statuses.size() ? statuses.get(i) : null;
         result.add(new WizDatasourceStatus(paths.get(i), status != null && status.connected(),
                                            status == null ? null : status.message()));
      }

      return result;
   }

   /**
    * The endpoint catalogues for a set of data source types.
    *
    * <p>POST rather than GET because the type list is unbounded, matching {@code
    * /datasources/search} and {@code /datasources/statuses} in this same controller.</p>
    *
    * <p>No permission gate, but that reasoning only covers half the response. {@code catalogs} is a
    * property of the CONNECTOR — identical for every customer, compiled into the connector jar,
    * carrying nothing about any one of them. There is no instance, no path and no stored data to
    * authorize against, so no permission applies. {@code notCatalogued}, {@code unavailable} and
    * {@code unknownType} are not that: they report which connector plugins THIS deployment has
    * installed and how its type registry is configured, which is instance-specific information a
    * customer-neutral catalogue is not. Do not extend the "no instance to authorize against"
    * reasoning to those three fields — it does not hold for them.</p>
    *
    * <p>The actual boundary protecting this endpoint today is authentication, not permission: the
    * whole {@code /api/wiz/**} prefix sits behind {@code WizServiceAuthenticationFilter}'s bearer
    * token check, so the exposure is "any authenticated wiz caller can enumerate this server's
    * installed connector plugins by POSTing every known {@code Rest.*} type", not "anyone on the
    * network can". Whether that residual exposure warrants its own permission is a separate policy
    * decision, not made here — this handler does not even take a {@code Principal} yet.</p>
    *
    * <p>{@code unavailable} and {@code unknownType} answer two different questions and must not be
    * merged. Both start from {@code Config.getQueryClass(type)}, a plain map lookup that either
    * returns a class name or {@code null} — {@code null} means the type is not registered in this
    * build at all, permanently, regardless of which plugins get installed, so that case is {@code
    * unknownType} and is never retried. A returned class name is then resolved with {@code
    * Config.getClass}, which does the actual class loading and throws when the type is registered
    * but its connector plugin is not installed; that failure is an environment problem that goes
    * away once the plugin is installed, so it is {@code unavailable}. Collapsing the two would send
    * a client that mistyped a type, or that is talking to a server on a different version, into an
    * endless retry loop against a type that will never resolve, and would point operators at a
    * plugin that does not exist.</p>
    *
    * @param request the types to look up. An absent or empty list yields an empty answer rather
    *                than an error — asking about nothing is not a fault.
    */
   @PostMapping(value = "/datasources/endpoint-catalog", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizEndpointCatalogResponse getEndpointCatalog(@RequestBody WizEndpointCatalogRequest request) {
      Map<String, WizEndpointCatalog> catalogs = new LinkedHashMap<>();
      List<String> notCatalogued = new ArrayList<>();
      List<String> unavailable = new ArrayList<>();
      List<String> unknownType = new ArrayList<>();
      List<String> types = request.getTypes() == null ? List.of() : request.getTypes();
      Set<String> distinctTypes = new LinkedHashSet<>();

      // A null element is neither "no catalogue" nor "unreadable" - it is not a type at all, and
      // uqlConfig.getQueryClass(null) does not reject it, so left unfiltered it would surface as a
      // literal null in the unavailable array with no way for the caller to explain it. A
      // blank string is filtered for the same reason: it cannot name any data source type either.
      for(String type : types) {
         if(type != null && !type.isBlank()) {
            distinctTypes.add(type);
         }
      }

      for(String type : distinctTypes) {
         String className;

         try {
            // A plain map lookup (dxmap.get(type)) that cannot fail for a well-formed registry;
            // caught defensively so a lookup failure is treated the same as the type being absent,
            // rather than being misread as a plugin-load failure below.
            className = uqlConfig.getQueryClass(type);
         }
         catch(Throwable ex) {
            LOG.debug("Failed to resolve the query class name for type {}: {}", type, ex.getMessage());
            className = null;
         }

         if(className == null) {
            // Nothing in the type registry for this string at all. This is permanent - a client
            // typo or a version mismatch, never something a plugin install fixes - so it must not
            // be reported as "unavailable", which the portal retries.
            unknownType.add(type);
            continue;
         }

         Class<?> queryClass;

         try {
            queryClass = uqlConfig.getClass(type, className);
         }
         catch(Throwable ex) {
            // The type is registered, so this is the connector's plugin failing to load - an
            // environment problem, not a verdict about the connector, and not a verdict that the
            // type does not exist. It must not land in notCatalogued or unknownType.
            LOG.debug("Failed to load the query class for type {}: {}", type, ex.getMessage());
            queryClass = null;
         }

         if(queryClass == null) {
            unavailable.add(type);
            continue;
         }

         try {
            WizEndpointCatalog catalog = endpointCatalogReader.read(queryClass);

            if(catalog == null) {
               notCatalogued.add(type);
            }
            else {
               catalogs.put(type, catalog);
            }
         }
         catch(Exception ex) {
            // The resource is there but unreadable. Reporting it as "has no catalogue" would send
            // the portal to ask the user for documentation it does not need.
            LOG.warn("Failed to read the endpoint catalogue for type {}: {}", type, ex.getMessage());
            unavailable.add(type);
         }
      }

      return new WizEndpointCatalogResponse(catalogs, notCatalogued, unavailable, unknownType);
   }

   /**
    * Lists the database types the wiz editor can configure, plus the installed driver classes.
    *
    * @return the type dropdown's contents. Types whose driver is not installed are still listed —
    *         the client explains the problem rather than hiding the option.
    */
   @GetMapping(value = "/databases/meta", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizDatabaseMeta getDatabaseMeta() {
      DriverAvailability availability = databaseDatasourcesService.getDriverAvailability();
      List<WizDatabaseTypeInfo> types = new ArrayList<>();

      for(DriverAvailability.DriverInfo driver : offeredDrivers(availability)) {
         types.add(new WizDatabaseTypeInfo(driver.getType(), driver.isInstalled(),
                                           driver.getDefaultPort()));
      }

      String[] driverClasses = availability.getDriverClasses();

      return new WizDatabaseMeta(
         types, driverClasses == null ? List.of() : Arrays.asList(driverClasses));
   }

   /**
    * Returns the blank definition a new database starts from.
    *
    * @param principal the current user.
    *
    * @return a {@code CUSTOM} definition with an empty name, no network location and no
    *         credentials.
    */
   @GetMapping(value = "/databases/template", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizDatabaseDefinition getDatabaseTemplate(Principal principal) {
      DataSourceSettingsModel model = databaseDatasourcesService.getDefaultDatabase(principal);

      return toWizDefinition(model.dataSource());
   }

   /**
    * Reads an existing database's connection settings.
    *
    * <p>Gated on WRITE rather than READ, matching the native
    * {@code DatabaseDatasourcesController.getDataSourceModel}: the response is the connection's
    * configuration — host, port, user name, driver class, JDBC or custom URL, pool properties —
    * which is what an editor needs and what a reader of the data has no business seeing.
    * {@code DatabaseDatasourcesService} does not check this itself; it uses the principal only to
    * decide the {@code deletable} flag.</p>
    *
    * @param path      the database's full repository path.
    * @param principal the current user.
    *
    * @return the settings, with the password suppressed.
    *
    * @throws UnsupportedDatasourceException if the path names a data source that is not a JDBC
    *                                        database — a tabular source has no equivalent settings
    *                                        and is edited elsewhere.
    */
   @GetMapping(value = "/databases/definition", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public WizDatabaseDefinition getDatabaseDefinition(
      @PermissionPath @RequestParam("path") String path, Principal principal)
      throws Exception
   {
      String database = requirePath(path);

      return toWizDefinition(requireJdbcDefinition(database, principal));
   }

   /**
    * Suggests the connection test query for a database type.
    *
    * <p>The type is checked against the list {@code getDatabaseMeta} offers before it is used.
    * {@code SQLHelper} answers an unrecognized type with its generic base helper, so an unchecked
    * value would come back as a plausible {@code SELECT 1} attributed to a database the editor
    * cannot even configure; a rejection is the honest answer.</p>
    *
    * @param type   the database type identifier.
    * @param driver the JDBC driver class. Only consulted for {@code CUSTOM}, whose real product is
    *               only knowable from its driver.
    *
    * @return the query, or a null query when the type has no known default.
    */
   @GetMapping(value = "/databases/default-test-query", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizDefaultTestQuery getDefaultTestQuery(
      @RequestParam("type") String type,
      @RequestParam(value = "driver", required = false) String driver)
   {
      if(!offeredDatabaseTypes().contains(type)) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown database type: " + type);
      }

      String dbType = type;

      if(CustomDatabaseType.TYPE.equals(dbType)) {
         dbType = uqlConfig.getJDBCType(driver);
      }

      SQLHelper helper = SQLHelper.getSQLHelper(Objects.toString(dbType, "").toLowerCase());

      return new WizDefaultTestQuery(helper.getConnectionTestQuery());
   }

   /**
    * Opens a connection with the supplied settings without saving them.
    *
    * <p>Requires WRITE on {@code path} whenever one is given, and nothing at all when it is
    * omitted. The asymmetry is the whole point: naming an existing database turns this into a
    * credential read. The password of an unchanged connection is recovered server-side (see
    * {@code oldName} below) and then used to connect to whatever host the request's own definition
    * names, so without the check a caller who cannot read the database at all could recover its
    * password by pointing the test at a host they control. A request with no path is creating a
    * connection that does not exist yet: there is no stored credential to recover and the whole
    * definition, password included, is already the caller's own.</p>
    *
    * <p>The condition is why this is an explicit {@code SecurityEngine} call and not a
    * {@code @Secured} annotation, which would have to demand the permission unconditionally and
    * would then reject the legitimate "test before creating" case outright.</p>
    *
    * @param request   the settings to test, and the path of the database they came from when it
    *                  already exists.
    * @param principal the current user.
    *
    * @return whether the connection succeeded, and the server's account of why not.
    */
   @PostMapping(value = "/databases/test", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizConnectionTestResult testDatabaseConnection(
      @RequestBody WizDatabaseTestRequest request, Principal principal)
      throws Exception
   {
      WizDatabaseDefinition definition = request == null ? null : request.definition();

      if(definition == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definition is required");
      }

      String path = normalizePath(request.path());

      if(path != null) {
         requireDataSourcePermission(path, ResourceAction.WRITE, principal);
      }

      // Same reason as on update: without oldName the stored password cannot be recovered, so a
      // test of an otherwise-unchanged connection would connect with an empty password and fail.
      DatabaseDefinition database = toDatabaseDefinition(definition, lastSegment(path));
      ConnectionStatus status = databaseDatasourcesService.testDataSourceConnection(
         path == null ? "" : path, database, principal, false);

      return new WizConnectionTestResult(status != null && status.isConnected(),
                                         status == null ? null : status.getStatus());
   }

   /**
    * Creates a database in a folder.
    *
    * <p>Always saves with the {@code create} action, i.e. the equivalent of the native editor's
    * {@code create=true}. The native "blank new database" route passes {@code create=false} with the
    * parent folder as its path, which cannot ever succeed — that lookup necessarily finds no data
    * source and reports "Datasource Lost" — so it is not reproduced here.</p>
    *
    * <p>The permission check up front is defence in depth rather than the only gate:
    * {@code saveDatabase} refuses an unauthorized create on its own, but only after it has resolved
    * the folder and read the repository.</p>
    *
    * @param request   the parent folder and the new database.
    * @param principal the current user.
    *
    * @return the saved path, or the reason the save was rejected.
    */
   @PostMapping(value = "/databases/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizDatabaseSaveResult createDatabase(@RequestBody WizDatabaseCreateRequest request,
                                               Principal principal)
      throws Exception
   {
      WizDatabaseDefinition definition = request == null ? null : request.definition();
      String name = requireName(definition);
      String parentPath = normalizePath(request.parentPath());

      requireCreatePermission(parentPath, principal);

      // oldName stays null: a database that does not exist yet has no stored password to recover,
      // and a non-null value would send the recovery lookup after an unrelated data source.
      DatabaseDefinition database = toDatabaseDefinition(definition, null);
      ConnectionStatus status = databaseDatasourcesService.saveDatabase(
         parentPath == null ? "" : parentPath, toSettingsModel(database),
         ActionRecord.ACTION_NAME_CREATE, principal);

      return toSaveResult(status, parentPath == null ? name : parentPath + "/" + name);
   }

   /**
    * Updates an existing database's connection settings.
    *
    * <p>Gated the same way as the read, and for a reason the internal check does not cover:
    * {@code saveDatabase} does verify WRITE, but this method has by then already read the stored
    * definition in order to carry {@code unasgn} and the credential reference forward. The gate
    * belongs at the entry point, before anything is read.</p>
    *
    * @param path       the database's current full repository path. Also the sole source of
    *                   {@code oldName} — see the class javadoc.
    * @param definition the new settings.
    * @param principal  the current user.
    *
    * @return the saved path, which differs from {@code path} when the connection was renamed, or
    *         the reason the save was rejected.
    *
    * @throws UnsupportedDatasourceException if the path names a data source that is not a JDBC
    *                                        database.
    */
   @PostMapping(value = "/databases/update", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public WizDatabaseSaveResult updateDatabase(@PermissionPath @RequestParam("path") String path,
                                               @RequestBody WizDatabaseDefinition definition,
                                               Principal principal)
      throws Exception
   {
      String database = requirePath(path);
      String name = requireName(definition);
      String actionName = databaseDatasourcesService.getActionName(database, name);

      // The save path treats a path with no data source behind it as a parent folder and would
      // silently create a second database instead of updating one. This is the check the native
      // controller performs with create=false.
      if(!ActionRecord.ACTION_NAME_EDIT.equals(actionName)) {
         return new WizDatabaseSaveResult(false, "DATASOURCE_LOST", null);
      }

      DatabaseDefinition current = requireJdbcDefinition(database, principal);
      DatabaseDefinition updated = toDatabaseDefinition(definition, lastSegment(database));
      carryOverUneditedSettings(current, updated);

      ConnectionStatus status = databaseDatasourcesService.saveDatabase(
         database, toSettingsModel(updated), actionName, principal);
      int index = database.lastIndexOf('/');

      return toSaveResult(status, index < 0 ? name : database.substring(0, index + 1) + name);
   }

   /**
    * Creates a folder in the data source repository.
    *
    * <p>Gated by the same rule as {@link #createDatabase}: WRITE on the parent folder, or, at the
    * root only, the standalone {@code CREATE_DATA_SOURCE} grant. {@code addDatasourceFolder} itself
    * performs no duplicate check and no permission check — the native
    * {@code DataSourceController} guards both in the caller, and this mirrors that rather than
    * relying on the service to catch either.</p>
    *
    * <p>{@code userScope} is passed through from {@link #requireCreatePermission}: {@code true} only
    * when the caller reached this endpoint on the strength of the standalone
    * {@code CREATE_DATA_SOURCE} grant, with no WRITE on the parent folder itself. That flag is what
    * grants such a caller personal READ/WRITE/DELETE on the new folder — the same "create in my own
    * scope" affordance the native controller applies for the identical case. Without it, that caller
    * would have no permission entry on the folder they just created and no WRITE on the root to
    * inherit from, and would be locked out of it immediately after creating it.</p>
    *
    * @param request   the parent folder and the new folder's name.
    * @param principal the current user.
    *
    * @return the new folder's path, or {@code DUPLICATE_NAME} when a folder or data source of that
    *         name already exists in the parent.
    *
    * <p><b>Known limitations, both pre-existing in the underlying repository API rather than
    * introduced here:</b> the duplicate check is advisory, not atomic with the create that follows —
    * two concurrent requests for the same name can both pass it, the same race
    * {@code checkTabularDuplicateName} already has on the tabular create path. And neither this
    * endpoint nor the native {@code DataSourceController.addDatasourceFolder} it mirrors confirms that
    * {@code parentPath} names an existing folder before creating inside it, so a caller with WRITE on
    * a broad ancestor can create a folder under a path that does not exist.</p>
    */
   @PostMapping(value = "/datasources/folders/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizFolderSaveResult createFolder(@RequestBody WizFolderCreateRequest request,
                                           Principal principal)
      throws Exception
   {
      String name = requireFolderName(request);
      String parentPath = normalizePath(request.parentPath());

      boolean userScope = requireCreatePermission(parentPath, principal);

      String path = parentPath == null ? name : parentPath + "/" + name;
      CheckDuplicateResponse duplicate = dataSourceBrowserService.checkFolderDuplicate(path);

      if(duplicate != null && duplicate.isDuplicate()) {
         return WizFolderSaveResult.failed(WizFolderSaveResult.DUPLICATE_NAME);
      }

      String auditPath = Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE_FOLDER, path, principal);
      dataSourceBrowserService.addDatasourceFolder(path, auditPath, principal, userScope);

      return WizFolderSaveResult.ok(path);
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
    * Fails unless the caller may create a database in a folder.
    *
    * <p>The rule is the one {@code DataSourceController} applies to its {@code newDatasourceEnabled}
    * flag: WRITE on the parent folder, or, at the root only, the standalone
    * {@code CREATE_DATA_SOURCE} grant that lets a user own data sources without holding the root
    * folder. The native controller's fuller rule set is not reproduced — this is a guard in front of
    * {@code saveDatabase}, which enforces the real thing.</p>
    *
    * @return whether the ONLY grant that let the caller through was the standalone
    *         {@code CREATE_DATA_SOURCE} permission, i.e. they hold no WRITE on the parent folder
    *         itself. {@code createDatabase} ignores this — {@code saveDatabase} grants the creator
    *         ownership of the new database itself — but {@code createFolder} needs it: that is the
    *         exact case where the caller would otherwise have no permission on the folder they just
    *         created. See the {@code userScope} note on {@code createFolder}.
    */
   private boolean requireCreatePermission(String parentPath, Principal principal) throws Exception {
      boolean root = parentPath == null;
      boolean writeOnParent = securityEngine.checkPermission(
         principal, ResourceType.DATA_SOURCE_FOLDER, root ? "/" : parentPath, ResourceAction.WRITE);

      if(writeOnParent) {
         return false;
      }

      boolean createDataSourceOnly = root && securityEngine.checkPermission(
         principal, ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS);

      if(!createDataSourceOnly) {
         throw new SecurityException("Unauthorized access to data source folder \"" +
                                        (root ? "/" : parentPath) + "\" by user " + principal);
      }

      return true;
   }

   /** The database type identifiers the editor offers, i.e. what {@code getDatabaseMeta} returns. */
   private Set<String> offeredDatabaseTypes() {
      Set<String> types = new LinkedHashSet<>();

      for(DriverAvailability.DriverInfo driver :
         offeredDrivers(databaseDatasourcesService.getDriverAvailability()))
      {
         types.add(driver.getType());
      }

      return types;
   }

   private static List<DriverAvailability.DriverInfo> offeredDrivers(DriverAvailability availability)
   {
      List<DriverAvailability.DriverInfo> drivers = new ArrayList<>();
      Set<String> seen = new HashSet<>();

      if(availability == null || availability.getDrivers() == null) {
         return drivers;
      }

      for(DriverAvailability.DriverInfo driver : availability.getDrivers()) {
         String type = driver == null ? null : driver.getType();

         // Several driver beans can share one type identifier (DB2 and Sybase each ship more than
         // one driver class), and the editor offers the type, not the driver.
         if(type == null || EXCLUDED_DATABASE_TYPES.contains(type) || !seen.add(type)) {
            continue;
         }

         drivers.add(driver);
      }

      return drivers;
   }

   private List<WizDatasourceEntry> toEntries(List<DataSourceInfo> infos) {
      List<WizDatasourceEntry> entries = new ArrayList<>();

      if(infos == null) {
         return entries;
      }

      for(DataSourceInfo info : infos) {
         if(info != null) {
            entries.add(toEntry(info));
         }
      }

      return entries;
   }

   private WizDatasourceEntry toEntry(DataSourceInfo info) {
      String nodeType = info.type() == null ? null : info.type().name();
      boolean folder = isFolder(nodeType);
      String sourceType = null;
      String databaseType = null;
      String annotationClass = null;

      if(!folder) {
         try {
            XDataSource dataSource = xrepository.getDataSource(info.path());

            if(dataSource != null) {
               sourceType = dataSource.getType();

               if(dataSource instanceof JDBCDataSource jdbcDataSource) {
                  databaseType = jdbcDataSource.getDatabaseTypeString();
               }

               annotationClass = annotationClassOf(dataSource);
            }
         }
         catch(Throwable ex) {
            // One unreadable data source — a missing driver, a broken definition — must not take
            // the whole listing down with it. The row still renders, just without a type.
            LOG.debug("Failed to resolve the type of data source {}: {}", info.path(),
                      ex.getMessage());
            annotationClass = UNKNOWN;
         }
      }

      return new WizDatasourceEntry(info.name(), info.path(), nodeType, folder, sourceType,
                                    databaseType, annotationClass, info.createdBy(),
                                    info.createdDate(), info.editable(), info.deletable(),
                                    info.hasSubFolder());
   }

   /**
    * Decides how a data source can be annotated. See {@link WizDatasourceEntry} for what each value
    * means to the portal.
    *
    * <p>The question is answered from the QUERY class rather than the data source, because what
    * distinguishes these cases is how targets are discovered, and that lives on the query. The query
    * class also owns the {@code endpoints.json} resource, which is the whole of the
    * {@code ENDPOINT_CATALOG} test.</p>
    *
    * <p>Order matters and runs specific to general. {@code EndpointJsonQuery} extends
    * {@code RestJsonQuery}, so testing for REST first would classify all 65 catalogued connectors as
    * needing documentation they do not need.</p>
    */
   private String annotationClassOf(XDataSource dataSource) {
      String type = dataSource.getType();

      if(dataSource instanceof JDBCDataSource) {
         return JDBC;
      }

      Class<?> queryClass;

      try {
         String className = uqlConfig.getQueryClass(type);

         if(className == null) {
            return UNKNOWN;
         }

         queryClass = uqlConfig.getClass(type, className);
      }
      catch(Throwable ex) {
         // A connector whose plugin is absent cannot be classified. Saying UNKNOWN keeps that
         // distinct from "classified as not annotatable", which the portal reports very differently.
         LOG.debug("Failed to load the query class for type {}: {}", type, ex.getMessage());
         return UNKNOWN;
      }

      return queryClass == null ? UNKNOWN : classifyQueryClass(queryClass);
   }

   /**
    * The classification itself, separated from how the class was obtained so it can be exercised
    * without standing up a plugin registry.
    */
   static String classifyQueryClass(Class<?> queryClass) {
      if(ScriptedQuery.class.isAssignableFrom(queryClass)) {
         return UNSUPPORTED;
      }

      // Resource lookup rather than a class-name test: shipping an endpoints.json IS the property
      // being asked about, and core cannot reference the connector classes to test them directly.
      if(queryClass.getResource("endpoints.json") != null) {
         return ENDPOINT_CATALOG;
      }

      if(isRestQuery(queryClass)) {
         return DOCUMENT_REQUIRED;
      }

      if(SelectableTabularQuery.class.isAssignableFrom(queryClass)) {
         return FILE;
      }

      return METADATA;
   }

   /**
    * Whether the query descends from the REST base class, by name.
    *
    * <p>{@code AbstractRestQuery} lives in a connector module that core does not depend on, so it
    * cannot be named directly. Walking the superclass chain keeps this to one string rather than
    * enumerating every REST query type.</p>
    */
   private static boolean isRestQuery(Class<?> queryClass) {
      for(Class<?> c = queryClass; c != null; c = c.getSuperclass()) {
         if(REST_QUERY_CLASS.equals(c.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Loads a database definition, rejecting anything that is not a JDBC database.
    *
    * <p>{@code getDatabaseDefinition} answers null rather than throwing for a tabular data source.
    * Letting that null through would be worse than an error: on the read path the client would get
    * an empty body, and on the save path {@code saveDatabase} would return the same null it returns
    * for success, so a save that did nothing at all would be reported as having worked.</p>
    */
   private DatabaseDefinition requireJdbcDefinition(String path, Principal principal)
      throws Exception
   {
      DatabaseDefinition definition =
         databaseDatasourcesService.getDatabaseDefinition(path, principal);

      if(definition == null) {
         String sourceType = null;

         try {
            XDataSource dataSource = xrepository.getDataSource(path);
            sourceType = dataSource == null ? null : dataSource.getType();
         }
         catch(Throwable ex) {
            LOG.debug("Failed to resolve the type of data source {}: {}", path, ex.getMessage());
         }

         throw new UnsupportedDatasourceException(path, sourceType);
      }

      return definition;
   }

   /**
    * Copies forward the settings the wiz editor does not expose.
    *
    * <p>Every field of the stored definition that the wire contract omits would otherwise be reset
    * to its default by an update, since the definition handed to the save path is built from
    * scratch. Two are worth preserving: {@code unasgn} governs whether users with no explicit grant
    * are denied data, and the cloud-secret credential reference replaces the user name and password
    * entirely — clearing either through a connection edit would be a silent security change.</p>
    */
   private static void carryOverUneditedSettings(DatabaseDefinition current,
                                                 DatabaseDefinition updated)
   {
      updated.setUnasgn(current.isUnasgn());

      AuthenticationDetails currentAuth = current.getAuthentication();
      AuthenticationDetails updatedAuth = updated.getAuthentication();

      if(currentAuth != null && updatedAuth != null) {
         updatedAuth.setUseCredentialId(currentAuth.isUseCredentialId());
         updatedAuth.setCredentialId(currentAuth.getCredentialId());
      }
   }

   /**
    * Wraps a definition for the save path.
    *
    * <p>{@code permissions} is left null deliberately: the save path writes whatever permissions it
    * is handed as long as their {@code changed} flag survives, and the wiz portal has no permission
    * editor whose intent that could reflect. {@code additionalDataSources} is left null for the same
    * reason — a non-null value there is read as the complete list and would delete every additional
    * connection the database has.</p>
    */
   private static DataSourceSettingsModel toSettingsModel(DatabaseDefinition definition) {
      return DataSourceSettingsModel.builder()
         .dataSource(definition)
         .uploadEnabled(false)
         .build();
   }

   /**
    * Turns the save path's status string into a result the client can branch on.
    *
    * <p>A business failure arrives as an English string inside a successful call, and a null status
    * means the save went through.</p>
    */
   private static WizDatabaseSaveResult toSaveResult(ConnectionStatus status, String path) {
      String reason = status == null ? null : status.getStatus();

      if(reason == null) {
         return new WizDatabaseSaveResult(true, null, path);
      }

      String code = SAVE_FAILURE_REASONS.get(reason);

      if(code == null) {
         // Not swallowed: the client only ever sees UNKNOWN, so the original wording has to be
         // recoverable from the server log for anyone diagnosing it.
         LOG.warn("Unrecognized database save status for {}: {}", path, reason);
         code = "UNKNOWN";
      }

      return new WizDatabaseSaveResult(false, code, null);
   }

   private WizDatabaseDefinition toWizDefinition(DatabaseDefinition definition) {
      if(definition == null) {
         return null;
      }

      NetworkLocation network = definition.getNetwork();
      WizNetworkLocation wizNetwork = network == null
         ? null : new WizNetworkLocation(network.getHostName(), network.getPortNumber());
      AuthenticationDetails authentication = definition.getAuthentication();

      // The password is dropped, never masked. StyleBI's own model sends a placeholder constant
      // here, but a client that returned that constant unchanged would be indistinguishable from a
      // user who had typed it, and the wiz contract expresses "unchanged" as null instead.
      WizAuthentication wizAuthentication = authentication == null
         ? new WizAuthentication(false, null, null)
         : new WizAuthentication(authentication.isRequired(), authentication.getUserName(), null);

      return new WizDatabaseDefinition(
         definition.getName(), definition.getDescription(), definition.getType(), wizNetwork,
         wizAuthentication, toWizInfo(definition.getInfo()), definition.isAnsiJoin(),
         definition.getTransactionIsolation(), definition.getTableNameOption(),
         definition.getDefaultDatabase(), definition.isChangeDefaultDB(),
         definition.isDeletable());
   }

   private WizDatabaseInfo toWizInfo(DatabaseInfo info) {
      if(info == null) {
         return null;
      }

      String databaseName = null;
      String sid = null;
      String instanceName = null;
      String serverName = null;
      String databaseLocale = null;
      String driverClass = null;
      String jdbcUrl = null;
      String testQuery = null;

      // Dispatched on the subclass rather than on the type identifier: the two agree, but only the
      // subclass can be trusted to actually have the accessor being called.
      if(info instanceof DatabaseNameInfo nameInfo) {
         databaseName = nameInfo.getDatabaseName();
      }

      if(info instanceof OracleDatabaseType.OracleDatabaseInfo oracleInfo) {
         sid = oracleInfo.getSid();
      }

      if(info instanceof SQLServerDatabaseType.SQLServerDatabaseInfo sqlServerInfo) {
         instanceName = sqlServerInfo.getInstanceName();
      }

      if(info instanceof InformixDatabaseType.InformixDatabaseInfo informixInfo) {
         serverName = informixInfo.getServerName();
         databaseLocale = informixInfo.getDatabaseLocale();
      }

      if(info instanceof CustomDatabaseType.CustomDatabaseInfo customInfo) {
         driverClass = customInfo.getDriverClass();
         jdbcUrl = customInfo.getJdbcUrl();
         testQuery = customInfo.getTestQuery();
      }

      Map<String, String> poolProperties = info.getPoolProperties() == null
         ? null : new LinkedHashMap<>(info.getPoolProperties());

      return new WizDatabaseInfo(databaseName, sid, instanceName, serverName, databaseLocale,
                                 driverClass, jdbcUrl, testQuery, info.isCustomEditMode(),
                                 info.getCustomUrl(), poolProperties);
   }

   /**
    * Builds the definition the save and test paths take.
    *
    * @param definition the client's settings.
    * @param oldName    the name the database is stored under, or null when it does not exist yet.
    *                   Never taken from the client: it is the key to the stored password, and a save
    *                   without it leaves the password at the placeholder, skips {@code setPassword}
    *                   and then has the whole credential — placeholder and all — copied over the
    *                   real one. The password is gone with no error anywhere.
    */
   private DatabaseDefinition toDatabaseDefinition(WizDatabaseDefinition definition,
                                                   String oldName)
   {
      DatabaseDefinition result = new DatabaseDefinition();
      result.setName(definition.name());
      result.setOldName(oldName);
      result.setType(definition.type());
      result.setDescription(definition.description());
      result.setInfo(toDatabaseInfo(definition.type(), definition.info()));

      // Always non-null: the URL formatters read the host and port unconditionally for every type
      // that has them, so a client that omitted the network would get a NullPointerException.
      NetworkLocation network = new NetworkLocation();

      if(definition.network() != null) {
         network.setHostName(definition.network().hostName());
         network.setPortNumber(definition.network().portNumber());
      }

      result.setNetwork(network);

      AuthenticationDetails authentication = new AuthenticationDetails();
      WizAuthentication wizAuthentication = definition.authentication();
      authentication.setRequired(wizAuthentication != null && wizAuthentication.required());

      if(wizAuthentication != null) {
         authentication.setUserName(wizAuthentication.userName());
      }

      // null means "leave the stored password alone", which the save path expresses as the
      // placeholder constant; paired with oldName above it recovers the real password. An empty
      // string is a deliberate clear and is passed through as itself.
      String password = wizAuthentication == null ? null : wizAuthentication.password();
      authentication.setPassword(password == null ? Util.PLACEHOLDER_PASSWORD : password);
      result.setAuthentication(authentication);

      result.setAnsiJoin(definition.ansiJoin());
      result.setTransactionIsolation(definition.transactionIsolation());
      result.setTableNameOption(definition.tableNameOption());
      result.setDefaultDatabase(definition.defaultDatabase());
      result.setChangeDefaultDB(definition.changeDefaultDB());
      result.setDeletable(definition.deletable());

      return result;
   }

   private DatabaseInfo toDatabaseInfo(String type, WizDatabaseInfo wizInfo) {
      DatabaseType<?> databaseType = databaseTypeService.getDatabaseType(type);

      if(databaseType == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown database type: " + type);
      }

      DatabaseInfo info = databaseType.createDatabaseInfo();

      // Never left null: the save path reads the pool properties unconditionally.
      info.setPoolProperties(wizInfo == null || wizInfo.poolProperties() == null
                                ? new TreeMap<>() : new TreeMap<>(wizInfo.poolProperties()));

      if(wizInfo != null) {
         info.setCustomEditMode(wizInfo.customEditMode());
         info.setCustomUrl(wizInfo.customUrl());

         if(info instanceof DatabaseNameInfo nameInfo) {
            nameInfo.setDatabaseName(wizInfo.databaseName());
         }

         if(info instanceof OracleDatabaseType.OracleDatabaseInfo oracleInfo) {
            oracleInfo.setSid(wizInfo.sid());
         }

         if(info instanceof SQLServerDatabaseType.SQLServerDatabaseInfo sqlServerInfo) {
            sqlServerInfo.setInstanceName(wizInfo.instanceName());
         }

         if(info instanceof InformixDatabaseType.InformixDatabaseInfo informixInfo) {
            informixInfo.setServerName(wizInfo.serverName());
            informixInfo.setDatabaseLocale(wizInfo.databaseLocale());
         }

         if(info instanceof CustomDatabaseType.CustomDatabaseInfo customInfo) {
            customInfo.setDriverClass(wizInfo.driverClass());
            customInfo.setJdbcUrl(wizInfo.jdbcUrl());
            customInfo.setTestQuery(wizInfo.testQuery());

            // A CUSTOM connection is always read back with customEditMode set, and in that mode the
            // stored URL is taken from customUrl, not from jdbcUrl. Pinning the flag and falling
            // back to jdbcUrl keeps a client that filled in only one of the two from saving a
            // database with no URL at all.
            customInfo.setCustomEditMode(true);

            if(customInfo.getCustomUrl() == null) {
               customInfo.setCustomUrl(wizInfo.jdbcUrl());
            }
         }
      }

      return info;
   }

   private static String requirePath(String path) {
      String normalized = normalizePath(path);

      if(normalized == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
      }

      return normalized;
   }

   private static String requireName(WizDatabaseDefinition definition) {
      if(definition == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definition is required");
      }

      String name = definition.name() == null ? null : definition.name().trim();

      if(name == null || name.isEmpty()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
      }

      return name;
   }

   private static String requireFolderName(WizFolderCreateRequest request) {
      if(request == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
      }

      String name = request.name() == null ? null : request.name().trim();

      if(name == null || name.isEmpty()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
      }

      if(name.contains("/") || ".".equals(name) || "..".equals(name)) {
         throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "name must be a single path segment");
      }

      return name;
   }

   private static String lastSegment(String path) {
      if(path == null) {
         return null;
      }

      int index = path.lastIndexOf('/');

      return index < 0 ? path : path.substring(index + 1);
   }

   private static boolean isFolder(String nodeType) {
      if(nodeType == null) {
         return false;
      }

      try {
         return FOLDER_TYPES.contains(PortalDataType.valueOf(nodeType));
      }
      catch(IllegalArgumentException ex) {
         // A node type this build does not know about is treated as a leaf, which is the safe
         // default: the client will not try to descend into it.
         return false;
      }
   }

   /**
    * Maps the browser's notion of "no folder" onto the services', which take null for the root but
    * are given an empty string or a slash by clients that build the path by concatenation.
    */
   private static String normalizePath(String path) {
      if(path == null || path.isBlank() || "/".equals(path)) {
         return null;
      }

      return path;
   }

   private static final Set<PortalDataType> FOLDER_TYPES = EnumSet.of(
      PortalDataType.FOLDER,
      PortalDataType.DATA_SOURCE_FOLDER,
      PortalDataType.DATA_SOURCE_ROOT_FOLDER,
      PortalDataType.DATA_MODEL_FOLDER,
      PortalDataType.VPM_FOLDER,
      PortalDataType.PRIVATE_WORKSHEETS_FOLDER,
      PortalDataType.SHARED_WORKSHEETS_FOLDER);

   /**
    * Database types the wiz editor cannot configure. Access needs a file upload for the .mdb, and
    * ODBC needs the server to enumerate its DSNs; neither has a form here, so neither is offered.
    *
    * <p>Only the Access entry currently does anything: no ODBC {@code DatabaseType} bean is
    * registered, so no driver ever reports that identifier. It is kept so that registering one
    * would not silently put an unconfigurable type in the dropdown.</p>
    */
   private static final Set<String> EXCLUDED_DATABASE_TYPES =
      Set.of(AccessDatabaseType.TYPE, JDBCDataSource.ODBC);

   /** The save path's status strings, mapped to codes the client can branch on. */
   private static final Map<String, String> SAVE_FAILURE_REASONS = Map.of(
      "Duplicate", "DUPLICATE_NAME",
      "Duplicate Folder", "DUPLICATE_FOLDER",
      "Datasource Lost", "DATASOURCE_LOST",
      "Invalid Folder", "INVALID_FOLDER");

   private static final Logger LOG = LoggerFactory.getLogger(WizDatabaseController.class);

   private final DataSourceBrowserService dataSourceBrowserService;
   private final DataSourceStatusService dataSourceStatusService;
   private final DatabaseDatasourcesService databaseDatasourcesService;
   private final DatabaseTypeService databaseTypeService;
   private final SecurityEngine securityEngine;
   private final Config uqlConfig;
   private final XRepository xrepository;
   private final EndpointCatalogReader endpointCatalogReader;

   /** Annotation classes. See {@link WizDatasourceEntry} for what each one means to the portal. */
   private static final String JDBC = "JDBC";
   private static final String FILE = "FILE";
   private static final String METADATA = "METADATA";
   private static final String ENDPOINT_CATALOG = "ENDPOINT_CATALOG";
   private static final String DOCUMENT_REQUIRED = "DOCUMENT_REQUIRED";
   private static final String UNSUPPORTED = "UNSUPPORTED";
   private static final String UNKNOWN = "UNKNOWN";

   /** Named rather than imported: it lives in a connector module core does not depend on. */
   private static final String REST_QUERY_CLASS = "inetsoft.uql.rest.AbstractRestQuery";
}
