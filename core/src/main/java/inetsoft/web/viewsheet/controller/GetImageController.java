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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.internal.DimensionD;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.gui.viewsheet.VSFaceUtil;
import inetsoft.report.gui.viewsheet.VSImageable;
import inetsoft.report.gui.viewsheet.cylinder.VSCylinder;
import inetsoft.report.gui.viewsheet.gauge.VSGauge;
import inetsoft.report.gui.viewsheet.slidingscale.VSSlidingScale;
import inetsoft.report.gui.viewsheet.thermometer.VSThermometer;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.HandleAssetExceptions;
import inetsoft.web.viewsheet.InGroupedThread;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.Principal;
import java.util.Optional;

/**
 * This class handles image retrieval from the server.  It was extaccted from
 * inetsoft.analytic.web.composition.AssetWebHandler, less a LOT of code which
 * is no longer being used.
 *
 * @version 12.3, 9/8/2016
 * @author InetSoft Technology
 */
@Controller
public class GetImageController {
   @Autowired
   public GetImageController(
      VSLayoutService vsLayoutService,
      ViewsheetService viewsheetService,
      AssemblyImageService imageService)
   {
      this.vsLayoutService = vsLayoutService;
      this.viewsheetService = viewsheetService;
      this.imageService = imageService;
   }

   /**
    * Method for downloading an assembly image. Similar as getter, except with an added
    * header to force the image to download in the front end.
    *
    * @param vid        The runtime viewsheet id.
    * @param aid        The asset id (name).
    * @param width      The width of the image to be returned.
    * @param height     The height of the image to be returned.
    * @param principal  The user which is logged into the browser.
    */
   @GetMapping(value = "/downloadAssemblyImage/{vid}/{aid}/{width}/{height}/{svg}")
   @InGroupedThread
   public void processDownloadAssemblyImage(
      @PathVariable("vid") String vid,
      @PathVariable("aid") String aid,
      @PathVariable("width") double width,
      @PathVariable("height") double height,
      @PathVariable("svg") boolean svg,
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception
   {
      imageService.downloadAssemblyImage(vid, aid, width, height, width, height, svg,
                                         principal, request, response);
   }

   /**
    * This method is a helper, to allow {Image, Gauge, Line, Rectangle, Oval} to
    * only have to supply the 4 parameters they care about, and forward the call
    * to the 9-parameter version below.
    *
    * @param vid        The runtime viewsheet id.
    * @param aid        The asset id (name).
    * @param width      The width of the image to be returned.
    * @param height     The height of the image to be returned.
    * @param principal  The user which is logged into the browser.
    */
   @GetMapping(value = "/getAssemblyImage/{vid}/{aid}/{width}/{height}")
   @InGroupedThread
   @HandleAssetExceptions
   public void processGetAssemblyImage(
      @PathVariable("vid") String vid,
      @PathVariable("aid") String aid,
      @PathVariable("width") double width,
      @PathVariable("height") double height,
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception
   {
      imageService.processGetAssemblyImage(vid, aid, width, height, 0, 0, null, 0, 0, 0,
                                           principal, request, response, true, false);
   }

   /**
    * Get image given the supplied hash
    *
    * @param vid        The runtime viewsheet id.
    * @param hash       The image hash.
    * @param principal  The user which is logged into the browser.
    */
   @GetMapping(value = "/getImageFromHash/{vid}/{hash}")
   @InGroupedThread
   public void processGetImageFromHash(
      @PathVariable("vid") String vid,
      @PathVariable("hash") String hash,
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception
   {
      imageService.processImageFromHash(vid, hash, principal, request, response);
   }

   /**
    * Gets the image requested, and puts it into the response.  Only called directly
    * by Chart, the other image-using assets all call the 4-parameter version above.
    *
    * @param vid        The runtime viewsheet id.
    * @param aid        The asset id (name).
    * @param width      The width of the image to be returned.
    * @param height     The height of the image to be returned.
    * @param aname      The area name of the chart (optional - chart only).
    * @param index      The index of the image if it is tiled (optional - chart only).
    * @param principal  The user which is logged into the browser.
    */
   @GetMapping(value = "/getAssemblyImage/{vid}/{aid}/{width}/{height}/{maxWidth}/{maxHeight}/{aname}/{index}/{row}/{col}/{genTime}/{svg}")
   @InGroupedThread
   @HandleAssetExceptions
   public void processGetAssemblyImage(
      @PathVariable("vid") String vid,
      @PathVariable("aid") String aid,
      @PathVariable("width") double width,
      @PathVariable("height") double height,
      @PathVariable("maxWidth") double maxWidth,
      @PathVariable("maxHeight") double maxHeight,
      @PathVariable("aname") String aname,
      @PathVariable("index") int index,
      @PathVariable("row") int row,
      @PathVariable("col") int col,
      @PathVariable("genTime") String genTime,
      @PathVariable("svg") boolean svg,
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception
   {
      VSUtil.globalShareVsRunInHostScope(Tool.byteDecode(vid), principal,
         () -> {
            imageService.processGetAssemblyImage(vid, aid, width, height, maxWidth, maxHeight, aname,
                                           index, row, col, principal, request, response, svg,
                                           false);
            return null;
         });
   }

   /**
    * Gets the requested image in layout pane, and puts it into the response.
    *
    * @param layoutName   The layout name
    * @param region       The layout region.
    * @param vid          The runtime viewsheet id.
    * @param assemblyName The assembly name.
    * @param width        The width of the image to be returned.
    * @param height       The height of the image to be returned.
    * @param principal    The user which is logged into the browser.
    */
   @GetMapping(value = "/getLayoutImage/{layoutName}/{region}/{vid}/{assemblyName}/{width}/{height}")
   @InGroupedThread
   public void processGetLayoutImage(
      @PathVariable("layoutName") String layoutName,
      @PathVariable("region") String region,
      @PathVariable("vid") String vid,
      @PathVariable("assemblyName") String assemblyName,
      @PathVariable("width") double width,
      @PathVariable("height") double height,
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception
   {
      ViewsheetService vservice = viewsheetService;
      RuntimeViewsheet rvs = vservice.getViewsheet(Tool.byteDecode(vid), principal);
      RuntimeViewsheet parentRvs = rvs.getOriginalID() == null ? rvs :
         vservice.getViewsheet(rvs.getOriginalID(), principal);
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
         return;
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

               if(buf != null && response != null) {
                  response.setContentType(isSvg ? "image/svg+xml" : "image/png");
                  response.getOutputStream().write(buf);
               }
            }
         }
         else {
            //Assembly Image
            processGetAssemblyImage(vid, assemblyName, width, height, principal, request, response);
         }
      }
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

   /**
    * Get the face image for the specified type and id.
    * (Taken from processGetFaceImage(...) method in AssetWebHandler)
    *
    * @param type       the face type
    * @param id         The face id
    * @param response   The response which will be returned to the browser, into
    *                   which the requested image data is to be returned.
    */
   @GetMapping(value = "/getFaceImage/{type}/{id}")
   @InGroupedThread
   public void processGetFaceImage(
      @PathVariable("type") int type,
      @PathVariable("id") int id,
      HttpServletResponse response) throws Exception
   {
      BufferedImage image;
      VSImageable imageable = null;

      if(type == AbstractSheet.GAUGE_ASSET) {
         imageable = VSGauge.getGauge(id);
         GaugeVSAssemblyInfo info = new GaugeVSAssemblyInfo();
         int align = StyleConstants.H_CENTER | StyleConstants.V_CENTER;
         info.getFormat().getDefaultFormat().setAlignmentValue(align);
         assert imageable != null;
         info.setValue(90);
         imageable.setAssemblyInfo(info);
      }
      else if(type == AbstractSheet.THERMOMETER_ASSET) {
         imageable = VSThermometer.getThermometer(id);
         assert imageable != null;
         imageable.setAssemblyInfo(new ThermometerVSAssemblyInfo());
      }
      else if(type == AbstractSheet.SLIDING_SCALE_ASSET) {
         imageable = VSSlidingScale.getSlidingScale(id);
         assert imageable != null;
         imageable.setAssemblyInfo(new SlidingScaleVSAssemblyInfo());
      }
      else if(type == AbstractSheet.CYLINDER_ASSET) {
         imageable = VSCylinder.getCylinder(id);
         assert imageable != null;
         imageable.setAssemblyInfo(new CylinderVSAssemblyInfo());
      }

      assert imageable != null;
      Dimension dsize = imageable.getDefaultSize();
      Dimension fsize = new Dimension(100, 100);
      Dimension scaledSize = VSFaceUtil.getEqualScaleSize(dsize, fsize);

      imageable.setPixelSize(scaledSize);
      image = imageable.getContentImage();
      byte[] buf = VSUtil.getImageBytes(image, 72);

      if(buf != null && response != null) {
         response.setContentType("image/png");
         response.getOutputStream().write(buf);
      }
   }

   private final VSLayoutService vsLayoutService;
   private final ViewsheetService viewsheetService;
   private final AssemblyImageService imageService;
}
