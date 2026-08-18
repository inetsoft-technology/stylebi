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
                              VSBindingModelService bindingModelService)
   {
      this.sessions = sessions;
      this.binding = binding;
      this.bindingModelService = bindingModelService;
   }

   public void setShelf(String sessionToken, Principal user, String assemblyName, String shelf,
                        List<FieldRef> fields) throws Exception
   {
      apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.setShelf(model, shelf, fields));
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

   public void addField(String sessionToken, Principal user, String assemblyName, String shelf,
                        FieldRef field, Integer position) throws Exception
   {
      apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.addField(model, shelf, field, position));
   }

   public void removeField(String sessionToken, Principal user, String assemblyName,
                           String shelf, String column) throws Exception
   {
      apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.removeField(model, shelf, column));
   }

   public void moveField(String sessionToken, Principal user, String assemblyName,
                         String fromShelf, String toShelf, String column, Integer position)
      throws Exception
   {
      apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.moveField(model, fromShelf, toShelf, column, position));
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
}
