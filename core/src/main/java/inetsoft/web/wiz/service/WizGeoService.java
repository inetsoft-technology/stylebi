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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.aesthetic.StaticShapeFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geo.solver.NameTable;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.report.internal.graph.ChangeChartTypeProcessor;
import inetsoft.report.internal.graph.MapData;
import inetsoft.report.internal.graph.MapHelper;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.FeatureMapping;
import inetsoft.uql.viewsheet.graph.GeoRef;
import inetsoft.uql.viewsheet.graph.GeographicOption;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameContext;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartGeoRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.VSMapInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.viewsheet.controller.chart.VSChartMapService;
import inetsoft.web.wiz.WizUtil;
import inetsoft.web.wiz.model.GeoApplyRequest;
import inetsoft.web.wiz.model.GeoApplyResponse;
import inetsoft.web.wiz.model.GeoDetectRequest;
import inetsoft.web.wiz.model.GeoDetectResponse;
import inetsoft.web.wiz.model.MapViewResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.geom.Rectangle2D;
import java.security.Principal;
import java.util.*;

/**
 * Deterministic geo-binding service for the wiz AI plugin.
 *
 * <p>{@link #detect} marks a chart dimension geographic, runs StyleBI's built-in
 * auto-detection of map type + layer + feature matching, and reports which values
 * failed to match plus the candidate features for the detected layer.
 *
 * <p>{@link #apply} records manual value-to-geoCode mappings supplied by the caller
 * and reports which originally-unmatched values still remain.
 *
 * <p>The sequence mirrors the interactive {@code ChangeGeographicService} /
 * {@code VSMapHandler} flow but is headless (no command dispatcher / UI commands).
 */
@Service
public class WizGeoService {
   public WizGeoService(ViewsheetService viewsheetService, VSChartHandler chartHandler,
                        SecurityEngine securityEngine)
   {
      this.viewsheetService = viewsheetService;
      this.chartHandler = chartHandler;
      this.securityEngine = securityEngine;
   }

   /**
    * Marks {@code column} geographic on the chart and auto-detects its geo type/layer + matching.
    */
   public GeoDetectResponse detect(GeoDetectRequest request, Principal user) throws Exception {
      // Action-level gate ("Visual Composer -> Data Viewsheet"): opens/mutates a viewsheet runtime
      // and (in apply) persists it, so require the composer action right. Mirrors the other wiz
      // viewsheet operations.
      if(!securityEngine.checkPermission(user, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "composer.authorization.permissionDenied"));
      }

      RuntimeViewsheet rvs =
         getRuntimeViewsheet(request.getRuntimeId(), request.getViewsheetIdentifier(), user);
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly chart = getChart(vs, request.getAssemblyName());
      ViewsheetSandbox box = getSandbox(rvs);

      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = ainfo.getVSChartInfo();
      SourceInfo sourceInfo = ainfo.getSourceInfo();
      String refName = request.getColumn();

      if(Tool.isEmptyString(refName)) {
         throw new IllegalArgumentException("column is required");
      }

      // 1. Mark the dimension geographic (adds a VSChartGeoRef to the design geo columns).
      chartHandler.changeGeographic(cinfo, refName, VSChartHandler.SET_GEOGRAPHIC, true);
      // fixMapInfo is part of the confirmed sequence; for "set" it is a no-op that returns false
      // (it only strips x/y/geo fields on "clear"). Kept for parity with the interactive flow.
      chartHandler.fixMapInfo(cinfo, refName, VSChartHandler.SET_GEOGRAPHIC);

      // 2. Build runtime geo columns and execute the chart to obtain the data set.
      chartHandler.updateAllGeoColumns(box, vs, chart);
      DataSet source = chartHandler.getChartData(box, chart);

      if(source == null) {
         throw new IllegalStateException("Failed to execute chart data for geo detection");
      }

      // 3. Auto-detect type/layer and seed the (lazy, empty) feature mapping on the geo ref.
      //    Pre-seed the map type when the caller supplied one; autoDetect skips type detection
      //    if a valid type is already present on the chart info.
      if(!Tool.isEmptyString(request.getGeoType())) {
         cinfo.setMeasureMapType(request.getGeoType());
      }

      chartHandler.autoDetect(vs, sourceInfo, cinfo, refName, source);

      VSChartGeoRef geoRef = getGeoRef(cinfo, refName);

      if(geoRef == null) {
         throw new IllegalStateException("Geographic column '" + refName + "' not found after detection");
      }

      GeographicOption option = geoRef.getGeographicOption();
      FeatureMapping mapping = option.getMapping();
      int layer = option.getLayer();
      String geoType = mapping != null ? mapping.getType() : cinfo.getMeasureMapType();

      // 4. Convert the chart to a MAP if it is not one already. This mirrors the product's
      //    "set column geographic -> change chart type to Map" flow: marking the column
      //    geographic (step 1) populates the geo columns + auto-detected mapping, and
      //    ChangeChartTypeProcessor builds the VSMapInfo, moving the geo dimension into a
      //    geo field. The plugin's create path can't offer a map at recommend time (geo
      //    columns aren't tagged until the chart's data is executed), so the conversion
      //    happens here, where the data is already available.
      if(!(cinfo instanceof VSMapInfo)) {
         int oldType = cinfo.getRTChartType() != 0 ? cinfo.getRTChartType() : cinfo.getChartType();
         VSChartInfo converted = (VSChartInfo) new ChangeChartTypeProcessor(
            oldType, GraphTypes.CHART_MAP, null, cinfo).process();

         if(converted instanceof VSMapInfo mapInfo) {
            // Re-home the bindings deterministically: geo field = the detected column (carrying its
            // auto-detected GeographicOption), and the remaining categorical dimension on color so
            // the map renders as a choropleth. ChangeChartTypeProcessor's geo-field heuristic
            // ("first dim on x") mis-assigns when the geo column wasn't on x (e.g. it was on shape),
            // so we do not rely on its slot assignment.
            fixMapBinding(mapInfo, refName);

            if(!Tool.isEmptyString(geoType)) {
               mapInfo.setMeasureMapType(geoType);
            }

            // Assign visual frames for the (re-homed) aesthetic fields. ChangeChartTypeProcessor
            // already ran this once, but BEFORE we moved the category onto color — without it the
            // color field has a null VisualFrame and graph generation NPEs ("graph.gen.failed").
            try(CategoricalColorFrameContext.Scope scope = CategoricalColorFrameContext.bind(vs)) {
               GraphUtil.fixVisualFrames(mapInfo);
            }

            chart.setVSChartInfo(mapInfo);
            cinfo = mapInfo;
            // Rebuild the runtime geo columns so the map's RT structures reflect the new info.
            chartHandler.updateAllGeoColumns(box, vs, chart);
         }
      }

      // 5. Compute unmatched values + matched count.
      int colIndex = GraphUtil.indexOfHeader(source, refName);
      Set<String> unmatched = new LinkedHashSet<>(
         MapHelper.getUnMatchedValues(source, colIndex, mapping, cinfo).keySet());
      int distinct = MapHelper.distinctValueCount(source, colIndex);

      GeoDetectResponse response = new GeoDetectResponse();
      response.setGeoType(geoType != null ? geoType : "");
      response.setLayer(layer);
      response.setLayerName(MapData.getLayerName(layer));
      response.setMatchedCount(Math.max(0, distinct - unmatched.size()));
      response.setUnmatched(new ArrayList<>(unmatched));
      response.setCandidateFeatures(candidateFeatures(layer));

      // If the runtime was reaped, getRuntimeViewsheet reopened it under a new id; echo it so the
      // client adopts the live runtime for the following geo_apply (mirrors apply()).
      if(!request.getRuntimeId().equals(rvs.getID())) {
         response.setRuntimeId(rvs.getID());
      }

      // Persist the converted map back to the session asset so save_viewsheet (which reads from
      // storage, not the live runtime) captures the map type + geo binding.
      persistViewsheet(vs, request.getViewsheetIdentifier(), user);
      return response;
   }

   /**
    * Applies manual value-to-geoCode mappings on the geographic column and reports remaining
    * unmatched values.
    */
   public GeoApplyResponse apply(GeoApplyRequest request, Principal user) throws Exception {
      // Action-level gate ("Visual Composer -> Data Viewsheet"): mutates and persists a viewsheet,
      // so require the composer action right before touching the runtime.
      if(!securityEngine.checkPermission(user, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "composer.authorization.permissionDenied"));
      }

      RuntimeViewsheet rvs =
         getRuntimeViewsheet(request.getRuntimeId(), request.getViewsheetIdentifier(), user);
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly chart = getChart(vs, request.getAssemblyName());
      ViewsheetSandbox box = getSandbox(rvs);

      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = ainfo.getVSChartInfo();
      String refName = request.getColumn();

      if(Tool.isEmptyString(refName)) {
         throw new IllegalArgumentException("column is required");
      }

      // Refresh runtime geo columns + data so the geo ref and its mapping are available.
      chartHandler.updateAllGeoColumns(box, vs, chart);
      DataSet source = chartHandler.getChartData(box, chart);

      VSChartGeoRef geoRef = getGeoRef(cinfo, refName);

      if(geoRef == null) {
         throw new IllegalArgumentException(
            "Column '" + refName + "' is not geographic; run geo/detect first");
      }

      GeographicOption option = geoRef.getGeographicOption();
      FeatureMapping mapping = option.getMapping();

      if(mapping == null) {
         throw new IllegalStateException("Geographic column '" + refName + "' has no feature mapping");
      }

      String type = mapping.getType();
      int layer = mapping.getLayer();

      if(source == null) {
         throw new IllegalStateException("Failed to execute chart data for geo apply");
      }

      // Originally-unmatched values BEFORE applying the manual mappings.
      Set<String> originalUnmatched = new LinkedHashSet<>(MapHelper.getUnMatchedValues(
            source, GraphUtil.indexOfHeader(source, refName), mapping, cinfo).keySet());

      // Apply each mapping. The supplied value is a geoCode; if a caller passed a display label,
      // resolve it to a geoCode via getGeoCodeByLabel.
      Map<String, String> requested = request.getMappings() != null
         ? request.getMappings() : Collections.emptyMap();
      Map<String, String> applied = new LinkedHashMap<>();

      for(Map.Entry<String, String> e : requested.entrySet()) {
         String value = e.getKey();
         String code = e.getValue();

         if(value == null || code == null) {
            continue;
         }

         String resolved = resolveGeoCode(type, layer, code);
         mapping.addMapping(value, resolved);
         applied.put(value, resolved);
      }

      Set<String> dropped = request.getDrop() != null
         ? new LinkedHashSet<>(request.getDrop()) : new LinkedHashSet<>();

      Set<String> stillUnmatched =
         WizGeoMappingResolver.stillUnmatched(originalUnmatched, applied, dropped);

      GeoApplyResponse response = new GeoApplyResponse();
      response.setStatus(stillUnmatched.isEmpty() ? GeoApplyResponse.STATUS_COMPLETE : GeoApplyResponse.STATUS_PARTIAL);
      response.setStillUnmatched(new ArrayList<>(stillUnmatched));

      // If the runtime was reaped, getRuntimeViewsheet reopened it under a new id; echo it so the
      // client adopts the live runtime for its next edit (mirrors the create/modify path).
      if(!request.getRuntimeId().equals(rvs.getID())) {
         response.setRuntimeId(rvs.getID());
      }

      // Persist the resolved feature mappings to the session asset so a saved map keeps them.
      persistViewsheet(vs, request.getViewsheetIdentifier(), user);
      return response;
   }

   /**
    * Removes the geographic mark from {@code column} (the Composer's "Clear Geographic"), the
    * inverse of {@link #detect}. Mirrors {@code ChangeGeographicService}: refresh the runtime geo
    * columns first (the clear branch of {@code changeGeographic} walks them by index), clear the
    * design column, strip the same-name lat/lon refs and geo field, then fix the map frame. Fails
    * loud when the column is not geographic or the clear changed nothing.
    *
    * <p>The response has the {@code detect} shape with empty detection results.
    */
   public GeoDetectResponse clear(RuntimeViewsheet rvs, String assemblyName, String column)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly chart = getChart(vs, assemblyName);
      ViewsheetSandbox box = getSandbox(rvs);

      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = ainfo.getVSChartInfo();
      String refName = column;

      if(Tool.isEmptyString(refName)) {
         throw new IllegalArgumentException("column is required");
      }

      chartHandler.updateGeoColumns(box, vs, chart, cinfo);
      ColumnSelection rtCols = cinfo.getRTGeoColumns();
      ColumnSelection designCols = cinfo.getGeoColumns();

      if(rtCols == null || designCols == null ||
         rtCols.getAttribute(refName) == null || designCols.getAttribute(refName) == null)
      {
         throw new IllegalArgumentException(
            "Column '" + refName + "' is not geographic on chart '" + assemblyName +
            "'; there is nothing to clear. Use set_chart_geo_shelf or detect_geographic to mark a column geographic.");
      }

      VSChartInfo before = (VSChartInfo) cinfo.clone();

      try {
         DataRef removed = chartHandler.changeGeographic(
            cinfo, refName, VSChartHandler.CLEAR_GEOGRAPHIC, true);

         if(removed == null || cinfo.getGeoColumns().getAttribute(refName) != null) {
            throw new IllegalStateException(
               "Failed to clear geographic column '" + refName + "': it is still geographic");
         }

         chartHandler.fixMapInfo(cinfo, refName, VSChartHandler.CLEAR_GEOGRAPHIC);
         new ChangeChartProcessor().fixMapFrame(before, cinfo);
         chart.setVSAssemblyInfo(ainfo);
         box.updateAssembly(chart.getAbsoluteName());
         chartHandler.updateGeoColumns(box, vs, chart, cinfo);
      }
      catch(Exception | Error ex) {
         // changeGeographic mutates the design chart info in place; leave it untouched on failure
         ainfo.setVSChartInfo(before);
         chart.setVSAssemblyInfo(ainfo);
         throw ex;
      }

      GeoDetectResponse response = new GeoDetectResponse();
      String mapType = cinfo.getMeasureMapType();
      response.setGeoType(mapType != null ? mapType : "");
      response.setLayer(0);
      response.setLayerName("");
      response.setMatchedCount(0);
      response.setUnmatched(new ArrayList<>());
      response.setCandidateFeatures(new ArrayList<>());

      return response;
   }

   /** Reads the saved zoom/pan of a map chart. */
   public MapViewResponse getMapView(RuntimeViewsheet rvs, String assemblyName) {
      return mapView(getMapChart(rvs.getViewsheet(), assemblyName));
   }

   /**
    * Applies a relative zoom/pan, or resets the view, on a map chart's design descriptor via the
    * same {@link VSChartMapService#applyZoomPan} the interactive map uses. Unlike the interactive
    * path a failure (e.g. zoom out of range) is raised, not swallowed. The data-bbox anchor
    * ({@code lonLat}) is not persisted by StyleBI, so a saved viewport is replayed against the
    * current data's extent.
    */
   public MapViewResponse setMapView(RuntimeViewsheet rvs, String name, Integer zoomBy,
                                     Double panX, Double panY, Boolean resetFlag)
      throws Exception
   {
      boolean reset = Boolean.TRUE.equals(resetFlag);

      if(reset && (zoomBy != null || panX != null || panY != null)) {
         throw new IllegalArgumentException(
            "reset cannot be combined with zoomBy, panX or panY");
      }

      if(!reset && zoomBy == null && panX == null && panY == null) {
         throw new IllegalArgumentException(
            "Nothing to set: pass zoomBy, panX/panY, or reset:true");
      }

      for(Double d : new Double[] { panX, panY }) {
         if(d != null && (d.isNaN() || d.isInfinite())) {
            throw new IllegalArgumentException("panX and panY must be finite numbers");
         }
      }

      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly chart = getMapChart(vs, name);
      ViewsheetSandbox box = getSandbox(rvs);
      ChartDescriptor desc = chart.getChartDescriptor();
      PlotDescriptor plot = desc.getPlotDescriptor();

      if(reset) {
         VSChartMapService.clearPanZoom(plot);
      }
      else {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
         double oldZoom = plot.getZoom();
         int oldZoomLevel = plot.getZoomLevel();
         double oldPanX = plot.getPanX();
         double oldPanY = plot.getPanY();
         Rectangle2D lonLat = plot.getLonLat();

         try {
            VGraphPair vpair = box.getVGraphPair(name, true, info.getMaxSize());

            if(vpair == null) {
               throw new IllegalStateException(
                  "Chart '" + name + "' has not rendered, so its map view cannot be changed");
            }

            VSChartMapService.applyZoomPan(
               zoomBy == null ? 0 : zoomBy, panX == null ? 0 : panX, panY == null ? 0 : panY,
               desc, vpair.getRealSizeVGraph(), vpair.getEGraph(), vpair.getData());
         }
         catch(Exception ex) {
            // applyZoomPan mutates the design plot step by step, so put every field back
            plot.setZoom(oldZoom);
            plot.setZoomLevel(oldZoomLevel);
            plot.setPanX(oldPanX);
            plot.setPanY(oldPanY);
            plot.setLonLat(lonLat);
            throw new IllegalArgumentException(
               "Could not apply the map view to '" + name + "': " + ex.getMessage(), ex);
         }
      }

      chart.getChartInfo().setRTChartDescriptor(null);
      box.clearGraph(name);
      return mapView(chart);
   }

   private MapViewResponse mapView(ChartVSAssembly chart) {
      PlotDescriptor plot = chart.getChartDescriptor().getPlotDescriptor();
      MapViewResponse response = new MapViewResponse();
      response.setZoom(plot.getZoom());
      response.setZoomLevel(plot.getZoomLevel());
      response.setPanX(plot.getPanX());
      response.setPanY(plot.getPanY());
      response.setWebMap(plot.isWebMap());
      return response;
   }

   private ChartVSAssembly getMapChart(Viewsheet vs, String assemblyName) {
      ChartVSAssembly chart = getChart(vs, assemblyName);
      VSChartInfo info = chart.getVSChartInfo();

      if(info == null || !GraphTypes.isGeo(info.getChartType())) {
         throw new IllegalArgumentException(
            "Chart '" + assemblyName + "' is not a map or map_contour chart, so it has no map view");
      }

      return chart;
   }


   // ── helpers ──────────────────────────────────────────────────────────────────

   /**
    * Writes the (mutated) runtime viewsheet back to its session asset so save_viewsheet — which
    * reads the source viewsheet from the asset repository, not the live runtime — captures the
    * geo binding / map conversion. No-op when no identifier is supplied. Mirrors the in-place
    * persistence used by the apply-filter flow.
    */
   private void persistViewsheet(Viewsheet vs, String viewsheetIdentifier, Principal user) {
      if(Tool.isEmptyString(viewsheetIdentifier)) {
         return;
      }

      try {
         AssetEntry entry = AssetEntry.createAssetEntry(viewsheetIdentifier);

         if(entry != null) {
            // Restrict writes to the managed wiz folders, mirroring WizVsService.persistViewsheet:
            // ROOT holds session viewsheets, COMPONENTS holds saved visualizations. Prevents the
            // mutated (map-converted) viewsheet being written to an arbitrary caller-supplied
            // identifier outside the wiz-managed area.
            String path = entry.getPath();

            if(path == null ||
               !(path.startsWith(WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/") ||
                 path.startsWith(WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/")))
            {
               throw new IllegalArgumentException(
                  "viewsheetIdentifier points outside the managed visualizations folder: " + path);
            }

            viewsheetService.setViewsheet(vs, entry, user, true, true);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to persist geo viewsheet to {}: {}", viewsheetIdentifier, ex.getMessage());
      }
   }

   /**
    * Forces the converted map's binding to the choropleth we want: the detected geographic column is
    * the sole geo field (carrying its auto-detected {@link GeographicOption}), and the remaining
    * categorical dimension drives the fill color.
    *
    * <p>This does not trust {@code ChangeChartTypeProcessor}'s slot assignment: its geo-field
    * heuristic moves the "first dimension on x" to the geo field, which mis-binds when the geo
    * column was on a different slot (e.g. shape) — in the "most-popular category per region" case it
    * wrongly makes the <em>category</em> the geo field and leaves the region on shape. We re-home
    * explicitly off the known geo column name.
    */
   private void fixMapBinding(VSMapInfo mapInfo, String geoColumn) {
      if(geoColumn == null) {
         return;
      }

      // Guard before any mutations.
      DataRef geoColRef = mapInfo.getGeoColumns().getAttribute(geoColumn);

      if(!(geoColRef instanceof ChartRef geoChartRef)) {
         LOG.warn("Unable to fix geo binding: geo column '{}' not found in geoColumns", geoColumn);
         return;
      }

      // Safe to mutate now.
      ChartRef category = firstNonGeoGeoField(mapInfo, geoColumn);
      category = stripNonGeoDims(mapInfo, Slot.X, geoColumn, category);
      category = stripNonGeoDims(mapInfo, Slot.Y, geoColumn, category);
      category = stripNonGeoDims(mapInfo, Slot.GROUP, geoColumn, category);

      mapInfo.removeGeoFields();
      mapInfo.addGeoField((ChartRef) geoChartRef.clone());

      // A polygon map can't use the shape aesthetic; drop it (the region often lands there).
      mapInfo.setShapeField(null);

      // Category := color, as a plain categorical dimension (the source may have been a geo ref).
      // Both candidate sources (a mis-assigned geo field or an axis/group dim) are VSChartDimensionRefs.
      if(category instanceof VSChartDimensionRef catDim && mapInfo.getColorField() == null) {
         VSChartDimensionRef colorDim = new VSChartDimensionRef(catDim.getDataRef());
         // Carry the binding value so the color ref is fully formed (matches a native dimension ref);
         // without it the column resolves only lazily and a saved copy can lose the binding.
         colorDim.setGroupColumnValue(catDim.getGroupColumnValue());
         VSAestheticRef color = new VSAestheticRef();
         color.setDataRef(colorDim);
         mapInfo.setColorField(color);
      }

      // No category dimension to color by → shade the choropleth by the MEASURE instead (mirrors
      // MapChartFilter.putInside: on a polygon map the aggregate drives the fill color). Move the
      // first aggregate left on an axis onto color so the regions reflect the value (e.g. account
      // count per state) instead of rendering a single flat shade.
      if(mapInfo.getColorField() == null && !hasPointField(mapInfo)) {
         VSChartAggregateRef measure = takeFirstAxisAggregate(mapInfo);

         if(measure != null) {
            VSAestheticRef color = new VSAestheticRef();
            color.setDataRef(measure);
            mapInfo.setColorField(color);
         }
      }

      // Render filled polygons (a choropleth) rather than point markers: with no point-layer geo
      // field and no size field, set a NIL static shape frame so the regions fill instead of
      // drawing a shape at each centroid. Mirrors MapChartFilter.createChartInfo.
      if(!hasPointField(mapInfo) && mapInfo.getSizeField() == null) {
         mapInfo.setShapeFrame(new StaticShapeFrame(GShape.NIL));
      }
   }

   /** True if any geo field is bound at a point (non-polygon) layer. */
   private boolean hasPointField(VSMapInfo mapInfo) {
      for(ChartRef f : mapInfo.getGeoFields()) {
         if(f instanceof GeoRef geo && geo.getGeographicOption() != null &&
            MapData.isPointLayer(geo.getGeographicOption().getLayer()))
         {
            return true;
         }
      }

      return false;
   }

   private enum Slot { X, Y, GROUP }

   /** The first geo FIELD whose name is not the geo column (a dimension the conversion mis-assigned). */
   private ChartRef firstNonGeoGeoField(VSMapInfo mapInfo, String geoColumn) {
      for(ChartRef gf : mapInfo.getGeoFields()) {
         if(gf != null && !geoColumn.equals(gf.getName())) {
            return gf;
         }
      }

      return null;
   }

   /** Removes non-geo dimensions from the given slot; returns the category (first found, or prior). */
   private ChartRef stripNonGeoDims(VSMapInfo mapInfo, Slot slot, String geoColumn, ChartRef category) {
      ChartRef[] fields = switch(slot) {
         case X -> mapInfo.getXFields();
         case Y -> mapInfo.getYFields();
         case GROUP -> mapInfo.getGroupFields();
      };

      for(int i = fields.length - 1; i >= 0; i--) {
         ChartRef ref = fields[i];

         if(ref instanceof XDimensionRef) {
            // A non-geo dimension becomes the fill-color category. The geo column itself must ALSO be
            // removed from the axis — otherwise it both draws the polygons (as the geo field) and
            // facets the map into one panel per value (the small-multiples bug). Only the
            // category-assignment is guarded by name; the axis removal applies to both.
            if(!geoColumn.equals(ref.getName()) && category == null) {
               category = ref;
            }

            switch(slot) {
               case X -> mapInfo.removeXField(i);
               case Y -> mapInfo.removeYField(i);
               case GROUP -> mapInfo.removeGroupField(i);
            }
         }
      }

      return category;
   }

   /** Removes and returns the first aggregate (measure) found on the X or Y axis, or null. */
   private VSChartAggregateRef takeFirstAxisAggregate(VSMapInfo mapInfo) {
      ChartRef[] xfields = mapInfo.getXFields();

      for(int i = xfields.length - 1; i >= 0; i--) {
         if(xfields[i] instanceof VSChartAggregateRef agg) {
            mapInfo.removeXField(i);
            return agg;
         }
      }

      ChartRef[] yfields = mapInfo.getYFields();

      for(int i = yfields.length - 1; i >= 0; i--) {
         if(yfields[i] instanceof VSChartAggregateRef agg) {
            mapInfo.removeYField(i);
            return agg;
         }
      }

      return null;
   }

   private RuntimeViewsheet getRuntimeViewsheet(String runtimeId, String viewsheetIdentifier,
                                                Principal user)
      throws Exception
   {
      if(Tool.isEmptyString(runtimeId)) {
         throw new IllegalArgumentException("runtimeId is required");
      }

      // Transparently restore a reaped runtime from its durable asset identifier.
      RuntimeViewsheet rvs =
         WizUtil.getViewsheetOrRestore(viewsheetService, runtimeId, viewsheetIdentifier, user);

      if(rvs == null || rvs.getViewsheet() == null) {
         throw new IllegalStateException("Runtime viewsheet not found: " + runtimeId);
      }

      return rvs;
   }

   private ChartVSAssembly getChart(Viewsheet vs, String assemblyName) {
      if(Tool.isEmptyString(assemblyName)) {
         throw new IllegalArgumentException("assemblyName is required");
      }

      if(!(vs.getAssembly(assemblyName) instanceof ChartVSAssembly chart)) {
         throw new IllegalArgumentException("Chart assembly not found: " + assemblyName);
      }

      return chart;
   }

   private ViewsheetSandbox getSandbox(RuntimeViewsheet rvs) {
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         throw new IllegalStateException("ViewsheetSandbox is empty");
      }

      return box.get();
   }

   /**
    * Resolves the geographic ref for {@code refName} from the runtime geo columns. Mirrors the
    * (package-private) {@code VSChartHandler.getGeoRef} via the public {@code getRTGeoColumns}.
    */
   private VSChartGeoRef getGeoRef(VSChartInfo cinfo, String refName) {
      DataRef ref = cinfo.getRTGeoColumns().getAttribute(refName);

      if(ref instanceof VSChartGeoRef geoRef) {
         return geoRef;
      }

      // Fall back to the design geo columns in case the runtime columns are not yet built.
      ref = cinfo.getGeoColumns().getAttribute(refName);
      return ref instanceof VSChartGeoRef geoRef ? geoRef : null;
   }

   /** Treats {@code code} as a geoCode; if it is a display label, resolves it to a geoCode. */
   private String resolveGeoCode(String type, int layer, String code) {
      NameTable names = MapData.getNameTable(layer);

      if(names != null && names.getNames().contains(code)) {
         // Already a valid geoCode.
         return code;
      }

      String resolved = MapHelper.getGeoCodeByLabel(type, layer, code);

      if(resolved == null) {
         LOG.warn("Could not resolve '{}' to a geoCode for type={} layer={}; using as-is", code, type, layer);
      }

      return resolved != null ? resolved : code;
   }

   /** Candidate feature display labels for the given layer's name table, capped at {@value MAX_CANDIDATES}. */
   private List<String> candidateFeatures(int layer) {
      NameTable names = MapData.getNameTable(layer);

      if(names == null) {
         return Collections.emptyList();
      }

      return names.getNames().stream()
         .limit(MAX_CANDIDATES)
         .map(code -> { String label = names.getLabel(code); return label != null ? label : code; })
         .collect(java.util.stream.Collectors.toList());
   }

   private static final int MAX_CANDIDATES = 500;

   private final ViewsheetService viewsheetService;
   private final VSChartHandler chartHandler;
   private final SecurityEngine securityEngine;

   private static final Logger LOG = LoggerFactory.getLogger(WizGeoService.class);
}
