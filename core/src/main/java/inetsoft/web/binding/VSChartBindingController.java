/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.graph.aesthetic.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.SummaryAttr;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.model.graph.ChartGeoRefModel;
import inetsoft.web.binding.model.graph.MapFeatureModel;
import inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.reportviewer.HandleExceptions;
import inetsoft.web.viewsheet.OrganizationID;
import inetsoft.web.viewsheet.SwitchOrg;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

/**
 * Controller that provides a REST endpoint for composer viewsheet actions.
 */
@RestController
public class VSChartBindingController {
   @Autowired
   public VSChartBindingController(VisualFrameModelFactoryService visualService,
                                   VSChartBindingServiceProxy chartBindingService)
   {
      this.visualService = visualService;
      this.chartBindingService = chartBindingService;
   }

   @RequestMapping(value = "/api/composer/binding", method = RequestMethod.GET)
   public BindingModel getChartBinding(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName, Principal principal)
      throws Exception
   {
      return chartBindingService.getChartBinding(vsId, assemblyName, principal);
   }

   @RequestMapping(value = "/api/composer/binding", method = RequestMethod.PUT)
   public BindingModel setChartBinding(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestBody ChartBindingModel cmodel, Principal principal) throws Exception
   {
      return chartBindingService.setChartBinding(vsId, assemblyName, cmodel, principal);
   }

   @RequestMapping(value = "/api/composer/refresh", method = RequestMethod.GET)
   public void refreshChart(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("chartWidth") String chartWidth,
      @RequestParam("chartHeight") String chartHeight,
      Principal principal) throws Exception
   {
      chartBindingService.refreshChart(vsId, assemblyName, chartWidth, chartHeight, principal);
   }

   @RequestMapping(value = "/api/composer/namedgroup", method = RequestMethod.GET)
   public List<NamedGroupInfoModel> getNamedGroups(
      @RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("fieldName") String fieldName, Principal principal)
      throws Exception
   {
      return chartBindingService.getNamedGroups(vsId, assemblyName, fieldName, principal);
   }

   @RequestMapping(value = "/api/composer/getMappingStatus", method = RequestMethod.PUT)
   public String getMappingStatus(@RequestParam("vsId") String vsId,
           @RequestParam("assemblyName") String assemblyName,
           @RequestParam(value = "refName", required=false) String refName,
           @RequestParam(value = "type", required=false) String type,
           @RequestBody FeatureMapping mapping,
           Principal principal) throws Exception
   {
      return chartBindingService.getMappingStatus(vsId, assemblyName, refName, type, mapping, principal);
   }

   @RequestMapping(value = "/api/composer/getGeoData", method = RequestMethod.GET)
   public Map<String, Object> getGeoData(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("refName") String refName, Principal principal) throws Exception
   {
      return chartBindingService.getGeoData(vsId, assemblyName, refName, principal);
   }

   @RequestMapping(value = "/api/composer/changeMapType", method = RequestMethod.GET)
   public Map<String, Object> changeMapType(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("type") String type,
      @RequestParam(value="layer", required=false) String layerstr,
      @RequestParam("refName") String refName, Principal principal)
      throws Exception
   {
      return chartBindingService.changeMapType(vsId, assemblyName, type, layerstr, refName, principal);
   }

   @RequestMapping(value = "/api/composer/convert", method = RequestMethod.GET)
   public BindingModel convertChartRef(
      @RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("refName") String refName,
      @RequestParam("type") int type, Principal principal) throws Exception
   {
      return chartBindingService.convertChartRef(vsId, assemblyName, refName, type, principal);
   }

   @RequestMapping(value = "/api/composer/getMappingData", method = RequestMethod.PUT)
   public Map<String, Object> getMappingData(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName, @RequestBody ChartGeoRefModel geo,
      Principal principal) throws Exception
   {
      return chartBindingService.getMappingData(vsId, assemblyName, geo, principal);
   }

   @RequestMapping(value = "/api/composer/getLikelyFeatures", method = RequestMethod.PUT)
   public List<MapFeatureModel> getLikelyFeatures(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("row") String row, @RequestParam("algorithm") String algorithm,
      @RequestBody ChartGeoRefModel geo, Principal principal) throws Exception
   {
      return chartBindingService.getLikelyFeatures(vsId, assemblyName, row, algorithm, geo, principal);
   }

   @RequestMapping(value = "/api/composer/changeGeographic", method = RequestMethod.PUT)
   public BindingModel setGeographic(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("refName") String refName, @RequestParam("isDim") boolean isDim,
      @RequestParam("type") String type, @RequestBody ChartBindingModel cmodel,
      Principal principal) throws Exception
   {
      return chartBindingService.setGeographic(vsId, assemblyName, refName, isDim, type, cmodel, principal);
   }

   @GetMapping("/api/composer/imageShapes")
   @ResponseBody
   public String[] getImageShapes() throws Exception{
      return ImageShapes.getShapeNames().toArray(new String[0]);
   }

   @GetMapping(value = "/api/composer/imageShape/{name:.+}")
   public void getImageShape(
      @PathVariable("name") String name,
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception
   {
      name = Tool.byteDecode(name);
      GShape shape = ImageShapes.getShape(name);
      Image img = null;

      if(shape instanceof GShape.ImageShape) {
         img = ((GShape.ImageShape) shape).getImage();
      }
      else if(shape instanceof SVGShape) {
         img = ((SVGShape) shape).getImage(new Dimension(16, 16));
      }

      if(img != null) {
         int iw = img.getWidth(null);
         int ih = img.getHeight(null);

         // limit image size to 32px.
         if(iw > 32 || ih > 32) {
            if(iw > 32) {
               ih = ih * 32 / iw;
               iw = 32;
            }

            img = img.getScaledInstance(iw, ih, Image.SCALE_SMOOTH);
         }

         byte[] buf = VSUtil.getImageBytes(img, 72);

         if(buf != null && response != null) {
            ServletOutputStream outputStream = response.getOutputStream();
            outputStream.write(buf);
            outputStream.flush();
         }
      }
   }

   @GetMapping("/api/composer/customChartTypes")
   @ResponseBody
   public String[] getChartTypes() throws Exception {
      return Tool.split(SreeEnv.getProperty("custom.chart.types", ""), ',');
   }

   @GetMapping("/api/composer/customChartFrames")
   @ResponseBody
   public String[] getChartFrames() throws Exception {
      return Tool.split(SreeEnv.getProperty("custom.chart.frames", ""), ',');
   }

   @RequestMapping(value = "/api/composer/chart/colorpalettes", method = RequestMethod.GET)
   @HandleExceptions
   @SwitchOrg
   public CategoricalColorModel[] getColorPalettes(@OrganizationID String orgId, Principal principal)
      throws Exception
   {
      String[] names = ColorPalettes.getPaletteNames().toArray(new String[0]);
      CategoricalColorModel[] palettes = new CategoricalColorModel[names.length];

      for(int i = 0; i < names.length; i++) {
         CategoricalColorFrame palette = ColorPalettes.getPalette(names[i]);
         CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
         wrapper.setVisualFrame(palette);
         CategoricalColorModel model = visualService.createVisualFrameModel(wrapper);
         model.setName(names[i]);
         palettes[i] = model;
      }

      return palettes;
   }

   private final VisualFrameModelFactoryService visualService;
   private VSChartBindingServiceProxy chartBindingService;
}
