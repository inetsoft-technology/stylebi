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

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.CalendarPropertyDialogService;
import inetsoft.web.composer.vs.dialog.RangeSliderPropertyDialogService;
import inetsoft.web.composer.vs.dialog.SelectionListPropertyDialogService;
import inetsoft.web.composer.vs.dialog.SelectionTreePropertyDialogService;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.viewsheet.SelectionRuntimeService;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Points a selection list, selection tree, range slider (time slider) or calendar at a
 * table/column.
 *
 * <p>{@code TableBindingService}'s write path goes nowhere near these four types: it resolves a
 * {@code BaseTableBindingModel} through {@code VSBindingService}, which has no
 * {@code VSBindingFactory} for any of them — their actual shape (one table + one column, or one
 * table + an ordered column list) is not a shelf collection, and there is no polymorphic model to
 * ask for. So this service does not build one either. Instead it drives the exact same public
 * round trip the Composer's own property dialogs use for every other field on these types —
 * {@code get<Type>PropertyModel} / {@code set<Type>PropertyModel}, both already reachable from the
 * wiz layer via {@code AssemblyPropertyService}'s reflective dispatch — and only ever touches the
 * table/column part of the model it reads back. Everything else on the model round-trips
 * unchanged, so this cannot regress any property {@code set_assembly_properties} already reaches.
 *
 * <p>This is deliberately a peer of {@code TableBindingService}, not a branch inside it or an
 * entry in {@code AssemblyPropertyService}'s bindings map: it is a new, independent write surface
 * ({@code selection/source}), not a properties-patch alias.
 */
@Service
public class SelectionBindingService {
   @Autowired
   public SelectionBindingService(ViewsheetSessionService sessions,
                                  BindableFieldsService fieldsService,
                                  SelectionListPropertyDialogService selectionListService,
                                  SelectionTreePropertyDialogService selectionTreeService,
                                  RangeSliderPropertyDialogService rangeSliderService,
                                  CalendarPropertyDialogService calendarService)
   {
      this.sessions = sessions;
      this.fieldsService = fieldsService;
      this.selectionListService = selectionListService;
      this.selectionTreeService = selectionTreeService;
      this.rangeSliderService = rangeSliderService;
      this.calendarService = calendarService;
   }

   /**
    * @param columns  one or more column names, as reported by {@code list_bindable_fields}. A
    *                 selection list or calendar accepts exactly one; a selection tree accepts one
    *                 or more, in hierarchy order; a range slider accepts one (a single range) or
    *                 more (a composite range).
    * @param measure  selection list only — an optional aggregate/bar-chart measure column. Ignored
    *                 for every other type.
    * @param force    discards an existing binding to a different table, the way
    *                 {@code set_table_source}'s {@code force} does.
    */
   public Map<String, Object> setSource(String sessionToken, Principal user, String assemblyName,
                                        String table, List<String> columns,
                                        List<String> additionalTables, String measure,
                                        boolean force, String linkUri) throws Exception
   {
      if(table == null || table.isBlank()) {
         throw new IllegalArgumentException(
            "set_selection_source requires 'table' — the source table's name. " +
            "list_bindable_fields reports what this assembly can bind to.");
      }

      if(columns == null || columns.isEmpty()) {
         throw new IllegalArgumentException(
            "set_selection_source requires at least one column in 'columns'.");
      }

      List<String> additional = additionalTables == null ? List.of() : additionalTables;
      Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         SelectionVSAssembly assembly = SelectionRuntimeService.requireSelection(rvs, assemblyName);

         // Unscoped, not scoped to assemblyName: VSBindingTreeService.getBinding only builds a
         // tree for a ChartVSAssemblyInfo or TableDataVSAssemblyInfo — a SelectionVSAssemblyInfo
         // is neither, so a scoped call returns an empty listing for every one of these four
         // types. The unscoped call reads the same worksheet-wide tree that any fresh, unbound
         // table or crosstab would also see before it has a source of its own.
         List<BindableTable> tables = fieldsService.list(runtimeId, null, user);
         String resolvedTable = resolveTable(tables, assemblyName, table);
         List<BindableField> resolvedColumns =
            resolveColumns(tables, assemblyName, resolvedTable, columns);

         if(assembly instanceof SelectionListVSAssembly) {
            requireArity(assemblyName, "a selection list", resolvedColumns, 1, 1);
            SelectionListPropertyDialogModel model =
               selectionListService.getSelectionListPropertyModel(runtimeId, assemblyName, user);
            SelectionListPaneModel pane = model.getSelectionListPaneModel();
            requireRepoint(assemblyName, pane.getSelectedTable(), resolvedTable, force);
            pane.setSelectedTable(resolvedTable);
            pane.setAdditionalTables(additional);
            pane.setSelectedColumn(columnRef(resolvedTable, resolvedColumns.get(0)));

            if(measure != null && !measure.isBlank()) {
               pane.getSelectionMeasurePaneModel().setMeasure(measure);
            }

            selectionListService.setSelectionListPropertyModel(
               runtimeId, assemblyName, model, linkUri, user, dispatcher);
            result.put("bound", "single");
         }
         else if(assembly instanceof SelectionTreeVSAssembly) {
            requireArity(assemblyName, "a selection tree", resolvedColumns, 1, null);
            SelectionTreePropertyDialogModel model =
               selectionTreeService.getSelectionTreePropertyModel(runtimeId, assemblyName, user);
            SelectionTreePaneModel pane = model.getSelectionTreePaneModel();
            requireRepoint(assemblyName, pane.getSelectedTable(), resolvedTable, force);
            pane.setSelectedTable(resolvedTable);
            pane.setAdditionalTables(additional);
            // Hierarchy levels, not the id/parent-id/label mode — the shape set_selection_source
            // exposes is an ordered column list, matching TimeSlider's own SingleTimeInfo/
            // CompositeTimeInfo choice below rather than the ID-hierarchy alternative.
            pane.setMode(SelectionTreeVSAssemblyInfo.COLUMN);
            pane.setSelectedColumns(columnRefs(resolvedTable, resolvedColumns));
            selectionTreeService.setSelectionTreePropertyModel(
               runtimeId, assemblyName, model, linkUri, user, dispatcher);
            result.put("levels", resolvedColumns.size());
         }
         else if(assembly instanceof TimeSliderVSAssembly) {
            requireArity(assemblyName, "a range slider", resolvedColumns, 1, null);
            RangeSliderPropertyDialogModel model =
               rangeSliderService.getRangeSliderPropertyModel(runtimeId, assemblyName, user);
            RangeSliderDataPaneModel pane = model.getRangeSliderDataPaneModel();
            requireRepoint(assemblyName, pane.getSelectedTable(), resolvedTable, force);
            pane.setSelectedTable(resolvedTable);
            pane.setAdditionalTables(additional);
            boolean composite = resolvedColumns.size() > 1;
            pane.setComposite(composite);
            pane.setSelectedColumns(columnRefs(resolvedTable, resolvedColumns));

            if(!composite) {
               // A composite range's per-column type is read straight off each OutputColumnRefModel
               // by setTimeInfo; only the single-range case needs the range type decided up front,
               // the way AddFilterService.createFilterAssembly already infers it for a brand new
               // filter assembly.
               model.getRangeSliderAdvancedPaneModel().getRangeSliderSizePaneModel()
                  .setRangeType(inferRangeType(resolvedColumns.get(0).dataType()));
            }

            rangeSliderService.setRangeSliderPropertyModel(
               runtimeId, assemblyName, model, linkUri, user, dispatcher);
            result.put("composite", composite);
         }
         else if(assembly instanceof CalendarVSAssembly) {
            requireArity(assemblyName, "a calendar", resolvedColumns, 1, 1);
            CalendarPropertyDialogModel model =
               calendarService.getCalendarPropertyModel(runtimeId, assemblyName, user);
            CalendarDataPaneModel pane = model.getCalendarDataPaneModel();
            requireRepoint(assemblyName, pane.getSelectedTable(), resolvedTable, force);
            pane.setSelectedTable(resolvedTable);
            pane.setAdditionalTables(additional);
            pane.setSelectedColumn(columnRef(resolvedTable, resolvedColumns.get(0)));
            calendarService.setCalendarPropertyModel(
               runtimeId, assemblyName, model, linkUri, user, dispatcher);
         }
         else {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
               ", which set_selection_source does not support.");
         }

         result.put("assembly", assemblyName);
         result.put("table", resolvedTable);
         List<String> columnNames = new ArrayList<>();

         for(BindableField field : resolvedColumns) {
            columnNames.add(field.column());
         }

         result.put("columns", columnNames);
      });

      return result;
   }

   /**
    * Refuses to silently discard an existing binding to a different table.
    *
    * <p>Unlike {@code TableBindingService.requireNoBoundFields}, there are no shelves to count —
    * a selection assembly binds at most one table's worth of columns. Rebinding within the same
    * table (a different column, or a different set of hierarchy levels) is allowed without
    * {@code force}, matching {@code TableBindingService}'s own same-source tolerance; only an
    * actual table change while already bound requires it.
    */
   private static void requireRepoint(String assemblyName, String currentTable,
                                      String resolvedTable, boolean force)
   {
      if(currentTable == null || currentTable.isBlank() || force) {
         return;
      }

      if(currentTable.equalsIgnoreCase(resolvedTable)) {
         return;
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' is already bound to '" + currentTable + "'. Repointing to '" +
         resolvedTable + "' would discard that binding, so it is refused unless force:true is " +
         "set.");
   }

   private static void requireArity(String assemblyName, String typeLabel,
                                    List<BindableField> columns, int min, Integer max)
   {
      int size = columns.size();

      if(size >= min && (max == null || size <= max)) {
         return;
      }

      String expectation = max != null && max.intValue() == min
         ? "exactly " + min + " column" + (min == 1 ? "" : "s")
         : "at least " + min + " column" + (min == 1 ? "" : "s");

      throw new IllegalArgumentException(
         "'" + assemblyName + "' is " + typeLabel + ", which needs " + expectation +
         " in 'columns', got " + size + ".");
   }

   /** Matches a requested table against what this viewsheet's worksheet actually offers. */
   private static String resolveTable(List<BindableTable> tables, String assemblyName,
                                      String table)
   {
      List<String> names = new ArrayList<>();

      for(BindableTable candidate : tables) {
         if(candidate.name() != null) {
            names.add(candidate.name());

            if(candidate.name().equalsIgnoreCase(table)) {
               return candidate.name();
            }
         }
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' cannot bind to '" + table + "'. Available: " + names + ". " +
         "A source the assembly cannot see binds nothing and renders an empty assembly.");
   }

   private static List<BindableField> resolveColumns(List<BindableTable> tables,
                                                      String assemblyName, String table,
                                                      List<String> columns)
   {
      List<BindableField> fields = List.of();

      for(BindableTable candidate : tables) {
         if(candidate.name().equalsIgnoreCase(table)) {
            fields = candidate.fields();
            break;
         }
      }

      List<String> available = new ArrayList<>();

      for(BindableField field : fields) {
         if(field.column() != null) {
            available.add(field.column());
         }
      }

      List<BindableField> resolved = new ArrayList<>();

      for(String column : columns) {
         BindableField found = null;

         for(BindableField field : fields) {
            if(field.column() != null && field.column().equalsIgnoreCase(column)) {
               found = field;
               break;
            }
         }

         if(found == null) {
            throw new IllegalArgumentException(
               "'" + column + "' is not a column of '" + table + "' that '" + assemblyName +
               "' can bind. Available: " + String.join(", ", available) + ".");
         }

         resolved.add(found);
      }

      return resolved;
   }

   private static OutputColumnRefModel[] columnRefs(String table, List<BindableField> fields) {
      OutputColumnRefModel[] refs = new OutputColumnRefModel[fields.size()];

      for(int i = 0; i < fields.size(); i++) {
         refs[i] = columnRef(table, fields.get(i));
      }

      return refs;
   }

   /**
    * Builds the same {@code OutputColumnRefModel} shape the property dialogs read a selection's
    * column back into — entity/attribute split on ':', matching
    * {@code BindableFieldsService.fieldOf}'s own "Customer:Region" convention for a logical
    * model's entities, and a bare attribute for a plain table column.
    */
   private static OutputColumnRefModel columnRef(String table, BindableField field) {
      OutputColumnRefModel ref = new OutputColumnRefModel();
      ref.setTable(table);
      String column = field.column();
      int colon = column.indexOf(':');

      if(colon >= 0) {
         ref.setEntity(column.substring(0, colon));
         ref.setAttribute(column.substring(colon + 1));
      }
      else {
         ref.setAttribute(column);
      }

      ref.setName(field.column());
      ref.setDataType(field.dataType() == null ? XSchema.STRING : field.dataType());
      return ref;
   }

   /**
    * Mirrors {@code AddFilterService.createFilterAssembly}'s range-type inference, so a caller
    * does not have to know a range slider's numeric range-type vocabulary just to bind a column.
    */
   private static int inferRangeType(String dataType) {
      if(XSchema.isNumericType(dataType)) {
         return TimeInfo.NUMBER;
      }
      else if(XSchema.TIME.equals(dataType)) {
         return TimeInfo.MINUTE_OF_DAY;
      }

      return TimeInfo.MONTH;
   }

   private final ViewsheetSessionService sessions;
   private final BindableFieldsService fieldsService;
   private final SelectionListPropertyDialogService selectionListService;
   private final SelectionTreePropertyDialogService selectionTreeService;
   private final RangeSliderPropertyDialogService rangeSliderService;
   private final CalendarPropertyDialogService calendarService;
}
