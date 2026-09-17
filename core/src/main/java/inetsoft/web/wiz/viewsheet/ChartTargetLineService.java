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

import inetsoft.graph.GraphConstants;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.adhoc.model.property.ColorInfo;
import inetsoft.web.adhoc.model.property.MeasureInfo;
import inetsoft.web.adhoc.model.property.TargetInfo;
import inetsoft.web.composer.model.vs.ChartAdvancedPaneModel;
import inetsoft.web.composer.model.vs.ChartPropertyDialogModel;
import inetsoft.web.composer.model.vs.ChartTargetLinesPaneModel;
import inetsoft.web.composer.vs.dialog.ChartPropertyDialogService;
import inetsoft.web.viewsheet.service.ChartPropertyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * A chart's target (goal) lines: list them, add a fixed-value one, remove one.
 *
 * <p>The underlying field — {@code chartAdvancedPaneModel.chartTargetLinesPaneModel.chartTargets},
 * a {@code TargetInfo[]} — is unreachable through {@code set_assembly_properties}:
 * {@link PropertyPath#coerce} builds arrays of primitives, {@code String} and enums, and nothing
 * else, so a JSON object array is refused outright. That refusal is correct, and this service is
 * the success path behind it. {@code PropertyAliases}' own refusal points here.
 *
 * <p><b>Hand-writing a TargetInfo is not merely inconvenient, it is a minefield</b>, which is why
 * the agent-facing surface is scalars and every bean is built here. Each of these produces a
 * target that saves, lists back, survives a reload — and draws nothing:
 * <ul>
 * <li>{@code ChartPropertyService.updateTargetCommonInfo} dereferences {@code measure},
 * {@code lineColor}, {@code fillAboveColor} and {@code fillBelowColor} with no null checks, and
 * runs {@code alpha} through {@code Integer.parseInt}. A partial bean NPEs inside the commit.</li>
 * <li>{@code updateTarget} dispatches on {@code tabFlag} and does <i>nothing at all</i> for a
 * value outside 0/1/2.</li>
 * <li>{@code updateAllTargets} only calls {@code addTarget} when {@code index == -1}. An
 * {@code index >= 0} on a chart with no targets builds a {@code GraphTarget} and discards it.</li>
 * <li>A non-numeric constant reaches {@code TargetStrategyWrapper.runtimeValueOf}, which drops
 * unparseable values, leaving a strategy with zero boundaries and a target with no geometry.</li>
 * <li>{@code isFormulaSupported} reads the five literal strings Average/Min/Max/Median/Sum as
 * <i>formulas</i>, so a "fixed value" of {@code "Max"} silently becomes a data-derived line.</li>
 * <li>{@code supportsTarget} is computed on read and never consulted on write, so a target on a
 * pie or a treemap is stored and simply never drawn.</li>
 * </ul>
 *
 * <p><b>Pre-existing targets are written back with {@code changed = false}.</b>
 * {@code updateAllTargets} skips an unchanged entry outright; a changed one is re-derived from the
 * model, and that round trip is lossy — a statistics target's multiple labels are joined with
 * commas on read and come back as one escaped label, and a band's default fill is re-applied as an
 * explicit user colour. Only the entry this call appends is marked changed. As a side effect the
 * silent-drop branch above becomes structurally unreachable from here.
 *
 * <p>Scope is deliberately one kind of target: a <b>fixed-value line</b>
 * ({@code TargetInfo.LINE_TARGET}). Bands and statistics targets are listed and removable — an
 * agent needs their indexes for that — but not creatable, because their own required shapes
 * ({@code StrategyInfo}, {@code CategoricalColorModel}) carry the same class of trap and no one
 * has asked for them.
 */
@Service
public class ChartTargetLineService {
   @Autowired
   public ChartTargetLineService(ViewsheetSessionService sessions,
                                 ChartPropertyDialogService chartService)
   {
      this.sessions = sessions;
      this.chartService = chartService;
   }

   /**
    * {@code list_chart_target_lines}. Every target on the chart, with the index each one is
    * addressed by, plus what this chart can accept.
    */
   public Map<String, Object> list(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      requireChart(rvs, assemblyName);
      ChartPropertyDialogModel model =
         chartService.getChartPropertyDialogModel(rvs.getID(), assemblyName, user);
      ChartTargetLinesPaneModel pane = requirePane(model, assemblyName);

      List<Map<String, Object>> targets = new ArrayList<>();
      TargetInfo[] existing = targets(pane);

      for(int i = 0; i < existing.length; i++) {
         targets.add(describe(existing[i], i));
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);
      out.put("targets", targets);
      out.put("supportsTarget", pane.isSupportsTarget());
      out.put("availableMeasures", measureNames(pane));
      out.put("lineStyles", new ArrayList<>(LINE_STYLES.keySet()));

      // A map chart reports supportsTarget == true, but GraphGenerator.addTarget returns early
      // for a geo measure, so the target never draws. Reported rather than refused, because a
      // non-geo measure on the same map is fine.
      if(pane.isMapInfo()) {
         out.put("mapInfo", true);
         out.put("note",
                 "This is a map. A target line bound to a geographic measure is dropped when the " +
                 "graph is generated, even though it saves and lists back here.");
      }

      return out;
   }

   /**
    * {@code add_chart_target_line}. Appends one fixed-value line target.
    *
    * <p>One {@code sessions.mutate}, so one undo checkpoint.
    *
    * @param measure   the measure the line is drawn against, {@code "all"} for an unbound line,
    *                  or null/blank to bind the chart's only measure.
    * @param value     the constant the line sits at.
    * @param label     the line's label; {@code {0}} interpolates the value. Defaults to
    *                  {@code "{0}"}.
    * @param lineStyle one of {@link #LINE_STYLES}, or null for the chart's default.
    * @param lineColor {@code #RRGGBB}, or null for the chart's default target colour.
    */
   public Map<String, Object> add(String sessionToken, Principal user, String assemblyName,
                                  String measure, String value, String label, String lineStyle,
                                  String lineColor, String linkUri)
      throws Exception
   {
      String constant = requireFixedValue(value);
      String style = normalizeLineStyle(lineStyle);
      String color = normalizeColor(lineColor);
      Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         ChartPropertyDialogModel model =
            chartService.getChartPropertyDialogModel(runtimeId, assemblyName, user);
         ChartTargetLinesPaneModel pane = requirePane(model, assemblyName);

         if(!pane.isSupportsTarget()) {
            throw new IllegalArgumentException(
               "This chart (" + describeChartTypes(chart) + ") has no target lines. The Composer " +
               "hides the Targets tab for it, and nothing in the write path checks -- a target " +
               "added here would be stored, listed back, saved, and never drawn. Change the " +
               "chart type with set_chart_type first if a goal line is what you want.");
         }

         TargetInfo prototype = pane.getNewTargetInfo();

         if(prototype == null) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' offers no target-line template, so there is no shape to " +
               "build a target from. That is a chart the Targets tab cannot edit either.");
         }

         MeasureInfo bound = resolveMeasure(pane, measure, assemblyName);
         TargetInfo target = fromPrototype(prototype);
         target.setMeasure(bound);
         target.setValue(constant);
         target.setLabel(label == null || label.isBlank() ? DEFAULT_LABEL : label);

         if(style != null) {
            target.setLineStyle(LINE_STYLES.get(style));
         }

         if(color != null) {
            target.setLineColor(new ColorInfo(color, ChartPropertyService.COLOR_PALETTE));
         }

         // The three that updateAllTargets/updateTarget read structurally, set together because
         // getting any one of them wrong is a silent no-op rather than an error. index == -1 is
         // the only value that reaches addTarget.
         target.setTabFlag(TargetInfo.LINE_TARGET);
         target.setIndex(-1);
         target.setChanged(true);

         TargetInfo[] existing = targets(pane);
         markUnchanged(existing);
         TargetInfo[] updated = Arrays.copyOf(existing, existing.length + 1);
         updated[existing.length] = target;
         pane.setChartTargets(updated);
         // Never both in one call: deletedIndexList is applied by removeDeletedTargets AFTER
         // updateAllTargets has appended, so its indexes would refer to a list that has moved.
         pane.setDeletedIndexList(new Integer[0]);

         chartService.setChartPropertyModel(runtimeId, assemblyName, model, linkUri, user,
                                            dispatcher);

         result.put("assembly", assemblyName);
         result.put("index", existing.length);
         result.put("measure", bound.getName().isEmpty() ? ALL_MEASURES : bound.getName());
         result.put("value", constant);
         result.put("label", target.getLabel());
         result.put("lineStyle", styleName(target.getLineStyle()));
      });

      return result;
   }

   /**
    * {@code remove_chart_target_line}. Removes targets by the indexes {@link #list} reports.
    *
    * <p>Indexes renumber after the call, so a caller removing several must pass them all at once
    * (they are read as positions in the pre-write list, which is what {@code removeDeletedTargets}
    * expects) rather than looping.
    */
   public Map<String, Object> remove(String sessionToken, Principal user, String assemblyName,
                                     List<Integer> indexes, String linkUri)
      throws Exception
   {
      List<Integer> wanted = requireIndexes(indexes);
      Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireChart(rvs, assemblyName);
         ChartPropertyDialogModel model =
            chartService.getChartPropertyDialogModel(runtimeId, assemblyName, user);
         ChartTargetLinesPaneModel pane = requirePane(model, assemblyName);
         TargetInfo[] existing = targets(pane);

         for(int index : wanted) {
            if(index < 0 || index >= existing.length) {
               throw new IllegalArgumentException(
                  "Chart '" + assemblyName + "' has no target at index " + index + ". " +
                  (existing.length == 0
                     ? "It has no targets at all."
                     : "Current indexes: 0-" + (existing.length - 1) + ".") +
                  " Take indexes from list_chart_target_lines -- they are positions in the " +
                  "chart's target list and they renumber after every removal.");
            }
         }

         List<TargetInfo> kept = new ArrayList<>();
         List<Integer> deleted = new ArrayList<>();

         for(int i = 0; i < existing.length; i++) {
            if(wanted.contains(i)) {
               // The LIST POSITION, not the target's own index field. removeDeletedTargets
               // addresses the descriptor positionally -- cDescp.getTarget(n) is targets.get(n)
               // -- and chartTargets was built by walking those same positions in order, so i
               // is the correct address. The two normally agree, but SyncChartHandler clones
               // only the targets whose field is still bound and keeps each clone's original
               // index, which leaves gaps: with indexes {1, 2}, sending the index field would
               // delete the wrong target for position 0 and run off the end for position 1.
               deleted.add(i);
            }
            else {
               kept.add(existing[i]);
            }
         }

         // Both halves, mirroring what the Composer's own target pane does: the model must
         // describe the post-write state, and leaving a doomed entry in chartTargets would also
         // re-derive it through updateAllTargets on the way past.
         TargetInfo[] survivors = kept.toArray(new TargetInfo[0]);
         markUnchanged(survivors);
         pane.setChartTargets(survivors);
         pane.setDeletedIndexList(deleted.toArray(new Integer[0]));

         chartService.setChartPropertyModel(runtimeId, assemblyName, model, linkUri, user,
                                            dispatcher);

         // A refused write does not throw here. VSObjectPropertyService.editObjectProperty
         // dispatches an ERROR MessageCommand and returns false, and ViewsheetSessionService
         // turns that into a CommandErrorException only once this lambda has returned -- so the
         // count check below would run first and report "left N where M was expected", claiming
         // a partial apply for a concurrent-edit conflict that applied nothing at all. Leaving
         // now lets the real error surface. (The dispatcher is always present in production;
         // the null is for the unit harness, which drives the mutation directly.)
         if(dispatcher != null && !dispatcher.getErrors().isEmpty()) {
            return;
         }

         // ChartDescriptor.removeTarget is equals-based on a GraphTarget whose equals() includes
         // every field, addressed here by position. Distinct positions make a mis-hit impossible
         // in practice; confirming costs one read on a path that is not hot, and the alternative
         // is reporting a removal that did not happen.
         ChartPropertyDialogModel after =
            chartService.getChartPropertyDialogModel(runtimeId, assemblyName, user);
         int remaining = targets(requirePane(after, assemblyName)).length;

         if(remaining != existing.length - wanted.size()) {
            throw new IllegalStateException(
               "Removing " + wanted.size() + " target(s) from '" + assemblyName + "' left " +
               remaining + " where " + (existing.length - wanted.size()) + " was expected. The " +
               "edit has been applied as far as it got and is covered by one undo step; re-read " +
               "with list_chart_target_lines before trying again.");
         }

         result.put("assembly", assemblyName);
         result.put("removed", wanted);
         result.put("remaining", remaining);
      });

      return result;
   }

   /**
    * Copies the pane's own {@code newTargetInfo} template rather than constructing a
    * {@code TargetInfo}.
    *
    * <p>The template is {@code getTargetInfo(info, new GraphTarget(), rt)} — it already carries a
    * non-null {@code MeasureInfo}, three non-null {@code ColorInfo}s, a numeric {@code alpha} and
    * a real {@code lineStyle}, every one of which the commit path dereferences without checking.
    * Field-by-field rather than a deep clone because the fields worth carrying are exactly the
    * ones listed here: the rest are either set by the caller below or read only for band and
    * statistics targets.
    */
   private static TargetInfo fromPrototype(TargetInfo prototype) {
      TargetInfo target = new TargetInfo();
      target.setMeasure(prototype.getMeasure());
      target.setGenericLabel(prototype.getGenericLabel());
      target.setChartScope(prototype.isChartScope());
      target.setLineStyle(prototype.getLineStyle());
      target.setLineColor(prototype.getLineColor());
      target.setFillAboveColor(prototype.getFillAboveColor());
      target.setFillBelowColor(prototype.getFillBelowColor());
      target.setFillBandColor(prototype.getFillBandColor());
      target.setAlpha(prototype.getAlpha());
      target.setStrategyInfo(prototype.getStrategyInfo());
      target.setBandFill(prototype.getBandFill());
      target.setSupportFill(prototype.isSupportFill());
      return target;
   }

   /** See the class note: an unchanged target is skipped rather than re-derived lossily. */
   private static void markUnchanged(TargetInfo[] targets) {
      for(TargetInfo target : targets) {
         if(target != null) {
            target.setChanged(false);
         }
      }
   }

   /**
    * Resolves the measure the line is drawn against, always by copying an entry out of the
    * model's own {@code availableFields}.
    *
    * <p>Never by constructing a {@code MeasureInfo} from the name: {@code updateTargetCommonInfo}
    * propagates {@code isDateField}/{@code isTimeField} into both the target and its parameter
    * wrapper, and those flags decide whether the constant is parsed as a number or as a date.
    *
    * <p>The names come from {@code availableFields}, which the read side builds with
    * {@code rt = appliedDateComparison || hasDynamic(info)}. That looks inconsistent with a plain
    * design-time read and is not: those are the runtime names {@code GraphGenerator.addTarget}
    * matches the target's field against, so anything else would bind a target the graph then
    * skips.
    */
   private static MeasureInfo resolveMeasure(ChartTargetLinesPaneModel pane, String measure,
                                             String assemblyName)
   {
      MeasureInfo[] available = pane.getAvailableFields() == null
         ? new MeasureInfo[0] : pane.getAvailableFields();
      List<MeasureInfo> real = new ArrayList<>();
      MeasureInfo unbound = null;

      for(MeasureInfo field : available) {
         if(field == null) {
            continue;
         }

         if(field.getName() == null || field.getName().isEmpty()) {
            unbound = field;
         }
         else {
            real.add(field);
         }
      }

      String wanted = measure == null ? "" : measure.trim();

      if(ALL_MEASURES.equalsIgnoreCase(wanted)) {
         return copy(unbound == null ? new MeasureInfo("", "", false) : unbound);
      }

      if(wanted.isEmpty()) {
         if(real.size() == 1) {
            return copy(real.get(0));
         }

         if(real.isEmpty()) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' has no measure to draw a target line against. Bind an " +
               "aggregate to the chart first, or pass measure:\"" + ALL_MEASURES + "\" for a " +
               "line drawn once against the plot's default scale.");
         }

         throw new IllegalArgumentException(
            "'" + assemblyName + "' has more than one measure (" + names(real) + "), so " +
            "'measure' is required: a target line with no measure is drawn once against the " +
            "plot's default scale, not once per measure, and on a dual-axis chart that is not " +
            "the axis you meant. Pass measure:\"" + ALL_MEASURES + "\" if you do want the " +
            "single unbound line.");
      }

      for(MeasureInfo field : real) {
         if(wanted.equalsIgnoreCase(field.getName()) || wanted.equalsIgnoreCase(field.getLabel())) {
            if(field.isDateField() || field.isTimeField()) {
               throw new IllegalArgumentException(
                  "'" + field.getName() + "' is a " + (field.isTimeField() ? "time" : "date") +
                  " field. This tool writes fixed numeric target lines; on a date-valued target " +
                  "the value is parsed as a date and silently dropped when it does not match. " +
                  "Pick a numeric measure.");
            }

            return copy(field);
         }
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' has no measure '" + measure + "'. It has: " +
         (real.isEmpty() ? "(none)" : names(real)) + ". Use measure:\"" + ALL_MEASURES +
         "\" for a line that is not bound to any of them.");
   }

   private static MeasureInfo copy(MeasureInfo measure) {
      return new MeasureInfo(measure.getName(), measure.getLabel(), measure.isDateField(),
                             measure.isTimeField(), measure.isGroupOthers());
   }

   /**
    * Refuses everything that would store a target the graph cannot draw.
    *
    * <p>Two distinct traps, deliberately named separately in the messages: a value that does not
    * parse yields a strategy with no boundaries (a target that exists and has no geometry), and
    * one of the five formula names yields a data-derived line (a target that draws, in the wrong
    * place, having quietly ignored the "fixed" in fixed-value).
    */
   private static String requireFixedValue(String value) {
      String text = value == null ? "" : value.trim();

      if(text.isEmpty()) {
         throw new IllegalArgumentException(
            "add_chart_target_line needs 'value' -- the number the line sits at.");
      }

      for(String formula : FORMULA_NAMES) {
         if(formula.equalsIgnoreCase(text)) {
            throw new IllegalArgumentException(
               "'" + value + "' is one of the five names the Composer reads as a formula (" +
               String.join(", ", FORMULA_NAMES) + "), so it would produce a data-derived line " +
               "rather than the fixed one you asked for. Give a number instead; statistics " +
               "targets are not settable through this tool.");
         }
      }

      if(!NUMERIC.matcher(text).matches() || !Double.isFinite(Double.parseDouble(text))) {
         throw new IllegalArgumentException(
            "'" + value + "' is not a number. A fixed-value target line stores its value as a " +
            "constant; a value that does not parse as a double produces a target with no " +
            "boundaries -- it saves, it lists back, and it draws nothing. Give a plain number " +
            "(1000, -2.5); thousands separators, currency symbols and expressions do not parse.");
      }

      return text;
   }

   private static String normalizeLineStyle(String lineStyle) {
      if(lineStyle == null || lineStyle.isBlank()) {
         return null;
      }

      String wanted = lineStyle.trim().toLowerCase().replace('-', '_').replace(' ', '_');

      if(!LINE_STYLES.containsKey(wanted)) {
         throw new IllegalArgumentException(
            "'" + lineStyle + "' is not a line style. Valid values: " +
            String.join(", ", LINE_STYLES.keySet()) + ".");
      }

      return wanted;
   }

   private static String normalizeColor(String lineColor) {
      if(lineColor == null || lineColor.isBlank()) {
         return null;
      }

      String color = lineColor.trim();
      color = color.startsWith("#") ? color : "#" + color;

      if(!HEX_COLOR.matcher(color).matches()) {
         throw new IllegalArgumentException(
            "'" + lineColor + "' is not a colour. Give #RRGGBB, e.g. #cc0000.");
      }

      return color.toLowerCase();
   }

   private static List<Integer> requireIndexes(List<Integer> indexes) {
      if(indexes == null || indexes.isEmpty()) {
         throw new IllegalArgumentException(
            "remove_chart_target_line needs 'indexes' -- the target positions " +
            "list_chart_target_lines reports.");
      }

      List<Integer> wanted = new ArrayList<>();

      for(Integer index : indexes) {
         if(index == null) {
            throw new IllegalArgumentException("'indexes' contains a null entry.");
         }

         if(!wanted.contains(index)) {
            wanted.add(index);
         }
      }

      return wanted;
   }

   /** What {@link #list} reports per target. */
   private static Map<String, Object> describe(TargetInfo target, int position) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("index", position);
      out.put("kind", kind(target.getTabFlag()));

      MeasureInfo measure = target.getMeasure();
      out.put("measure", measure == null || measure.getName() == null ||
                         measure.getName().isEmpty() ? ALL_MEASURES : measure.getName());
      out.put("value", target.getValue());
      out.put("label", target.getLabel());
      out.put("lineStyle", styleName(target.getLineStyle()));

      ColorInfo color = target.getLineColor();
      out.put("lineColor", color == null ? null : color.getColor());

      if(target.getTabFlag() != TargetInfo.LINE_TARGET) {
         out.put("editable", false);
         out.put("note", "Band and statistics targets are read-only here -- this tool writes " +
                         "fixed-value lines. The index is still what remove_chart_target_line " +
                         "takes. Use the Composer's Targets tab to edit it.");
      }

      return out;
   }

   private static String kind(int tabFlag) {
      return switch(tabFlag) {
         case TargetInfo.LINE_TARGET -> "line";
         case TargetInfo.BAND_TARGET -> "band";
         case TargetInfo.STATISTICS_TARGET -> "statistics";
         default -> "unknown(" + tabFlag + ")";
      };
   }

   private static String styleName(int lineStyle) {
      for(Map.Entry<String, Integer> style : LINE_STYLES.entrySet()) {
         if(style.getValue() == lineStyle) {
            return style.getKey();
         }
      }

      return String.valueOf(lineStyle);
   }

   private static List<String> measureNames(ChartTargetLinesPaneModel pane) {
      List<String> names = new ArrayList<>();

      for(MeasureInfo measure : pane.getAvailableFields() == null
         ? new MeasureInfo[0] : pane.getAvailableFields())
      {
         if(measure != null && measure.getName() != null && !measure.getName().isEmpty()) {
            names.add(measure.getName());
         }
      }

      return names;
   }

   private static String names(List<MeasureInfo> measures) {
      List<String> names = new ArrayList<>();

      for(MeasureInfo measure : measures) {
         names.add(measure.getName());
      }

      return String.join(", ", names);
   }

   private static TargetInfo[] targets(ChartTargetLinesPaneModel pane) {
      return pane.getChartTargets() == null ? new TargetInfo[0] : pane.getChartTargets();
   }

   /**
    * The pane is populated on every read by {@code ChartPropertyDialogService}, and
    * {@code setChartPropertyModel} dereferences it unguarded on the way back in — so this service
    * must only ever write a model it just read, never one it built.
    */
   private static ChartTargetLinesPaneModel requirePane(ChartPropertyDialogModel model,
                                                        String assemblyName)
   {
      ChartAdvancedPaneModel advanced = model == null ? null : model.getChartAdvancedPaneModel();
      ChartTargetLinesPaneModel pane =
         advanced == null ? null : advanced.getChartTargetLinesPaneModel();

      if(pane == null) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' has no target-lines pane on its property dialog, so there is " +
            "nothing to read or write.");
      }

      return pane;
   }

   private static ChartVSAssembly requireChart(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(!(assembly instanceof ChartVSAssembly chart)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a chart. Target lines only exist on charts.");
      }

      return chart;
   }

   /**
    * Names the chart type(s) in the refusal, because "targets are not supported" with no reason
    * sends the reader to the Composer to find out which of a multi-style chart's measures is the
    * one without them. {@code supportsTarget} is false when <i>any</i> aggregate has a no-target
    * type, so a multi-style chart lists them all.
    */
   private static String describeChartTypes(ChartVSAssembly chart) {
      VSChartInfo info = chart.getVSChartInfo();

      if(info == null) {
         return "unknown type";
      }

      Set<String> types = new LinkedHashSet<>();

      if(info.isMultiStyles()) {
         collectTypes(info.getXFields(), types);
         collectTypes(info.getYFields(), types);
      }
      else {
         types.add(typeName(info.getChartType()));
      }

      return types.isEmpty() ? "unknown type" : String.join(", ", types);
   }

   private static void collectTypes(ChartRef[] fields, Set<String> types) {
      for(ChartRef field : fields == null ? new ChartRef[0] : fields) {
         if(field instanceof ChartAggregateRef aggregate) {
            types.add(typeName(aggregate.getChartType()));
         }
      }
   }

   /**
    * Only the types that can reach this refusal need a name — {@code NO_TARGET_STYLES}, plus a
    * numeric fallback. A full int-to-name table lives in {@code WizAutoBindingService}, private
    * to it, and copying 40 entries to name one of 17 would be the larger duplication.
    */
   private static String typeName(int chartType) {
      return switch(chartType) {
         case GraphTypes.CHART_PIE -> "pie";
         case GraphTypes.CHART_DONUT -> "donut";
         case GraphTypes.CHART_3D_PIE -> "3D pie";
         case GraphTypes.CHART_RADAR -> "radar";
         case GraphTypes.CHART_FILL_RADAR -> "filled radar";
         case GraphTypes.CHART_TREEMAP -> "treemap";
         case GraphTypes.CHART_ICICLE -> "icicle";
         case GraphTypes.CHART_SUNBURST -> "sunburst";
         case GraphTypes.CHART_CIRCLE_PACKING -> "circle packing";
         case GraphTypes.CHART_MEKKO -> "mekko";
         case GraphTypes.CHART_TREE -> "tree";
         case GraphTypes.CHART_NETWORK -> "network";
         case GraphTypes.CHART_CIRCULAR -> "circular network";
         case GraphTypes.CHART_FUNNEL -> "funnel";
         case GraphTypes.CHART_GANTT -> "gantt";
         case GraphTypes.CHART_SCATTER_CONTOUR -> "scatter contour";
         case GraphTypes.CHART_MAP_CONTOUR -> "contour map";
         default -> "chart type " + chartType;
      };
   }

   /** {@code measure:"all"} — the blank MeasureInfo the composer itself offers first. */
   static final String ALL_MEASURES = "all";
   private static final String DEFAULT_LABEL = "{0}";
   /** {@code ChartPropertyService.isFormulaSupported}'s literals. */
   private static final String[] FORMULA_NAMES = {"Average", "Min", "Max", "Median", "Sum"};
   private static final Pattern NUMERIC =
      Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
   private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

   /** The {@code GraphConstants} line styles, named. Insertion order is the listed order. */
   private static final Map<String, Integer> LINE_STYLES;

   static {
      Map<String, Integer> styles = new LinkedHashMap<>();
      styles.put("ultra_thin", GraphConstants.ULTRA_THIN_LINE);
      styles.put("thin_thin", GraphConstants.THIN_THIN_LINE);
      styles.put("thin", GraphConstants.THIN_LINE);
      styles.put("medium", GraphConstants.MEDIUM_LINE);
      styles.put("thick", GraphConstants.THICK_LINE);
      styles.put("dotted", GraphConstants.DOT_LINE);
      styles.put("dashed", GraphConstants.DASH_LINE);
      styles.put("medium_dash", GraphConstants.MEDIUM_DASH);
      styles.put("large_dash", GraphConstants.LARGE_DASH);
      LINE_STYLES = Collections.unmodifiableMap(styles);
   }

   private final ViewsheetSessionService sessions;
   private final ChartPropertyDialogService chartService;
}
