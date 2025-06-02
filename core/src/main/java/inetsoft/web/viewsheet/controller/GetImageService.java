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

package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.graph.internal.DimensionD;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.Principal;
import java.util.Optional;

@Service
@ClusterProxy
public class GetImageService {

   public GetImageService(
      VSLayoutService vsLayoutService,
      ViewsheetService viewsheetService,
      AssemblyImageService imageService)
   {
      this.vsLayoutService = vsLayoutService;
      this.viewsheetService = viewsheetService;
      this.imageService = imageService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Pair<Boolean, BinaryTransfer> processGetLayoutImage(@ClusterProxyKey String runtimeId, String layoutName,
                                                              String region, String assemblyName, double width, double height,
                                                              Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      RuntimeViewsheet parentRvs = rvs.getOriginalID() == null ? rvs :
         viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      Viewsheet vs = parentRvs.getViewsheet();
      AbstractLayout layout;
      LayoutInfo layoutInfo = vs.getLayoutInfo();
      assemblyName = Tool.byteDecode(assemblyName);
      String vslayoutName = layoutName == null ? null : Tool.byteDecode(layoutName);

      if(Catalog.getCatalog().getString("Print Layout").equals(vslayoutName)) {
         layout = layoutInfo.getPrintLayout();
      }
      else {
         layout = layoutInfo.getViewsheetLayouts()
            .stream()
            .filter(l -> l.getName().equals(vslayoutName))
            .findFirst()
            .orElse(null);
      }

      int layoutRegion;

      switch(region) {
      case "HEADER":
         layoutRegion = VSLayoutService.HEADER;
         break;
      case "CONTENT":
         layoutRegion = VSLayoutService.CONTENT;
         break;
      default:
         layoutRegion = VSLayoutService.FOOTER;
      }

      VSAssembly vsassembly = vs.getAssembly(assemblyName);

      if(layoutRegion == VSLayoutService.CONTENT && vsassembly == null) {
         return null;
      }

      VSAssembly container = getTopContainer(vsassembly);
      Optional<VSAssemblyLayout> assemblyLayout = vsLayoutService.findAssemblyLayout(
         layout, container == null ? assemblyName : container.getAbsoluteName(), layoutRegion);

      if(assemblyLayout.isPresent()) {
         if(assemblyLayout.get() instanceof VSEditableAssemblyLayout) {
            //Header or Footer toolbox Image
            Optional<ImageVSAssemblyInfo> imageVSAssemblyInfo = assemblyLayout
               .map(l -> (ImageVSAssemblyInfo) ((VSEditableAssemblyLayout) l).getInfo());

            if(imageVSAssemblyInfo.isPresent()) {
               ImageVSAssemblyInfo imageAssemblyInfo = imageVSAssemblyInfo.get();
               ImageVSAssembly assembly = new ImageVSAssembly(vs, assemblyName);
               assembly.setVSAssemblyInfo(imageAssemblyInfo);
               Dimension pixelSize = imageAssemblyInfo.getPixelSize();
               DimensionD scale = new DimensionD(width / pixelSize.width,
                                                 height / pixelSize.height);
               assembly.setScalingRatio(scale);
               byte[] buf;
               String path = assembly.getImage();
               boolean isSvg = path.toLowerCase().endsWith(".svg");

               if(((ImageVSAssemblyInfo) assembly.getInfo()).isAnimateGIF() || isSvg) {
                  buf = VSUtil.getVSImageBytes(
                     null, path, vs, -1, -1, null,
                     new VSPortalHelper());
               }
               else {
                  BufferedImage image = imageService.getAssemblyImage(assembly);
                  buf = VSUtil.getImageBytes(image, 72);
               }

               String key = "/" + GetImageService.class.getName() + "_" + runtimeId + "_" + assemblyName;
               BinaryTransfer imageData = new BinaryTransfer(key);
               imageData.setData(buf);

               return new ImmutablePair<>(isSvg, imageData);
            }
         }
         else {
            //Assembly Image
            return new ImmutablePair<>(false, null);
         }
      }

      return null;
   }

   /**
    * Get the top container of the target assembly.
    */
   private VSAssembly getTopContainer(VSAssembly assembly) {
      if(assembly == null) {
         return null;
      }

      VSAssembly topContainer = null;
      VSAssembly container = assembly.getContainer();

      while(container != null) {
         topContainer = container;
         container = container.getContainer();
      }

      return topContainer;
   }

   private final ViewsheetService viewsheetService;
   private final AssemblyImageService imageService;
   private final VSLayoutService vsLayoutService;
}
