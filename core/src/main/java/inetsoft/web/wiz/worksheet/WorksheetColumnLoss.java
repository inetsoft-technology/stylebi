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

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PreAssetQuery;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.DataRefWrapper;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.util.CoreTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Bug #79595: a vanished upstream column silently degrades the worksheet tables built on a
 * tabular source (mirror, join, concat). Two agent-side checks that report it:
 * <ul>
 *    <li>{@link StoredInputs}: snapshot the stored group/aggregate/sort inputs before a refresh
 *    and report the ones the refresh dropped, since the drop in
 *    {@code AbstractTableAssembly.setColumnSelection} raises nothing.</li>
 *    <li>{@link #missingUpstreamInputs}: at preview time, report the stored inputs whose column is
 *    absent from the executed upstream result, independent of any cache.</li>
 * </ul>
 * Deliberately not wired into the shared assembly/sandbox paths, which also run on every
 * Composer open.
 */
final class WorksheetColumnLoss {
   private WorksheetColumnLoss() {
   }

   /** The stored group/aggregate/sort inputs of every non-crosstab table, by identity. */
   static final class StoredInputs {
      private final Map<String, Set<Object>> inputs = new LinkedHashMap<>();

      static StoredInputs take(Worksheet ws) {
         StoredInputs snapshot = new StoredInputs();

         if(ws == null) {
            return snapshot;
         }

         for(Assembly a : ws.getAssemblies()) {
            if(a instanceof TableAssembly table && !isCrosstab(table)) {
               snapshot.inputs.put(table.getName(), storedInputs(table));
            }
         }

         return snapshot;
      }

      /**
       * One warning per stored input that no longer exists, using the wording and exclusions of
       * the runtime {@code PreAssetQuery} check. Empty when nothing was lost.
       */
      List<String> lost(Worksheet ws) {
         Set<String> messages = new LinkedHashSet<>();

         for(Map.Entry<String, Set<Object>> e : inputs.entrySet()) {
            if(!(ws.getAssembly(e.getKey()) instanceof TableAssembly table)) {
               continue;
            }

            Set<Object> kept = storedInputs(table);

            for(Object input : e.getValue()) {
               if(!kept.contains(input)) {
                  String message = PreAssetQuery.upstreamColumnLossMessage(e.getKey(), input);

                  if(message != null) {
                     messages.add(message);
                  }
               }
            }
         }

         return new ArrayList<>(messages);
      }

      /** Raises the lost-input warnings on the CURRENT thread, where the caller reads them. */
      void warnLost(Worksheet ws) {
         lost(ws).forEach(CoreTool::addUserWarning);
      }
   }

   /**
    * For the previewed table and every composed table below it whose source chain contains a
    * tabular source, the warnings for stored group/aggregate/sort columns that are absent from
    * the executed result of the table's direct sources. Conservative on purpose: anything that
    * cannot be judged (crosstab, expression/calc/alias inputs, hidden source columns, date-level
    * group names, a source that cannot be read or hit the column cap) is not reported.
    */
   static List<String> missingUpstreamInputs(Worksheet ws, AssetQuerySandbox box,
                                             String tableName)
   {
      Set<String> messages = new LinkedHashSet<>();

      try {
         if(ws != null && ws.getAssembly(tableName) instanceof TableAssembly table) {
            check(ws, box, table, new HashSet<>(), messages);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to check the upstream columns of table: {}", tableName, e);
      }

      return new ArrayList<>(messages);
   }

   private static void check(Worksheet ws, AssetQuerySandbox box, TableAssembly table,
                             Set<String> visited, Set<String> messages)
   {
      if(!visited.add(table.getName()) || !(table instanceof ComposedTableAssembly composed)) {
         return;
      }

      for(String name : composed.getTableNames()) {
         if(ws.getAssembly(name) instanceof TableAssembly source) {
            check(ws, box, source, visited, messages);
         }
      }

      if(!hasTabularSource(ws, composed, new HashSet<>()) || isCrosstab(composed)) {
         return;
      }

      Set<String> available = new HashSet<>();
      Set<String> hidden = new HashSet<>();

      for(String name : composed.getTableNames()) {
         if(!(ws.getAssembly(name) instanceof TableAssembly source) ||
            !addHeaders(box, source, available))
         {
            return;
         }

         ColumnSelection columns = source.getColumnSelection(false);

         for(int i = 0; columns != null && i < columns.getAttributeCount(); i++) {
            if(columns.getAttribute(i) instanceof ColumnRef col && !col.isVisible()) {
               addNames(hidden, col);
            }
         }
      }

      if(available.size() >= Util.getOrganizationMaxColumn()) {
         return;
      }

      AggregateInfo info = composed.getAggregateInfo();
      // an aggregate table sorts on its own output columns (Sum(n), aggregate aliases), which are
      // not source columns; its lost group/aggregate inputs already report the loss
      boolean aggregated = info != null && !info.isEmpty();

      for(Object input : storedInputs(composed)) {
         if(aggregated && input instanceof SortRef) {
            continue;
         }

         String message = PreAssetQuery.upstreamColumnLossMessage(composed.getName(), input);

         if(message == null) {
            continue;
         }

         DataRef ref = inputRef(input);

         while(ref instanceof DataRefWrapper wrapper && wrapper.getDataRef() != null) {
            ref = wrapper.getDataRef();
         }

         if(ref == null || ref.isExpression() || ref instanceof CalculateRef) {
            continue;
         }

         Set<String> names = new HashSet<>();
         addNames(names, ref);
         addNames(names, inputRef(input));

         if(names.stream().noneMatch(n -> available.contains(n) || hidden.contains(n))) {
            messages.add(message);
         }
      }
   }

   /** False when the source's executed header cannot be read, so the caller skips the table. */
   private static boolean addHeaders(AssetQuerySandbox box, TableAssembly source,
                                     Set<String> available)
   {
      try {
         var lens = box.getTableLens(source.getName(), AssetQuerySandbox.RUNTIME_MODE);

         if(lens == null) {
            return false;
         }

         for(int c = 0; c < lens.getColCount(); c++) {
            Object header = lens.getObject(0, c);

            if(header != null) {
               String text = header.toString();
               available.add(text);
               available.add(text.substring(text.lastIndexOf('.') + 1));
            }
         }

         return true;
      }
      catch(Exception e) {
         LOG.debug("Cannot read the executed columns of table: {}", source.getName(), e);
         return false;
      }
   }

   private static void addNames(Set<String> names, DataRef ref) {
      if(ref == null) {
         return;
      }

      names.add(ref.getName());
      names.add(ref.getAttribute());

      if(ref instanceof ColumnRef col && col.getAlias() != null) {
         names.add(col.getAlias());
      }

      if(ref instanceof DataRefWrapper wrapper && wrapper.getDataRef() != null &&
         wrapper.getDataRef() != ref)
      {
         addNames(names, wrapper.getDataRef());
      }
   }

   private static boolean hasTabularSource(Worksheet ws, TableAssembly table,
                                           Set<String> visited)
   {
      if(!(table instanceof ComposedTableAssembly composed)) {
         return table instanceof TabularTableAssembly;
      }

      for(String name : composed.getTableNames()) {
         if(name != null && visited.add(name) &&
            ws.getAssembly(name) instanceof TableAssembly source &&
            (source instanceof TabularTableAssembly || hasTabularSource(ws, source, visited)))
         {
            return true;
         }
      }

      return false;
   }

   private static DataRef inputRef(Object input) {
      return input instanceof GroupRef g ? g.getDataRef() :
         input instanceof AggregateRef a ? a.getDataRef() :
         input instanceof SortRef s ? s.getDataRef() : null;
   }

   private static boolean isCrosstab(TableAssembly table) {
      AggregateInfo info = table.getAggregateInfo();
      return info != null && info.isCrosstab() ||
         table instanceof AbstractTableAssembly abstractTable && abstractTable.isCrosstab();
   }

   private static Set<Object> storedInputs(TableAssembly table) {
      Set<Object> inputs = Collections.newSetFromMap(new IdentityHashMap<>());
      AggregateInfo info = table.getAggregateInfo();

      if(info != null) {
         inputs.addAll(Arrays.asList(info.getGroups()));
         inputs.addAll(Arrays.asList(info.getAggregates()));
      }

      SortInfo sort = table.getSortInfo();

      if(sort != null) {
         inputs.addAll(Arrays.asList(sort.getSorts()));
      }

      return inputs;
   }

   private static final Logger LOG = LoggerFactory.getLogger(WorksheetColumnLoss.class);
}
