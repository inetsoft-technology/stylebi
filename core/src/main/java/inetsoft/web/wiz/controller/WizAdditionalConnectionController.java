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
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.repository.DataSourceSettingsModel;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.WizAdditionalDefinitionRequest;
import inetsoft.web.wiz.request.WizAdditionalRemoveRequest;
import inetsoft.web.wiz.request.WizAdditionalTestRequest;
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
 * Additional JDBC connections of a data source, for the wiz bridge.
 *
 * <p>An additional connection has no endpoint of its own in StyleBI: the Portal edits them as part
 * of the base database, sending the COMPLETE list through {@code saveDatabase}, which replaces the
 * stored list. The mutating endpoints here therefore read the stored list, change one entry, and
 * save the whole list back through that same audited service method with the base definition
 * re-read from the repository, so every entry the caller did not touch round-trips unchanged: its
 * {@code oldName} is its own name and its password is the placeholder the service recovers.
 * Entries the caller cannot READ are listed neither by {@link #listAdditionalConnections} nor
 * touched, but they ARE carried through a save, or the replace-the-list save would delete them.</p>
 *
 * <p>Permissions are WRITE on the base data source (the rule {@code /databases/definition} uses),
 * stricter than the Portal's test endpoint, plus READ on {@code base::name} for a named
 * connection. Never the site-admin gate. A null password on a changed connection means "keep the
 * stored one", recovered by the service through {@code oldName}.</p>
 */
@RestController
@RequestMapping("/api/wiz")
public class WizAdditionalConnectionController {
   public WizAdditionalConnectionController(DatabaseDatasourcesService databaseDatasourcesService,
                                            DatabaseTypeService databaseTypeService,
                                            SecurityEngine securityEngine,
                                            XRepository xrepository)
   {
      this.databaseDatasourcesService = databaseDatasourcesService;
      this.securityEngine = securityEngine;
      this.xrepository = xrepository;
      this.gates = new WizDatasourceGates(securityEngine);
      this.mapper = new WizDatabaseMapper(databaseTypeService);
   }

   /** Lists the additional connections of a data source that the caller may read. */
   @GetMapping(value = "/databases/additional", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public WizAdditionalConnections listAdditionalConnections(
      @PermissionPath @RequestParam("path") String path, Principal principal)
      throws Exception
   {
      String base = requireBase(path, principal);
      List<WizAdditionalConnection> connections = new ArrayList<>();

      for(DatabaseDefinition definition : storedAdditionals(base, principal)) {
         String name = definition.getName();

         if(!databaseDatasourcesService.checkAdditionalPermission(
            base, name, ResourceAction.READ, principal))
         {
            continue;
         }

         boolean deletable = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, base + "::" + name, ResourceAction.DELETE);

         connections.add(new WizAdditionalConnection(
            name, mapper.toWizDefinition(definition), deletable));
      }

      return new WizAdditionalConnections(connections);
   }

   /** Adds an additional connection. */
   @PostMapping(value = "/databases/additional/add", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public WizDatabaseSaveResult addAdditionalConnection(
      @PermissionPath @RequestParam("path") String path,
      @RequestBody WizAdditionalDefinitionRequest request, Principal principal)
      throws Exception
   {
      String base = requireBase(path, principal);
      WizDatabaseDefinition definition = request == null ? null : request.definition();
      String name = requireName(definition);
      WizDatasourceNames.requireValidDatasourceName(name);

      List<DatabaseDefinition> stored = storedAdditionals(base, principal);

      if(indexOf(stored, name) >= 0) {
         return new WizDatabaseSaveResult(false, "DUPLICATE_NAME", null);
      }

      // oldName stays null: a new connection has no stored password to recover.
      stored.add(mapper.toDatabaseDefinition(definition, null));

      return save(base, stored, name, principal);
   }

   /** Edits an additional connection, possibly renaming it. A null password keeps the stored one. */
   @PostMapping(value = "/databases/additional/update",
                produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public WizDatabaseSaveResult updateAdditionalConnection(
      @PermissionPath @RequestParam("path") String path,
      @RequestParam("name") String oldName,
      @RequestBody WizAdditionalDefinitionRequest request, Principal principal)
      throws Exception
   {
      String base = requireBase(path, principal);
      WizDatabaseDefinition definition = request == null ? null : request.definition();
      String name = requireName(definition);
      String existing = requireConnectionName(oldName);

      if(!name.equals(existing)) {
         WizDatasourceNames.requireValidDatasourceName(name);
      }

      List<DatabaseDefinition> stored = storedAdditionals(base, principal);
      int index = indexOf(stored, existing);

      if(index < 0) {
         return new WizDatabaseSaveResult(false, "DATASOURCE_LOST", null);
      }

      requireReadable(base, existing, principal);

      int clash = indexOf(stored, name);

      if(clash >= 0 && clash != index) {
         return new WizDatabaseSaveResult(false, "DUPLICATE_NAME", null);
      }

      // oldName is the stored name, never taken from the client: it is the key to the stored
      // password.
      DatabaseDefinition updated = mapper.toDatabaseDefinition(definition, existing);
      DatabaseDefinition current = stored.get(index);
      updated.setUnasgn(current.isUnasgn());
      WizDatabaseMapper.carryOverCredentialReference(current, updated);
      stored.set(index, updated);

      return save(base, stored, name, principal);
   }

   /**
    * Removes additional connections. Each name is judged alone: a name that is missing or still used
    * by a physical view or logical model (unless {@code force}) is reported in its own result and
    * does not stop the others. The removable names are saved together as one write.
    */
   @PostMapping(value = "/databases/additional/remove",
                produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public WizAdditionalRemoveResult removeAdditionalConnections(
      @PermissionPath @RequestParam("path") String path,
      @RequestBody WizAdditionalRemoveRequest request, Principal principal)
      throws Exception
   {
      String base = requireBase(path, principal);
      List<String> names = request == null || request.names() == null ? List.of() : request.names();
      boolean force = request != null && request.force();
      List<DatabaseDefinition> stored = storedAdditionals(base, principal);
      List<WizAdditionalRemoveResult.Item> results = new ArrayList<>();
      Set<String> removable = new LinkedHashSet<>();

      for(String requested : names) {
         String name = requested == null ? "" : requested.trim();

         if(indexOf(stored, name) < 0) {
            results.add(new WizAdditionalRemoveResult.Item(
               name, false, WizAdditionalRemoveResult.DATASOURCE_LOST, null, null));
            continue;
         }

         requireReadable(base, name, principal);

         if(!force && databaseDatasourcesService.additionalDeletable(base, name)) {
            results.add(new WizAdditionalRemoveResult.Item(
               name, false, WizAdditionalRemoveResult.HAS_DEPENDENCIES,
               "A physical view or logical model of \"" + base +
                  "\" uses the additional connection \"" + name + "\"", null));
            continue;
         }

         removable.add(name);
         results.add(null);
      }

      if(removable.isEmpty()) {
         return new WizAdditionalRemoveResult(results);
      }

      List<DatabaseDefinition> remaining = new ArrayList<>(stored);
      remaining.removeIf(definition -> removable.contains(definition.getName()));
      WizDatabaseSaveResult saved = save(base, remaining, null, principal);

      // The removable placeholders were reserved in request order; fill them with the one outcome
      // of the shared save, so the response never leaves a name ambiguous.
      int position = 0;

      for(String requested : names) {
         String name = requested == null ? "" : requested.trim();

         if(results.get(position) == null) {
            results.set(position, new WizAdditionalRemoveResult.Item(
               name, saved.ok(), saved.ok() ? null : saved.reason(), null,
               saved.ok() ? null : saved.message()));
         }

         position++;
      }

      return new WizAdditionalRemoveResult(results);
   }

   /**
    * Opens a connection with the supplied settings without saving them. The stored password of the
    * named connection is recovered server-side, which is why this needs WRITE on the base (stricter
    * than the Portal's {@code PORTAL_TAB} or {@code EM_COMPONENT} gate): the stored password would
    * otherwise be used against whatever host the request names.
    */
   @PostMapping(value = "/databases/additional/test", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public WizConnectionTestResult testAdditionalConnection(
      @PermissionPath @RequestParam("path") String path,
      @RequestBody WizAdditionalTestRequest request, Principal principal)
      throws Exception
   {
      String base = requireBase(path, principal);
      WizDatabaseDefinition definition = request == null ? null : request.definition();

      if(definition == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definition is required");
      }

      String name = request.name() == null || request.name().isBlank() ? null : request.name().trim();

      if(name != null) {
         requireReadable(base, name, principal);
      }

      DatabaseDefinition database = mapper.toDatabaseDefinition(definition, name);
      ConnectionStatus status;

      try {
         status = databaseDatasourcesService.testDataSourceConnection(
            base, database, principal, true);
      }
      catch(MessageException ex) {
         return new WizConnectionTestResult(false, ex.getMessage());
      }

      return new WizConnectionTestResult(status != null && status.isConnected(),
                                         status == null ? null : status.getStatus());
   }

   /**
    * Saves the base database with the complete additional-connection list.
    *
    * <p>The base is re-read from the repository and passed back unchanged (its name and oldName are
    * its own leaf, so the stored password is recovered), exactly as the Portal editor round-trips
    * it; permissions stay null so the save cannot write permissions as a side effect.</p>
    */
   private WizDatabaseSaveResult save(String base, List<DatabaseDefinition> additionals,
                                      String savedName, Principal principal)
      throws Exception
   {
      DatabaseDefinition definition = databaseDatasourcesService.getDatabaseDefinition(base, principal);
      String leaf = lastSegment(base);
      definition.setName(leaf);
      definition.setOldName(leaf);

      DataSourceSettingsModel model = DataSourceSettingsModel.builder()
         .dataSource(definition)
         .additionalDataSources(additionals.toArray(new DatabaseDefinition[0]))
         .uploadEnabled(false)
         .build();
      ConnectionStatus status;

      try {
         status = databaseDatasourcesService.saveDatabase(
            base, model, ActionRecord.ACTION_NAME_EDIT, principal);
      }
      catch(MessageException ex) {
         LOG.info("Additional connection save rejected for \"{}\": {}", base, ex.getMessage());

         return new WizDatabaseSaveResult(false, "UNKNOWN", null, ex.getMessage());
      }

      String reason = status == null ? null : status.getStatus();

      if(reason == null) {
         return new WizDatabaseSaveResult(true, null, savedName == null ? base : base + "/" + savedName);
      }

      String code = SAVE_FAILURE_REASONS.get(reason);

      if(code == null) {
         LOG.warn("Unrecognized additional connection save status for {}: {}", base, reason);
         code = "UNKNOWN";
      }

      return new WizDatabaseSaveResult(false, code, null, "UNKNOWN".equals(code) ? reason : null);
   }

   /** Authorizes WRITE on the base and checks that it is a JDBC database. */
   private String requireBase(String path, Principal principal) throws Exception {
      String base = path == null || path.isBlank() || "/".equals(path) ? null : path;

      if(base == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
      }

      gates.requireDataSourcePermission(base, ResourceAction.WRITE, principal);
      XDataSource dataSource = xrepository.getDataSource(base);

      if(dataSource == null) {
         throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "No data source at \"" + base + "\"");
      }

      if(!(dataSource instanceof JDBCDataSource)) {
         throw new UnsupportedDatasourceException(base, dataSource.getType());
      }

      return base;
   }

   /** The stored additional connections, as native definitions, in a list that can be changed. */
   private List<DatabaseDefinition> storedAdditionals(String base, Principal principal)
      throws Exception
   {
      DatabaseDefinition[] stored =
         databaseDatasourcesService.getAdditionalDatabaseDefinition(base, principal);

      return new ArrayList<>(stored == null ? List.of() : Arrays.asList(stored));
   }

   private void requireReadable(String base, String name, Principal principal)
      throws Exception
   {
      if(!databaseDatasourcesService.checkAdditionalPermission(
         base, name, ResourceAction.READ, principal))
      {
         throw new SecurityException("Unauthorized access to additional connection \"" + name +
                                        "\" of data source \"" + base + "\" by user " + principal);
      }
   }

   private static int indexOf(List<DatabaseDefinition> definitions, String name) {
      for(int i = 0; i < definitions.size(); i++) {
         if(name.equals(definitions.get(i).getName())) {
            return i;
         }
      }

      return -1;
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

   private static String requireConnectionName(String name) {
      String trimmed = name == null ? null : name.trim();

      if(trimmed == null || trimmed.isEmpty()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
      }

      return trimmed;
   }

   private static String lastSegment(String path) {
      int index = path.lastIndexOf('/');

      return index < 0 ? path : path.substring(index + 1);
   }

   /** The save path's status strings, as in {@code WizDatabaseController}. */
   private static final Map<String, String> SAVE_FAILURE_REASONS = Map.of(
      "Duplicate", "DUPLICATE_NAME",
      "Duplicate Folder", "DUPLICATE_FOLDER",
      "Datasource Lost", "DATASOURCE_LOST",
      "Invalid Folder", "INVALID_FOLDER");

   private static final Logger LOG = LoggerFactory.getLogger(WizAdditionalConnectionController.class);

   private final DatabaseDatasourcesService databaseDatasourcesService;
   private final SecurityEngine securityEngine;
   private final XRepository xrepository;
   private final WizDatasourceGates gates;
   private final WizDatabaseMapper mapper;
}
