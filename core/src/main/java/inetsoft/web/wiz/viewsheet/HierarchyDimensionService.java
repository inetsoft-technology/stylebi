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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.model.vs.ChartPropertyDialogModel;
import inetsoft.web.composer.model.vs.CrosstabPropertyDialogModel;
import inetsoft.web.composer.model.vs.HierarchyPropertyPaneModel;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.model.vs.VSDimensionMemberModel;
import inetsoft.web.composer.model.vs.VSDimensionModel;
import inetsoft.web.composer.vs.dialog.ChartPropertyDialogService;
import inetsoft.web.composer.vs.dialog.CrosstabPropertyDialogService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * A chart or crosstab's custom drill hierarchy — the dimensions on
 * {@code hierarchyPropertyPaneModel.dimensions}: list, add, remove.
 *
 * <p>{@code dimensions} is a {@code VSDimensionModel[]}, which {@link PropertyPath#coerce} cannot
 * build from JSON — it has no bean-construction branch at all, so every JSON object array for this
 * field is refused ({@code PropertyAliases}' own refusal points here). This service is the success
 * path behind that refusal, mirroring {@link ChartTargetLineService} for the analogous
 * {@code chartTargets} field on bug #76770. An ordinary <i>sequential</i> drill — nesting
 * dimensions on a shelf in order — already works and is untouched by any of this; this service is
 * only for a <i>custom/irregular</i> hierarchy, which requires writing this field directly.
 *
 * <p><b>The landmine this service exists to avoid.</b> {@code ChartPropertyDialogService.setCube}
 * / {@code CrosstabPropertyDialogService.setCube} rebuild the assembly's ENTIRE {@code XCube} —
 * dimensions AND measures together — from {@code hierarchyPropertyPaneModel.getDimensions()} and
 * {@code .getColumnList()}, on every single property save, not just a hierarchy edit. Each
 * dimension member's column is looked up in {@code columnList} with {@code List.indexOf}, and
 * {@code OutputColumnRefModel} has no {@code equals()}/{@code hashCode()} override, so that lookup
 * is reference identity: a freshly-built {@code OutputColumnRefModel} — even one field-identical
 * to a real {@code columnList} entry — never matches, and the column meant to be a dimension
 * member is ALSO added as a duplicate measure. Worse, {@code columnList} arrives at that method
 * wrapped in {@code Arrays.asList(...)}, a fixed-size view — so the one case where the lookup
 * *does* succeed calls {@code .remove(index)} on it, which throws
 * {@code UnsupportedOperationException}. Between the two, a submitted {@code columnList} that
 * still contains a member's column cannot be handled correctly by {@code setCube} either way: a
 * miss silently double-books the column as a measure, and a hit throws.
 *
 * <p>This service never lets {@code setCube} make that call. {@link #add} and {@link #remove}
 * always submit a {@code columnList} that has ALREADY had every column used by ANY dimension — the
 * one just edited and every pre-existing one, matched by entity+attribute rather than identity,
 * since identity does not survive even a pre-existing dimension's own read/convert round trip —
 * removed up front. {@code setCube}'s own {@code indexOf} then always misses, by construction: it
 * neither throws nor double-books, because the submitted {@code columnList} already IS the correct
 * remaining-measures list. Each member's {@code dataRef} is still built from the actual
 * {@code OutputColumnRefModel} instance in the pane's own {@code columnList} (never a hand-built
 * copy) — {@code convertModelToVSDimension} only reads its fields, so this costs nothing, and it
 * keeps this service correct even if a future change stops pre-filtering {@code columnList}.
 */
@Service
public class HierarchyDimensionService {
   @Autowired
   public HierarchyDimensionService(ViewsheetSessionService sessions,
                                    ChartPropertyDialogService chartService,
                                    CrosstabPropertyDialogService crosstabService)
   {
      this.sessions = sessions;
      this.chartService = chartService;
      this.crosstabService = crosstabService;
   }

   /**
    * {@code list_hierarchy_dimensions}. The assembly's current custom hierarchy dimensions, with
    * the index each one is addressed by, plus the columns still free to build a new one from.
    */
   public Map<String, Object> list(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      Target target = resolveTarget(rvs, assemblyName);
      HierarchyPropertyPaneModel pane = target.readPane(rvs.getID(), user);

      return describe(assemblyName, pane);
   }

   /**
    * {@code add_hierarchy_dimension}. Appends one dimension whose levels are the given columns, in
    * order (outermost first).
    *
    * <p>One {@code sessions.mutate}, so one undo checkpoint.
    *
    * @param columns    column names, outermost level first. Matched against
    *                   {@code list_hierarchy_dimensions}' {@code availableColumns}.
    * @param dateLevels optional, parallel to {@code columns}: a date grouping
    *                   ({@link #DATE_LEVELS}) for the column at the same position, or null/blank
    *                   for none. Ignored positions past the end of a shorter list are treated as
    *                   null. Meaningless for a non-date column; not validated against the column's
    *                   own type, since a caller building a level from a column
    *                   {@code list_hierarchy_dimensions} has not yet seen may not know it yet.
    */
   public Map<String, Object> add(String sessionToken, Principal user, String assemblyName,
                                  List<String> columns, List<String> dateLevels, String linkUri)
      throws Exception
   {
      List<String> wanted = requireColumns(columns);
      List<Integer> levels = resolveLevels(wanted, dateLevels);
      Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         Target target = resolveTarget(rvs, assemblyName);
         Object model = target.readModel(runtimeId, user);
         HierarchyPropertyPaneModel pane = target.pane(model);
         requireColumnCatalog(pane, assemblyName);

         OutputColumnRefModel[] catalog = columns(pane);
         VSDimensionMemberModel[] members = new VSDimensionMemberModel[wanted.size()];
         Set<String> seen = new HashSet<>();

         for(int i = 0; i < wanted.size(); i++) {
            OutputColumnRefModel column = findColumn(catalog, wanted.get(i), assemblyName);
            String key = columnKey(column);

            if(!seen.add(key)) {
               throw new IllegalArgumentException(
                  "'" + wanted.get(i) + "' is listed more than once in 'columns' -- a hierarchy " +
                  "level can only use a column once.");
            }

            VSDimensionMemberModel member = new VSDimensionMemberModel();
            member.setDataRef(column);
            member.setOption(levels.get(i));
            members[i] = member;
         }

         VSDimensionModel newDimension = new VSDimensionModel();
         newDimension.setMembers(members);

         VSDimensionModel[] existing = dimensions(pane);
         VSDimensionModel[] updated = Arrays.copyOf(existing, existing.length + 1);
         updated[existing.length] = newDimension;
         pane.setDimensions(updated);
         pane.setColumnList(withoutConsumedColumns(catalog, updated));

         target.write(runtimeId, model, linkUri, user, dispatcher);

         result.put("assembly", assemblyName);
         result.put("index", existing.length);
         result.put("columns", wanted);
      });

      return result;
   }

   /**
    * {@code remove_hierarchy_dimension}. Removes the dimension at {@code index} (as reported by
    * {@link #list}) and frees its columns back into {@code availableColumns}.
    */
   public Map<String, Object> remove(String sessionToken, Principal user, String assemblyName,
                                     int index, String linkUri)
      throws Exception
   {
      Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         Target target = resolveTarget(rvs, assemblyName);
         Object model = target.readModel(runtimeId, user);
         HierarchyPropertyPaneModel pane = target.pane(model);
         VSDimensionModel[] existing = dimensions(pane);

         if(index < 0 || index >= existing.length) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' has no hierarchy dimension at index " + index + ". " +
               (existing.length == 0 ? "It has none at all."
                  : "Current indexes: 0-" + (existing.length - 1) + ".") +
               " Take indexes from list_hierarchy_dimensions -- they renumber after every " +
               "removal.");
         }

         OutputColumnRefModel[] catalog = columns(pane);
         VSDimensionModel[] updated = new VSDimensionModel[existing.length - 1];
         System.arraycopy(existing, 0, updated, 0, index);
         System.arraycopy(existing, index + 1, updated, index, existing.length - index - 1);

         pane.setDimensions(updated);
         pane.setColumnList(withoutConsumedColumns(catalog, updated));

         target.write(runtimeId, model, linkUri, user, dispatcher);

         result.put("assembly", assemblyName);
         result.put("removed", index);
         result.put("remaining", updated.length);
      });

      return result;
   }

   private static Map<String, Object> describe(String assemblyName, HierarchyPropertyPaneModel pane)
   {
      VSDimensionModel[] dims = dimensions(pane);
      OutputColumnRefModel[] catalog = columns(pane);
      List<Map<String, Object>> dimensionsOut = new ArrayList<>();

      for(int i = 0; i < dims.length; i++) {
         dimensionsOut.add(describeDimension(i, dims[i]));
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);
      out.put("dimensions", dimensionsOut);
      out.put("availableColumns", names(withoutConsumedColumns(catalog, dims)));
      out.put("isCube", pane.isCube());
      out.put("dateLevels", new ArrayList<>(DATE_LEVELS.keySet()));

      if(pane.isCube()) {
         out.put("note", "This assembly is bound to a cube/OLAP source. Its drill dimensions come " +
            "from the cube itself; add_hierarchy_dimension does not apply here.");
      }

      return out;
   }

   private static Map<String, Object> describeDimension(int index, VSDimensionModel dimension) {
      List<Map<String, Object>> membersOut = new ArrayList<>();

      for(VSDimensionMemberModel member : members(dimension)) {
         OutputColumnRefModel ref = member.getDataRef();
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("column", ref == null ? null : displayName(ref));
         out.put("dateLevel", dateLevelName(member.getOption()));
         membersOut.add(out);
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("index", index);
      out.put("members", membersOut);
      return out;
   }

   private Target resolveTarget(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(assembly instanceof ChartVSAssembly) {
         return new ChartTarget(assemblyName);
      }

      if(assembly instanceof CrosstabVSAssembly) {
         return new CrosstabTarget(assemblyName);
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() + ", not a chart " +
         "or crosstab. Hierarchy dimensions only exist on those two.");
   }

   private static void requireColumnCatalog(HierarchyPropertyPaneModel pane, String assemblyName) {
      if(pane.isCube()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is bound to a cube/OLAP source. Its drill dimensions come " +
            "from the cube itself, not from a custom hierarchy built here.");
      }

      if(columns(pane).length == 0) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' has no columns to build a hierarchy from.");
      }
   }

   private static OutputColumnRefModel findColumn(OutputColumnRefModel[] catalog, String wanted,
                                                  String assemblyName)
   {
      for(OutputColumnRefModel column : catalog) {
         if(matches(column, wanted)) {
            return column;
         }
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' has no column '" + wanted + "'. Available: " +
         String.join(", ", names(catalog)) + ". Use list_hierarchy_dimensions to see them.");
   }

   private static boolean matches(OutputColumnRefModel column, String wanted) {
      if(wanted == null) {
         return false;
      }

      String w = wanted.trim();
      return w.equalsIgnoreCase(column.getName()) || w.equalsIgnoreCase(qualifiedName(column));
   }

   private static String displayName(OutputColumnRefModel column) {
      return column.getName() != null && !column.getName().isEmpty()
         ? column.getName() : qualifiedName(column);
   }

   private static String qualifiedName(OutputColumnRefModel column) {
      return column.getEntity() == null || column.getEntity().isEmpty()
         ? column.getAttribute() : column.getEntity() + "." + column.getAttribute();
   }

   /** Identity for the purposes of "is this column already a dimension member" -- entity+attribute,
    *  since {@code OutputColumnRefModel} overrides neither {@code equals()} nor {@code hashCode()}
    *  and its instances never survive a read/convert round trip anyway. */
   private static String columnKey(OutputColumnRefModel column) {
      return (column.getEntity() == null ? "" : column.getEntity()) + " " +
         (column.getAttribute() == null ? "" : column.getAttribute());
   }

   private static List<String> names(OutputColumnRefModel[] columns) {
      List<String> names = new ArrayList<>();

      for(OutputColumnRefModel column : columns) {
         names.add(displayName(column));
      }

      return names;
   }

   /**
    * {@code catalog} minus every column any member of {@code dimensions} already uses -- see the
    * class doc for why this, not {@code setCube}'s own {@code indexOf}, is what decides.
    */
   private static OutputColumnRefModel[] withoutConsumedColumns(OutputColumnRefModel[] catalog,
                                                                 VSDimensionModel[] dimensions)
   {
      Set<String> consumed = new HashSet<>();

      for(VSDimensionModel dimension : dimensions) {
         for(VSDimensionMemberModel member : members(dimension)) {
            OutputColumnRefModel ref = member.getDataRef();

            if(ref != null) {
               consumed.add(columnKey(ref));
            }
         }
      }

      List<OutputColumnRefModel> remaining = new ArrayList<>();

      for(OutputColumnRefModel column : catalog) {
         if(!consumed.contains(columnKey(column))) {
            remaining.add(column);
         }
      }

      return remaining.toArray(new OutputColumnRefModel[0]);
   }

   private static VSDimensionModel[] dimensions(HierarchyPropertyPaneModel pane) {
      return pane.getDimensions() == null ? new VSDimensionModel[0] : pane.getDimensions();
   }

   private static OutputColumnRefModel[] columns(HierarchyPropertyPaneModel pane) {
      return pane.getColumnList() == null ? new OutputColumnRefModel[0] : pane.getColumnList();
   }

   private static VSDimensionMemberModel[] members(VSDimensionModel dimension) {
      return dimension.getMembers() == null ? new VSDimensionMemberModel[0] : dimension.getMembers();
   }

   private static List<String> requireColumns(List<String> columns) {
      if(columns == null || columns.isEmpty()) {
         throw new IllegalArgumentException(
            "add_hierarchy_dimension needs 'columns' -- the column names for each level, " +
            "outermost first, e.g. [\"Country\", \"State\", \"City\"].");
      }

      for(String column : columns) {
         if(column == null || column.isBlank()) {
            throw new IllegalArgumentException("'columns' contains a blank entry.");
         }
      }

      return columns;
   }

   private static List<Integer> resolveLevels(List<String> columns, List<String> dateLevels) {
      List<Integer> levels = new ArrayList<>();

      for(int i = 0; i < columns.size(); i++) {
         String requested = dateLevels == null || i >= dateLevels.size() ? null : dateLevels.get(i);
         levels.add(dateLevel(requested, columns.get(i)));
      }

      return levels;
   }

   private static int dateLevel(String requested, String columnName) {
      if(requested == null || requested.isBlank()) {
         return DateRangeRef.NONE_INTERVAL;
      }

      String key = requested.trim().toLowerCase();
      Integer level = DATE_LEVELS.get(key);

      if(level == null) {
         throw new IllegalArgumentException(
            "'" + requested + "' is not a date level for '" + columnName + "'. Use one of: " +
            String.join(", ", DATE_LEVELS.keySet()) + ", or omit it for no date grouping.");
      }

      return level;
   }

   private static String dateLevelName(int option) {
      for(Map.Entry<String, Integer> level : DATE_LEVELS.entrySet()) {
         if(level.getValue() == option) {
            return level.getKey();
         }
      }

      return "none";
   }

   /** The {@link DateRangeRef} grouping levels a member can request, named. */
   private static final Map<String, Integer> DATE_LEVELS;

   static {
      Map<String, Integer> levels = new LinkedHashMap<>();
      levels.put("year", DateRangeRef.YEAR_INTERVAL);
      levels.put("quarter", DateRangeRef.QUARTER_INTERVAL);
      levels.put("month", DateRangeRef.MONTH_INTERVAL);
      levels.put("week", DateRangeRef.WEEK_INTERVAL);
      levels.put("day", DateRangeRef.DAY_INTERVAL);
      levels.put("hour", DateRangeRef.HOUR_INTERVAL);
      levels.put("minute", DateRangeRef.MINUTE_INTERVAL);
      levels.put("second", DateRangeRef.SECOND_INTERVAL);
      DATE_LEVELS = Collections.unmodifiableMap(levels);
   }

   /** One assembly type's read/write pair -- lets {@link #add}/{@link #remove}/{@link #list} stay
    *  written once instead of twice, chart and crosstab differing only in which dialog service and
    *  model class they go through. */
   private interface Target {
      Object readModel(String runtimeId, Principal user) throws Exception;
      HierarchyPropertyPaneModel pane(Object model);
      void write(String runtimeId, Object model, String linkUri, Principal user,
                CapturingCommandDispatcher dispatcher) throws Exception;

      default HierarchyPropertyPaneModel readPane(String runtimeId, Principal user)
         throws Exception
      {
         return pane(readModel(runtimeId, user));
      }
   }

   private final class ChartTarget implements Target {
      ChartTarget(String assemblyName) {
         this.assemblyName = assemblyName;
      }

      @Override
      public Object readModel(String runtimeId, Principal user) throws Exception {
         return chartService.getChartPropertyDialogModel(runtimeId, assemblyName, user);
      }

      @Override
      public HierarchyPropertyPaneModel pane(Object model) {
         return ((ChartPropertyDialogModel) model).getHierarchyPropertyPaneModel();
      }

      @Override
      public void write(String runtimeId, Object model, String linkUri, Principal user,
                        CapturingCommandDispatcher dispatcher) throws Exception
      {
         // Narrow, cube-only write (Redmine #76861 VCX-001) -- routing this through the whole
         // ChartPropertyDialogService.setChartPropertyModel dialog save unconditionally touches
         // every other pane (e.g. the Trend Line pane), which can NPE on a fresh chart whose
         // ChartDescriptor was never populated by a human through that dialog. See
         // ChartPropertyDialogService.setChartHierarchy's own class doc.
         //
         // The model's own revision is still threaded through (not dropped along with the rest
         // of the wide model) so a Chart hierarchy write keeps the same stale-write refusal its
         // CrosstabTarget sibling has via setCrosstabPropertyModel.
         chartService.setChartHierarchy(runtimeId, assemblyName, pane(model), linkUri, user,
                                        dispatcher, ((ChartPropertyDialogModel) model).getRevision());
      }

      private final String assemblyName;
   }

   private final class CrosstabTarget implements Target {
      CrosstabTarget(String assemblyName) {
         this.assemblyName = assemblyName;
      }

      @Override
      public Object readModel(String runtimeId, Principal user) throws Exception {
         return crosstabService.getCrosstabPropertyDialogModel(runtimeId, assemblyName, user);
      }

      @Override
      public HierarchyPropertyPaneModel pane(Object model) {
         return ((CrosstabPropertyDialogModel) model).getHierarchyPropertyPaneModel();
      }

      @Override
      public void write(String runtimeId, Object model, String linkUri, Principal user,
                        CapturingCommandDispatcher dispatcher) throws Exception
      {
         crosstabService.setCrosstabPropertyModel(runtimeId, assemblyName,
                                                  (CrosstabPropertyDialogModel) model, linkUri,
                                                  user, dispatcher);
      }

      private final String assemblyName;
   }

   private final ViewsheetSessionService sessions;
   private final ChartPropertyDialogService chartService;
   private final CrosstabPropertyDialogService crosstabService;
}
