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
    * @param columns        one or more column names, as reported by
    *                       {@code list_bindable_fields}. A selection list or calendar accepts
    *                       exactly one; a selection tree accepts one or more, in hierarchy order,
    *                       or none at all when {@code parentIdColumn}/{@code idColumn}/
    *                       {@code labelColumn} are given instead; a range slider accepts one (a
    *                       single range) or more (a composite range).
    * @param measure        selection list only — an optional aggregate/bar-chart measure column.
    *                       Ignored for every other type.
    * @param parentIdColumn selection tree only, together with {@code idColumn}/
    *                       {@code labelColumn} — builds an arbitrary-depth tree from one flat,
    *                       self-referencing table instead of a fixed {@code columns} hierarchy.
    *                       All three must be given together, and never combined with a non-empty
    *                       {@code columns}.
    * @param idColumn       see {@code parentIdColumn}.
    * @param labelColumn    see {@code parentIdColumn}.
    * @param force          discards an existing binding to a different table, the way
    *                       {@code set_table_source}'s {@code force} does.
    */
   public Map<String, Object> setSource(String sessionToken, Principal user, String assemblyName,
                                        String table, List<String> columns,
                                        List<String> additionalTables, String measure,
                                        String parentIdColumn, String idColumn,
                                        String labelColumn, boolean force, String linkUri)
      throws Exception
   {
      if(table == null || table.isBlank()) {
         throw new IllegalArgumentException(
            "set_selection_source requires 'table' — the source table's name. " +
            "list_bindable_fields reports what this assembly can bind to.");
      }

      boolean idMode = validateIdModeFields(parentIdColumn, idColumn, labelColumn);

      if(idMode && columns != null && !columns.isEmpty()) {
         throw new IllegalArgumentException(
            "set_selection_source's 'columns' cannot be combined with 'parentIdColumn'/" +
            "'idColumn'/'labelColumn' — pick one hierarchy shape per call.");
      }

      if(!idMode && (columns == null || columns.isEmpty())) {
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
         List<BindableField> resolvedColumns = resolveColumns(
            tables, assemblyName, resolvedTable, columns == null ? List.of() : columns);
         List<String> resolvedAdditional = resolveAdditionalTables(tables, assemblyName, additional);

         if(assembly instanceof SelectionListVSAssembly) {
            requireArity(assemblyName, "a selection list", resolvedColumns, 1, 1);
            SelectionListPropertyDialogModel model =
               selectionListService.getSelectionListPropertyModel(runtimeId, assemblyName, user);
            SelectionListPaneModel pane = model.getSelectionListPaneModel();
            requireRepoint(assemblyName, pane.getSelectedTable(), resolvedTable, force);
            pane.setSelectedTable(resolvedTable);
            pane.setAdditionalTables(resolvedAdditional);
            pane.setSelectedColumn(columnRef(resolvedTable, resolvedColumns.get(0)));

            if(measure != null && !measure.isBlank()) {
               pane.getSelectionMeasurePaneModel().setMeasure(measure);
            }

            selectionListService.setSelectionListPropertyModel(
               runtimeId, assemblyName, model, linkUri, user, dispatcher);
            result.put("bound", "single");
         }
         else if(assembly instanceof SelectionTreeVSAssembly) {
            SelectionTreePropertyDialogModel model =
               selectionTreeService.getSelectionTreePropertyModel(runtimeId, assemblyName, user);
            SelectionTreePaneModel pane = model.getSelectionTreePaneModel();
            requireRepoint(assemblyName, pane.getSelectedTable(), resolvedTable, force);
            pane.setSelectedTable(resolvedTable);
            pane.setAdditionalTables(resolvedAdditional);

            if(idMode) {
               BindableField resolvedParentId = resolveColumns(
                  tables, assemblyName, resolvedTable, List.of(parentIdColumn)).get(0);
               BindableField resolvedId = resolveColumns(
                  tables, assemblyName, resolvedTable, List.of(idColumn)).get(0);
               BindableField resolvedLabel = resolveColumns(
                  tables, assemblyName, resolvedTable, List.of(labelColumn)).get(0);

               pane.setMode(SelectionTreeVSAssemblyInfo.ID);
               pane.setParentId(resolvedParentId.column());
               pane.setId(resolvedId.column());
               pane.setLabel(resolvedLabel.column());
               pane.setParentIdRef(columnRef(resolvedTable, resolvedParentId));
               pane.setIdRef(columnRef(resolvedTable, resolvedId));
               pane.setLabelRef(columnRef(resolvedTable, resolvedLabel));
            }
            else {
               requireArity(assemblyName, "a selection tree", resolvedColumns, 1, null);
               // Hierarchy levels, not the id/parent-id/label mode — the shape
               // set_selection_source exposes is an ordered column list, matching TimeSlider's
               // own SingleTimeInfo/CompositeTimeInfo choice below rather than the ID-hierarchy
               // alternative.
               pane.setMode(SelectionTreeVSAssemblyInfo.COLUMN);
               pane.setSelectedColumns(columnRefs(resolvedTable, resolvedColumns));
               result.put("levels", resolvedColumns.size());
            }

            selectionTreeService.setSelectionTreePropertyModel(
               runtimeId, assemblyName, model, linkUri, user, dispatcher);
         }
         else if(assembly instanceof TimeSliderVSAssembly) {
            requireArity(assemblyName, "a range slider", resolvedColumns, 1, null);
            RangeSliderPropertyDialogModel model =
               rangeSliderService.getRangeSliderPropertyModel(runtimeId, assemblyName, user);
            RangeSliderDataPaneModel pane = model.getRangeSliderDataPaneModel();
            requireRepoint(assemblyName, pane.getSelectedTable(), resolvedTable, force);
            pane.setSelectedTable(resolvedTable);
            pane.setAdditionalTables(resolvedAdditional);
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
            pane.setAdditionalTables(resolvedAdditional);
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

   /**
    * @return true when all three ID-hierarchy fields are given. Mirrors the plugin's own
    *         client-side check ({@code selectionTools.ts:612-625}), enforced again here since
    *         this is a public HTTP endpoint any client can call directly, not only through the
    *         plugin.
    */
   private static boolean validateIdModeFields(String parentIdColumn, String idColumn,
                                               String labelColumn)
   {
      int given = (isBlank(parentIdColumn) ? 0 : 1) + (isBlank(idColumn) ? 0 : 1) +
         (isBlank(labelColumn) ? 0 : 1);

      if(given > 0 && given < 3) {
         throw new IllegalArgumentException(
            "set_selection_source's 'parentIdColumn', 'idColumn' and 'labelColumn' are " +
            "required together for a selection tree's ID-hierarchy mode.");
      }

      return given == 3;
   }

   private static boolean isBlank(String s) {
      return s == null || s.isBlank();
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
      String resolved = findByName(tables, table);

      if(resolved != null) {
         return resolved;
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' cannot bind to '" + table + "'. Available: " +
         availableNames(tables) + ". A source the assembly cannot see binds nothing and " +
         "renders an empty assembly.");
   }

   /**
    * Case-insensitive lookup shared by {@link #resolveTable} and {@link #resolveAdditionalTable}
    * — both need the same "does this name match a bindable table" match, just with different
    * error messages on a miss.
    */
   private static String findByName(List<BindableTable> tables, String name) {
      for(BindableTable candidate : tables) {
         if(candidate.name() != null && candidate.name().equalsIgnoreCase(name)) {
            return candidate.name();
         }
      }

      return null;
   }

   private static List<String> availableNames(List<BindableTable> tables) {
      List<String> names = new ArrayList<>();

      for(BindableTable candidate : tables) {
         if(candidate.name() != null) {
            names.add(candidate.name());
         }
      }

      return names;
   }

   /**
    * Canonicalizes each {@code additionalTables} entry the same way {@link #resolveTable} does
    * for {@code table}, instead of writing the raw caller-supplied strings straight onto the
    * pane model. Without this, an entry that does not match a real worksheet table-assembly name
    * (including a correctly-spelled one in the wrong case) reaches
    * {@code Viewsheet.createSelectionTables()}, which silently skips building the composite
    * selection table for the whole union with no error surfaced back to the caller.
    */
   private static List<String> resolveAdditionalTables(List<BindableTable> tables,
                                                        String assemblyName,
                                                        List<String> additionalTables)
   {
      List<String> resolved = new ArrayList<>(additionalTables.size());

      for(String additionalTable : additionalTables) {
         resolved.add(resolveAdditionalTable(tables, assemblyName, additionalTable));
      }

      return resolved;
   }

   private static String resolveAdditionalTable(List<BindableTable> tables, String assemblyName,
                                                String additionalTable)
   {
      String resolved = findByName(tables, additionalTable);

      if(resolved != null) {
         return resolved;
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' cannot add 'additionalTables' entry '" + additionalTable +
         "': it does not match a bindable table for this viewsheet. Available: " +
         availableNames(tables) + ". See list_bindable_fields.");
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
    * column back into.
    *
    * <p>Bug #76700: this used to split a logical-model column on {@code ':'} into
    * {@code entity}/{@code attribute}, matching {@code BindableFieldsService.fieldOf}'s own
    * "Customer:Region" convention for naming the column. But the tree
    * {@code getSelectionTablesTree} (and the interactive property dialog's own read-back,
    * {@code SelectionDialogService.findSelectedOutputColumnRefModel}) builds for a logical-model
    * column leaves {@code entity} {@code null} and puts the whole compound string
    * ({@code "Customer:Region"}) in {@code attribute} — the same shape
    * {@code AssetEventUtil}/{@code VSEventUtil} use for every logical-model column entry
    * server-side. Splitting here produced an {@code AttributeRef("Customer", "Region")} whose bare
    * {@code getAttribute()} ({@code "Region"}) never equals the tree's {@code "Customer:Region"},
    * so the read-back match always failed and {@code selectedColumn} came back {@code null} on
    * every affected assembly. Never split: the full column string is the attribute, matching what
    * a human's own selection binding round-trips through this same tree.
    */
   private static OutputColumnRefModel columnRef(String table, BindableField field) {
      OutputColumnRefModel ref = new OutputColumnRefModel();
      ref.setTable(table);
      String column = field.column();
      ref.setAttribute(column);
      ref.setName(column);
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
