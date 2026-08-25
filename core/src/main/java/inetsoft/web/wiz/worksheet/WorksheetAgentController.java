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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.PropertyMeta;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.text.TextOutput;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.filereader.CSVLoader;
import inetsoft.uql.util.filereader.DateParseInfo;
import inetsoft.uql.util.filereader.ExcelFileInfo;
import inetsoft.uql.util.filereader.ExcelFileReader;
import inetsoft.uql.util.filereader.ExcelFileSupport;
import inetsoft.util.Catalog;
import inetsoft.util.FileSystemService;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSLayoutGraphEvent;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.service.TabularEndpointBindingSupport;
import inetsoft.web.wiz.script.PaneScopeService;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import inetsoft.web.wiz.worksheet.model.WorksheetPropertiesModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.security.Principal;
import java.util.*;
import java.util.List;

/**
 * REST controller that exposes worksheet editing capabilities to the wiz sheet agent.
 *
 * <p>All endpoints except {@link #detach} are protected by the {@link SheetAgentFeature}
 * flag; a disabled flag returns {@code 403 Forbidden}.</p>
 *
 * <p>URL prefix: {@code /api/wiz/v1/agent/worksheet}</p>
 */
@RestController
public class WorksheetAgentController {

   @Autowired
   public WorksheetAgentController(SheetAgentFeature feature,
                                   SheetJoinService joinService,
                                   SheetSessionService sessionService,
                                   WorksheetReadService readService,
                                   WorksheetEditService editService,
                                   WorksheetService worksheetService,
                                   WorksheetPreviewService previewService,
                                   SheetAgentBroadcastService broadcast,
                                   XRepository xrepository,
                                   AssetRepository assetRepository,
                                   inetsoft.web.wiz.service.MetadataApiService metadataApiService,
                                   QueryManagerService queryManagerService,
                                   LayoutGraphService layoutGraphService,
                                   DataSourceService dataSourceService,
                                   SecurityEngine securityEngine)
   {
      this.feature = feature;
      this.joinService = joinService;
      this.sessionService = sessionService;
      this.readService = readService;
      this.editService = editService;
      this.worksheetService = worksheetService;
      this.previewService = previewService;
      this.broadcast = broadcast;
      this.xrepository = xrepository;
      this.assetRepository = assetRepository;
      this.metadataApiService = metadataApiService;
      this.queryManagerService = queryManagerService;
      this.layoutGraphService = layoutGraphService;
      this.dataSourceService = dataSourceService;
      this.securityEngine = securityEngine;
   }

   // ---------------------------------------------------------------------------
   // Endpoints
   // ---------------------------------------------------------------------------

   /**
    * Join a worksheet session using a single-use pairing code.
    *
    * @param code the pairing code minted by the browser-side mint endpoint
    * @param user the authenticated agent principal
    * @return session token and identifying metadata
    * @throws PairingException if the code is invalid/expired, the user doesn't match,
    *                          or the feature flag is off (feature gate throws this first
    *                          via {@link SheetJoinService#join})
    */
   public record JoinRequest(String code) {}

   @PostMapping("/api/wiz/v1/agent/worksheet/join")
   public JoinResponse join(@RequestBody JoinRequest body, Principal user) throws PairingException {
      String code = body.code();
      requireEnabled();
      JoinSession session = joinService.join(code, user);
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity(),
                              session.sheetType().name().toLowerCase(), session.editorContext());
   }

   /**
    * Read the current structural model of the worksheet identified by {@code sessionToken}.
    *
    * @param sessionToken the token obtained at join time
    * @param user         the authenticated agent principal
    * @return a snapshot of tables, columns, filters, aggregates, joins, and sort specs
    * @throws PairingException if the session is invalid/expired or the runtime is not found
    */
   @GetMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/model")
   public WorksheetModel read(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);
      RuntimeWorksheet rws = editService.resolve(sessionToken, user);
      return readService.read(rws);
   }

   /**
    * Apply a single structural mutation to the worksheet.
    *
    * @param sessionToken the token obtained at join time
    * @param req          the edit operation and its parameters
    * @param user         the authenticated agent principal
    * @throws PairingException if the session is invalid/expired, the runtime is not found,
    *                          or the requested operation is unknown
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/edit")
   public void edit(@PathVariable String sessionToken,
                    @RequestBody EditRequest req,
                    Principal user)
      throws Exception
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);
      editOp(sessionToken, req, user);
   }

   /**
    * The op dispatcher itself, WITHOUT the whole-sheet session gate {@link #edit} applies.
    *
    * <p>Exists for exactly one in-process caller: {@link WorksheetScriptService}, the front door
    * for {@code worksheetExpression}/{@code worksheetCondition}. That path is legitimately
    * pane-scoped and has ALREADY run {@code PaneScopeService.check} against the specific
    * {@link inetsoft.web.wiz.script.ScriptTarget} it is writing, which is the narrow check the
    * grant actually authorizes; routing it through {@link #edit} would refuse it on the broad
    * check instead, and reimplementing the write to avoid that would be a second writer -- the
    * one thing that service exists not to be.
    *
    * <p>Not a hole: it is not mapped to any URL, so nothing outside this JVM can reach it, and
    * its one caller is scoped tighter than {@link #edit} is, not looser.
    */
   public void editOp(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      requireEnabled();

      // add_table with endpoint (named connector) or suffix (generic/custom REST-JSON) binds a
      // TabularTableAssembly instead of a physical table or logical-model entity. Checked before
      // every other add_table branch below so its own contradiction errors fire first -- in
      // particular before the plain "add_table with a datasource" branch, which would otherwise
      // route a request carrying both datasource and endpoint/suffix into addBoundTable.
      if("add_table".equals(req.op()) &&
         ((req.endpoint() != null && !req.endpoint().isBlank()) ||
          (req.suffix() != null && !req.suffix().isBlank())))
      {
         boolean hasEndpoint = req.endpoint() != null && !req.endpoint().isBlank();
         boolean hasSuffix = req.suffix() != null && !req.suffix().isBlank();

         if(hasEndpoint && hasSuffix) {
            throw new PairingException("add_table cannot carry both endpoint and suffix -- " +
               "choose the named-connector form (endpoint) or the custom form (suffix), not both.");
         }

         if(req.datasource() == null || req.datasource().isBlank()) {
            throw new PairingException(
               "datasource is required when " + (hasEndpoint ? "endpoint" : "suffix") +
               " is specified.");
         }

         if(req.logicalModel() != null && !req.logicalModel().isBlank()) {
            throw new PairingException("add_table cannot carry both " +
               (hasEndpoint ? "endpoint" : "suffix") + " and logicalModel -- choose one.");
         }

         if(req.schema() != null || req.catalog() != null) {
            throw new PairingException("add_table cannot carry schema/catalog together with " +
               (hasEndpoint ? "endpoint" : "suffix") + " -- those name a physical table; " +
               (hasEndpoint ? "an endpoint" : "a custom REST-JSON suffix") +
               " has no schema/catalog.");
         }

         addTabularTable(sessionToken, req, user);
         return;
      }

      // add_table with logicalModel requires datasource.
      if("add_table".equals(req.op()) && req.logicalModel() != null
         && !req.logicalModel().isBlank()
         && (req.datasource() == null || req.datasource().isBlank()))
      {
         throw new PairingException(
            "datasource is required when logicalModel is specified.");
      }

      // add_table with a datasource needs RuntimeWorksheet for initColumnSelection.
      if("add_table".equals(req.op()) && req.datasource() != null
         && !req.datasource().isBlank())
      {
         if(req.logicalModel() != null && !req.logicalModel().isBlank()) {
            addLogicalModelTable(sessionToken, req, user);
         }
         else {
            addBoundTable(sessionToken, req, user);
         }
         return;
      }

      // add_named_group with a datasource scopes "Only For" directly to a datasource/logical-model
      // or physical-table path -- matching what a human produces via the Composer's own "Add
      // Grouping" dialog -- rather than attaching to a column on an existing worksheet table.
      // Needs the same permission checks + XLogicalModel/JDBC metadata lookups as add_table, so it
      // is dispatched here rather than through the plain Editor.
      if("add_named_group".equals(req.op()) && req.datasource() != null
         && !req.datasource().isBlank())
      {
         if(req.table() != null || req.column() != null || req.type() != null) {
            throw new PairingException(
               "add_named_group: datasource is mutually exclusive with table/column and type.");
         }

         if(req.sourceTable() == null || req.sourceTable().isBlank()) {
            throw new PairingException(
               "sourceTable is required when datasource is specified for add_named_group.");
         }

         if(req.attribute() == null || req.attribute().isBlank()) {
            throw new PairingException(
               "attribute is required when datasource is specified for add_named_group.");
         }

         addDatasourceScopedNamedGroup(sessionToken, req, user);
         return;
      }

      // add_named_group's datasource-scoped fields require 'datasource' -- without this guard,
      // a caller that supplies sourceTable/attribute/logicalModel/schema/catalog but omits (or
      // blanks) datasource would silently fall through to the plain Editor.addNamedGroup(table,
      // column, type, ...) below with table/column/type all null, creating a standalone
      // string-typed grouping and discarding what the caller actually asked for, with no error.
      if("add_named_group".equals(req.op()) &&
         (req.sourceTable() != null || req.attribute() != null || req.logicalModel() != null ||
            req.schema() != null || req.catalog() != null))
      {
         throw new PairingException(
            "add_named_group: sourceTable/attribute/logicalModel/schema/catalog require " +
               "'datasource' to be set.");
      }

      // set_variable_values needs AssetQuerySandbox, not just Editor.
      if("set_variable_values".equals(req.op())) {
         setVariableValues(sessionToken, req, user);
         return;
      }

      // convert_to_embedded needs AssetQuerySandbox for data population.
      if("convert_to_embedded".equals(req.op())) {
         convertToEmbedded(sessionToken, req, user);
         return;
      }

      // edit_sql_query needs RuntimeWorksheet for SQL parsing and column re-init.
      if("edit_sql_query".equals(req.op())) {
         editSqlQuery(sessionToken, req, user);
         return;
      }

      // update_mirror needs AssetRepository + Principal for the refresh.
      if("update_mirror".equals(req.op())) {
         updateMirror(sessionToken, req, user);
         return;
      }

      // auto_layout uses mxGraph — needs LayoutGraphService.
      if("auto_layout".equals(req.op())) {
         autoLayout(sessionToken, req, user);
         return;
      }

      // refresh_data clears the query cache and forces re-execution.
      if("refresh_data".equals(req.op())) {
         refreshData(sessionToken, req, user);
         return;
      }

      // insert_column manipulates XEmbeddedTable directly.
      if("insert_column".equals(req.op())) {
         insertColumn(sessionToken, req, user);
         return;
      }

      // reorder_concat_subtables calls CompositeTableAssembly.reorderTableAssemblies.
      if("reorder_concat_subtables".equals(req.op())) {
         reorderConcatSubtables(sessionToken, req, user);
         return;
      }

      // add_variable needs RuntimeWorksheet to add the assembly.
      if("add_variable".equals(req.op())) {
         addVariableFromEdit(sessionToken, req, user);
         return;
      }

      editService.apply(sessionToken, user, editor -> dispatch(editor, req));
   }

   /**
    * Create a {@link PhysicalBoundTableAssembly} from a datasource table reference.
    *
    * <p>The {@code req.table()} field contains the physical table path (e.g.
    * {@code "schema.tableName"} or just {@code "tableName"}), and {@code req.datasource()}
    * contains the datasource name. A {@link SourceInfo} of type
    * {@link SourceInfo#PHYSICAL_TABLE} is created, the assembly is added to the worksheet,
    * and {@link AssetEventUtil#initColumnSelection} populates the column metadata.</p>
    */
   private void addBoundTable(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      String datasourceName = req.datasource();
      String tablePath = req.table();

      if(tablePath == null || tablePath.isBlank()) {
         throw new PairingException("table is required for add_table.");
      }

      // Verify ACCESS permission on physical-table binding ("Visual Composer -> Physical
      // Table"), mirroring the checks in OpenWorksheetController.checkPermission() and
      // TableAssemblyModelFactory.createModelFrom().
      if(!securityEngine.checkPermission(user, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS)) {
         throw new SecurityException(
            Catalog.getCatalog().getString("composer.ws.boundPhysicalTableForbidden"));
      }

      // Verify READ on the datasource BEFORE any JDBC metadata probe. getJDBCDatasource/
      // getTableMetaData do no permission check of their own, so without this an unauthorized
      // datasource would still be probed (getTableDetails below only checks READ afterwards).
      // Mirrors addLogicalModelTable / addSqlQuery.
      if(datasourceName != null &&
         !dataSourceService.checkPermission(datasourceName, ResourceAction.READ, user))
      {
         throw new PairingException(
            "Access denied: no READ permission on datasource " + datasourceName);
      }

      // Get the qualified table name via metadata lookup.
      JDBCDataSource jdbcDs = metadataApiService.getJDBCDatasource(datasourceName);
      XNode tableMetaData = metadataApiService.getTableMetaData(
         jdbcDs, req.catalog(), req.schema(), tablePath);

      if(tableMetaData == null) {
         throw new PairingException("Table not found: " + tablePath +
            " (datasource=" + datasourceName +
            ", schema=" + req.schema() +
            ", catalog=" + req.catalog() + ")");
      }

      String qname = inetsoft.uql.jdbc.util.SQLTypes.getSQLTypes(jdbcDs)
         .getQualifiedName(tableMetaData, jdbcDs);
      String tableType = (String) tableMetaData.getAttribute("type");

      // Get column metadata via the table details endpoint (queries JDBC for columns).
      inetsoft.web.wiz.model.DatabaseTableMeta tableMeta =
         metadataApiService.getTableDetails(datasourceName, tablePath,
            req.catalog(), req.schema(), user);

      ColumnSelection columns = new ColumnSelection();

      for(inetsoft.web.wiz.model.DatabaseTableMeta.ColumnMeta colMeta : tableMeta.getColumns()) {
         AttributeRef attr = new AttributeRef(null, colMeta.getName());
         ColumnRef ref = new ColumnRef(attr);

         if(colMeta.getType() != null) {
            ref.setDataType(colMeta.getType());
         }

         columns.addAttribute(ref);
      }

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         String assemblyName = AssetUtil.normalizeTable(tablePath);
         assemblyName = AssetUtil.getNextName(ws, assemblyName);

         PhysicalBoundTableAssembly assembly =
            new PhysicalBoundTableAssembly(ws, assemblyName);

         SourceInfo sinfo = new SourceInfo(
            SourceInfo.PHYSICAL_TABLE, datasourceName, qname);
         sinfo.setProperty(SourceInfo.SCHEMA, req.schema());
         sinfo.setProperty(SourceInfo.CATALOG, req.catalog());
         sinfo.setProperty(SourceInfo.TABLE_TYPE, tableType);
         assembly.setSourceInfo(sinfo);
         assembly.setColumnSelection(columns);

         positionBelowExisting(ws, assembly);
         ws.addAssembly(assembly);
         return null;
      });
   }

   /**
    * Create a {@link BoundTableAssembly} from a logical model entity.
    *
    * <p>{@code req.datasource()} is the datasource name, {@code req.logicalModel()} is the
    * logical model name, and {@code req.table()} is the entity name within that model.
    * A {@link SourceInfo} of type {@link SourceInfo#MODEL} is created and the column
    * selection is populated from the entity's attributes.</p>
    */
   private void addLogicalModelTable(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      String datasourceName = req.datasource();
      String logicalModelName = req.logicalModel();
      String entityName = req.table();

      if(entityName == null || entityName.isBlank()) {
         throw new PairingException("table (entity name) is required for add_table with a logical model.");
      }

      // Verify READ permission on the datasource and logical model,
      // mirroring the checks in DatasourceMetaApiController.listLogicalModels().
      if(!dataSourceService.checkPermission(datasourceName, ResourceAction.READ, user)) {
         throw new PairingException("Access denied: no READ permission on datasource " + datasourceName);
      }

      XDataModel dataModel = dataSourceService.getDataModel(datasourceName);

      if(dataModel == null) {
         throw new PairingException("No data model found for datasource: " + datasourceName);
      }

      XLogicalModel lm = dataModel.getLogicalModel(logicalModelName);

      if(lm == null) {
         throw new PairingException("Logical model not found: " + logicalModelName
            + " (datasource=" + datasourceName + ")");
      }

      AssetEntry modelEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
         AssetEntry.Type.LOGIC_MODEL, datasourceName + "/" + logicalModelName, null);
      modelEntry = dataSourceService.getModelAssetEntry(modelEntry);

      if(modelEntry == null ||
         !dataSourceService.checkPermission(modelEntry, ResourceAction.READ, user))
      {
         throw new PairingException("Access denied: no READ permission on logical model "
            + logicalModelName + " (datasource=" + datasourceName + ")");
      }

      XEntity entity = lm.getEntity(entityName);

      if(entity == null) {
         throw new PairingException("Entity not found: " + entityName
            + " (logicalModel=" + logicalModelName + ", datasource=" + datasourceName + ")");
      }

      ColumnSelection columns = new ColumnSelection();
      Enumeration<XAttribute> attrEnum = entity.getAttributes();

      while(attrEnum.hasMoreElements()) {
         XAttribute attr = attrEnum.nextElement();
         AttributeRef attributeRef = new AttributeRef(entityName, attr.getName());
         ColumnRef ref = new ColumnRef(attributeRef);

         if(attr.getDataType() != null) {
            ref.setDataType(attr.getDataType());
         }

         columns.addAttribute(ref);
      }

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         String assemblyName = AssetUtil.normalizeTable(entityName);
         assemblyName = AssetUtil.getNextName(ws, assemblyName);

         BoundTableAssembly assembly = new BoundTableAssembly(ws, assemblyName);

         SourceInfo sinfo = new SourceInfo(
            SourceInfo.MODEL, datasourceName, logicalModelName);
         assembly.setSourceInfo(sinfo);
         assembly.setColumnSelection(columns);

         positionBelowExisting(ws, assembly);
         ws.addAssembly(assembly);
         return null;
      });
   }

   /**
    * Create a {@link TabularTableAssembly} bound either to a named REST/JSON connector's
    * pre-built endpoint (+ optional pre-built lookup chain), or to a generic/custom REST-JSON
    * datasource's hand-authored URL suffix (+ optional hand-authored custom lookup chain).
    *
    * <p>A datasource resolves to exactly one {@code TabularQuery} concrete class via
    * {@link TabularUtil#createQuery}, never both shapes for the same datasource -- so which of
    * the two this method builds is decided by which property the RESOLVED query class actually
    * exposes ({@code pmap.get("endpoint")}), cross-checked against which field the CALLER
    * supplied, rather than trusting the caller's field choice alone: setting {@code suffix}
    * directly on a named connector's query is a silent no-op (its suffix is derived from
    * {@code endpoint} instead), so a caller confusing the two forms is refused here rather than
    * silently building a table against a contract nobody asked for.</p>
    *
    * <p>Shares {@link TabularEndpointBindingSupport} with
    * {@link WorksheetTableService#buildTabularTable} (the wiz-services {@code /ws/table} write
    * path), which already builds the same kind of {@code TabularTableAssembly} from its own
    * {@code TabularSource} request shape.</p>
    */
   private void addTabularTable(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      String dsName = req.datasource();

      if(!dataSourceService.checkPermission(dsName, ResourceAction.READ, user)) {
         throw new PairingException("Access denied: no READ permission on datasource " + dsName);
      }

      XDataSource dataSource = xrepository.getDataSource(dsName);

      if(dataSource == null) {
         throw new PairingException("Data source not found: " + dsName);
      }

      if(!(dataSource instanceof TabularDataSource)) {
         throw new PairingException("'" + dsName + "' is a " + dataSource.getType() +
            " data source, not a tabular/REST one, so it has no endpoints to call. Use " +
            "datasource+schema+table for a physical table, or datasource+logicalModel for a " +
            "logical model entity.");
      }

      TabularQuery query = TabularUtil.createQuery(dsName);

      if(query == null) {
         throw new PairingException("Could not create a query for data source '" + dsName +
            "' -- its connector plugin may not be loaded.");
      }

      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      boolean namedConnector = pmap.get("endpoint") != null;
      String suffix;

      if(req.endpoint() != null && !req.endpoint().isBlank()) {
         if(!namedConnector) {
            throw new PairingException("'" + dsName + "' has no predefined endpoint catalogue " +
               "-- use suffix (+ optional customLookups) instead of endpoint.");
         }

         suffix = TabularEndpointBindingSupport.applyEndpointContract(query, pmap, req.endpoint(),
            null /* no parameters on this op yet -- see the design doc's flagged decision */,
            null, null, null, dsName);
         TabularEndpointBindingSupport.requireRowCapWhenPaged(query, req.endpoint(), dsName);

         if(req.lookup() != null && !req.lookup().isEmpty()) {
            TabularEndpointBindingSupport.applyLookupChain(query, pmap, req.lookup(),
               req.lookupExpandArrays(), req.lookupTopLevelOnly(), req.endpoint(), dsName);
         }
      }
      else {
         if(namedConnector) {
            throw new PairingException("'" + dsName + "' has a predefined endpoint catalogue -- " +
               "use endpoint (+ optional lookup) instead of suffix; see list_endpoint_lookups.");
         }

         suffix = TabularEndpointBindingSupport.applyCustomSuffix(query, pmap, req.suffix(), null,
            dsName);
         TabularEndpointBindingSupport.requireRowCapWhenPaged(query, req.suffix(), dsName);

         if(req.customLookups() != null && !req.customLookups().isEmpty()) {
            TabularEndpointBindingSupport.applyCustomLookupChain(query, pmap, req.customLookups(),
               dsName);
         }
      }

      String tableName = req.table();

      if(tableName == null || tableName.isBlank()) {
         throw new PairingException("table (the desired worksheet table name) is required for " +
            "add_table with endpoint/suffix.");
      }

      String target = namedConnector ? req.endpoint() : req.suffix();

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         String assemblyName = AssetUtil.getNextName(ws, AssetUtil.normalizeTable(tableName));
         TabularTableAssembly assembly = new TabularTableAssembly(ws, assemblyName);
         TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) assembly.getTableInfo();
         info.setQuery(query);
         info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, dsName, dsName));

         positionBelowExisting(ws, assembly);
         ws.addAssembly(assembly);

         // The live HTTP call -- same call TabularQueryDialogService.setUpTable and
         // WorksheetTableService.buildTabularTable both make; a tabular query has no columns
         // until one response has been parsed.
         assembly.loadColumnSelection(new VariableTable(), true, null);

         ColumnSelection columns = assembly.getColumnSelection(false);

         if(columns == null || columns.getAttributeCount() == 0) {
            // Mirrors WorksheetTableService.buildTabularTable's empty-column check -- without this
            // the assembly persists with zero columns and the agent is told "success" for a table
            // nothing can bind to.
            throw new PairingException("The request to '" + target + "' of '" + dsName +
               "' returned no columns. URL suffix sent: " + suffix + ". Check the parameter " +
               "values and datasource credentials -- see the server log for the cause.");
         }

         return null;
      });
   }

   /**
    * Creates a {@link DefaultNamedGroupAssembly} scoped directly to a datasource/logical-model
    * or physical-table attribute -- exactly what a human produces via the Composer's own "Add
    * Grouping" dialog ("Only For" + "Attribute"), independent of any worksheet table. See
    * {@code GroupingAssemblyDialogService#setGroupingAssemblyDialogProperties} for the
    * human-driven equivalent this mirrors, and {@link #addLogicalModelTable}/{@link
    * #addBoundTable} for the permission-check and metadata-lookup patterns reused here.
    *
    * <p>This is a different, orthogonal mode from the {@code table}+{@code column} attached
    * grouping in {@code WorksheetEditService.Editor#addNamedGroup}: that mode intentionally
    * records {@code attachedSource} as the worksheet table's own name -- a convention {@code
    * CalcTableService}/{@code FieldRefFactory} rely on to resolve a chart/table/crosstab/
    * calc-table's {@code field.namedGroup} binding -- so a grouping created here is not
    * resolvable through that same binding-time matching, and is not attached to any worksheet
    * table at all.</p>
    */
   private void addDatasourceScopedNamedGroup(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      String datasourceName = req.datasource();
      String logicalModelName = req.logicalModel();
      String sourceTableName = req.sourceTable();
      String attributeName = req.attribute();
      String name = req.name();

      SourceInfo sinfo;
      DataRef ref;

      if(logicalModelName != null && !logicalModelName.isBlank()) {
         // Mirrors addLogicalModelTable's permission checks and XLogicalModel/XEntity lookup.
         if(!dataSourceService.checkPermission(datasourceName, ResourceAction.READ, user)) {
            throw new PairingException(
               "Access denied: no READ permission on datasource " + datasourceName);
         }

         XDataModel dataModel = dataSourceService.getDataModel(datasourceName);

         if(dataModel == null) {
            throw new PairingException("No data model found for datasource: " + datasourceName);
         }

         XLogicalModel lm = dataModel.getLogicalModel(logicalModelName);

         if(lm == null) {
            throw new PairingException("Logical model not found: " + logicalModelName
               + " (datasource=" + datasourceName + ")");
         }

         AssetEntry modelEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.LOGIC_MODEL, datasourceName + "/" + logicalModelName, null);
         modelEntry = dataSourceService.getModelAssetEntry(modelEntry);

         if(modelEntry == null ||
            !dataSourceService.checkPermission(modelEntry, ResourceAction.READ, user))
         {
            throw new PairingException("Access denied: no READ permission on logical model "
               + logicalModelName + " (datasource=" + datasourceName + ")");
         }

         XEntity entity = lm.getEntity(sourceTableName);

         if(entity == null) {
            throw new PairingException("Entity not found: " + sourceTableName
               + " (logicalModel=" + logicalModelName + ", datasource=" + datasourceName + ")");
         }

         XAttribute attr = entity.getAttribute(attributeName);

         if(attr == null) {
            throw new PairingException("Attribute not found: " + attributeName
               + " (entity=" + sourceTableName + ", logicalModel=" + logicalModelName + ")");
         }

         sinfo = new SourceInfo(SourceInfo.MODEL, datasourceName, logicalModelName);
         AttributeRef attributeRef = new AttributeRef(sourceTableName, attr.getName());
         ColumnRef cref = new ColumnRef(attributeRef);

         if(attr.getDataType() != null) {
            cref.setDataType(attr.getDataType());
         }

         ref = cref;
      }
      else {
         // Mirrors addBoundTable's permission checks and JDBC metadata lookup.
         if(!securityEngine.checkPermission(user, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS)) {
            throw new SecurityException(
               Catalog.getCatalog().getString("composer.ws.boundPhysicalTableForbidden"));
         }

         if(!dataSourceService.checkPermission(datasourceName, ResourceAction.READ, user)) {
            throw new PairingException(
               "Access denied: no READ permission on datasource " + datasourceName);
         }

         JDBCDataSource jdbcDs = metadataApiService.getJDBCDatasource(datasourceName);
         XNode tableMetaData = metadataApiService.getTableMetaData(
            jdbcDs, req.catalog(), req.schema(), sourceTableName);

         if(tableMetaData == null) {
            throw new PairingException("Table not found: " + sourceTableName +
               " (datasource=" + datasourceName +
               ", schema=" + req.schema() +
               ", catalog=" + req.catalog() + ")");
         }

         String qname = inetsoft.uql.jdbc.util.SQLTypes.getSQLTypes(jdbcDs)
            .getQualifiedName(tableMetaData, jdbcDs);
         String tableType = (String) tableMetaData.getAttribute("type");

         inetsoft.web.wiz.model.DatabaseTableMeta tableMeta =
            metadataApiService.getTableDetails(datasourceName, sourceTableName,
               req.catalog(), req.schema(), user);

         inetsoft.web.wiz.model.DatabaseTableMeta.ColumnMeta colMeta = null;

         for(inetsoft.web.wiz.model.DatabaseTableMeta.ColumnMeta cm : tableMeta.getColumns()) {
            if(attributeName.equals(cm.getName())) {
               colMeta = cm;
               break;
            }
         }

         if(colMeta == null) {
            throw new PairingException("Column not found: " + attributeName +
               " (table=" + sourceTableName + ", datasource=" + datasourceName + ")");
         }

         SourceInfo physSinfo = new SourceInfo(SourceInfo.PHYSICAL_TABLE, datasourceName, qname);
         physSinfo.setProperty(SourceInfo.SCHEMA, req.schema());
         physSinfo.setProperty(SourceInfo.CATALOG, req.catalog());
         physSinfo.setProperty(SourceInfo.TABLE_TYPE, tableType);
         sinfo = physSinfo;

         AttributeRef attributeRef = new AttributeRef(null, colMeta.getName());
         ColumnRef cref = new ColumnRef(attributeRef);

         if(colMeta.getType() != null) {
            cref.setDataType(colMeta.getType());
         }

         ref = cref;
      }

      String conditionType = ref.getDataType() != null ? ref.getDataType() : XSchema.STRING;
      List<WorksheetMutationSupport.GroupMapping> mappings = req.groupMappings();
      boolean groupOthers = req.groupOthers() != null && req.groupOthers();

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();

         NamedGroupInfo ngi = new NamedGroupInfo();
         ngi.setOthers(groupOthers ? XConstants.GROUP_OTHERS : XConstants.LEAVE_OTHERS);

         if(mappings != null) {
            for(WorksheetMutationSupport.GroupMapping m : mappings) {
               ngi.setGroupCondition(m.name(),
                  WorksheetMutationSupport.buildGroupConditionList(conditionType, ref, m));
            }
         }

         DefaultNamedGroupAssembly assembly = new DefaultNamedGroupAssembly(ws, name);
         assembly.setNamedGroupInfo(ngi);
         assembly.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
         assembly.setAttachedSource(sinfo);
         assembly.setAttachedAttribute(ref);

         positionBelowExisting(ws, assembly);
         ws.addAssembly(assembly);
         return null;
      });
   }

   /**
    * Positions a newly-created assembly below every existing one on the canvas, left-aligned at
    * the same column every other agent-created assembly starts at. Shared by every {@code add_*}
    * op that builds its own assembly directly (rather than going through {@code Editor}, which
    * has its own {@code placeAssembly}): {@link #addBoundTable}, {@link #addLogicalModelTable},
    * {@link #addDatasourceScopedNamedGroup}, and {@code addSqlQuery}.
    */
   private static void positionBelowExisting(Worksheet ws, AbstractWSAssembly assembly) {
      int maxY = 0;

      for(Assembly a : ws.getAssemblies()) {
         if(!(a instanceof AbstractWSAssembly wa)) {
            continue;
         }

         Point p = wa.getPixelOffset();
         Dimension d = wa.getPixelSize();

         if(p != null && d != null) {
            maxY = Math.max(maxY, p.y + d.height);
         }
      }

      assembly.setPixelOffset(new Point(25, maxY + 40));
   }

   /**
    * Return up to {@code limit} data rows from the named table in the live worksheet.
    *
    * @param sessionToken the token obtained at join time
    * @param table        the table assembly name to query
    * @param limit        maximum rows to return (capped at 200; defaults to 50)
    * @param user         the authenticated agent principal
    * @return list of row maps, each keyed by column name
    * @throws PairingException if the session is invalid/expired, the sandbox is absent,
    *                          or the query fails
    */
   @GetMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/preview")
   public List<Map<String, Object>> preview(@PathVariable String sessionToken,
                                             @RequestParam String table,
                                             @RequestParam(defaultValue = "50") int limit,
                                             Principal user)
      throws PairingException
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);
      RuntimeWorksheet rws = editService.resolve(sessionToken, user);
      return previewService.preview(rws, table, Math.min(limit, 200));
   }

   /**
    * Request body for the save endpoint.
    *
    * @param name  optional name/path to save the worksheet as (e.g. {@code "agent_ws_1"} or
    *              {@code "My Folder/agent_ws_1"}).  Required when the worksheet is untitled
    *              (i.e. has not been saved before).  When omitted the worksheet is saved in-place.
    * @param scope optional scope — {@code "global"} (default) for the shared repository,
    *              {@code "user"} for the user's private folder.
    */
   public record SaveRequest(String name, String scope) {}

   /**
    * Persist the current in-memory worksheet state back to the asset repository.
    *
    * <p>When {@code body.name()} is provided (or the worksheet is still in temporary scope)
    * a new {@link AssetEntry} is created from the supplied name and scope (defaults to
    * {@code GLOBAL_SCOPE}) and the worksheet is saved under that path ("Save As" semantics).
    * The session entry is updated so that subsequent plain saves work without repeating
    * the name.</p>
    *
    * @param sessionToken the token obtained at join time
    * @param body         optional name for save-as
    * @param user         the authenticated agent principal
    * @throws PairingException if the session is invalid/expired, the runtime is not found,
    *                          or the worksheet is untitled and no name was supplied
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/save")
   public void save(@PathVariable String sessionToken,
                    @RequestBody SaveRequest body,
                    Principal user) throws PairingException
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);
      WorksheetEditService.ResolvedSession resolved =
         editService.resolveWithSession(sessionToken, user);
      RuntimeWorksheet rws = resolved.rws();
      String runtimeId = resolved.runtimeId();
      AssetEntry entry = rws.getEntry();

      String name = body.name() != null ? body.name().trim() : null;

      if(entry.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         if(name == null || name.isEmpty()) {
            throw new PairingException(
               "Worksheet is unsaved — provide a 'name' to save it (e.g. \"agent_ws_1\").");
         }
      }

      if(name != null && !name.isEmpty()) {
         IdentityID uname = IdentityID.getIdentityIDFromKey(user.getName());
         int assetScope = "user".equalsIgnoreCase(body.scope())
            ? AssetRepository.USER_SCOPE
            : AssetRepository.GLOBAL_SCOPE;
         IdentityID owner = assetScope == AssetRepository.USER_SCOPE ? uname : null;
         entry = new AssetEntry(assetScope, AssetEntry.Type.WORKSHEET, name,
                                owner, uname.orgID);
      }

      if(!(user instanceof XPrincipal xp)) {
         throw new PairingException("Cannot save: agent principal is not an XPrincipal (" +
                                    user.getClass().getName() + ")");
      }

      try {
         worksheetService.setWorksheet(rws.getWorksheet(), entry, xp, true, true);
         rws.setEntry(entry);
         rws.setEditable(true);
         rws.setSavePoint(rws.getCurrent());
         broadcast.broadcastSave(rws, runtimeId, user);
      }
      catch(Exception e) {
         throw new PairingException("Failed to save worksheet: " + e.getMessage(), e);
      }
   }

   /**
    * Import CSV data as a new embedded table assembly in the worksheet.
    *
    * @param sessionToken the token obtained at join time
    * @param body         name (optional) and csv string
    * @param user         the authenticated agent principal
    */
   public record ImportCsvRequest(String name, String csv, String encoding, String delimiter,
                                  Boolean delimiterTab, Boolean detectType,
                                  Boolean firstRowAsHeader, Boolean removeQuotes,
                                  Boolean unpivot, Integer headerCols)
   {
      /**
       * The common case: text plus an optional name, every setting left to its default. Spelling
       * out eight nulls at each call site would bury which ones a caller actually meant to set.
       */
      public ImportCsvRequest(String name, String csv) {
         this(name, csv, null, null, null, null, null, null, null, null);
      }
   }
   public record ImportCsvResponse(String tableName, int rows, int columns) {}

   /**
    * The import settings the Composer's own Import Data File dialog exposes.
    *
    * <p>Normalized once, here, so both transports agree: the JSON endpoint below and the
    * multipart one that follows it hand this to the same loader.
    *
    * @param encoding        charset name to decode the bytes with
    * @param delimiter       the field separator, already resolved from the Tab checkbox
    * @param detectType      convert values to a detected type; when false every column is string
    * @param firstRowAsHeader take column names from line 1; when false they become col0, col1, ...
    * @param removeQuotes    strip a value's surrounding quotes, treating them as escaping only
    * @param unpivot         reshape crosstab-shaped input into a tabular table
    * @param headerCols      with unpivot, how many leading columns stay as row identifiers
    */
   private record CsvSettings(String encoding, String delimiter, boolean detectType,
                              boolean firstRowAsHeader, boolean removeQuotes, boolean unpivot,
                              int headerCols) {}

   /**
    * Whether a charset name can be used to decode with.
    *
    * <p>{@link Charset#isSupported} answers {@code false} only for a name that is well formed but
    * unrecognized; a malformed one -- a name with a space in it, say -- throws
    * {@link IllegalCharsetNameException} instead. Both are the same mistake from the caller's side,
    * so both become the same 400 rather than one of them escaping as a 500.
    */
   private static boolean isSupportedCharset(String name) {
      try {
         return Charset.isSupported(name);
      }
      catch(IllegalCharsetNameException | UnsupportedCharsetException e) {
         return false;
      }
   }

   /**
    * Whether a charset name names UTF-8, by resolving it rather than by comparing strings, so the
    * aliases ("utf8", "UTF-8", "unicode-1-1-utf-8") all count. A name that resolves to nothing is
    * not UTF-8 either, which is the answer the JSON route wants for it.
    */
   private static boolean isUtf8(String name) {
      try {
         return StandardCharsets.UTF_8.equals(Charset.forName(name));
      }
      catch(IllegalCharsetNameException | UnsupportedCharsetException e) {
         return false;
      }
   }

   /**
    * Resolves the dialog's settings, defaulting to what the previous hand-rolled parser did so an
    * existing caller that passes none sees no change: comma-separated, types detected, line 1 as
    * the header, quotes left alone, no unpivot.
    */
   private CsvSettings csvSettings(String encoding, String delimiter, Boolean delimiterTab,
                                   Boolean detectType, Boolean firstRowAsHeader,
                                   Boolean removeQuotes, Boolean unpivot, Integer headerCols)
   {
      String encode = encoding == null || encoding.isBlank() ? "UTF-8" : encoding.trim();

      if(!isSupportedCharset(encode)) {
         throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Unsupported encoding: " + encode);
      }

      String delim;

      if(Boolean.TRUE.equals(delimiterTab)) {
         delim = "\t";
      }
      else if(delimiter == null || delimiter.isEmpty()) {
         delim = ",";
      }
      else if(delimiter.length() > 1) {
         // The dialog's own input is maxlength=1; a multi-character separator would be silently
         // truncated by the loader's splitter rather than honoured.
         throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "delimiter must be a single character (got \"" + delimiter + "\"). " +
            "For a tab, set delimiterTab instead of passing an escape sequence.");
      }
      else {
         delim = delimiter;
      }

      boolean pivot = Boolean.TRUE.equals(unpivot);
      int hcol = headerCols == null ? 1 : headerCols;

      if(hcol < 0) {
         throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "headerCols cannot be negative (got " + hcol + ")");
      }

      return new CsvSettings(encode, delim,
                             detectType == null || detectType,
                             firstRowAsHeader == null || firstRowAsHeader,
                             Boolean.TRUE.equals(removeQuotes),
                             pivot, hcol);
   }

   /**
    * Import CSV supplied inline as text.
    *
    * <p>{@code encoding} cannot mean anything on this route: the text arrived already decoded as
    * part of a JSON body, so any mis-decoding happened before the request was built. Anything but
    * UTF-8 is refused rather than quietly ignored -- a caller who set it has a file to re-send to
    * the multipart route, not a setting to drop.
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/import-csv")
   public ImportCsvResponse importCsv(@PathVariable String sessionToken,
                                      @RequestBody ImportCsvRequest body,
                                      Principal user) throws Exception
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);

      if(body.csv() == null || body.csv().isBlank()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "csv is required");
      }

      if(body.encoding() != null && !body.encoding().isBlank() &&
         !isUtf8(body.encoding().trim()))
      {
         throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "encoding cannot be honoured here: the csv text was already decoded before this " +
            "request was built. Post the file's bytes to import-csv-file with encoding=" +
            body.encoding().trim() + " instead.");
      }

      CsvSettings settings = csvSettings(
         "UTF-8", body.delimiter(), body.delimiterTab(), body.detectType(),
         body.firstRowAsHeader(), body.removeQuotes(), body.unpivot(), body.headerCols());

      // The same guard the multipart route gets: a JSON body's csv string is no less capable of
      // driving an unbounded temp-file write and loader scan than an uploaded file is.
      byte[] bytes = body.csv().getBytes(StandardCharsets.UTF_8);
      checkImportFileSize(bytes.length, "CSV");

      return importCsvBytes(sessionToken, user, body.name(), bytes, settings);
   }

   /**
    * Import CSV supplied as raw file bytes.
    *
    * <p>Separate from the JSON route rather than a flag on it, because the two differ in what they
    * can honour rather than only in shape: bytes are decoded here with the caller's {@code
    * encoding}, which is the only way a non-UTF-8 file survives the trip at all. Mirrors how
    * {@code import-excel} already takes its file.
    */
   @PostMapping(value = "/api/wiz/v1/agent/worksheet/{sessionToken}/import-csv-file",
                consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   public ImportCsvResponse importCsvFile(@PathVariable String sessionToken,
                                          @RequestPart(value = "file", required = false)
                                          MultipartFile file,
                                          @RequestParam(required = false) String name,
                                          @RequestParam(required = false) String encoding,
                                          @RequestParam(required = false) String delimiter,
                                          @RequestParam(required = false) Boolean delimiterTab,
                                          @RequestParam(required = false) Boolean detectType,
                                          @RequestParam(required = false) Boolean firstRowAsHeader,
                                          @RequestParam(required = false) Boolean removeQuotes,
                                          @RequestParam(required = false) Boolean unpivot,
                                          @RequestParam(required = false) Integer headerCols,
                                          Principal user) throws Exception
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);

      if(file == null || file.isEmpty()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
      }

      checkImportFileSize(file.getSize(), "CSV");

      CsvSettings settings = csvSettings(encoding, delimiter, delimiterTab, detectType,
                                         firstRowAsHeader, removeQuotes, unpivot, headerCols);

      return importCsvBytes(sessionToken, user, name, file.getBytes(), settings);
   }

   /**
    * The one CSV import path, shared by both transports.
    *
    * <p>Delegates to {@link CSVLoader#readCSV}, the same loader the Composer's own import dialog
    * uses, rather than parsing here. The hand-rolled parser this replaced was the origin of a
    * cluster of findings in the L2 test lane -- it could not honour an encoding, a delimiter, a
    * quote-stripping choice or a "first row is data" file at all, and its own type coercion
    * dropped values the loader keeps. A second implementation of a format this fiddly earns
    * nothing.
    *
    * <p>The result is deliberately a plain {@link EmbeddedTableAssembly} and **not** the
    * {@link SnapshotEmbeddedTableAssembly} the dialog builds. An agent-imported table has to stay
    * editable: it is the documented way to get an editable copy of data that arrived as a
    * snapshot, so turning this into a snapshot would quietly remove the only workaround there is.
    */
   private ImportCsvResponse importCsvBytes(String sessionToken, Principal user, String name,
                                            byte[] bytes, CsvSettings settings)
      throws Exception
   {
      // The product's cache directory rather than java.io.tmpdir: the bytes are the caller's data,
      // and the system temp dir is shared with whatever else runs on the host, at whatever
      // permissions the umask gives. This is the same convention the import dialog's own file
      // handling follows.
      File temp = FileSystemService.getInstance().getCacheTempFile("wiz-agent-import", "csv");

      if(temp == null) {
         throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Unable to create a temporary file for the import");
      }

      try {
         Files.write(temp.toPath(), bytes);

         List<String> types = new ArrayList<>();
         // oldTypes carries the column types of a table being re-imported over. A fresh import has
         // none -- but the loader reads and writes through this map, so it must be an empty mutable
         // map rather than null.
         Map<Object, String> oldTypes = new HashMap<>();
         XSwappableTable loaded = CSVLoader.readCSV(
            temp, settings.encoding(), settings.removeQuotes(), settings.delimiter(),
            settings.firstRowAsHeader(), settings.unpivot(), oldTypes, types,
            settings.detectType(), null, CSV_TYPE_SCAN_ROWS, 0,
            Util.getOrganizationMaxColumn(), new DateParseInfo());

         if(loaded == null || loaded.getRowCount() < 2) {
            if(loaded != null) {
               // XSwappableTable can have spilled to disk even this small; every other exit from
               // this method disposes, so this one does too.
               loaded.dispose();
            }

            throw new ResponseStatusException(
               HttpStatus.BAD_REQUEST,
               "CSV must have a header row and at least one data row" +
               (settings.firstRowAsHeader()
                  ? "" : " (firstRowAsHeader is false, so line 1 counts as data)"));
         }

         XEmbeddedTable table;

         if(settings.unpivot()) {
            XSwappableTable pivoted = AssetUtil.unpivot(loaded, settings.headerCols());

            if(pivoted != loaded) {
               loaded.dispose();
            }

            // Unpivoting reshapes the columns, so the type list readCSV produced describes a
            // table that no longer exists. Let XEmbeddedTable derive types from the new shape.
            table = new XEmbeddedTable(pivoted);
         }
         else {
            table = new XEmbeddedTable(types.toArray(new String[0]), loaded);
         }

         return createEmbeddedTable(sessionToken, user, name, table,
                                    table.getRowCount(), table.getColCount());
      }
      finally {
         if(!temp.delete()) {
            temp.deleteOnExit();
         }
      }
   }

   /**
    * How many rows {@link CSVLoader#readCSV} may scan while settling a column's type, matching
    * what the Composer's dialog passes for a full import.
    */
   private static final int CSV_TYPE_SCAN_ROWS = 50000;

   /**
    * Import an Excel file (.xls/.xlsx) as a new embedded table assembly in the worksheet.
    *
    * <p>Unlike {@link #importCsv}, column types are taken directly from the workbook's own
    * cell types (via {@link ExcelFileReader}, the same POI-backed reader the Composer's
    * "Import Data" dialog uses) rather than inferred from text, so numbers, dates, and
    * booleans round-trip correctly.</p>
    *
    * @param sessionToken the token obtained at join time
    * @param file         the uploaded workbook
    * @param fileType     either {@code "XLS"} or {@code "XLSX"}
    * @param sheet        sheet name to import (optional; defaults to the first sheet)
    * @param name         table name (optional; defaults to a generated name)
    * @param user         the authenticated agent principal
    */
   @PostMapping(value = "/api/wiz/v1/agent/worksheet/{sessionToken}/import-excel",
                consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   public ImportCsvResponse importExcel(@PathVariable String sessionToken,
                                        @RequestPart(value = "file", required = false) MultipartFile file,
                                        @RequestParam(required = false) String fileType,
                                        @RequestParam(required = false) String sheet,
                                        @RequestParam(required = false) String name,
                                        Principal user) throws Exception
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);

      LOG.debug("importExcel: file={}, size={}, fileType={}, sheet={}, name={}",
                file != null ? sanitizeForLog(file.getOriginalFilename()) : null,
                file != null ? file.getSize() : null, sanitizeForLog(fileType),
                sanitizeForLog(sheet), sanitizeForLog(name));

      if(file == null || file.isEmpty()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
      }

      boolean xls = "XLS".equalsIgnoreCase(fileType);
      boolean xlsx = "XLSX".equalsIgnoreCase(fileType);

      if(!xls && !xlsx) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                           "fileType must be either \"XLS\" or \"XLSX\"");
      }

      checkImportFileSize(file.getSize(), "Excel");

      byte[] bytes = file.getBytes();

      ExcelFileReader reader = xls
         ? ExcelFileSupport.getInstance().createXLSReader()
         : ExcelFileSupport.getInstance().createXLSXReader();

      TextOutput output = new TextOutput();
      ExcelFileInfo headerInfo = new ExcelFileInfo();
      headerInfo.setSheet(sheet);
      headerInfo.setStartRow(0);
      headerInfo.setEndRow(0);
      headerInfo.setStartColumn(0);
      headerInfo.setEndColumn(-1);
      output.setHeaderInfo(headerInfo);

      ExcelFileInfo bodyInfo = new ExcelFileInfo();
      bodyInfo.setSheet(sheet);
      bodyInfo.setStartRow(1);
      bodyInfo.setEndRow(-1);
      bodyInfo.setStartColumn(0);
      bodyInfo.setEndColumn(-1);
      output.setBodyInfo(bodyInfo);

      XTypeNode meta;

      int colLimit = Util.getOrganizationMaxColumn() > 0 ? Util.getOrganizationMaxColumn() : -1;

      try {
         meta = reader.importHeader(new ByteArrayInputStream(bytes), "UTF-8", output, 0, colLimit);
      }
      catch(Exception e) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                           "Failed to read Excel file: " + e.getMessage());
      }

      int ncols = meta.getChildCount();

      if(ncols == 0) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                           "Excel sheet must have a header row and at least one data row");
      }

      headerInfo.setEndColumn(ncols - 1);
      bodyInfo.setEndColumn(ncols - 1);

      String[] headers = new String[ncols];
      String[] types = new String[ncols];

      for(int c = 0; c < ncols; c++) {
         XTypeNode col = (XTypeNode) meta.getChild(c);
         headers[c] = col.getName();
         types[c] = col.getType();
      }

      List<Object[]> dataRows = new ArrayList<>();
      dataRows.add(headers);

      // +1 to account for the header row, which reader.read() also counts against the row limit
      // since firstRowHeader is true below.
      int rowLimit = Util.getOrganizationMaxRow() > 0 ? Util.getOrganizationMaxRow() + 1 : -1;

      XTableNode excelData = null;

      try {
         excelData = reader.read(new ByteArrayInputStream(bytes), "UTF-8", null, output,
                                 rowLimit, ncols, true, null, false);

         while(excelData.next()) {
            Object[] row = new Object[ncols];

            for(int c = 0; c < ncols; c++) {
               row[c] = excelData.getObject(c);
            }

            dataRows.add(row);
         }
      }
      catch(Exception e) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                           "Failed to read Excel file: " + e.getMessage());
      }
      finally {
         if(excelData != null) {
            excelData.close();
         }
      }

      if(dataRows.size() < 2) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                           "Excel sheet must have a header row and at least one data row");
      }

      int nrows = dataRows.size();
      Object[][] data = dataRows.toArray(new Object[0][]);

      return createEmbeddedTable(sessionToken, user, name, types, data, nrows, ncols);
   }

   private void checkImportFileSize(long size, String what) {
      String excelImportMax = SreeEnv.getProperty("excel.import.max");
      String max = excelImportMax != null ? excelImportMax : SreeEnv.getProperty("csv.import.max");

      if(max == null) {
         return;
      }

      long maxBytes;

      try {
         maxBytes = Long.parseLong(max);
      }
      catch(NumberFormatException e) {
         LOG.warn("Ignoring non-numeric excel.import.max/csv.import.max value: {}", max);
         return;
      }

      if(size > maxBytes) {
         long sizeK = maxBytes / 1024;
         long sizeM = sizeK / 1024;
         String sizeStr = sizeM > 0 ? sizeM + "M" : sizeK + "K";
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                           what + " file exceeds the maximum allowed size (" +
                                              sizeStr + ")");
      }
   }

   private static String sanitizeForLog(String value) {
      return value == null ? null : value.replaceAll("[\r\n]", "_");
   }

   /**
    * Shared tail of {@link #importCsv} and {@link #importExcel}: builds a new
    * {@link EmbeddedTableAssembly} from already-typed header+data rows and adds it to the
    * worksheet.
    */
   /**
    * The Excel route's shape: a type array plus a rectangular value block, row 0 being the header.
    */
   private ImportCsvResponse createEmbeddedTable(String sessionToken, Principal user, String name,
                                                 String[] types, Object[][] data, int nrows, int ncols)
      throws Exception
   {
      return createEmbeddedTable(sessionToken, user, name, new XEmbeddedTable(types, data),
                                 nrows, ncols);
   }

   private ImportCsvResponse createEmbeddedTable(String sessionToken, Principal user, String name,
                                                 XEmbeddedTable table, int nrows, int ncols)
      throws Exception
   {
      return editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         String tableName = (name != null && !name.isBlank())
            ? name
            : AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);

         EmbeddedTableAssembly assembly = new EmbeddedTableAssembly(ws, tableName);

         Assembly[] existing = ws.getAssemblies();
         int maxY = 0;
         for(Assembly a : existing) {
            if(!(a instanceof AbstractWSAssembly wa)) {
               continue;
            }

            Point p = wa.getPixelOffset();
            Dimension d = wa.getPixelSize();

            if(p != null && d != null) {
               maxY = Math.max(maxY, p.y + d.height);
            }
         }
         assembly.setPixelOffset(new Point(10, maxY + 10));
         assembly.setPixelSize(new Dimension(AssetUtil.defw, nrows + 1));

         assembly.setEmbeddedData(table);
         ws.addAssembly(assembly);

         try {
            AssetEventUtil.initColumnSelection(rws, assembly);
         }
         catch(Exception e) {
            throw new PairingException("Failed to initialize column selection: " + e.getMessage());
         }

         return new ImportCsvResponse(tableName, nrows - 1, ncols);
      });
   }

   /**
    * Close the agent session.  Always succeeds (no feature-gate check) so the agent can
    * clean up even when the flag is toggled off mid-session.
    *
    * <p>The session is only closed when the calling principal owns it.  Unknown or
    * foreign tokens are silently ignored (no error — idempotent cleanup).</p>
    *
    * @param sessionToken the token to invalidate
    * @param user         the authenticated agent principal
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/detach")
   public void detach(@PathVariable String sessionToken, Principal user) {
      JoinSession s = sessionService.resolve(sessionToken, agentKey(user));

      if(s != null) {
         sessionService.close(sessionToken);
      }
   }

   // ---------------------------------------------------------------------------
   // Internal helpers
   // ---------------------------------------------------------------------------

   private void requireEnabled() {
      if(!feature.isEnabled()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                           "Sheet agent pairing is disabled");
      }
   }

   /**
    * Refuses a pane-scoped session on this whole-sheet surface (whole-branch review finding 1).
    *
    * <p>Every endpoint here except {@code join} and {@code detach} calls this immediately after
    * {@link #requireEnabled()} and BEFORE any resolution or mutation. {@code join} cannot -- it is
    * where a session is created, pane-scoped or not; {@code detach} must not -- ending a
    * pane-scoped session is precisely what it is for.
    *
    * <p>Resolution here is a deliberate duplicate of the one the endpoint then does through
    * {@code WorksheetEditService}: that service is shared with {@link WorksheetScriptController},
    * the worksheet's SCRIPT surface, where a pane-scoped session is legitimate. Putting the
    * refusal in the shared service would refuse the one surface that is supposed to accept it,
    * so it lives at the surface that is not. The refusal itself is the single shared
    * {@link PaneScopeService#requireWholeSheetSession}, so its wording and rule cannot drift from
    * the viewsheet side's.
    *
    * <p>A token that does not resolve is passed through untouched, so the endpoint's own
    * resolution reports "invalid or expired" rather than this reporting a scope problem the
    * caller does not have.
    */
   private void requireWholeSheetSession(String sessionToken, Principal user)
      throws PairingException
   {
      PaneScopeService.requireWholeSheetSession(
         sessionService.resolve(sessionToken, agentKey(user)));
   }

   /** Derives the agent identity key used by SheetSessionService. */
   private static String agentKey(Principal agent) {
      if(agent instanceof XPrincipal p) {
         IdentityID id = IdentityID.getIdentityIDFromKey(p.getName());
         return id != null ? id.convertToKey() : p.getName();
      }

      return agent != null ? agent.getName() : null;
   }

   private void dispatch(WorksheetEditService.Editor editor, EditRequest req)
      throws Exception
   {
      switch(req.op() == null ? "" : req.op()) {
         case "add_column" ->
            editor.addColumn(req.table(), req.name(), req.type());
         case "remove_column" ->
            editor.removeColumn(req.table(), req.column());
         case "rename_column" ->
            editor.renameColumn(req.table(), req.column(), req.newName());
         case "add_filter" ->
            editor.addFilter(req.table(), req.field(), req.operation(),
                             req.values() != null
                                ? req.values().toArray(new String[0])
                                : new String[0]);
         case "remove_filter" ->
            editor.removeFilter(req.table(), req.field());
         case "set_group_aggregate" ->
            editor.setGroupAggregate(req.table(),
                                     req.groups() != null ? req.groups() : List.of(),
                                     req.aggregates() != null
                                        ? req.aggregates().stream()
                                            .map(a -> new WorksheetMutationSupport.AggregateSpec(
                                                a.field(), a.formula(), a.alias()))
                                            .toList()
                                        : List.of());
         case "add_expression_column" -> {
            if(req.name() == null || req.name().isBlank()) {
               throw new PairingException("name is required for add_expression_column.");
            }
            editor.addExpressionColumn(req.table(), req.name(), req.expression(),
                                       req.type(), req.sql());
         }
         case "set_sort" ->
            editor.setSort(req.table(), req.field(), req.direction());
         case "add_join" ->
            editor.addJoin(req.name(), req.leftTable(), req.leftKey(),
                           req.rightTable(), req.rightKey(), req.joinType(),
                           req.leftKeys(), req.rightKeys());
         case "remove_join" ->
            editor.removeJoin(req.name());
         case "add_table" ->
            editor.addTable(req.table(), new String[0]);
         case "edit_condition" ->
            editor.editCondition(req.table(), req.field(), req.operation(),
                                 req.values() != null
                                    ? req.values().toArray(new String[0])
                                    : new String[0]);
         case "edit_expression" -> {
            if(req.name() == null || req.name().isBlank()) {
               throw new PairingException("name is required for edit_expression.");
            }
            editor.editExpression(req.table(), req.name(), req.expression(),
                                  req.type(), req.sql());
         }
         case "edit_join" ->
            editor.editJoin(req.name(), req.leftKey(), req.rightKey(), req.joinType(),
                            req.leftKeys(), req.rightKeys());
         case "delete_table" ->
            editor.deleteTable(req.table());
         case "rename_table" ->
            editor.renameTable(req.table(), req.newName());
         case "set_column_visibility" ->
            editor.setColumnVisibility(req.table(), req.column(),
                                       req.visible() != null && req.visible());
         case "change_column_type" ->
            editor.changeColumnType(req.table(), req.column(), req.type());
         case "add_concatenation" ->
            editor.addConcatenation(req.name(), req.tables(), req.concatType());
         case "add_mirror" ->
            editor.addMirror(req.name(), req.source());
         case "set_conditions" ->
            editor.setConditions(req.table(), req.conditions());
         case "set_post_conditions" ->
            editor.setPostConditions(req.table(), req.conditions());
         case "set_ranking" ->
            editor.setRanking(req.table(), req.ranking());
         case "add_rotate" ->
            editor.addRotate(req.name(), req.source());
         case "add_unpivot" ->
            editor.addUnpivot(req.name(), req.source(),
                              req.headerColumns() != null ? req.headerColumns() : 1);
         case "add_date_range_column" ->
            editor.addDateRangeColumn(req.table(), req.column(), req.dateOption());
         case "add_numeric_range_column" ->
            editor.addNumericRangeColumn(req.table(), req.column(), req.boundaries());
         case "edit_cell" -> {
            if(req.row() == null || req.col() == null) {
               throw new PairingException("edit_cell requires 'row' and 'col' fields");
            }
            editor.editCell(req.table(), req.row(), req.col(), req.value());
         }
         case "insert_row" -> {
            if(req.index() == null) {
               throw new PairingException("insert_row requires an 'index' field");
            }
            editor.insertRow(req.table(), req.index());
         }
         case "delete_row" -> {
            if(req.index() == null) {
               throw new PairingException("delete_row requires an 'index' field");
            }
            editor.deleteRow(req.table(), req.index());
         }
         case "set_table_properties" ->
            editor.setTableProperties(
               req.table(), req.alias(), req.description(), req.maxRows(), req.distinct());
         case "add_cross_join" ->
            editor.addCrossJoin(req.name(), req.leftTable(), req.rightTable());
         case "add_merge_join" ->
            editor.addMergeJoin(req.name(),
               req.tables() != null ? req.tables().toArray(new String[0]) : null);
         case "reorder_columns" ->
            editor.reorderColumns(req.table(), req.columnOrder());
         case "add_concat_subtable" ->
            editor.addConcatSubtable(req.table(), req.name());
         case "remove_concat_subtable" ->
            editor.removeConcatSubtable(req.table(), req.name());
         case "add_named_group" ->
            editor.addNamedGroup(req.name(), req.table(), req.column(), req.type(),
               req.groupMappings(),
               req.groupOthers() != null && req.groupOthers());
         case "set_column_description" ->
            editor.setColumnDescription(req.table(), req.column(), req.description());
         case "set_mirror_auto_update" ->
            editor.setMirrorAutoUpdate(req.table(),
                                       req.visible() != null && req.visible());
         case "set_assembly_position" ->
            editor.setAssemblyPosition(req.table(),
                                       req.x() != null ? req.x() : 0,
                                       req.y() != null ? req.y() : 0);
         case "duplicate_assembly" ->
            editor.duplicateAssembly(req.table(), req.name());
         case "set_primary_assembly" ->
            editor.setPrimaryAssembly(req.table());
         case "edit_variable" ->
            editor.editVariable(req.name(), req.type(), req.label(), req.defaultValue());
         case "rename_variable" ->
            editor.renameVariable(req.name(), req.newName());
         case "delete_variable" ->
            editor.deleteVariable(req.name());
         case "edit_named_group" ->
            editor.editNamedGroup(req.name(), req.groupMappings(),
                                  req.groupOthers() != null && req.groupOthers());
         case "set_table_mode" ->
            editor.setTableMode(req.table(), req.mode() != null ? req.mode() : "default");
         case "edit_unpivot" ->
            editor.editUnpivot(req.table(),
                               req.headerColumns() != null ? req.headerColumns() : 1);
         default ->
            throw new PairingException("Unknown op: " + req.op());
      }
   }

   // ---------------------------------------------------------------------------
   // Variable values endpoint
   // ---------------------------------------------------------------------------

   /**
    * Set runtime values for worksheet variables. Uses
    * {@link inetsoft.report.composition.execution.AssetQuerySandbox#refreshVariableTable}
    * to apply the values and refresh dependent assemblies.
    */
   private void setVariableValues(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      editService.applyOnRuntime(sessionToken, user, rws -> {
         inetsoft.report.composition.execution.AssetQuerySandbox box =
            rws.getAssetQuerySandbox();

         if(box == null) {
            throw new PairingException(PairingException.Kind.INTERNAL, "No query sandbox available.");
         }

         inetsoft.uql.VariableTable vtable = new inetsoft.uql.VariableTable();

         if(req.variableValues() != null) {
            for(Map.Entry<String, String> entry : req.variableValues().entrySet()) {
               vtable.put(entry.getKey(), entry.getValue());
            }
         }

         box.refreshVariableTable(vtable);
         box.reset();
         return null;
      });
   }

   // ---------------------------------------------------------------------------
   // Undo / Redo
   // ---------------------------------------------------------------------------

   /**
    * Undo the last edit operation.
    *
    * @param sessionToken the token obtained at join time
    * @param user         the authenticated agent principal
    * @return whether the undo was successful and the current checkpoint index
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/undo")
   public Map<String, Object> undo(@PathVariable String sessionToken, Principal user)
      throws Exception
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);
      return editService.applyOnRuntimeNoCheckpoint(sessionToken, user, rws -> {
         boolean undone = rws.undo(null);
         return Map.of("undone", undone, "checkpoint", rws.getCurrent(),
                        "total", rws.size());
      });
   }

   /**
    * Redo the last undone operation.
    *
    * @param sessionToken the token obtained at join time
    * @param user         the authenticated agent principal
    * @return whether the redo was successful and the current checkpoint index
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/redo")
   public Map<String, Object> redo(@PathVariable String sessionToken, Principal user)
      throws Exception
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);
      return editService.applyOnRuntimeNoCheckpoint(sessionToken, user, rws -> {
         boolean redone = rws.redo(null);
         return Map.of("redone", redone, "checkpoint", rws.getCurrent(),
                        "total", rws.size());
      });
   }

   // ---------------------------------------------------------------------------
   // Edit SQL query on existing table
   // ---------------------------------------------------------------------------

   /**
    * Replace the SQL on an existing {@link SQLBoundTableAssembly}.
    */
   private void editSqlQuery(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      // Verify ACCESS permission on freeform SQL ("Visual Composer -> Free Form SQL"),
      // mirroring the check performed for the SQL query dialog (SQLQueryDialogController /
      // SQLQueryDialogService).
      if(!securityEngine.checkPermission(user, ResourceType.FREE_FORM_SQL, "*", ResourceAction.ACCESS)) {
         throw new SecurityException(
            Catalog.getCatalog().getString("composer.authorization.permissionDenied"));
      }

      if(req.table() == null || req.table().isBlank()) {
         throw new PairingException("table is required for edit_sql_query.");
      }

      if(req.expression() == null || req.expression().isBlank()) {
         throw new PairingException("expression (SQL string) is required for edit_sql_query.");
      }

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         Assembly a = ws.getAssembly(req.table());

         if(!(a instanceof SQLBoundTableAssembly sqlTable)) {
            throw new PairingException("Not a SQL-bound table: " + req.table());
         }

         SQLBoundTableAssemblyInfo info =
            (SQLBoundTableAssemblyInfo) sqlTable.getInfo();

         if(info.getQuery() == null) {
            throw new PairingException("Table has no query: " + req.table());
         }

         // Verify READ permission on the datasource this SQL executes against, mirroring
         // addSqlQuery (which checks the datasource named in the request). Here the datasource
         // is the one already bound to the assembly; re-checking it ensures a caller cannot run
         // arbitrary SQL against a datasource they lack READ on (e.g. via a paired-in runtime).
         XDataSource boundDs = info.getQuery().getDataSource();
         String dsName = boundDs != null ? boundDs.getFullName() : null;

         if(dsName != null &&
            !dataSourceService.checkPermission(dsName, ResourceAction.READ, user))
         {
            throw new PairingException(
               "Access denied: no READ permission on datasource " + dsName);
         }

         UniformSQL sql = (UniformSQL) info.getQuery().getSQLDefinition();

         if(sql == null) {
            sql = new UniformSQL();
            JDBCDataSource ds = (JDBCDataSource) info.getQuery().getDataSource();

            if(ds != null) {
               sql.setDataSource(ds);
            }
         }

         // setSQLString() with parseSQL=true fires an async parse on a background thread
         // and notifies the monitor when done. We wait up to 10s — the same timeout used
         // by SQLQueryDialogService. This does hold the HTTP thread for that duration, but
         // SQL parsing is bounded by the JDBC metadata call and is not a hot path.
         //
         // Known race: if the background thread completes and calls notify() before this
         // thread reaches wait(), the notification is silently lost and the wait() runs
         // for the full 10s. Under normal load this is rare (background parse takes at
         // least a round-trip to the JDBC driver). The subsequent empty-column check
         // will surface a timeout as a descriptive error rather than silently succeeding.
         try {
            synchronized(sql) {
               sql.setParseSQL(true);
               sql.setSQLString(req.expression(), true);
               sql.wait(10_000);
            }
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PairingException("SQL parsing was interrupted.");
         }

         info.getQuery().setSQLDefinition(sql);
         sqlTable.setSQLEdited(true);

         // initColumnSelection does NOT work for SQL-edited assemblies (returns empty
         // selection). Use the same path as addSqlQuery / SQLQueryDialogService:
         // fixUniformSQLInfo expands SELECT *, then getColumnSelection reads result metadata.
         Object metaSession =
            new DefaultMetaDataProvider(xrepository).getSession();
         JDBCUtil.fixUniformSQLInfo(
            sql, xrepository, metaSession,
            (JDBCDataSource) info.getQuery().getDataSource());
         ColumnSelection columns = queryManagerService.getColumnSelection(
            info.getQuery(), new VariableTable(), sqlTable, metaSession, null);

         if(columns == null || columns.getAttributeCount() == 0) {
            throw new PairingException(
               "SQL could not be parsed or no columns detected — check syntax and table references.");
         }

         WorksheetMutationSupport.sanitizeSqlColumnNames(columns);
         WorksheetMutationSupport.sanitizeSqlSelectionAliases(sql);
         sqlTable.setColumnSelection(columns);
         WorksheetEventUtil.refreshColumnSelection(rws, req.table(), true);
         return null;
      });
   }

   // ---------------------------------------------------------------------------
   // Update mirror (manual refresh)
   // ---------------------------------------------------------------------------

   /**
    * Manually refresh a mirror table assembly by calling
    * {@link inetsoft.uql.asset.MirrorAssembly#updateMirror}.
    */
   private void updateMirror(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      if(req.table() == null || req.table().isBlank()) {
         throw new PairingException("table is required for update_mirror.");
      }

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         Assembly a = ws.getAssembly(req.table());

         if(!(a instanceof MirrorAssembly mirror)) {
            throw new PairingException("Not a mirror assembly: " + req.table());
         }

         mirror.updateMirror(assetRepository, user);
         return null;
      });
   }

   // ---------------------------------------------------------------------------
   // Auto layout (canvas arrangement via mxGraph)
   // ---------------------------------------------------------------------------

   /**
    * Runs the hierarchical graph layout algorithm on all (or specified) worksheet assemblies,
    * updating their {@code pixelOffset} in-place. Uses the no-dispatcher overload of
    * {@link LayoutGraphService#layoutGraph(Worksheet, WSLayoutGraphEvent)} so no
    * WebSocket commands are needed — the subsequent {@code broadcastRefresh} sends the
    * updated positions to the client.
    */
   private void autoLayout(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         Assembly[] assemblies = ws.getAssemblies();

         // Collect the names to lay out — all assemblies if none specified.
         List<String> names = new ArrayList<>();

         for(Assembly a : assemblies) {
            if(a instanceof AbstractWSAssembly) {
               names.add(a.getName());
            }
         }

         if(names.isEmpty()) {
            return null;
         }

         // Use the assembly's existing pixelSize, or a sensible default.
         int[] widths = new int[names.size()];
         int[] heights = new int[names.size()];

         for(int i = 0; i < names.size(); i++) {
            AbstractWSAssembly a = (AbstractWSAssembly) ws.getAssembly(names.get(i));
            Dimension size = a.getPixelSize();

            if(size != null && size.width > 0 && size.height > 0) {
               widths[i] = size.width;
               heights[i] = size.height;
            }
            else {
               widths[i] = 200;
               heights[i] = 120;
            }
         }

         WSLayoutGraphEvent event = new WSLayoutGraphEvent.Builder()
            .names(names.toArray(new String[0]))
            .widths(widths)
            .heights(heights)
            .build();

         // The no-dispatcher overload applies setPixelOffset() directly on assemblies.
         layoutGraphService.layoutGraph(ws, event);
         return null;
      });
   }

   // ---------------------------------------------------------------------------
   // Refresh data (force query re-execution)
   // ---------------------------------------------------------------------------

   /**
    * Forces re-execution of worksheet queries by clearing the query-result cache and
    * resetting the table lens. If {@code req.table()} is specified, only that assembly is
    * refreshed; otherwise all table assemblies are refreshed.
    */
   private void refreshData(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      editService.applyOnRuntimeNoCheckpoint(sessionToken, user, rws -> {
         AssetQuerySandbox box = rws.getAssetQuerySandbox();

         if(box == null) {
            return null;
         }

         Worksheet ws = rws.getWorksheet();

         if(req.table() != null && !req.table().isBlank()) {
            // Refresh a single assembly.
            if(ws.getAssembly(req.table()) == null) {
               throw new PairingException("Table not found: " + req.table());
            }

            box.resetTableLens(req.table());
            WorksheetEventUtil.refreshColumnSelection(rws, req.table(), true);
            WorksheetEventUtil.loadTableData(rws, req.table(), true, true);
         }
         else {
            // Refresh all table assemblies.
            for(Assembly a : ws.getAssemblies()) {
               if(a instanceof TableAssembly) {
                  box.resetTableLens(a.getName());
               }
            }
         }

         return null;
      });
   }

   // ---------------------------------------------------------------------------
   // Insert column into embedded table
   // ---------------------------------------------------------------------------

   /**
    * Inserts a blank column into an embedded table at the specified position.
    *
    * @param req.table  the EmbeddedTableAssembly name
    * @param req.index  0-based column position in the ColumnSelection
    * @param req.insert {@code true} = insert before index; {@code false} = append after index
    */
   private void insertColumn(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      if(req.table() == null || req.table().isBlank()) {
         throw new PairingException("table is required for insert_column.");
      }

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         Assembly a = ws.getAssembly(req.table());

         if(!(a instanceof EmbeddedTableAssembly assembly)) {
            throw new PairingException("Not an embedded table: " + req.table());
         }

         WorksheetMutationSupport.assertSnapshotAllowsColumnAdd(
            assembly, req.table(), "insert_column");

         XEmbeddedTable data = assembly.getEmbeddedData();
         ColumnSelection columns = assembly.getColumnSelection();
         boolean insertBefore = req.insert() == null || req.insert();
         int index = req.index() != null ? req.index() : (insertBefore ? 0 : columns.getAttributeCount());

         // Generate a unique column name (col1, col2, ...).
         String colname;
         int i = 1;

         while(true) {
            colname = "col" + i;

            if(columns.getAttribute(colname) == null &&
               AssetUtil.findColumnConflictingWithAlias(columns, null, colname, true) == null)
            {
               break;
            }

            i++;
         }

         // Capture existing column identifiers before insert.
         List<String> identifiers = new ArrayList<>();

         for(int c = 0; c < data.getColCount(); c++) {
            identifiers.add(data.getColumnIdentifier(c));
         }

         // Map ColumnSelection index → XEmbeddedTable column index (skip expressions).
         int dataIndex = findEmbeddedColIndex(data, columns, index, insertBefore);
         int csIndex = insertBefore ? index : index + 1;

         data.insertCol(dataIndex);
         data.setObject(0, dataIndex, colname);
         identifiers.add(dataIndex, colname);

         for(int c = 0; c < data.getColCount(); c++) {
            data.setColumnIdentifier(c, identifiers.get(c));
         }

         AttributeRef attr = new AttributeRef(null, colname);
         ColumnRef column = new ColumnRef(attr);
         String alias = AssetUtil.findAlias(columns, column);
         column.setAlias(alias);
         columns.addAttribute(csIndex, column);
         assembly.setColumnSelection(columns);
         WorksheetEventUtil.refreshColumnSelection(rws, req.table(), true);
         WorksheetEventUtil.loadTableData(rws, req.table(), true, true);
         AssetEventUtil.refreshTableLastModified(ws, req.table(), true);
         return null;
      });
   }

   /** Maps a ColumnSelection index to the corresponding XEmbeddedTable column index. */
   private int findEmbeddedColIndex(XEmbeddedTable data, ColumnSelection columns,
                                     int index, boolean insertBefore)
   {
      if(insertBefore) {
         int idx = index;

         while(idx < columns.getAttributeCount()) {
            DataRef ref = columns.getAttribute(idx);

            if(!ref.isExpression()) {
               return AssetUtil.findColumn(data, ref);
            }

            idx++;
         }

         return data.getColCount();
      }
      else {
         int idx = index;

         while(idx > 0) {
            DataRef ref = columns.getAttribute(idx);

            if(!ref.isExpression()) {
               return AssetUtil.findColumn(data, ref) + 1;
            }

            idx--;
         }

         return 0;
      }
   }

   // ---------------------------------------------------------------------------
   // Reorder concat subtables
   // ---------------------------------------------------------------------------

   /**
    * Reorders the subtables of a {@link ConcatenatedTableAssembly} (UNION/INTERSECT/MINUS).
    *
    * <p>Operators are carried over by position inside {@code reorderTableAssemblies}. Do not
    * re-apply them here: writing them back adds the new adjacent pairs without removing the ones
    * the reorder invalidated, and the operator map then holds more pairs than there are
    * subtables — which used to make every subsequent read of the worksheet fail outright.</p>
    *
    * @param req.table    the ConcatenatedTableAssembly name
    * @param req.subtables the subtable names in the desired new order
    */
   private void reorderConcatSubtables(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      if(req.table() == null || req.table().isBlank()) {
         throw new PairingException("table is required for reorder_concat_subtables.");
      }

      if(req.subtables() == null || req.subtables().size() < 2) {
         throw new PairingException("subtables must contain at least 2 entries.");
      }

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         Assembly a = ws.getAssembly(req.table());

         if(!(a instanceof ConcatenatedTableAssembly table)) {
            throw new PairingException("Not a concatenated table assembly: " + req.table());
         }

         String[] subtables = req.subtables().toArray(new String[0]);
         TableAssembly[] reordered = new TableAssembly[subtables.length];

         for(int i = 0; i < subtables.length; i++) {
            Assembly sub = ws.getAssembly(subtables[i]);

            if(!(sub instanceof TableAssembly)) {
               throw new PairingException(
                  "Subtable not found in worksheet: " + subtables[i]);
            }

            reordered[i] = (TableAssembly) sub;
         }

         table.reorderTableAssemblies(reordered);

         WorksheetEventUtil.refreshColumnSelection(rws, req.table(), true);
         WorksheetEventUtil.loadTableData(rws, req.table(), true, true);
         return null;
      });
   }

   // ---------------------------------------------------------------------------
   // Convert bound table to embedded
   // ---------------------------------------------------------------------------

   /**
    * Converts a bound table assembly to an embedded table by executing the query
    * and storing the result data inline.
    */
   private void convertToEmbedded(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      if(req.table() == null || req.table().isBlank()) {
         throw new PairingException("table is required for convert_to_embedded.");
      }

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         Assembly a = ws.getAssembly(req.table());

         if(!(a instanceof BoundTableAssembly)) {
            throw new PairingException("Not a bound table: " + req.table());
         }

         // replace=true keeps the same name; the returned assembly must be
         // explicitly added to replace the old bound table in the worksheet.
         EmbeddedTableAssembly embedded = AssetEventUtil.convertEmbeddedTable(
            rws.getAssetQuerySandbox(), (BoundTableAssembly) a,
            true, false, false);

         if(embedded == null) {
            throw new PairingException(
               "Could not convert '" + req.table() + "' — table may have no data yet.");
         }

         ws.addAssembly(embedded);
         return null;
      });
   }

   // ---------------------------------------------------------------------------
   // Add variable from edit endpoint
   // ---------------------------------------------------------------------------

   /**
    * Handles {@code add_variable} dispatched through the edit endpoint.
    */
   private void addVariableFromEdit(String sessionToken, EditRequest req, Principal user)
      throws Exception
   {
      String varName = req.name();

      if(varName == null || varName.isBlank()) {
         throw new PairingException("name is required for add_variable.");
      }

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         createVariable(ws, varName, req.type(), req.label(), req.defaultValue());
         return null;
      });
   }

   /**
    * Shared helper that creates a {@link DefaultVariableAssembly} with the given
    * name, type, label, and default value.
    */
   private void createVariable(Worksheet ws, String name, String type,
                               String label, String defaultValue)
      throws PairingException
   {
      if(ws.getAssembly(name) != null) {
         // Fail loud: Worksheet.addAssembly() silently replaces an existing assembly
         // of the same name (or, for a same-name assembly of a different type, silently
         // no-ops). Either way a second add_variable call would destroy or discard state
         // without warning. edit_variable is the explicit path for changing an existing
         // variable's type/label/default value.
         throw new PairingException(
            "An assembly named '" + name + "' already exists in this worksheet. " +
            "Use edit_variable to change its type, label, or default value instead.");
      }

      AssetVariable var = new AssetVariable(name);

      if(label != null) {
         var.setAlias(label);
      }

      if(type != null) {
         var.setTypeNode(XSchema.createPrimitiveType(type));
      }

      if(defaultValue != null) {
         // Determine the effective type for the value node.  When a type is specified,
         // use the typed factory so the value node matches the variable type (e.g.
         // IntegerValue for "integer") and the value is parsed correctly through
         // XValueNode.parse0().  Without this, createValueNode(Object, String) always
         // creates a StringValue regardless of the variable type, and the stored
         // default value can be silently lost on serialization round-trips.
         String effectiveType = type != null
            ? type : (var.getTypeNode() != null ? var.getTypeNode().getType() : null);
         inetsoft.uql.schema.XValueNode valueNode =
            inetsoft.uql.schema.XValueNode.createValueNode(name, effectiveType);

         if(valueNode != null) {
            try {
               valueNode.parse0(defaultValue);
            }
            catch(Exception e) {
               // Fall back to storing the raw string value if parsing fails
               // (e.g. non-numeric string for an integer variable).
               valueNode.setValue(defaultValue);
            }

            var.setValueNode(valueNode);
         }
      }

      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, name);
      assembly.setVariable(var);
      assembly.setPixelOffset(new Point(25, 25));
      AssetEventUtil.adjustAssemblyPosition(assembly, ws);
      ws.addAssembly(assembly);
   }

   // ---------------------------------------------------------------------------
   // Query execution plan (read-only)
   // ---------------------------------------------------------------------------

   /**
    * Returns the SQL query string for a SQL-bound table assembly.
    *
    * @param sessionToken the token obtained at join time
    * @param table        the table assembly name
    * @param user         the authenticated agent principal
    * @return the SQL string, or an error message if the table is not SQL-bound
    */
   @GetMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/query-plan")
   public Map<String, Object> getQueryPlan(@PathVariable String sessionToken,
                                            @RequestParam String table,
                                            Principal user)
      throws PairingException
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);
      RuntimeWorksheet rws = editService.resolve(sessionToken, user);
      Worksheet ws = rws.getWorksheet();
      Assembly a = ws.getAssembly(table);

      if(!(a instanceof SQLBoundTableAssembly sqlTable)) {
         return Map.of("table", table, "sql", "",
                        "message", "Not a SQL-bound table — no query plan available.");
      }

      SQLBoundTableAssemblyInfo info =
         (SQLBoundTableAssemblyInfo) sqlTable.getInfo();
      String sqlStr = "";

      if(info.getQuery() != null && info.getQuery().getSQLDefinition() != null) {
         sqlStr = info.getQuery().getSQLDefinition().getSQLString();
      }

      return Map.of("table", table, "sql", sqlStr != null ? sqlStr : "");
   }

   // ---------------------------------------------------------------------------
   // Worksheet properties endpoint
   // ---------------------------------------------------------------------------

   public record WorksheetPropertiesRequest(String alias, String description) {}

   /**
    * Read worksheet-level properties.
    *
    * <p>The read counterpart of {@link #setProperties}. Without it an agent could set a
    * worksheet's display name or description but never read, verify or report the current value,
    * only overwrite it blindly -- and {@code /model} carries none of these fields.
    *
    * @param sessionToken the token obtained at join time
    * @param user         the authenticated agent principal
    * @return the sheet's name, alias, description and data-source flag
    * @throws PairingException if the session is invalid/expired or the runtime is not found
    */
   @GetMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/properties")
   public WorksheetPropertiesModel getProperties(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);
      RuntimeWorksheet rws = editService.resolve(sessionToken, user);
      return readService.readProperties(rws);
   }

   /**
    * Update worksheet-level properties (alias and description).
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/properties")
   public void setProperties(@PathVariable String sessionToken,
                             @RequestBody WorksheetPropertiesRequest body,
                             Principal user) throws Exception
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);
      editService.applyOnRuntime(sessionToken, user, rws -> {
         WorksheetInfo winfo = rws.getWorksheet().getWorksheetInfo();

         if(body.alias() != null) {
            winfo.setAlias(body.alias());
         }

         if(body.description() != null) {
            winfo.setDescription(body.description());
         }

         return null;
      });
   }

   // ---------------------------------------------------------------------------
   // Variables endpoint
   // ---------------------------------------------------------------------------

   public record VariableRequest(String name, String type, String label,
                                 String defaultValue) {}

   /**
    * Add a user variable to the worksheet.
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/variable")
   public void addVariable(@PathVariable String sessionToken,
                           @RequestBody VariableRequest body,
                           Principal user) throws Exception
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);

      if(body.name() == null || body.name().isBlank()) {
         throw new PairingException("Variable name is required.");
      }

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         createVariable(ws, body.name(), body.type(), body.label(), body.defaultValue());
         return null;
      });
   }

   // ---------------------------------------------------------------------------
   // SQL query table endpoint
   // ---------------------------------------------------------------------------

   /**
    * Request body for creating a SQL query table.
    *
    * @param datasource the JDBC datasource name (must already be configured in StyleBI)
    * @param sql        the SQL query string
    * @param name       optional assembly name (auto-generated if omitted)
    */
   public record SqlQueryRequest(String datasource, String sql, String name) {}

   /**
    * Response from creating a SQL query table.
    *
    * @param tableName the assembly name of the newly created table
    */
   public record SqlQueryResponse(String tableName) {}

   /**
    * Create a new SQL query table assembly in the worksheet.
    *
    * <p>The agent supplies a JDBC datasource name and a freeform SQL string. The endpoint
    * creates a {@link SQLBoundTableAssembly} with a {@link JDBCQuery}, parses the SQL,
    * initialises column selection, and positions the assembly on the canvas.</p>
    *
    * @param sessionToken the token obtained at join time
    * @param body         datasource, sql, and optional name
    * @param user         the authenticated agent principal
    * @return the assembly name of the new table
    * @throws PairingException if the session is invalid, the datasource is not found,
    *                          or the SQL cannot be parsed
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/sql-query")
   public SqlQueryResponse addSqlQuery(@PathVariable String sessionToken,
                                       @RequestBody SqlQueryRequest body,
                                       Principal user) throws Exception
   {
      requireEnabled();
      requireWholeSheetSession(sessionToken, user);

      if(body.datasource() == null || body.datasource().isBlank()) {
         throw new PairingException("datasource is required.");
      }

      if(body.sql() == null || body.sql().isBlank()) {
         throw new PairingException("sql is required.");
      }

      // Verify ACCESS permission on freeform SQL ("Visual Composer -> Free Form SQL"),
      // mirroring the check performed for the SQL query dialog (SQLQueryDialogController /
      // SQLQueryDialogService).
      if(!securityEngine.checkPermission(user, ResourceType.FREE_FORM_SQL, "*", ResourceAction.ACCESS)) {
         throw new SecurityException(
            Catalog.getCatalog().getString("composer.authorization.permissionDenied"));
      }

      // Verify READ permission on the datasource before resolving/querying it,
      // mirroring the check in addLogicalModelTable().
      if(!dataSourceService.checkPermission(body.datasource(), ResourceAction.READ, user)) {
         throw new PairingException(
            "Access denied: no READ permission on datasource " + body.datasource());
      }

      XDataSource xds;

      try {
         xds = xrepository.getDataSource(body.datasource());
      }
      catch(Exception e) {
         throw new PairingException("Datasource not found: " + body.datasource());
      }

      if(xds == null) {
         throw new PairingException("Datasource not found: " + body.datasource());
      }

      if(!(xds instanceof JDBCDataSource)) {
         throw new PairingException(
            "Datasource '" + body.datasource() + "' is not a JDBC datasource.");
      }

      return editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         String tableName = body.name() != null && !body.name().isBlank()
            ? body.name()
            : AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);

         SQLBoundTableAssembly assembly = new SQLBoundTableAssembly(ws, tableName);

         // Build the JDBCQuery with freeform SQL.
         JDBCQuery query = new JDBCQuery();
         query.setUserQuery(true);

         query.setDataSource(xds);

         UniformSQL sql = new UniformSQL();
         sql.setDataSource((JDBCDataSource) xds);

         // setSQLString() with parseSQL=true fires an async parse on a background thread
         // and notifies the monitor when done. We wait up to 10s — the same timeout used
         // by SQLQueryDialogService. This does hold the HTTP thread for that duration, but
         // SQL parsing is bounded by the JDBC metadata call and is not a hot path.
         //
         // Known race: if the background thread completes and calls notify() before this
         // thread reaches wait(), the notification is silently lost and the wait() runs
         // for the full 10s. Under normal load this is rare (background parse takes at
         // least a round-trip to the JDBC driver). The subsequent empty-column check
         // will surface a timeout as a descriptive error rather than silently succeeding.
         try {
            synchronized(sql) {
               sql.setParseSQL(true);
               sql.setSQLString(body.sql(), true);
               sql.wait(10_000);
            }
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PairingException("SQL parsing was interrupted.");
         }

         query.setSQLDefinition(sql);

         SQLBoundTableAssemblyInfo info =
            (SQLBoundTableAssemblyInfo) assembly.getInfo();
         info.setQuery(query);
         info.setSourceInfo(new SourceInfo(
            SourceInfo.PHYSICAL_TABLE, body.datasource(), body.datasource()));

         positionBelowExisting(ws, assembly);
         assembly.setSQLEdited(true);

         // Populate columns from the parsed SQL or by executing the query.
         // initColumnSelection does NOT work for SQL-edited assemblies;
         // we must use QueryManagerService.getColumnSelection() instead
         // (same approach as SQLQueryDialogService.setUpTableWithSQLString).
         // Validate before adding to the worksheet so a broken assembly is
         // never left in the model on failure.
         Object metaSession =
            new DefaultMetaDataProvider(xrepository).getSession();
         JDBCUtil.fixUniformSQLInfo(
            sql, xrepository, metaSession,
            (JDBCDataSource) query.getDataSource());
         ColumnSelection columns = queryManagerService.getColumnSelection(
            query, new VariableTable(), assembly, metaSession, null);

         if(columns == null || columns.getAttributeCount() == 0) {
            throw new PairingException(
               "SQL could not be parsed or no columns detected — check syntax and table references.");
         }

         WorksheetMutationSupport.sanitizeSqlColumnNames(columns);
         WorksheetMutationSupport.sanitizeSqlSelectionAliases(sql);
         assembly.setColumnSelection(columns);
         ws.addAssembly(assembly);

         return new SqlQueryResponse(tableName);
      });
   }

   // ---------------------------------------------------------------------------
   // Inner types
   // ---------------------------------------------------------------------------

   /**
    * Minimal response returned by the {@link #join} endpoint.
    *
    * @param sessionToken  reusable token for subsequent calls
    * @param runtimeId     server-side runtime identifier of the worksheet
    * @param ownerIdentity identity key of the browser user who owns the runtime
    */
   /**
    * @param sheetType     the runtime's own type, {@code viewsheet} or {@code worksheet} — NOT the
    *                      plugin that asked. Binding and script both drive a viewsheet runtime, and
    *                      without this the client had to label the session from its own name, which is
    *                      how one open viewsheet came to hold several unrelated sessions.
    * @param editorContext the script/formula location this session is scoped to, or {@code null}
    *                      for a whole-sheet ("Connect to Claude" toolbar) session
    */
   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity,
                              String sheetType, EditorContext editorContext) {}

   // ---------------------------------------------------------------------------
   // Exception handling
   // ---------------------------------------------------------------------------

   @ExceptionHandler(ResponseStatusException.class)
   public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException e) {
      String reason = e.getReason() != null ? e.getReason() : e.getMessage();
      LOG.warn("Rejecting request: {} ({})", reason, e.getStatusCode());
      Map<String, String> body = new LinkedHashMap<>();
      body.put("error", reason);
      return ResponseEntity.status(e.getStatusCode()).body(body);
   }

   @ExceptionHandler(PairingException.class)
   public ResponseEntity<Map<String, String>> handlePairingException(PairingException e) {
      HttpStatus status = switch(e.getKind()) {
         case SESSION_EXPIRED  -> HttpStatus.NOT_FOUND;
         case USER_MISMATCH,
              FEATURE_DISABLED -> HttpStatus.FORBIDDEN;
         case RATE_LIMITED     -> HttpStatus.TOO_MANY_REQUESTS;
         case INTERNAL        -> HttpStatus.INTERNAL_SERVER_ERROR;
         default              -> HttpStatus.BAD_REQUEST;
      };
      Map<String, String> body = new LinkedHashMap<>();
      body.put("error", e.getMessage());
      body.put("errorCode", e.getKind().name());
      return ResponseEntity.status(status).body(body);
   }

   @ExceptionHandler(MaxUploadSizeExceededException.class)
   public ResponseEntity<Map<String, String>> handleMaxUploadSizeException(
      MaxUploadSizeExceededException e)
   {
      Map<String, String> body = new LinkedHashMap<>();
      body.put("error", "Request body too large");
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
   }

   // ---------------------------------------------------------------------------
   // Dependencies
   // ---------------------------------------------------------------------------

   private final SheetAgentFeature feature;
   private final SheetJoinService joinService;
   private final SheetSessionService sessionService;
   private final WorksheetReadService readService;
   private final WorksheetEditService editService;
   private final WorksheetService worksheetService;
   private final WorksheetPreviewService previewService;
   private final SheetAgentBroadcastService broadcast;
   private final XRepository xrepository;
   private final AssetRepository assetRepository;
   private final inetsoft.web.wiz.service.MetadataApiService metadataApiService;
   private final QueryManagerService queryManagerService;
   private final LayoutGraphService layoutGraphService;
   private final DataSourceService dataSourceService;
   private final SecurityEngine securityEngine;
   private static final Logger LOG = LoggerFactory.getLogger(WorksheetAgentController.class);
}
