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
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.AxisDescriptor;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.TitleDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.composer.vs.dialog.RegionPropertyDialogService;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.graph.model.dialog.AxisLinePaneModel;
import inetsoft.web.graph.model.dialog.AxisPropertyDialogModel;
import inetsoft.web.graph.model.dialog.LegendFormatDialogModel;
import inetsoft.web.graph.model.dialog.ModelAlias;
import inetsoft.web.graph.model.dialog.TitleFormatDialogModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Properties of a chart's <b>sub-elements</b>: its axes, legends and titles.
 *
 * <p>These are the three dialogs {@code AssemblyPropertyService} could not reach. Every property
 * it handles is addressed by assembly alone; these are addressed by assembly <em>and a region
 * within it</em> — an axis by its type, a legend by its index, a title by which title it is. That
 * extra key is the whole reason they were deferred.
 *
 * <p>Everything else is deliberately the same as the assembly property engine: names resolve
 * through an alias table onto {@link PropertyPath} paths, values coerce the same way, and the
 * patch is validated whole before any of it is applied — so one bad key does not leave the others
 * written.
 */
@Service
public class ChartRegionPropertyService {
   @Autowired
   public ChartRegionPropertyService(ViewsheetSessionService sessions,
                                     RegionPropertyDialogService regions,
                                     ChartRegionHandler regionHandler)
   {
      this.sessions = sessions;
      this.regions = regions;
      this.regionHandler = regionHandler;
   }

   /** The regions and what each one's {@code target} means. */
   public Map<String, Object> vocabulary() {
      return Map.of(
         "regions", List.of("axis", "legend", "title"),
         "target", Map.of(
            "axis", "the axis type — y, y2, x, x2 — but only the ones this chart has; pass " +
               "the assembly to list_chart_elements to see which, since a y2 or x2 exists only " +
               "when a measure uses the secondary axis",
            "legend", "the legend's 0-based index",
            "title", "which axis title — x, x2, y, y2, and only ones this chart has. NOT the " +
               "chart's own title: its text/visibility are set_assembly_properties 'title' and " +
               "'titleVisible', and its font/color are set_format {assemblies: [chart], target: " +
               "'title'}"),
         "note", "An axis may also need 'field' when a chart has more than one axis of a type.");
   }

   /** Property names for a region, with their current values. */
   public Map<String, Object> list(String sessionToken, Principal user, String assembly,
                                   String region, String target, String field)
      throws Exception
   {
      String name = requireRegion(region);
      String key = requireTarget(target, name);
      requireExistingTarget(sessionToken, user, assembly, name, key);

      if("axis".equals(name)) {
         requireUnambiguousAxis(sessionToken, user, assembly, key, field);
      }

      Object model = readModel(sessionToken, user, assembly, name, key, field);
      Map<String, String> aliases = aliasesFor(name);
      List<Map<String, Object>> properties = new ArrayList<>();

      for(Map.Entry<String, String> alias : aliases.entrySet()) {
         Map<String, Object> one = new LinkedHashMap<>();
         one.put("name", alias.getKey());
         one.put("path", alias.getValue());
         one.put("value", PropertyPath.get(model, alias.getValue()));
         properties.add(one);
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assembly);
      out.put("region", name);
      out.put("target", key);
      out.put("properties", properties);
      return out;
   }

   /**
    * Applies a patch to one region.
    *
    * <p>Validated whole before anything is written, so a typo in one key does not leave the rest
    * applied — the same rule the assembly properties follow, and for the same reason: a partly
    * applied patch is worse than a rejected one because nothing reports it.
    */
   public void set(String sessionToken, Principal user, String assembly, String region,
                   String target, String field, Map<String, Object> properties, String linkUri)
      throws Exception
   {
      String name = requireRegion(region);
      String key = requireTarget(target, name);
      requireExistingTarget(sessionToken, user, assembly, name, key);

      if(properties == null || properties.isEmpty()) {
         throw new IllegalArgumentException(
            "set_chart_region_properties needs at least one property. " +
            "list_chart_region_properties reports the names this region accepts.");
      }

      // Item 3 (bug #77027): a raw dotted path that happens to alias exactly onto a known
      // property is normalized back to that property's plain name *before* any keyed guard
      // below runs. Every guard in this class (this one, the increment guard, the rotation/
      // aliases special-casing, the legend renamed-key checks) matches by the bare alias name --
      // a caller could otherwise write "axisLinePaneModel.minimum" on a categorical axis and
      // reproduce the exact corruption requireLinearAxisForLinearOnlyKeys exists to refuse,
      // simply by spelling the same property as its raw model path instead of its alias. A path
      // that does not match any known alias is left untouched -- that is the documented escape
      // hatch this method still supports, not the bug. Also gives every call site below a fresh,
      // mutable map, replacing the old conditional-copy special case for "rotation"/"aliases".
      properties = normalizeToAliasKeys(name, properties);

      if("axis".equals(name)) {
         // First, so a mixed or multi-dimension shelf gets the "pass field" message rather than a
         // misleading linear/non-linear one (bug #78187).
         requireUnambiguousAxis(sessionToken, user, assembly, key, field);
         requireLinearAxisForLinearOnlyKeys(sessionToken, user, assembly, key, field, properties);
         requireNonLinearAxisForNonLinearOnlyKeys(
            sessionToken, user, assembly, key, field, properties);
         requireLinearOrTimeSeriesAxisForIncrement(
            sessionToken, user, assembly, key, field, properties);
      }

      if("legend".equals(name)) {
         requireSymbolSizeInRange(properties);
      }

      if(properties.containsKey("rotation")) {
         properties.put("rotation", canonicalRotation(name, properties.get("rotation")));
      }

      // Shape-validated here, before readModel, so a malformed 'aliases' value keeps failing
      // fast the same way every other bad-shape property in this class does -- with no model
      // fetch spent on a request that was always going to be refused. Resolving each entry's
      // real value needs the model (see toModelAliases), which is fetched below regardless for
      // the main property-write loop; the resolution itself waits until then.
      List<Map<?, ?>> aliasEntries = properties.containsKey("aliases")
                                     && ("axis".equals(name) || "legend".equals(name))
         ? parseAliasEntries(properties.get("aliases")) : null;

      Object model = readModel(sessionToken, user, assembly, name, key, field);

      if(aliasEntries != null) {
         Object current = PropertyPath.get(model, "aliasPaneModel.aliasList");
         properties.put("aliases", toModelAliases(aliasEntries, (ModelAlias[]) current));
      }

      Map<String, String> aliases = aliasesFor(name);
      Map<String, Object> resolved = new LinkedHashMap<>();

      for(Map.Entry<String, Object> property : properties.entrySet()) {
         String propertyKey = property.getKey();
         String path = propertyKey != null && propertyKey.contains(".")
            ? propertyKey
            : aliases.get(propertyKey);

         if(path == null) {
            // Items 4/5 (bug #77027): "visible"/"fillColor" used to be real legend property
            // names, renamed to "titleVisible"/"borderColor" because the old names collided with
            // a different, wider meaning elsewhere (see legend()'s own comment). A caller still
            // spelling the old name gets a message naming the replacement, not the generic
            // "unknown property" list a brand-new typo gets.
            String renamed = "legend".equals(name) ? LEGEND_RENAMED_KEYS.get(propertyKey) : null;

            if(renamed != null) {
               throw new IllegalArgumentException(legendRenamedKeyMessage(propertyKey, renamed));
            }

            throw new IllegalArgumentException(
               "'" + propertyKey + "' is not a property of a chart " + name + ". Known " +
               "names: " + String.join(", ", new TreeSet<>(aliases.keySet())) +
               ". A raw model path (containing a '.') is also accepted.");
         }

         resolved.put(path, property.getValue());
      }

      // Write onto the model only after every key resolved.
      //
      // Keep the returned root. All three region dialog models are plain mutable classes today, so
      // PropertyPath.set mutates in place and the assignment is a no-op — but if any of them (or a
      // pane beneath one) becomes an Immutables model, a wither rebuilds rather than mutates and
      // the write would vanish with no error and no compile failure. That is the same shape that
      // made width/height/preview silently unwritable on the viewsheet's own dialog model.
      Object rebuilt = model;

      for(Map.Entry<String, Object> one : resolved.entrySet()) {
         rebuilt = PropertyPath.set(rebuilt, one.getKey(), one.getValue());
      }

      final Object written = rebuilt;

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         switch(name) {
         case "axis" -> regions.setAxisPropertyDialogModel(
            runtimeId, assembly, axisType(key), 0, field, (AxisPropertyDialogModel) written,
            linkUri, user, dispatcher);
         case "legend" -> regions.setLegendFormatDialogModel(
            runtimeId, assembly, indexOf(key), (LegendFormatDialogModel) written, linkUri, user,
            dispatcher);
         default -> regions.setTitleFormatDialogModel(
            runtimeId, assembly, key, (TitleFormatDialogModel) written, linkUri, user, dispatcher);
         }
      });
   }

   /** Axis properties that only mean something on a linear (measure) axis. */
   private static final Set<String> LINEAR_ONLY_AXIS_KEYS =
      Set.of("reverse", "logarithmicScale", "shared", "minimum", "maximum", "minorIncrement");

   /**
    * Refuses a linear-only axis property (a numeric range, a log scale, a reversed direction...)
    * on an axis that is not linear.
    *
    * <p>{@code AxisPropertyDialogModel.updateAxisPropertyDialogModel} only applies these under
    * {@code if(this.linear)} — correct given its input. The bug is upstream: {@link #readModel}
    * and {@link #set} ask {@code RegionPropertyDialogService.getAxisPropertyDialogModel} for area
    * index {@code 0} unconditionally, and {@code ChartRegionHandler.createAxisPropertyDialogModel}
    * uses that index to <em>infer</em> linearity from whichever leaf area happens to sort first on
    * screen ({@code AxisLineArea} vs. {@code DimensionLabelArea}) — a proxy that is valid for the
    * real Composer, which derives the index from the user's actual click, and meaningless here,
    * where there was no click and the index is always {@code 0}. For an ordinary bottom x-axis the
    * tick/line area sorts before the label area regardless of whether the bound field is a
    * dimension or a measure, so a purely categorical axis (e.g. a year-grouped date dimension)
    * silently comes back {@code isLinear: true}. Confirmed live: {@code minimum:"5"} on such an
    * axis was persisted and rendered as a fabricated numeric range, corrupting the chart.
    *
    * <p>This checks linearity independently, the same way {@code ChartRegionHandler}'s own
    * ref-driven branch does it ({@code ref instanceof ChartAggregateRef}), straight off the
    * binding rather than off the area sort order — and refuses rather than trying to fix the
    * shared area-index mechanism in place, which the real UI also depends on for its own,
    * legitimate click-derived index.
    */
   private void requireLinearAxisForLinearOnlyKeys(String sessionToken, Principal user,
                                                    String assembly, String axisTarget,
                                                    String field, Map<String, Object> properties)
      throws Exception
   {
      Set<String> requested = new TreeSet<>(properties.keySet());
      requested.retainAll(LINEAR_ONLY_AXIS_KEYS);

      if(requested.isEmpty()) {
         return;
      }

      boolean linear = computeTrueAxisKind(sessionToken, user, assembly, axisTarget, field).linear();

      if(!linear) {
         throw new IllegalArgumentException(
            "'" + String.join("', '", requested) + "' " + (requested.size() == 1 ? "only applies" : "only apply") + " to a linear (measure) axis. " +
            "'" + axisTarget + "'" + (field != null && !field.isBlank() ? " ('" + field + "')" : "")
            + " on this chart is bound to a dimension, not a measure, so these would be " +
            "silently ignored — or, on some chart types, corrupt the render instead of being " +
            "ignored. Omit them for a dimension axis.");
      }
   }

   /** Axis properties that only mean something on a non-linear (dimension) axis. */
   private static final Set<String> NON_LINEAR_ONLY_AXIS_KEYS = Set.of("ignoreNull", "truncate");

   /**
    * Refuses {@code ignoreNull}/{@code truncate} on a linear (measure) axis (bug #78187).
    * {@code AxisPropertyDialogModel} only reads and writes them when the axis is not linear, and
    * the Composer UI does not offer them there, so on a measure axis they were accepted and
    * silently dropped -- the mirror image of {@link #requireLinearAxisForLinearOnlyKeys}.
    *
    * <p>A discrete measure ({@code ChartAggregateRef.isDiscrete()}) is deliberately not refused:
    * it is mapped to a dimension-like column, so its axis is expected to be categorical and the
    * UI shows these controls for it. That was not traced through scale creation, so the refusal
    * is simply not widened to it.
    */
   private void requireNonLinearAxisForNonLinearOnlyKeys(
      String sessionToken, Principal user, String assembly, String axisTarget, String field,
      Map<String, Object> properties)
      throws Exception
   {
      Set<String> requested = new TreeSet<>(properties.keySet());
      requested.retainAll(NON_LINEAR_ONLY_AXIS_KEYS);

      if(requested.isEmpty()) {
         return;
      }

      AxisKind kind = computeTrueAxisKind(sessionToken, user, assembly, axisTarget, field);

      if(kind.linear() &&
         !(kind.matchedRef() instanceof ChartAggregateRef aggregate && aggregate.isDiscrete()))
      {
         throw new IllegalArgumentException(
            "'" + String.join("', '", requested) + "' " + (requested.size() == 1 ? "only applies" : "only apply") + " to a dimension axis. " +
            "'" + axisTarget + "'" + (field != null && !field.isBlank() ? " ('" + field + "')" : "")
            + " on this chart is bound to a measure, so these would be silently ignored. " +
            "Omit them for a measure axis.");
      }
   }

   /**
    * Refuses an axis read or write with a blank {@code field} when the shelf carries more than
    * one field and at least one is not a measure (bug #78187). Read and write both resolve a
    * blank-field axis by on-screen area index 0, so which dimension's descriptor is meant is
    * undecidable here; worse, the write sends the whole pane back, so the gated defaults that were
    * never loaded for the wrong axis kind reset the real descriptor (e.g. {@code truncate}
    * true to false). All-measure shelves (y with y2, or several measures) share one descriptor and
    * stay allowed. With {@code field} given the backfill and write use the same descriptor, so
    * nothing is refused.
    */
   private void requireUnambiguousAxis(String sessionToken, Principal user, String assembly,
                                       String axisTarget, String field)
      throws Exception
   {
      if(field != null && !field.isBlank()) {
         return;
      }

      List<String> names = sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         VSChartInfo info = ChartRegionResolver.requireChart(rvs, assembly).getVSChartInfo();
         String canonical = ChartRegionResolver.canonical(axisTarget);
         boolean onYShelf = "y".equals(canonical) || "y2".equals(canonical);
         boolean secondary = "y2".equals(canonical) || "x2".equals(canonical);
         ChartRef[] shelf = onYShelf ? info.getYFields() : info.getXFields();
         // Only refs that can render on the targeted axis are candidates, the same predicate
         // computeTrueAxisKind uses: a measure on the other axis of this type is not on it, and
         // dimensions never render on a secondary axis.
         ChartRef[] refs = Arrays.stream(shelf)
            .filter(r -> r instanceof ChartAggregateRef a ? a.isSecondaryY() == secondary
               : !secondary)
            .toArray(ChartRef[]::new);

         // A discrete measure has its own per-ref descriptor, so it does not share one with the
         // other measures and cannot take part in the all-measure exemption.
         if(refs.length < 2 || Arrays.stream(refs).allMatch(
               r -> r instanceof ChartAggregateRef a && !a.isDiscrete()))
         {
            return List.<String>of();
         }

         return Arrays.stream(refs).map(ChartRef::getFullName).toList();
      });

      if(!names.isEmpty()) {
         throw new IllegalArgumentException(
            "Axis '" + axisTarget + "' is ambiguous: its shelf has several fields (" +
            String.join(", ", names) + ") and no 'field' was given, so it is undefined which " +
            "one's axis is meant. Pass 'field' with the full name of one of them.");
      }
   }

   /** {@code increment} does not fit the plain linear/non-linear split {@link #LINEAR_ONLY_AXIS_KEYS}
    * checks -- see {@link #requireLinearOrTimeSeriesAxisForIncrement}. */
   private static final Set<String> LINEAR_OR_TIME_SERIES_AXIS_KEYS = Set.of("increment");

   /**
    * Refuses {@code increment} on an axis that is neither linear nor a non-linear time-series
    * date axis (bug #77027 item 2).
    *
    * <p>{@code AxisPropertyDialogModel.updateAxisPropertyDialogModel} applies {@code increment}
    * under {@code if(this.linear || this.timeSeries && !this.outer)} -- a three-variable
    * condition matched exactly by the real UI's own visibility guard on the "Major Increment"
    * field ({@code axis-line-pane.component.html}: {@code @if (linear || timeSeries && !outer)}).
    * Adding {@code increment} to {@link #LINEAR_ONLY_AXIS_KEYS} would wrongly refuse a legitimate
    * write on a genuinely non-linear, time-series axis, so it gets its own guard with the same
    * compound shape instead.
    *
    * <p>{@code linear} is independently re-derived the same way {@link
    * #requireLinearAxisForLinearOnlyKeys} does (via {@link #computeTrueAxisKind}). {@code
    * timeSeries} is re-derived the same way {@code AxisPropertyDialogModel}'s own constructor
    * defines it ({@code ref instanceof XDimensionRef && ref.isTimeSeries() &&
    * GraphUtil.isTimeSeriesVisible(...)}) -- <b>except</b> for the {@code !outer} factor, which is
    * deliberately left out: {@code outer} is derived the very same area-index-0-dependent way
    * {@code linear} itself needed correcting, and re-deriving it independently for a faceted or
    * scatter-matrix chart (where more than one dimension-label area can legitimately exist at
    * different indices) needs closer study than an 8-item audit's fix pass covers. Omitting it
    * only makes this guard <em>more permissive</em> than the real UI's own condition -- it may
    * allow an {@code increment} write on an inner-facet time-series axis the UI hides the control
    * for, which the underlying {@code AxisDescriptor.setIncrement} tolerates without corrupting
    * anything -- never less safe, since a genuinely non-time-series, non-linear axis is still
    * refused exactly as before.
    */
   private void requireLinearOrTimeSeriesAxisForIncrement(
      String sessionToken, Principal user, String assembly, String axisTarget, String field,
      Map<String, Object> properties)
      throws Exception
   {
      if(Collections.disjoint(properties.keySet(), LINEAR_OR_TIME_SERIES_AXIS_KEYS)) {
         return;
      }

      AxisKind kind = computeTrueAxisKind(sessionToken, user, assembly, axisTarget, field);

      if(kind.linear()) {
         return;
      }

      boolean timeSeries = sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         if(!(kind.matchedRef() instanceof XDimensionRef dim) || !dim.isTimeSeries()) {
            return false;
         }

         VSChartInfo info = ChartRegionResolver.requireChart(rvs, assembly).getVSChartInfo();
         return GraphUtil.isTimeSeriesVisible(info, kind.matchedRef());
      });

      if(!timeSeries) {
         throw new IllegalArgumentException(
            "'increment' only applies to a linear (measure) axis, or a non-linear time-series " +
            "date axis. '" + axisTarget + "'" +
            (field != null && !field.isBlank() ? " ('" + field + "')" : "") +
            " on this chart is neither, so this would be silently ignored. Omit it.");
      }
   }

   /** An axis's true kind, re-derived off its actual binding rather than off the area-index-0
    * read every axis request in this class necessarily uses (see {@link
    * #requireLinearAxisForLinearOnlyKeys}'s own javadoc for why that read is unreliable).
    * {@code matchedRef} is the specific {@link ChartRef} this determination is based on -- the
    * matching {@link ChartAggregateRef} when {@code linear} is true, or the sole candidate ref on
    * the shelf when unambiguous and non-linear, else {@code null} when there is more than one
    * candidate and no way to say which one this axis-target/field pair actually means. */
   private record AxisKind(boolean linear, ChartRef matchedRef) {}

   private AxisKind computeTrueAxisKind(String sessionToken, Principal user, String assembly,
                                         String axisTarget, String field)
      throws Exception
   {
      return sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         VSChartInfo info = ChartRegionResolver.requireChart(rvs, assembly).getVSChartInfo();
         String canonical = ChartRegionResolver.canonical(axisTarget);
         boolean secondary = "y2".equals(canonical) || "x2".equals(canonical);
         boolean onYShelf = "y".equals(canonical) || "y2".equals(canonical);
         // Canonical x/x2 always reads getXFields(), canonical y/y2 always reads getYFields() --
         // the same convention ChartRegionResolver.fromBinding uses one call earlier in this same
         // class's requireExistingTarget (present.add("x") off getXFields() alone, with no
         // isInvertedGraph() factor at all for the primary case; for the secondary case, the
         // inverted-ness of the chart decides which canonical NAME -- x2 or y2 -- a secondary
         // measure gets, not which shelf backs a given name). A repair-review catch confirmed live
         // 2026-09-02: an earlier cut XOR'd this with isInvertedGraph(), which resolves every
         // canonical target to the wrong shelf on any inverted chart (e.g. a Gantt chart, which is
         // unconditionally inverted) -- reopening the exact corruption this method exists to close,
         // or wrongly refusing a legitimate write on a real measure axis.
         ChartRef[] refs = onYShelf ? info.getYFields() : info.getXFields();
         List<ChartRef> candidates = new ArrayList<>(Arrays.asList(refs));

         // A shelf can carry more than one field of the same axis type (the tool's own
         // vocabulary() note: "to address one of several axes of the same type... pass the
         // column name as 'field'") -- checking "does ANY field on the shelf happen to be a
         // measure" instead of the ONE field this call actually addresses would let a write
         // aimed at a dimension slip through on a mixed dimension+measure shelf. When 'field' is
         // given, resolve to that specific ref; only fall back to "any measure on the shelf" when
         // it is not, matching how the rest of this class already treats an absent field as "the
         // shelf has just the one".
         if(field != null && !field.isBlank()) {
            candidates.removeIf(ref ->
               !field.equals(ref.getFullName()) && !field.equals(ref.getName()));
         }

         // Exact match, not "secondary implies acceptable, primary accepts anything": a measure
         // that lives on the OTHER axis of this type must not make this one look linear, in
         // either direction.
         ChartRef measureMatch = candidates.stream()
            .filter(ref -> ref instanceof ChartAggregateRef aggregate &&
               aggregate.isSecondaryY() == secondary)
            .findFirst().orElse(null);

         if(measureMatch != null) {
            return new AxisKind(true, measureMatch);
         }

         return new AxisKind(false, candidates.size() == 1 ? candidates.get(0) : null);
      });
   }

   /** The angles both the axis label and the title actually offer, degrees, "auto" aside. */
   private static final Set<Integer> ROTATION_DEGREES = Set.of(-90, -45, 0, 45, 90);

   /**
    * Validates a {@code rotation} against the domain the target region's model actually accepts,
    * and returns the canonical spelling PropertyPath should be given.
    *
    * <p>Both {@code AxisLabelPaneModel}'s and {@code TitleFormatPaneModel}'s rotation live at a
    * path ending in {@code .rotation} (via their shared {@code RotationRadioGroupModel}), so a
    * single {@code PropertyPath.CONSTRAINED_STRINGS} entry keyed by that leaf name cannot express
    * that the two accept different domains: the axis label genuinely offers {@code "auto"} in the
    * Composer (clears the rotation back to the default, matched case-sensitively —
    * {@code AxisPropertyDialogModel.java:300}, {@code "auto".equals(rotation)}), while the
    * title's own persist step ({@code TitleFormatDialogModel.updateTitleFormatPaneModel}) does not
    * handle {@code "auto"} at all and calls {@code Float.parseFloat(rotation)} directly on
    * whatever string arrives, which throws an unhelpful raw {@code NumberFormatException} for it.
    * Checked here, region-aware, before either path is reached.
    *
    * <p>The numeric side is compared as a float, not as an exact string: the model itself never
    * stores a bare integer string. {@code AxisPropertyDialogModel.updateAxisPropertyDialogModel}
    * populates {@code RotationRadioGroupModel} via {@code rotation + ""} and
    * {@code TitleFormatDialogModel} via {@code Number.toString()} on a {@code Float} field, which
    * yields {@code "90.0"}, not {@code "90"} — the same form the Composer UI's own radio group
    * writes ({@code rotation-radio-group.component.ts}). A read-then-write round trip through
    * {@code list_chart_region_properties} must not be rejected just because the stored form has a
    * decimal point PropertyPath.CONSTRAINED_STRINGS-style exact matching would have missed.
    */
   private static String canonicalRotation(String region, Object rotation) {
      String text = rotation == null ? "" : String.valueOf(rotation).trim();

      if("axis".equals(region) && text.equalsIgnoreCase("auto")) {
         return "auto";
      }

      try {
         float parsed = Float.parseFloat(text);
         int degrees = (int) parsed;

         if(degrees == parsed && ROTATION_DEGREES.contains(degrees)) {
            return String.valueOf(degrees);
         }
      }
      catch(NumberFormatException ignore) {
         // falls through to the error below
      }

      String allowedDescription = "axis".equals(region)
         ? "[-90, -45, 0, 45, 90, auto]" : "[-90, -45, 0, 45, 90]";
      throw new IllegalArgumentException(
         "'rotation' on a chart " + region + " accepts only " + allowedDescription +
         "; '" + rotation + "' is not one of them.");
   }

   /** {@code LegendDescriptor.setSymbolSize}'s own clamp range (bug #77027 item 6). */
   private static final int LEGEND_SYMBOL_SIZE_MIN = 6;
   private static final int LEGEND_SYMBOL_SIZE_MAX = 50;

   /**
    * Refuses a legend {@code symbolSize} outside {@code [6, 50]} rather than letting it reach
    * {@code LegendDescriptor.setSymbolSize}, which unconditionally clamps to that range with no
    * indication (round r1: the lead's dispatched decision is a loud refusal here, not a
    * warn-and-proceed shape -- the plugin's own client-side check mirrors this, and this
    * server-side guard is what makes the refusal apply no matter which caller reaches this
    * method, not only the wiz plugin). Same pattern as {@link #canonicalRotation}: parse, and
    * name the accepted range in the refusal rather than letting a raw parse failure or a silent
    * clamp through.
    */
   private static void requireSymbolSizeInRange(Map<String, Object> properties) {
      if(!properties.containsKey("symbolSize")) {
         return;
      }

      Object value = properties.get("symbolSize");
      Integer parsed = null;

      if(value instanceof Number number) {
         parsed = number.intValue();
      }
      else if(value != null) {
         try {
            parsed = Integer.parseInt(String.valueOf(value).trim());
         }
         catch(NumberFormatException ignore) {
            // falls through to the refusal below -- a non-numeric symbolSize gets the same
            // "here is the accepted range" message, not a raw NumberFormatException
         }
      }

      if(parsed == null || parsed < LEGEND_SYMBOL_SIZE_MIN || parsed > LEGEND_SYMBOL_SIZE_MAX) {
         throw new IllegalArgumentException(
            "'symbolSize' must be a whole number between " + LEGEND_SYMBOL_SIZE_MIN + " and " +
            LEGEND_SYMBOL_SIZE_MAX + " inclusive; '" + value + "' is not. Outside that range, " +
            "LegendDescriptor.setSymbolSize silently clamps it instead of refusing, which this " +
            "check exists to prevent.");
      }
   }

   /**
    * Converts the caller's per-value label overrides into the {@code ModelAlias[]} the Alias
    * tab's own model expects.
    *
    * <p>Not handled by {@code PropertyPath}'s generic array coercion: that engine builds an array
    * of a component type by recursively coercing each JSON element, but a {@code ModelAlias} is a
    * plain bean with no scalar/enum/array shape {@code PropertyPath.coerce} knows how to
    * construct from a JSON object, and extending that generic engine to build arbitrary beans
    * from a {@code Map} would widen every other property this class and its siblings expose, not
    * just this one. Converting here, before the value ever reaches {@code PropertyPath}, keeps
    * the change scoped to the one property that needs it: {@code PropertyPath.set} then receives
    * an already-correct {@code ModelAlias[]} and its own pass-through check
    * ({@code target.isInstance(value)}) hands it straight to the setter unchanged.
    *
    * <p>Each entry needs {@code value} (the data value the alias replaces) and {@code alias}
    * (what to show instead). {@code value} is matched against {@code current} — the region's own
    * alias list, exactly as {@link #list} already reports it — first by real value, then, more
    * forgivingly, by display {@code label}: a date-grouped axis's real value is not its display
    * text (e.g. the label {@code "2022"}'s real value is {@code "2022-01-01 00:00:00"}), and
    * both {@link AxisPropertyDialogModel#updateAxisPropertyDialogModel} and
    * {@link LegendFormatDialogModel#updateLegendFormatDialogModel} call
    * {@code XxxDescriptor.setLabelAlias(value, alias)} with whatever {@code value} this method
    * hands them, <b>unconditionally, with no matching of their own</b> — so a caller-supplied
    * value that does not match the real one is silently stored under a key nothing ever reads
    * back. Live-confirmed 2026-09-02, found by this audit's own live verification pass: writing
    * {@code {value:"2022", alias:"FY22"}} against a year-grouped axis returned {@code ok:true},
    * left the chart showing "2022" unchanged, and read back the original, untouched alias list.
    * A value matching neither the real value nor the label is refused by name, rather than
    * repeating that silent-no-op shape for a typo or a stale value from an earlier read.
    */
   private static List<Map<?, ?>> parseAliasEntries(Object value) {
      if(!(value instanceof List<?> list)) {
         throw new IllegalArgumentException(
            "'aliases' expects a JSON array of {value, alias} objects; '" + value + "' is not " +
            "an array.");
      }

      List<Map<?, ?>> parsed = new ArrayList<>(list.size());

      for(int i = 0; i < list.size(); i++) {
         Object entry = list.get(i);

         if(!(entry instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
               "'aliases[" + i + "]' must be an object with 'value' and 'alias', got '" + entry +
               "'.");
         }

         if(map.get("value") == null || map.get("alias") == null) {
            throw new IllegalArgumentException(
               "'aliases[" + i + "]' needs both 'value' (the data value to replace) and 'alias' " +
               "(what to show instead); got " + map + ".");
         }

         String aliasText = String.valueOf(map.get("alias"));

         if(aliasText.trim().isEmpty()) {
            throw new IllegalArgumentException(
               "'aliases[" + i + "].alias' is blank/whitespace-only, which this region's " +
               "underlying model treats as \"no override\" and silently reverts to the default " +
               "label -- not a way to force a genuinely empty tick/legend label. Use a " +
               "non-blank placeholder instead.");
         }

         parsed.add(map);
      }

      return parsed;
   }

   private static ModelAlias[] toModelAliases(List<Map<?, ?>> entries, ModelAlias[] current) {
      ModelAlias[] result = new ModelAlias[entries.size()];

      for(int i = 0; i < entries.size(); i++) {
         Map<?, ?> map = entries.get(i);
         String suppliedValue = String.valueOf(map.get("value"));
         Object rawAlias = map.get("alias");
         ModelAlias resolved = resolveAliasItem(suppliedValue, current, i);
         result[i] = new ModelAlias(resolved.getLabel(), resolved.getValue(),
                                    String.valueOf(rawAlias));
      }

      return result;
   }

   /**
    * Finds the {@code current} entry a caller-supplied alias {@code value} means — an exact match
    * on the real value first, then a match on the real display label — and refuses, naming the
    * real value/label pairs, when {@code current} is non-empty and neither matches.
    *
    * <p>An empty {@code current} is refused too, rather than passed through unresolved. An
    * earlier version treated empty as "unbound or not-yet-laid-out, nothing to validate against"
    * and let the value through as-is — but {@code current} comes from
    * {@code GraphUtil.getAxisItems}, which reads the <em>executed</em> graph area, not the
    * binding: a genuinely bound axis reports empty just as easily when the graph has not been
    * (re-)executed since a binding/filter change, or the active filter currently excludes every
    * row. Passing the caller's raw value through in that window reproduces the exact silent-no-op
    * shape {@link #parseAliasEntries}'s javadoc records (a display-text value stored under a key
    * nothing reads back) for the case that most needs the resolution — a date-grouped or
    * named-group axis is the one most likely to have just changed. Refusing here costs a
    * legitimately-unbound region a clear error instead of a silent no-op; the caller is guided to
    * check {@code list_chart_region_properties} first, which reads the same {@code current}.
    */
   private static ModelAlias resolveAliasItem(String suppliedValue, ModelAlias[] current, int index) {
      if(current == null || current.length == 0) {
         throw new IllegalArgumentException(
            "'aliases[" + index + "]' cannot be resolved: this region currently reports no " +
            "known values (list_chart_region_properties reports the same empty list). This " +
            "can mean the field is unbound, or the chart has not been (re-)executed since a " +
            "binding or filter change -- refresh the chart or re-check " +
            "list_chart_region_properties before retrying, rather than setting an alias whose " +
            "value cannot be matched against anything and may silently do nothing.");
      }

      for(ModelAlias item : current) {
         if(suppliedValue.equals(item.getValue())) {
            return item;
         }
      }

      for(ModelAlias item : current) {
         if(suppliedValue.equals(item.getLabel())) {
            return item;
         }
      }

      StringBuilder known = new StringBuilder();

      for(ModelAlias item : current) {
         if(known.length() > 0) {
            known.append(", ");
         }

         known.append("'").append(item.getLabel()).append("'");

         if(!Objects.equals(item.getLabel(), item.getValue())) {
            known.append(" (real value '").append(item.getValue()).append("')");
         }
      }

      throw new IllegalArgumentException(
         "'aliases[" + index + "].value' ('" + suppliedValue + "') matches neither the real " +
         "value nor the display label of anything list_chart_region_properties reports for " +
         "this region. Known: " + known + ".");
   }

   /**
    * Refuses an axis, or an axis title, that this chart does not have.
    *
    * <p>{@link #requireTarget} already rejects a target that names <em>no</em> axis type. This is
    * the other half: a target that names a real type the <em>chart</em> does not have.
    * {@code ChartRegionHandler.getAxisArea} maps {@code y2} onto an axis area {@code ChartArea}
    * builds unconditionally, so a chart with one measure returned the full y1 property list for
    * y2, accepted a write against it, and read the write straight back. Read and write are both
    * guarded here rather than only the write, because the read is what talked the caller into the
    * write.
    *
    * <p>Legends are not checked here — their target is an index, bounded by a different question,
    * and an out-of-range one has its own defect to fix.
    */
   private void requireExistingTarget(String sessionToken, Principal user, String assembly,
                                      String region, String target)
      throws Exception
   {
      if("legend".equals(region)) {
         ChartRegionResolver.Legends legends = sessions.read(
            sessionToken, user,
            (rvs, runtimeId, dispatcher) ->
               ChartRegionResolver.legends(
                  rvs, ChartRegionResolver.requireChart(rvs, assembly)));

         ChartRegionResolver.requireLegend(legends, indexOf(target));
         return;
      }

      if(!"axis".equals(region) && !"title".equals(region)) {
         return;
      }

      // The chart title is not a region title at all, and asking for it here was a raw HTTP 500.
      // TitlesDescriptor only holds x/x2/y/y2 descriptors, so ChartRegionHandler.getTitleDescriptor
      // and getTitleArea both return null for "chart" and RegionPropertyDialogService then
      // dereferences the null area. The chart title lives on the assembly instead.
      if("title".equals(region) && "chart".equals(ChartRegionResolver.canonical(target))) {
         throw new IllegalArgumentException(
            "The chart title is not a chart region — only the axis titles (x, x2, y, y2) are. " +
            "It lives on the assembly: set its text with set_assembly_properties 'title', show " +
            "or hide it with 'titleVisible' or with set_chart_element_visibility {element: " +
            "'title', target: 'chart'}, and set its font/color with set_format {assemblies: " +
            "[chart], target: 'title'}.");
      }

      ChartRegionResolver.Axes axes = sessions.read(
         sessionToken, user,
         (rvs, runtimeId, dispatcher) ->
            ChartRegionResolver.resolve(rvs, ChartRegionResolver.requireChart(rvs, assembly)));

      ChartRegionResolver.requireAxis(axes, region, target);

      if("title".equals(region)) {
         requireVisibleTitle(sessionToken, user, assembly, ChartRegionResolver.canonical(target));
      }
   }

   /**
    * Refuses a title region whose axis title is currently hidden.
    *
    * <p>{@code ChartArea} only builds a {@code TitleArea} for an axis whose
    * {@code TitleDescriptor.isVisible()} is true — when it is false, the corresponding
    * {@code x/x2/y/y2}TitleArea field stays null (it is normal, expected state; see
    * {@code ChartArea}'s own serialization code, which writes a boolean flag for exactly this
    * case). {@code RegionPropertyDialogService.getTitleFormatDialogModel} does not expect that
    * null and dereferences it unconditionally, turning a hidden title into a raw NPE for both
    * {@link #list} and {@link #set} (the latter via its own {@code readModel} prefetch). Refuse
    * loudly here, before either path reaches that dereference.
    */
   private void requireVisibleTitle(String sessionToken, Principal user, String assembly,
                                     String titleType)
      throws Exception
   {
      boolean visible = sessions.read(
         sessionToken, user,
         (rvs, runtimeId, dispatcher) -> {
            ChartVSAssemblyInfo info = (ChartVSAssemblyInfo)
               ChartRegionResolver.requireChart(rvs, assembly).getVSAssemblyInfo();
            TitleDescriptor titleDesc =
               regionHandler.getTitleDescriptor(info.getChartDescriptor(), titleType);

            return titleDesc == null || titleDesc.isVisible();
         });

      if(!visible) {
         // Bug #77027 item 8: the two remedies this message used to name were both dead ends --
         // showing a single non-chart axis title with a target is refused by
         // ChartElementService.titleFields itself (there is no per-axis-title show), and
         // list_chart_elements' vocabulary() never reported a titleVisible field to check. The
         // only real recovery path is showing every title at once (target omitted), which is
         // already what set_chart_element_visibility's own no-target show branch does.
         throw new IllegalArgumentException(
            "The " + titleType + " title is currently hidden, and there is no way to show only " +
            "this one title -- the Composer has no per-title show. Use " +
            "set_chart_element_visibility {element: 'title', visible: true} (no target) to show " +
            "every title on this chart, then retry.");
      }
   }

   private Object readModel(String sessionToken, Principal user, String assembly, String region,
                            String target, String field)
      throws Exception
   {
      String runtimeId = sessions.runtimeId(sessionToken, user);

      Object model = switch(region) {
         case "axis" -> regions.getAxisPropertyDialogModel(runtimeId, assembly, axisType(target),
                                                           "0", field, "", user);
         case "legend" -> regions.getLegendFormatDialogModel(runtimeId, assembly, target, "", user);
         default -> regions.getTitleFormatDialogModel(runtimeId, assembly, target, "", user);
      };

      // Item 1 (bug #77027): correct the model's own `linear` flag with the same independent,
      // ref-binding-based check requireLinearAxisForLinearOnlyKeys already uses, immediately after
      // fetching -- both list() and set() call this method, so both get the corrected value with
      // one change. This fixes updateAxisPropertyDialogModel's branch selection on the WRITE side
      // (an `ignoreNull`/`truncate` write to a categorical axis area-index-0 wrongly reports as
      // linear no longer lands in the `else if(this.linear)` branch and gets silently dropped).
      //
      // Round r1 addendum (B3): correcting `linear` alone does not fix the READ side.
      // `ignoreNull`/`truncate` (or `logarithmicScale`/`shared`/`reverse`) were already populated
      // -- or left at the AxisLinePaneModel bean's Java defaults -- inside the *constructor*,
      // under the WRONG `linear` value, before this method ever sees the model; correcting the
      // flag here changes nothing already written to those fields. Confirmed by round-r1 review to
      // be PERMANENT, not stale-until-a-write: every call reconstructs the model from scratch
      // under the identical wrong classification, so `list_chart_region_properties` on a
      // previously-misclassified axis reported the stale default forever, reproducing the
      // original bug's user-visible symptom through the read path instead of the write path.
      // Backfilled below via backfillAxisLinearOnlyFields.
      if("axis".equals(region)) {
         AxisKind kind = computeTrueAxisKind(sessionToken, user, assembly, target, field);
         AxisPropertyDialogModel axisModel = (AxisPropertyDialogModel) model;

         if(axisModel.getLinear() != kind.linear()) {
            axisModel.setLinear(kind.linear());
            backfillAxisLinearOnlyFields(sessionToken, user, assembly, target, field, kind,
                                         axisModel);
         }
      }

      return model;
   }

   /**
    * Backfills the pane fields {@link AxisPropertyDialogModel}'s constructor populated under the
    * WRONG {@code linear} value, once {@link #readModel} has corrected the flag itself (bug
    * #77027 item 1, round r1 addendum B3).
    *
    * <p>Re-fetches the real {@code AxisDescriptor} via {@code ChartRegionHandler}'s own
    * correctly-dispatched {@code getAxisDescriptor(ChartInfo, String, String, AtomicBoolean)}
    * overload -- the same one {@code ChartRegionHandler.updateAxisPropertyDialogModel} uses on the
    * write path -- rather than re-deriving that dispatch logic here: radar/mekko/
    * secondary-axis-sharing charts have real special-casing in it this class must not duplicate.
    *
    * <p>Needs a resolved column name: {@code field}, if given, or {@code kind}'s own {@code
    * matchedRef} when the shelf had exactly one unambiguous candidate (see {@link
    * #computeTrueAxisKind}). When neither is available -- a blank {@code field} on a shelf with
    * more than one field of this axis type -- the backfill is skipped. Axis reads and writes
    * refuse that case first (see {@link #requireUnambiguousAxis}), so only an all-measure shelf
    * sharing one descriptor can reach it here.
    */
   private void backfillAxisLinearOnlyFields(String sessionToken, Principal user, String assembly,
                                             String axisTarget, String field, AxisKind kind,
                                             AxisPropertyDialogModel model)
      throws Exception
   {
      String columnName = field != null && !field.isBlank() ? field
         : kind.matchedRef() != null ? kind.matchedRef().getFullName() : null;

      if(columnName == null) {
         return;
      }

      Void ignored = sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         VSChartInfo info = ChartRegionResolver.requireChart(rvs, assembly).getVSChartInfo();
         AxisDescriptor axisDesc = regionHandler.getAxisDescriptor(
            info, columnName, axisType(axisTarget), new AtomicBoolean());
         AxisLinePaneModel pane = model.getAxisLinePaneModel();

         if(kind.linear()) {
            pane.setLogarithmicScale(axisDesc.isLogarithmicScale());
            pane.setShared(axisDesc.isSharedRange());
            pane.setReverse(axisDesc.isReversed());
         }
         else {
            pane.setIgnoreNull(axisDesc.isNoNull());
            pane.setTruncate(axisDesc.isTruncate());
         }

         return null;
      });
   }

   /**
    * Translates a secondary axis target to the <b>long</b> area form before it reaches StyleBI.
    *
    * <p><b>Found live 2026-08-20, image-confirmed.</b> Writing {@code showAxisLabel: false} to
    * {@code y2} on a dual-axis chart hid the <em>primary</em> axis' labels instead, reported
    * success naming y2, and reading {@code y} back afterwards showed the value there. Passing
    * {@code field} did not help.
    *
    * <p>The cause is an asymmetry in {@code ChartRegionHandler}: {@code getAxisArea} accepts both
    * the short forms ({@code Y2_TITLE = "y2"}) and the long ones
    * ({@code RIGHT_Y_AXIS = "right_y_axis"}), so the <em>area</em> resolves either way and the read
    * looks healthy — but {@code getChartRef} (which every {@code getAxisDescriptor} overload
    * funnels through) knows {@code left_y_axis}, {@code right_y_axis}, {@code bottom_x_axis},
    * {@code top_x_axis}, {@code "y"} and {@code "x"}, and <b>not</b> {@code "y2"} or {@code "x2"}.
    * So the ref came back null, the descriptor chain fell through to its last branch —
    * {@code info.getAxisDescriptor()}, the descriptor shared with the primary axis — and the
    * secondary branch that exists for exactly this case
    * ({@code isSecondaryY() -> info.getAxisDescriptor2()}) was never reached.
    *
    * <p>Only the secondary forms are translated. {@code "y"} and {@code "x"} are handled by
    * {@code getChartRef} already, and mapping them to the long forms would change which shelf
    * {@code findDataRef} searches on a scatter matrix — a real behaviour change for no gain.
    */
   private static String axisType(String target) {
      return switch(ChartRegionResolver.canonical(target)) {
         case "y2" -> "right_y_axis";
         case "x2" -> "top_x_axis";
         default -> target;
      };
   }

   private static int indexOf(String target) {
      try {
         return Integer.parseInt(target.trim());
      }
      catch(NumberFormatException e) {
         throw new IllegalArgumentException(
            "A legend is addressed by its 0-based index, got '" + target + "'.");
      }
   }

   private static String requireRegion(String region) {
      String name = region == null ? "" : region.trim().toLowerCase();

      if(!REGIONS.contains(name)) {
         throw new IllegalArgumentException(
            "Unknown chart region '" + region + "'. Valid regions: " +
            String.join(", ", REGIONS) + ".");
      }

      return name;
   }

   private static String requireTarget(String target, String region) {
      if(target == null || target.isBlank()) {
         throw new IllegalArgumentException(
            "A chart " + region + " needs a 'target' — " +
            switch(region) {
               case "axis" -> "the axis type, such as y or x.";
               case "legend" -> "the legend's 0-based index.";
               default -> "which axis title: x, x2, y, y2. The chart's own title is not a " +
                  "region — its text is set_assembly_properties 'title', its font/color is " +
                  "set_format {assemblies: [chart], target: 'title'}.";
            });
      }

      String trimmed = target.trim();

      // Validated here rather than only at write time: the composer service parses the index
      // itself while READING the model, so a non-numeric target surfaced as a raw
      // `For input string: "..."` from inside StyleBI before the write-side guard was reached.
      if("legend".equals(region)) {
         indexOf(trimmed);
      }

      // Same reasoning, worse symptom. ChartRegionHandler.getAxisArea returns null for a type it
      // does not recognise, and neither side reports it: the read returned the same full, plausible
      // property list for "PAID" or "zzzznonsense" as for "y", and the write threw
      // NullPointerException: axisArea is null. So an unrecognised axis silently read as a real one
      // and then failed with a message naming nothing the caller passed.
      if("axis".equals(region)) {
         String normalized = trimmed.toLowerCase();

         if(!AXIS_TARGETS.contains(normalized)) {
            throw new IllegalArgumentException(
               "'" + target + "' does not name an axis. Valid targets: " +
               String.join(", ", AXIS_TARGETS) + ". To address one of several axes of the same " +
               "type, keep the axis type here and pass the column name as 'field'.");
         }

         return normalized;
      }

      return trimmed;
   }

   private static Map<String, String> aliasesFor(String region) {
      return switch(region) {
         case "axis" -> AXIS;
         case "legend" -> LEGEND;
         default -> TITLE;
      };
   }

   /** {@code path -> alias} for a region, the inverse of {@link #aliasesFor}. Built fresh per
    * call -- these maps are small and this is not a hot path -- rather than cached alongside
    * AXIS/LEGEND/TITLE, since it exists purely to serve {@link #normalizeToAliasKeys}. */
   private static Map<String, String> reverseAliasesFor(String region) {
      Map<String, String> reverse = new HashMap<>();

      for(Map.Entry<String, String> entry : aliasesFor(region).entrySet()) {
         reverse.put(entry.getValue(), entry.getKey());
      }

      return reverse;
   }

   /**
    * Normalizes an incoming property key back to its alias name when the raw dotted-path form
    * happens to match a known alias's own path exactly -- e.g. {@code "axisLinePaneModel.minimum"}
    * becomes {@code "minimum"} (bug #77027 item 3).
    *
    * <p>Every keyed guard in this class ({@link #requireLinearAxisForLinearOnlyKeys}, {@link
    * #requireLinearOrTimeSeriesAxisForIncrement}, the {@code rotation}/{@code aliases}
    * special-casing in {@link #set}, the legend renamed-key checks) matches by the bare alias
    * name only. {@link #set}'s own resolve loop deliberately accepts a raw model path as an
    * escape hatch ("A raw model path (containing a '.') is also accepted"), which every one of
    * those guards was blind to: a caller spelling a linear-only key as its raw path (e.g. {@code
    * "axisLinePaneModel.minimum": "5"} on a categorical axis) sailed straight past
    * {@code requireLinearAxisForLinearOnlyKeys} and reproduced the exact numeric-range corruption
    * that guard exists to refuse, simply by using the path form instead of the alias.
    *
    * <p>A raw path that does not match any known alias exactly is left untouched -- that is the
    * documented escape hatch this method still supports, not the bug. Always returns a fresh,
    * mutable map, which lets every call site after this one ({@code rotation}, {@code aliases})
    * mutate it freely without its own defensive copy.
    */
   private static Map<String, Object> normalizeToAliasKeys(String region,
                                                            Map<String, Object> properties)
   {
      Map<String, String> reverse = reverseAliasesFor(region);
      Map<String, Object> normalized = new LinkedHashMap<>();

      for(Map.Entry<String, Object> entry : properties.entrySet()) {
         String key = entry.getKey();
         String canonical = key != null ? reverse.get(key) : null;
         normalized.put(canonical != null ? canonical : key, entry.getValue());
      }

      return normalized;
   }

   /** Old legend property names renamed for clarity (bug #77027 items 4/5) -- kept only so a
    * caller still spelling the old name gets a message naming the replacement, rather than the
    * generic "unknown property" list a brand-new typo gets. */
   private static final Map<String, String> LEGEND_RENAMED_KEYS =
      Map.of("visible", "titleVisible", "fillColor", "borderColor");

   private static String legendRenamedKeyMessage(String oldName, String newName) {
      if("visible".equals(oldName)) {
         return "'visible' is not a property of a chart legend -- it was renamed to '" + newName +
            "' because it only shows or hides the legend's TITLE text, never the legend itself " +
            "(the real Composer's own 'Visible' checkbox sits beside the Title combo box for the " +
            "same reason). To hide the whole legend, use set_chart_element_visibility " +
            "{element: 'legend', target: <the legend's field or channel>, visible: false} instead.";
      }

      return "'" + oldName + "' is not a property of a chart legend -- it was renamed to '" +
         newName + "' (it sets the legend's border color, not a fill; 'fillColor' was a " +
         "historical misnomer on the underlying bean). Use '" + newName + "' instead.";
   }

   private static final List<String> REGIONS = List.of("axis", "legend", "title");

   /**
    * The axis types {@code ChartRegionHandler.getAxisArea} recognises — the short title forms and
    * the long area forms, both accepted there and so both accepted here. Any other value yields a
    * null axis area, which is the whole reason this list is enforced.
    */
   private static final List<String> AXIS_TARGETS =
      List.of("x", "x2", "y", "y2",
              "bottom_x_axis", "top_x_axis", "left_y_axis", "right_y_axis");

   private static final Map<String, String> AXIS = axis();
   private static final Map<String, String> LEGEND = legend();
   private static final Map<String, String> TITLE = title();

   private static Map<String, String> axis() {
      Map<String, String> aliases = new LinkedHashMap<>();
      aliases.put("showAxisLine", "axisLinePaneModel.showAxisLine");
      aliases.put("lineColor", "axisLinePaneModel.lineColor");
      aliases.put("showTicks", "axisLinePaneModel.showTicks");
      aliases.put("logarithmicScale", "axisLinePaneModel.logarithmicScale");
      aliases.put("reverse", "axisLinePaneModel.reverse");
      aliases.put("shared", "axisLinePaneModel.shared");
      aliases.put("ignoreNull", "axisLinePaneModel.ignoreNull");
      aliases.put("truncate", "axisLinePaneModel.truncate");
      aliases.put("minimum", "axisLinePaneModel.minimum");
      aliases.put("maximum", "axisLinePaneModel.maximum");
      aliases.put("increment", "axisLinePaneModel.increment");
      aliases.put("minorIncrement", "axisLinePaneModel.minorIncrement");
      aliases.put("showAxisLabel", "axisLabelPaneModel.showAxisLabel");
      aliases.put("labelOnSecondaryAxis", "axisLabelPaneModel.labelOnSecondaryAxis");
      // The Label tab's Rotation fieldset had no tool equivalent (parity audit L4, finding G3-6).
      // Unlike the title's rotation, "auto" is a real, offered value here -- see
      // requireValidRotation for why the two can't share one CONSTRAINED_STRINGS entry.
      aliases.put("rotation", "axisLabelPaneModel.rotationRadioGroupModel.rotation");
      // The Alias tab (per-value label overrides), shown only for a non-linear axis, had no tool
      // equivalent (parity audit L4, finding G3-7). See toModelAliases for the payload shape.
      aliases.put("aliases", "aliasPaneModel.aliasList");
      return aliases;
   }

   private static Map<String, String> legend() {
      Map<String, String> aliases = new LinkedHashMap<>();
      // "title" (not titleValue) is the read-only dvalue/default the combo box shows as a
      // placeholder (legend-format-general-pane.component.html's origValue) -- the field a human
      // actually edits, and the only one LegendFormatDialogModel.updateLegendFormatDialogModel
      // ever persists, is titleValue. Aliasing "title" here used to point at the wrong sibling:
      // set_chart_region_properties({region:"legend", properties:{title:"X"}}) returned ok:true
      // and silently changed nothing, confirmed live 2026-09-02.
      aliases.put("title", "legendFormatGeneralPaneModel.titleValue");
      // "visible" (bug #77027 item 4) collides with set_chart_element_visibility's own,
      // differently-scoped "visible" -- this one only ever maps to LegendDescriptor's
      // isTitleVisible()/setTitleVisible() (the legend's TITLE caption, shown/hidden by the same
      // checkbox row as the Title combo box), never LegendDescriptor's separate whole-legend
      // isVisible()/setVisible(), which only set_chart_element_visibility reaches. Named
      // "titleVisible" for the same reason "title" was renamed to "titleValue" above: the old
      // name read like it meant the other, wider thing.
      aliases.put("titleVisible", "legendFormatGeneralPaneModel.visible");
      aliases.put("position", "legendFormatGeneralPaneModel.position");
      // "fillColor" (bug #77027 item 5) is a historical misnomer on the underlying bean: it maps
      // only to LegendsDescriptor.getBorderColor()/setBorderColor() -- there is no separate
      // fill-color concept on that class, and the UI places this exact color editor beside the
      // "Legend Border" style dropdown. Renamed to the name that actually describes it.
      aliases.put("borderColor", "legendFormatGeneralPaneModel.fillColor");
      aliases.put("style", "legendFormatGeneralPaneModel.style");
      aliases.put("notShowNull", "legendFormatGeneralPaneModel.notShowNull");
      aliases.put("symbolSize", "legendFormatGeneralPaneModel.symbolSize");
      // Only meaningful on a measure-bound legend (LegendScalePaneModel.reverseVisible/
      // includeZeroVisible say which of these two actually apply, per legend; logarithmic is
      // always offered). Genuinely missing before this: the UI's Scale tab had no tool
      // equivalent at all (parity audit L4, finding G3-3).
      aliases.put("logarithmicScale", "legendScalePaneModel.logarithmic");
      aliases.put("reverse", "legendScalePaneModel.reverse");
      aliases.put("includeZero", "legendScalePaneModel.includeZero");
      // The Alias tab (per-value label overrides), shown only for a dimension-bound legend, had
      // no tool equivalent (parity audit L4, finding G3-4). See toModelAliases for the payload
      // shape.
      aliases.put("aliases", "aliasPaneModel.aliasList");
      return aliases;
   }

   private static Map<String, String> title() {
      Map<String, String> aliases = new LinkedHashMap<>();
      aliases.put("title", "titleFormatPaneModel.title");
      // The Title Properties dialog's Rotation fieldset had no tool equivalent (parity audit L4,
      // finding G3-5). "auto" is not offered here -- the UI's own title-rotation control never
      // offers it either (contrast axis-label rotation, which does); the fixed angles are -90,
      // -45, 0, 45, 90.
      aliases.put("rotation", "titleFormatPaneModel.rotationRadioGroupModel.rotation");
      return aliases;
   }

   private final ViewsheetSessionService sessions;
   private final RegionPropertyDialogService regions;
   private final ChartRegionHandler regionHandler;
}
