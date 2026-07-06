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

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.web.wiz.pairing.PairingException;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Returns a sample of data rows from a live {@link RuntimeWorksheet} table without
 * modifying any state.
 *
 * <p>Data is fetched via {@link AssetQuerySandbox#getTableLens} in
 * {@link AssetQuerySandbox#RUNTIME_MODE}, which executes the full underlying query
 * against the live data source.  LIVE_MODE must NOT be used here: it samples input
 * tables to the sandbox's design-time row cap, so aggregates over large tables are
 * silently computed on truncated data — an agent reading those numbers gets
 * plausible-but-wrong analytics.  The {@code limit} only caps how many result rows
 * are returned, not the rows the query computes over.  Callers should keep
 * {@code limit} small (≤ 200) to avoid large payloads in an agent context.</p>
 */
@Service
public class WorksheetPreviewService {

   /**
    * Query up to {@code limit} rows from the named table in the live worksheet.
    *
    * @param rws       the live runtime worksheet
    * @param tableName the table assembly name to query
    * @param limit     maximum number of data rows to return (header row excluded)
    * @return list of row maps keyed by column name; never {@code null}
    * @throws PairingException if the sandbox is absent, the table is not found,
    *                          or the query execution fails
    */
   public List<Map<String, Object>> preview(RuntimeWorksheet rws,
                                             String tableName,
                                             int limit)
      throws PairingException
   {
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      if(box == null) {
         throw new PairingException(PairingException.Kind.INTERNAL, "Worksheet query sandbox is not available");
      }

      // RUNTIME_MODE execution calls TableAssembly.replaceVariables, which substitutes
      // variable values ($(name)) into condition lists IN PLACE. Bound tables are cloned
      // per-execution so their originals are safe, but EmbeddedTableAssembly executes
      // un-cloned — the substitution would permanently bake the current variable value
      // into the worksheet state (the composer avoids this by previewing on a cloned
      // runtime sheet). Snapshot the variable-bearing structures and restore them after.
      Map<String, ConditionSnapshot> snapshots = snapshotConditions(rws.getWorksheet());
      TableLens lens;

      try {
         lens = box.getTableLens(tableName, AssetQuerySandbox.RUNTIME_MODE);
      }
      catch(Exception e) {
         throw new PairingException("Failed to execute worksheet query for '"
                                    + tableName + "': " + e.getMessage());
      }
      finally {
         restoreConditions(rws.getWorksheet(), snapshots);
      }

      if(lens == null) {
         throw new PairingException("Table not found or produced no data: " + tableName);
      }

      int colCount = lens.getColCount();

      // Row 0 is the header row in StyleBI's TableLens convention.
      String[] headers = new String[colCount];

      for(int col = 0; col < colCount; col++) {
         Object h = lens.getObject(0, col);
         headers[col] = h != null ? h.toString() : "col" + col;
      }

      List<Map<String, Object>> rows = new ArrayList<>();

      for(int row = 1; rows.size() < limit && lens.moreRows(row); row++) {
         Map<String, Object> rowMap = new LinkedHashMap<>();

         for(int col = 0; col < colCount; col++) {
            rowMap.put(headers[col], toJsonSafe(lens.getObject(row, col)));
         }

         rows.add(rowMap);
      }

      return rows;
   }

   /**
    * The structures {@link inetsoft.uql.asset.TableAssembly#replaceVariables} mutates:
    * pre/post/ranking/MV condition lists and the aggregate info.
    */
   private record ConditionSnapshot(
      inetsoft.uql.ConditionListWrapper pre,
      inetsoft.uql.ConditionListWrapper post,
      inetsoft.uql.ConditionListWrapper ranking,
      inetsoft.uql.ConditionListWrapper mvUpdatePre,
      inetsoft.uql.ConditionListWrapper mvUpdatePost,
      inetsoft.uql.ConditionListWrapper mvDeletePre,
      inetsoft.uql.ConditionListWrapper mvDeletePost,
      inetsoft.uql.asset.AggregateInfo aggregateInfo) {}

   private static Map<String, ConditionSnapshot> snapshotConditions(
      inetsoft.uql.asset.Worksheet ws)
   {
      Map<String, ConditionSnapshot> snapshots = new HashMap<>();

      if(ws == null) {
         return snapshots;
      }

      for(inetsoft.uql.asset.Assembly a : ws.getAssemblies()) {
         if(a instanceof inetsoft.uql.asset.TableAssembly t) {
            snapshots.put(t.getName(), new ConditionSnapshot(
               cloneConds(t.getPreConditionList()),
               cloneConds(t.getPostConditionList()),
               cloneConds(t.getRankingConditionList()),
               cloneConds(t.getMVUpdatePreConditionList()),
               cloneConds(t.getMVUpdatePostConditionList()),
               cloneConds(t.getMVDeletePreConditionList()),
               cloneConds(t.getMVDeletePostConditionList()),
               t.getAggregateInfo() != null
                  ? (inetsoft.uql.asset.AggregateInfo) t.getAggregateInfo().clone() : null));
         }
      }

      return snapshots;
   }

   private static void restoreConditions(inetsoft.uql.asset.Worksheet ws,
                                         Map<String, ConditionSnapshot> snapshots)
   {
      if(ws == null) {
         return;
      }

      for(inetsoft.uql.asset.Assembly a : ws.getAssemblies()) {
         if(a instanceof inetsoft.uql.asset.TableAssembly t) {
            ConditionSnapshot s = snapshots.get(t.getName());

            if(s == null) {
               continue;
            }

            t.setPreConditionList(s.pre());
            t.setPostConditionList(s.post());
            t.setRankingConditionList(s.ranking());
            t.setMVUpdatePreConditionList(s.mvUpdatePre());
            t.setMVUpdatePostConditionList(s.mvUpdatePost());
            t.setMVDeletePreConditionList(s.mvDeletePre());
            t.setMVDeletePostConditionList(s.mvDeletePost());

            if(s.aggregateInfo() != null) {
               t.setAggregateInfo(s.aggregateInfo());
            }
         }
      }
   }

   private static inetsoft.uql.ConditionListWrapper cloneConds(
      inetsoft.uql.ConditionListWrapper wrapper)
   {
      return wrapper != null
         ? (inetsoft.uql.ConditionListWrapper) wrapper.clone() : null;
   }

   /**
    * Convert a cell value to a JSON-safe type.  Primitive wrappers, Strings, and
    * null pass through unchanged.  Anything else (e.g. PostgreSQL PGobject for
    * tsvector/enum, byte arrays, custom JDBC types) is converted to its String
    * representation so Jackson can always serialize the response without breaking
    * gzip or JSON encoding.
    */
   private static Object toJsonSafe(Object value) {
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

      // Covers PGobject (tsvector, enum, etc.) and any other non-standard JDBC type
      return value.toString();
   }
}
