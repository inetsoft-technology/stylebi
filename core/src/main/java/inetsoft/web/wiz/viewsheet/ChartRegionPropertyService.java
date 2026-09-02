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

import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.composer.vs.dialog.RegionPropertyDialogService;
import inetsoft.web.graph.model.dialog.AxisPropertyDialogModel;
import inetsoft.web.graph.model.dialog.LegendFormatDialogModel;
import inetsoft.web.graph.model.dialog.ModelAlias;
import inetsoft.web.graph.model.dialog.TitleFormatDialogModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

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
                                     RegionPropertyDialogService regions)
   {
      this.sessions = sessions;
      this.regions = regions;
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

      if("axis".equals(name)) {
         requireLinearAxisForLinearOnlyKeys(sessionToken, user, assembly, key, field, properties);
      }

      if(properties.containsKey("rotation") || properties.containsKey("aliases")) {
         properties = new LinkedHashMap<>(properties);
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
         String path = property.getKey() != null && property.getKey().contains(".")
            ? property.getKey()
            : aliases.get(property.getKey());

         if(path == null) {
            throw new IllegalArgumentException(
               "'" + property.getKey() + "' is not a property of a chart " + name + ". Known " +
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

      boolean linear = sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
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
         Stream<ChartRef> candidates = Arrays.stream(refs);

         // A shelf can carry more than one field of the same axis type (the tool's own
         // vocabulary() note: "to address one of several axes of the same type... pass the
         // column name as 'field'") -- checking "does ANY field on the shelf happen to be a
         // measure" instead of the ONE field this call actually addresses would let a write
         // aimed at a dimension slip through on a mixed dimension+measure shelf. When 'field' is
         // given, resolve to that specific ref; only fall back to "any measure on the shelf" when
         // it is not, matching how the rest of this class already treats an absent field as "the
         // shelf has just the one".
         if(field != null && !field.isBlank()) {
            candidates = candidates.filter(ref ->
               field.equals(ref.getFullName()) || field.equals(ref.getName()));
         }

         // Exact match, not "secondary implies acceptable, primary accepts anything": a measure
         // that lives on the OTHER axis of this type must not make this one look linear, in
         // either direction.
         return candidates.anyMatch(ref ->
            ref instanceof ChartAggregateRef aggregate && aggregate.isSecondaryY() == secondary);
      });

      if(!linear) {
         throw new IllegalArgumentException(
            "'" + String.join("', '", requested) + "' only apply to a linear (measure) axis. " +
            "'" + axisTarget + "'" + (field != null && !field.isBlank() ? " ('" + field + "')" : "")
            + " on this chart is bound to a dimension, not a measure, so these would be " +
            "silently ignored — or, on some chart types, corrupt the render instead of being " +
            "ignored. Omit them for a dimension axis.");
      }
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
   }

   private Object readModel(String sessionToken, Principal user, String assembly, String region,
                            String target, String field)
      throws Exception
   {
      String runtimeId = sessions.runtimeId(sessionToken, user);

      return switch(region) {
         case "axis" -> regions.getAxisPropertyDialogModel(runtimeId, assembly, axisType(target),
                                                           "0", field, "", user);
         case "legend" -> regions.getLegendFormatDialogModel(runtimeId, assembly, target, "", user);
         default -> regions.getTitleFormatDialogModel(runtimeId, assembly, target, "", user);
      };
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
      aliases.put("visible", "legendFormatGeneralPaneModel.visible");
      aliases.put("position", "legendFormatGeneralPaneModel.position");
      aliases.put("fillColor", "legendFormatGeneralPaneModel.fillColor");
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
}
