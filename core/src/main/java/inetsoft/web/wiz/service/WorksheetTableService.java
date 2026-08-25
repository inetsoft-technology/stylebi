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
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.asset.internal.TabularTableAssemblyInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.PropertyMeta;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.TabularUtil;
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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

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

   // ─── Probe: discover a tabular target's columns and sample rows, persisting nothing ───────

   /**
    * Open a throwaway worksheet runtime for a run of {@link #probeTable} calls.
    *
    * <p>{@code openTemporaryWorksheet} and {@code openWorksheet} are two different things:
    * the latter opens a stored asset for editing, the former mints a pure runtime session that
    * never touches {@code AssetRepository}. This path wants the second — an annotation pass reads
    * a data source's files to describe them and must leave nothing behind, so there is no asset to
    * create, no permission to delete one with, and no cleanup task to write.</p>
    *
    * <p>Opened once for a whole data source rather than per file, and CLOSED BY THE CALLER — a
    * runtime session that is never closed is a leak, which is why {@link #closeProbeWorksheet}
    * exists as its own call rather than being folded into the last probe.</p>
    *
    * <p>Sequential by construction: the returned runtime holds ONE {@code Worksheet}, which
    * {@link #probeTable} mutates. Files of one data source must therefore be probed one at a time.
    * That is a choice, not a limit — open a runtime per file to probe them in parallel, at the cost
    * of that many live sessions.</p>
    */
   public String openProbeWorksheet(Principal user) throws Exception {
      // The same action-level gate createTables applies, for the same reason: this opens a
      // worksheet runtime, and the caller has to be allowed worksheets at all before it can.
      checkWorksheetActionPermission(user);

      return viewsheetService.openTemporaryWorksheet(user, null);
   }

   /**
    * Build one table in the probe runtime, report what it holds, and take it back out.
    *
    * <p>WHY A WORKSHEET AT ALL, given a tabular query can answer its columns without one:
    * {@code SelectableTabularQuery.getColumns()} needs no runtime, but SAMPLE ROWS do — only the
    * runner that executes the query ever sees a row, and {@code buildTabularTable} is where
    * {@code sampleRows} is wired onto the query. For a CSV that is not a nicety: a header row is
    * often the entire column-name vocabulary a file has, so the values are most of what an
    * annotation pass has to reason from. Replacing this with {@code createQuery + getColumns()}
    * would look simpler and would halve what the annotation is written from.</p>
    *
    * <p>The assembly is removed on the way out. Each file is an independent question and none of
    * them is being saved, so leaving them to pile up in the shared worksheet would only grow the
    * runtime and slow every later probe's name resolution.</p>
    *
    * <p>A failure comes back as {@code success=false} with the reason, exactly as it does inside a
    * {@code createTables} batch — the caller is an annotation loop walking a directory, and one
    * unreadable file must not end the walk. It is also how a multi-sheet workbook reports its sheet
    * names: {@code applyFileContract} refuses to guess and names them in the message.</p>
    */
   public WorksheetTableResponse probeTable(String runtimeId, WorksheetTable table, Principal user)
      throws Exception
   {
      checkWorksheetActionPermission(user);

      if(runtimeId == null || runtimeId.isBlank()) {
         throw new IllegalArgumentException("runtimeId is required; open a probe worksheet first");
      }

      if(table == null) {
         throw new IllegalArgumentException("table is required");
      }

      // Assigned rather than demanded. The name is an artifact of building an assembly and is
      // discarded with it below, so making the caller invent one per file would be asking for a
      // decision that has no consequence.
      if(table.getTableName() == null || table.getTableName().isBlank()) {
         table.setTableName("probe_" + PROBE_TABLE_SEQUENCE.incrementAndGet());
      }

      RuntimeWorksheet rws = viewsheetService.getWorksheet(runtimeId, user);
      Worksheet worksheet = rws.getWorksheet();

      try {
         WorksheetTableResponse response = addOneTable(worksheet, table, user);
         applySandboxSampleRows(worksheet, table, response, user);

         return response;
      }
      catch(Exception e) {
         return failure(table.getTableName(), rootMessage(e));
      }
      finally {
         if(worksheet.getAssembly(table.getTableName()) != null) {
            worksheet.removeAssembly(table.getTableName());
         }
      }
   }

   /**
    * Sample a FILE target's rows by executing the table that was just built.
    *
    * <p>WHY THIS EXISTS AT ALL. The two target kinds discover their columns by opposite means, and
    * the sampling mechanism was built for one of them. An endpoint has no column list until a
    * request has been answered, so {@code EndpointJsonQueryRunner} — the only writer of
    * {@code TabularQuery.setSampleRows} anywhere — takes the sample from that one unavoidable
    * response. A file's columns come from {@code ServerFileQuery.loadColumns()} reading the header,
    * which executes no query and involves no runner at all, so nothing ever fills the slot and
    * {@link #applyResponseSampleRows} finds it empty every time. Left there, the probe worksheet
    * would earn nothing: columns are obtainable with {@code createQuery + getColumns()} and no
    * runtime whatever, and the sample was the entire reason for standing one up.</p>
    *
    * <p>WHY NOT IN THE CONNECTOR. The REST mechanism exists because a REST read is expensive —
    * metered, paginated, one chance at it — so the sample has to ride along with the request that
    * was already being paid for. A local file has no such constraint: executing the table once more
    * after building it is cheap, and doing it HERE rather than in ServerFile means every future
    * file-shaped connector (OneDrive) gets sampling with no connector code at all.</p>
    *
    * <p>WHY ONLY HERE, and not in {@code buildTabularTable}. This is the annotation probe, whose
    * whole output is a description of the file. The user's own {@code createTables} must not run an
    * extra query as a side effect of adding a table to a worksheet.</p>
    *
    * <p>A FAILURE IS NOT A FAILURE OF THE PROBE. The columns are already in hand and they are what
    * the caller asked for; the values are the bonus. A corrupt row, a bad encoding, a timeout —
    * each is logged and leaves the sample absent, and the probe still answers {@code success=true}
    * with its column list.</p>
    */
   private void applySandboxSampleRows(Worksheet worksheet, WorksheetTable request,
                                       WorksheetTableResponse response, Principal user)
   {
      // EVERY statement is inside the try, the deciding one included. This method is called from
      // inside probeTable's own try, so anything that escaped here would be caught there and turn a
      // successful probe into a failed one — throwing away the column list over a missing bonus.
      // The inner catch is what makes that impossible, so nothing may sit outside it.
      AssetQuerySandbox box = null;

      try {
         int limit = sandboxSampleLimit(request, response);

         if(limit <= 0) {
            return;
         }

         // One more than asked for, which is how "there was more" is learned: the extra row is the
         // evidence, and it is dropped before the sample is reported. Without it a file of exactly
         // "limit" rows and a file of ten thousand are indistinguishable in the answer.
         int probe = limit + 1;

         if(!(worksheet.getAssembly(request.getTableName()) instanceof TableAssembly table)) {
            return;
         }

         // Bounds the CONNECTOR's read, not just what is copied out of the lens.
         // TabularBoundQuery.merge pushes this onto the query as XQuery.rowlimit
         // (PreAssetQuery.getDefinedMaxRows -> getMaxRows), and ServerFileRuntime's row loop tests
         // exactly that: `while(node.next() && (maxRows <= 0 || getRowCount() <= maxRows))`. So a
         // 200MB CSV stops after probe rows rather than being read to the end and then trimmed.
         // Not restored: this assembly is removed in probeTable's finally and never persisted.
         table.setMaxRows(probe);

         // A FRESH sandbox, disposed here, rather than the runtime's own. getTableLens caches by
         // (name, mode, aggregate, column-selection hash) and probeTable is a loop that reuses one
         // table name across files — a directory of monthly exports with identical headers hashes
         // identically, so the runtime's cache would answer February with January's rows. A private
         // sandbox cannot have a stale entry. Mirrors probeExecutable, which builds its own for the
         // same one-shot reason.
         box = new AssetQuerySandbox(worksheet);
         box.setBaseUser(user);

         // RUNTIME_MODE, not the LIVE_MODE probeExecutable uses. LIVE samples input tables down to
         // the sandbox's design-time cap (see WorksheetPreviewService), which is harmless when the
         // only question is "did the query run" and wrong when the answer IS the values.
         TableLens lens = box.getTableLens(request.getTableName(),
                                           AssetQuerySandbox.RUNTIME_MODE);

         if(lens == null) {
            return;
         }

         List<Map<String, Object>> rows = readSampleRows(lens, probe);

         if(rows.isEmpty()) {
            return;
         }

         boolean truncated = rows.size() > limit;
         response.setSampleRows(List.copyOf(truncated ? rows.subList(0, limit) : rows));

         // Only when true, matching how the endpoint path reports it: a consumer reading values out
         // of a sample has to tell "not in the file" from "not sampled".
         if(truncated) {
            response.setSampleRowsTruncated(true);
         }
      }
      catch(Exception ex) {
         LOG.warn("Could not sample rows for probe table '{}'; reporting its columns without them",
                  request.getTableName(), ex);
      }
      finally {
         // Caught separately because a finally block runs AFTER the catch above and would otherwise
         // throw straight past it — the one way a sampling problem could still fail the probe.
         if(box != null) {
            try {
               box.dispose();
            }
            catch(Exception ex) {
               LOG.warn("Could not dispose the sampling sandbox for probe table '{}'",
                        request.getTableName(), ex);
            }
         }
      }
   }

   /**
    * How many rows to sample for this probe, or 0 for none.
    *
    * <p>KEYED ON {@code targetKind == "file"}, which is what keeps the endpoint path untouched.
    * Testing "the response has no sample yet" instead would look equivalent and is not: an endpoint
    * whose first page came back empty also reports no sample, and re-executing to check would bill
    * the customer a second time for a question already answered. The kind is the only signal that
    * cannot be confused with an empty result.</p>
    *
    * <p>Opt-in, unchanged: {@code sampleRows} unset or 0 means none, so a caller that wanted only
    * the column list runs exactly the work it ran before. {@code rest.sample.rows} is the
    * deployment's ceiling on that decision and is honoured here too — it is the switch that stops
    * sampled customer data leaving a tabular connector, and a deployment that set it to 0 must not
    * be circumvented by a different kind of target reaching the same data. A value that is not a
    * number yields no sample, which is the right way to be wrong about a knob governing customer
    * data.</p>
    */
   static int sandboxSampleLimit(WorksheetTable request, WorksheetTableResponse response) {
      if(request == null || response == null || !response.isSuccess() ||
         response.getSampleRows() != null)
      {
         return 0;
      }

      WorksheetTable.TabularSource src = request.getTabularSource();

      if(src == null || src.getSampleRows() == null || src.getSampleRows() <= 0) {
         return 0;
      }

      String kind;

      try {
         kind = targetKindOf(src);
      }
      catch(RuntimeException ex) {
         // A kind this build accepted cannot be invalid, but the predicate must be safe to ask
         // about anything rather than throw out of a by-product step.
         return 0;
      }

      if(!TARGET_KIND_FILE.equals(kind)) {
         return 0;
      }

      int ceiling;

      try {
         ceiling = Integer.parseInt(SreeEnv.getProperty(
            SAMPLE_ROWS_PROPERTY, Integer.toString(DEFAULT_SAMPLE_ROWS)));
      }
      catch(Exception ex) {
         return 0;
      }

      return ceiling <= 0 ? 0 : Math.min(src.getSampleRows(), ceiling);
   }

   /**
    * Copy at most {@code limit} data rows out of a lens, keyed by column name.
    *
    * <p>Row 0 is the header in StyleBI's {@code TableLens} convention, so data starts at 1. The read
    * is bounded by {@code moreRows(row)} per row rather than by a row count taken up front: asking
    * a lens how many rows it has forces the whole query to completion, which is the opposite of
    * sampling.</p>
    *
    * <p>Keyed by name, and by the SAME name the {@code columns} list reports, so a consumer can pair
    * the two without positional arithmetic. That also matches the endpoint path's rows, which are
    * JSON objects keyed by the response's own field names.</p>
    */
   private static List<Map<String, Object>> readSampleRows(TableLens lens, int limit) {
      int colCount = lens.getColCount();
      String[] headers = new String[colCount];

      for(int col = 0; col < colCount; col++) {
         Object header = lens.getObject(0, col);
         headers[col] = header != null ? header.toString() : "col" + col;
      }

      List<Map<String, Object>> rows = new ArrayList<>();

      for(int row = 1; rows.size() < limit && lens.moreRows(row); row++) {
         Map<String, Object> values = new LinkedHashMap<>();

         for(int col = 0; col < colCount; col++) {
            values.put(headers[col], toSampleCell(lens.getObject(row, col)));
         }

         rows.add(values);
      }

      return rows;
   }

   /**
    * Reduce one cell to something Jackson can write and a consumer can read.
    *
    * <p>Deliberately identical to {@code WorksheetPreviewService.toJsonSafe}: both hand worksheet
    * cell values to the same caller, and two rules for the same value would mean a date that is a
    * string in one answer and an object in the other. Numbers, strings and booleans pass through as
    * themselves; a date or {@code Temporal} becomes its {@code toString()} rather than a Jackson
    * object graph or an epoch number nothing labels; {@code byte[]} becomes a marker because a
    * base64 blob is never what a sample is read for.</p>
    */
   private static Object toSampleCell(Object value) {
      if(value == null) {
         return null;
      }

      if(value instanceof String || value instanceof Number || value instanceof Boolean) {
         return value;
      }

      if(value instanceof java.util.Date || value instanceof java.time.temporal.Temporal) {
         return value.toString();
      }

      if(value instanceof byte[]) {
         return "(binary)";
      }

      return value.toString();
   }

   /** Release what {@link #openProbeWorksheet} opened. Nothing was persisted, so nothing is left. */
   public void closeProbeWorksheet(String runtimeId, Principal user) throws Exception {
      checkWorksheetActionPermission(user);

      if(runtimeId == null || runtimeId.isBlank()) {
         throw new IllegalArgumentException("runtimeId is required");
      }

      viewsheetService.closeWorksheet(runtimeId, user);
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
            table.getColumnSelection(true), request.getPreAggregateCondition(), worksheet, false,
            table.getColumnSelection(false));
         table.setPreConditionList(preList);
      }

      // 4. Aggregation.
      if(request.getAggregateInfo() != null) {
         applyAggregateInfo(table, request.getAggregateInfo());
      }

      // 5. Post-aggregate conditions (HAVING).
      if(request.getPostAggregateCondition() != null && !request.getPostAggregateCondition().isEmpty()) {
         ConditionList postList = buildConditionList(
            table.getColumnSelection(true), request.getPostAggregateCondition(), worksheet, true,
            table.getColumnSelection(false));
         table.setPostConditionList(postList);
      }

      // 6. Ranking / top-N.
      if(request.getRankingCondition() != null && !request.getRankingCondition().isEmpty()) {
         ConditionList rankList = buildRankingConditionList(
            table.getColumnSelection(true), request.getRankingCondition(),
            table.getColumnSelection(false));
         table.setRankingConditionList(rankList);
      }

      // 7. Persist any synthetic date-part column(s) that a condition/ranking dateGroupLevel
      // registered into the private column selection (see applyDateGroupLevel). Mirrors the
      // finalize step in applyAggregateInfo — without re-running setColumnSelection, the query
      // engine's merge-eligibility check can't see the newly added column, and an unresolvable
      // column falls back to StyleBI's in-memory condition evaluation, which defaults to
      // matching every row instead of filtering or erroring.
      if(hasDateGroupLevel(request.getPreAggregateCondition()) ||
         hasDateGroupLevel(request.getPostAggregateCondition()) ||
         hasDateGroupLevel(request.getRankingCondition()))
      {
         table.setColumnSelection(table.getColumnSelection(false), false);
      }

      // 8. Execution probe for render-time-executable tables. windowColumns are probed here too:
      // when the base can't fully SQL-merge, AssetQuery computes the window in-memory
      // (getWindowTableLens → buildWindowLens → WindowTableLens), which is a fully supported path
      // (WindowTableLens delegates its base columns' identifiers so downstream column resolution
      // still finds them — see WindowTableLens.getColumnIdentifier). The probe surfaces any
      // genuine render-time failure instead of letting it reach the viewer as an empty result.
      if(shouldProbe(request)) {
         probeExecutable(worksheet, table, user);
      }

      // 9. Build the success response.
      List<WorksheetColumnData> columns = extractColumnsFromSelection(table);

      WorksheetTableResponse response = new WorksheetTableResponse();
      response.setTableName(table.getName());
      response.setColumns(columns);
      applyResponseShape(response, table);
      applyResponseSampleRows(response, table);

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

   /**
    * Report the shape of the response the endpoint returned, when this table came from one.
    *
    * <p>Only a {@code tabular table} has one: it is the only table type built by sending a request,
    * and {@code loadColumnSelection} has already sent it by the time this runs. So the shape is a
    * BY-PRODUCT of work already done and already paid for — no second request, and nothing to
    * enable. See {@code EndpointJsonQueryRunner} for where it is distilled and why there.</p>
    *
    * <p>WHY THE CALLER NEEDS IT, given it also gets {@code columns}: the columns are what the row
    * path it chose produced, and {@code jsonPath} defaults to {@code "$"}
    * ({@code RestJsonQuery.getValidJsonPath}). A caller that guessed wrong against an envelope
    * response — {@code {object, url, has_more, data:[...]}} — gets a one-row table of envelope
    * columns and no way to tell that from a correct result. The shape is distilled BEFORE the row
    * path is applied, so it is unaffected by that mistake and is what identifies the real rows.</p>
    *
    * <p>Absent is normal and not an error: a non-tabular table has no response, a connector may
    * report no shape, and {@code getQuery()} is {@code @Nullable} when the data source plugin is
    * missing.</p>
    */
   private void applyResponseShape(WorksheetTableResponse response, AbstractTableAssembly table) {
      if(!(table.getTableInfo() instanceof TabularTableAssemblyInfo info)) {
         return;
      }

      TabularQuery query = info.getQuery();

      if(query == null || query.getResponseShape() == null) {
         return;
      }

      response.setResponseSchema(query.getResponseShape());

      // Only set when true. A truncated shape is the exceptional case, and a caller has to be able
      // to tell "this path is not in the response" from "this path was not reached" — the flag is
      // the only thing that carries the difference.
      if(query.isResponseShapeTruncated()) {
         response.setResponseSchemaTruncated(true);
      }
   }

   /**
    * Report a sample of the rows the endpoint returned, when this table came from one.
    *
    * <p>Same by-product as the shape and from the same single request — see
    * {@link #applyResponseShape}. What it adds is the VALUES, which is what a caller needs when the
    * legal values of one endpoint's parameter are rows of another's data; the shape can only say
    * that the field exists.</p>
    *
    * <p>Kept separate from {@code applyResponseShape} rather than folded in, because the two carry
    * different things: a shape is a property of the connector, and a sample is customer data that
    * a deployment can switch off ({@code rest.sample.rows}). They are consumed, bounded and
    * governed separately, so they are read separately.</p>
    *
    * <p>Absent is normal: a non-tabular table has no response, sampling may be off, and an empty
    * page yields no rows.</p>
    */
   private void applyResponseSampleRows(WorksheetTableResponse response,
                                        AbstractTableAssembly table)
   {
      if(!(table.getTableInfo() instanceof TabularTableAssemblyInfo info)) {
         return;
      }

      TabularQuery query = info.getQuery();

      if(query == null || query.getSampleRows() == null || query.getSampleRows().isEmpty()) {
         return;
      }

      response.setSampleRows(query.getSampleRows());

      // Only set when true, same as the shape's flag: a caller extracting parameter values has to be
      // able to tell "the response has no such value" from "the sample stopped before it".
      if(query.isSampleRowsTruncated()) {
         response.setSampleRowsTruncated(true);
      }
   }

   // ─── Probe: execute a freshly-built table to surface render-time query failures ──────────

   /**
    * Whether {@link #addOneTable} should run the execution probe for this request. A {@code sql query
    * table} always executes its raw SQL at render time, and a table carrying expression columns (JS or
    * {@code sql:true}) evaluates them at render time — both can be structurally valid yet fail on
    * execution. {@code windowColumns} carry the same risk: the pushed-down {@code OVER(...)} SQL
    * (see {@link WindowExpressionRef}) requires its base table to fully SQL-merge, which silently
    * fails when the lineage contains an upstream JS-evaluated expression column — the window then
    * produces an empty result at render time instead of the fail-loud error this class otherwise
    * guarantees. Pure physical / mirror / join tables with no expression or window columns carry no
    * render-time execution risk beyond what structural creation already validates, so they are not
    * probed (zero added latency).
    */
   // Package-private for unit testing (WorksheetTableServiceShouldProbeTest).
   boolean shouldProbe(WorksheetTable request) {
      if("sql query table".equals(request.getTableType())) {
         return true;
      }

      List<WorksheetTable.ExpressionColumnInfo> exprCols = request.getExpressionColumns();

      if(exprCols != null && !exprCols.isEmpty()) {
         return true;
      }

      List<WorksheetTable.WindowColumnInfo> winCols = request.getWindowColumns();
      return winCols != null && !winCols.isEmpty();
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
   private void probeExecutable(Worksheet worksheet, AbstractTableAssembly table, Principal user)
      throws Exception
   {
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

            // A query that runs is not yet a query that produces what this table promises — see
            // checkProducedColumns.
            checkProducedColumns(table, lens);
         }
      }
      finally {
         box.dispose();
      }
   }

   /**
    * Fail when the probe's query produces FEWER columns than the table assembly advertises.
    *
    * <p>{@link #probeExecutable} on its own only answers "did the query run". It cannot see a column
    * quietly going missing, because that is not an error anywhere in the query layer:
    * {@code PreAssetQuery.validateColumnSelection} REMOVES a column the source does not have and
    * carries on, so the generated SQL stays valid and the probe passes. The assembly still holds the
    * removed column in its stored selection, so every read-back this API serves ({@code /ws/table}'s
    * response, {@code /ws/structure}) keeps advertising a column no query can ever produce — a chart
    * bound to it renders empty with no error, and the column disappears from the worksheet as soon as
    * Composer refreshes it.
    *
    * <p>This is the backstop for that class of silent loss in general. The known cause — an explicit
    * {@code columns} entry the physical source does not have — is rejected up front by
    * {@link #resolveRequestedColumns}; this catches whatever else reaches the same end state
    * (a stale mirror base, an expression referencing a vanished column) for the tables that are
    * probed at all.
    */
   private void checkProducedColumns(AbstractTableAssembly table, TableLens lens) {
      List<String> advertised = advertisedColumnNames(table);
      List<String> produced = producedColumnNames(lens);

      if(produced.size() >= advertised.size()) {
         return;
      }

      List<String> dropped = droppedColumns(advertised, produced);

      throw new IllegalArgumentException(
         "Table '" + table.getName() + "' advertises " + advertised.size() +
         " column(s) but its query produces only " + produced.size() + ": " +
         (dropped.isEmpty() ? String.join(", ", produced)
            : "the query cannot produce " + String.join(", ", dropped)) +
         ". A column the query cannot resolve is dropped from it instead of failing it, so this " +
         "table would render as an empty or partial result. Check each column resolves against " +
         "what this table is built on — the source table for a physical table, the SELECT list for " +
         "a sql query table, the base table for a mirror or join.");
   }

   /** The columns this table promises its callers — exactly what {@link #extractColumnsFromSelection} reports. */
   private static List<String> advertisedColumnNames(AbstractTableAssembly table) {
      ColumnSelection cs = table.getColumnSelection(true);
      List<String> names = new ArrayList<>();

      for(int i = 0; cs != null && i < cs.getAttributeCount(); i++) {
         if(cs.getAttribute(i) instanceof ColumnRef cr && cr.isVisible()) {
            names.add(cr.getName());
         }
      }

      return names;
   }

   /**
    * The columns the executed query actually produced. {@code AssetQuery} only stamps a column
    * identifier when it differs from the header (see {@code AssetQuery.getMetaDataTableLens}), so the
    * header is the fallback, not a second-class source.
    */
   private static List<String> producedColumnNames(TableLens lens) {
      List<String> names = new ArrayList<>();

      for(int col = 0; col < Math.max(0, lens.getColCount()); col++) {
         String id = lens.getColumnIdentifier(col);

         if(id == null || id.isBlank()) {
            Object header = lens.getObject(0, col);
            id = header == null ? null : header.toString();
         }

         names.add(id);
      }

      return names;
   }

   /**
    * The {@code advertised} columns absent from {@code produced}.
    *
    * <p>Called only once the counts prove something was dropped, so a mere spelling difference
    * between a {@code ColumnRef}'s name and a lens header can never by itself fail a create — at
    * worst it makes this list empty and the caller reports the counts instead.
    */
   // Package-private for unit testing (WorksheetTableServiceProducedColumnsTest).
   static List<String> droppedColumns(List<String> advertised, List<String> produced) {
      Set<String> producedKeys = new HashSet<>();

      for(String name : produced) {
         producedKeys.addAll(matchKeys(name));
      }

      List<String> dropped = new ArrayList<>();

      for(String name : advertised) {
         Set<String> keys = matchKeys(name);

         // A nameless advertised column cannot be matched either way, so it is no evidence — the
         // caller's count-based message covers it rather than this list naming a "null" column.
         if(!keys.isEmpty() && Collections.disjoint(keys, producedKeys)) {
            dropped.add(name);
         }
      }

      return dropped;
   }

   /**
    * The forms a column name may be matched by: its own spelling, case-insensitively, plus the bare
    * attribute after the last dot — a lens reports a mirror's column bare where the assembly's
    * {@code ColumnRef} names it {@code BaseTable.col}.
    */
   private static Set<String> matchKeys(String name) {
      if(name == null || name.isBlank()) {
         return Set.of();
      }

      // Locale.ROOT: these are datasource/lens identifiers, not display text.
      String upper = name.toUpperCase(Locale.ROOT);
      int dot = upper.lastIndexOf('.');

      return dot > 0 && dot < upper.length() - 1
         ? Set.of(upper, upper.substring(dot + 1)) : Set.of(upper);
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
      String previousPrimary = worksheet.getPrimaryAssemblyName();

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

      // Worksheet.removeAssembly() clears the primary assembly to null when the deleted
      // table was primary, with no replacement — restore it when there's an unambiguous
      // candidate so a leaf table's own deletion doesn't orphan chart creation on the
      // worksheet (see restorePrimaryAssembly()).
      if(previousPrimary != null && deleted.contains(previousPrimary)
         && worksheet.getPrimaryAssemblyName() == null)
      {
         restorePrimaryAssembly(worksheet);
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
      response.setPrimaryTable(worksheet.getPrimaryAssemblyName());
      return response;
   }

   /**
    * Reassigns the worksheet's primary table after its previous primary was deleted.
    * Only acts when exactly one remaining table has no other remaining table depending
    * on it (an unambiguous leaf) — mirrors the "don't guess" convention used when a
    * batch's intended primary fails to build (see {@code addOneTable}): zero or multiple
    * candidates leave the worksheet without a primary rather than silently picking one.
    */
   // Package-private (not private) so WorksheetTableServiceDeleteTablesTest can exercise it
   // directly, mirroring the shouldProbe/applyWindowColumns test convention in this package.
   void restorePrimaryAssembly(Worksheet worksheet) {
      List<String> leaves = new ArrayList<>();

      for(Assembly asm : worksheet.getAssemblies()) {
         if(!(asm instanceof TableAssembly)) {
            continue;
         }

         if(findExternalDependent(worksheet, asm.getName(), Collections.emptySet()) == null) {
            leaves.add(asm.getName());
         }
      }

      if(leaves.size() == 1) {
         worksheet.setPrimaryAssembly(leaves.get(0));
      }
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

      // Verify READ permission on the datasource before resolving/using it. physicalSource covers
      // physical table and sql query table bind one through physicalSource; tabular table binds one
      // through tabularSource. Read here rather than inside each builder for the same reason the
      // physical check always was — it must precede every use of the path, including the one that
      // dials a remote endpoint. Mirror/join tables only reference already-in-worksheet assemblies,
      // so there is no new datasource to check for them.
      // Mirrors WorksheetAgentController.addLogicalModelTable's usage of DataSourceService.
      //
      // KEYED ON tableType, NEVER ON WHICH FIELD HAPPENS TO BE SET. tableType is what selects the
      // builder below, so it is the only thing that can say which path will actually be used. Picking
      // by field presence let a tabular request carrying BOTH fields be checked against its
      // physicalSource — a path no tabular code path ever reads — and then reach the connector on its
      // unchecked tabularSource path. buildTabularTable rejects the mismatched pair outright as well;
      // this line is what makes the check itself correct.
      WorksheetTable.PhysicalSource src = request.getPhysicalSource();
      WorksheetTable.TabularSource tabularSrc = request.getTabularSource();
      String datasourcePath = "tabular table".equals(tableType)
         ? (tabularSrc != null ? tabularSrc.getDatasourcePath() : null)
         : (src != null ? src.getDatasourcePath() : null);

      if(datasourcePath != null &&
         !dataSourceService.checkPermission(datasourcePath, ResourceAction.READ, user))
      {
         throw new IllegalArgumentException(
            "Access denied: no READ permission on datasource " + datasourcePath);
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
         case "tabular table"         -> buildTabularTable(worksheet, request);
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

      // Build column selection. Both branches need the source's real columns — the explicit branch
      // to reconcile the caller's list against them, the implicit branch to build the list from them.
      OsiDataset metaData = fetchSourceMetaData(src, user);

      if(request.getColumns() != null && !request.getColumns().isEmpty()) {
         // Explicit column list from the LLM — reconciled against the source's real columns so a
         // name the table does not have fails loud here (see resolveRequestedColumns). A name this
         // same request derives is dropped from the result: applyExpressionColumns /
         // applyWindowColumns below contribute the real derived column.
         List<WorksheetTable.ColumnInfo> cols = resolveRequestedColumns(
            request.getColumns(), sourceColumnNames(metaData), derivedColumnNames(request));
         ColumnSelection cs = buildColumnSelection(cols);
         table.setColumnSelection(cs);
      }
      else {
         // No explicit columns → take all of them from datasource metadata.
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
    * Build a {@link TabularTableAssembly} bound to ONE target of a tabular connector — an endpoint
    * of a SaaS/REST connector, or a file of a path-addressed one (ServerFile).
    *
    * <p>Follows the six steps {@code TabularQueryDialogService.setUpTable} takes, with the dialog's
    * {@code TabularView} round trip left out. The dialog needs a view because a human drives it; the
    * two things a caller supplies here — which target, and what its options are — are plain
    * annotated bean properties, so {@link TabularUtil#getPropertyMap} reaches them directly. That
    * also keeps {@code TabularUtil.callButtonMethods} out of the path, which is the only reader of
    * the {@code TabularUtil.sessionId} ThreadLocal — so unlike {@code WizTabularController}, this
    * path needs no connector session bound around it.</p>
    *
    * <p>THIS METHOD READS THE SOURCE. {@code loadColumnSelection} is how the column list comes
    * into existence — a tabular query has none until one response has been parsed
    * ({@code TabularQuery.loadOutputColumns} runs it under {@code HINT_PREVIEW} at 100 rows). On an
    * endpoint that means a real, metered request; on a file it means opening and parsing the file's
    * header. Either way a failure there is a failure of the whole table, which is why this type is
    * left out of {@link #shouldProbe}: the probe would repeat the read to answer a question the
    * non-empty column check below already answers.</p>
    *
    * <p>WHAT VARIES BY KIND is only the contract — which bean properties carry the target, which
    * ones are required, and how a failure should be described. That is
    * {@link #applyEndpointContract} / {@link #applyFileContract}; each returns a description of what
    * it dialed, which is the one thing the shared half needs from it. Everything after that —
    * the assembly, {@code setQuery}/{@code setSourceInfo}, {@code loadColumnSelection}, the
    * empty-column check — is built on {@code TabularQuery}/{@code SelectableTabularQuery} and does
    * not distinguish connectors at all.</p>
    */
   private AbstractTableAssembly buildTabularTable(Worksheet worksheet, WorksheetTable request)
      throws Exception
   {
      WorksheetTable.TabularSource src = request.getTabularSource();

      if(src == null || src.getDatasourcePath() == null || src.getDatasourcePath().isBlank()) {
         throw new IllegalArgumentException(
            "tabularSource.datasourcePath is required for tabular table");
      }

      String targetKind = targetKindOf(src);

      if(src.getTarget() == null || src.getTarget().isBlank()) {
         throw new IllegalArgumentException(
            "tabularSource.target is required for tabular table — " +
            (TARGET_KIND_FILE.equals(targetKind)
               ? "the file path relative to the data source's root folder."
               : "the connector's own name for the endpoint."));
      }

      // Meaningless in every direction: nothing on this path reads physicalSource, so a request
      // carrying both names two sources and gets one of them silently ignored. It is also the shape
      // that made the READ gate checkable against the wrong path when the gate keyed on field
      // presence, so it is refused rather than tolerated.
      if(request.getPhysicalSource() != null) {
         throw new IllegalArgumentException(
            "a tabular table cannot carry physicalSource — it is bound through tabularSource. " +
            "Remove physicalSource, or use tableType \"physical table\" if a database table is what " +
            "was meant.");
      }

      // Rejected rather than ignored, and checked before anything is dialed: a tabular table's
      // columns are discovered by the request below, so a caller cannot know them beforehand, and
      // silently dropping the list would answer a request nobody made.
      if(request.getColumns() != null && !request.getColumns().isEmpty()) {
         throw new IllegalArgumentException(
            "'columns' is not supported for tableType \"tabular table\" — an endpoint has no column " +
            "list until its request has run. Create the tabular table first, read the columns from " +
            "the response, then select or rename them on a mirror table over it.");
      }

      // applyExpressionColumns/applyWindowColumns are reached from buildPhysicalTable and
      // buildMirrorTable only, so on this type they would be accepted and then never applied — a
      // table that reports success while quietly lacking the derived column the caller asked for.
      // Rejected instead, and pointed at the mirror, which is where a derived column over an
      // endpoint belongs anyway: it needs the column list this call has not produced yet.
      if((request.getExpressionColumns() != null && !request.getExpressionColumns().isEmpty()) ||
         (request.getWindowColumns() != null && !request.getWindowColumns().isEmpty()))
      {
         throw new IllegalArgumentException(
            "expressionColumns/windowColumns are not supported for tableType \"tabular table\" — a " +
            "derived column needs the endpoint's columns, which do not exist until this request has " +
            "run. Put them on a mirror table over this one, in a later call.");
      }

      String dsName = src.getDatasourcePath();

      // Answered before createQuery rather than inferred from its failure. Pointed at a JDBC
      // database, createQuery resolves that type's query class and hands back a JDBCQuery — the
      // property map below then simply has no "endpoint", and the caller is told the connector is
      // not endpoint-based when the real answer is that this is not a tabular data source at all.
      XDataSource dataSource = xrepository.getDataSource(dsName);

      if(dataSource == null) {
         throw new IllegalArgumentException("Data source not found: " + dsName);
      }

      // Not UnsupportedDatasourceException: its message is hard-coded to "Annotations are currently
      // not supported for ... datasources", which is true where it was introduced and nonsense here.
      // The type buys nothing on this path either — createTables catches per table and keeps only
      // rootMessage(e), so it never reaches the handler that gives the type meaning.
      if(!(dataSource instanceof TabularDataSource)) {
         throw new IllegalArgumentException(
            "'" + dsName + "' is a " + dataSource.getType() + " data source, not a tabular/REST one, " +
            "so it has no endpoints to call. Use tableType \"physical table\" or \"sql query table\" " +
            "for a database.");
      }

      // createQuery logs and returns null on every failure — a missing connector plugin, an
      // unregistered type, an inaccessible query class all arrive as the same null. Absent this
      // guard the NPE lands two frames down with no mention of the data source.
      TabularQuery query = TabularUtil.createQuery(dsName);

      if(query == null) {
         throw new IllegalArgumentException(
            "Could not create a query for data source '" + dsName + "' (type '" +
            dataSource.getType() + "') — its connector plugin may not be loaded.");
      }

      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      // The one branch. Each side fills the properties its kind of target needs and returns a
      // description of what it is about to read — a built URL suffix for an endpoint, a resolved
      // path (and sheet) for a file — as proof that all of it landed, and as the only thing the
      // empty-column failure below can name. Throws with every problem named; see the methods.
      String probeDesc = switch(targetKind) {
         case TARGET_KIND_ENDPOINT -> applyEndpointContract(query, pmap, src);
         case TARGET_KIND_FILE -> applyFileContract(query, pmap, src);
         // Unreachable: targetKindOf refuses anything else. Present so a third kind added to the
         // enum without a contract fails here rather than silently building an unconfigured query.
         default -> throw new IllegalStateException(
            "No tabular contract for targetKind \"" + targetKind + "\"");
      };

      // Persisted on the query (XQuery.rowlimit, written as <maxrows>) rather than passed as a
      // HINT_MAX_ROWS hint: a hint bounds one execution, and what has to stay bounded is every
      // future render of this table. createTables sets designMaxRows to 0 for wiz analytics, which
      // on a paginated metered API means paging to the end of the customer's data every time.
      if(src.getMaxRows() != null && src.getMaxRows() > 0) {
         query.setMaxRows(src.getMaxRows());
      }
      // Endpoints only. A local file is read whole in one pass — there are no pages to walk and no
      // per-call bill — so demanding a row cap there would refuse a correct request for a cost that
      // does not exist.
      else if(TARGET_KIND_ENDPOINT.equals(targetKind)) {
         TabularEndpointBindingSupport.requireRowCapWhenPaged(query, src.getTarget(), dsName);
      }

      // How many rows to report back, carried ON THE QUERY because the runner is the only place that
      // sees a response and it is the only thing that can sample one. Zero when the caller did not
      // ask, which is the default: sampling is opt-in, so a table built without this field runs
      // exactly the request it ran before and reports exactly the columns.
      query.setSampleRowLimit(src.getSampleRows() == null ? 0 : Math.max(0, src.getSampleRows()));

      TabularTableAssembly table = new TabularTableAssembly(worksheet, request.getTableName());
      TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);

      // Prefix and source are both the data source path, which is what the composer dialog writes
      // too. The endpoint is NOT in the SourceInfo — it lives inside the query bean — so nothing
      // reading only SourceInfo (notably MetadataApiService.extractStructureSource) can say which
      // endpoint a tabular table came from.
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, dsName, dsName));

      // A fresh VariableTable and a null QueryManager, matching what
      // TabularTableAssembly.dependencyChanged passes and what buildSqlTable uses for its own column
      // resolution. Taking an AssetQuerySandbox instead would mean either disposing it — leaving the
      // "queryManager" property pointing at a dead object on a query that outlives it — or leaking
      // it. The cost is that this one column-discovery request cannot be cancelled, and that
      // user-entered query variables are unavailable; the parameter values here are literals,
      // already substituted into the suffix above.
      table.loadColumnSelection(new VariableTable(), true, null);

      // loadColumnSelection reports a failed request with LOG.warn and returns normally. Without
      // this check the assembly is persisted with zero columns and /ws/table answers success=true
      // with an empty column list — an error that first becomes visible when a chart is bound to
      // the table and renders nothing.
      ColumnSelection columns = table.getColumnSelection(false);

      if(columns == null || columns.getAttributeCount() == 0) {
         // Worded per kind. The endpoint text names the URL suffix, which is the whole of what was
         // sent and the first thing to check; repeating it for a file would hand whoever is
         // debugging a URL that was never built and say nothing about the path, the sheet, or the
         // delimiter — the three things that actually produce an empty parse.
         throw new IllegalArgumentException(TARGET_KIND_FILE.equals(targetKind)
            ? "Reading " + probeDesc + " of '" + dsName + "' produced no columns. Check that the " +
              "file exists at that path, is not empty, and that the parsing options in " +
              "tabularSource.params match it (sheet name, delimiter, encoding, first row as " +
              "header); the underlying cause is in the server log for this request."
            : "The request to endpoint '" + src.getTarget() + "' of '" + dsName +
              "' returned no columns. URL suffix sent: " + probeDesc +
              ". Check the parameter values and that the data source's credentials are valid; the " +
              "underlying cause is in the server log for this request.");
      }

      worksheet.addAssembly(table);
      return table;
   }

   /**
    * Set the endpoint and its parameter values on a tabular query, and return the URL suffix that
    * results. Also applies {@code src}'s lookup chain, if any, once the base endpoint is set.
    *
    * <p>Delegates the connector-agnostic reflection machinery to
    * {@link TabularEndpointBindingSupport#applyEndpointContract}, which
    * {@code WorksheetAgentController.addTabularTable} (the composer plugin's write path) shares —
    * this method's own job is just translating {@code src} into that shared contract's plain
    * parameters, plus the one check ({@code params} is the FILE contract's option bag, not this
    * one) that has no equivalent on the plugin path, which has no file target at all.</p>
    *
    * @return the built URL suffix, with every parameter substituted.
    */
   private String applyEndpointContract(TabularQuery query,
                                        Map<String, PropertyMeta> pmap,
                                        WorksheetTable.TabularSource src)
      throws Exception
   {
      // Refused rather than ignored, same rule as an unknown parameter name below: params is the
      // file contract's option bag, nothing on this path reads it, and dropping it would parse the
      // caller's request differently from how they wrote it and report success.
      if(src.getParams() != null && !src.getParams().isEmpty()) {
         throw new IllegalArgumentException(
            "tabularSource.params applies to targetKind \"file\" only — an endpoint's values go in " +
            "tabularSource.parameters, keyed by the endpoint's own parameter names.");
      }

      String endpoint = src.getTarget().trim();
      String suffix = TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, endpoint, src.getParameters(), src.getJsonPath(), src.getExpanded(),
         src.getExpandedPath(), src.getDatasourcePath());

      if(src.getLookup() != null && !src.getLookup().isEmpty()) {
         TabularEndpointBindingSupport.applyLookupChain(query, pmap, src.getLookup(),
            src.getLookupExpandArrays(), src.getLookupTopLevelOnly(), endpoint,
            src.getDatasourcePath());
      }

      return suffix;
   }

   /**
    * The {@code targetKind} a tabular source asks for, normalized and checked.
    *
    * <p>Absent means {@code "endpoint"}, because before {@code target} existed this object could
    * express nothing else — a caller that does not mention a kind is a caller written against that
    * shape, and answering anything else for it would change the meaning of a request already in
    * flight. Case and surrounding space are forgiven; an unrecognized value is refused by name
    * rather than falling through to a default, which would build a table against a contract nobody
    * asked for and report success.</p>
    */
   private static String targetKindOf(WorksheetTable.TabularSource src) {
      String kind = src.getTargetKind();

      if(kind == null || kind.isBlank()) {
         return TARGET_KIND_ENDPOINT;
      }

      String normalized = kind.trim().toLowerCase();

      if(!TARGET_KIND_ENDPOINT.equals(normalized) && !TARGET_KIND_FILE.equals(normalized)) {
         throw new IllegalArgumentException(
            "tabularSource.targetKind must be \"" + TARGET_KIND_ENDPOINT + "\" or \"" +
            TARGET_KIND_FILE + "\", got \"" + kind + "\".");
      }

      return normalized;
   }

   /**
    * Point a path-addressed tabular query at one file, apply its parsing options, and return a
    * description of what it will read.
    *
    * <p>The counterpart of {@link #applyEndpointContract} — same job, different contract. What the
    * two share is the mechanism underneath ({@link TabularUtil#getPropertyMap} plus
    * {@link PropertyMeta}); what differs is which properties carry the target, which values are
    * legal, and what a failure has to say. Nothing about a URL, a parameter template or pagination
    * applies here, and nothing about a sheet name or a delimiter applies there.</p>
    *
    * <p>NOTHING HERE TRUSTS THAT A WRITE HAPPENED, for the same reason the endpoint contract does
    * not: {@code PropertyMeta.setValue} reports a failed invocation with {@code LOG.error} and
    * returns, so a mistyped option would silently leave the connector's default in place and parse
    * the file differently from what was asked. The write methods are therefore invoked directly, so
    * a reflection failure throws, and the file itself is read back afterwards.</p>
    *
    * @return a human description of the resolved target, e.g. {@code file '2024/q1.csv'} or
    *         {@code file '2024/sales.xlsx' sheet 'Q1'}.
    */
   private String applyFileContract(TabularQuery query,
                                    Map<String, PropertyMeta> pmap,
                                    WorksheetTable.TabularSource src)
      throws Exception
   {
      String dsName = src.getDatasourcePath();

      // Refused rather than ignored, the mirror of the params rejection on the endpoint side. These
      // four describe the shape of a JSON response; a file has no response and no row path, so
      // accepting them would answer a request that cannot be honored.
      if(src.getParameters() != null && !src.getParameters().isEmpty()) {
         throw new IllegalArgumentException(
            "tabularSource.parameters applies to targetKind \"" + TARGET_KIND_ENDPOINT + "\" only " +
            "— a file's parsing options go in tabularSource.params.");
      }

      if(src.getJsonPath() != null || src.getExpanded() != null || src.getExpandedPath() != null) {
         throw new IllegalArgumentException(
            "tabularSource.jsonPath/expanded/expandedPath apply to targetKind \"" +
            TARGET_KIND_ENDPOINT + "\" only — they describe a JSON response, and a file has none.");
      }

      PropertyMeta fileProp = fileTargetProperty(pmap, query, dsName);

      // "path" or "path#sheet". Split on the LAST '#' so a file whose own name contains one still
      // resolves; the sheet suffix is the same identity the annotation stores for the table, so
      // accepting it here is what keeps the stored name and the bindable name the same string.
      String rawTarget = src.getTarget().trim();
      int hash = rawTarget.lastIndexOf('#');
      String relativePath = hash < 0 ? rawTarget : rawTarget.substring(0, hash).trim();
      String sheetFromTarget = hash < 0 ? null : rawTarget.substring(hash + 1).trim();

      if(relativePath.isEmpty()) {
         throw new IllegalArgumentException(
            "tabularSource.target names no file: \"" + rawTarget + "\".");
      }

      File file = resolveTargetFile(query, relativePath, dsName);
      invokeWriteMethod(fileProp, query, file);

      // Read back, because the getter rebuilds the path from what the setter actually stored (the
      // setter relativizes against the data source's root folder, so a failed or misresolved write
      // shows up here and nowhere else). Everything below — the Excel test, the sheet list, the
      // column read — derives from the file being set.
      Object readBack = fileProp.getValue(query);

      if(!(readBack instanceof File actual) ||
         !actual.getCanonicalPath().equals(file.getCanonicalPath()))
      {
         throw new IllegalStateException(
            "Failed to point '" + dsName + "' at file '" + relativePath + "' (resolved to " +
            file.getPath() + ", read back as " + readBack + "); see the server log for the " +
            "reflection failure.");
      }

      // Applied BEFORE the sheet is resolved: excelSheet is itself one of these, and the resolution
      // below has to see whichever value the caller supplied.
      applyFileParams(query, pmap, src, fileProp.getName(), dsName);

      String sheet = resolveExcelSheet(query, pmap, src, sheetFromTarget, relativePath, dsName);

      return "file '" + relativePath + "'" + (sheet == null ? "" : " sheet '" + sheet + "'");
   }

   /**
    * The bean property that names the file, and the check that this connector has one at all.
    *
    * <p>Resolved by TYPE rather than hard-coded to ServerFile's {@code fileFolder}: a file target is
    * a {@code java.io.File} property, which is exactly what {@code TabularUtil.getEditorType} keys
    * its {@code FILE} editor on, so the next path-addressed connector needs no change here. The
    * name is still preferred when present, so a connector carrying two File properties resolves the
    * same way the composer dialog does rather than by declaration order.</p>
    */
   private PropertyMeta fileTargetProperty(Map<String, PropertyMeta> pmap, TabularQuery query,
                                           String dsName)
   {
      List<PropertyMeta> fileProps = new ArrayList<>();

      for(PropertyMeta prop : pmap.values()) {
         if(isFileProperty(prop)) {
            fileProps.add(prop);
         }
      }

      if(fileProps.isEmpty()) {
         throw new IllegalArgumentException(
            "Data source '" + dsName + "' (type '" + query.getType() + "') is tabular but not " +
            "file-based, so it has no file to select. Use targetKind \"" + TARGET_KIND_ENDPOINT +
            "\" for a connector that ships an endpoint catalogue.");
      }

      for(PropertyMeta prop : fileProps) {
         if(FILE_TARGET_PROPERTY.equals(prop.getName())) {
            return prop;
         }
      }

      if(fileProps.size() > 1) {
         throw new IllegalStateException(
            "Data source '" + dsName + "' (type '" + query.getType() + "') declares " +
            fileProps.size() + " file properties and none named \"" + FILE_TARGET_PROPERTY +
            "\", so which one tabularSource.target means is ambiguous.");
      }

      return fileProps.get(0);
   }

   private static boolean isFileProperty(PropertyMeta prop) {
      Method setter = prop.getDescriptor().getWriteMethod();

      return setter != null && setter.getParameterCount() == 1 &&
         File.class.isAssignableFrom(setter.getParameterTypes()[0]);
   }

   /**
    * Resolve {@code target} against the connector's root folder, refusing anything that leaves it.
    *
    * <p>The root folder IS the grant — a {@code ServerFileDataSource} authorizes one directory and
    * nothing above it — so an absolute path or a {@code ".."} segment is not a path this method can
    * resolve leniently; it is a request to read outside what the data source gives access to. Both
    * are refused by shape, and the resolved path is then checked against the root canonically as
    * well, because a symlink inside the root satisfies the shape check and still points out.</p>
    *
    * <p>{@code getRootFolder()} is reached by name for the same reason {@code getEndpoints} is: the
    * class declaring it lives in the connector plugin and is not visible from core. A connector that
    * does not answer it is left to resolve the path itself rather than blocked.</p>
    */
   private File resolveTargetFile(TabularQuery query, String relativePath, String dsName)
      throws Exception
   {
      String normalized = relativePath.replace('\\', '/');

      if(new File(normalized).isAbsolute() || normalized.startsWith("/") ||
         normalized.matches("^[A-Za-z]:.*"))
      {
         throw new IllegalArgumentException(
            "tabularSource.target must be relative to the data source's root folder, not an " +
            "absolute path: '" + relativePath + "'.");
      }

      for(String segment : normalized.split("/")) {
         if("..".equals(segment)) {
            throw new IllegalArgumentException(
               "tabularSource.target must not contain '..': '" + relativePath + "'. The data " +
               "source's root folder is the whole of what it grants access to.");
         }
      }

      String root = (String) callQueryMethod(query, "getRootFolder", dsName);
      File file = root == null || root.isBlank()
         ? new File(normalized) : new File(root, normalized);

      if(root != null && !root.isBlank()) {
         String rootPath = new File(root).getCanonicalPath();
         String filePath = file.getCanonicalPath();

         if(!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
            throw new IllegalArgumentException(
               "tabularSource.target resolves outside the data source's root folder: '" +
               relativePath + "'.");
         }
      }

      // Answered here rather than left to surface as "produced no columns", which is the same
      // message an empty file and a bad delimiter produce and says nothing about which of the three
      // it was.
      if(!file.exists()) {
         throw new IllegalArgumentException(
            "Data source '" + dsName + "' has no file at '" + relativePath + "'. Browse the data " +
            "source to see what it holds; the path is relative to its root folder.");
      }

      return file;
   }

   /**
    * Write the caller's parsing options onto the query, refusing any the connector does not declare.
    *
    * <p>Validated against {@code pmap} rather than a fixed list of ServerFile's option names, so the
    * next path-addressed connector's options work without a change here and its caller still gets
    * told what it does accept. Unknown names are an error rather than something to drop, for the
    * same reason an unknown endpoint parameter is: a dropped option parses the file DIFFERENTLY —
    * a wrong delimiter yields one column, a wrong sheet yields another table's data — and reports
    * success either way.</p>
    */
   private void applyFileParams(TabularQuery query, Map<String, PropertyMeta> pmap,
                                WorksheetTable.TabularSource src, String targetPropertyName,
                                String dsName)
      throws Exception
   {
      Map<String, String> params = src.getParams();

      if(params == null || params.isEmpty()) {
         return;
      }

      for(Map.Entry<String, String> entry : params.entrySet()) {
         String name = entry.getKey() == null ? null : entry.getKey().trim();
         PropertyMeta prop = name == null ? null : pmap.get(name);

         if(prop == null || isFileProperty(prop) || "columns".equals(name)) {
            throw new IllegalArgumentException(
               "Data source '" + dsName + "' has no parsing option named '" + entry.getKey() +
               "'. Its options are: " + optionNames(pmap, targetPropertyName) +
               ". The file itself goes in tabularSource.target, not here.");
         }

         invokeWriteMethod(prop, query, coerceParam(prop, name, entry.getValue()));
      }
   }

   /** The option names a file-based connector accepts, target and column list excluded. */
   private static String optionNames(Map<String, PropertyMeta> pmap, String targetPropertyName) {
      return pmap.values().stream()
         .map(PropertyMeta::getName)
         .filter(name -> !name.equals(targetPropertyName) && !"columns".equals(name))
         .sorted()
         .collect(Collectors.joining(", "));
   }

   /**
    * Convert one option's text to the type its setter takes.
    *
    * <p>Every value arrives as a string because {@code params} is a flat string map — the shape a
    * caller can actually produce without knowing the connector's Java types. A value the setter's
    * type cannot hold is refused with both the option name and what it expected, rather than
    * silently becoming {@code 0} or {@code false}, which is what an unchecked conversion would
    * write for "one" or "yes".</p>
    */
   private static Object coerceParam(PropertyMeta prop, String name, String raw) {
      Class<?> type = prop.getDescriptor().getWriteMethod().getParameterTypes()[0];

      if(raw == null) {
         if(type.isPrimitive()) {
            throw new IllegalArgumentException(
               "Parsing option '" + name + "' has no value, and it cannot be cleared: it is a " +
               type.getSimpleName() + ".");
         }

         return null;
      }

      String value = raw.trim();

      try {
         if(type == String.class) {
            // Not trimmed: a delimiter of " " is a legitimate value, and it is the only option
            // whose whole meaning can be whitespace.
            return raw;
         }
         else if(type == boolean.class || type == Boolean.class) {
            if(!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
               throw new NumberFormatException(value);
            }

            return Boolean.valueOf(value);
         }
         else if(type == int.class || type == Integer.class) {
            return Integer.valueOf(value);
         }
         else if(type == long.class || type == Long.class) {
            return Long.valueOf(value);
         }
         else if(type == short.class || type == Short.class) {
            return Short.valueOf(value);
         }
         else if(type == double.class || type == Double.class) {
            return Double.valueOf(value);
         }
         else if(type == float.class || type == Float.class) {
            return Float.valueOf(value);
         }
         else if(type == char.class || type == Character.class) {
            return value.isEmpty() ? ' ' : value.charAt(0);
         }
         else if(type.isEnum()) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object constant = Enum.valueOf((Class<Enum>) type, value);

            return constant;
         }
      }
      catch(IllegalArgumentException ex) {
         throw new IllegalArgumentException(
            "Parsing option '" + name + "' expects a " + type.getSimpleName() + ", got \"" + raw +
            "\".");
      }

      throw new IllegalArgumentException(
         "Parsing option '" + name + "' is a " + type.getSimpleName() +
         ", which cannot be supplied as text in tabularSource.params.");
   }

   /**
    * Settle which sheet of a workbook to read, and refuse to guess when the answer matters.
    *
    * <p>ServerFile's own default is the first sheet ({@code ServerFileUtil.getColumnDefinition}
    * falls back to {@code getExcelSheetNames()[0]}), which is deterministic but silent — a
    * three-sheet workbook builds a table from sheet one and reports success, and nothing in the
    * result says the other two exist. That is the wrong answer for an annotation pass, whose whole
    * job is to enumerate what can be bound. So an unqualified multi-sheet workbook FAILS, carrying
    * the sheet names, which is the same "fail with what was missing, refill, retry" shape an
    * endpoint's missing required parameter has.</p>
    *
    * <p>A single-sheet workbook is not ambiguous and is left to the connector's default: there is
    * nothing for the caller to choose, and demanding a choice would refuse a correct request.</p>
    *
    * <p>{@code isExcel} and {@code getExcelSheetNames} are reached by name — the connector plugin is
    * not visible from core. A connector answering neither is left alone rather than blocked; this
    * exists to stop a silent wrong sheet, not to reject an unfamiliar connector.</p>
    *
    * @return the sheet that will be read, or {@code null} when the target is not a workbook.
    */
   private String resolveExcelSheet(TabularQuery query, Map<String, PropertyMeta> pmap,
                                    WorksheetTable.TabularSource src, String sheetFromTarget,
                                    String relativePath, String dsName)
      throws Exception
   {
      PropertyMeta sheetProp = pmap.get(EXCEL_SHEET_PROPERTY);
      Object excel = callQueryMethod(query, "isExcel", dsName);

      if(sheetProp == null || !(excel instanceof Boolean isExcel)) {
         if(sheetFromTarget != null) {
            throw new IllegalArgumentException(
               "Data source '" + dsName + "' has no sheet selection, so the \"#" + sheetFromTarget +
               "\" suffix on tabularSource.target cannot be honored.");
         }

         return null;
      }

      // The '#' suffix and params.excelSheet name the same thing. Both is fine when they agree —
      // a caller that echoes the stored identity into both is not making a mistake — and refused
      // when they do not, because one of the two would have to be discarded silently.
      String supplied = sheetFromTarget;
      String fromParams = src.getParams() == null ? null : src.getParams().get(EXCEL_SHEET_PROPERTY);

      if(fromParams != null && !fromParams.isBlank()) {
         if(supplied != null && !supplied.isEmpty() && !supplied.equals(fromParams.trim())) {
            throw new IllegalArgumentException(
               "tabularSource.target names sheet '" + supplied + "' and tabularSource.params names " +
               "sheet '" + fromParams.trim() + "'. Supply one of them.");
         }

         supplied = fromParams.trim();
      }

      if(!isExcel) {
         if(supplied != null && !supplied.isEmpty()) {
            throw new IllegalArgumentException(
               "'" + relativePath + "' of '" + dsName + "' is not a workbook, so it has no sheet " +
               "'" + supplied + "' to select.");
         }

         return null;
      }

      // Written through the property rather than left in params, so a sheet that arrived on the
      // target suffix reaches the query the same way one supplied in params does.
      if(supplied != null && !supplied.isEmpty()) {
         invokeWriteMethod(sheetProp, query, supplied);
      }

      List<String> sheets = excelSheetNames(query, dsName);

      if(sheets.isEmpty()) {
         // The connector could not list them (an unreadable or malformed workbook). Not fatal here:
         // the column read below fails with the real reason, and inventing one now would hide it.
         return supplied;
      }

      if(supplied == null || supplied.isEmpty()) {
         if(sheets.size() > 1) {
            throw new IllegalArgumentException(
               "'" + relativePath + "' of '" + dsName + "' has " + sheets.size() + " sheets, so " +
               "one has to be named: " + String.join(", ", sheets) + ". Put it in " +
               "tabularSource.params.excelSheet, or suffix tabularSource.target with \"#<sheet>\".");
         }

         // getExcelSheetNames() sets the query's sheet to the only one as a side effect, so the
         // query is already pointed at it; reported back so the caller sees which one it was.
         return sheets.get(0);
      }

      if(!sheets.contains(supplied)) {
         throw new IllegalArgumentException(
            "'" + relativePath + "' of '" + dsName + "' has no sheet named '" + supplied +
            "'. Its sheets are: " + String.join(", ", sheets) + ".");
      }

      return supplied;
   }

   /** The workbook's sheet names, blanks dropped — the connector answers {@code [""]} for a miss. */
   private List<String> excelSheetNames(TabularQuery query, String dsName) {
      Object names = callQueryMethod(query, "getExcelSheetNames", dsName);
      List<String> sheets = new ArrayList<>();

      if(names instanceof String[] array) {
         for(String name : array) {
            if(name != null && !name.isBlank()) {
               sheets.add(name);
            }
         }
      }

      return sheets;
   }

   /**
    * Invoke a connector method core cannot see the declaring type of.
    *
    * <p>Same reflection {@link #assertKnownEndpoint} and {@link #requireRowCapWhenPaged} use, and
    * the same stance on failure: a connector that does not answer is left alone rather than blocked,
    * because these calls sharpen an error message or a default — none of them is the check that
    * makes the build correct.</p>
    */
   private static Object callQueryMethod(TabularQuery query, String method, String dsName) {
      try {
         return query.getClass().getMethod(method).invoke(query);
      }
      catch(Exception ex) {
         LOG.debug("Could not call {}() on the query for '{}'", method, dsName, ex);
         return null;
      }
   }

   /**
    * Write a property through its setter directly, so a failed write throws.
    *
    * <p>{@code PropertyMeta.setValue} swallows the invocation failure with a {@code LOG.error},
    * which on this path would leave the connector's default in place and parse the file differently
    * from what was asked, reporting success. The endpoint contract answers that by reading the URL
    * suffix back at the end; a file contract has no equivalent single readable summary, so the
    * writes themselves are made loud instead.</p>
    */
   private static void invokeWriteMethod(PropertyMeta prop, Object bean, Object value)
      throws Exception
   {
      try {
         prop.getDescriptor().getWriteMethod().invoke(bean, value);
      }
      catch(InvocationTargetException ex) {
         Throwable cause = ex.getCause() == null ? ex : ex.getCause();

         throw new IllegalArgumentException(
            "Setting '" + prop.getName() + "' to \"" + value + "\" failed: " +
            (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()),
            cause);
      }
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

   /**
    * The source table's metadata (column names + types), as {@link #buildColumnSelectionFromMeta}
    * consumes it. A failure propagates: building a physical table on column names that were never
    * reconciled against the source is exactly the silent-phantom-column failure
    * {@link #resolveRequestedColumns} exists to prevent.
    */
   private OsiDataset fetchSourceMetaData(WorksheetTable.PhysicalSource src, Principal user)
      throws Exception
   {
      GetDatabaseTableMetaRequest metaReq = new GetDatabaseTableMetaRequest();
      metaReq.setDsName(src.getDatasourcePath());
      metaReq.setCatalog(src.getCatalog());
      metaReq.setSchema(src.getSchema());
      metaReq.setTableName(src.getTableName());
      return metadataApiService.getMetaData(metaReq, user);
   }

   /** The source table's real column names, in metadata order. */
   private static List<String> sourceColumnNames(OsiDataset metaData) {
      if(metaData == null || metaData.getFields() == null) {
         return Collections.emptyList();
      }

      List<String> names = new ArrayList<>();

      for(OsiField field : metaData.getFields()) {
         if(field != null && !Tool.isEmptyString(field.getName())) {
            names.add(field.getName());
         }
      }

      return names;
   }

   /**
    * The names (and aliases) of the columns this same request DERIVES rather than reads from the
    * source — {@code expressionColumns} and {@code windowColumns}. A request that also lists a
    * derived column in {@code columns} is naming something the source's metadata cannot know about,
    * so {@link #resolveRequestedColumns} neither rejects it nor keeps it: the entry is dropped and
    * the real derived column is contributed by {@link #applyExpressionColumns} /
    * {@link #applyWindowColumns}.
    */
   private static Set<String> derivedColumnNames(WorksheetTable request) {
      Set<String> names = new HashSet<>();

      if(request.getExpressionColumns() != null) {
         for(WorksheetTable.ExpressionColumnInfo col : request.getExpressionColumns()) {
            if(col == null) {
               continue;
            }

            if(!Tool.isEmptyString(col.getName())) {
               names.add(col.getName());
            }

            if(!Tool.isEmptyString(col.getAlias())) {
               names.add(col.getAlias());
            }
         }
      }

      if(request.getWindowColumns() != null) {
         for(WorksheetTable.WindowColumnInfo col : request.getWindowColumns()) {
            if(col != null && !Tool.isEmptyString(col.getName())) {
               names.add(col.getName());
            }
         }
      }

      return names;
   }

   /**
    * Reconcile a caller-declared {@code columns} list against the source table's real column names,
    * canonicalizing each name to the source's exact spelling.
    *
    * <p>Forgiving where the intent is unambiguous: a case-only difference, or a name qualified with
    * a table prefix ({@code ORDERS.ORDER_AMOUNT}) whose remainder is a real column, resolves to the
    * source's spelling. Fail loud otherwise — and the error names both the unresolvable columns and
    * the ones the table does have, so a caller (LLM or human) can correct itself in one step.
    *
    * <p>Why this must fail rather than pass through: {@link #buildColumnSelection} turns any string
    * into an {@link AttributeRef}, so a column the source does not have used to enter the assembly's
    * stored column selection unchallenged, and every read-back this API serves ({@code /ws/table}'s
    * response, {@code /ws/structure}) then advertised the phantom column as real. Nothing failed
    * later either: at query time {@code PreAssetQuery.validateColumnSelection} silently REMOVES
    * columns the source does not have, so the generated SQL is valid and even the execution probe
    * passes. The caller binds a chart to a column no query can ever produce and gets an empty chart
    * with no error, and the column disappears from the worksheet as soon as Composer refreshes it.
    *
    * @param requested       the caller's column list; resolvable names are canonicalized in place
    * @param sourceColumns   the source table's real column names; empty means "metadata unavailable",
    *                        which skips reconciliation rather than failing the create
    * @param derivedColumns  columns this request derives itself (see {@link #derivedColumnNames})
    * @return the source-backed subset of {@code requested}, each name canonicalized to the source's
    *         spelling — a name only this request derives is DROPPED, not passed through
    * @throws IllegalArgumentException if any name cannot be resolved to a source column
    */
   // Package-private for unit testing (WorksheetTableServiceColumnValidationTest).
   List<WorksheetTable.ColumnInfo> resolveRequestedColumns(
      List<WorksheetTable.ColumnInfo> requested, List<String> sourceColumns,
      Set<String> derivedColumns)
   {
      if(sourceColumns == null || sourceColumns.isEmpty()) {
         LOG.warn("No source metadata to reconcile the requested columns against; " +
                  "accepting them as declared.");
         return requested;
      }

      // Upper-cased name → the source's exact spelling. First spelling wins, so a source that
      // reports two columns differing only in case keeps the one it listed first. Locale.ROOT
      // because these are datasource identifiers, not display text — a Turkish default locale
      // would otherwise fold "i" to "İ" and break the match for any column containing an i.
      Map<String, String> canonical = new HashMap<>();

      for(String name : sourceColumns) {
         canonical.putIfAbsent(name.toUpperCase(Locale.ROOT), name);
      }

      // Case-insensitive: the caller can spell a derived column differently in `columns` than in
      // `expressionColumns`, and reporting that as "not found in source" would misdirect.
      Set<String> derived = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

      if(derivedColumns != null) {
         derived.addAll(derivedColumns);
      }

      List<WorksheetTable.ColumnInfo> resolved = new ArrayList<>(requested.size());
      List<String> unresolved = new ArrayList<>();

      for(WorksheetTable.ColumnInfo col : requested) {
         String name = col == null ? null : col.getName();

         if(name == null || name.isBlank()) {
            unresolved.add("<empty>");
            continue;
         }

         String match = canonical.get(name.toUpperCase(Locale.ROOT));
         int dot = name.lastIndexOf('.');

         // "ORDERS.ORDER_AMOUNT" — a table-qualified name. Unqualify ONLY when the exact name is
         // not itself a column, so a source column whose own name contains a dot still wins.
         if(match == null && dot > 0 && dot < name.length() - 1) {
            match = canonical.get(name.substring(dot + 1).toUpperCase(Locale.ROOT));
         }

         if(match == null) {
            // Derived by this same request — the source's metadata cannot know it, so it is not an
            // unresolvable name. It is DROPPED here rather than passed through: the derived column
            // is contributed by applyExpressionColumns / applyWindowColumns, and a plain
            // AttributeRef of the same name reaching the selection first would SHADOW it —
            // ColumnSelection.addAttribute is exclusive and ColumnRef equality is by name alone
            // (AbstractDataRef.equals), so the real expression would be silently discarded and the
            // phantom attribute then dropped from the query by validateColumnSelection.
            //
            // Only a derived name the source does NOT have takes this branch, so a derived column
            // that shadows a real source column keeps resolving to the source column, exactly as
            // before.
            if(derived.contains(name)) {
               continue;
            }

            unresolved.add(name);
            continue;
         }

         if(!match.equals(name)) {
            col.setName(match);
         }

         resolved.add(col);
      }

      if(!unresolved.isEmpty()) {
         throw new IllegalArgumentException(
            "Column(s) not found in source table: " + String.join(", ", unresolved) +
            ". Available columns: " + String.join(", ", sourceColumns) +
            ". Use the exact column names the datasource reports; a value the source does not " +
            "store must be derived in expressionColumns (on a mirror of this table), not listed " +
            "in columns.");
      }

      return resolved;
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

            // RANGE value-offset and GROUPS need an ORDER BY; RANGE value-offset needs exactly
            // one. Unlike the general bounded-frame check above, GROUPS has NO whole-partition
            // exemption: Postgres (and ANSI) require an ORDER BY for GROUPS mode regardless of
            // bounds, since GROUPS defines its peer groups purely from the order-by key — with no
            // orderBy there is no notion of a "group" to count PRECEDING/FOLLOWING, even when the
            // frame spans the whole partition. The wiz TS validator rejects ANY GROUPS frame
            // without orderBy for the same reason; this mirrors it.
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

            // offsetUnit: only meaningful when the frame has a real value-offset bound (a
            // PRECEDING/FOLLOWING with an offset) — gate on valueOffset BEFORE consulting
            // orderRefs, since a whole-partition RANGE/GROUPS frame legitimately carries no
            // orderBy at all (requireDateOrderKey's orderRefs.get(0) would otherwise throw
            // IndexOutOfBoundsException instead of a field-named IllegalArgumentException).
            String unit = frame.getOffsetUnit();

            if(unit != null) {
               if(!"RANGE".equals(mode)) {
                  throw new IllegalArgumentException(
                     "windowColumns['" + colName +
                     "']: frame.offsetUnit is only valid for a RANGE frame");
               }

               if(!valueOffset) {
                  throw new IllegalArgumentException(
                     "windowColumns['" + colName +
                     "']: frame.offsetUnit requires a PRECEDING/FOLLOWING value-offset bound");
               }

               String u = unit.toLowerCase();

               if(!VALID_OFFSET_UNITS.contains(u)) {
                  throw new IllegalArgumentException(
                     "windowColumns['" + colName + "']: invalid frame.offsetUnit: " + unit);
               }

               // Safe: a value-offset RANGE frame already requires exactly one orderBy column
               // (guard above), so orderRefs is never empty here.
               requireDateOrderKey(colName, orderRefs);
               unit = u;
            }
            else if("RANGE".equals(mode) && valueOffset && orderRefs.size() == 1) {
               // Converse of requireDateOrderKey above: a RANGE value-offset (PRECEDING/
               // FOLLOWING n) whose single ORDER BY key is date/time-typed but carries NO
               // offsetUnit is meaningless in SQL — a bare numeric offset against a date/
               // timestamp column needs an INTERVAL, not a raw number. Left unguarded, the
               // in-memory path silently treats the offset as raw milliseconds (dateOffsetMs's
               // unit==null branch) and a pushdown would emit an invalid date RANGE. Fail loud
               // here (the authoritative gateway) rather than letting either path mis-render.
               DataRef oref = orderRefs.get(0).getDataRef();
               String dt = oref == null ? null : oref.getDataType();

               if(XSchema.isDateType(dt)) {
                  throw new IllegalArgumentException(
                     "windowColumns['" + colName + "']: a RANGE value-offset on a date/time " +
                     "order key requires frame.offsetUnit (e.g. day/month)");
               }
               // Converse case: with no offsetUnit, a RANGE value-offset is a bare numeric
               // threshold applied against the single order key. A non-numeric, non-date order
               // key (e.g. string/boolean) makes that threshold meaningless — fail loud instead
               // of emitting a nonsensical "RANGE BETWEEN n PRECEDING" against e.g. a string
               // column. Deferred (not thrown) when dt is unresolvable (null), matching the
               // best-effort pattern used throughout this method.
               else if(dt != null && !XSchema.isNumericType(dt)) {
                  throw new IllegalArgumentException(
                     "windowColumns['" + colName + "']: a numeric RANGE value-offset requires " +
                     "a numeric order key");
               }
            }

            winRef.setFrame(mode, frame.getStartBound(), startOffset, frame.getEndBound(),
                             endOffset, unit);
         }

         ColumnRef colRef = new ColumnRef(winRef);
         colRef.setSQL(true);

         if(!Tool.isEmptyString(col.getType())) {
            colRef.setDataType(col.getType());
         }

         // Business meaning of the window output column — surfaced by /ws/structure (data insight).
         if(!Tool.isEmptyString(col.getDescription())) {
            colRef.setDescription(col.getDescription());
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

   // Package-private (not private) so WorksheetTableServiceAggregateDescriptionTest can invoke it
   // directly without standing up the full createTable dependency graph, mirroring applyWindowColumns.
   void applyAggregateInfo(AbstractTableAssembly table,
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

            // Business meaning of the group-by output column. The public output column is a CLONE
            // of the matching PRIVATE ColumnRef (see AbstractTableAssembly.setColumnSelection), so
            // set the description on the private target for it to survive to /ws/structure. For a
            // date-grouped column that target IS the DateRangeRef column just added to privateCs
            // (referenced by `column`); otherwise it is the private column of the same name.
            //
            // PREFER an inherited annotation over the model's group description: a plain group-by
            // dimension is a passthrough of an existing (often annotated) column — grouping does not
            // change its business meaning — so keep the base column's description rather than let the
            // model overwrite it with a lineage-restating one (e.g. "Company name from the joined ...
            // data"). Only fill from the model when the target has no description yet: a freshly
            // built date-grouped column (DateRangeRef carries none), or a group key with no upstream
            // annotation. Aggregate MEASURES are handled separately below and DO take the model's
            // description, since aggregation genuinely changes the column's meaning.
            // Output-column alias, mirroring the aggregate branch below (which sets it on the PRIVATE
            // ColumnRef, because the public selection is regenerated as clones of privateCs). Resolved
            // to the same private target the description uses just below: the DateRangeRef column for a
            // date-grouped field, otherwise the private column of the same name.
            //
            // Without this a dateGroupLevel group's output name is DateRangeRef's RENDERED expression
            // ("Month(T.due_date)"), which is not a SQL alias — so it could not be referenced from a
            // downstream sql:true expression under any form, and the canonical
            // COALESCE(left_key, right_key) over a FULL join was unexpressible in pushed-down SQL.
            if(!Tool.isEmptyString(grp.getAlias())) {
               ColumnRef aliasTarget = grp.getDateGroupLevel() != null
                  ? column
                  : (privateCs.getAttribute(grp.getFieldName()) instanceof ColumnRef pc ? pc : null);

               if(aliasTarget != null) {
                  aliasTarget.setAlias(grp.getAlias());
               }
            }

            if(!Tool.isEmptyString(grp.getDescription())) {
               // Best-effort: unlike the aggregate branch below (which falls back to the public
               // `column` when privateCs lookup misses), a plain group whose fieldName does not
               // resolve in privateCs leaves descTarget null and the model's group description is
               // dropped. Setting it on the public `column` instead would not help — the public
               // selection is regenerated as clones of privateCs, so a description must live on the
               // private target to survive. A resolution miss here is unexpected (the group was
               // already resolved against `cs` above) and rare; dropping the description is the
               // accepted best-effort behavior, consistent with the rest of this method.
               ColumnRef descTarget = grp.getDateGroupLevel() != null
                  ? column
                  : (privateCs.getAttribute(grp.getFieldName()) instanceof ColumnRef pc ? pc : null);

               if(descTarget != null && Tool.isEmptyString(descTarget.getDescription())) {
                  descTarget.setDescription(grp.getDescription());
               }
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

            // Business meaning of the aggregated output column. aggColumn is the PRIVATE ColumnRef
            // (the aliased base column for the first aggregate, or the synthetic column for 2nd+),
            // which setColumnSelection clones into the public output column — so a description set
            // here survives to /ws/structure (data insight).
            if(!Tool.isEmptyString(agg.getDescription()) && aggColumn instanceof ColumnRef aggColRef) {
               aggColRef.setDescription(agg.getDescription());
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
         // isAggregate() requires BOTH a non-empty AggregateInfo AND this separate flag
         // (AbstractTableAssembly.isAggregate() checks getTableInfo().isAggregate()).
         // setAggregateInfo() alone does not set it, so without this the table is left in
         // "has aggregates, but isAggregate()==false" — an inconsistent state that survives
         // fine standalone but gets normalized away (silently dropping the AggregateInfo)
         // once the table is later resolved/merged into a dashboard viewsheet binding.
         table.setAggregate(true);
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
      return buildConditionList(columns, items, worksheet, isHaving, null);
   }

   /**
    * @param privateCs the table's private column selection, used to register a synthetic
    *                  date-part column created by a {@code dateGroupLevel} (see
    *                  {@link #applyDateGroupLevel}) so it can be resolved at query time. May be
    *                  {@code null} (e.g. from unit tests that don't exercise dateGroupLevel),
    *                  in which case the wrapped column is built but not registered.
    */
   ConditionList buildConditionList(ColumnSelection columns,
                                    List<WorksheetTable.ConditionItem> items,
                                    Worksheet worksheet,
                                    boolean isHaving,
                                    ColumnSelection privateCs)
   {
      ConditionList list = new ConditionList();

      for(WorksheetTable.ConditionItem item : items) {
         // Insert a junction operator before each non-first item.
         if(item.getJunction() != null) {
            int junctionType = "or".equalsIgnoreCase(item.getJunction())
               ? JunctionOperator.OR : JunctionOperator.AND;
            list.append(new JunctionOperator(junctionType, item.resolveJunctionLevel()));
         }

         appendConditionItem(list, item, columns, worksheet, isHaving, privateCs);
      }

      return list;
   }

   // Package-private for unit testing (WorksheetTableServiceConditionTest).
   ConditionList buildRankingConditionList(ColumnSelection columns,
                                           List<WorksheetTable.ConditionItem> items,
                                           ColumnSelection privateCs)
   {
      ConditionList list = new ConditionList();

      for(WorksheetTable.ConditionItem item : items) {
         if(item.getJunction() != null) {
            int junctionType = "or".equalsIgnoreCase(item.getJunction())
               ? JunctionOperator.OR : JunctionOperator.AND;
            list.append(new JunctionOperator(junctionType, item.resolveJunctionLevel()));
         }

         appendRankingConditionItem(list, item, columns, privateCs);
      }

      return list;
   }

   /** True if any item in {@code items} carries a {@code dateGroupLevel}. Null-safe. */
   private static boolean hasDateGroupLevel(List<WorksheetTable.ConditionItem> items) {
      return items != null && items.stream().anyMatch(i -> i.getDateGroupLevel() != null);
   }

   /**
    * Wraps {@code ref} in a {@link DateRangeRef} truncated/extracted to {@code dateGroupLevel} (e.g.
    * "year", "month of year"), so a condition/ranking compares the date part instead of the raw
    * timestamp — mirrors the grouping wrap in {@link #applyAggregateInfo}. No-op when
    * {@code dateGroupLevel} is null or {@code ref} isn't a plain column.
    *
    * <p>When {@code privateCs} is non-null, also registers the synthetic column into it (mirroring
    * {@link #applyAggregateInfo}'s GROUP BY registration) so the query engine can resolve it — an
    * unregistered synthetic column that fails to SQL-merge falls back to StyleBI's in-memory
    * condition evaluation, which can't find it by name and defaults to matching every row. The
    * synthetic column is marked not-visible so it never leaks into the table's output columns.
    */
   private DataRef applyDateGroupLevel(DataRef ref, String dateGroupLevel, ColumnSelection privateCs) {
      if(dateGroupLevel == null || !(ref instanceof ColumnRef column)) {
         return ref;
      }

      int dgroup = getDateGroupLevel(dateGroupLevel);
      String name = DateRangeRef.getName(column.getName(), dgroup);

      // Reuse an already-registered synthetic column for the same base field + level (e.g. two
      // condition leaves on the same date field/level) instead of adding a duplicate entry.
      if(privateCs != null) {
         DataRef existing = privateCs.getAttribute(name);

         if(existing instanceof ColumnRef) {
            return existing;
         }
      }

      DateRangeRef rangeRef = new DateRangeRef(name, column.getDataRef(), dgroup);
      rangeRef.setOriginalType(column.getDataType());
      ColumnRef dateColumn = new ColumnRef(rangeRef);
      dateColumn.setDataType(rangeRef.getDataType());
      dateColumn.setVisible(false);

      if(privateCs != null) {
         int baseIdx = privateCs.indexOfAttribute(column);

         if(baseIdx >= 0) {
            privateCs.addAttribute(baseIdx, dateColumn);
         }
         else {
            privateCs.addAttribute(dateColumn);
         }
      }

      return dateColumn;
   }

   private void appendRankingConditionItem(ConditionList list,
                                           WorksheetTable.ConditionItem item,
                                           ColumnSelection columns,
                                           ColumnSelection privateCs)
   {
      if(item.getField() == null || item.getOperation() == null) {
         return;
      }

      DataRef ref = columns.getAttribute(item.getField());

      if(ref == null) {
         return;
      }

      ref = applyDateGroupLevel(ref, item.getDateGroupLevel(), privateCs);

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
                                    boolean isHaving,
                                    ColumnSelection privateCs)
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

      // Truncate/extract to the requested date part BEFORE comparing, mirroring applyAggregateInfo's
      // grouping wrap. Without this, a condition's dateGroupLevel was silently ignored: the raw
      // timestamp column was compared directly against a date-part literal (e.g. "2025-01-01"),
      // which never matches, silently zeroing every row instead of erroring.
      ref = applyDateGroupLevel(ref, item.getDateGroupLevel(), privateCs);

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

      // When equal=true expands one operator into two (e.g. >= becomes "> OR ="), that internal OR
      // must bind ONLY the expanded pair. XConditionGroup evaluates a flat ConditionList by level
      // (higher level = tighter grouping), so if the pair's OR sits at the item's own level it
      // re-associates with the surrounding AND/OR junctions (which buildConditionList appends at
      // item.resolveJunctionLevel() == the item's level). That silently rewrites the logic — e.g.
      // "a AND b>=x AND c" (intended "a AND (b>x OR b=x) AND c") collapses to
      // "(a AND b>x) OR (b=x AND c)", dropping the c bound entirely. Nest the expanded pair one
      // level deeper so it forms a self-contained "(> OR =)" group. A single-op condition is
      // unchanged (keeps the item's level).
      int opLevel = ops.size() > 1 ? item.getConditionLevel() + 1 : item.getConditionLevel();
      boolean firstOp = true;

      for(int op : ops) {
         if(!firstOp) {
            // LESS_THAN/GREATER_THAN with equal=true expand to two ops joined by OR (e.g. < OR =).
            list.append(new JunctionOperator(JunctionOperator.OR, opLevel));
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

         list.append(new ConditionItem(ref, ac, opLevel));
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
            // A FIELD operand compares the condition column against ANOTHER column (e.g.
            // amount > stage_avg). Resolve the referenced column name to a DataRef against the
            // table's ColumnSelection (same mechanism as appendRankingConditionItem) and add that
            // as the operand value: Condition serializes a DataRef operand via its isfield branch,
            // and reverseValue maps it back to "FIELD". Previously this wrapped the bare column name
            // in a JavaScript ExpressionValue (an undefined identifier), so the comparison never
            // became a column reference and the filter silently matched ALL rows.
            String name = v.getValue() != null ? v.getValue().toString() : null;
            DataRef ref = name != null ? columns.getAttribute(name) : null;

            if(ref == null) {
               throw new IllegalArgumentException(
                  "FIELD condition operand references unknown column \"" + name + "\".");
            }

            condition.addValue(ref);
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

   /** Names probe tables the caller did not name; the assembly is removed before the next one. */
   private static final java.util.concurrent.atomic.AtomicLong PROBE_TABLE_SEQUENCE =
      new java.util.concurrent.atomic.AtomicLong();

   /**
    * The deployment ceiling on sampled rows, shared with the endpoint path
    * ({@code EndpointJsonQueryRunner}). Named for REST because that is where sampling started; it
    * governs sampled customer data leaving ANY tabular connector, and 0 switches it off entirely.
    */
   private static final String SAMPLE_ROWS_PROPERTY = "rest.sample.rows";
   /** What {@code rest.sample.rows} defaults to — {@code JsonRowSampler.DEFAULT_MAX_ROWS}. */
   private static final int DEFAULT_SAMPLE_ROWS = 20;

   /** {@code tabularSource.targetKind} for a SaaS/REST connector's endpoint. */
   private static final String TARGET_KIND_ENDPOINT = "endpoint";
   /** {@code tabularSource.targetKind} for a path-addressed connector's file. */
   private static final String TARGET_KIND_FILE = "file";
   /** ServerFile's name for the property that carries the file; preferred when a connector has it. */
   private static final String FILE_TARGET_PROPERTY = "fileFolder";
   /** The property a workbook's sheet is selected through. */
   private static final String EXCEL_SHEET_PROPERTY = "excelSheet";

   private static final Logger LOG = LoggerFactory.getLogger(WorksheetTableService.class);
}
