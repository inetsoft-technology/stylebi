/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.binding;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.SummaryAttr;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.model.graph.ChartGeoRefModel;
import inetsoft.web.binding.model.graph.MapFeatureModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

@Service
@ClusterProxy
public class VSChartBindingService {
   public VSChartBindingService(VisualFrameModelFactoryService visualService,
                                   ChartRefModelFactoryService refService,
                                   VSBindingService bindingService,
                                   VSMapHandler mapHandler,
                                   VSChartDataHandler chartDataHandler,
                                   VSChartHandler chartHandler, ViewsheetService viewsheetService)
   {
      this.visualService = visualService;
      this.refService = refService;
      this.bindingService = bindingService;
      this.mapHandler = mapHandler;
      this.chartDataHandler = chartDataHandler;
      this.chartHandler = chartHandler;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BindingModel getChartBinding(@ClusterProxyKey String vsId, String assemblyName,
                                       Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ChartVSAssembly assembly = (ChartVSAssembly) viewsheet.getAssembly(assemblyName);

      return bindingService.createModel(assembly);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BindingModel setChartBinding(@ClusterProxyKey String vsId, String assemblyName,
                                       ChartBindingModel cmodel, Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ChartVSAssembly assembly =
         (ChartVSAssembly) viewsheet.getAssembly(assemblyName).clone();
      assembly = (ChartVSAssembly) bindingService.updateAssembly(cmodel, assembly);
      ChartVSAssemblyInfo info = assembly.getChartInfo();
      chartDataHandler.changeChartData(rvs, info, null, null, null, null);
      assembly = (ChartVSAssembly) viewsheet.getAssembly(assemblyName);

      return bindingService.createModel(assembly);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean refreshChart(@ClusterProxyKey String vsId, String assemblyName,
                            String chartWidth, String chartHeight, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ChartVSAssembly assembly = (ChartVSAssembly) viewsheet.getAssembly(assemblyName);
      ChartVSAssemblyInfo assemblyInfo =
         (ChartVSAssemblyInfo) assembly.getChartInfo().clone();
      Dimension reqsize =
         new Dimension(Integer.parseInt(chartWidth), Integer.parseInt(chartHeight));
      chartDataHandler.refreshChart(rvs, assemblyInfo, reqsize);
      return true;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public java.util.List<NamedGroupInfoModel> getNamedGroups(
      @ClusterProxyKey String vsId, String assemblyName, String fieldName, Principal principal)
      throws Exception
   {
      ChartInfo cinfo = chartHandler.getChartInfo(vsId, assemblyName, principal);
      DataRef ref = cinfo.getRTFieldByFullName(fieldName);
      List<NamedGroupInfoModel> ngs = new ArrayList<>();

      if(ref != null && ref instanceof XDimensionRef) {
         DataRef fld = ((XDimensionRef) ref).getDataRef();
         AssetRepository rep = AssetUtil.getAssetRepository(false);
         AssetNamedGroupInfo[] infos =
            SummaryAttr.getAssetNamedGroupInfos(fld, rep, null);

         for(int k = 0; k < infos.length; k++) {
            ngs.add(new NamedGroupInfoModel(infos[k]));
         }
      }

      return ngs;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String getMappingStatus(@ClusterProxyKey String vsId, String assemblyName,
                                  String refName, String type, FeatureMapping mapping,
                                  Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) viewsheet.getAssembly(assemblyName);
      boolean allMapped = mapHandler.getMappingStatus(rvs, chart, refName, mapping);

      return allMapped ? "true" : "false";
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Map<String, Object> getGeoData(@ClusterProxyKey String vsId, String assemblyName,
                                         String refName, Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) viewsheet.getAssembly(assemblyName);

      return mapHandler.getGeoData(rvs, chart, refName);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Map<String, Object> changeMapType(@ClusterProxyKey String vsId,
                                            String assemblyName, String type, String layerstr,
                                            String refName, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) viewsheet.getAssembly(assemblyName);

      return mapHandler.changeMapType(rvs, chart, refName, type, layerstr);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BindingModel convertChartRef(
      @ClusterProxyKey String vsId, String assemblyName, String refName,
      int type, Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly assembly = (ChartVSAssembly) vs.getAssembly(assemblyName);
      chartDataHandler.convertChartRef(rvs, assembly, refName, type);

      return bindingService.createModel(assembly);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Map<String, Object> getMappingData(@ClusterProxyKey String vsId,
                                             String assemblyName, ChartGeoRefModel geo,
                                             Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) viewsheet.getAssembly(assemblyName);
      VSChartInfo cinfo = chart.getVSChartInfo();
      VSChartGeoRef ref = (VSChartGeoRef) refService.pasteChartRef(cinfo, geo);

      return mapHandler.getMappingData(rvs, chart, ref);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public List<MapFeatureModel> getLikelyFeatures(@ClusterProxyKey String vsId,
                                                  String assemblyName,
                                                  String row, String algorithm,
                                                  ChartGeoRefModel geo, Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) viewsheet.getAssembly(assemblyName);
      VSChartInfo cinfo = chart.getVSChartInfo();
      VSChartInfo ocinfo = (VSChartInfo) cinfo.clone();
      VSChartGeoRef ref = (VSChartGeoRef) refService.pasteChartRef(cinfo, geo);
      int nrow = Integer.parseInt(row);

      List<MapFeatureModel> features = mapHandler.getLikelyFeatures(rvs, chart, ref, nrow,
                                                                    algorithm);
      chart.setVSChartInfo(ocinfo);

      return features;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BindingModel setGeographic(@ClusterProxyKey String vsId,
                                     String assemblyName,
                                     String refName, boolean isDim,
                                     String type, ChartBindingModel cmodel,
                                     Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ChartVSAssembly assembly =
         (ChartVSAssembly) viewsheet.getAssembly(assemblyName).clone();
      assembly = (ChartVSAssembly) bindingService.updateAssembly(cmodel, assembly);
      ChartVSAssemblyInfo info = assembly.getChartInfo();
      chartDataHandler.setGeographic(rvs, assemblyName, info, refName, isDim, type);
      assembly = (ChartVSAssembly) viewsheet.getAssembly(assemblyName);

      return bindingService.createModel(assembly);
   }


   private final VisualFrameModelFactoryService visualService;
   private ChartRefModelFactoryService refService;
   private VSBindingService bindingService;
   private VSMapHandler mapHandler;
   private VSChartDataHandler chartDataHandler;
   private VSChartHandler chartHandler;
   private ViewsheetService viewsheetService;
}
