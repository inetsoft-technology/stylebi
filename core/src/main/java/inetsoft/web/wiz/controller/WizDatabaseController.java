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
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.util.Config;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.database.*;
import inetsoft.web.admin.content.database.types.*;
import inetsoft.web.admin.content.repository.DataSourceSettingsModel;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DataSourceConnectionStatusRequest;
import inetsoft.web.portal.data.DataSourceInfo;
import inetsoft.web.portal.data.DataSourceStatus;
import inetsoft.web.portal.data.ImmutableDataSourceConnectionStatusRequest;
import inetsoft.web.portal.data.PortalDataType;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.*;
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
 */
@RestController
@RequestMapping("/api/wiz")
public class WizDatabaseController {
   public WizDatabaseController(DataSourceBrowserService dataSourceBrowserService,
                                DataSourceStatusService dataSourceStatusService,
                                DatabaseDatasourcesService databaseDatasourcesService,
                                DatabaseTypeService databaseTypeService,
                                Config uqlConfig,
                                XRepository xrepository)
   {
      this.dataSourceBrowserService = dataSourceBrowserService;
      this.dataSourceStatusService = dataSourceStatusService;
      this.databaseDatasourcesService = databaseDatasourcesService;
      this.databaseTypeService = databaseTypeService;
      this.uqlConfig = uqlConfig;
      this.xrepository = xrepository;
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
    * @param request   the data source paths to report on.
    * @param principal the current user.
    *
    * @return one status per requested path, in the order requested.
    */
   @PostMapping(value = "/datasources/statuses", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<WizDatasourceStatus> getDatasourceStatuses(
      @RequestBody WizDatasourceStatusRequest request,
      Principal principal)
      throws Exception
   {
      List<String> paths = request == null ? null : request.paths();

      if(paths == null || paths.isEmpty()) {
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
    * Lists the database types the wiz editor can configure, plus the installed driver classes.
    *
    * @return the type dropdown's contents. Types whose driver is not installed are still listed —
    *         the client explains the problem rather than hiding the option.
    */
   @GetMapping(value = "/databases/meta", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizDatabaseMeta getDatabaseMeta() {
      DriverAvailability availability = databaseDatasourcesService.getDriverAvailability();
      List<WizDatabaseTypeInfo> types = new ArrayList<>();
      Set<String> seen = new HashSet<>();

      if(availability.getDrivers() != null) {
         for(DriverAvailability.DriverInfo driver : availability.getDrivers()) {
            String type = driver == null ? null : driver.getType();

            // Several driver beans can share one type identifier (DB2 and Sybase each ship more
            // than one driver class), and the editor offers the type, not the driver.
            if(type == null || EXCLUDED_DATABASE_TYPES.contains(type) || !seen.add(type)) {
               continue;
            }

            types.add(
               new WizDatabaseTypeInfo(type, driver.isInstalled(), driver.getDefaultPort()));
         }
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
   public WizDatabaseDefinition getDatabaseDefinition(@RequestParam("path") String path,
                                                      Principal principal)
      throws Exception
   {
      String database = requirePath(path);

      return toWizDefinition(requireJdbcDefinition(database, principal));
   }

   /**
    * Suggests the connection test query for a database type.
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
    * @param request   the settings to test, and the path of the database they came from when it
    *                  already exists.
    * @param principal the current user.
    *
    * @return whether the connection succeeded, and the server's account of why not.
    */
   @PostMapping(value = "/databases/test", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizConnectionTestResult testDatabaseConnection(
      @RequestBody WizDatabaseTestRequest request, Principal principal)
   {
      WizDatabaseDefinition definition = request == null ? null : request.definition();

      if(definition == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definition is required");
      }

      String path = normalizePath(request.path());

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
   public WizDatabaseSaveResult updateDatabase(@RequestParam("path") String path,
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

      if(!folder) {
         try {
            XDataSource dataSource = xrepository.getDataSource(info.path());

            if(dataSource != null) {
               sourceType = dataSource.getType();

               if(dataSource instanceof JDBCDataSource jdbcDataSource) {
                  databaseType = jdbcDataSource.getDatabaseTypeString();
               }
            }
         }
         catch(Throwable ex) {
            // One unreadable data source — a missing driver, a broken definition — must not take
            // the whole listing down with it. The row still renders, just without a type.
            LOG.debug("Failed to resolve the type of data source {}: {}", info.path(),
                      ex.getMessage());
         }
      }

      return new WizDatasourceEntry(info.name(), info.path(), nodeType, folder, sourceType,
                                    databaseType, info.createdBy(), info.createdDate(),
                                    info.editable(), info.deletable(), info.hasSubFolder());
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
    */
   private static final Set<String> EXCLUDED_DATABASE_TYPES =
      Set.of(AccessDatabaseType.TYPE, "ODBC");

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
   private final Config uqlConfig;
   private final XRepository xrepository;
}
