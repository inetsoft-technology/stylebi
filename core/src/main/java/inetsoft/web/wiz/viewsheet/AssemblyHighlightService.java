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

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.ImageVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.adhoc.model.FontInfo;
import inetsoft.web.wiz.binding.VisualFrameAliases;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.condition.ConditionValueModel;
import inetsoft.web.composer.model.condition.ExpressionValueModel;
import inetsoft.web.composer.model.vs.HighlightDialogModel;
import inetsoft.web.composer.model.vs.HighlightModel;
import inetsoft.web.composer.model.vs.VSConditionDialogModel;
import inetsoft.web.composer.vs.dialog.HighlightDialogService;
import inetsoft.web.service.HighlightService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Highlights — conditional formatting on an assembly.
 *
 * <p>Each {@code HighlightModel} embeds a full {@code VSConditionDialogModel}, so highlights
 * inherit the alternating-array hazard wholesale. They therefore use {@link ConditionVocabulary}
 * unchanged: one implementation, two callers. A caller who has written a condition for
 * {@code set_condition} already knows how to write one here.
 *
 * <p><b>{@code usedHighlightNames} exists to stop name collisions, and this honours it.</b> A
 * duplicate name silently replaces an existing highlight, so adding one under a name already in
 * use is refused; updating requires saying so, which makes the destructive case explicit rather
 * than accidental.
 *
 * <p>Region-addressed like hyperlinks: a highlight applies to a cell, an axis or a text element,
 * not to the assembly as a whole.
 */
@Service
public class AssemblyHighlightService {
   /** Evaluates a highlight expression the way the renderer will. */
   @FunctionalInterface
   interface ExpressionChecker {
      /** @return the script error (null if it evaluates) and the value; throws if it does not compile. */
      VSAQuery.ExpressionResult check(String expression, boolean sql, ViewsheetSandbox box)
         throws Exception;
   }

   @Autowired
   public AssemblyHighlightService(ViewsheetSessionService sessions,
                                   HighlightDialogService highlightService)
   {
      this(sessions, highlightService, VSAQuery::evaluateHighlightExpression);
   }

   AssemblyHighlightService(ViewsheetSessionService sessions,
                            HighlightDialogService highlightService, ExpressionChecker checker)
   {
      this.sessions = sessions;
      this.highlightService = highlightService;
      this.checker = checker;
   }

   /** The outcome of a highlight write: anything the caller should be told (bug #78567). */
   public record SetResult(List<String> warnings) {}

   /** Where a highlight lives. All-defaults means the assembly itself. */
   public record Region(Integer row, Integer col, String colName, boolean axis, boolean text,
                        boolean cellSupplied) {
      /**
       * Five-argument form: {@code cellSupplied} is true when the caller gave a row or a col.
       * The compact constructor below erases that (null becomes 0), so it is carried explicitly
       * for crosstab {@code colName} resolution (bug 78776).
       */
      public Region(Integer row, Integer col, String colName, boolean axis, boolean text) {
         this(row, col, colName, axis, text, row != null || col != null);
      }

      /**
       * Normalizes a null row/col to <b>0</b> — on every construction path.
       *
       * <p>For a table-type assembly {@code HighlightDialogService.getHighlightDialogModel} calls
       * {@code lens.getTableDataPath(row, col)}, which NPEs on null, so highlights were unusable
       * on every table, crosstab and calc table (a chart never reaches that branch). Zero is what
       * the rest of that same method already assumes: a few lines later it reads
       * {@code row == null ? 0 : row}.
       *
       * <p>This lives in the compact constructor rather than in {@link #whole()} because the
       * controller builds a Region straight from its nullable {@code @RequestParam}s and never
       * calls the factory — normalizing only there fixed nothing on the live path.
       */
      public Region {
         row = row == null ? 0 : row;
         col = col == null ? 0 : col;
      }

      public static Region whole() {
         return new Region(0, 0, null, false, false);
      }
   }

   /** One highlight in the agent vocabulary. Colours are {@code #RRGGBB}. */
   public record Highlight(String name, String foreground, String background, FontInfo font,
                           List<ConditionVocabulary.Clause> conditions, boolean applyRow) {}

   public Map<String, Object> list(String sessionToken, Principal user, String assemblyName,
                                   Region region) throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      HighlightDialogModel model = read(rvs, assemblyName, region, user);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);

      if(model == null) {
         out.put("highlights", List.of());
         return out;
      }

      List<Map<String, Object>> highlights = new ArrayList<>();

      for(HighlightModel highlight : model.getHighlights() == null
         ? new HighlightModel[0] : model.getHighlights())
      {
         highlights.add(describe(highlight));
      }

      out.put("highlights", highlights);
      out.put("usedNames", usedNames(model));
      out.put("fields", fieldNames(model));
      out.put("appliesToRow", model.isShowRow());

      // Additive (bug #79114): a colName-less read of a chart sees only the whole-chart group,
      // so say which measures hold highlights of their own rather than looking empty.
      // Highlights a chart type change left on a measure's own group are listed too, marked as
      // not addressable by colName.
      MeasureGroups groups = measureGroups(rvs, assemblyName, region);
      List<Map<String, Object>> others = new ArrayList<>();

      for(Map.Entry<String, List<String>> entry : groups.reachable().entrySet()) {
         others.add(otherGroup(entry));
      }

      for(Map.Entry<String, List<String>> entry : groups.orphaned().entrySet()) {
         Map<String, Object> group = otherGroup(entry);
         group.put("note", ORPHANED_NOTE);
         others.add(group);
      }

      if(!others.isEmpty()) {
         out.put("otherGroups", others);
      }

      return out;
   }

   private static Map<String, Object> otherGroup(Map.Entry<String, List<String>> entry) {
      Map<String, Object> group = new LinkedHashMap<>();
      group.put("colName", entry.getKey());
      group.put("names", entry.getValue());
      return group;
   }

   /**
    * Adds a highlight, or updates one by name when {@code replace} is set. With {@code replace}
    * set, the name must already exist; a name that matches nothing is refused rather than added.
    *
    * <p>One {@code sessions.mutate}, so one undo checkpoint.
    */
   public SetResult set(String sessionToken, Principal user, String assemblyName, Region region,
                        Highlight highlight, boolean replace, String linkUri) throws Exception
   {
      requireName(highlight);
      List<String> warnings = new ArrayList<>();

      // Refuse before mutate(): a refused call inside it still costs an undo step and a revision.
      // Checked again inside the mutation in case the assembly changed in between.
      requireHighlightRenderable(sessions.resolve(sessionToken, user), assemblyName);

      List<String> sessionWarnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireHighlightRenderable(rvs, assemblyName);
         requireChartRegionDrawn(rvs, assemblyName, region);
         HighlightDialogModel model = read(rvs, assemblyName, region, user);

         if(model == null) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' has no highlight dialog — it is not an assembly that " +
               "supports conditional formatting.");
         }

         // Before the shared condition vocabulary gets a chance to blame the field name.
         requireHighlightableRegion(model, assemblyName, region);
         requireRowApplicable(model, assemblyName, highlight);
         requireNoDerivedConditionField(rvs.getViewsheet().getAssembly(assemblyName), highlight);
         requireNoDerivedConditionField(highlight, highlightService.getDateComparisonFields(
            runtimeId, assemblyName, model.getRow(), model.getCol(), user));

         List<HighlightModel> highlights = new ArrayList<>();
         boolean found = false;

         for(HighlightModel existing : model.getHighlights() == null
            ? new HighlightModel[0] : model.getHighlights())
         {
            if(existing != null && highlight.name().equalsIgnoreCase(existing.getName())) {
               found = true;

               // Silently replacing is the recorded hazard usedHighlightNames exists to
               // prevent, so overwriting has to be asked for.
               if(!replace) {
                  throw new IllegalArgumentException(
                     "A highlight named '" + highlight.name() + "' already exists on '" +
                     assemblyName + "'. Adding another under the same name would silently " +
                     "replace it. Pass replace:true to update it deliberately, or choose a " +
                     "different name.");
               }

               highlights.add(build(highlight, model));
            }
            else {
               highlights.add(existing);
            }
         }

         if(!found) {
            // replace:true is a request to update, so a name that matches nothing is almost
            // always a misspelling -- adding it would leave the intended highlight unchanged and
            // stack a second one next to it, while the caller is told the update succeeded.
            if(replace) {
               List<String> names = new ArrayList<>();

               for(HighlightModel existing : highlights) {
                  if(existing != null) {
                     names.add(existing.getName());
                  }
               }

               throw new IllegalArgumentException(
                  "'" + assemblyName + "' has no highlight named '" + highlight.name() +
                  "' to replace. " +
                  existingNames(names, rvs, assemblyName, region, highlight.name()) +
                  " Drop replace:true to add it as a new highlight.");
            }

            highlights.add(build(highlight, model));
         }

         checkExpressions(rvs, highlight, model, warnings);
         model.setHighlights(highlights.toArray(new HighlightModel[0]));
         model.setRejectDerivedMeasure(true);
         ChartRenderCheck.guarded(rvs, assemblyName, dispatcher, () ->
            highlightService.setHighlightDialogModel(runtimeId, assemblyName, model, linkUri,
                                                     user, dispatcher));
      });

      if(sessionWarnings != null) {
         warnings.addAll(sessionWarnings);
      }

      return new SetResult(warnings);
   }

   /**
    * Evaluates each expression operand before anything is committed (bug #78567). The renderer
    * swallows a highlight expression's script failure, so the highlight would silently never
    * apply while the write read as a success. A syntax error is refused (it can never become
    * valid); a runtime failure is committed with a warning, since it can be transient, e.g. an
    * expression reading a selection that has no value yet.
    */
   private void checkExpressions(RuntimeViewsheet rvs, Highlight highlight,
                                 HighlightDialogModel model, List<String> warnings)
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);

      if(box == null) {
         return;
      }

      Object[] conditionList = build(highlight, model).getVsConditionDialogModel().getConditionList();

      for(Object item : conditionList) {
         if(!(item instanceof ConditionModel condition) || condition.getValues() == null) {
            continue;
         }

         for(ConditionValueModel value : condition.getValues()) {
            if(value == null || !(value.getValue() instanceof ExpressionValueModel expression)) {
               continue;
            }

            boolean sql = expression.getType() == ExpressionValueModel.SQL;
            String text = expression.getExpression();
            VSAQuery.ExpressionResult result;

            try {
               result = checker.check(text, sql, box);
            }
            catch(Exception e) {
               throw new IllegalArgumentException(
                  "The " + (sql ? "sql" : "js") + " expression '" + text + "' does not compile: " +
                  e.getMessage() + " Nothing was changed.");
            }

            String error = result.error();

            if(error == null) {
               ConditionVocabulary.requireScalarExpressionResult(condition, text, result.value());
            }
            else {
               warnings.add("The expression '" + text + "' threw: " + error +
                            "; the highlight never applies until it evaluates.");
            }
         }
      }
   }

   /**
    * Removes one highlight by name. Removing one that is not there is an error, not a no-op.
    *
    * @return the warnings {@code sessions.mutate} captured while the table re-ran, empty when
    *         clean.
    */
   public List<String> delete(String sessionToken, Principal user, String assemblyName, Region region,
                      String name, String linkUri) throws Exception
   {
      if(name == null || name.isBlank()) {
         throw new IllegalArgumentException("delete_highlight needs the highlight's 'name'.");
      }

      List<String> orphanWarnings = new ArrayList<>();
      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         HighlightDialogModel model = read(rvs, assemblyName, region, user);
         HighlightModel[] existing = model == null || model.getHighlights() == null
            ? new HighlightModel[0] : model.getHighlights();
         List<HighlightModel> kept = new ArrayList<>();
         List<String> names = new ArrayList<>();

         for(HighlightModel highlight : existing) {
            if(highlight == null) {
               continue;
            }

            names.add(highlight.getName());

            if(!name.equalsIgnoreCase(highlight.getName())) {
               kept.add(highlight);
            }
         }

         if(kept.size() == existing.length) {
            String holder = orphanHolder(rvs, assemblyName, region, name);

            if(holder == null) {
               throw new IllegalArgumentException(
                  "'" + assemblyName + "' has no highlight named '" + name + "'. " +
                  existingNames(names, rvs, assemblyName, region, name));
            }

            deleteOrphan(rvs, runtimeId, dispatcher, assemblyName, holder, name, linkUri, user);
            orphanWarnings.add(
               "Removed '" + name + "' from " + holder + "'s own highlight group, which " +
               ORPHANED_NOTE + ".");
            return;
         }

         model.setHighlights(kept.toArray(new HighlightModel[0]));
         ChartRenderCheck.guarded(rvs, assemblyName, dispatcher, () ->
            highlightService.setHighlightDialogModel(runtimeId, assemblyName, model, linkUri,
                                                     user, dispatcher));
      });

      List<String> out = new ArrayList<>(warnings == null ? List.of() : warnings);
      out.addAll(orphanWarnings);
      return out;
   }

   /**
    * The measure whose own group holds {@code name} when the whole-chart lookup missed it, for a
    * chart type that reads one chart-wide group (highlights set while the chart was another type
    * stay on the measures). Null when no such highlight exists; throws when it cannot be told
    * which measure is meant.
    *
    * <p>With a colName only that measure's own group is searched, so a colName never removes
    * another measure's highlight; without one, exactly one holder is removed and several are
    * refused.
    */
   private static String orphanHolder(RuntimeViewsheet rvs, String assemblyName, Region region,
                                      String name)
   {
      Map<String, List<String>> orphaned = measureGroups(rvs, assemblyName, region).orphaned();
      List<String> holders = new ArrayList<>();

      for(Map.Entry<String, List<String>> entry : orphaned.entrySet()) {
         if(entry.getValue().stream().anyMatch(name::equalsIgnoreCase)) {
            holders.add(entry.getKey());
         }
      }

      if(holders.isEmpty()) {
         return null;
      }

      if(hasColName(region)) {
         String own = resolveMeasureName(rvs, assemblyName, region.colName());

         if(own != null && holders.contains(own)) {
            return own;
         }

         throw new IllegalArgumentException(
            "'" + assemblyName + "' has no highlight named '" + name + "' on '" +
            region.colName() + "'. '" + name + "' is held by " + String.join(", ", holders) +
            "; pass that measure as colName to remove it.");
      }

      if(holders.size() > 1) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' has a highlight named '" + name + "' on more than one " +
            "measure: " + String.join(", ", holders) + ". Pass the measure as colName to say " +
            "which one to remove.");
      }

      return holders.get(0);
   }

   /**
    * Removes {@code name} from {@code measure}'s own group, reading and writing that group rather
    * than the chart-wide one so the other highlights on the measure are written back unchanged.
    */
   private void deleteOrphan(RuntimeViewsheet rvs, String runtimeId,
                             CapturingCommandDispatcher dispatcher,
                             String assemblyName, String measure, String name, String linkUri,
                             Principal user) throws Exception
   {
      HighlightDialogModel own = highlightService.getRefLevelHighlightDialogModel(
         runtimeId, assemblyName, null, null, measure, false, false, user);
      List<HighlightModel> kept = new ArrayList<>();

      for(HighlightModel highlight : own.getHighlights()) {
         if(highlight != null && !name.equalsIgnoreCase(highlight.getName())) {
            kept.add(highlight);
         }
      }

      own.setHighlights(kept.toArray(new HighlightModel[0]));
      own.setRefLevelGroup(true);
      ChartRenderCheck.guarded(rvs, assemblyName, dispatcher, () ->
         highlightService.setHighlightDialogModel(runtimeId, assemblyName, own, linkUri, user,
                                                  dispatcher));
   }

   /** The full name of the measure a colName addresses, resolved as the read and write do. */
   private static String resolveMeasureName(RuntimeViewsheet rvs, String assemblyName,
                                            String colName)
   {
      ChartVSAssemblyInfo info = chartInfo(rvs, assemblyName);
      VSChartInfo chartInfo = info == null ? null : info.getVSChartInfo();
      ChartRef ref = chartInfo == null ? null :
         HighlightDialogService.resolveChartHighlightRef(chartInfo, colName, false, false);
      return ref == null ? null : ref.getFullName();
   }

   private static ChartVSAssemblyInfo chartInfo(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);
      return assembly != null && assembly.getVSAssemblyInfo() instanceof ChartVSAssemblyInfo info
         ? info : null;
   }

   private static boolean hasColName(Region region) {
      return region != null && region.colName() != null && !region.colName().isBlank();
   }

   /**
    * The "It has: ..." half of a name-not-found refusal. On a chart addressed without a colName
    * the lookup sees only the whole-chart group, while a highlight set with a colName lives on
    * that measure's own group (bug #79114); when such a group holds highlights, say so and name
    * the colName to pass, and do not claim "(none)" about a chart that has highlights. On a chart
    * type that reads one chart-wide group, colName cannot reach a measure's own group, so those
    * highlights are listed without that advice.
    */
   private static String existingNames(List<String> names, RuntimeViewsheet rvs,
                                       String assemblyName, Region region, String missing)
   {
      Map<String, List<String>> columns = otherColumnGroups(rvs, assemblyName, region, names);

      if(!columns.isEmpty()) {
         return tableHint(names, columns);
      }

      MeasureGroups all = measureGroups(rvs, assemblyName, region);
      Map<String, List<String>> groups = all.reachable();
      boolean holdsName = false;

      for(List<String> group : groups.values()) {
         for(String existing : group) {
            holdsName = holdsName || missing.equalsIgnoreCase(existing);
         }
      }

      StringBuilder out = new StringBuilder();

      // A non-empty whole-chart group that merely lacks the name only gets the hint when another
      // group holds that name; an empty one gets it whenever any group has highlights.
      if(groups.isEmpty() || !names.isEmpty() && !holdsName) {
         out.append("It has: ").append(names.isEmpty() ? "(none)" : String.join(", ", names))
            .append(".");
      }
      else {
         out.append(names.isEmpty() ? "The whole-chart group (no colName) has no highlights."
                                    : "It has: " + String.join(", ", names) + ".");
         out.append(" The chart has highlights on measures: ").append(describe(groups));
         out.append(". A highlight set with a colName lives on that measure's group, so pass " +
                    "colName; a call without colName reads only the whole-chart group.");
      }

      if(!all.orphaned().isEmpty()) {
         out.append(" The chart also has highlights on measures that ").append(ORPHANED_NOTE)
            .append(": ").append(describe(all.orphaned())).append(".");
      }

      return out.toString();
   }

   /**
    * The cell-level highlights held by the columns of a plain table other than the one that was
    * read, keyed by column name (bug #79397). A cell-level highlight lives on its own column's
    * data path, while a call that names no row/col/colName reads only the first data cell (that
    * column's group plus the shared row-level group), so a highlight on any other column is
    * invisible to it. Names in {@code readNames} (already reported) are left out.
    *
    * <p>Empty for anything but a plain table, and for a call that names a colName, row, col, axis
    * or text, which addresses one cell and is never widened. Read-only.
    */
   private static Map<String, List<String>> otherColumnGroups(RuntimeViewsheet rvs,
                                                              String assemblyName, Region region,
                                                              List<String> readNames)
   {
      boolean located = region != null &&
         (hasColName(region) || region.cellSupplied() || region.axis() || region.text());
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(located || assembly == null ||
         !(assembly.getVSAssemblyInfo() instanceof TableVSAssemblyInfo info))
      {
         return Map.of();
      }

      return columnGroups(info.getHighlightAttr(), readNames);
   }

   /** The non-row detail-cell groups of {@code attr}, minus {@code skip}, by column name. */
   static Map<String, List<String>> columnGroups(TableHighlightAttr attr, Collection<String> skip) {
      Map<String, List<String>> out = new LinkedHashMap<>();

      if(attr == null) {
         return out;
      }

      Enumeration<TableDataPath> paths = attr.getAllDataPaths();

      while(paths.hasMoreElements()) {
         TableDataPath path = paths.nextElement();
         HighlightGroup group = path == null ? null : attr.getHighlight(path);

         if(path == null || path.isRow() || path.getType() != TableDataPath.DETAIL ||
            group == null || path.getPath() == null || path.getPath().length == 0)
         {
            continue;
         }

         List<String> held = new ArrayList<>();

         for(String n : group.getNames()) {
            if(skip.stream().noneMatch(n::equalsIgnoreCase)) {
               held.add(n);
            }
         }

         if(!held.isEmpty()) {
            out.computeIfAbsent(path.getPath()[path.getPath().length - 1],
                                k -> new ArrayList<>()).addAll(held);
         }
      }

      return out;
   }

   /**
    * The refusal text for a plain table whose first data cell lacks the name while other columns
    * hold highlights. Keeps the "It has: ..." half, names the columns, and says to pass colName.
    */
   static String tableHint(List<String> names, Map<String, List<String>> columns) {
      return (names.isEmpty() ? "The first data cell has no highlights of its own."
                              : "It has: " + String.join(", ", names) + ".") +
         " Other columns of the table hold highlights: " + describe(columns) +
         ". A cell-level highlight lives on its column, so pass colName (or row/col) to address " +
         "it; a call with no location reads only the first data cell.";
   }

   private static String describe(Map<String, List<String>> groups) {
      StringBuilder out = new StringBuilder();
      boolean first = true;

      for(Map.Entry<String, List<String>> entry : groups.entrySet()) {
         out.append(first ? "" : "; ").append(entry.getKey()).append(" (")
            .append(String.join(", ", entry.getValue())).append(")");
         first = false;
      }

      return out.toString();
   }

   static final String ORPHANED_NOTE = "cannot be addressed by colName on this chart type";

   /**
    * The highlights held by each x/y measure's own group, keyed by the exact colName that names
    * the measure. {@code reachable} are the groups a colName reads on this chart type (listed for
    * a call that names no colName, which reads the whole-chart group); {@code orphaned} are those
    * on a chart type that reads one chart-wide group instead, e.g. highlights set while the chart
    * was a bar and left behind by a change to a multi-measure radar.
    */
   private record MeasureGroups(Map<String, List<String>> reachable,
                                Map<String, List<String>> orphaned)
   {
      static final MeasureGroups NONE = new MeasureGroups(Map.of(), Map.of());
   }

   /**
    * Empty for anything but a mark region of a chart whose measures hold highlights of their own;
    * {@code reachable} is also empty for a call that names a colName. Read-only.
    */
   private static MeasureGroups measureGroups(RuntimeViewsheet rvs, String assemblyName,
                                              Region region)
   {
      if(region != null && (region.axis() || region.text())) {
         return MeasureGroups.NONE;
      }

      ChartVSAssemblyInfo info = chartInfo(rvs, assemblyName);
      VSChartInfo chartInfo = info == null ? null : info.getVSChartInfo();

      if(chartInfo == null) {
         return MeasureGroups.NONE;
      }

      Map<String, List<String>> reachable = new LinkedHashMap<>();
      Map<String, List<String>> orphaned = new LinkedHashMap<>();
      List<VSDataRef> refs = new ArrayList<>(Arrays.asList(chartInfo.getXFields()));
      refs.addAll(Arrays.asList(chartInfo.getYFields()));

      for(VSDataRef ref : refs) {
         if(!(ref instanceof ChartRef chartRef) || !chartRef.isMeasure() ||
            !(ref instanceof HighlightRef highlightRef) || highlightRef.getHighlightGroup() == null)
         {
            continue;
         }

         String[] held = highlightRef.getHighlightGroup().getNames();

         if(held != null && held.length > 0) {
            // evaluated per measure, as the read and write of a chart highlight do
            Map<String, List<String>> target =
               HighlightDialogService.readsChartLevelGroup(chartInfo, chartRef, false)
               ? orphaned : reachable;
            target.putIfAbsent(chartRef.getFullName(), Arrays.asList(held));
         }
      }

      return new MeasureGroups(hasColName(region) ? Map.of() : reachable, orphaned);
   }

   // ── conversions ───────────────────────────────────────────────────────────

   private static void requireName(Highlight highlight) {
      if(highlight == null || highlight.name() == null || highlight.name().isBlank()) {
         throw new IllegalArgumentException(
            "A highlight needs a 'name' — it is how the highlight is addressed for updates and " +
            "deletion.");
      }

      if(highlight.foreground() == null && highlight.background() == null) {
         throw new IllegalArgumentException(
            "Highlight '" + highlight.name() + "' sets no colour. A highlight with no " +
            "foreground and no background is stored and renders nothing, which reads as a " +
            "condition that never matched.");
      }
   }

   private static HighlightModel build(Highlight highlight, HighlightDialogModel model) {
      HighlightModel out = new HighlightModel();
      out.setName(highlight.name());

      if(highlight.foreground() != null) {
         out.setForeground(VisualFrameAliases.normalizeColor(highlight.foreground()));
      }

      if(highlight.background() != null) {
         out.setBackground(VisualFrameAliases.normalizeColor(highlight.background()));
      }

      if(highlight.font() != null) {
         out.setFontInfo(highlight.font());
      }

      out.setApplyRow(highlight.applyRow());

      // The embedded condition model reuses spec #4's vocabulary rather than a parallel one,
      // and is validated against the highlight dialog's own fields.
      VSConditionDialogModel condition = new VSConditionDialogModel();
      condition.setTableName(model.getTableName());
      condition.setFields(model.getFields());
      condition.setConditionList(
         ConditionVocabulary.toConditionList(highlight.conditions(), model.getFields()));
      out.setVsConditionDialogModel(condition);
      return out;
   }

   private static Map<String, Object> describe(HighlightModel highlight) {
      Map<String, Object> out = new LinkedHashMap<>();

      if(highlight == null) {
         return out;
      }

      out.put("name", highlight.getName());
      out.put("foreground", highlight.getForeground());
      out.put("background", highlight.getBackground());
      out.put("font", describeFont(highlight.getFontInfo()));
      out.put("applyRow", highlight.isApplyRow());
      out.put("conditions", highlight.getVsConditionDialogModel() == null
         ? List.of()
         : ConditionVocabulary.describe(
            highlight.getVsConditionDialogModel().getConditionList(), true));
      return out;
   }

   /** {@code null} when no font override is stored, mirroring {@code foreground}/{@code background}. */
   private static Map<String, Object> describeFont(FontInfo font) {
      if(font == null || font.getFontFamily() == null) {
         return null;
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("fontFamily", font.getFontFamily());
      out.put("fontSize", describeFontSize(font.getFontSize()));
      out.put("bold", "bold".equals(font.getFontWeight()));
      out.put("italic", "italic".equals(font.getFontStyle()));
      out.put("underline", "underline".equals(font.getFontUnderline()));
      out.put("strikethrough", "strikethrough".equals(font.getFontStrikethrough()));
      return out;
   }

   /**
    * {@code FontInfo.toFont()} parses the same stored string with an unguarded
    * {@code Integer.parseInt}, so a malformed size is a pre-existing risk — but that path only
    * runs once a highlight is applied, while this one is now reachable straight from
    * {@code list_highlights}'s read. Reporting the raw stored value on a parse failure keeps the
    * read itself from throwing, while still surfacing that something is off (a caller expecting
    * an int wouldn't get a string here otherwise).
    */
   private static Object describeFontSize(String fontSize) {
      if(fontSize == null) {
         return null;
      }

      try {
         return Integer.valueOf(fontSize);
      }
      catch(NumberFormatException e) {
         return fontSize;
      }
   }

   private HighlightDialogModel read(RuntimeViewsheet rvs, String assemblyName, Region region,
                                     Principal user) throws Exception
   {
      String runtimeId = rvs.getID();
      requireTableRendered(rvs, assemblyName);
      Region target = region == null ? Region.whole() : renderedCell(rvs, assemblyName, region);
      target = resolveColName(rvs, assemblyName, target, user);
      HighlightDialogModel model = readAt(runtimeId, assemblyName, target, user);

      // A crosstab's cell (0,0) is a HEADER, and a header has no highlightable fields — so
      // addressing the whole assembly returned `fields: []` on an assembly whose data cells have
      // two, and every condition was then refused for naming a field "this assembly cannot filter
      // on". The field was fine; the region was wrong, and nothing said so.
      //
      // When the caller named no region at all, their intent is the assembly's data, so fall
      // forward to the first data cell rather than reporting an empty vocabulary. An explicit
      // region is never second-guessed: the caller meant that cell, and silently moving it would
      // highlight somewhere they did not ask for.
      // A null model means the assembly has no highlight dialog at all; that is the caller's
      // answer, and re-reading a different cell of it would only produce the same null.
      if(region == null && model != null) {
         // `fieldNames(model).isEmpty()` is the general "we're stuck on the header" signal, and
         // it is reliable for a Crosstab (its fields are computed dpath-aware, so a header cell
         // genuinely reports none). It is NOT reliable for a plain Table:
         // HighlightService.getRefsForVSAssembly's Table branch reports the table's entire base
         // column list regardless of row/col, so `fields` comes back non-empty even at (0,0) —
         // the fall-forward below would never fire and the header would be highlighted silently.
         // For a Table, check the header extent directly instead.
         boolean tableAtHeader = model.isTableAssembly() &&
            isHeaderCell(target, firstDataCell(runtimeId, assemblyName, user));

         if(tableAtHeader || fieldNames(model).isEmpty()) {
            HighlightDialogModel atData =
               readAt(runtimeId, assemblyName, firstDataCell(runtimeId, assemblyName, user), user);

            if(!fieldNames(atData).isEmpty()) {
               return atData;
            }
         }
      }

      return model;
   }

   /**
    * Lets a plain table's {@code colName} be either the rendered label or the column name (bug
    * 78953). The native lookup matches only the rendered header, so a column relabelled in the
    * viewsheet could not be addressed by the name the {@code fields} list reports. The name is
    * resolved through {@link ConditionVocabulary#find} (column name wins, a label shared by
    * several columns is refused) and handed on as the label the lens knows it by.
    *
    * <p>Not applied to a crosstab, whose {@code colName} names an aggregate, nor to a chart.
    * When the label cannot single out the column (another column renders under the same label,
    * or is named it), the lens would pick the wrong one, so that is refused instead.
    */
   private Region resolveColName(RuntimeViewsheet rvs, String assemblyName, Region region,
                                 Principal user) throws Exception
   {
      String colName = region.colName();

      if(colName == null || colName.isBlank() || region.axis() || region.text()) {
         return region;
      }

      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(!(assembly instanceof TableDataVSAssembly) || assembly instanceof CrosstabVSAssembly) {
         return region;
      }

      HighlightDialogModel whole =
         readAt(rvs.getID(), assemblyName, new Region(null, null, null, false, false), user);
      DataRefModel[] fields = whole == null ? null : whole.getFields();
      DataRefModel field = ConditionVocabulary.find(fields, colName);

      if(field == null || field.getName() == null || field.getName().equals(colName)) {
         return region;
      }

      String label = field.getName();

      for(DataRefModel other : fields) {
         if(other != null && other != field && other.getName() != null &&
            (label.equalsIgnoreCase(other.getName()) ||
               label.equalsIgnoreCase(ConditionVocabulary.canonicalName(other))))
         {
            throw new IllegalArgumentException(
               "colName '" + colName + "' is rendered under the label '" + label + "', which " +
               "another column of '" + assemblyName + "' also carries (" +
               ConditionVocabulary.canonicalName(other) + "), so the name cannot single out " +
               "the column. Use row/col to address the cell.");
         }
      }

      return new Region(region.row(), region.col(), label, region.axis(), region.text(),
                        region.cellSupplied());
   }

   /** Whether {@code cell} falls inside the assembly's own reported header extent. */
   private static boolean isHeaderCell(Region cell, Region firstDataCell) {
      return cell.row() < firstDataCell.row() || cell.col() < firstDataCell.col();
   }

   /**
    * A highlight on a table is stored per rendered cell, and {@code HighlightDialogService}
    * dereferences the table's lens unguarded, so a table that did not render surfaced as an NPE
    * (a masked 500). Refuse it by name instead, for a script failure on the first read and for
    * the cached failed load alike.
    */
   private static void requireTableRendered(RuntimeViewsheet rvs, String assemblyName)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly instanceof TableDataVSAssembly && rvs.getViewsheetSandbox().isPresent()) {
         TableLensGuard.requireRenderedLens(
            rvs, assembly, assemblyName, "its highlights cannot be addressed (they are stored " +
            "per rendered cell)");
      }
   }

   /**
    * The rendered cell an explicit region of a table-type assembly addresses, refused by name
    * when it is outside the table (bug #78719); see {@link TableLensGuard#requireRenderedCell}.
    * A freehand table's layout row/col become its first runtime instance. Any other assembly type
    * keeps the region as given.
    */
   private static Region renderedCell(RuntimeViewsheet rvs, String assemblyName, Region region)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(!(assembly instanceof TableDataVSAssembly table)) {
         return region;
      }

      TableLensGuard.Cell cell = TableLensGuard.requireRenderedCell(
         rvs, table, assemblyName, region.row(), region.col(),
         "no highlight can be addressed there (a table's highlights are stored per rendered cell)");

      return new Region(cell.row(), cell.col(), region.colName(), region.axis(), region.text(),
                        region.cellSupplied());
   }

   /**
    * The first genuine DATA cell of a table-type assembly, not a guessed literal.
    *
    * <p>A crosstab with 2+ aggregates inserts one extra header row (side by side) or column
    * (stacked, the default) labeling each aggregate, which shifts the real first data cell past
    * {@code (1, 1)} on whichever axis carries it -- {@code (1, 1)} was itself still a HEADER cell
    * for a 2-aggregate crosstab with no column dimension, so falling forward to it reported the
    * same empty vocabulary the caller was already falling forward from. {@link
    * HighlightDialogService#getFirstDataCell} reads the assembly's own header row/col counts
    * instead of assuming either is 1.
    */
   private Region firstDataCell(String runtimeId, String assemblyName, Principal user)
      throws Exception
   {
      int[] headerSize = highlightService.getFirstDataCell(runtimeId, assemblyName, user);
      return new Region(headerSize[0], headerSize[1], null, false, false);
   }

   private HighlightDialogModel readAt(String runtimeId, String assemblyName, Region target,
                                       Principal user) throws Exception
   {
      boolean supplied = target.cellSupplied();
      return highlightService.getHighlightDialogModel(runtimeId, assemblyName,
                                                      supplied ? target.row() : null,
                                                      supplied ? target.col() : null,
                                                      target.colName(), target.axis(),
                                                      target.text(), user);
   }

   /**
    * Refuses a highlight whose region exposes no fields, naming the region rather than the field.
    *
    * <p>Without this the failure surfaced from the shared condition vocabulary as "names 'X', which
    * this assembly cannot filter on. Available fields: (none)" — which reads as a bad field name on
    * an assembly that cannot be filtered, when in fact the assembly filters fine and the region was
    * the header row.
    */
   private static void requireHighlightableRegion(HighlightDialogModel model, String assemblyName,
                                                  Region region)
   {
      if(!fieldNames(model).isEmpty()) {
         return;
      }

      if(region != null && region.colName() != null && !region.colName().isBlank() &&
         !region.axis() && !region.text())
      {
         throw new IllegalArgumentException(
            "colName '" + region.colName() + "' did not resolve to a data cell of '" +
            assemblyName + "', and the explicit cell row " + region.row() + ", col " +
            region.col() + " exposes no highlightable fields. Name a measure of the assembly " +
            "as colName, or address a data cell with row/col.");
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' exposes no highlightable fields at " +
         (region == null ? "the default region" :
            "row " + region.row() + ", col " + region.col()) +
         ". For a crosstab or table this usually means a header cell: highlights attach to DATA " +
         "cells, not header rows/columns -- and for a crosstab with 2+ aggregates the first data " +
         "cell is not always (1, 1). Pick a DATA cell: pass row/col of a data cell, or colName " +
         "naming a column or measure of the assembly (list_highlights with that row/col or " +
         "colName shows the available fields). On a calc table cell (0, 0) is fixed, so a " +
         "region-less call does not resolve to a data cell there.");
   }

   /**
    * Refuses a highlight on any assembly that does not render one. Only Table/Crosstab/CalcTable,
    * Chart, Text and Image do; every other type (Submit, Gauge, Cylinder, Thermometer,
    * SlidingScale, a Form input, a shape, a selection, a container) is refused.
    *
    * <p>{@code HighlightDialogService} accepts the info of any assembly, but a highlight colour or
    * font is only ever applied for those four ({@code VSFormatModel}, {@code VSImage}, the table
    * and chart renderers). The Composer offers no Highlight action for the other types either.
    * Without this the write reported success, read back correctly via list_highlights, and never
    * rendered, or fell through to a table-oriented field-lookup error. An allow-list on the
    * assembly class, like {@code AssemblyHyperlinkService.requireHyperlinkable}, so a future type
    * is refused until it renders highlights. Only {@code set} calls it, so a stray highlight
    * already stored on such an assembly can still be listed and deleted.
    */
   private static void requireHighlightRenderable(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         return;
      }

      if(assembly instanceof TableDataVSAssembly || assembly instanceof ChartVSAssembly ||
         assembly instanceof TextVSAssembly || assembly instanceof ImageVSAssembly)
      {
         return;
      }

      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      String type = (info == null ? assembly : info).getClass().getSimpleName()
         .replaceFirst("VSAssembly(Info)?$", "");

      throw new IllegalArgumentException(
         "'" + assemblyName + "' is a " + type + ", which does not render highlights -- the " +
         "Composer offers no Highlight for this type and nothing paints one. Highlight a " +
         "Text, Image, Table, Crosstab or Chart instead (a Gauge-family assembly also has " +
         "rangeValues/rangeColorValues for a similar effect).");
   }

   /**
    * Refuses a chart {@code axis}/{@code text} region whose write would land on a highlight
    * group the chart never reads for that region. Accepts exactly what the Composer can create.
    *
    * <p>{@code axis} and {@code text} only choose which field
    * {@code HighlightDialogService.getMeasure} looks up. When that lookup finds no field, the
    * write falls to the whole-chart group (or the chart-level text group) and the call reported
    * success for a highlight that renders nothing, or renders as a mark highlight on every mark.
    * The field is therefore resolved with the same lookup the write uses
    * ({@link HighlightDialogService#resolveChartHighlightRef}) and then checked against what
    * {@code GraphGenerator} draws:
    * <ul>
    *   <li>{@code axis:true}: an x/y (including outer/facet) dimension, a string-typed measure
    *   drawn on the axis, or a radar spoke measure (the Composer offers this from a spoke
    *   label). A numeric x/y measure on any other chart is never drawn on the axis
    *   ({@code GraphGenerator.findHighlightRef}), date comparison period and custom-merged axes
    *   are refused by the Composer, and aesthetic-only or unknown names resolve to no axis.</li>
    *   <li>{@code text:true}: a colName is always required (the chart-level text group is only
    *   read when the element var resolves to no field, which the Composer never creates), and it
    *   must resolve to a field whose value labels are drawn: the bound text field, an x/y
    *   measure, a radar/treemap/relation dimension, a map geo field, or a candle/stock/gantt
    *   field that a bound text field labels.</li>
    * </ul>
    *
    * <p>Only {@code set} calls it, so a stray highlight already stored that way can still be
    * listed and deleted.
    */
   private static void requireChartRegionDrawn(RuntimeViewsheet rvs, String assemblyName,
                                               Region region)
   {
      if(region == null || !region.axis() && !region.text()) {
         return;
      }

      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null ||
         !(assembly.getVSAssemblyInfo() instanceof ChartVSAssemblyInfo chart))
      {
         return;
      }

      String colName = region.colName() == null || region.colName().isBlank()
         ? null : region.colName();

      if(region.axis() && colName == null) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "': axis:true needs a 'colName'. 'axis' only chooses which " +
            "field's axis the highlight attaches to; without a colName it is ignored and the " +
            "highlight lands on the whole chart instead. Pass 'colName' naming the axis field, " +
            "as get_binding reports it.");
      }

      VSChartInfo info = chart.getVSChartInfo();

      if(region.text() && colName == null) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "': text:true needs a 'colName' naming the field whose value " +
            "labels to highlight" + fieldList(textFields(info)) + ". Without one the " +
            "highlight lands on the chart-level text group, which the chart does not read for " +
            "its labels, so it would be stored and never render.");
      }

      if(info == null) {
         return;
      }

      ChartRef ref = HighlightDialogService.resolveChartHighlightRef(
         info, colName, region.axis(), region.text());

      if(region.axis()) {
         requireAxisField(info, assemblyName, colName, ref);
      }

      if(region.text()) {
         Set<String> drawn = textFields(info);

         if(ref == null || !drawn.contains(ref.getFullName())) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "': text:true colName '" + colName + "' is not a field " +
               "whose value labels this chart draws, so the highlight would be stored and " +
               "never render" + fieldList(drawn) + ". Name the measure (or the bound text " +
               "field), or drop text:true for a mark highlight.");
         }
      }
   }

   private static void requireAxisField(VSChartInfo info, String assemblyName, String colName,
                                        ChartRef ref)
   {
      boolean radar = isRadar(info);
      Set<String> axisFields = new LinkedHashSet<>();
      Set<String> drawnOnAxis = new LinkedHashSet<>();

      for(ChartRef field : axisCandidates(info)) {
         axisFields.add(field.getFullName());

         if(!field.isMeasure() || radar || XSchema.STRING.equals(field.getDataType())) {
            drawnOnAxis.add(field.getFullName());
         }
      }

      if(ref == null || !axisFields.contains(ref.getFullName())) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "': axis:true colName '" + colName + "' is not a field this " +
            "chart draws an axis for" + fieldList(drawnOnAxis) + ". An axis highlight colors " +
            "the labels of an x/y dimension; for a field on a color/shape/size/text shelf, " +
            "drop axis:true (a mark highlight) or use text:true for its value labels.");
      }

      if(isPeriodAxis(info, ref)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "': axis:true colName '" + colName + "' is a date " +
            "comparison period axis, which the Composer does not highlight. Highlight the " +
            "marks instead (drop axis:true).");
      }

      if(ref.isMeasure() && !radar && !XSchema.STRING.equals(ref.getDataType())) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "': axis:true colName '" + colName + "' is a numeric " +
            "measure. Its axis shows a value scale, not labels, so an axis highlight on it is " +
            "never drawn on the axis and would only act as a mark highlight. Drop axis:true " +
            "for a plain (mark) highlight on '" + colName + "', or name an x/y dimension" +
            fieldList(drawnOnAxis) + ".");
      }
   }

   /** The x/y fields an axis is drawn for (design and runtime), plus radar-one's group axis. */
   private static List<ChartRef> axisCandidates(VSChartInfo info) {
      List<ChartRef> fields = new ArrayList<>();
      addAll(fields, info.getXFields());
      addAll(fields, info.getYFields());
      addAll(fields, info.getRTXFields());
      addAll(fields, info.getRTYFields());

      // GraphGenerator.findHighlightRef reads the group field on radar-one's outer axis (57035).
      if(GraphTypes.isRadarOne(info)) {
         addAll(fields, info.getGroupFields());
         addAll(fields, info.getRTGroupFields());
      }

      return fields;
   }

   /** The Composer refuses axis highlights on period regions and custom-merged DC refs. */
   private static boolean isPeriodAxis(VSChartInfo info, ChartRef ref) {
      String name = ref.getFullName();

      if(info.isPeriodRef(name) || info.isPeriodPartRef(name)) {
         return true;
      }

      for(Object candidate : new Object[]{ ref, info.getRTFieldByFullName(name) }) {
         if(candidate instanceof VSDimensionRef dim &&
            (dim.getDates() != null && dim.getDates().length >= 2 ||
               dim.getDcMergeGroup() != null))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Full names of the fields whose text highlight group {@code GraphGenerator} reads for value
    * labels: the bound text field(s), x/y measures, and the per-type element fields. A
    * candle/stock/gantt field counts only when a text field labels it.
    */
   private static Set<String> textFields(VSChartInfo info) {
      Set<String> names = new LinkedHashSet<>();

      if(info == null) {
         return names;
      }

      List<ChartRef> fields = new ArrayList<>();
      AestheticRef chartText = info.getTextField();
      addText(fields, chartText);

      // Candle/stock and gantt (MergedGraphGenerator) draw no value labels of their own: a label
      // exists only when a text field is bound, and its highlight is read from that text field,
      // which is where HighlightDialogService.getMeasure redirects a write naming the field.
      List<ChartRef> labelOnlyFields = new ArrayList<>();

      if(info instanceof CandleChartInfo candle) {
         addAll(labelOnlyFields, candle.getHighField(), candle.getLowField(),
                candle.getOpenField(), candle.getCloseField(), candle.getRTHighField(),
                candle.getRTLowField(), candle.getRTOpenField(), candle.getRTCloseField());
      }

      if(info instanceof GanttChartInfo gantt) {
         addAll(labelOnlyFields, gantt.getStartField(), gantt.getEndField(),
                gantt.getMilestoneField(), gantt.getRTStartField(), gantt.getRTEndField(),
                gantt.getRTMilestoneField());
      }

      if(info.isMultiAesthetic()) {
         // Per-field text fields (multi-style charts, and gantt, which is always multi-aesthetic).
         List<ChartRef> bindables = new ArrayList<>(axisCandidates(info));
         bindables.addAll(labelOnlyFields);

         for(ChartRef field : bindables) {
            if(field instanceof ChartBindable bindable &&
               bindable.getTextField() != null &&
               bindable.getTextField().getDataRef() instanceof ChartRef)
            {
               addText(fields, bindable.getTextField());

               // Naming the field itself is redirected to its text field, so it is valid too.
               if(labelOnlyFields.contains(field)) {
                  fields.add(field);
               }
            }
         }
      }
      else if(chartText != null) {
         // Redirected to the chart's text field.
         fields.addAll(labelOnlyFields);
      }

      boolean radar = isRadar(info);

      for(ChartRef field : axisCandidates(info)) {
         if(field.isMeasure() || radar) {
            fields.add(field);
         }
      }

      if(radar || GraphTypes.isTreemap(info.getRTChartType()) ||
         GraphTypes.isTreemap(info.getChartType()))
      {
         addAll(fields, info.getGroupFields());
         addAll(fields, info.getRTGroupFields());
      }

      if(info instanceof RelationChartInfo relation) {
         addAll(fields, relation.getSourceField(), relation.getTargetField(),
                relation.getRTSourceField(), relation.getRTTargetField());
      }

      if(info instanceof MapInfo map) {
         addAll(fields, map.getGeoFields());
         addAll(fields, map.getRTGeoFields());
      }

      for(ChartRef field : fields) {
         if(field.getFullName() != null) {
            names.add(field.getFullName());
         }
      }

      return names;
   }

   private static boolean isRadar(ChartInfo info) {
      return GraphTypes.isRadar(info.getRTChartType()) || GraphTypes.isRadar(info.getChartType());
   }

   private static void addText(List<ChartRef> fields, AestheticRef text) {
      if(text != null && text.getDataRef() instanceof ChartRef ref) {
         fields.add(ref);
      }
   }

   private static void addAll(List<ChartRef> fields, ChartRef... refs) {
      if(refs != null) {
         for(ChartRef ref : refs) {
            if(ref != null) {
               fields.add(ref);
            }
         }
      }
   }

   private static String fieldList(Set<String> names) {
      return names.isEmpty() ? " (this chart has none)" :
         " (this chart's are: " + String.join(", ", names) + ")";
   }

   /**
    * Refuses {@code applyRow:true} on an assembly whose dialog model does not support it (a
    * Crosstab, whose {@code TableDataVSAssemblyInfo} sibling class never wires up row highlights).
    *
    * <p>Without this the highlight was silently dropped: {@code HighlightDialogService} only
    * stores a row-level highlight for a {@code TableVSAssemblyInfo}, so an {@code applyRow:true}
    * highlight on a Crosstab is excluded from the per-cell store AND never reaches the row store —
    * stored nowhere, with the call still reporting success.
    */
   /**
    * Refuses a condition on a field the active date comparison derives (e.g. "Change from
    * previous year of Year(D): Sum(M)"). It is listed as a conditionable field while the
    * comparison is on, but it has no design-time field behind it, so the condition dangles once the comparison is
    * cleared or changed. Chart; the crosstab is the overload below. Matched case-insensitively, the way {@link ConditionVocabulary} resolves field names.
    */
   static void requireNoDerivedConditionField(VSAssembly assembly, Highlight highlight) {
      Set<String> dimensions = new HashSet<>();
      Set<String> derived = derivedDateComparisonFields(assembly, dimensions);
      requireNoDerivedConditionField(highlight,
         new HighlightService.DateComparisonFields(derived, Set.of()), dimensions);
   }

   /**
    * The crosstab half: refuses a condition on a field a crosstab lists only because of the active
    * date comparison (a derived measure, a date dimension it re-leveled or inserted, a temp group).
    * A derived measure's highlight is deleted by the next binding apply even with the comparison
    * kept on; a re-leveled dimension's is deleted when the comparison is cleared; an inserted
    * period dimension's dangles. {@code fields} is classified in the listed names (see
    * {@link HighlightService#getDateComparisonFields(RuntimeViewsheet, VSAssembly,
    * inetsoft.report.TableDataPath)}); a field it cannot classify is refused, not let through.
    */
   static void requireNoDerivedConditionField(Highlight highlight,
                                              HighlightService.DateComparisonFields fields)
   {
      requireNoDerivedConditionField(highlight, fields, Set.of());
   }

   /**
    * @param dimensions the lower-cased derived names that are dimensions, so the refusal does not
    *                   tell the caller to move a dimension condition onto a measure.
    */
   private static void requireNoDerivedConditionField(Highlight highlight,
                                                      HighlightService.DateComparisonFields fields,
                                                      Set<String> dimensions)
   {
      if(highlight.conditions() == null || highlight.conditions().isEmpty() ||
         fields.isEmpty())
      {
         return;
      }

      int index = 0;

      for(ConditionVocabulary.Clause clause : highlight.conditions()) {
         index++;
         if(clause == null) {
            continue;
         }

         String field = clause.field() == null ? "" : clause.field().trim();
         requireNotDerived(fields, dimensions, index, "field", field);

         // A field-typed value operand ({type:"field", field}) and a ranking value's groupField
         // name a column just like the condition field does; matched on the raw JSON shape, before
         // ConditionVocabulary parses it, so anything unrecognised is left for its own error.
         for(Object value : clause.values() == null ? List.of() : clause.values()) {
            if(!(value instanceof Map<?, ?> map)) {
               continue;
            }

            if(map.get("type") instanceof String type && "field".equals(type.trim().toLowerCase()) &&
               map.get("field") instanceof String operand)
            {
               requireNotDerived(fields, dimensions, index, "value operand field", operand.trim());
            }

            if(map.get("groupField") instanceof String group) {
               requireNotDerived(fields, dimensions, index, "ranking groupField", group.trim());
            }
         }
      }
   }

   private static void requireNotDerived(HighlightService.DateComparisonFields fields,
                                         Set<String> dimensions, int index, String role,
                                         String field)
   {
      if(fields.isDerived(field)) {
         String advice = dimensions.contains(field.toLowerCase())
            ? "Put the condition on a field that is listed without the date comparison, " +
               "or clear the date comparison first."
            : "Put the condition on the base measure instead.";
         throw new IllegalArgumentException(
            "Condition " + index + "'s " + role + " '" + field + "' is derived by the active date " +
            "comparison and has no design-time field behind it, so the highlight would be " +
            "lost or left dangling as soon as the comparison changes or is cleared. " +
            advice);
      }

      if(fields.isAmbiguous(field)) {
         throw new IllegalArgumentException(
            "Condition " + index + "'s " + role + " '" + field + "' cannot be told apart from a " +
            "field the active date comparison derives, so it is refused rather than risk a " +
            "highlight that is lost or left dangling once the comparison changes or is " +
            "cleared. Put the condition on a field that is listed without the comparison, " +
            "or clear the date comparison first.");
      }
   }

   private static Set<String> derivedDateComparisonFields(VSAssembly assembly,
                                                          Set<String> dimensions)
   {
      Set<String> names = new HashSet<>();

      if(assembly != null && assembly.getInfo() instanceof ChartVSAssemblyInfo info) {
         VSChartInfo chartInfo = info.getVSChartInfo();
         ChartRef[] refs = chartInfo == null ? null : chartInfo.getRuntimeDateComparisonRefs();

         for(ChartRef ref : refs == null ? new ChartRef[0] : refs) {
            String name = ref.getFullName();

            if(HighlightDialogService.isDerivedDateComparisonRef(chartInfo, name, ref) ||
               ref instanceof XDimensionRef && chartInfo.getFieldByName(name, false) == null)
            {
               names.add(name);

               if(ref instanceof XDimensionRef) {
                  dimensions.add(name.toLowerCase());
               }
            }
         }

         XDimensionRef[] tempGroups = chartInfo == null ? null : chartInfo.getDcTempGroups();

         for(XDimensionRef ref : tempGroups == null ? new XDimensionRef[0] : tempGroups) {
            String name = ref.getFullName();

            if(chartInfo.getFieldByName(name, false) == null) {
               names.add(name);
               dimensions.add(name.toLowerCase());
            }
         }
      }

      return names;
   }

   private static void requireRowApplicable(HighlightDialogModel model, String assemblyName,
                                            Highlight highlight)
   {
      if(!highlight.applyRow() || model.isShowRow()) {
         return;
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' does not support 'applyRow' — apply-to-row highlights are only " +
         "available on assemblies whose dialog model shows the row option (e.g. a plain table), " +
         "not this one. Drop 'applyRow' to highlight the cell instead.");
   }

   /**
    * The highlight names already taken on this assembly.
    *
    * <p>Derived from the highlights themselves, not from {@code getUsedHighlightNames()} alone.
    * That array is populated by the dialog for its own editing flow and comes back empty on a plain
    * read, so an assembly carrying a highlight reported {@code usedNames: []} — while
    * {@link #set} refused that very name, because its duplicate check walks {@code getHighlights()}.
    * A caller told to "call list_highlights to see what is taken" was therefore told the wrong
    * thing, then refused. Both answers now come from the same place.
    *
    * <p>The dialog's own array is still unioned in: it is authoritative for names reserved outside
    * the highlights currently on this region, and dropping it would trade one gap for another.
    */
   private static List<String> usedNames(HighlightDialogModel model) {
      // Case-insensitively deduplicated, because set()'s refusal compares that way.
      Map<String, String> byLower = new LinkedHashMap<>();

      if(model.getHighlights() != null) {
         for(HighlightModel highlight : model.getHighlights()) {
            if(highlight != null && highlight.getName() != null) {
               byLower.putIfAbsent(highlight.getName().toLowerCase(), highlight.getName());
            }
         }
      }

      if(model.getUsedHighlightNames() != null) {
         for(String name : model.getUsedHighlightNames()) {
            if(name != null) {
               byLower.putIfAbsent(name.toLowerCase(), name);
            }
         }
      }

      return new ArrayList<>(byLower.values());
   }

   private static List<String> fieldNames(HighlightDialogModel model) {
      return ConditionVocabulary.canonicalNames(model.getFields());
   }

   private final ViewsheetSessionService sessions;
   private final HighlightDialogService highlightService;
   private final ExpressionChecker checker;
}
