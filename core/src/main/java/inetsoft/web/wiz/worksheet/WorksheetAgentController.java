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
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSLayoutGraphEvent;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
                                   AssetRepository assetRepository,
                                   inetsoft.web.wiz.service.MetadataApiService metadataApiService,
                                   QueryManagerService queryManagerService,
                                   LayoutGraphService layoutGraphService)
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

      if(rows.size() < 2) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                           "CSV must have a header row and at least one data row");
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

   /**
    * Parse a CSV string into a list of string arrays (one per row).
    * Handles quoted fields and escaped double-quotes per RFC 4180.
    *
    * <p><strong>Limitation:</strong> embedded newlines inside quoted fields are not
    * supported — the parser reads line-by-line, so a newline inside a quoted value
    * will be treated as a row boundary.  Callers should ensure cell values do not
    * contain newlines.</p>
    */
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

   /**
    * Infer XSchema type for a column by scanning data rows (row 0 is header, skip it).
    *
    * <p>Known limitation: only DOUBLE and STRING are returned. Integer columns become DOUBLE
    * (acceptable for most comparisons but loses integer-precision semantics), and
    * date/boolean columns become STRING (may affect sort order and aggregate operations).
    * This is sufficient for CSV import but is not a general type-inference solution.</p>
    */
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
            editor.addNamedGroup(req.name(), req.table(), req.column(),
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
    * Operators are preserved after reordering by restoring them at their new positions.
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

         // Preserve existing operators (indexed by position) before reordering.
         TableAssemblyOperator[] ops = new TableAssemblyOperator[subtables.length - 1];

         for(int i = 0; i < ops.length; i++) {
            ops[i] = table.getOperator(i);
         }

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

         // Restore operators at new positions.
         for(int i = 0; i < ops.length; i++) {
            if(ops[i] != null) {
               table.setOperator(i, ops[i]);
            }
         }

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
   {
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

         // Position below existing assemblies.
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
   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity) {}

   // ---------------------------------------------------------------------------
   // Exception handling
   // ---------------------------------------------------------------------------

   @ExceptionHandler(PairingException.class)
   public ResponseEntity<Map<String, String>> handlePairingException(PairingException e) {
      HttpStatus status = switch(e.getKind()) {
         case SESSION_EXPIRED  -> HttpStatus.NOT_FOUND;
         case USER_MISMATCH,
              FEATURE_DISABLED -> HttpStatus.FORBIDDEN;
         case INTERNAL        -> HttpStatus.INTERNAL_SERVER_ERROR;
         default              -> HttpStatus.BAD_REQUEST;
      };
      Map<String, String> body = new LinkedHashMap<>();
      body.put("error", e.getMessage());
      body.put("errorCode", e.getKind().name());
      return ResponseEntity.status(status).body(body);
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
}
