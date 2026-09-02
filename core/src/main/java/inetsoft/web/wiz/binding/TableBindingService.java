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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.model.SourceInfo;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.table.BaseTableBindingModel;
import inetsoft.web.binding.model.table.CalcTableBindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.function.Consumer;

/**
 * Crosstab and table shelf mutations.
 *
 * <p>Unlike charts, tables have no dedicated write endpoint: they go through the generic
 * {@code setbinding}, which takes the whole polymorphic {@code BindingModel}. That endpoint
 * carries a trap flag, defaulting on, so a binding that would produce a cartesian result is
 * reported rather than quietly applied. It is deliberately not disabled to make a call
 * succeed.
 *
 * <p>Each public method is exactly one {@code sessions.mutate} — one undo checkpoint. That is
 * why {@code moveField} exists rather than asking callers to remove then add: a crosstab pivot
 * as two calls would be two checkpoints, with an intermediate state the browser renders.
 */
@Service
public class TableBindingService {
   @Autowired
   public TableBindingService(ViewsheetSessionService sessions,
                              VSBindingService binding,
                              VSBindingModelService bindingModelService,
                              DataRefModelFactoryService refModelService)
   {
      this.sessions = sessions;
      this.binding = binding;
      this.bindingModelService = bindingModelService;
      this.refModelService = refModelService;
   }

   public void setShelf(String sessionToken, Principal user, String assemblyName, String shelf,
                        List<FieldRef> fields) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
         Viewsheet vs = rvs.getViewsheet();
         VSAssembly assembly = vs.getAssembly(assemblyName);
         inetsoft.uql.asset.SourceInfo source = assembly instanceof DataVSAssembly data
            ? data.getSourceInfo() : null;
         requireSourceForFieldWrite(assemblyName, shelf, source,
                                    fields == null ? 0 : fields.size());
         TableBindingMutator.setShelf(model, shelf, fields, rvs, source, refModelService);

         ApplyVSAssemblyInfoEvent event = new ApplyVSAssemblyInfoEvent();
         event.setName(assemblyName);
         event.setBinding(model);
         bindingModelService.setBinding(runtimeId, event, user, dispatcher);
      });
   }

   /**
    * Points a crosstab or table at a source table.
    *
    * <p>An assembly added in the Composer starts with no source. Its shelves can be populated —
    * {@code set_table_fields} reports success — and it renders nothing at all, because shelves
    * with no source have nothing to query. Nothing else here assigns one: the mutators
    * <em>preserve</em> {@code source} through a read-modify-write, which is not the same as
    * being able to set it.
    *
    * <p>Repointing a bound assembly discards every field on its shelves, since the columns
    * belong to the old source. That is refused unless {@code force} is set, rather than done
    * silently on one call.
    */
   public void setSource(String sessionToken, Principal user, String assemblyName,
                         String table, boolean force) throws Exception
   {
      if(table == null || table.isBlank()) {
         throw new IllegalArgumentException(
            "set_table_source requires 'table' — the source table's name. " +
            "list_bindable_fields reports what this assembly can bind to.");
      }

      apply(sessionToken, user, assemblyName, model -> {
         String resolved = resolveTable(model, table, assemblyName);

         if(!force) {
            requireNoBoundFields(model, assemblyName, resolved);
         }
         else {
            discardBoundFields(model, resolved);
         }

         // Only type, prefix and source survive the trip back: VSBindingService.updateSourceInfo
         // calls SourceInfo.toSourceAttr, which rebuilds the asset source from exactly those
         // three. Setting them directly rather than through the SourceInfo(uql.SourceInfo)
         // convenience constructor also avoids that constructor's toView() call, which drags in
         // VSUtil for a display string nothing here reads.
         SourceInfo source = new SourceInfo();
         source.setType(inetsoft.uql.asset.SourceInfo.ASSET);
         source.setSource(resolved);
         source.setView(resolved);
         model.setSource(source);
      }, true);
   }

   /** Matches a requested table against what the assembly can actually bind to. */
   private static String resolveTable(BaseTableBindingModel model, String table,
                                      String assemblyName)
   {
      List<BindingModel.SourceTable> tables = model.getTables();
      List<String> names = new ArrayList<>();

      if(tables != null) {
         for(BindingModel.SourceTable candidate : tables) {
            if(candidate.getName() != null) {
               names.add(candidate.getName());

               if(candidate.getName().equalsIgnoreCase(table)) {
                  return candidate.getName();
               }
            }
         }
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' cannot bind to '" + table + "'. Available: " + names + ". " +
         "A source the assembly cannot see binds nothing and renders an empty assembly.");
   }

   /**
    * Refuses to discard bound fields. The columns on a shelf belong to the source that was set
    * when they were added, so repointing invalidates all of them.
    */
   private static void requireNoBoundFields(BaseTableBindingModel model, String assemblyName,
                                            String table)
   {
      SourceInfo current = model.getSource();

      if(current != null && table.equalsIgnoreCase(current.getSource())) {
         return;
      }

      // A calc table has no shelves to discard — its binding lives in its cells, which keep
      // referring to their columns by name. Nothing to warn about, and shelvesOf would refuse it.
      if(model instanceof CalcTableBindingModel) {
         return;
      }

      List<String> populated = new ArrayList<>();

      for(String shelf : TableBindingMutator.shelvesOf(model)) {
         int count = TableBindingMutator.read(model, shelf).size();

         if(count > 0) {
            populated.add(count + " on " + shelf);
         }
      }

      if(!populated.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' already has fields bound (" + String.join(", ", populated) +
            "). Changing its source would discard them, because those columns belong to the " +
            "old source. Clear the shelves first, or pass force:true to discard them " +
            "deliberately.");
      }
   }

   /**
    * The {@code force:true} counterpart to {@link #requireNoBoundFields}: discards only the
    * fields that no longer resolve in the new source, matching the selective-discard intent
    * documented on the UI's own repoint path ({@code VSAssemblyInfoHandler}: "check the old
    * binding columns when source changed, if cannot found the columns in the source, just
    * remove them"). A field whose column name also exists in the new source is kept — a
    * same-shaped repoint (e.g. a partitioned/monthly table swapped for its sibling) should not
    * discard bindings a human doing the equivalent repoint would keep. Without this at all, a
    * repoint left the old source's field refs sitting on every shelf {@code force} didn't itself
    * touch, and those stale refs were written straight back onto the live assembly's design
    * headers by the factory that follows this mutation — that failure mode (never discarding
    * anything) is guarded against by shelves whose fields never resolve in the new source still
    * being fully cleared here, same as before.
    *
    * <p>Known limitation: a kept field's {@code namedGroup} binding is not preserved, because
    * this path has no {@code RuntimeViewsheet}/{@code DataRefModelFactoryService} context to
    * re-resolve it against (the same limitation {@link TableBindingMutator}'s context-less
    * {@code setShelf} overload already has everywhere else it is used).
    */
   private static void discardBoundFields(BaseTableBindingModel model, String table) {
      SourceInfo current = model.getSource();

      if(current != null && table.equalsIgnoreCase(current.getSource())) {
         return;
      }

      // A calc table has no shelves to discard — see requireNoBoundFields above.
      if(model instanceof CalcTableBindingModel) {
         return;
      }

      List<String> availableColumns = columnsOf(model, table);

      for(String shelf : TableBindingMutator.shelvesOf(model)) {
         List<FieldRef> bound = TableBindingMutator.read(model, shelf);
         List<FieldRef> stillResolves = new ArrayList<>();

         for(FieldRef field : bound) {
            if(field.column() != null &&
               availableColumns.stream().anyMatch(
                  c -> c.equalsIgnoreCase(field.column()) ||
                       c.equalsIgnoreCase(unqualified(field.column()))))
            {
               stillResolves.add(field);
            }
         }

         if(stillResolves.size() != bound.size()) {
            TableBindingMutator.setShelf(model, shelf, stillResolves);
         }
      }
   }

   /**
    * The new source table's column names, matching {@link #resolveTable}'s own lookup. Each
    * column contributes both its raw name and, when it is qualified ({@code "table.attribute"}),
    * the unqualified attribute name too -- {@code ColumnSelection} entries for a joined/merged
    * worksheet table (the common case a repoint targets) commonly carry the qualified form. An
    * old bound field's column name can independently be qualified or not (see {@link
    * #unqualified}), so expanding only this side is not sufficient by itself -- but skipping it
    * would still treat every field as unresolved whenever the new source itself is qualified and
    * the old field is not, silently degrading back to discarding everything.
    */
   private static List<String> columnsOf(BaseTableBindingModel model, String table) {
      List<String> names = new ArrayList<>();
      List<BindingModel.SourceTable> tables = model.getTables();

      if(tables != null) {
         for(BindingModel.SourceTable candidate : tables) {
            if(table.equalsIgnoreCase(candidate.getName()) && candidate.getColumns() != null) {
               for(BindingModel.SourceTableColumn column : candidate.getColumns()) {
                  if(column.getName() == null) {
                     continue;
                  }

                  names.add(column.getName());
                  String bare = unqualified(column.getName());

                  if(!bare.equals(column.getName())) {
                     names.add(bare);
                  }
               }
            }
         }
      }

      return names;
   }

   /**
    * The unqualified suffix of a possibly {@code "table.attribute"}-qualified column name, or
    * the name itself when it carries no qualifier. Applied to both the new source's column list
    * and an old bound field's column name in {@link #discardBoundFields}, since either side can
    * independently be qualified or not depending on whether its own source table is a
    * joined/merged worksheet table -- comparing only one side's unqualified form would still
    * miss the {qualified old field, unqualified new source} pairing.
    */
   private static String unqualified(String name) {
      int dot = name.lastIndexOf('.');
      return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1) : name;
   }

   public void addField(String sessionToken, Principal user, String assemblyName, String shelf,
                        FieldRef field, Integer position) throws Exception
   {
      applyWithContext(sessionToken, user, assemblyName,
         (model, rvs, source) -> {
            requireSourceForFieldWrite(assemblyName, shelf, source, field == null ? 0 : 1);
            TableBindingMutator.addField(model, shelf, field, position, rvs, source,
                                         refModelService);
         });
   }

   /**
    * Refuses a non-empty shelf write to an assembly with no source: {@link
    * TableBindingMutator}'s shelf builders have no source-conditioned branch, so the write would
    * be applied and reported as success, then render nothing because there is no source to
    * query. Scoped to callers that add fields to a shelf ({@link #setShelf}/{@link #addField});
    * {@link #removeField}/{@link #moveField} must not call this — a sourceless assembly can never
    * have anything on its shelves to remove or move in the first place, once this guard is in
    * place on the calls that put fields there.
    */
   private static void requireSourceForFieldWrite(String assemblyName, String shelf,
                                                   inetsoft.uql.asset.SourceInfo source,
                                                   int fieldCount)
   {
      if(source == null && fieldCount > 0) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' has no source table yet, so binding " + fieldCount +
            " field(s) to its '" + shelf + "' shelf would render nothing -- shelves with no " +
            "source have nothing to query. Call set_table_source first.");
      }
   }

   public void removeField(String sessionToken, Principal user, String assemblyName,
                           String shelf, String column) throws Exception
   {
      applyWithContext(sessionToken, user, assemblyName,
         (model, rvs, source) ->
            TableBindingMutator.removeField(model, shelf, column, rvs, source, refModelService));
   }

   public void moveField(String sessionToken, Principal user, String assemblyName,
                         String fromShelf, String toShelf, String column, Integer position)
      throws Exception
   {
      applyWithContext(sessionToken, user, assemblyName,
         (model, rvs, source) ->
            TableBindingMutator.moveField(model, fromShelf, toShelf, column, position, rvs,
                                          source, refModelService));
   }

   public void setSort(String sessionToken, Principal user, String assemblyName, String shelf,
                       String column, Integer index, DimensionSortRanking.Sort sort)
      throws Exception
   {
      apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.setSort(model, shelf, column, index, sort));
   }

   public void setRanking(String sessionToken, Principal user, String assemblyName, String shelf,
                          String column, Integer index, DimensionSortRanking.Ranking ranking)
      throws Exception
   {
      apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.setRanking(model, shelf, column, index, ranking));
   }

   public void setColumnLabels(String sessionToken, Principal user, String assemblyName,
                               Map<String, String> labels) throws Exception
   {
      apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.setColumnLabels(model, labels));
   }

   public void setOptions(String sessionToken, Principal user, String assemblyName,
                          Map<String, Object> options) throws Exception
   {
      apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.setOptions(model, options));
   }

   public Map<String, Object> optionVocabulary() {
      return TableBindingMutator.optionVocabulary();
   }

   /** The shelves, their contents, and the object type — without opening a checkpoint. */
   public Map<String, Object> read(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
      Map<String, Object> shelves = new LinkedHashMap<>();

      for(String shelf : TableBindingMutator.shelvesOf(model)) {
         shelves.put(shelf, TableBindingMutator.read(model, shelf));
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);
      out.put("objectType", model instanceof CrosstabBindingModel ? "crosstab" : "table");
      out.put("source", model.getSource() == null ? null : model.getSource().getSource());

      // A sourceless assembly accepts shelf writes and renders nothing, with no error anywhere
      // to say why. Saying so on the read every caller is told to make first is the cheapest
      // place to stop that.
      if(model.getSource() == null) {
         out.put("note",
                 "This assembly has no source table, so it renders empty whatever is on its " +
                 "shelves. Point it at one with set_table_source — list_bindable_fields " +
                 "reports the names it accepts.");
      }

      out.put("shelves", shelves);
      out.put("columnLabels", model.getName2Labels());
      Map<String, Object> sorts = new LinkedHashMap<>();

      for(String shelf : TableBindingMutator.shelvesOf(model)) {
         if(!"aggregates".equals(shelf) && !"details".equals(shelf)) {
            sorts.putAll(TableBindingMutator.describeSorts(model, shelf));
         }
      }

      out.put("sorts", sorts);
      out.put("options", describeOptions(model));

      if(model instanceof CrosstabBindingModel crosstab) {
         out.put("suppressGroupTotal", crosstab.getSuppressGroupTotal());
      }
      else if(model instanceof TableBindingModel table) {
         // Read-only for now: writing it turns an embedded table into a bound one, which is
         // closer to a data-loss operation than a binding edit.
         out.put("embedded", table.getEmbedded());
      }

      return out;
   }

   private static Map<String, Object> describeOptions(BaseTableBindingModel model) {
      Map<String, Object> out = new LinkedHashMap<>();

      if(model instanceof CrosstabBindingModel crosstab && crosstab.getOption() != null) {
         out.put("rowTotals", crosstab.getOption().getRowTotalVisibleValue());
         out.put("colTotals", crosstab.getOption().getColTotalVisibleValue());
         out.put("percentageBy",
                 TableBindingMutator.percentageByName(
                    crosstab.getOption().getPercentageByValue()));
         out.put("summarySideBySide", crosstab.getOption().isSummarySideBySide());
      }
      else if(model instanceof TableBindingModel table && table.getOption() != null) {
         out.put("grandTotal", table.getOption().getGrandTotal());
         out.put("distinct", table.getOption().getDistinct());
      }

      return out;
   }

   private void apply(String sessionToken, Principal user, String assemblyName,
                      Consumer<BaseTableBindingModel> mutation) throws Exception
   {
      apply(sessionToken, user, assemblyName, mutation, false);
   }

   /**
    * A shelf mutation that also needs the runtime context {@link TableBindingMutator#setShelf}
    * threads through to resolve a field's {@code namedGroup} -- unlike the plain {@link Consumer}
    * overload above, which reapplies a shelf with no context and so silently drops an
    * already-resolved {@code namedGroup} on any field the mutation doesn't itself touch.
    */
   @FunctionalInterface
   private interface ContextualShelfMutation {
      void accept(BaseTableBindingModel model, RuntimeViewsheet rvs,
                  inetsoft.uql.asset.SourceInfo source) throws Exception;
   }

   private void applyWithContext(String sessionToken, Principal user, String assemblyName,
                                 ContextualShelfMutation mutation) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
         inetsoft.uql.asset.SourceInfo source = assembly instanceof DataVSAssembly data
            ? data.getSourceInfo() : null;
         mutation.accept(model, rvs, source);

         ApplyVSAssemblyInfoEvent event = new ApplyVSAssemblyInfoEvent();
         event.setName(assemblyName);
         event.setBinding(model);
         bindingModelService.setBinding(runtimeId, event, user, dispatcher);
      });
   }

   private void apply(String sessionToken, Principal user, String assemblyName,
                      Consumer<BaseTableBindingModel> mutation, boolean allowCalcTable)
      throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName, allowCalcTable);
         mutation.accept(model);

         ApplyVSAssemblyInfoEvent event = new ApplyVSAssemblyInfoEvent();
         event.setName(assemblyName);
         event.setBinding(model);
         // Left at its default of true. A trap means the binding produces a cartesian or
         // otherwise invalid result, and turning it off to make the call succeed would be
         // trading a reported problem for an unreported one.
         bindingModelService.setBinding(runtimeId, event, user, dispatcher);
      });
   }

   private BaseTableBindingModel requireTableBinding(RuntimeViewsheet rvs, String assemblyName) {
      return requireTableBinding(rvs, assemblyName, false);
   }

   /**
    * @param allowCalcTable calc tables are refused for <b>shelf</b> operations, because their
    *                       binding lives in their cell layout. Assigning a <b>source</b> is not a
    *                       shelf operation: a {@code CalcTableVSAssembly} is a
    *                       {@code TableDataVSAssembly} and carries a source like any other, and
    *                       without one a freehand table renders empty however its cells are bound.
    */
   private BaseTableBindingModel requireTableBinding(RuntimeViewsheet rvs, String assemblyName,
                                                     boolean allowCalcTable)
   {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(allowCalcTable && assembly instanceof CalcTableVSAssembly) {
         BindingModel calcModel = binding.createModel(assembly);

         if(calcModel instanceof BaseTableBindingModel calcTable) {
            return calcTable;
         }

         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a calc table whose binding model cannot carry a source.");
      }

      if(assembly instanceof CalcTableVSAssembly) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a calc table. Its binding lives in its cell layout, " +
            "not its shelves — use the calc-table tools instead.");
      }

      BindingModel model = binding.createModel(assembly);

      if(!(model instanceof BaseTableBindingModel table)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a table or crosstab. Use the chart binding tools for charts.");
      }

      return table;
   }

   private final ViewsheetSessionService sessions;
   private final VSBindingService binding;
   private final VSBindingModelService bindingModelService;
   private final DataRefModelFactoryService refModelService;
}
