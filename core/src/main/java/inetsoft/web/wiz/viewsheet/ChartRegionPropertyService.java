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
            "axis", "the axis type — y, y2, x, x2 — as reported by list_chart_elements",
            "legend", "the legend's 0-based index",
            "title", "which title — x, x2, y, y2 or chart"),
         "note", "An axis may also need 'field' when a chart has more than one axis of a type.");
   }

   /** Property names for a region, with their current values. */
   public Map<String, Object> list(String sessionToken, Principal user, String assembly,
                                   String region, String target, String field)
      throws Exception
   {
      String name = requireRegion(region);
      String key = requireTarget(target, name);
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

      if(properties == null || properties.isEmpty()) {
         throw new IllegalArgumentException(
            "set_chart_region_properties needs at least one property. " +
            "list_chart_region_properties reports the names this region accepts.");
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
      for(Map.Entry<String, Object> one : resolved.entrySet()) {
         PropertyPath.set(model, one.getKey(), one.getValue());
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         switch(name) {
         case "axis" -> regions.setAxisPropertyDialogModel(
            runtimeId, assembly, key, 0, field, (AxisPropertyDialogModel) model, linkUri, user,
            dispatcher);
         case "legend" -> regions.setLegendFormatDialogModel(
            runtimeId, assembly, indexOf(key), (LegendFormatDialogModel) model, linkUri, user,
            dispatcher);
         default -> regions.setTitleFormatDialogModel(
            runtimeId, assembly, key, (TitleFormatDialogModel) model, linkUri, user, dispatcher);
         }
      });
   }

   private Object readModel(String sessionToken, Principal user, String assembly, String region,
                            String target, String field)
      throws Exception
   {
      String runtimeId = sessions.runtimeId(sessionToken, user);

      return switch(region) {
         case "axis" -> regions.getAxisPropertyDialogModel(runtimeId, assembly, target, "0", field,
                                                           "", user);
         case "legend" -> regions.getLegendFormatDialogModel(runtimeId, assembly, target, "", user);
         default -> regions.getTitleFormatDialogModel(runtimeId, assembly, target, "", user);
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
               default -> "which title: x, x2, y, y2 or chart.";
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
      aliases.put("title", "legendFormatGeneralPaneModel.title");
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
