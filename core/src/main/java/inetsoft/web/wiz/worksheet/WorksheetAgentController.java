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
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.awt.*;
import java.io.*;
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
                                   inetsoft.web.wiz.service.MetadataApiService metadataApiService)
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
      this.metadataApiService = metadataApiService;
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
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity());
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

      // add_table with a datasource needs RuntimeWorksheet for initColumnSelection.
      if("add_table".equals(req.op()) && req.datasource() != null
         && !req.datasource().isBlank())
      {
         addBoundTable(sessionToken, req, user);
         return;
      }

      // set_variable_values needs AssetQuerySandbox, not just Editor.
      if("set_variable_values".equals(req.op())) {
         setVariableValues(sessionToken, req, user);
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

         // Position below existing assemblies.
         int maxY = 0;

         for(Assembly a : ws.getAssemblies()) {
            Point p = a.getPixelOffset();
            Dimension d = ((WSAssembly) a).getPixelSize();

            if(p != null && d != null) {
               maxY = Math.max(maxY, p.y + d.height);
            }
         }

         assembly.setPixelOffset(new Point(25, maxY + 40));
         ws.addAssembly(assembly);
         return null;
      });
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

      try {
         worksheetService.setWorksheet(rws.getWorksheet(), entry,
                                       (XPrincipal) user, true, true);
         rws.setEntry(entry);
         rws.setEditable(true);
         rws.setSavePoint(rws.getCurrent());
         broadcast.broadcastSave(rws, runtimeId, user);
      }
      catch(Exception e) {
         throw new PairingException("Failed to save worksheet: " + e.getMessage());
      }
   }

   /**
    * Import CSV data as a new embedded table assembly in the worksheet.
    *
    * @param sessionToken the token obtained at join time
    * @param body         name (optional) and csv string
    * @param user         the authenticated agent principal
    */
   public record ImportCsvRequest(String name, String csv) {}
   public record ImportCsvResponse(String tableName, int rows, int columns) {}

   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/import-csv")
   public ImportCsvResponse importCsv(@PathVariable String sessionToken,
                                      @RequestBody ImportCsvRequest body,
                                      Principal user) throws Exception
   {
      requireEnabled();

      if(body.csv() == null || body.csv().isBlank()) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "csv is required");
      }

      List<String[]> rows = parseCsv(body.csv());
      if(rows.size() < 1) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV has no rows");
      }

      String[] headers = rows.get(0);
      int ncols = headers.length;
      int nrows = rows.size();

      String[] types = new String[ncols];
      for(int c = 0; c < ncols; c++) {
         types[c] = inferType(rows, c);
      }

      Object[][] data = new Object[nrows][ncols];
      for(int r = 0; r < nrows; r++) {
         String[] srcRow = rows.get(r);
         for(int c = 0; c < ncols; c++) {
            String cell = c < srcRow.length ? srcRow[c] : "";
            data[r][c] = r == 0 ? cell : convertCell(cell, types[c]);
         }
      }

      return editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         String tableName = (body.name() != null && !body.name().isBlank())
            ? body.name()
            : AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);

         EmbeddedTableAssembly assembly = new EmbeddedTableAssembly(ws, tableName);

         Assembly[] existing = ws.getAssemblies();
         int maxY = 0;
         for(Assembly a : existing) {
            if(a instanceof AbstractWSAssembly wa) {
               maxY = Math.max(maxY, wa.getPixelOffset().y + wa.getPixelSize().height);
            }
         }
         assembly.setPixelOffset(new Point(10, maxY + 10));
         assembly.setPixelSize(new Dimension(AssetUtil.defw, nrows + 1));

         XEmbeddedTable table = new XEmbeddedTable(types, data);
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

   /** Parse a CSV string into a list of string arrays (one per row). Handles quoted fields. */
   private static List<String[]> parseCsv(String csv) {
      List<String[]> result = new ArrayList<>();
      try(BufferedReader reader = new BufferedReader(new StringReader(csv))) {
         String line;
         while((line = reader.readLine()) != null) {
            if(line.isBlank()) continue;
            result.add(parseCsvLine(line));
         }
      }
      catch(IOException e) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse CSV: " + e.getMessage());
      }
      return result;
   }

   private static String[] parseCsvLine(String line) {
      List<String> fields = new ArrayList<>();
      StringBuilder sb = new StringBuilder();
      boolean inQuotes = false;
      for(int i = 0; i < line.length(); i++) {
         char c = line.charAt(i);
         if(c == '"') {
            if(inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
               sb.append('"');
               i++;
            }
            else {
               inQuotes = !inQuotes;
            }
         }
         else if(c == ',' && !inQuotes) {
            fields.add(sb.toString());
            sb.setLength(0);
         }
         else {
            sb.append(c);
         }
      }
      fields.add(sb.toString());
      return fields.toArray(new String[0]);
   }

   /** Infer XSchema type for a column by scanning data rows (row 0 is header, skip it). */
   private static String inferType(List<String[]> rows, int col) {
      boolean allNumeric = true;
      for(int r = 1; r < rows.size(); r++) {
         String[] row = rows.get(r);
         String cell = col < row.length ? row[col].trim() : "";
         if(cell.isEmpty()) continue;
         try { Double.parseDouble(cell); }
         catch(NumberFormatException e) { allNumeric = false; break; }
      }
      return allNumeric ? XSchema.DOUBLE : XSchema.STRING;
   }

   private static Object convertCell(String cell, String type) {
      if(cell == null || cell.isBlank()) return null;
      if(XSchema.DOUBLE.equals(type) || XSchema.FLOAT.equals(type) ||
         XSchema.INTEGER.equals(type) || XSchema.LONG.equals(type))
      {
         try { return Double.parseDouble(cell.trim()); }
         catch(NumberFormatException e) { return cell; }
      }
      return cell;
   }

   /**
    * Close the agent session.  Always succeeds (no feature-gate check) so the agent can
    * clean up even when the flag is toggled off mid-session.
    *
    * @param sessionToken the token to invalidate
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/detach")
   public void detach(@PathVariable String sessionToken) {
      sessionService.close(sessionToken);
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

   private void dispatch(WorksheetEditService.Editor editor, EditRequest req)
      throws PairingException
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
            editor.setGroupAggregate(req.table(), req.groups(),
                                     req.aggregates() != null
                                        ? req.aggregates().stream()
                                            .map(a -> new WorksheetMutationSupport.AggregateSpec(
                                                a.field(), a.formula(), a.alias()))
                                            .toList()
                                        : List.of());
         case "add_expression_column" ->
            editor.addExpressionColumn(req.table(), req.name(), req.expression(),
                                       req.type(), req.sql());
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
         case "edit_expression" ->
            editor.editExpression(req.table(), req.name(), req.expression(),
                                  req.type(), req.sql());
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
         case "edit_cell" ->
            editor.editCell(req.table(), req.row(), req.col(), req.value());
         case "insert_row" ->
            editor.insertRow(req.table(), req.index());
         case "delete_row" ->
            editor.deleteRow(req.table(), req.index());
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
            editor.addNamedGroup(req.name(), req.table(), req.column(),
               req.groupMappings(),
               req.groupOthers() != null && req.groupOthers());
         case "set_column_description" ->
            editor.setColumnDescription(req.table(), req.column(), req.description());
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
            throw new PairingException("No query sandbox available.");
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
   // Worksheet properties endpoint
   // ---------------------------------------------------------------------------

   public record WorksheetPropertiesRequest(String alias, String description) {}

   /**
    * Update worksheet-level properties (alias and description).
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/properties")
   public void setProperties(@PathVariable String sessionToken,
                             @RequestBody WorksheetPropertiesRequest body,
                             Principal user) throws Exception
   {
      requireEnabled();
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

      if(body.name() == null || body.name().isBlank()) {
         throw new PairingException("Variable name is required.");
      }

      editService.applyOnRuntime(sessionToken, user, rws -> {
         Worksheet ws = rws.getWorksheet();
         AssetVariable var = new AssetVariable(body.name());

         if(body.label() != null) {
            var.setAlias(body.label());
         }

         if(body.type() != null) {
            var.setTypeNode(XSchema.createPrimitiveType(body.type()));
         }

         if(body.defaultValue() != null) {
            var.setValueNode(
               inetsoft.uql.schema.XValueNode.createValueNode(
                  body.defaultValue(), body.name()));
         }

         DefaultVariableAssembly assembly =
            new DefaultVariableAssembly(ws, body.name());
         assembly.setVariable(var);
         ws.addAssembly(assembly);
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

      if(body.datasource() == null || body.datasource().isBlank()) {
         throw new PairingException("datasource is required.");
      }

      if(body.sql() == null || body.sql().isBlank()) {
         throw new PairingException("sql is required.");
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

         try {
            query.setDataSource(xrepository.getDataSource(body.datasource()));
         }
         catch(Exception e) {
            throw new PairingException("Failed to load datasource: " + e.getMessage());
         }

         UniformSQL sql = new UniformSQL();
         sql.setDataSource((JDBCDataSource) xds);

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

         // Position below existing assemblies.
         int maxY = 0;

         for(Assembly a : ws.getAssemblies()) {
            Point p = a.getPixelOffset();
            Dimension d = ((WSAssembly) a).getPixelSize();

            if(p != null && d != null) {
               maxY = Math.max(maxY, p.y + d.height);
            }
         }

         assembly.setPixelOffset(new Point(25, maxY + 40));
         assembly.setSQLEdited(true);
         ws.addAssembly(assembly);
         AssetEventUtil.initColumnSelection(rws, assembly);
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
   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity) {}

   // ---------------------------------------------------------------------------
   // Exception handling
   // ---------------------------------------------------------------------------

   @ExceptionHandler(PairingException.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   public Map<String, String> handlePairingException(PairingException e) {
      return Map.of("error", e.getMessage());
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
   private final inetsoft.web.wiz.service.MetadataApiService metadataApiService;
}
