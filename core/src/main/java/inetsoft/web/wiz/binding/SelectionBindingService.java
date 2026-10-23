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
import inetsoft.uql.viewsheet.internal.SelectionVSUtil;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.CalendarPropertyDialogService;
import inetsoft.web.composer.vs.dialog.DataOutputService;
import inetsoft.web.composer.vs.dialog.RangeSliderPropertyDialogService;
import inetsoft.web.composer.vs.dialog.SelectionListPropertyDialogService;
import inetsoft.web.composer.vs.dialog.SelectionTreePropertyDialogService;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.viewsheet.SelectionRuntimeService;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
                                  CalendarPropertyDialogService calendarService,
                                  DataOutputService dataOutputService)
   {
      this.sessions = sessions;
      this.fieldsService = fieldsService;
      this.selectionListService = selectionListService;
      this.selectionTreeService = selectionTreeService;
      this.rangeSliderService = rangeSliderService;
      this.calendarService = calendarService;
      this.dataOutputService = dataOutputService;
   }

   /**
    * @param columns  one or more column names, as reported by {@code list_bindable_fields}. A
    *                 selection list or calendar accepts exactly one; a selection tree accepts one
    *                 or more, in hierarchy order; a range slider accepts one (a single range) or
    *                 more (a composite range). Not used — and refused if non-empty — when
    *                 {@code parentIdColumn}/{@code idColumn}/{@code labelColumn} are given instead
    *                 (a selection tree's ID-hierarchy mode).
    * @param additionalTables  extra tables the selection also filters. Null (omitted) keeps the
    *                 assembly's existing additional tables; an empty list clears them. A rebind
    *                 that did not resend them used to silently drop them. Each table must have
    *                 every bound column (same name, mergeable type) or it is refused; an
    *                 inherited one that no longer fits is dropped and disclosed. An ID-mode tree
    *                 takes none (#78473).
    * @param measure  selection list or tree — an optional aggregate/bar-chart measure column. A
    *                 non-blank value is refused for a range slider or calendar, which have no
    *                 measure. A literal column name is checked against the same list
    *                 the property dialog's measure dropdown offers (columns shared by the table and
    *                 every additional table) and stored under its canonical name; an unknown one is
    *                 refused. A {@code "$…"} or {@code "=…"} value (a StyleBI {@code DynamicValue}
    *                 {@code "$(ComponentName)"} reference, or an expression) is forwarded
    *                 untouched, as the dialog does: the caller (the plugin) is responsible for
    *                 validating a {@code "$(ComponentName)"} resolves to a real,
    *                 dynamic-reference-capable assembly before this method ever sees it (#76768).
    *                 A blank value clears the stored measure. When omitted, a stored literal
    *                 measure the final table set no longer offers is cleared and disclosed in
    *                 {@code droppedMeasure} (#78472).
    * @param parentIdColumn  selection tree only — the column holding each row's parent's
    *                        {@code idColumn} value, for an arbitrary-depth tree built from one
    *                        flat, self-referencing table ({@code SelectionTreeVSAssemblyInfo.ID}
    *                        mode) instead of {@code columns}' fixed hierarchy levels. Required
    *                        together with {@code idColumn}/{@code labelColumn}; refused alongside
    *                        a non-empty {@code columns}. As of #79252, also accepts a
    *                        {@code "$(ComponentName)"} dynamic reference, forwarded untouched the
    *                        same way {@code measure} already is — see
    *                        {@code resolveIdModeColumnRef}'s doc for the mechanism that makes this
    *                        safe.
    * @param idColumn  selection tree only — the column holding each row's own unique key. See
    *                  {@code parentIdColumn}.
    * @param labelColumn  selection tree only — the column holding each row's displayed label. See
    *                     {@code parentIdColumn}.
    * @param force    discards an existing binding to a different table, the way
    *                 {@code set_table_source}'s {@code force} does.
    * @return the bound shape. A column-mode selection tree whose stored single-select levels name
    *         a column the rebind no longer binds also reports {@code droppedSingleSelectionLevels}
    *         (those names) and {@code singleSelectionLevels} (the levels still single-select;
    *         empty means the tree is now single-select as a whole, one value in the entire tree)
    *         (#79168).
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

      boolean idMode = requireIdModeAllOrNothing(parentIdColumn, idColumn, labelColumn);

      if(idMode && columns != null && !columns.isEmpty()) {
         throw new IllegalArgumentException(
            "set_selection_source's 'columns' cannot be combined with 'parentIdColumn'/" +
            "'idColumn'/'labelColumn' — pick one hierarchy shape per call.");
      }

      if(!idMode && (columns == null || columns.isEmpty())) {
         throw new IllegalArgumentException(
            "set_selection_source requires at least one column in 'columns', or " +
            "'parentIdColumn'/'idColumn'/'labelColumn' for a selection tree's ID-hierarchy mode.");
      }

      List<String> columnsOrEmpty = columns == null ? List.of() : columns;
      // Null means "not sent": keep what the assembly already has rather than clearing it.
      List<String> additional = additionalTables == null ? List.of() : additionalTables;
      boolean replaceAdditional = additionalTables != null;
      Map<String, Object> result = new LinkedHashMap<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         SelectionVSAssembly assembly = SelectionRuntimeService.requireSelection(rvs, assemblyName);

         // Unscoped, not scoped to assemblyName: VSBindingTreeService.getBinding only builds a
         // tree for a ChartVSAssemblyInfo or TableDataVSAssemblyInfo — a SelectionVSAssemblyInfo
         // is neither, so a scoped call returns an empty listing for every one of these four
         // types. The unscoped call reads the same worksheet-wide tree that any fresh, unbound
         // table or crosstab would also see before it has a source of its own.
         List<BindableTable> tables = fieldsService.list(runtimeId, null, user);
         String resolvedTable = resolveTable(tables, assemblyName, table);
         List<BindableField> resolvedColumns =
            resolveColumns(tables, assemblyName, resolvedTable, columnsOrEmpty);
         List<String> resolvedAdditional = resolveAdditionalTables(tables, assemblyName, additional);
         refuseOverlap(assemblyName, resolvedTable, resolvedAdditional);

         if(assembly instanceof SelectionListVSAssembly) {
            requireArity(assemblyName, "a selection list", resolvedColumns, 1, 1);
            SelectionListPropertyDialogModel model =
               selectionListService.getSelectionListPropertyModel(runtimeId, assemblyName, user);
            SelectionListPaneModel pane = model.getSelectionListPaneModel();
            requireRepoint(assemblyName, tables, pane.getSelectedTable(), resolvedTable, force);
            pane.setSelectedTable(resolvedTable);

            List<String> finalAdditional = finalAdditional(
               tables, assemblyName, resolvedTable, resolvedColumns, false, replaceAdditional,
               resolvedAdditional, pane.getAdditionalTables(), result);

            if(finalAdditional != null) {
               pane.setAdditionalTables(finalAdditional);
            }

            result.put("additionalTables", additionalOrEmpty(pane.getAdditionalTables()));

            pane.setSelectedColumn(columnRef(resolvedTable, resolvedColumns.get(0)));

            applyMeasure(pane.getSelectionMeasurePaneModel(), runtimeId, user, assemblyName,
                         resolvedTable, pane.getAdditionalTables(), measure, result);

            selectionListService.setSelectionListPropertyModel(
               runtimeId, assemblyName, model, linkUri, user, dispatcher);
            result.put("bound", "single");
         }
         else if(assembly instanceof SelectionTreeVSAssembly treeAssembly) {
            SelectionTreePropertyDialogModel model =
               selectionTreeService.getSelectionTreePropertyModel(runtimeId, assemblyName, user);
            SelectionTreePaneModel pane = model.getSelectionTreePaneModel();
            requireRepoint(assemblyName, tables, pane.getSelectedTable(), resolvedTable, force);
            pane.setSelectedTable(resolvedTable);

            // An ID-mode tree takes no additional tables: the Composer clears them when it
            // switches a tree to ID mode (selection-tree-pane switchType).
            List<String> finalAdditional = finalAdditional(
               tables, assemblyName, resolvedTable, resolvedColumns, idMode, replaceAdditional,
               resolvedAdditional, pane.getAdditionalTables(), result);

            if(finalAdditional != null) {
               pane.setAdditionalTables(finalAdditional);
            }

            result.put("additionalTables", additionalOrEmpty(pane.getAdditionalTables()));

            // Resolved here, once for both modes, after the final additional-table set is known
            // and before anything is applied, so an unknown measure throws without a partial
            // write.
            applyMeasure(pane.getSelectionMeasurePaneModel(), runtimeId, user, assemblyName,
                         resolvedTable, pane.getAdditionalTables(), measure, result);

            if(idMode) {
               // #76768/#79252: an arbitrary-depth tree from one flat, self-referencing table,
               // distinct from the fixed-hierarchy-levels COLUMN mode below. A literal column is
               // resolved and canonicalized the same way it always was; a "$(ComponentName)"
               // dynamic reference is now forwarded untouched, exactly like `measure` -- see
               // resolveIdModeColumnRef's doc for the mechanism that makes this safe.
               ResolvedIdColumn parentId = resolveIdModeColumnRef(
                  tables, assemblyName, resolvedTable, "parentIdColumn", parentIdColumn);
               ResolvedIdColumn id = resolveIdModeColumnRef(
                  tables, assemblyName, resolvedTable, "idColumn", idColumn);
               ResolvedIdColumn label = resolveIdModeColumnRef(
                  tables, assemblyName, resolvedTable, "labelColumn", labelColumn);

               // Persist the canonicalized names (parentId.value(), etc.) for a literal column --
               // dataRefs (parentId.ref()/id.ref()/label.ref(), built off the resolved
               // BindableField) already hold the canonical DB casing, and
               // SelectionTreeVSAQuery2.refreshSelectionValue0 matches parentId/id/label against
               // dataRefs by name with a case-sensitive Tool.equals. Persisting the raw input here
               // instead would silently break a case-mismatched call (e.g. "state_id" for a real
               // "STATE_ID" column) exactly the way Bug #76747 did for additionalTables before
               // resolveAdditionalTables started returning the canonical name. A dynamic value has
               // no column to canonicalize -- its raw "$(ComponentName)" string is stored as-is,
               // same as `measure`.
               pane.setMode(SelectionTreeVSAssemblyInfo.ID);
               pane.setParentId(parentId.value());
               pane.setId(id.value());
               pane.setLabel(label.value());

               // A literal column's Ref model seeds dataRefs so the tree already has a matching
               // column at bind time. A dynamic reference has none to seed with, so the pane's own
               // existing Ref (if any -- e.g. a previous literal pick, left over from before this
               // field became dynamic) is left untouched, exactly as the Composer's own
               // selection-tree-id-pane.component.ts does for the identical case:
               // SelectionTreeVSAssemblyInfo.update()/restoreRefs() additively rebuilds dataRefs
               // from the live-resolved value on the very next refresh regardless (and, per
               // ViewsheetSandbox.executeView's unconditional refreshMetaData call, on first
               // render too), so there is nothing unsafe about leaving it alone here.
               if(parentId.ref() != null) {
                  pane.setParentIdRef(parentId.ref());
               }

               if(id.ref() != null) {
                  pane.setIdRef(id.ref());
               }

               if(label.ref() != null) {
                  pane.setLabelRef(label.ref());
               }

               selectionTreeService.setSelectionTreePropertyModel(
                  runtimeId, assemblyName, model, linkUri, user, dispatcher);
               result.put("parentIdColumn", parentId.value());
               result.put("idColumn", id.value());
               result.put("labelColumn", label.value());
            }
            else {
               requireArity(assemblyName, "a selection tree", resolvedColumns, 1, null);
               // Hierarchy levels, not the id/parent-id/label mode above — this shape is an
               // ordered column list, matching TimeSlider's own SingleTimeInfo/CompositeTimeInfo
               // choice below rather than the ID-hierarchy alternative.
               pane.setMode(SelectionTreeVSAssemblyInfo.COLUMN);
               pane.setSelectedColumns(columnRefs(resolvedTable, resolvedColumns));
               List<String> levelsBefore = singleSelectionLevelNames(treeAssembly);
               selectionTreeService.setSelectionTreePropertyModel(
                  runtimeId, assemblyName, model, linkUri, user, dispatcher);
               result.put("levels", resolvedColumns.size());
               // The apply replaces the assembly's info, so the survivors come from a fresh
               // lookup. The stored level names are resolved against the new columns, and one that
               // is no longer bound is lost without a trace.
               discloseDroppedSingleSelectionLevels(
                  levelsBefore,
                  singleSelectionLevelNames(
                     (SelectionTreeVSAssembly) SelectionRuntimeService.requireSelection(
                        rvs, assemblyName)),
                  result);
            }
         }
         else if(assembly instanceof TimeSliderVSAssembly) {
            refuseMeasure(assemblyName, "a range slider", measure);
            requireArity(assemblyName, "a range slider", resolvedColumns, 1, null);
            requireSingleRangeType(assemblyName, resolvedTable, resolvedColumns);
            RangeSliderPropertyDialogModel model =
               rangeSliderService.getRangeSliderPropertyModel(runtimeId, assemblyName, user);
            RangeSliderDataPaneModel pane = model.getRangeSliderDataPaneModel();
            requireRepoint(assemblyName, tables, pane.getSelectedTable(), resolvedTable, force);
            pane.setSelectedTable(resolvedTable);

            List<String> finalAdditional = finalAdditional(
               tables, assemblyName, resolvedTable, resolvedColumns, false, replaceAdditional,
               resolvedAdditional, pane.getAdditionalTables(), result);

            if(finalAdditional != null) {
               pane.setAdditionalTables(finalAdditional);
            }

            result.put("additionalTables", additionalOrEmpty(pane.getAdditionalTables()));

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
            refuseMeasure(assemblyName, "a calendar", measure);
            requireArity(assemblyName, "a calendar", resolvedColumns, 1, 1);
            CalendarPropertyDialogModel model =
               calendarService.getCalendarPropertyModel(runtimeId, assemblyName, user);
            CalendarDataPaneModel pane = model.getCalendarDataPaneModel();
            requireRepoint(assemblyName, tables, pane.getSelectedTable(), resolvedTable, force);
            pane.setSelectedTable(resolvedTable);

            List<String> finalAdditional = finalAdditional(
               tables, assemblyName, resolvedTable, resolvedColumns, false, replaceAdditional,
               resolvedAdditional, pane.getAdditionalTables(), result);

            if(finalAdditional != null) {
               pane.setAdditionalTables(finalAdditional);
            }

            result.put("additionalTables", additionalOrEmpty(pane.getAdditionalTables()));

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
      ViewsheetSessionService.relayWarnings(result, warnings);

      return result;
   }

   /**
    * Returns true (ID-hierarchy mode requested) only when all three of parentIdColumn/idColumn/
    * labelColumn are non-blank; returns false when none are given. Throws, naming exactly which
    * are present and which are missing, for a partial set — rather than silently falling back to
    * column mode or guessing at the missing ones (#76768). Mirrors the plugin's own client-side
    * check ({@code selectionTools.ts:612-625}), enforced again here since this is a public HTTP
    * endpoint any client can call directly, not only through the plugin.
    */
   private static boolean requireIdModeAllOrNothing(String parentIdColumn, String idColumn,
                                                     String labelColumn)
   {
      boolean parent = parentIdColumn != null && !parentIdColumn.isBlank();
      boolean id = idColumn != null && !idColumn.isBlank();
      boolean label = labelColumn != null && !labelColumn.isBlank();

      if(!parent && !id && !label) {
         return false;
      }

      if(parent && id && label) {
         return true;
      }

      List<String> given = new ArrayList<>();
      List<String> missing = new ArrayList<>();
      if(parent) given.add("parentIdColumn"); else missing.add("parentIdColumn");
      if(id) given.add("idColumn"); else missing.add("idColumn");
      if(label) given.add("labelColumn"); else missing.add("labelColumn");

      throw new IllegalArgumentException(
         "set_selection_source's 'parentIdColumn', 'idColumn' and 'labelColumn' are required " +
         "together — got " + String.join(", ", given) + " but not " + String.join(", ", missing) +
         ".");
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
   private static void requireRepoint(String assemblyName, List<BindableTable> tables,
                                      String currentTable, String resolvedTable, boolean force)
   {
      if(currentTable == null || currentTable.isBlank() || force) {
         return;
      }

      // Resolve the stored table the way the request was resolved, then compare exactly: Customers
      // and CUSTOMERS are two tables, so moving between them is a repoint (bug #79192).
      String live = TableNames.resolveStored(availableNames(tables), currentTable);

      if(live != null ? live.equals(resolvedTable) : currentTable.equals(resolvedTable)) {
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

   /**
    * Refuses a single-column range slider on a column whose type it cannot render (#79202):
    * only numeric (byte/short/integer/long/float/double), date, time and timeInstant columns
    * have a range type; every other type (string, boolean, char, decimal) used to be stored as
    * a MONTH range that renders empty. Runs before anything is read or applied, so a refused
    * rebind leaves the slider's existing binding untouched. A composite range (two or more
    * columns) is not checked, and a column with no known data type is allowed, as elsewhere in
    * this class. Decimal is refused because the native range slider pane does not set a range
    * type for it either ({@code XSchema.isNumericType} is false for it).
    */
   private static void requireSingleRangeType(String assemblyName, String table,
                                              List<BindableField> columns)
   {
      if(columns.size() != 1) {
         return;
      }

      BindableField field = columns.get(0);
      String type = field.dataType();

      if(type == null || XSchema.isNumericType(type) || XSchema.isDateType(type)) {
         return;
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' is a range slider bound to one column, which must be numeric, " +
         "date or time; '" + field.column() + "' is " + type + " (table '" + table + "'), so " +
         "the slider would render empty. Bind '" + field.column() + "' to a selection list " +
         "instead. The slider's existing binding was not changed.");
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
    * Table lookup shared by {@link #resolveTable} and {@link #resolveAdditionalTable}
    * — both need the same "does this name match a bindable table" match, just with different
    * error messages on a miss. An exact name wins, a unique case-only match is canonicalized, and
    * several case-only matches are refused ({@link TableNames}).
    */
   private static String findByName(List<BindableTable> tables, String name) {
      return TableNames.resolve(availableNames(tables), name);
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

   /**
    * The source table is already the first of the assembly's table names, so listing it again as
    * an additional table builds a selection union of the table with itself. The product's own
    * dialog never offers it; refuse an explicit list that does.
    */
   private static void refuseOverlap(String assemblyName, String resolvedTable,
                                     List<String> resolvedAdditional)
   {
      for(String additionalTable : resolvedAdditional) {
         if(additionalTable.equals(resolvedTable)) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' cannot use 'additionalTables' entry '" + additionalTable +
               "': it is the source table itself ('" + resolvedTable + "'). " +
               "additionalTables lists only the other tables sharing the filter.");
         }
      }
   }

   /**
    * Decides the additional tables the assembly ends up with, so the selection union
    * ({@code Viewsheet.createSelectionTables} → {@code findMutualColumnRefs}) still contains every
    * bound column. The union keeps only the columns every table shares
    * ({@code SelectionVSUtil.areColumnsCompatible}); an additional table without a bound column
    * hides that column and the selection shows another column's values (#78473). The Composer's
    * dialog never offers such a table, and drops it on every column change.
    *
    * <ul>
    *   <li>Explicit ({@code replace}): an ID-mode tree ({@code noneAllowed}) refuses a non-empty
    *       list; otherwise an entry lacking a compatible column for any bound column is refused,
    *       naming the compatible tables. Nothing is applied before the throw.</li>
    *   <li>Inherited (the field was omitted): drops, and discloses in
    *       {@code droppedAdditionalTables}, an entry that is the source table itself (#78213), any
    *       entry of an ID-mode tree, an entry that is not a bindable table here, and an entry
    *       lacking a compatible bound column.</li>
    * </ul>
    *
    * @return the list to set, or null to leave the pane's list unchanged.
    */
   private static List<String> finalAdditional(List<BindableTable> tables, String assemblyName,
                                               String resolvedTable, List<BindableField> bound,
                                               boolean noneAllowed, boolean replace,
                                               List<String> explicit, List<String> inherited,
                                               Map<String, Object> result)
   {
      if(replace) {
         if(noneAllowed && !explicit.isEmpty()) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is a selection tree in ID-hierarchy mode, which cannot use " +
               "'additionalTables' (the Composer clears them in this mode). Omit it or pass [].");
         }

         for(String additionalTable : explicit) {
            BindableTable candidate = findTable(tables, additionalTable);
            List<String> missing = candidate == null
               ? boundNames(bound) : missingColumns(candidate, resolvedTable, bound);

            if(!missing.isEmpty()) {
               throw new IllegalArgumentException(
                  "'" + assemblyName + "' cannot use 'additionalTables' entry '" +
                  additionalTable + "': it has no column compatible with " +
                  String.join(", ", missing) + " (same name, mergeable type). A selection " +
                  "filters its additional tables on the bound column(s), so such a table would " +
                  "hide them and show another column's values. Compatible tables: " +
                  compatibleOrNone(tables, resolvedTable, bound) + ".");
            }
         }

         return explicit;
      }

      if(inherited == null) {
         return null;
      }

      List<String> kept = new ArrayList<>();
      List<String> dropped = new ArrayList<>();
      boolean changed = false;

      for(String additionalTable : inherited) {
         if(additionalTable == null) {
            // A null entry builds nothing; drop it without listing a null to the caller.
            changed = true;
            continue;
         }

         BindableTable candidate = findTable(tables, additionalTable);

         if(candidate != null && resolvedTable.equals(candidate.name()) || noneAllowed ||
            candidate == null || !missingColumns(candidate, resolvedTable, bound).isEmpty())
         {
            dropped.add(additionalTable);
            changed = true;
         }
         else {
            kept.add(additionalTable);
         }
      }

      if(!dropped.isEmpty()) {
         result.put("droppedAdditionalTables", dropped);
      }

      return changed ? kept : null;
   }

   /**
    * Lookup that returns null, never throws, for an unknown or case-ambiguous name (the entries it
    * serves are dropped or refused by the caller, which has its own message).
    */
   private static BindableTable findTable(List<BindableTable> tables, String name) {
      try {
         return TableNames.resolveTable(tables, name);
      }
      catch(IllegalArgumentException e) {
         return null;
      }
   }

   /**
    * The bound columns {@code candidate} has no compatible column for, by the selection union's
    * own rule ({@code SelectionVSUtil.areColumnsCompatible}): exact, case-sensitive name, a
    * mergeable data type ({@code columnRef} defaults a null type to string on both sides), same
    * cube bit. The bound ref goes first, and candidate fields without a column are skipped, so a
    * null name can never reach {@code getName().equals}.
    */
   private static List<String> missingColumns(BindableTable candidate, String resolvedTable,
                                              List<BindableField> bound)
   {
      List<String> missing = new ArrayList<>();
      List<BindableField> fields = candidate.fields() == null ? List.of() : candidate.fields();

      for(BindableField boundField : bound) {
         OutputColumnRefModel boundRef = columnRef(resolvedTable, boundField);
         boolean found = false;

         for(BindableField field : fields) {
            if(field.column() != null && SelectionVSUtil.areColumnsCompatible(
               boundRef, columnRef(candidate.name(), field)))
            {
               found = true;
               break;
            }
         }

         if(!found) {
            missing.add(boundField.column());
         }
      }

      return missing;
   }

   private static String compatibleOrNone(List<BindableTable> tables, String resolvedTable,
                                          List<BindableField> bound)
   {
      List<String> names = new ArrayList<>();

      for(BindableTable candidate : tables) {
         if(candidate.name() != null && !candidate.name().equals(resolvedTable) &&
            missingColumns(candidate, resolvedTable, bound).isEmpty())
         {
            names.add(candidate.name());
         }
      }

      return names.isEmpty() ? "none" : String.join(", ", names);
   }

   private static List<String> boundNames(List<BindableField> bound) {
      List<String> names = new ArrayList<>();

      for(BindableField field : bound) {
         names.add(field.column());
      }

      return names;
   }

   private static List<String> additionalOrEmpty(List<String> additionalTables) {
      return additionalTables == null ? List.of() : additionalTables;
   }

   private static void refuseMeasure(String assemblyName, String kind, String measure) {
      if(measure != null && !measure.isBlank()) {
         throw new IllegalArgumentException(
            "'measure' is not supported for " + kind + " ('" + assemblyName +
            "') — selection list or tree only. Omit it.");
      }
   }

   private String resolveMeasure(String runtimeId, Principal user, String assemblyName,
                                 String table, List<String> additionalTables, String measure)
      throws Exception
   {
      return SelectionMeasureResolver.resolve(
         dataOutputService, runtimeId, user, assemblyName, table, additionalTables, measure);
   }

   private static List<BindableField> resolveColumns(List<BindableTable> tables,
                                                      String assemblyName, String table,
                                                      List<String> columns)
   {
      List<BindableField> fields = List.of();

      for(BindableTable candidate : tables) {
         // `table` is a resolved name; Customers and CUSTOMERS are different tables (bug #79192).
         if(candidate.name().equals(table)) {
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

   /**
    * One resolved ID-hierarchy-mode column (parentIdColumn/idColumn/labelColumn): {@code value} is
    * what {@code SelectionTreePaneModel.setParentId}/{@code setId}/{@code setLabel} store (the
    * canonical column name for a literal, or the raw {@code "$(ComponentName)"} string for a
    * dynamic reference, unchanged), and {@code ref} is the {@code OutputColumnRefModel}
    * {@code SelectionTreePropertyDialogService.setAssemblyInfoDataRefs} needs to seed the tree's
    * {@code dataRefs} with — null for a dynamic reference, which has no literal column to seed.
    */
   private record ResolvedIdColumn(String value, OutputColumnRefModel ref) {
   }

   /**
    * Resolves one ID-hierarchy-mode column (parentIdColumn/idColumn/labelColumn).
    *
    * <p>#76768 originally refused a {@code "$(ComponentName)"} dynamic reference here outright, on
    * the premise that {@code SelectionTreeVSAQuery2.refreshSelectionValue0} locates the
    * id/parentId/label columns by matching {@code assembly.getDataRefs()[i].getName()} against the
    * live-resolved {@code assembly.getID()}/{@code getParentID()}/{@code getLabel()}, and that
    * {@code dataRefs} is built once, at bind time, from a literal column only — so a dynamic
    * reference could never find a matching index at runtime. #79252 found that premise incomplete:
    * {@code SelectionTreeVSAssemblyInfo.update()}/{@code restoreRefs()} additively rebuilds
    * {@code dataRefs} from the table's live column selection, keyed on the live-resolved getters,
    * on every ordinary input-driven refresh (and, per {@code ViewsheetSandbox.executeView}'s
    * unconditional {@code refreshMetaData} call, on first render too) — before the selection query
    * runs. So a dynamic reference is forwarded untouched here, exactly like {@code measure}
    * already is: its raw string becomes the pane's {@code DynamicValue}, and no
    * {@code OutputColumnRefModel} is produced for it, since there is no literal column to seed
    * {@code dataRefs} with — the caller leaves the pane's existing ref (if any) untouched for that
    * case, matching the Composer's own {@code selection-tree-id-pane.component.ts}.
    */
   private static ResolvedIdColumn resolveIdModeColumnRef(List<BindableTable> tables,
                                                           String assemblyName, String table,
                                                           String fieldName, String value)
   {
      // Deliberately not VSUtil.isVariableValue: that class's static initializer reaches into a
      // live Spring application context, which SelectionBindingServiceTest's plain-Mockito setup
      // does not provide -- pulling it in here would break every idMode unit test with a
      // NoClassDefFoundError/ExceptionInInitializerError instead of a clean assertion failure. This
      // mirrors SelectionMeasureResolver.resolve's own plain prefix/suffix check for the identical
      // reason.
      String trimmed = value.trim();

      if(trimmed.startsWith("$(") && trimmed.endsWith(")")) {
         if(trimmed.substring(2, trimmed.length() - 1).isBlank()) {
            throw new IllegalArgumentException(
               "set_selection_source's '" + fieldName + "' \"" + trimmed + "\" needs a component " +
               "name between the parentheses, e.g. \"$(TextInput1)\".");
         }

         return new ResolvedIdColumn(trimmed, null);
      }

      List<BindableField> resolved = resolveColumns(tables, assemblyName, table, List.of(value));
      BindableField field = resolved.get(0);
      return new ResolvedIdColumn(field.column(), columnRef(table, field));
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

   private static List<String> singleSelectionLevelNames(SelectionTreeVSAssembly tree) {
      SelectionTreeVSAssemblyInfo info = tree.getSelectionTreeInfo();

      // Levels are meaningless in ID mode, yet the getter falls back to the ID columns' names there.
      return info == null || info.getMode() != SelectionTreeVSAssemblyInfo.COLUMN
         ? null : info.getSingleSelectionLevelNames();
   }

   /**
    * Discloses the single-select levels a column-mode rebind removed (#79168). With
    * {@code singleSelection} on and no level left the tree is single-select as a whole, so the
    * remaining levels are reported alongside the dropped ones.
    */
   private static void discloseDroppedSingleSelectionLevels(List<String> before,
                                                            List<String> after,
                                                            Map<String, Object> result)
   {
      if(before == null) {
         return;
      }

      List<String> remaining = after == null ? List.of() : after;
      List<String> dropped = before.stream().filter(name -> !remaining.contains(name)).toList();

      if(!dropped.isEmpty()) {
         result.put("droppedSingleSelectionLevels", dropped);
         result.put("singleSelectionLevels", remaining);
      }
   }

   /**
    * Writes a selection list's or tree's measure from the final table set (#78472). The pane is
    * pre-filled with the stored measure, which used to be written back unchanged unless
    * {@code measure} was non-blank: a repoint or an additional-table change left a measure the
    * table set no longer offers, and {@code ""} could not clear it.
    *
    * <ul>
    *   <li>Non-blank: resolved by {@link #resolveMeasure}, unchanged (throws on an unknown one).</li>
    *   <li>Sent but blank: cleared; {@code clearedMeasure} names the old value.</li>
    *   <li>Omitted: a stored dynamic value ({@code $(…)}/{@code =…}, resolveMeasure's test) is
    *       kept. A stored literal is re-checked against the measure dropdown on every call, as
    *       the dialog does on every open; one it no longer offers is cleared and named in
    *       {@code droppedMeasure}. A failed lookup keeps the measure (fail open).</li>
    * </ul>
    */
   private void applyMeasure(SelectionMeasurePaneModel measurePane, String runtimeId,
                             Principal user, String assemblyName, String table,
                             List<String> additionalTables, String measure,
                             Map<String, Object> result)
      throws Exception
   {
      if(measure != null) {
         if(!measure.isBlank()) {
            measurePane.setMeasure(resolveMeasure(
               runtimeId, user, assemblyName, table, additionalTables, measure));
            return;
         }

         String old = measurePane.getMeasure();
         measurePane.setMeasure(null);

         if(old != null && !old.isBlank()) {
            result.put("clearedMeasure", old);
         }

         return;
      }

      String stored = measurePane.getMeasure();

      if(stored == null || stored.isBlank() || isDynamicMeasure(stored)) {
         return;
      }

      String offered;

      try {
         offered = offeredMeasure(runtimeId, user, table, additionalTables, stored);
      }
      catch(Exception e) {
         // The caller never mentioned the measure, so a failed lookup must not fail the rebind.
         LOG.debug("Could not re-check the stored measure '{}' of '{}'", stored, assemblyName, e);
         return;
      }

      if(offered == null) {
         measurePane.setMeasure(null);
         result.put("droppedMeasure", stored);
      }
   }

   /** resolveMeasure's dynamic test: {@code $(…)} or {@code =…}. A bare {@code $Foo} is literal. */
   private static boolean isDynamicMeasure(String measure) {
      String trimmed = measure.trim();
      return (trimmed.startsWith("$(") && trimmed.endsWith(")")) || trimmed.startsWith("=");
   }

   /**
    * The measure dropdown's candidate matching {@code literal} (case-insensitive), or null when
    * the dropdown does not offer it. Unlike {@link #resolveMeasure}, never refuses.
    */
   private String offeredMeasure(String runtimeId, Principal user, String table,
                                 List<String> additionalTables, String literal)
      throws Exception
   {
      return SelectionMeasureResolver.offered(
         dataOutputService, runtimeId, user, table, additionalTables, literal);
   }

   private static final Logger LOG = LoggerFactory.getLogger(SelectionBindingService.class);

   private final ViewsheetSessionService sessions;
   private final BindableFieldsService fieldsService;
   private final SelectionListPropertyDialogService selectionListService;
   private final SelectionTreePropertyDialogService selectionTreeService;
   private final RangeSliderPropertyDialogService rangeSliderService;
   private final CalendarPropertyDialogService calendarService;
   private final DataOutputService dataOutputService;
}
