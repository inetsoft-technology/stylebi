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
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.DataRefWrapper;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.model.vs.VSTableTrapModel;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.event.InsertSelectionChildEvent;
import inetsoft.web.wiz.binding.BindableFieldsService.AggregateSource;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Inserts a filter for a named column into an existing Selection Container in one step -- the
 * Composer's "Add Filter" / drop-a-column-on-the-container gesture ({@code
 * VSBindingService.insertChild}'s {@code columns} branch), which picks the filter type itself
 * (a range slider for a number/date/time column, a selection list for everything else) and, in
 * the UI, runs the selection trap check first.
 *
 * <p>{@code insertChild} validates nothing and reports nothing: a typo'd column makes an unbound
 * filter, a duplicate column is silently skipped, and the created names are not returned. So this
 * service resolves the columns against the bindable listing first, refuses a duplicate itself
 * (comparing a normalized table + column key, because StyleBI's own guard compares {@code DataRef}
 * names and misses a logical-model column spelled {@code entity.attribute} versus the compound
 * {@code entity:attribute}), and diffs the container's children afterwards to report what was
 * created.
 *
 * <p>Every read-only check, including the trap check, runs outside {@code mutate}: a refusal
 * thrown inside it would still checkpoint, bump the write revision and broadcast.
 */
@Service
public class SelectionContainerFilterService {
   @Autowired
   public SelectionContainerFilterService(ViewsheetSessionService sessions,
                                          BindableFieldsService fieldsService,
                                          VSBindingService bindingService,
                                          QueryManagerService queryManagerService)
   {
      this.sessions = sessions;
      this.fieldsService = fieldsService;
      this.bindingService = bindingService;
      this.queryManagerService = queryManagerService;
   }

   /**
    * @param assembly  the Selection Container
    * @param columns   one filter per column, in order
    * @param table     the source table; omitted, it is inferred when exactly one table carries
    *                  every column
    * @param index     the first new filter's position (0..children, default append)
    * @param confirmed proceed although the selection trap check flags the new filters
    */
   public record Request(String assembly, List<String> columns, String table, Integer index,
                         Boolean confirmed) {}

   /** {@code type} is "Selection List" or "Range Slider". */
   public record CreatedFilter(String name, String type, String column, String table) {}

   public record Trap(boolean detected, boolean confirmed) {}

   public record Result(String container, int index, List<CreatedFilter> created, Trap trap,
                        List<String> warnings) {}

   public Result addFilters(String sessionToken, Principal user, Request request, String linkUri)
      throws Exception
   {
      String containerName = request.assembly();

      if(containerName == null || containerName.isBlank()) {
         throw new IllegalArgumentException(
            "add_filter_to_container requires 'assembly' -- the Selection Container's name.");
      }

      List<String> requested = request.columns();

      if(requested == null || requested.isEmpty()) {
         throw new IllegalArgumentException(
            "add_filter_to_container requires 'columns' -- at least one column name to add a " +
            "filter for.");
      }

      for(String column : requested) {
         if(column == null || column.isBlank()) {
            throw new IllegalArgumentException(
               "add_filter_to_container 'columns' contains a blank column name.");
         }
      }

      boolean confirmed = Boolean.TRUE.equals(request.confirmed());

      // Reads only: nothing below until the insert checkpoints, broadcasts or bumps a revision.
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      String runtimeId = sessions.runtimeId(sessionToken, user);
      Viewsheet vs = rvs.getViewsheet();
      CurrentSelectionVSAssembly container = requireContainer(vs, containerName);
      List<String> children = childrenOf(container);
      int size = children.size();
      int index = request.index() == null ? size : request.index();

      if(index < 0 || index > size) {
         throw new IllegalArgumentException(
            "'index' " + index + " is out of range for Selection Container '" + containerName +
            "', which has " + size + " child" + (size == 1 ? "" : "ren") + " -- use 0.." + size +
            " (" + size + " appends).");
      }

      List<BindableTable> all = fieldsService.list(runtimeId, null, user);
      List<BindableTable> tables = new ArrayList<>();

      for(BindableTable candidate : all) {
         if(!candidate.isComponent() && !candidate.isAggregate()) {
            tables.add(candidate);
         }
      }

      String table = resolveSourceTable(tables, runtimeId, user, containerName, request.table(),
                                        requested);
      List<BindableField> fields =
         SelectionBindingService.resolveColumns(tables, containerName, table, requested);
      Set<String> seen = new HashSet<>();

      for(BindableField field : fields) {
         if(!seen.add(field.column().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
               "'" + field.column() + "' is listed more than once in 'columns'.");
         }
      }

      requireNotAlreadyFiltered(vs, container, table, fields);
      queryManagerService.checkCubeTableReadPermission(table, user);

      OutputColumnRefModel[] refs = new OutputColumnRefModel[fields.size()];

      for(int i = 0; i < refs.length; i++) {
         refs[i] = SelectionBindingService.columnRef(table, fields.get(i));
      }

      InsertSelectionChildEvent event = new InsertSelectionChildEvent();
      event.setName(container.getAbsoluteName());
      event.setToIndex(index);
      event.setColumn(refs);

      VSTableTrapModel trapModel = bindingService.checkVSSelectionTrap(runtimeId, event, user);
      boolean trapDetected = trapModel != null && trapModel.showTrap();

      if(trapDetected && !confirmed) {
         throw new IllegalArgumentException(
            "Adding " + quoteAll(requested) + " to Selection Container '" + containerName +
            "' would create a selection trap: the new filter(s), combined with the data model's " +
            "relationships, can make the filtered data misleading (the Composer asks for " +
            "confirmation here). Nothing was changed. Re-run with confirmed:true to add them " +
            "anyway.");
      }

      List<CreatedFilter> created = new ArrayList<>();
      List<String> warnings = sessions.mutate(sessionToken, user, (r, rtId, dispatcher) -> {
         Viewsheet sheet = r.getViewsheet();
         CurrentSelectionVSAssembly live = requireContainer(sheet, containerName);
         // The human may have added the same column since the reads above.
         requireNotAlreadyFiltered(sheet, live, table, fields);
         Set<String> before = new LinkedHashSet<>(childrenOf(live));

         bindingService.insertChild(rtId, event, linkUri, user, dispatcher, true);

         List<String> added = new ArrayList<>();

         for(String child : childrenOf(requireContainer(sheet, containerName))) {
            if(!before.contains(child)) {
               added.add(child);
            }
         }

         created.addAll(createdFilters(sheet, added, table, fields));
      });

      return new Result(containerName, index, created, new Trap(trapDetected,
                        trapDetected && confirmed), warnings);
   }

   private static List<CreatedFilter> createdFilters(Viewsheet vs, List<String> added,
                                                     String table, List<BindableField> fields)
   {
      List<CreatedFilter> created = new ArrayList<>();
      Set<String> used = new HashSet<>();

      for(BindableField field : fields) {
         AbstractSelectionVSAssembly match = null;

         for(String name : added) {
            if(used.contains(name)) {
               continue;
            }

            if(vs.getAssembly(name) instanceof AbstractSelectionVSAssembly candidate &&
               filters(candidate, table, field.column()))
            {
               match = candidate;
               break;
            }
         }

         if(match == null) {
            throw new IllegalStateException(
               "StyleBI did not create a filter for '" + field.column() + "' of '" + table +
               "' (the insert skipped it). Created: " + added + ". Check get_layout and the " +
               "container's children before retrying.");
         }

         used.add(match.getName());
         created.add(new CreatedFilter(match.getAbsoluteName(), typeName(match), field.column(),
                                       table));
      }

      if(added.size() != fields.size()) {
         throw new IllegalStateException(
            "StyleBI created " + added.size() + " filter(s) " + added + " for " + fields.size() +
            " requested column(s). Check get_layout before retrying.");
      }

      return created;
   }

   private static String typeName(AbstractSelectionVSAssembly assembly) {
      return assembly instanceof TimeSliderVSAssembly ? "Range Slider" :
         assembly instanceof SelectionListVSAssembly ? "Selection List" :
         assembly.getClass().getSimpleName().replace("VSAssembly", "");
   }

   /**
    * The worksheet table a column list is drawn from: the named one, or the only one that carries
    * every column. A chart/crosstab is refused -- the insert builds a worksheet-table filter, and
    * a component's aggregated rows are a range slider's {@code set_selection_source} path.
    */
   private String resolveSourceTable(List<BindableTable> tables, String runtimeId, Principal user,
                                     String containerName, String requestedTable,
                                     List<String> columns)
      throws Exception
   {
      if(requestedTable != null && !requestedTable.isBlank()) {
         if(TableNames.resolve(
            tables.stream().map(BindableTable::name).filter(Objects::nonNull).toList(),
            requestedTable) == null)
         {
            AggregateSource component = TableNames.resolve(
               fieldsService.aggregateSources(runtimeId, user), AggregateSource::name,
               requestedTable);

            if(component != null) {
               throw new IllegalArgumentException(
                  "add_filter_to_container cannot filter on the chart/crosstab '" +
                  component.name() + "' (its aggregated rows are not a worksheet table). Add a " +
                  "Range Slider with edit(op:'add'), bind it with set_selection_source " +
                  "(table:'" + component.name() + "', columns:[<aggregate>]) and put it in the " +
                  "container with edit(op:'add_to_selection_container').");
            }
         }

         return SelectionBindingService.resolveTable(tables, List.of(), containerName,
                                                     requestedTable);
      }

      List<String> candidates = new ArrayList<>();

      for(BindableTable candidate : tables) {
         if(candidate.name() != null && carriesAll(candidate, columns)) {
            candidates.add(candidate.name());
         }
      }

      if(candidates.size() == 1) {
         return candidates.get(0);
      }

      if(candidates.isEmpty()) {
         List<String> names = new ArrayList<>();

         for(BindableTable candidate : tables) {
            if(candidate.name() != null) {
               names.add(candidate.name());
            }
         }

         throw new IllegalArgumentException(
            "No single table carries every column of " + quoteAll(columns) + ". Pass 'table'; " +
            "tables: " + names + " (list_bindable_fields shows each one's columns).");
      }

      throw new IllegalArgumentException(
         "Columns " + quoteAll(columns) + " exist in more than one table: " + candidates +
         ". Pass 'table' to say which.");
   }

   private static boolean carriesAll(BindableTable table, List<String> columns) {
      for(String column : columns) {
         boolean found = false;

         for(BindableField field : table.fields()) {
            if(field.column() != null && field.column().equalsIgnoreCase(column)) {
               found = true;
               break;
            }
         }

         if(!found) {
            return false;
         }
      }

      return true;
   }

   private static void requireNotAlreadyFiltered(Viewsheet vs, CurrentSelectionVSAssembly container,
                                                 String table, List<BindableField> fields)
   {
      for(String child : childrenOf(container)) {
         if(!(vs.getAssembly(child) instanceof AbstractSelectionVSAssembly existing)) {
            continue;
         }

         for(BindableField field : fields) {
            if(filters(existing, table, field.column())) {
               throw new IllegalArgumentException(
                  "Selection Container '" + container.getAbsoluteName() + "' already has a " +
                  "filter on '" + field.column() + "' of '" + table + "' ('" + child + "', a " +
                  typeName(existing) + "); StyleBI skips a second filter on the same column, so " +
                  "nothing was added. Use edit(op:'remove') on '" + child + "' first to replace " +
                  "it.");
            }
         }
      }
   }

   /**
    * Whether {@code assembly} filters {@code column} of {@code table}, comparing a normalized key
    * rather than {@code DataRef} equality: the same logical-model column can be stored as
    * {@code attribute = "Customer:Region"} (the agent path), or as {@code entity = "Customer"},
    * {@code attribute = "Region"} (a Composer drag).
    */
   static boolean filters(AbstractSelectionVSAssembly assembly, String table, String column) {
      boolean sameTable = table.equalsIgnoreCase(assembly.getTableName());
      List<String> names = assembly.getTableNames();

      for(int i = 0; !sameTable && names != null && i < names.size(); i++) {
         sameTable = table.equalsIgnoreCase(names.get(i));
      }

      if(!sameTable) {
         return false;
      }

      DataRef[] refs = assembly.getDataRefs();
      String key = column.toLowerCase(Locale.ROOT);

      for(int i = 0; refs != null && i < refs.length; i++) {
         if(refKeys(refs[i]).contains(key)) {
            return true;
         }
      }

      return false;
   }

   private static Set<String> refKeys(DataRef ref) {
      Set<String> keys = new HashSet<>();

      while(ref != null) {
         String attribute = ref.getAttribute();
         String entity = ref.getEntity();
         addKey(keys, ref.getName());
         addKey(keys, attribute);

         if(entity != null && !entity.isEmpty()) {
            addKey(keys, entity + "." + attribute);
            addKey(keys, entity + ":" + attribute);
         }

         ref = ref instanceof DataRefWrapper wrapper ? wrapper.getDataRef() : null;
      }

      return keys;
   }

   private static void addKey(Set<String> keys, String key) {
      if(key != null) {
         keys.add(key.toLowerCase(Locale.ROOT));
      }
   }

   /**
    * Mirrors {@code ViewsheetEditService.requireSelectionContainer}, which is private to that
    * class and instance-bound to its own assembly lookup.
    */
   private static CurrentSelectionVSAssembly requireContainer(Viewsheet vs, String name) {
      VSAssembly assembly = vs == null ? null : (VSAssembly) vs.getAssembly(name);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + name + "'.");
      }

      if(!(assembly instanceof CurrentSelectionVSAssembly container)) {
         VSAssembly parent = assembly.getContainer();
         throw new IllegalArgumentException(
            "'" + name + "' is a " + assembly.getClass().getSimpleName().replace("VSAssembly", "") +
            ", not a Selection Container" +
            (parent instanceof CurrentSelectionVSAssembly
               ? " -- it is a child of Selection Container '" + parent.getAbsoluteName() +
                  "', pass that name as 'assembly'"
               : "") + ". edit(op:'add_filter_to_container') works on a container made by " +
            "group_as_selection_container.");
      }

      VSAssembly parent = container.getContainer();

      if(parent != null && !(parent instanceof TabVSAssembly)) {
         throw new IllegalArgumentException(
            "Selection Container '" + name + "' is inside Group '" + parent.getAbsoluteName() +
            "' -- take it out first with edit(op:'move_from_container'), then edit its children.");
      }

      return container;
   }

   private static List<String> childrenOf(CurrentSelectionVSAssembly container) {
      String[] children = container.getAssemblies();
      return children == null ? List.of() : List.of(children);
   }

   private static String quoteAll(List<String> names) {
      List<String> quoted = new ArrayList<>();

      for(String name : names) {
         quoted.add("'" + name + "'");
      }

      return quoted.toString();
   }

   private final ViewsheetSessionService sessions;
   private final BindableFieldsService fieldsService;
   private final VSBindingService bindingService;
   private final QueryManagerService queryManagerService;
}
