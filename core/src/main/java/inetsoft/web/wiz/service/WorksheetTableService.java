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

package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.model.osi.*;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.service.GenerateWsService.WORKSHEET_ROOT_FOLDER_PATH;
import static inetsoft.web.wiz.service.WizDateLevelUtil.getDateGroupLevel;

/**
 * Implements the incremental worksheet-table creation endpoint (/ws/table).
 * <p>
 * Each call handles one table assembly:
 * <ul>
 *   <li>{@code physical table} — {@link PhysicalBoundTableAssembly} referencing a DB table</li>
 *   <li>{@code mirror table}   — {@link MirrorTableAssembly} over an existing worksheet table,
 *       with optional aggregation and expression columns</li>
 *   <li>{@code relational join table} — {@link RelationalJoinTableAssembly} over existing tables</li>
 *   <li>{@code sql query table} — {@link SQLBoundTableAssembly} bound to a raw SQL SELECT
 *       (window functions / CTEs / any dialect SQL execute on the database)</li>
 * </ul>
 */
@Service
public class WorksheetTableService {

   public WorksheetTableService(ViewsheetService viewsheetService,
                                MetadataApiService metadataApiService,
                                InnerJoinService innerJoinService,
                                LayoutGraphService layoutGraphService,
                                QueryManagerService queryManagerService,
                                XRepository xrepository,
                                ObjectMapper objectMapper,
                                DataSourceService dataSourceService,
                                SecurityEngine securityEngine)
   {
      this.viewsheetService = viewsheetService;
      this.metadataApiService = metadataApiService;
      this.innerJoinService = innerJoinService;
      this.layoutGraphService = layoutGraphService;
      this.queryManagerService = queryManagerService;
      this.xrepository = xrepository;
      this.objectMapper = objectMapper;
      this.dataSourceService = dataSourceService;
      this.securityEngine = securityEngine;
   }

   /**
    * Verifies the "Visual Composer -> Data Worksheet" action permission (EM Security -> Actions).
    * This is the same action-level gate that {@code OpenWorksheetController}/
    * {@code SaveWorksheetController} apply to every worksheet open/create/save entry point;
    * it is independent of (and checked before) any asset- or datasource-level permission.
    */
   private void checkWorksheetActionPermission(Principal user) throws Exception {
      if(!securityEngine.checkPermission(user, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "composer.authorization.permissionDenied"));
      }
   }

   // ─── Public entry point ───────────────────────────────────────────────────

   public WorksheetTablesResponse createTables(WorksheetTableRequest request, Principal user)
      throws Exception
   {
      // Action-level gate ("Visual Composer -> Data Worksheet"): checked first, before any
      // asset- or datasource-level permission and before any worksheet construction/mutation.
      checkWorksheetActionPermission(user);

      // 1. Load or create the worksheet — once for the whole batch.
      Worksheet worksheet;
      AssetEntry worksheetEntry;

      if(request.getWorksheetId() != null) {
         worksheetEntry = AssetEntry.createAssetEntry(request.getWorksheetId());
         AbstractSheet sheet = viewsheetService.getAssetRepository()
            .getSheet(worksheetEntry, user, true, AssetContent.ALL);

         if(!(sheet instanceof Worksheet ws)) {
            throw new IllegalArgumentException(
               sheet == null
                  ? "Worksheet not found: " + request.getWorksheetId()
                  : "worksheetId does not reference a worksheet: " + request.getWorksheetId());
         }

         worksheet = ws;
      }
      else {
         worksheet = new Worksheet();
         worksheetEntry = null;
      }

      // #75456: default wiz analytics to full data (0 = unlimited).
      worksheet.getWorksheetInfo().setDesignMaxRows(0);

      List<WorksheetTableResponse> results = new ArrayList<>();
      Set<String> failed = new HashSet<>();
      List<WorksheetTable> tables = request.getTables() != null
         ? request.getTables() : Collections.emptyList();

      for(WorksheetTable table : tables) {
         String missing = firstMissingDependency(worksheet, table, failed);

         if(missing != null) {
            results.add(failure(table.getTableName(),
               "Depends on table \"" + missing + "\" which failed to create or does not exist."));
            failed.add(table.getTableName());
            continue;
         }

         try {
            results.add(addOneTable(worksheet, table, user));
         }
         catch(Exception e) {
            // Roll back any half-built assembly so the persisted worksheet stays clean.
            if(worksheet.getAssembly(table.getTableName()) != null) {
               worksheet.removeAssembly(table.getTableName());
            }

            results.add(failure(table.getTableName(), rootMessage(e)));
            failed.add(table.getTableName());
         }
      }

      // Persist once. New worksheet is only persisted when it actually holds an assembly.
      WsServiceHelper.layoutGraph(layoutGraphService, worksheet);

      if(worksheetEntry != null) {
         viewsheetService.getAssetRepository().setSheet(worksheetEntry, worksheet, user, true);
      }
      else if(worksheet.getAssemblies().length > 0) {
         worksheetEntry = WsServiceHelper.persistWorksheet(viewsheetService, worksheet, user);
      }

      WorksheetTablesResponse response = new WorksheetTablesResponse();
      response.setWsId(worksheetEntry != null ? worksheetEntry.toIdentifier() : null);
      response.setTables(results);
      return response;
   }

   /**
    * The first declared dependency of {@code t} (a join/mirror base, or a join path's left/right
    * table) that is either already failed in this batch or absent from the worksheet — or
    * {@code null} when every dependency is present and healthy.
    */
   public String firstMissingDependency(Worksheet ws, WorksheetTable t, Set<String> failed) {
      List<String> deps = new ArrayList<>();

      if(t.getBaseTables() != null) {
         deps.addAll(t.getBaseTables());
      }

      if(t.getJoinPaths() != null) {
         for(WorksheetTable.JoinPathInfo jp : t.getJoinPaths()) {
            deps.add(jp.getLeftTable());
            deps.add(jp.getRightTable());
         }
      }

      for(String dep : deps) {
         if(dep != null && (failed.contains(dep) || ws.getAssembly(dep) == null)) {
            return dep;
         }
      }

      return null;
   }

   private WorksheetTableResponse failure(String tableName, String message) {
      WorksheetTableResponse r = new WorksheetTableResponse();
      r.setTableName(tableName);
      r.setColumns(Collections.emptyList());
      r.setSuccess(false);
      r.setErrorMessage(message);
      return r;
   }

   private WorksheetTableResponse addOneTable(Worksheet worksheet, WorksheetTable request,
                                              Principal user)
      throws Exception
   {
      // 2. Build the table assembly.
      AbstractTableAssembly table = buildTable(worksheet, request, user);

      // 3. Pre-aggregate conditions (WHERE).
      if(request.getPreAggregateCondition() != null && !request.getPreAggregateCondition().isEmpty()) {
         ConditionList preList = buildConditionList(
            table.getColumnSelection(true), request.getPreAggregateCondition(), worksheet, false);
         table.setPreConditionList(preList);
      }

      // 4. Aggregation.
      if(request.getAggregateInfo() != null) {
         applyAggregateInfo(table, request.getAggregateInfo());
      }

      // 5. Post-aggregate conditions (HAVING).
      if(request.getPostAggregateCondition() != null && !request.getPostAggregateCondition().isEmpty()) {
         ConditionList postList = buildConditionList(
            table.getColumnSelection(true), request.getPostAggregateCondition(), worksheet, true);
         table.setPostConditionList(postList);
      }

      // 6. Ranking / top-N.
      if(request.getRankingCondition() != null && !request.getRankingCondition().isEmpty()) {
         ConditionList rankList = buildRankingConditionList(
            table.getColumnSelection(true), request.getRankingCondition());
         table.setRankingConditionList(rankList);
      }

      // 8. Execution probe for render-time-executable tables.
      if(shouldProbe(request)) {
         probeExecutable(worksheet, table, user);
      }

      // 9. Build the success response.
      List<WorksheetColumnData> columns = extractColumnsFromSelection(table);

      WorksheetTableResponse response = new WorksheetTableResponse();
      response.setTableName(table.getName());
      response.setColumns(columns);

      if(request.isAsPrimaryTable()) {
         String dbTableOverride = request.getPhysicalSource() != null
            ? request.getPhysicalSource().getTableName() : null;
         response.setPrimaryTableFields(
            WsServiceHelper.extractPrimaryTableFields(worksheet, table, dbTableOverride));
      }

      response.setSuccess(true);

      // Set primary only after the table fully succeeds (so a failed+rolled-back table never
      // leaves a dangling primary reference — see Task 2 rollback).
      if(request.isAsPrimaryTable()) {
         worksheet.setPrimaryAssembly(table.getName());
      }

      return response;
   }

   // ─── Probe: execute a freshly-built table to surface render-time query failures ──────────

   /**
    * Whether {@link #addOneTable} should run the execution probe for this request. A {@code sql query
    * table} always executes its raw SQL at render time, and a table carrying expression columns (JS or
    * {@code sql:true}) evaluates them at render time — both can be structurally valid yet fail on
    * execution. Pure physical / mirror / join tables with no expression columns carry no render-time
    * execution risk beyond what structural creation already validates, so they are not probed (zero
    * added latency).
    */
   private boolean shouldProbe(WorksheetTable request) {
      if("sql query table".equals(request.getTableType())) {
         return true;
      }

      List<WorksheetTable.ExpressionColumnInfo> exprCols = request.getExpressionColumns();
      return exprCols != null && !exprCols.isEmpty();
   }

   /**
    * Probe whether a freshly-built worksheet table can actually execute, without exporting any data.
    * Runs the table in {@code LIVE_MODE} and forces the first data row so lazily-evaluated expression
    * columns (notably JS) actually run, then inspects the result for a failed-query fallback lens.
    * Any failure propagates (the caller maps it to {@code success=false} + {@code errorMessage}).
    *
    * <p>Why {@code LIVE_MODE} rather than {@code RUNTIME_MODE}: a failed query is degraded to a
    * failed-query fallback lens rather than thrown. {@code RUNTIME_MODE} discards the cause
    * (AssetQuery swallows {@code SQLExpressionFailedException} for RUNTIME), whereas {@code LIVE_MODE}
    * stamps the underlying cause onto the fallback lens for BOTH SQL and expression failures, which
    * {@link WizVsService#checkFailedQuery} then surfaces. The cause never bubbles out of
    * {@code getTableLens} (LIVE re-swallows it at doGetTableLens), so {@code checkFailedQuery} must
    * be called actively.
    */
   private void probeExecutable(Worksheet worksheet, Assembly table, Principal user) throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(worksheet);
      box.setBaseUser(user);

      try {
         TableLens lens = box.getTableLens(table.getAbsoluteName(), AssetQuerySandbox.LIVE_MODE);

         if(lens != null) {
            // Force the first data row (row 0 = header, row 1 = first data row) so lazily-evaluated
            // expression columns actually run — a JS column only fails when a row is produced.
            lens.moreRows(1);

            // Throws IllegalArgumentException(raw cause) when the lens chain carries a failed-query
            // fallback — the unified signal for SQL and expression failures alike. Pass false so the
            // real cause is surfaced verbatim instead of the expression-specific failedQueryError
            // message, which would misdirect for a raw SQL query table or an infra error.
            // WizVsService is in this same package.
            WizVsService.checkFailedQuery(lens, false);
         }
      }
      finally {
         box.dispose();
      }
   }

   // Get worksheet table metadata.

   public WorksheetModel getWorksheetModel(String wsIdentifier, Principal user)
      throws Exception
   {
      checkWorksheetActionPermission(user);

      if(Tool.isEmptyString(wsIdentifier)) {
         throw new IllegalArgumentException("wsIdentifier is required");
      }

      WorksheetSource source = resolveWorksheet(wsIdentifier, user);
      Worksheet worksheet = source.worksheet();
      List<WorksheetTableModel> tables = new ArrayList<>();

      for(Assembly assembly : worksheet.getAssemblies()) {
         if(assembly instanceof AbstractTableAssembly table) {
            tables.add(buildWorksheetTableModel(worksheet, table));
         }
      }

      WorksheetModel.Builder builder = WorksheetModel.builder()
         .identifier(source.identifier())
         .description(worksheet.getDescription())
         .tables(tables);

      String primaryTable = worksheet.getPrimaryAssemblyName();

      if(!Tool.isEmptyString(primaryTable)) {
         builder.primaryTable(primaryTable);
      }

      List<WorksheetColumnInfo> primaryColumnMetas = extractPrimaryColumnMetas(worksheet);

      if(primaryColumnMetas != null && !primaryColumnMetas.isEmpty()) {
         builder.primaryColumnMetas(primaryColumnMetas);
      }

      return builder.build();
   }

   /**
    * Column metadata of the worksheet's primary (binding) table for the visualization layer,
    * or null when the worksheet has no primary table assembly.
    */
   private List<WorksheetColumnInfo> extractPrimaryColumnMetas(Worksheet worksheet) {
      String primaryName = worksheet.getPrimaryAssemblyName();

      if(Tool.isEmptyString(primaryName)) {
         return null;
      }

      if(!(worksheet.getAssembly(primaryName) instanceof AbstractTableAssembly primaryTable)) {
         return null;
      }

      return WsServiceHelper.extractPrimaryTableFields(
         worksheet, primaryTable, getPhysicalTableName(primaryTable));
   }

   private String getPhysicalTableName(AbstractTableAssembly table) {
      if(!(table instanceof PhysicalBoundTableAssembly physicalTable)) {
         return null;
      }

      SourceInfo sourceInfo = physicalTable.getSourceInfo();
      String tableName = sourceInfo != null ? sourceInfo.getSource() : null;

      if(Tool.isEmptyString(tableName)) {
         return physicalTable.getName();
      }

      int index = tableName.lastIndexOf('.');

      if(index >= 0 && index + 1 < tableName.length()) {
         tableName = tableName.substring(index + 1);
      }

      return tableName.replace("\"", "")
         .replace("`", "")
         .replace("[", "")
         .replace("]", "");
   }

   // Delete tables.

   public DeleteWorksheetTablesResponse deleteTables(DeleteWorksheetTablesRequest request,
                                                     Principal user)
      throws Exception
   {
      checkWorksheetActionPermission(user);

      if(request.getWorksheetId() == null) {
         throw new IllegalArgumentException("worksheetId is required");
      }

      if(request.getTableNames() == null || request.getTableNames().isEmpty()) {
         throw new IllegalArgumentException("tableNames must not be empty");
      }

      AssetEntry worksheetEntry = AssetEntry.createAssetEntry(request.getWorksheetId());
      AbstractSheet sheet = viewsheetService.getAssetRepository()
         .getSheet(worksheetEntry, user, true, AssetContent.ALL);

      if(!(sheet instanceof Worksheet worksheet)) {
         throw new IllegalArgumentException("worksheetId does not reference a worksheet: "
                                            + request.getWorksheetId());
      }

      Set<String> deleteSet = new LinkedHashSet<>(request.getTableNames());

      DeleteWorksheetTablesResponse response = new DeleteWorksheetTablesResponse();
      List<String> deleted = new ArrayList<>();
      List<String> notFound = new ArrayList<>();
      Map<String, String> skipped = new LinkedHashMap<>();

      for(String name : request.getTableNames()) {
         if(worksheet.getAssembly(name) == null) {
            notFound.add(name);
            continue;
         }

         // Check whether any assembly NOT in the delete set depends on this one.
         String blocker = findExternalDependent(worksheet, name, deleteSet);

         if(blocker != null) {
            skipped.put(name, blocker);
         }
      }

      // Remove tables that passed the blocker check, dependents-first so that
      // removeAssembly never encounters a broken reference within the delete set.
      List<String> toDelete = new ArrayList<>(deleteSet);
      toDelete.removeAll(notFound);
      toDelete.removeAll(skipped.keySet());
      propagateSkippedDependencies(worksheet, toDelete, skipped);
      toDelete = topoSort(worksheet, toDelete);

      for(String name : toDelete) {
         worksheet.removeAssembly(name);
         deleted.add(name);
      }

      // Persist only when something changed.
      if(!deleted.isEmpty()) {
         WsServiceHelper.layoutGraph(layoutGraphService, worksheet);
         viewsheetService.getAssetRepository().setSheet(worksheetEntry, worksheet, user, true);
      }

      response.setWsId(request.getWorksheetId());
      response.setDeleted(deleted);
      response.setNotFound(notFound);
      response.setSkipped(skipped);
      response.setSuccess(true);
      return response;
   }

   /**
    * Ensures that deleting a table never breaks a skipped table.
    *
    * If a skipped table depends on a table in {@code toDelete}, that table is also
    * moved to {@code skipped}. The process repeats until the skipped set is stable.
    */
   private void propagateSkippedDependencies(Worksheet worksheet, List<String> toDelete,
                                             Map<String, String> skipped)
   {
      boolean changed;

      do {
         changed = false;
         Iterator<String> it = toDelete.iterator();

         while(it.hasNext()) {
            String name = it.next();
            Assembly asm = worksheet.getAssembly(name);

            if(asm != null) {
               for(String skippedName : skipped.keySet()) {
                  Assembly skippedAsm = worksheet.getAssembly(skippedName);

                  if(dependsOn(skippedAsm, name)) {
                     skipped.put(name, skippedName);
                     it.remove();
                     changed = true;
                     break;
                  }
               }
            }
         }
      }
      while(changed);
   }

   /**
    * Returns the name of the first assembly that is NOT in {@code deleteSet} and
    * directly depends on {@code targetName}, or {@code null} if none exists.
    */
   private String findExternalDependent(Worksheet worksheet, String targetName,
                                        Set<String> deleteSet)
   {
      for(Assembly asm : worksheet.getAssemblies()) {
         String asmName = asm.getName();

         if(deleteSet.contains(asmName)) {
            continue;
         }

         if(dependsOn(asm, targetName)) {
            return asmName;
         }
      }

      return null;
   }

   /** True when {@code assembly} directly references {@code targetName} as a base table. */
   private boolean dependsOn(Assembly assembly, String targetName) {
      if(assembly instanceof ComposedTableAssembly composed) {
         for(TableAssembly ta : composed.getTableAssemblies(false)) {
            if(targetName.equals(ta.getName())) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Returns {@code names} in topological order so that dependents come before
    * their bases (safe delete order).  Uses a simple DFS; cycles are impossible
    * in a valid worksheet.
    */
   private List<String> topoSort(Worksheet worksheet, List<String> names) {
      Set<String> nameSet = new HashSet<>(names);
      List<String> result = new ArrayList<>();
      Set<String> visited = new HashSet<>();

      for(String name : names) {
         topoVisit(worksheet, name, nameSet, visited, result);
      }

      return result;
   }

   private void topoVisit(Worksheet worksheet, String name, Set<String> nameSet,
                          Set<String> visited, List<String> result)
   {
      if(!visited.add(name)) {
         return;
      }

      Assembly asm = worksheet.getAssembly(name);

      if(asm == null) {
         return;
      }

      // Visit dependents first (assemblies in the delete set that reference this one).
      for(String candidate : nameSet) {
         if(!visited.contains(candidate)) {
            Assembly candidateAsm = worksheet.getAssembly(candidate);

            if(candidateAsm != null && dependsOn(candidateAsm, name)) {
               topoVisit(worksheet, candidate, nameSet, visited, result);
            }
         }
      }

      result.add(name);
   }

   // ─── Table builders ───────────────────────────────────────────────────────

   private AbstractTableAssembly buildTable(Worksheet worksheet, WorksheetTable request,
                                            Principal user)
      throws Exception
   {
      String tableType = request.getTableType();

      if(tableType == null) {
         throw new IllegalArgumentException("tableType is required");
      }

      // Verify READ permission on the datasource before resolving/using it (physical table and
      // sql query table both bind to one via physicalSource; mirror/join tables only reference
      // already-in-worksheet assemblies, so there is no new datasource to check for them).
      // Mirrors WorksheetAgentController.addLogicalModelTable's usage of DataSourceService.
      WorksheetTable.PhysicalSource src = request.getPhysicalSource();

      if(src != null && src.getDatasourcePath() != null &&
         !dataSourceService.checkPermission(src.getDatasourcePath(), ResourceAction.READ, user))
      {
         throw new IllegalArgumentException(
            "Access denied: no READ permission on datasource " + src.getDatasourcePath());
      }

      // Free-Form SQL action gate ("Visual Composer -> Free Form SQL"): a raw sql query table
      // executes caller-authored SQL verbatim against the datasource (parsing disabled), so it
      // must be gated exactly like the SQL query dialog and WorksheetAgentController.addSqlQuery/
      // editSqlQuery. WORKSHEET/ACCESS + datasource READ (checked above) are not sufficient — a
      // user allowed to pick tables but denied free-form SQL must not reach this path.
      if("sql query table".equals(tableType) &&
         !securityEngine.checkPermission(user, ResourceType.FREE_FORM_SQL, "*", ResourceAction.ACCESS))
      {
         throw new SecurityException(Catalog.getCatalog().getString(
            "composer.authorization.permissionDenied"));
      }

      AbstractTableAssembly table = switch(tableType) {
         case "physical table"        -> buildPhysicalTable(worksheet, request, user);
         case "mirror table"          -> buildMirrorTable(worksheet, request);
         case "relational join table" -> buildJoinTable(worksheet, request);
         case "sql query table"       -> buildSqlTable(worksheet, request);
         default -> throw new IllegalArgumentException("Unknown tableType: " + tableType);
      };

      return table;
   }

   private AbstractTableAssembly buildPhysicalTable(Worksheet worksheet,
                                                    WorksheetTable request,
                                                    Principal user)
      throws Exception
   {
      PhysicalBoundTableAssembly table =
         new PhysicalBoundTableAssembly(worksheet, request.getTableName());

      WorksheetTable.PhysicalSource src = request.getPhysicalSource();

      if(src == null) {
         throw new IllegalArgumentException("physicalSource is required for physical table");
      }

      // Apply source info (datasource + qualified table name).
      JDBCDataSource ds = metadataApiService.getJDBCDatasource(src.getDatasourcePath());
      XNode tableMetaData = metadataApiService.getTableMetaData(
         ds, src.getCatalog(), src.getSchema(), src.getTableName());

      if(tableMetaData == null) {
         throw new IllegalArgumentException(
            "Table not found: " + src.getTableName() +
            " (datasource=" + src.getDatasourcePath() +
            ", schema=" + src.getSchema() +
            ", catalog=" + src.getCatalog() + ")");
      }

      String qname = SQLTypes.getSQLTypes(ds).getQualifiedName(tableMetaData, ds);
      SourceInfo sinfo = new SourceInfo(SourceInfo.PHYSICAL_TABLE, src.getDatasourcePath(), qname);
      sinfo.setProperty(SourceInfo.SCHEMA, src.getSchema());
      sinfo.setProperty(SourceInfo.CATALOG, src.getCatalog());
      sinfo.setProperty(SourceInfo.TABLE_TYPE, (String) tableMetaData.getAttribute("type"));
      table.setSourceInfo(sinfo);

      // Build column selection.
      if(request.getColumns() != null && !request.getColumns().isEmpty()) {
         // Explicit column list from the LLM.
         ColumnSelection cs = buildColumnSelection(request.getColumns());
         table.setColumnSelection(cs);
      }
      else {
         // No explicit columns → fetch all from datasource metadata.
         GetDatabaseTableMetaRequest metaReq = new GetDatabaseTableMetaRequest();
         metaReq.setDsName(src.getDatasourcePath());
         metaReq.setCatalog(src.getCatalog());
         metaReq.setSchema(src.getSchema());
         metaReq.setTableName(src.getTableName());
         OsiDataset metaData = metadataApiService.getMetaData(metaReq, user);
         ColumnSelection cs = buildColumnSelectionFromMeta(metaData);
         table.setColumnSelection(cs);
      }

      // Expression columns are only meaningful on non-aggregated mirror tables;
      // log a warning but don't fail if someone passes them here.
      applyExpressionColumns(table, request.getExpressionColumns());
      applyWindowColumns(table, request.getWindowColumns());

      worksheet.addAssembly(table);
      return table;
   }

   /**
    * Build a {@link SQLBoundTableAssembly} from a raw SQL SELECT. The query is pushed to the
    * database verbatim, so window functions, CTEs, non-equi joins and any dialect-specific SQL
    * work — unlike the physical/mirror/join model, whose generated SQL can't express them.
    * Other tables can mirror/join the result by name.
    */
   private AbstractTableAssembly buildSqlTable(Worksheet worksheet, WorksheetTable request)
      throws Exception
   {
      String sqlString = request.getSqlExpression();
      WorksheetTable.PhysicalSource src = request.getPhysicalSource();

      if(sqlString == null || sqlString.isBlank()) {
         throw new IllegalArgumentException("sqlExpression is required for sql query table");
      }

      if(src == null || src.getDatasourcePath() == null) {
         throw new IllegalArgumentException(
            "physicalSource.datasourcePath is required for sql query table");
      }

      String dsName = src.getDatasourcePath();
      JDBCDataSource ds = metadataApiService.getJDBCDatasource(dsName);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(worksheet, request.getTableName());

      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);

      // Do NOT parse: the sqlExpression is authored to run verbatim. StyleBI's SQL parser builds a
      // structured representation and REGENERATES the query at execution, which silently drops
      // clauses it can't round-trip — notably a GROUP BY / ORDER BY whose expression isn't identical
      // to a SELECT column (e.g. GROUP BY DATE_TRUNC('month', d) under SELECT TO_CHAR(DATE_TRUNC(...))),
      // producing an aggregate-without-GROUP-BY error against the database. With parsing off the raw
      // string is sent to the database unchanged (honoring GROUP BY/ORDER BY/HAVING/CTEs/window
      // functions), and column metadata is resolved from the result set (the non-parsed branch of
      // QueryManagerService.getColumnSelection → JDBCQuery.getOutputTypeForNonParseableSQL).
      sql.setParseSQL(false);
      sql.setSQLString(sqlString, false);

      JDBCQuery query = new JDBCQuery();
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(sql);

      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, dsName, dsName));

      Object session = viewsheetService.getAssetRepository().getSession();
      JDBCUtil.fixUniformSQLInfo(sql, xrepository, session, ds);

      ColumnSelection columns =
         queryManagerService.getColumnSelection(query, new VariableTable(), table, session, null);

      if(columns == null || columns.getAttributeCount() == 0) {
         Exception cause = query.getLastQueryError();
         String detail = cause != null
            ? " Database reported: " + rootMessage(cause)
            : "";
         throw new IllegalArgumentException(
            "Could not resolve any columns from sqlExpression — check the SQL is a valid SELECT for datasource '" +
            dsName + "'." + detail, cause);
      }

      // The parsed UniformSQL selection only carries types for base table columns it can resolve
      // from catalog metadata; aggregates, window functions and subquery-passthrough columns get
      // no type, so each would default to "string" (ColumnRef's fallback) and a numeric measure
      // would be misread as a dimension by the chart recommender. Overlay the real result types
      // from the query's output metadata (ResultSetMetaData).
      applySqlResultTypes(columns, query, session);

      // StyleBI's SQL parser captures selection aliases with surrounding double-quotes (e.g.
      // "seller_state"). Expose a clean column name via an applied alias — this leaves each column's
      // underlying ref (which maps to the SQL result) untouched, so data still binds.
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         if(columns.getAttribute(i) instanceof ColumnRef cr) {
            String nm = cr.getName();

            if(nm != null && nm.length() >= 2 && nm.startsWith("\"") && nm.endsWith("\"")) {
               cr.setAlias(nm.substring(1, nm.length() - 1));
               cr.setApplyingAlias(true);
            }
         }
      }

      table.setColumnSelection(columns);
      table.setAdvancedEditing(true);

      worksheet.addAssembly(table);
      return table;
   }

   /**
    * Overlay real column data types (from the query's ResultSetMetaData) onto a SQL-bound table's
    * column selection. The SQL parser only types base catalog columns; without this, aggregates,
    * window functions and subquery-passthrough columns default to "string" (ColumnRef's fallback)
    * and a numeric measure binds as a dimension. Types are matched positionally — the parsed
    * selection order equals the result-set order — with a name-based fallback when the counts differ.
    */
   private void applySqlResultTypes(ColumnSelection columns, JDBCQuery query, Object session) {
      try {
         XTypeNode meta = query.getOutputTypeForNonParseableSQL(
            new XTypeNode("table"), new VariableTable(), session);

         if(meta == null || meta.getChildCount() == 0) {
            return;
         }

         int n = columns.getAttributeCount();
         boolean byIndex = meta.getChildCount() == n;
         Map<String, String> byName = new HashMap<>();

         for(int i = 0; i < meta.getChildCount(); i++) {
            XTypeNode node = (XTypeNode) meta.getChild(i);

            if(node.getName() != null && node.getType() != null) {
               byName.putIfAbsent(node.getName(), node.getType());
            }
         }

         for(int i = 0; i < n; i++) {
            if(!(columns.getAttribute(i) instanceof ColumnRef cr)) {
               continue;
            }

            String type = byIndex ? ((XTypeNode) meta.getChild(i)).getType() : null;

            if(type == null || type.isEmpty()) {
               type = byName.get(cr.getName());
            }

            if(type != null && !type.isEmpty()) {
               cr.setDataType(type);
            }
         }
      }
      catch(Exception ex) {
         // Best-effort: a metadata failure leaves the parser-derived types (worst case "string").
         LOG.debug("Failed to resolve SQL result types for sql query table", ex);
      }
   }

   private AbstractTableAssembly buildMirrorTable(Worksheet worksheet,
                                                  WorksheetTable request)
   {
      List<String> bases = request.getBaseTables();

      if(bases == null || bases.isEmpty()) {
         throw new IllegalArgumentException("Mirror table requires baseTables[0]");
      }

      String baseTableName = bases.get(0);
      WSAssembly baseAssembly = (WSAssembly) worksheet.getAssembly(baseTableName);

      if(baseAssembly == null) {
         throw new IllegalArgumentException(
            "Base table '" + baseTableName + "' not found in worksheet");
      }

      MirrorTableAssembly mirror =
         new MirrorTableAssembly(worksheet, request.getTableName(), baseAssembly);

      // Expression columns are only valid when there is no aggregation.
      boolean hasAggregation = request.getAggregateInfo() != null &&
         ((request.getAggregateInfo().getGroups() != null && !request.getAggregateInfo().getGroups().isEmpty()) ||
          (request.getAggregateInfo().getAggregates() != null && !request.getAggregateInfo().getAggregates().isEmpty()));

      if(!hasAggregation) {
         applyExpressionColumns(mirror, request.getExpressionColumns());
         applyWindowColumns(mirror, request.getWindowColumns());
      }

      worksheet.addAssembly(mirror);
      return mirror;
   }

   private AbstractTableAssembly buildJoinTable(Worksheet worksheet,
                                                WorksheetTable request)
      throws Exception
   {
      List<String> bases = request.getBaseTables();
      List<WorksheetTable.JoinPathInfo> joinPaths = request.getJoinPaths();

      if(bases == null || bases.isEmpty()) {
         throw new IllegalArgumentException("Relational join table requires baseTables");
      }

      if(joinPaths == null || joinPaths.isEmpty()) {
         throw new IllegalArgumentException("Relational join table requires joinPaths");
      }

      // Collect distinct base table assemblies in declaration order.
      Set<TableAssembly> tableSet = new LinkedHashSet<>();

      for(String name : bases) {
         WSAssembly asm = (WSAssembly) worksheet.getAssembly(name);

         if(asm == null) {
            throw new IllegalArgumentException(
               "Table '" + name + "' not found in worksheet");
         }

         if(!(asm instanceof TableAssembly)) {
            throw new IllegalArgumentException(
               "Assembly '" + name + "' is not a table assembly");
         }

         tableSet.add((TableAssembly) asm);
      }

      // Build the composite operator.
      TableAssemblyOperator noperator = new TableAssemblyOperator();

      for(WorksheetTable.JoinPathInfo path : joinPaths) {
         TableAssembly left = (TableAssembly) worksheet.getAssembly(path.getLeftTable());
         TableAssembly right = (TableAssembly) worksheet.getAssembly(path.getRightTable());

         if(left == null || right == null) {
            throw new IllegalArgumentException(
               "Join path references table not in worksheet: " +
               path.getLeftTable() + " → " + path.getRightTable());
         }

         DataRef leftAttr = left.getColumnSelection(true).getAttribute(path.getLeftKey());
         DataRef rightAttr = right.getColumnSelection(true).getAttribute(path.getRightKey());

         if(leftAttr == null) {
            throw new IllegalArgumentException(
               "Left join key '" + path.getLeftKey() + "' not found in table '" + path.getLeftTable() + "'");
         }

         if(rightAttr == null) {
            throw new IllegalArgumentException(
               "Right join key '" + path.getRightKey() + "' not found in table '" + path.getRightTable() + "'");
         }

         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setLeftAttribute(leftAttr);
         op.setRightAttribute(rightAttr);
         op.setLeftTable(path.getLeftTable());
         op.setRightTable(path.getRightTable());
         op.setOperation(getJoinOperation(path.getJoinType(), path.getJoinOperator()));
         noperator.addOperator(op);
      }

      RelationalJoinTableAssembly joinTable = new RelationalJoinTableAssembly(
         worksheet, request.getTableName(),
         tableSet.toArray(new TableAssembly[0]),
         new TableAssemblyOperator[0]);

      worksheet.addAssembly(joinTable);
      innerJoinService.editExistingJoinTable(worksheet, joinTable, noperator, true);
      WsServiceHelper.initCompositeColumnSelection(joinTable);

      return joinTable;
   }

   // ─── Column selection helpers ─────────────────────────────────────────────

   private ColumnSelection buildColumnSelection(List<WorksheetTable.ColumnInfo> cols) {
      ColumnSelection cs = new ColumnSelection();

      for(WorksheetTable.ColumnInfo col : cols) {
         AttributeRef ref = new AttributeRef(null, AssetUtil.trimEntity(col.getName(), null));

         if(col.getType() != null) {
            ref.setDataType(col.getType());
         }

         ColumnRef colRef = new ColumnRef(ref);

         if(col.getAlias() != null) {
            colRef.setAlias(col.getAlias());
         }

         if(col.getDescription() != null) {
            colRef.setDescription(col.getDescription());
         }

         if(Boolean.FALSE.equals(col.getVisible())) {
            colRef.setVisible(false);
         }

         cs.addAttribute(colRef);
      }

      return cs;
   }

   private ColumnSelection buildColumnSelectionFromMeta(OsiDataset metaData) {
      ColumnSelection cs = new ColumnSelection();

      if(metaData == null || metaData.getFields() == null) {
         return cs;
      }

      for(OsiField field : metaData.getFields()) {
         String type = WsServiceHelper.extractFieldType(objectMapper, field);
         AttributeRef ref = new AttributeRef(null, field.getName());

         if(type != null) {
            ref.setDataType(type);
         }

         cs.addAttribute(new ColumnRef(ref));
      }

      return cs;
   }

   private void applyExpressionColumns(AbstractTableAssembly table,
                                       List<WorksheetTable.ExpressionColumnInfo> exprCols)
   {
      if(exprCols == null || exprCols.isEmpty()) {
         return;
      }

      ColumnSelection cs = table.getColumnSelection(false);

      for(WorksheetTable.ExpressionColumnInfo col : exprCols) {
         String colName = col.getAlias() != null ? col.getAlias() : col.getName();
         ExpressionRef expr = new ExpressionRef(null, colName);
         expr.setExpression(col.getExpression() != null ? col.getExpression() : "");
         ColumnRef colRef = new ColumnRef(expr);
         colRef.setSQL(col.isSql());

         if(col.getAlias() != null) {
            colRef.setAlias(col.getAlias());
         }

         if(col.getDescription() != null) {
            colRef.setDescription(col.getDescription());
         }

         if(Boolean.FALSE.equals(col.getVisible())) {
            colRef.setVisible(false);
         }

         if(!Tool.isEmptyString(col.getType())) {
            colRef.setDataType(col.getType());
         }

         cs.addAttribute(colRef);
      }

      table.setColumnSelection(cs, false);
   }

   /**
    * Apply structured window (analytic) function columns — the {@link WindowExpressionRef}-backed
    * counterpart of {@link #applyExpressionColumns}. Each entry resolves its {@code column},
    * {@code partitionBy}, and {@code orderBy} field names against the table's own column
    * selection (same resolution mechanism as {@code cs.getAttribute(name)} used throughout this
    * class, e.g. {@link #applyAggregateInfo}), builds a {@link WindowExpressionRef}, and adds it
    * to the selection as a {@code sql:true} {@link ColumnRef} so {@code PreAssetQuery} inlines the
    * generated {@code OVER(...)} text verbatim.
    * <p>
    * Package-private (not private) so {@code WorksheetTableServiceWindowColumnsTest} can invoke it
    * directly without standing up the full {@code createTable} dependency graph, mirroring how
    * {@link #buildConditionList} is tested.
    */
   void applyWindowColumns(AbstractTableAssembly table,
                           List<WorksheetTable.WindowColumnInfo> winCols)
   {
      if(winCols == null || winCols.isEmpty()) {
         return;
      }

      ColumnSelection cs = table.getColumnSelection(false);

      for(WorksheetTable.WindowColumnInfo col : winCols) {
         String colName = col.getName();

         if(Tool.isEmptyString(colName)) {
            throw new IllegalArgumentException("windowColumns[].name is required");
         }

         if(Tool.isEmptyString(col.getFn())) {
            throw new IllegalArgumentException("windowColumns['" + colName + "'].fn is required");
         }

         DataRef argRef = null;

         if(!Tool.isEmptyString(col.getColumn())) {
            argRef = cs.getAttribute(col.getColumn());

            if(argRef == null) {
               throw new IllegalArgumentException(
                  "windowColumns['" + colName + "'].column not found: " + col.getColumn());
            }
         }

         List<DataRef> partitionRefs = new ArrayList<>();

         if(col.getPartitionBy() != null) {
            for(String p : col.getPartitionBy()) {
               DataRef pref = cs.getAttribute(p);

               if(pref == null) {
                  throw new IllegalArgumentException(
                     "windowColumns['" + colName + "'].partitionBy column not found: " + p);
               }

               partitionRefs.add(pref);
            }
         }

         List<SortRef> orderRefs = new ArrayList<>();

         if(col.getOrderBy() != null) {
            for(WorksheetTable.OrderByInfo ob : col.getOrderBy()) {
               DataRef oref = cs.getAttribute(ob.getField());

               if(oref == null) {
                  throw new IllegalArgumentException(
                     "windowColumns['" + colName + "'].orderBy field not found: " + ob.getField());
               }

               SortRef sort = new SortRef(oref);
               sort.setOrder("ASC".equalsIgnoreCase(ob.getDirection())
                                ? XConstants.SORT_ASC : XConstants.SORT_DESC);
               orderRefs.add(sort);
            }
         }

         int n = col.getN() != null ? col.getN() : 0;
         WindowExpressionRef winRef =
            new WindowExpressionRef(col.getFn(), argRef, n, partitionRefs, orderRefs);
         winRef.setName(colName);

         WorksheetTable.WindowFrameInfo frame = col.getFrame();

         if(frame != null) {
            if(!FRAMEABLE_FNS.contains(col.getFn())) {
               throw new IllegalArgumentException(
                  "windowColumns['" + colName +
                  "']: frame is only valid on aggregate/FIRST_VALUE/LAST_VALUE functions");
            }

            int startOffset = requireFrameOffset(colName, frame.getStartBound(), frame.getStartOffset());
            int endOffset = requireFrameOffset(colName, frame.getEndBound(), frame.getEndOffset());
            validateFrameOrder(colName, frame.getStartBound(), startOffset,
                                frame.getEndBound(), endOffset);

            if(!isWholePartitionFrame(frame) && orderRefs.isEmpty()) {
               throw new IllegalArgumentException(
                  "windowColumns['" + colName + "']: a bounded frame requires orderBy");
            }

            // Phase 4: RANGE/GROUPS frame mode + offsetUnit.
            String mode = frame.getMode() == null ? "ROWS" : frame.getMode().toUpperCase();

            if(!VALID_FRAME_MODES.contains(mode)) {
               throw new IllegalArgumentException(
                  "windowColumns['" + colName + "']: invalid frame mode: " + frame.getMode());
            }

            // A PRECEDING/FOLLOWING bound carries a real offset (as opposed to a fixed bound
            // like CURRENT_ROW/UNBOUNDED_*).
            boolean valueOffset =
               "PRECEDING".equals(frame.getStartBound()) || "FOLLOWING".equals(frame.getStartBound())
               || "PRECEDING".equals(frame.getEndBound()) || "FOLLOWING".equals(frame.getEndBound());

            // RANGE value-offset and GROUPS need an ORDER BY; RANGE value-offset needs exactly one.
            if(("RANGE".equals(mode) && valueOffset) || "GROUPS".equals(mode)) {
               if(orderRefs.isEmpty()) {
                  throw new IllegalArgumentException(
                     "windowColumns['" + colName + "']: a " + mode + " frame requires orderBy");
               }
            }

            if("RANGE".equals(mode) && valueOffset && orderRefs.size() != 1) {
               throw new IllegalArgumentException(
                  "windowColumns['" + colName +
                  "']: a RANGE value-offset frame requires exactly one orderBy column");
            }

            // offsetUnit: only for RANGE, only on a date/time order key.
            String unit = frame.getOffsetUnit();

            if(unit != null) {
               if(!"RANGE".equals(mode)) {
                  throw new IllegalArgumentException(
                     "windowColumns['" + colName +
                     "']: frame.offsetUnit is only valid for a RANGE frame");
               }

               if(!VALID_OFFSET_UNITS.contains(unit)) {
                  throw new IllegalArgumentException(
                     "windowColumns['" + colName + "']: invalid frame.offsetUnit: " + unit);
               }

               requireDateOrderKey(colName, orderRefs);
            }

            winRef.setFrame(mode, frame.getStartBound(), startOffset, frame.getEndBound(),
                             endOffset, unit);
         }

         ColumnRef colRef = new ColumnRef(winRef);
         colRef.setSQL(true);

         if(!Tool.isEmptyString(col.getType())) {
            colRef.setDataType(col.getType());
         }

         cs.addAttribute(colRef);
      }

      table.setColumnSelection(cs, false);
   }

   /** Window functions a ROWS frame may be attached to (aggregates + FIRST_VALUE/LAST_VALUE). */
   private static final Set<String> FRAMEABLE_FNS =
      Set.of("SUM", "AVG", "COUNT", "MIN", "MAX", "FIRST_VALUE", "LAST_VALUE");

   /** Recognized {@link WorksheetTable.WindowFrameInfo} frame mode tokens (Phase 4). */
   private static final Set<String> VALID_FRAME_MODES = Set.of("ROWS", "RANGE", "GROUPS");

   /** Recognized {@link WorksheetTable.WindowFrameInfo} offset unit tokens (Phase 4). */
   private static final Set<String> VALID_OFFSET_UNITS = Set.of(
      "year", "quarter", "month", "week", "day", "hour", "minute", "second");

   /**
    * Validate that the (single) orderBy column of a date-valued RANGE frame is actually a
    * date/time-typed column — {@code offsetUnit} is only meaningful (and only rendered as a
    * Postgres {@code INTERVAL '<n> <unit>'} literal) when the ORDER BY key it measures the
    * offset against is itself a date/time value.
    */
   private static void requireDateOrderKey(String colName, List<SortRef> orderRefs) {
      SortRef s = orderRefs.get(0);
      DataRef ref = s.getDataRef();
      String dt = ref == null ? null : ref.getDataType();

      if(!XSchema.isDateType(dt)) {
         throw new IllegalArgumentException(
            "windowColumns['" + colName + "']: frame.offsetUnit requires a date/time orderBy column");
      }
   }

   /** Recognized {@link WorksheetTable.WindowFrameInfo} bound tokens. */
   private static final Set<String> VALID_FRAME_BOUNDS = Set.of(
      "UNBOUNDED_PRECEDING", "PRECEDING", "CURRENT_ROW", "FOLLOWING", "UNBOUNDED_FOLLOWING");

   /**
    * Validate one frame bound + its offset and return the effective offset to pass to
    * {@link WindowExpressionRef#setFrame}: the given offset (required, must be positive) when
    * {@code bound} is {@code PRECEDING}/{@code FOLLOWING}, else {@code 0} (ignored for the other
    * bounds). Throws a wire-clear {@link IllegalArgumentException} naming the column for an
    * unrecognized bound or a missing/non-positive offset — validated here so an invalid bound
    * never reaches {@code WindowExpressionRef}'s internal {@code frameBoundSql}, whose failure
    * mode is a bare {@code RuntimeException}.
    */
   private static int requireFrameOffset(String colName, String bound, Integer offset) {
      if(bound == null || !VALID_FRAME_BOUNDS.contains(bound)) {
         throw new IllegalArgumentException(
            "windowColumns['" + colName + "']: invalid frame bound: " + bound);
      }

      if("PRECEDING".equals(bound) || "FOLLOWING".equals(bound)) {
         if(offset == null || offset <= 0) {
            throw new IllegalArgumentException(
               "windowColumns['" + colName + "']: frame bound '" + bound +
               "' requires a positive offset");
         }

         return offset;
      }

      if(offset != null) {
         throw new IllegalArgumentException(
            "windowColumns['" + colName + "']: frame bound '" + bound +
            "' must not carry an offset");
      }

      return 0;
   }

   /**
    * Reject a frame whose start bound is ordered after its end bound. Frame order is
    * {@code UNBOUNDED_PRECEDING < N PRECEDING < CURRENT_ROW < N FOLLOWING < UNBOUNDED_FOLLOWING},
    * with a larger offset sorting earlier among two {@code PRECEDING} bounds and a smaller offset
    * sorting earlier among two {@code FOLLOWING} bounds.
    */
   private static void validateFrameOrder(String colName, String startBound, int startOffset,
                                          String endBound, int endOffset)
   {
      if("UNBOUNDED_FOLLOWING".equals(startBound)) {
         throw new IllegalArgumentException(
            "windowColumns['" + colName + "']: frame start bound cannot be UNBOUNDED_FOLLOWING");
      }

      if("UNBOUNDED_PRECEDING".equals(endBound)) {
         throw new IllegalArgumentException(
            "windowColumns['" + colName + "']: frame end bound cannot be UNBOUNDED_PRECEDING");
      }

      if(frameBoundRank(startBound, startOffset) > frameBoundRank(endBound, endOffset)) {
         throw new IllegalArgumentException(
            "windowColumns['" + colName + "']: frame start (" + startBound +
            (startOffset != 0 ? " " + startOffset : "") + ") must not be after frame end (" +
            endBound + (endOffset != 0 ? " " + endOffset : "") + ")");
      }
   }

   /**
    * Map a validated frame bound + offset to a comparable rank for {@link #validateFrameOrder}.
    * Bound validity is assumed to have already been checked by {@link #requireFrameOffset}.
    */
   private static int frameBoundRank(String bound, int offset) {
      switch(bound) {
      case "UNBOUNDED_PRECEDING":
         return Integer.MIN_VALUE;
      case "PRECEDING":
         return -offset;
      case "CURRENT_ROW":
         return 0;
      case "FOLLOWING":
         return offset;
      case "UNBOUNDED_FOLLOWING":
         return Integer.MAX_VALUE;
      default:
         // Unreachable: requireFrameOffset already rejected unrecognized bounds.
         throw new IllegalArgumentException("invalid window frame bound: " + bound);
      }
   }

   /** True for the whole-partition frame ({@code UNBOUNDED_PRECEDING .. UNBOUNDED_FOLLOWING}). */
   private static boolean isWholePartitionFrame(WorksheetTable.WindowFrameInfo frame) {
      return "UNBOUNDED_PRECEDING".equals(frame.getStartBound())
         && "UNBOUNDED_FOLLOWING".equals(frame.getEndBound());
   }

   // ─── Aggregate info ───────────────────────────────────────────────────────

   private void applyAggregateInfo(AbstractTableAssembly table,
                                   WorksheetTable.AggregateInfo aggInfo)
   {
      if(aggInfo == null) {
         return;
      }

      List<WorksheetTable.GroupByFieldInfo> groups = aggInfo.getGroups();
      List<WorksheetTable.AggregateFieldInfo> aggregates = aggInfo.getAggregates();

      if((groups == null || groups.isEmpty()) && (aggregates == null || aggregates.isEmpty())) {
         return;
      }

      AggregateInfo info = new AggregateInfo();
      ColumnSelection cs = table.getColumnSelection(true);
      ColumnSelection privateCs = table.getColumnSelection(false);

      if(groups != null) {
         for(WorksheetTable.GroupByFieldInfo grp : groups) {
            DataRef ref = cs.getAttribute(grp.getFieldName());

            if(!(ref instanceof ColumnRef column)) {
               continue;
            }

            if(grp.getDateGroupLevel() != null) {
               String colName = column.getName();
               int dgroup = getDateGroupLevel(grp.getDateGroupLevel());
               String name = DateRangeRef.getName(colName, dgroup);
               DateRangeRef rangeRef = new DateRangeRef(name, column.getDataRef(), dgroup);
               rangeRef.setOriginalType(column.getDataType());
               ColumnRef dateColumn = new ColumnRef(rangeRef);
               dateColumn.setDataType(rangeRef.getDataType());

               // Insert the DateRangeRef column into the column selection before the base
               // column so the aggregate engine can resolve it (mirrors processDateGrouping).
               int baseIdx = privateCs.indexOfAttribute(column);

               if(baseIdx >= 0) {
                  privateCs.addAttribute(baseIdx, dateColumn);
               }
               else {
                  privateCs.addAttribute(dateColumn);
               }

               column = dateColumn;
            }

            info.addGroup(new GroupRef(column));
         }
      }

      if(aggregates != null) {
         // Tracks how many aggregates have been registered for each fieldName so far.
         // The first occurrence reuses the existing ColumnRef; subsequent ones need a
         // dedicated synthetic column so setColumnSelection()'s getAggregate() loop
         // can find a distinct match for each AggregateRef (mirrors the approach in
         // AggregateDialogService.updateAggregateInfo()).
         Map<String, Integer> fieldOccurrenceCount = new HashMap<>();

         for(WorksheetTable.AggregateFieldInfo agg : aggregates) {
            DataRef column = cs.getAttribute(agg.getFieldName());

            if(column == null) {
               continue;
            }

            AggregateFormula formula = AggregateFormula.getFormula(agg.getFormula());

            if(formula == null) {
               formula = AggregateFormula.SUM;
            }

            DataRef secondaryCol = null;

            if(agg.getSecondaryField() != null && formula.isTwoColumns()) {
               secondaryCol = cs.getAttribute(agg.getSecondaryField());
            }

            int occurrence = fieldOccurrenceCount.merge(agg.getFieldName(), 1, Integer::sum);
            DataRef aggColumn;

            if(occurrence == 1) {
               // First aggregate for this field: use the private column directly so that
               // aggColumn and the alias target are the same object (avoids relying on
               // cs / privateCs sharing identical ColumnRef instances).
               DataRef privateCol = privateCs.getAttribute(agg.getFieldName());
               aggColumn = privateCol != null ? privateCol : column;

               if(agg.getAlias() != null && privateCol instanceof ColumnRef columnRef) {
                  columnRef.setAlias(agg.getAlias());
               }
            }
            else {
               // Second+ aggregate on the same field: create a synthetic ExpressionRef
               // column so each AggregateRef has a distinct entry in the private column
               // selection. Without this, setColumnSelection()'s getAggregate() loop only
               // finds one match per field and the remaining aggregates are silently lost.
               String entity = column instanceof ColumnRef cr ? cr.getEntity() : null;
               String baseName = agg.getAlias() != null
                  ? agg.getAlias()
                  : column.getName() + "_" + occurrence;

               // Bump suffix until the name is free in privateCs to avoid silent duplicates.
               String colName = baseName;
               int suffix = 2;

               while(privateCs.getAttribute(colName) != null) {
                  colName = baseName + "_" + suffix++;
               }

               ExpressionRef expRef = new ExpressionRef(entity, colName);
               expRef.setExpression("field['" + column.getName() + "']");
               ColumnRef syntheticCol = new ColumnRef(expRef);

               if(column instanceof ColumnRef cr) {
                  syntheticCol.setDataType(cr.getDataType());
               }

               privateCs.addAttribute(syntheticCol);
               aggColumn = syntheticCol;
            }

            AggregateRef aggRef = new AggregateRef(aggColumn, secondaryCol, formula);

            if(agg.getN() != null && formula.hasN()) {
               aggRef.setN(agg.getN());
            }

            // Pass false to skip name-based dedup — same field can have multiple aggregates
            // with different formulas (e.g. NthLargest(1) and NthLargest(2)). Each entry
            // carries a distinct alias, so field-name deduplication is not needed here.
            info.addAggregate(aggRef, false);
         }
      }

      if(!info.isEmpty()) {
         table.setAggregateInfo(info);
         table.setColumnSelection(privateCs, false);
      }
   }

   // ─── Condition list ───────────────────────────────────────────────────────

   /**
    * Converts the flat {@link WorksheetTable.ConditionItem} list emitted by
    * the wiz-services condition-tree normaliser into a StyleBI {@link ConditionList}.
    *
    * <p>Each item carries:
    * <ul>
    *   <li>{@code conditionLevel} — nesting depth of the condition itself.</li>
    *   <li>{@code junction} — logical operator connecting this item to the preceding one
    *       ({@code null} for the first item).</li>
    *   <li>{@code conditionJunctionLevel} — level at which the {@link JunctionOperator} is
    *       inserted; equals {@code conditionLevel} for same-level siblings but equals the
    *       outer level when this item is the first element of a group that is itself a sibling
    *       of the preceding group (falls back to {@code conditionLevel} when absent).</li>
    * </ul>
    *
    * @param columns   column selection used to resolve field names
    * @param items     flat condition list from the request
    * @param worksheet the worksheet (needed for SUBQUERY value resolution)
    * @param isHaving  true when building a HAVING (post-aggregate) condition list;
    *                  fields with {@code aggregateFormula} are wrapped in {@link AggregateRef}
    */
   // Package-private for unit testing (WorksheetTableServiceConditionTest).
   ConditionList buildConditionList(ColumnSelection columns,
                                    List<WorksheetTable.ConditionItem> items,
                                    Worksheet worksheet,
                                    boolean isHaving)
   {
      ConditionList list = new ConditionList();

      for(WorksheetTable.ConditionItem item : items) {
         // Insert a junction operator before each non-first item.
         if(item.getJunction() != null) {
            int junctionType = "or".equalsIgnoreCase(item.getJunction())
               ? JunctionOperator.OR : JunctionOperator.AND;
            list.append(new JunctionOperator(junctionType, item.resolveJunctionLevel()));
         }

         appendConditionItem(list, item, columns, worksheet, isHaving);
      }

      return list;
   }

   private ConditionList buildRankingConditionList(ColumnSelection columns,
                                                   List<WorksheetTable.ConditionItem> items)
   {
      ConditionList list = new ConditionList();

      for(WorksheetTable.ConditionItem item : items) {
         if(item.getJunction() != null) {
            int junctionType = "or".equalsIgnoreCase(item.getJunction())
               ? JunctionOperator.OR : JunctionOperator.AND;
            list.append(new JunctionOperator(junctionType, item.resolveJunctionLevel()));
         }

         appendRankingConditionItem(list, item, columns);
      }

      return list;
   }

   private void appendRankingConditionItem(ConditionList list,
                                           WorksheetTable.ConditionItem item,
                                           ColumnSelection columns)
   {
      if(item.getField() == null || item.getOperation() == null) {
         return;
      }

      DataRef ref = columns.getAttribute(item.getField());

      if(ref == null) {
         return;
      }

      int op = switch(item.getOperation()) {
         case "TOP_N"    -> XCondition.TOP_N;
         case "BOTTOM_N" -> XCondition.BOTTOM_N;
         default -> throw new IllegalArgumentException(
            "rankingCondition only supports TOP_N or BOTTOM_N, got: " + item.getOperation());
      };

      RankingCondition rc = new RankingCondition();
      rc.setOperation(op);
      rc.setDataRef(ref);

      if(item.getValues() != null && !item.getValues().isEmpty()) {
         WorksheetTable.WorksheetConditionValue v = item.getValues().get(0);

         if("VALUE".equals(v.getType()) && v.getValue() != null) {
            Object val = v.getValue() instanceof Number
               ? ((Number) v.getValue()).intValue()
               : Integer.parseInt(v.getValue().toString());
            rc.setN(val);
         }
      }

      list.append(new ConditionItem(ref, rc, item.getConditionLevel()));
   }

   private void appendConditionItem(ConditionList list,
                                    WorksheetTable.ConditionItem item,
                                    ColumnSelection columns,
                                    Worksheet worksheet,
                                    boolean isHaving)
   {
      // Fail loud rather than silently skipping. A skipped item leaves a dangling JunctionOperator
      // in the ConditionList (the operator for this item was already appended by buildConditionList),
      // which breaks the list's required item/operator alternation and later throws an opaque
      // "JunctionOperator cannot be cast to ConditionItem". With a single condition the silent skip
      // instead dropped the filter outright (wrong results, no error). Either way the caller's intent
      // was lost without a signal — so reject the bad condition with a clear, actionable message.
      if(item.getField() == null || item.getOperation() == null) {
         throw new IllegalArgumentException(
            "Condition is missing a field or operation (field=" + item.getField() +
            ", operation=" + item.getOperation() + ").");
      }

      // Resolve the column reference.
      DataRef ref = columns.getAttribute(item.getField());

      if(ref == null) {
         throw new IllegalArgumentException(
            "Condition references column \"" + item.getField() + "\" which is not in the table's " +
            "column selection. Add it to the table's columns (or omit columns to select all), " +
            "and reference it exactly as it appears in the selection.");
      }

      // For HAVING conditions, wrap the column in an AggregateRef when a formula is present.
      if(isHaving && item.getAggregateFormula() != null &&
         !"none".equalsIgnoreCase(item.getAggregateFormula()))
      {
         AggregateFormula formula = AggregateFormula.getFormula(item.getAggregateFormula());

         if(formula == null) {
            formula = AggregateFormula.COUNT_ALL;
         }

         DataRef secondary = item.getSecondaryField() != null
            ? columns.getAttribute(item.getSecondaryField()) : null;
         AggregateRef aggRef = new AggregateRef(ref, secondary, formula);

         if(item.getNOrP() != null && formula.hasN()) {
            aggRef.setN(item.getNOrP());
         }

         ref = aggRef;
      }

      // Determine the XCondition operation code(s).  LESS/GREATER with equal=true expand to two.
      List<Integer> ops = mapOperation(item.getOperation(), item.getEqual());
      String dataType = ref.getDataType() != null ? ref.getDataType() : XSchema.STRING;

      boolean firstOp = true;

      for(int op : ops) {
         if(!firstOp) {
            // LESS_THAN/GREATER_THAN with equal=true expand to two ops joined by OR (e.g. < OR =).
            list.append(new JunctionOperator(JunctionOperator.OR, item.getConditionLevel()));
         }

         firstOp = false;
         AssetCondition ac = new AssetCondition();
         ac.setOperation(op);
         ac.setType(dataType);
         ac.setNegated(item.isNegated());

         if(item.getValues() != null) {
            for(WorksheetTable.WorksheetConditionValue v : item.getValues()) {
               addConditionValue(ac, v, columns, worksheet);
            }
         }

         list.append(new ConditionItem(ref, ac, item.getConditionLevel()));
      }
   }

   private List<Integer> mapOperation(String operation, Boolean equal) {
      boolean isEqual = Boolean.TRUE.equals(equal);
      List<Integer> ops = new ArrayList<>();

      switch(operation) {
         case "EQUAL_TO"      -> ops.add(XCondition.EQUAL_TO);
         case "ONE_OF"        -> ops.add(XCondition.ONE_OF);
         case "LESS_THAN"     -> {
            ops.add(XCondition.LESS_THAN);
            if(isEqual) ops.add(XCondition.EQUAL_TO);
         }
         case "GREATER_THAN"  -> {
            ops.add(XCondition.GREATER_THAN);
            if(isEqual) ops.add(XCondition.EQUAL_TO);
         }
         case "BETWEEN"       -> ops.add(XCondition.BETWEEN);
         case "STARTING_WITH" -> ops.add(XCondition.STARTING_WITH);
         case "CONTAINS"      -> ops.add(XCondition.CONTAINS);
         case "LIKE"          -> ops.add(XCondition.LIKE);
         case "NULL"          -> ops.add(XCondition.NULL);
         case "DATE_IN"       -> ops.add(XCondition.DATE_IN);
         default -> throw new IllegalArgumentException("Unknown condition operation: " + operation);
      }

      return ops;
   }

   private void addConditionValue(AssetCondition condition,
                                  WorksheetTable.WorksheetConditionValue v,
                                  ColumnSelection columns,
                                  Worksheet worksheet)
   {
      if(v == null || v.getType() == null) {
         return;
      }

      switch(v.getType()) {
         case "VALUE" -> condition.addValue(v.getValue());

         case "EXPRESSION" -> {
            ExpressionValue ev = new ExpressionValue();
            ev.setExpression(v.getValue() != null ? v.getValue().toString() : "");
            ev.setType(ExpressionValue.JAVASCRIPT);
            condition.addValue(ev);
         }

         case "SESSION_DATA" -> {
            // Session variables are stored as UserVariable references.
            UserVariable uv = new UserVariable(
               v.getValue() != null ? v.getValue().toString() : "");
            condition.addValue(uv);
         }

         case "FIELD" -> {
            ExpressionValue ev = new ExpressionValue();
            String expressoin = v.getValue() != null ? v.getValue().toString() : "";
            ev.setExpression(expressoin);
            ev.setType(ExpressionValue.JAVASCRIPT);
            condition.addValue(ev);
         }

         case "SUBQUERY" -> {
            WorksheetTable.SubQueryInfo sq = v.getSubQuery();

            if(sq == null || sq.getSubQueryName() == null) {
               return;
            }

            SubQueryValue subQuery = new SubQueryValue();
            subQuery.setQuery(sq.getSubQueryName());

            TableAssembly queryTable = (TableAssembly) worksheet.getAssembly(sq.getSubQueryName());

            if(queryTable != null) {
               ColumnSelection queryCs = queryTable.getColumnSelection(true);
               DataRef attrRef = queryCs.getAttribute(sq.getInSubQueryColumn());
               subQuery.setAttribute(attrRef);

               // Correlated subquery: per-row filter linking subquery to main table.
               WorksheetTable.SubQueryWhere where = sq.getWhere();

               if(where != null) {
                  DataRef subAttrRef = queryCs.getAttribute(where.getSubQueryColumn());
                  subQuery.setSubAttribute(subAttrRef);
                  DataRef mainAttrRef = columns.getAttribute(where.getCurrentTableColumn());
                  subQuery.setMainAttribute(mainAttrRef);
               }
            }

            condition.addValue(subQuery);
         }

         default -> condition.addValue(v.getValue());
      }
   }

   // ─── Column extraction for response ──────────────────────────────────────
   private List<WorksheetColumnData> extractColumnsFromSelection(
      AbstractTableAssembly table)
   {
      ColumnSelection cs = table.getColumnSelection(true);

      if(cs == null) {
         return Collections.emptyList();
      }

      List<WorksheetColumnData> result = new ArrayList<>(cs.getAttributeCount());

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef attr = cs.getAttribute(i);

         if(attr instanceof ColumnRef cr && cr.isVisible()) {
            String name = cr.getName();
            String type = cr.getDataType();
            result.add(new WorksheetColumnData(name, type));
         }
      }

      return result;
   }

   // Worksheet model helpers.

   private WorksheetTableModel buildWorksheetTableModel(Worksheet worksheet,
                                                        AbstractTableAssembly table)
   {
      WorksheetTableModel.Builder builder = WorksheetTableModel.builder()
         .name(table.getName())
         .tableType(getTableType(table))
         .columns(extractModelColumns(table));

      String description = table.getDescription();

      if(!Tool.isEmptyString(description)) {
         builder.description(description);
      }

      List<String> baseTables = getBaseTables(table);

      if(!baseTables.isEmpty()) {
         builder.baseTables(baseTables);
      }

      List<JoinPath> joinPaths = extractJoinPaths(table);

      if(!joinPaths.isEmpty()) {
         builder.joinPaths(joinPaths);
      }

      WorksheetAggregateInfo aggregateInfo = extractAggregateInfo(table);

      if(aggregateInfo != null) {
         builder.aggregateInfo(aggregateInfo);
      }

      List<VisualizationConditionModel.ConditionNode> pre =
         buildConditionNodes(table.getPreConditionList());

      if(pre != null) {
         builder.preAggregateCondition(pre);
      }

      List<VisualizationConditionModel.ConditionNode> post =
         buildConditionNodes(table.getPostConditionList());

      if(post != null) {
         builder.postAggregateCondition(post);
      }

      List<VisualizationConditionModel.ConditionNode> ranking =
         buildConditionNodes(table.getRankingConditionList());

      if(ranking != null) {
         builder.rankingCondition(ranking);
      }

      return builder.build();
   }

   /**
    * Extract the visible columns of a table mirroring the TypeScript {@code TableColumn} shape
    * (name + optional alias/description + type). Inverse of {@link #buildColumnSelection}.
    */
   private List<WorksheetColumnData> extractModelColumns(AbstractTableAssembly table) {
      ColumnSelection cs = table.getColumnSelection(true);

      if(cs == null) {
         return Collections.emptyList();
      }

      List<WorksheetColumnData> result = new ArrayList<>(cs.getAttributeCount());

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef attr = cs.getAttribute(i);

         if(attr instanceof ColumnRef cr && cr.isVisible()) {
            DataRef underlying = cr.getDataRef() != null ? cr.getDataRef() : cr;
            String dbName = underlying.getAttribute();
            String aliasRaw = !Tool.isEmptyString(cr.getAlias()) ? cr.getAlias() : null;
            String alias = aliasRaw != null && !aliasRaw.equals(dbName) ? aliasRaw : null;
            String description = !Tool.isEmptyString(cr.getDescription()) ? cr.getDescription() : null;
            result.add(new WorksheetColumnData(cr.getName(), alias, description, cr.getDataType()));
         }
      }

      return result;
   }

   // ─── Join path reconstruction ─────────────────────────────────────────────

   /**
    * Reconstruct the {@link JoinPath} list of a relational join table from its operators
    * (inverse of {@link #buildJoinTable}). Returns an empty list for non-join tables.
    */
   private List<JoinPath> extractJoinPaths(AbstractTableAssembly table) {
      if(!(table instanceof RelationalJoinTableAssembly join)) {
         return Collections.emptyList();
      }

      List<JoinPath> paths = new ArrayList<>();
      Enumeration<TableAssemblyOperator> ops = join.getOperators();

      while(ops.hasMoreElements()) {
         for(TableAssemblyOperator.Operator op : ops.nextElement().getOperators()) {
            paths.add(reverseJoinPath(op));
         }
      }

      return paths;
   }

   /** Map a {@link TableAssemblyOperator.Operator} back to a {@link JoinPath}. */
   private JoinPath reverseJoinPath(TableAssemblyOperator.Operator op) {
      int operation = op.getOperation();
      String type = WorksheetConstructionModel.JoinType.INNER;
      String operator = WorksheetConstructionModel.JoinOperator.EQUALS;

      if(operation == TableAssemblyOperator.LEFT_JOIN) {
         type = WorksheetConstructionModel.JoinType.LEFT;
      }
      else if(operation == TableAssemblyOperator.RIGHT_JOIN) {
         type = WorksheetConstructionModel.JoinType.RIGHT;
      }
      else if(operation == TableAssemblyOperator.FULL_JOIN) {
         type = WorksheetConstructionModel.JoinType.FULL;
      }
      else if(operation == TableAssemblyOperator.CROSS_JOIN) {
         type = WorksheetConstructionModel.JoinType.CROSS;
      }
      else if(operation == TableAssemblyOperator.NOT_EQUAL_JOIN) {
         operator = WorksheetConstructionModel.JoinOperator.NOT_EQUALS;
      }
      else if(operation == TableAssemblyOperator.GREATER_JOIN) {
         operator = WorksheetConstructionModel.JoinOperator.GREATER;
      }
      else if(operation == TableAssemblyOperator.GREATER_EQUAL_JOIN) {
         operator = WorksheetConstructionModel.JoinOperator.GREATER_EQUALS;
      }
      else if(operation == TableAssemblyOperator.LESS_JOIN) {
         operator = WorksheetConstructionModel.JoinOperator.LESS;
      }
      else if(operation == TableAssemblyOperator.LESS_EQUAL_JOIN) {
         operator = WorksheetConstructionModel.JoinOperator.LESS_EQUALS;
      }
      // else INNER_JOIN → inner / "="

      return new JoinPath(op.getLeftTable(), refName(op.getLeftAttribute()),
                          op.getRightTable(), refName(op.getRightAttribute()), type, operator);
   }

   // ─── Aggregate info reconstruction ────────────────────────────────────────

   /**
    * Reconstruct the {@link WorksheetAggregateInfo} of a table (inverse of
    * {@link #applyAggregateInfo}). Returns null when the table is not aggregated.
    */
   private WorksheetAggregateInfo extractAggregateInfo(AbstractTableAssembly table) {
      AggregateInfo info = table.getAggregateInfo();

      if(info == null || info.isEmpty()) {
         return null;
      }

      List<GroupByField> groups = new ArrayList<>();

      for(GroupRef group : info.getGroups()) {
         groups.add(reverseGroup(group));
      }

      List<AggregateField> aggregates = new ArrayList<>();

      for(AggregateRef agg : info.getAggregates()) {
         aggregates.add(reverseAggregate(agg));
      }

      return new WorksheetAggregateInfo(groups.isEmpty() ? null : List.copyOf(groups),
                                        aggregates.isEmpty() ? null : List.copyOf(aggregates));
   }

   private GroupByField reverseGroup(GroupRef group) {
      DateRangeRef dateRange = findDateRangeRef(group.getDataRef());

      if(dateRange != null) {
         String level = WizDateLevelUtil.getDateGroupLevelName(dateRange.getDateOption());
         DataRef base = dateRange.getDataRef();
         return new GroupByField(base != null ? refName(base) : refName(group), level);
      }

      return new GroupByField(refName(group), null);
   }

   private AggregateField reverseAggregate(AggregateRef agg) {
      DataRef base = agg.getDataRef();
      String fieldName;
      String alias = null;

      if(base instanceof ColumnRef cr) {
         DataRef underlying = cr.getDataRef() != null ? cr.getDataRef() : cr;
         fieldName = refName(underlying);

         if(!Tool.isEmptyString(cr.getAlias()) && !cr.getAlias().equals(fieldName)) {
            alias = cr.getAlias();
         }
      }
      else {
         fieldName = base != null ? refName(base) : refName(agg);
      }

      AggregateFormula formula = agg.getFormula();
      String formulaName = formula != null ? formula.getName() : null;

      DataRef secondary = agg.getSecondaryColumn();
      String secondaryField = secondary != null ? refName(secondary) : null;

      Integer n = formula != null && formula.hasN() && agg.getN() != 0 ? agg.getN() : null;

      return new AggregateField(fieldName, formulaName, alias, secondaryField, n);
   }

   /** Unwrap a {@link DateRangeRef} from a ref or its wrapping {@link ColumnRef}; null if none. */
   private DateRangeRef findDateRangeRef(DataRef ref) {
      if(ref instanceof DateRangeRef dr) {
         return dr;
      }

      if(ref instanceof ColumnRef cr && cr.getDataRef() instanceof DateRangeRef dr) {
         return dr;
      }

      return null;
   }

   // ─── Condition tree reconstruction ────────────────────────────────────────

   /**
    * Reconstruct a nested {@link VisualizationConditionModel.ConditionNode} tree from a flat
    * {@link ConditionList} (inverse of {@link #buildConditionList}). The nesting is driven solely
    * by junction-operator levels: the lowest-level junctions split the top-level siblings, deeper
    * junctions form parenthesized groups (recursively).
    * <p>
    * Note: reconstruction is best-effort and not guaranteed to round-trip byte-for-byte. In
    * particular the forward {@code equal=true} expansion of {@code <}/{@code >} into two ops joined
    * by OR cannot be collapsed back, and FIELD/EXPRESSION operands (both stored as
    * {@link ExpressionValue}) are reported as EXPRESSION.
    *
    * @return the node list, or null when there are no conditions.
    */
   private List<VisualizationConditionModel.ConditionNode> buildConditionNodes(
      ConditionListWrapper wrapper)
   {
      if(wrapper == null || wrapper.isEmpty()) {
         return null;
      }

      ConditionList list = wrapper.getConditionList();

      if(list == null || list.getSize() == 0) {
         return null;
      }

      List<ConditionItem> items = new ArrayList<>();
      List<JunctionOperator> junctions = new ArrayList<>();

      for(int i = 0; i < list.getSize(); i++) {
         if(i % 2 == 0) {
            ConditionItem ci = list.getConditionItem(i);

            if(ci != null) {
               items.add(ci);
            }
         }
         else {
            JunctionOperator jo = list.getJunctionOperator(i);

            if(jo != null) {
               junctions.add(jo);
            }
         }
      }

      if(items.isEmpty()) {
         return null;
      }

      return nestNodes(items, junctions, 0, items.size() - 1);
   }

   private List<VisualizationConditionModel.ConditionNode> nestNodes(
      List<ConditionItem> items, List<JunctionOperator> junctions, int lo, int hi)
   {
      List<VisualizationConditionModel.ConditionNode> nodes = new ArrayList<>();

      if(lo == hi) {
         nodes.add(makeLeaf(items.get(lo), null));
         return nodes;
      }

      // The minimum junction level in [lo, hi-1] drives the top-level split.
      int minLevel = Integer.MAX_VALUE;

      for(int j = lo; j < hi; j++) {
         minLevel = Math.min(minLevel, junctions.get(j).getLevel());
      }

      int segLo = lo;
      String pendingJunction = null;

      for(int j = lo; j < hi; j++) {
         if(junctions.get(j).getLevel() == minLevel) {
            nodes.add(buildSegment(items, junctions, segLo, j, pendingJunction));
            pendingJunction = junctionName(junctions.get(j));
            segLo = j + 1;
         }
      }

      nodes.add(buildSegment(items, junctions, segLo, hi, pendingJunction));
      return nodes;
   }

   private VisualizationConditionModel.ConditionNode buildSegment(
      List<ConditionItem> items, List<JunctionOperator> junctions, int lo, int hi, String junction)
   {
      if(lo == hi) {
         return makeLeaf(items.get(lo), junction);
      }

      return new VisualizationConditionModel.ConditionGroup(
         junction, List.copyOf(nestNodes(items, junctions, lo, hi)));
   }

   private String junctionName(JunctionOperator jo) {
      return jo.getJunction() == JunctionOperator.OR ? "or" : "and";
   }

   private VisualizationConditionModel.ConditionLeaf makeLeaf(ConditionItem item, String junction) {
      DataRef attr = item.getAttribute();
      XCondition xc = item.getXCondition();

      String field;
      String aggregateFormula = null;
      String secondaryField = null;
      Integer nOrP = null;
      String dateGroupLevel = null;

      // Field + aggregate / date-group metadata.
      if(attr instanceof AggregateRef agg) {
         DataRef base = agg.getDataRef();
         field = base != null ? refName(base) : refName(agg);
         AggregateFormula formula = agg.getFormula();

         if(formula != null) {
            aggregateFormula = formula.getName();
         }

         DataRef secondary = agg.getSecondaryColumn();

         if(secondary != null) {
            secondaryField = refName(secondary);
         }

         if(formula != null && formula.hasN() && agg.getN() != 0) {
            nOrP = agg.getN();
         }
      }
      else {
         DateRangeRef dateRange = findDateRangeRef(attr);

         if(dateRange != null) {
            dateGroupLevel = WizDateLevelUtil.getDateGroupLevelName(dateRange.getDateOption());
            DataRef base = dateRange.getDataRef();
            field = base != null ? refName(base) : refName(attr);
         }
         else {
            field = refName(attr);
         }
      }

      // Operation + negated + equal + values.
      String operation = null;
      boolean negated = false;
      Boolean equal = null;
      List<VisualizationConditionModel.ValueSpec> values = null;

      if(xc instanceof RankingCondition rc) {
         operation = reverseOperation(rc.getOperation());
         negated = rc.isNegated();
         values = List.of(new VisualizationConditionModel.ValueSpec("VALUE", rc.getN(), null));
      }
      else if(xc != null) {
         int op = xc.getOperation();
         operation = reverseOperation(op);
         negated = xc.isNegated();

         // equal only carries meaning for <= / >= (LESS_THAN / GREATER_THAN).
         if(op == XCondition.LESS_THAN || op == XCondition.GREATER_THAN) {
            equal = xc.isEqual();
         }

         if(xc instanceof Condition cond) {
            List<VisualizationConditionModel.ValueSpec> vals = new ArrayList<>();

            for(int i = 0; i < cond.getValueCount(); i++) {
               vals.add(reverseValue(cond.getValue(i)));
            }

            if(!vals.isEmpty()) {
               values = List.copyOf(vals);
            }
         }
      }

      VisualizationConditionModel.ConditionSpec spec = new VisualizationConditionModel.ConditionSpec(
         field, aggregateFormula, secondaryField, nOrP, dateGroupLevel, negated, operation, equal, values);

      return new VisualizationConditionModel.ConditionLeaf(junction, spec);
   }

   private String reverseOperation(int op) {
      return switch(op) {
         case XCondition.EQUAL_TO -> "EQUAL_TO";
         case XCondition.ONE_OF -> "ONE_OF";
         case XCondition.LESS_THAN -> "LESS_THAN";
         case XCondition.GREATER_THAN -> "GREATER_THAN";
         case XCondition.BETWEEN -> "BETWEEN";
         case XCondition.STARTING_WITH -> "STARTING_WITH";
         case XCondition.CONTAINS -> "CONTAINS";
         case XCondition.LIKE -> "LIKE";
         case XCondition.NULL -> "NULL";
         case XCondition.DATE_IN -> "DATE_IN";
         case XCondition.TOP_N -> "TOP_N";
         case XCondition.BOTTOM_N -> "BOTTOM_N";
         default -> {
            LOG.warn("Unrecognized XCondition operation code '{}', omitting from condition spec", op);
            yield null;
         }
      };
   }

   private VisualizationConditionModel.ValueSpec reverseValue(Object value) {
      if(value instanceof SubQueryValue sq) {
         VisualizationConditionModel.Where where = null;

         if(sq.getSubAttribute() != null || sq.getMainAttribute() != null) {
            where = new VisualizationConditionModel.Where(
               refName(sq.getSubAttribute()), refName(sq.getMainAttribute()));
         }

         VisualizationConditionModel.SubQuery sub = new VisualizationConditionModel.SubQuery(
            sq.getQuery(), refName(sq.getAttribute()), where);
         return new VisualizationConditionModel.ValueSpec("SUBQUERY", null, sub);
      }
      else if(value instanceof ExpressionValue ev) {
         return new VisualizationConditionModel.ValueSpec("EXPRESSION", ev.getExpression(), null);
      }
      else if(value instanceof UserVariable uv) {
         return new VisualizationConditionModel.ValueSpec("SESSION_DATA", uv.getName(), null);
      }
      else if(value instanceof DataRef ref) {
         return new VisualizationConditionModel.ValueSpec("FIELD", refName(ref), null);
      }
      else {
         return new VisualizationConditionModel.ValueSpec("VALUE", value, null);
      }
   }

   private String refName(DataRef ref) {
      if(ref == null) {
         return null;
      }

      String name = ref.getName();
      return !Tool.isEmptyString(name) ? name : ref.getAttribute();
   }

   private WorksheetSource resolveWorksheet(String wsIdentifier, Principal user)
      throws Exception
   {
      AssetEntry worksheetEntry = AssetEntry.createAssetEntry(wsIdentifier);
      AbstractSheet sheet = viewsheetService.getAssetRepository()
         .getSheet(worksheetEntry, user, true, AssetContent.ALL);

      if(!(sheet instanceof Worksheet worksheet)) {
         throw new IllegalArgumentException(
            "wsIdentifier does not reference a worksheet: " + wsIdentifier);
      }

      return new WorksheetSource(worksheet, getIdentifier(worksheetEntry, wsIdentifier));
   }

   private List<String> getBaseTables(AbstractTableAssembly table) {
      if(table instanceof ComposedTableAssembly composed) {
         String[] names = composed.getTableNames();
         return names == null ? Collections.emptyList() : Arrays.asList(names);
      }

      return Collections.emptyList();
   }

   private String getTableType(AbstractTableAssembly table) {
      if(table instanceof PhysicalBoundTableAssembly) {
         return "physical table";
      }
      else if(table instanceof MirrorTableAssembly) {
         return "mirror table";
      }
      else if(table instanceof RelationalJoinTableAssembly) {
         return "relational join table";
      }
      else if(table instanceof SQLBoundTableAssembly) {
         return "sql query table";
      }

      String fallback = table.getClass().getSimpleName();
      LOG.warn("Unrecognized worksheet table type for table '{}', falling back to '{}'",
               table.getName(), fallback);
      return fallback;
   }

   private String getIdentifier(AssetEntry entry, String fallback) {
      String identifier = entry != null ? entry.toIdentifier() : null;
      return Tool.isEmptyString(identifier) ? fallback : identifier;
   }

   private record WorksheetSource(Worksheet worksheet, String identifier) {
   }

   // Join operation mapping.

   private int getJoinOperation(String joinType, String joinOp) {
      if(joinType == null) {
         joinType = WorksheetConstructionModel.JoinType.INNER;
      }

      return switch(joinType) {
         case WorksheetConstructionModel.JoinType.FULL  -> TableAssemblyOperator.FULL_JOIN;
         case WorksheetConstructionModel.JoinType.CROSS -> TableAssemblyOperator.CROSS_JOIN;
         case WorksheetConstructionModel.JoinType.LEFT  -> TableAssemblyOperator.LEFT_JOIN;
         case WorksheetConstructionModel.JoinType.RIGHT -> TableAssemblyOperator.RIGHT_JOIN;
         default -> joinOp == null ? TableAssemblyOperator.INNER_JOIN :
            switch(joinOp) {
               case WorksheetConstructionModel.JoinOperator.NOT_EQUALS    -> TableAssemblyOperator.NOT_EQUAL_JOIN;
               case WorksheetConstructionModel.JoinOperator.GREATER       -> TableAssemblyOperator.GREATER_JOIN;
               case WorksheetConstructionModel.JoinOperator.GREATER_EQUALS-> TableAssemblyOperator.GREATER_EQUAL_JOIN;
               case WorksheetConstructionModel.JoinOperator.LESS          -> TableAssemblyOperator.LESS_JOIN;
               case WorksheetConstructionModel.JoinOperator.LESS_EQUALS   -> TableAssemblyOperator.LESS_EQUAL_JOIN;
               default                                                     -> TableAssemblyOperator.INNER_JOIN;
            };
      };
   }

   // ─── Dependencies ─────────────────────────────────────────────────────────

   private final ViewsheetService viewsheetService;
   private final MetadataApiService metadataApiService;
   private final InnerJoinService innerJoinService;
   private final LayoutGraphService layoutGraphService;
   private final QueryManagerService queryManagerService;
   private final XRepository xrepository;
   private final ObjectMapper objectMapper;
   private final DataSourceService dataSourceService;
   private final SecurityEngine securityEngine;

   private static String rootMessage(Throwable t) {
      Throwable root = t;
      while(root.getCause() != null) {
         root = root.getCause();
      }
      String msg = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
      int nl = msg.indexOf('\n');
      return nl > 0 ? msg.substring(0, nl) : msg;
   }

   private static final Logger LOG = LoggerFactory.getLogger(WorksheetTableService.class);
}
