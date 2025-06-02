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

import inetsoft.report.StyleConstants;
import inetsoft.report.gui.viewsheet.VSFaceUtil;
import inetsoft.report.gui.viewsheet.VSImageable;
import inetsoft.report.gui.viewsheet.cylinder.VSCylinder;
import inetsoft.report.gui.viewsheet.gauge.VSGauge;
import inetsoft.report.gui.viewsheet.slidingscale.VSSlidingScale;
import inetsoft.report.gui.viewsheet.thermometer.VSThermometer;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.viewsheet.HandleAssetExceptions;
import inetsoft.web.viewsheet.InGroupedThread;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.Principal;
import java.util.zip.GZIPOutputStream;

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
   public GetImageController(AssemblyImageServiceProxy imageServiceProxy,
                             GetImageServiceProxy serviceProxy)
   {
      this.imageServiceProxy = imageServiceProxy;
      this.serviceProxy = serviceProxy;
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
      vid = Tool.byteDecode(vid);

      String suffix = svg ? ".svg" : ".png";

      response.setHeader("Content-Disposition", "attachment; filename=" + StringUtils.normalizeSpace(aid) + suffix);

      AssemblyImageService.ImageRenderResult result = imageServiceProxy.downloadAssemblyImage(Tool.byteDecode(vid),
                                                                                              aid, width, height, width,
                                                                                              height, svg, principal);

      processImageRenderResult(result, request, response);
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
      vid = Tool.byteDecode(vid);
      AssemblyImageService.ImageRenderResult result = imageServiceProxy.processGetAssemblyImage(
         vid, aid, width, height, 0, 0, null, 0, 0, 0, principal, true, false);
      processImageRenderResult(result, request, response);
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
         AssemblyImageService.ImageRenderResult result =
            imageServiceProxy.processGetAssemblyImageInHostScope(Tool.byteDecode(vid), aid, width,
                                                                 height, maxWidth, maxHeight, aname,
                                                                 index, row, col, principal, svg,
                                                                 false);
         processImageRenderResult(result, request, response);
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
      Pair<Boolean, BinaryTransfer> layoutResults = serviceProxy.processGetLayoutImage(Tool.byteDecode(vid), layoutName, region,
                                                                                       assemblyName, width, height, principal);

      boolean isSvg = layoutResults.getLeft();
      byte[] buf = layoutResults.getRight().getData();

      if(buf != null && response != null) {
         response.setContentType(isSvg ? "image/svg+xml" : "image/png");
         response.getOutputStream().write(buf);
      }

      else if(buf == null) {
         processGetAssemblyImage(vid, assemblyName, width, height, principal, request, response);
      }
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

   public static void processImageRenderResult(AssemblyImageService.ImageRenderResult result,
                                               HttpServletRequest request, HttpServletResponse response) throws Exception
   {
      if(result != null) {
         boolean isPNG = result.isPng();
         byte[] buf = result.getImageData().getData();

         if(buf != null && response != null) {
            final String encodingTypes = request.getHeader("Accept-Encoding");
            final ServletOutputStream outputStream = response.getOutputStream();

            try {
               if(isPNG) {
                  response.setContentType("image/png");
               }
               else {
                  response.setContentType("image/svg+xml");
               }

               if(encodingTypes != null && encodingTypes.contains("gzip")) {
                  try(final GZIPOutputStream out = new GZIPOutputStream(outputStream)) {
                     response.addHeader("Content-Encoding", "gzip");
                     out.write(buf);
                  }
               }
               else {
                  outputStream.write(buf);
               }
            }
            catch(IOException e) {
               LOG.debug("Broken connection while writing image", e);
            }
         }
      }
   }

   private final AssemblyImageServiceProxy imageServiceProxy;
   private final GetImageServiceProxy serviceProxy;
   private static final Logger LOG = LoggerFactory.getLogger(GetImageController.class);
}
