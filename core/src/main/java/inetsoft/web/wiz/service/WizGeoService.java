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
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geo.solver.NameTable;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.graph.MapData;
import inetsoft.report.internal.graph.MapHelper;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.FeatureMapping;
import inetsoft.uql.viewsheet.graph.GeographicOption;
import inetsoft.uql.viewsheet.graph.VSChartGeoRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.wiz.model.GeoApplyRequest;
import inetsoft.web.wiz.model.GeoApplyResponse;
import inetsoft.web.wiz.model.GeoDetectRequest;
import inetsoft.web.wiz.model.GeoDetectResponse;
import org.springframework.stereotype.Service;

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
   public WizGeoService(ViewsheetService viewsheetService, VSChartHandler chartHandler) {
      this.viewsheetService = viewsheetService;
      this.chartHandler = chartHandler;
   }

   /**
    * Marks {@code column} geographic on the chart and auto-detects its geo type/layer + matching.
    */
   public GeoDetectResponse detect(GeoDetectRequest request, Principal user) throws Exception {
      RuntimeViewsheet rvs = getRuntimeViewsheet(request.getRuntimeId(), user);
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
      chartHandler.autoDetect(vs, sourceInfo, cinfo, refName, source);

      VSChartGeoRef geoRef = getGeoRef(cinfo, refName);

      if(geoRef == null) {
         throw new IllegalStateException("Geographic column '" + refName + "' not found after detection");
      }

      GeographicOption option = geoRef.getGeographicOption();
      FeatureMapping mapping = option.getMapping();
      int layer = option.getLayer();
      String geoType = mapping != null ? mapping.getType() : cinfo.getMeasureMapType();

      // 4. Compute unmatched values + matched count.
      int colIndex = GraphUtil.indexOfHeader(source, refName);
      Set<String> unmatched = new LinkedHashSet<>(
         MapHelper.getUnMatchedValues(source, colIndex, mapping, cinfo).keySet());
      int distinct = distinctValueCount(source, colIndex);

      GeoDetectResponse response = new GeoDetectResponse();
      response.setGeoType(geoType);
      response.setLayer(layer);
      response.setMatchedCount(Math.max(0, distinct - unmatched.size()));
      response.setUnmatched(new ArrayList<>(unmatched));
      response.setCandidateFeatures(candidateFeatures(layer));
      return response;
   }

   /**
    * Applies manual value-to-geoCode mappings on the geographic column and reports remaining
    * unmatched values.
    */
   public GeoApplyResponse apply(GeoApplyRequest request, Principal user) throws Exception {
      RuntimeViewsheet rvs = getRuntimeViewsheet(request.getRuntimeId(), user);
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

      // Originally-unmatched values BEFORE applying the manual mappings.
      Set<String> originalUnmatched = source == null
         ? new LinkedHashSet<>()
         : new LinkedHashSet<>(MapHelper.getUnMatchedValues(
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
      response.setStatus(stillUnmatched.isEmpty() ? "complete" : "partial");
      response.setStillUnmatched(new ArrayList<>(stillUnmatched));
      return response;
   }

   // ── helpers ──────────────────────────────────────────────────────────────────

   private RuntimeViewsheet getRuntimeViewsheet(String runtimeId, Principal user) throws Exception {
      if(Tool.isEmptyString(runtimeId)) {
         throw new IllegalArgumentException("runtimeId is required");
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, user);

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
      return resolved != null ? resolved : code;
   }

   /** Distinct number of non-null values in the geo column. */
   private int distinctValueCount(DataSet source, int colIndex) {
      if(colIndex < 0) {
         return 0;
      }

      Set<String> distinct = new HashSet<>();

      for(int r = 0; r < source.getRowCount(); r++) {
         Object v = source.getData(colIndex, r);

         if(v != null) {
            distinct.add(Tool.toString(v));
         }
      }

      return distinct.size();
   }

   /** Candidate feature display labels for the given layer's name table. */
   private List<String> candidateFeatures(int layer) {
      NameTable names = MapData.getNameTable(layer);

      if(names == null) {
         return Collections.emptyList();
      }

      List<String> labels = new ArrayList<>();

      for(String code : names.getNames()) {
         String label = names.getLabel(code);
         labels.add(label != null ? label : code);
      }

      return labels;
   }

   private final ViewsheetService viewsheetService;
   private final VSChartHandler chartHandler;
}
