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
import inetsoft.web.graph.model.dialog.TitleFormatDialogModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

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
         requireLinearAxisForLinearOnlyKeys(sessionToken, user, assembly, key, properties);
      }

      Object model = readModel(sessionToken, user, assembly, name, key, field);
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
                                                    Map<String, Object> properties)
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
         boolean inverted = info.isInvertedGraph();
         boolean secondary = "y2".equals(canonical) || "x2".equals(canonical);
         boolean onYShelf = "y".equals(canonical) || "y2".equals(canonical);
         ChartRef[] refs = (onYShelf != inverted) ? info.getYFields() : info.getXFields();

         return Arrays.stream(refs).anyMatch(ref ->
            ref instanceof ChartAggregateRef aggregate &&
            (!secondary || aggregate.isSecondaryY()));
      });

      if(!linear) {
         throw new IllegalArgumentException(
            "'" + String.join("', '", requested) + "' only apply to a linear (measure) axis. " +
            "'" + axisTarget + "' on this chart is bound to a dimension, not a measure, so " +
            "these would be silently ignored — or, on some chart types, corrupt the render " +
            "instead of being ignored. Omit them for a dimension axis.");
      }
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
      return aliases;
   }

   private static Map<String, String> title() {
      Map<String, String> aliases = new LinkedHashMap<>();
      aliases.put("title", "titleFormatPaneModel.title");
      return aliases;
   }

   private final ViewsheetSessionService sessions;
   private final RegionPropertyDialogService regions;
}
