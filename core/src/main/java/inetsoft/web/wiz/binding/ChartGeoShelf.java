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

import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.internal.graph.MapData;
import inetsoft.report.internal.graph.MapHelper;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.wiz.viewsheet.ChartRenderCheck;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;

import java.security.Principal;
import java.util.*;

/**
 * Writes a map chart's geo shelf the way the Composer does: every geo field gets a geo column
 * entry ({@link VSChartInfo#getGeoColumns()}) whose layer and feature mapping come from
 * {@link MapHelper#autoDetect} run over the chart's own data, and the runtime geo columns are
 * refreshed so a later layer change is not ignored.
 *
 * <p>The script path ({@code bindingInfo.geoFields = [...]}) writes only the shelf fields. A
 * field without a geo column entry reads as an empty map type to
 * {@code MapGenerator.checkGeoValidity}, so a second field next to a detected one fails to render
 * with "Multiple map types are not supported." The detection needs the chart's data, which a
 * script must not execute, so this is a separate step from the script engine.
 *
 * <p>Covers any number of geo fields, in any order, for any layers of one map type. It refuses,
 * naming StyleBI's own message, the combinations {@code MapGenerator} refuses at render time:
 * more than one polygon layer, fields whose detected map types differ, and a layer the detected
 * map type does not provide.
 */
public final class ChartGeoShelf {
   private ChartGeoShelf() {
   }

   /**
    * One requested geo field: the column and its layer name (Country, State, Province, City,
    * Zip). A {@code null} layer means "keep the layer the column already has" (Bug #81664): the
    * column's own Geographic setting, as the Composer leaves it when another field is added.
    */
   public record Field(String column, String layer) {}

   /** Most unmatched values listed per field, so a high-cardinality column cannot flood a reply. */
   public static final int MAX_UNMATCHED_LISTED = 20;

   /**
    * What was registered for one field: the layer and map type detection settled on, and how many
    * of the column's distinct values that layer matched ({@code matchedCount} of
    * {@code distinctCount}; both {@code null} when not measured). {@code unmatched} lists up to
    * {@link #MAX_UNMATCHED_LISTED} values that matched no feature, and {@code unmatchedTotal}
    * counts them all.
    */
   public record Detected(String column, String layer, String mapType, Integer matchedCount,
                          Integer distinctCount, List<String> unmatched, Integer unmatchedTotal)
   {
      public Detected(String column, String layer, String mapType) {
         this(column, layer, mapType, null, null, null, null);
      }
   }

   /**
    * Refuses what does not depend on the data: an unknown layer name, a repeated column, and
    * more than one polygon layer. Runs before anything is written. A field without a layer is
    * skipped by the layer checks here; {@link #apply} runs them again once it has filled in the
    * layers those fields keep.
    */
   public static void validate(List<Field> fields) {
      validate(fields, null);
   }

   /**
    * As {@link #validate(List)}, and when {@code mapType} is given, refuses any layer that map
    * type does not provide.
    */
   public static void validate(List<Field> fields, String mapType) {
      if(fields == null || fields.isEmpty()) {
         throw new IllegalArgumentException("fields must name at least one geographic column");
      }

      Set<String> seen = new HashSet<>();
      int polygons = 0;

      for(Field field : fields) {
         if(field == null || field.column() == null || field.column().isBlank()) {
            throw new IllegalArgumentException("every geo field needs a 'column'");
         }

         if(!seen.add(field.column())) {
            throw new IllegalArgumentException(
               "Column '" + field.column() + "' is listed more than once in the geo fields");
         }

         if(field.layer() == null) {
            continue;
         }

         int layer = layerId(field);

         if(!MapData.isPointLayer(layer)) {
            polygons++;
         }

         if(mapType != null && !MapHelper.isLayerSupported(mapType, layer)) {
            throw new IllegalArgumentException(
               Catalog.getCatalog().getString("em.common.graph.layerNotSupported",
                                              field.layer(), mapType) +
               " Column '" + field.column() + "'.");
         }
      }

      if(polygons > 1) {
         throw new IllegalArgumentException(
            Catalog.getCatalog().getString("em.common.graph.multiPolygons"));
      }
   }

   private static String requireMapType(String mapType) {
      if(mapType == null || mapType.isBlank()) {
         return null;
      }

      if(!MapHelper.isValidType(mapType)) {
         throw new IllegalArgumentException(
            "Map type '" + mapType + "' is not one of " + String.join(", ", MapData.getMapTypes()));
      }

      return mapType;
   }

   /**
    * Refuses a chart that has no source yet. Detection runs over the chart's own data and reads the
    * chart's own source ({@code ChartVSAssemblyInfo.getSourceInfo()}); with none, it failed deep
    * inside the query as an unnamed server error. "No source" is exactly what that read sees: a
    * null {@code SourceInfo}, or one with a null or empty source.
    *
    * @param rvs the runtime, or null (nothing to check)
    * @throws IllegalArgumentException naming the missing source and the step that supplies it
    */
   static void requireSource(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(!(assembly instanceof ChartVSAssembly chart)) {
         return;
      }

      SourceInfo source = ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).getSourceInfo();

      if(source == null || source.getSource() == null || source.getSource().isEmpty()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' has no source table yet, so its geographic columns cannot " +
            "be detected. Call set_chart_source to bind it to a table first, then repeat " +
            "set_chart_geo_shelf.");
      }
   }

   /**
    * One undo checkpoint: writes the geo shelf of {@code assemblyName} through {@link #apply}
    * and, when the write ends in a combination the chart cannot render, puts the chart back as
    * it was and refuses.
    */
   public static List<Detected> write(ViewsheetSessionService sessions, String sessionToken,
                                      Principal user, String assemblyName, VSChartHandler handler,
                                      List<Field> fields, String mapType)
      throws Exception
   {
      String requested = requireMapType(mapType);
      validate(fields, requested);
      // Before mutate(): it checkpoints even when the mutation throws, and a refusal that changed
      // nothing must not leave an undo step behind.
      requireSource(sessions.resolve(sessionToken, user), assemblyName);
      List<Detected> result = new ArrayList<>();
      List<String> expressions = fields.stream()
         .map(Field::column)
         .filter(c -> c != null && c.startsWith("="))
         .distinct()
         .toList();

      sessions.mutate(sessionToken, user, ChartRenderCheck.checked(assemblyName, expressions,
         (rvs, runtimeId, dispatcher) -> {
            Viewsheet vs = rvs.getViewsheet();
            VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

            if(!(assembly instanceof ChartVSAssembly chart)) {
               throw new IllegalArgumentException(
                  "Unknown chart '" + assemblyName + "'. Geo shelf writes only apply to charts.");
            }

            try {
               result.addAll(apply(rvs, chart, handler, fields, requested));
            }
            catch(Exception e) {
               // The write is already committed and mutate() does not undo a thrown mutation; the
               // current checkpoint is the state before this call (see ChartBindingService.setShelf).
               boolean restored = false;

               try {
                  restored = rvs.restoreCurrentCheckpoint();
               }
               catch(RuntimeException ignore) {
               }

               if(e instanceof IllegalArgumentException) {
                  throw new IllegalArgumentException(e.getMessage() + (restored
                     ? " The chart was left as it was."
                     : " The chart could not be restored; undo the last step in the Composer."), e);
               }

               throw e;
            }
         }));

      return result;
   }

   /**
    * Replaces the chart's geo shelf with {@code fields}, registers and detects their geo columns,
    * and refreshes the runtime geo columns.
    *
    * @throws IllegalArgumentException if the result is a combination the chart cannot render. The
    *         chart is already modified when the post-detection checks refuse; the caller restores
    *         the checkpoint, as {@code ChartBindingService.setShelf} does.
    */
   public static List<Detected> apply(RuntimeViewsheet rvs, ChartVSAssembly chart,
                                      VSChartHandler handler, List<Field> fields)
      throws Exception
   {
      return apply(rvs, chart, handler, fields, null);
   }

   /**
    * @param mapType0 the map type to detect within, or {@code null} to detect it from the data.
    *                 A geo column left over from an earlier call whose mapping is of another
    *                 type is reset, so it cannot keep its old type.
    */
   public static List<Detected> apply(RuntimeViewsheet rvs, ChartVSAssembly chart,
                                      VSChartHandler handler, List<Field> fields, String mapType0)
      throws Exception
   {
      String mapType = requireMapType(mapType0);
      validate(fields, mapType);
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow(
         () -> new IllegalStateException("The viewsheet has no sandbox"));
      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();

      if(!(ainfo.getVSChartInfo() instanceof VSMapInfo info)) {
         throw new IllegalArgumentException(
            "'" + chart.getName() + "' is not a map chart; set the chart type to map first");
      }

      if(mapType != null) {
         info.setMeasureMapType(mapType);
      }

      // A field without a layer keeps its column's own layer, so it is resolved before anything
      // is changed: a column that has none to keep is refused with the chart untouched. The
      // checks that need every layer then run on the resolved list.
      List<Resolved> resolved = resolveLayers(info, chart.getName(), fields);
      validate(resolved.stream().map(Resolved::asField).toList(), mapType);

      // The shelf fields, with the layer asked for on both the field and its geo column.
      info.removeGeoFields();

      for(Resolved r : resolved) {
         Field field = r.field();

         if(info.getGeoColumns().getAttribute(field.column()) == null) {
            handler.changeGeographic(info, field.column(), VSChartHandler.SET_GEOGRAPHIC, true);
         }

         VSChartGeoRef col = (VSChartGeoRef) info.getGeoColumns().getAttribute(field.column());

         if(!r.keep()) {
            col.getGeographicOption().setLayerValue(r.layerValue());
         }

         if(mapType != null) {
            GeographicOption opt = col.getGeographicOption();

            if(!mapType.equals(opt.getMapping().getType())) {
               opt.setMapping(new FeatureMapping());
            }

            opt.getMapping().setType(mapType);
         }

         VSChartGeoRef geo = new VSChartGeoRef();
         geo.setGroupColumnValue(field.column());
         geo.setDataRef(createDataRef(field.column()));
         geo.getGeographicOption().setLayerValue(r.layerValue());
         info.addGeoField(geo);
      }

      // Detection needs the runtime geo columns of the entries just added, and the chart's data.
      handler.updateAllGeoColumns(box, vs, chart);
      DataSet source = handler.getChartData(box, chart);

      if(source == null) {
         throw new IllegalStateException("Failed to execute chart data for geo detection");
      }

      SourceInfo sourceInfo = ainfo.getSourceInfo();

      for(Field field : fields) {
         handler.autoDetect(vs, sourceInfo, info, field.column(), source);
      }

      // Detection may have moved a layer to one the map type provides; carry it onto the fields.
      for(ChartRef ref : info.getGeoFields()) {
         VSChartGeoRef geo = (VSChartGeoRef) ref;
         VSChartGeoRef col = (VSChartGeoRef) info.getGeoColumns().getAttribute(geo.getName());

         if(col != null) {
            geo.getGeographicOption().setLayerValue(col.getGeographicOption().getLayerValue());
         }
      }

      handler.updateAllGeoColumns(box, vs, chart);
      handler.copyGeoColumns(info);
      box.updateAssembly(chart.getAbsoluteName());

      return withMatchStats(check(info, fields), info, source);
   }

   /**
    * Adds the matched/unmatched figures to each detected field, measured against the chart's own
    * data with the mapping and layer detection just settled on, as the Composer's geographic
    * dialog does for its check/X marks.
    */
   static List<Detected> withMatchStats(List<Detected> detected, VSMapInfo info, DataSet source) {
      List<Detected> result = new ArrayList<>();

      for(Detected d : detected) {
         int colIndex = GraphUtil.indexOfHeader(source, d.column());
         VSChartGeoRef col = (VSChartGeoRef) info.getGeoColumns().getAttribute(d.column());

         if(colIndex < 0 || col == null) {
            result.add(d);
            continue;
         }

         Set<String> unmatched = new LinkedHashSet<>(MapHelper.getUnMatchedValues(
            source, colIndex, col.getGeographicOption().getMapping(), info).keySet());
         int distinct = MapHelper.distinctValueCount(source, colIndex);
         List<String> listed = unmatched.stream().limit(MAX_UNMATCHED_LISTED).toList();
         result.add(new Detected(d.column(), d.layer(), d.mapType(),
                                 Math.max(0, distinct - unmatched.size()), distinct, listed,
                                 unmatched.size()));
      }

      return result;
   }

   /**
    * The checks {@code MapGenerator.checkGeoValidity} makes at render time, against the geo
    * columns detection just filled in.
    */
   static List<Detected> check(VSMapInfo info, List<Field> fields) {
      List<Detected> detected = new ArrayList<>();
      String type = null;

      for(Field field : fields) {
         VSChartGeoRef col = (VSChartGeoRef) info.getGeoColumns().getAttribute(field.column());

         if(col == null) {
            throw new IllegalArgumentException(
               "Column '" + field.column() + "' could not be registered as geographic; check " +
               "that it is a column of the chart's source.");
         }

         String name = field.column();

         if(!name.startsWith("=") && !name.startsWith("$(") &&
            info.getRTGeoColumns().getAttribute(name) == null)
         {
            throw new IllegalArgumentException(
               "Column '" + name + "' was not found in the chart's source, so it cannot be " +
               "used as a geographic column.");
         }

         String colType = col.getGeographicOption().getMapping().getType();
         colType = colType == null ? "" : colType;
         int layer = col.getGeographicOption().getLayer();

         if(type != null && !type.equals(colType)) {
            throw new IllegalArgumentException(
               Catalog.getCatalog().getString("composer.graph.mapType.doNotSupport") +
               " '" + field.column() + "' was detected as '" + colType + "' but the other geo " +
               "fields are '" + type + "'.");
         }

         type = colType;

         if(!MapHelper.isLayerSupported(colType, layer)) {
            throw new IllegalArgumentException(
               Catalog.getCatalog().getString("em.common.graph.layerNotSupported",
                                              MapData.getLayerName(layer), colType) +
               " Column '" + field.column() + "'.");
         }

         detected.add(new Detected(field.column(), MapData.getLayerName(layer), colType));
      }

      // Detection can move a layer onto a polygon layer another field already uses.
      long polygons = fields.stream().filter(f -> {
         VSChartGeoRef c = (VSChartGeoRef) info.getGeoColumns().getAttribute(f.column());
         return !MapData.isPointLayer(c.getGeographicOption().getLayer());
      }).count();

      if(polygons > 1) {
         throw new IllegalArgumentException(
            Catalog.getCatalog().getString("em.common.graph.multiPolygons"));
      }

      return detected;
   }

   /**
    * A requested field with the layer value to put on its shelf entry. {@code keep} is true when
    * the field named no layer and the column's own layer stays as it is.
    */
   private record Resolved(Field field, String layerValue, boolean keep) {
      /** The field with its layer named, for the layer checks; null layer when it has no name. */
      Field asField() {
         if(!keep) {
            return field;
         }

         try {
            String name = layerValue == null ? null : MapData.getLayerName(
               Integer.parseInt(layerValue.trim()));
            return new Field(field.column(), name);
         }
         catch(NumberFormatException e) {
            // a variable or expression: nothing to check until it is evaluated
            return new Field(field.column(), null);
         }
      }
   }

   private static List<Resolved> resolveLayers(VSMapInfo info, String assembly, List<Field> fields) {
      List<Resolved> resolved = new ArrayList<>();

      for(Field field : fields) {
         if(field.layer() != null) {
            resolved.add(new Resolved(field, layerId(field) + "", false));
            continue;
         }

         VSChartGeoRef col = (VSChartGeoRef) info.getGeoColumns().getAttribute(field.column());
         String value = col == null ? null : col.getGeographicOption().getLayerValue();

         if(value == null || value.isBlank()) {
            throw new IllegalArgumentException(
               "Column '" + field.column() + "' has no 'layer' and no layer of its own to keep: " +
               (col == null ? "it is not a geographic column of '" + assembly + "' yet. "
                  : "its geographic layer is not set. ") +
               "Pass a layer, one of " + String.join(", ", MapData.getLayerNames()) + ".");
         }

         resolved.add(new Resolved(field, value, true));
      }

      return resolved;
   }

   private static int layerId(Field field) {
      String layer = field.layer();

      if(layer == null || !Arrays.asList(MapData.getLayerNames()).contains(layer)) {
         throw new IllegalArgumentException(
            "Column '" + field.column() + "' has layer '" + layer + "', which is not one of " +
            String.join(", ", MapData.getLayerNames()));
      }

      return MapData.getLayer(layer);
   }

   private static BaseField createDataRef(String name) {
      int dot = name.lastIndexOf('.');

      return dot < 0 ? new BaseField(null, name) :
         new BaseField(name.substring(0, dot), name.substring(dot + 1));
   }
}
