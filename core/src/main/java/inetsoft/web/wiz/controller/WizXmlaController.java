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
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.data.DataSourceXmlaDefinition;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.WizMetadataRefreshRequest;
import inetsoft.web.wiz.request.WizXmlaCreateRequest;
import inetsoft.web.wiz.request.WizXmlaDraftRequest;
import inetsoft.web.wiz.service.UnsupportedDatasourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.security.Principal;

/**
 * XMLA (OLAP) data source management for the wiz bridge.
 *
 * <p>Delegates to {@code XmlaDatasourceService}, the permission-checking and audited service the
 * Portal's XMLA editor uses ({@code createNewDataSource} and {@code updateDataSource} carry
 * {@code @Audited}). The Portal's own controller gates only on {@code PORTAL_TAB Data}; this one
 * uses the per-resource rules the other wiz data controllers use: WRITE on the data source to read
 * or edit it, the create rule (WRITE on the parent folder, or {@code CREATE_DATA_SOURCE} at the
 * root) to create, and DELETE on top of WRITE for a rename. Never the site-admin gate.</p>
 *
 * <p>The password never crosses the wire on read, and a null password on write means "keep the
 * stored one": {@code createDataSource} would otherwise set the definition's null password over the
 * stored one unconditionally. Like its sibling controllers this class must not declare a local
 * {@code @ExceptionHandler}, which would take precedence over {@code WizControllerErrorHandler}.</p>
 *
 * <p>Cube/domain metadata is not edited: an update sends no {@code domain}, which the service reads
 * as "leave the stored domain unchanged". It is loaded only by {@link #refreshCubes}, which keeps
 * the edits already made to a stored domain.</p>
 */
@RestController
@RequestMapping("/api/wiz")
public class WizXmlaController {
   public WizXmlaController(XmlaDatasourceService xmlaDatasourceService,
                            SecurityEngine securityEngine,
                            XRepository xrepository)
   {
      this.xmlaDatasourceService = xmlaDatasourceService;
      this.securityEngine = securityEngine;
      this.xrepository = xrepository;
      this.gates = new WizDatasourceGates(securityEngine);
   }

   /**
    * Reads an existing XMLA data source's connection settings, password suppressed.
    *
    * @throws UnsupportedDatasourceException if the path is not an XMLA data source (422).
    */
   @GetMapping(value = "/xmla/definition", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public WizXmlaDefinition getXmlaDefinition(@PermissionPath @RequestParam("path") String path,
                                              Principal principal)
      throws Exception
   {
      String source = requirePath(path);
      gates.requireDataSourcePermission(source, ResourceAction.WRITE, principal);
      requireXmla(source);

      DataSourceXmlaDefinition stored = xmlaDatasourceService.getDataSourceModel(source, principal);
      boolean deletable =
         securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE, source,
                                        ResourceAction.DELETE);

      return toWizDefinition(stored, deletable);
   }

   /**
    * Creates an XMLA data source in a folder.
    *
    * @return the saved path, or the reason it was refused.
    */
   @PostMapping(value = "/xmla/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizXmlaSaveResult createXmla(@RequestBody WizXmlaCreateRequest request,
                                       Principal principal)
      throws Exception
   {
      WizXmlaDefinition definition = request == null ? null : request.definition();
      String name = requireName(definition);
      WizDatasourceNames.requireValidDatasourceName(name);
      String parentPath = normalizePath(request.parentPath());

      gates.requireCreatePermission(parentPath, principal);

      String fullPath = parentPath == null ? name : parentPath + "/" + name;

      if(xmlaDatasourceService.checkDuplicate(fullPath)) {
         return WizXmlaSaveResult.failed(WizXmlaSaveResult.DUPLICATE_NAME);
      }

      // A create has no stored password to keep: a null one is the caller saying "none".
      DataSourceXmlaDefinition model =
         toModel(definition, name, parentPath, null, passwordOf(definition));

      try {
         xmlaDatasourceService.createNewDataSource(model, true, principal);
      }
      catch(MessageException ex) {
         return toFailure(ex, "create", fullPath);
      }

      if(!xmlaDatasourceService.checkDuplicate(fullPath)) {
         LOG.warn("XMLA data source \"{}\" was not created", fullPath);

         return WizXmlaSaveResult.failed(
            WizXmlaSaveResult.UNKNOWN, "The data source was not created; see the server log.");
      }

      return WizXmlaSaveResult.ok(fullPath);
   }

   /**
    * Updates an existing XMLA data source. A null password keeps the stored one.
    *
    * @param path       the data source's current full path, and the sole source of its old name.
    * @param definition the new settings; its name is the new leaf name.
    *
    * @throws UnsupportedDatasourceException if the path is not an XMLA data source (422).
    */
   @PostMapping(value = "/xmla/update", produces = MediaType.APPLICATION_JSON_VALUE)
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public WizXmlaSaveResult updateXmla(@PermissionPath @RequestParam("path") String path,
                                       @RequestBody WizXmlaDefinition definition,
                                       Principal principal)
      throws Exception
   {
      String source = requirePath(path);
      gates.requireDataSourcePermission(source, ResourceAction.WRITE, principal);
      String name = requireName(definition);
      String oldName = lastSegment(source);

      // Only a real rename is checked: a stored name that predates the rule must stay re-savable.
      if(!name.equals(oldName)) {
         WizDatasourceNames.requireValidDatasourceName(name);
      }

      if(!xmlaDatasourceService.checkDuplicate(source)) {
         return WizXmlaSaveResult.failed(WizXmlaSaveResult.DATASOURCE_LOST);
      }

      XMLADataSource stored = requireXmla(source);
      String parentPath = parentOf(source);
      String fullPath = parentPath == null ? name : parentPath + "/" + name;

      if(!fullPath.equals(source) && xmlaDatasourceService.checkDuplicate(fullPath)) {
         return WizXmlaSaveResult.failed(WizXmlaSaveResult.DUPLICATE_NAME);
      }

      // The service demands DELETE for a rename but signals the denial with a MessageException,
      // which would read as "could not save" rather than "not allowed".
      if(!name.equals(oldName)) {
         gates.requireDataSourcePermission(source, ResourceAction.DELETE, principal);
      }

      DataSourceXmlaDefinition model =
         toModel(definition, name, parentPath, oldName, passwordOrStored(definition, stored));

      try {
         // The bare old name: updateDataSource re-prefixes the definition's parent path itself.
         xmlaDatasourceService.updateDataSource(oldName, model, principal);
      }
      catch(MessageException ex) {
         return toFailure(ex, "update", fullPath);
      }

      return WizXmlaSaveResult.ok(fullPath);
   }

   /**
    * Loads the cube metadata of an existing XMLA data source from its endpoint and saves it.
    *
    * <p>Needs WRITE on the data source, as the Portal save that persists the same domain does. The
    * stored domain's edited cube settings are kept. The result names the endpoint when it could
    * not be reached, and is ok only once the saved domain has been read back.</p>
    *
    * @throws UnsupportedDatasourceException if the path is not an XMLA data source (422).
    */
   @PostMapping(value = "/xmla/refresh-cubes", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizXmlaRefreshResult refreshCubes(@RequestBody WizMetadataRefreshRequest request,
                                            Principal principal)
      throws Exception
   {
      String source = requirePath(request == null ? null : request.path());
      gates.requireDataSourcePermission(source, ResourceAction.WRITE, principal);
      requireXmla(source);

      XmlaDatasourceService.CubeRefreshResult result;

      try {
         result = xmlaDatasourceService.refreshStoredCubes(source);
      }
      catch(FileNotFoundException ex) {
         return WizXmlaRefreshResult.failed(WizXmlaRefreshResult.DATASOURCE_LOST);
      }

      return new WizXmlaRefreshResult(result.ok(), result.ok() ? null : result.status(),
                                      result.ok() ? result.cubeCount() : null, result.url(),
                                      result.message());
   }

   /**
    * Lists the catalogs an XMLA endpoint offers.
    *
    * <p>With a {@code path} this needs WRITE on that data source and a null password means the
    * stored one; without one it is a draft and needs only the create rule at the root. When only a
    * path is given the stored settings are used.</p>
    */
   @PostMapping(value = "/xmla/catalogs", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizXmlaCatalogs getCatalogs(@RequestBody WizXmlaDraftRequest request,
                                      Principal principal)
      throws Exception
   {
      DataSourceXmlaDefinition model = draftModel(request, principal);

      try {
         return new WizXmlaCatalogs(xmlaDatasourceService.getCatalogs(model));
      }
      catch(Exception ex) {
         // A connection problem with the endpoint: the caller's input, not a server fault. Not only
         // MessageException: the catalog discovery itself runs after the service's own try block
         // and throws the raw IOException (connection refused, bad response).
         LOG.info("XMLA catalog listing failed: {}", ex.getMessage());
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, describe(ex));
      }
   }

   /**
    * Opens a connection with the supplied settings without saving them.
    *
    * <p>Gated like {@link #getCatalogs}: naming a stored data source turns this into a credential
    * read (the stored password is used against whatever URL the request names), so it needs WRITE
    * on it.</p>
    */
   @PostMapping(value = "/xmla/test", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizConnectionTestResult testXmla(@RequestBody WizXmlaDraftRequest request,
                                           Principal principal)
      throws Exception
   {
      DataSourceXmlaDefinition model = draftModel(request, principal);
      ConnectionStatus status = xmlaDatasourceService.testConnect(model);

      return new WizConnectionTestResult(status != null && status.isConnected(),
                                         status == null ? null : status.getStatus());
   }

   /** Resolves and authorizes a draft definition for the catalogs and test endpoints. */
   private DataSourceXmlaDefinition draftModel(WizXmlaDraftRequest request, Principal principal)
      throws Exception
   {
      String path = normalizePath(request == null ? null : request.path());
      WizXmlaDefinition definition = request == null ? null : request.definition();

      if(path == null) {
         if(definition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definition is required");
         }

         gates.requireCreatePermission(null, principal);

         return toModel(definition, nameOr(definition.name(), ""), null, null,
                        passwordOf(definition));
      }

      gates.requireDataSourcePermission(path, ResourceAction.WRITE, principal);
      XMLADataSource stored = requireXmla(path);

      if(definition == null) {
         return xmlaDatasourceService.getDataSourceModel(path, principal);
      }

      return toModel(definition, nameOr(definition.name(), lastSegment(path)), parentOf(path),
                     lastSegment(path), passwordOrStored(definition, stored));
   }

   private XMLADataSource requireXmla(String path) throws Exception {
      XDataSource dataSource = xrepository.getDataSource(path);

      if(dataSource == null) {
         throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "No data source at \"" + path + "\"");
      }

      if(!(dataSource instanceof XMLADataSource xmla)) {
         throw new UnsupportedDatasourceException(path, dataSource.getType());
      }

      return xmla;
   }

   private static String passwordOf(WizXmlaDefinition definition) {
      return definition.authentication() == null ? null : definition.authentication().password();
   }

   /** The caller's password, or the stored one when the caller sent none (null = keep). */
   private static String passwordOrStored(WizXmlaDefinition definition, XMLADataSource stored) {
      String password = passwordOf(definition);

      return password == null ? stored.getPassword() : password;
   }

   private static DataSourceXmlaDefinition toModel(WizXmlaDefinition definition, String name,
                                                   String parentPath, String oldName,
                                                   String password)
   {
      DataSourceXmlaDefinition model = new DataSourceXmlaDefinition();
      model.setName(name);
      model.setOldName(oldName);
      model.setDescription(definition.description());
      model.setParentPath(parentPath == null ? "" : parentPath);
      model.setDatasource(definition.datasource());
      model.setDatasourceName(definition.datasourceName());
      model.setDatasourceInfo(definition.datasourceInfo());
      model.setCatalogName(definition.catalogName());
      model.setUrl(definition.url());
      model.setLogin(definition.login());
      model.setUseCredential(definition.useCredential());
      model.setCredentialId(definition.credentialId());

      if(definition.authentication() != null) {
         model.setUser(definition.authentication().userName());
      }

      model.setPassword(password);

      return model;
   }

   /** Never carries the password, and never the cube/domain metadata. */
   private static WizXmlaDefinition toWizDefinition(DataSourceXmlaDefinition model,
                                                    boolean deletable)
   {
      return new WizXmlaDefinition(
         model.getName(), model.getDescription(), model.getDatasource(), model.getDatasourceName(),
         model.getDatasourceInfo(), model.getCatalogName(), model.getUrl(), model.isLogin(),
         new WizXmlaAuthentication(model.getUser(), null), model.isUseCredential(),
         model.isUseCredential() ? model.getCredentialId() : null, deletable);
   }

   private WizXmlaSaveResult toFailure(MessageException ex, String operation, String path) {
      String invalidFolder =
         Catalog.getCatalog().getString("data.datasources.invalidParentFolder");

      if(invalidFolder != null && invalidFolder.equals(ex.getMessage())) {
         return WizXmlaSaveResult.failed(WizXmlaSaveResult.INVALID_FOLDER);
      }

      LOG.info("XMLA data source {} rejected for \"{}\": {}", operation, path, ex.getMessage());

      return WizXmlaSaveResult.failed(WizXmlaSaveResult.UNKNOWN, ex.getMessage());
   }

   private static String requireName(WizXmlaDefinition definition) {
      if(definition == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "definition is required");
      }

      String name = definition.name() == null ? null : definition.name().trim();

      if(name == null || name.isEmpty()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
      }

      return name;
   }

   private static String describe(Exception ex) {
      String message = ex.getMessage();

      return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
   }

   private static String nameOr(String name, String fallback) {
      return name == null || name.isBlank() ? fallback : name.trim();
   }

   private static String requirePath(String path) {
      String normalized = normalizePath(path);

      if(normalized == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
      }

      return normalized;
   }

   private static String normalizePath(String path) {
      return path == null || path.isBlank() || "/".equals(path) ? null : path;
   }

   private static String lastSegment(String path) {
      int index = path.lastIndexOf('/');

      return index < 0 ? path : path.substring(index + 1);
   }

   private static String parentOf(String path) {
      int index = path.lastIndexOf('/');

      return index < 0 ? null : path.substring(0, index);
   }

   private static final Logger LOG = LoggerFactory.getLogger(WizXmlaController.class);

   private final XmlaDatasourceService xmlaDatasourceService;
   private final SecurityEngine securityEngine;
   private final XRepository xrepository;
   private final WizDatasourceGates gates;
}
