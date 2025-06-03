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
package inetsoft.web.composer.vs.dialog;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.HTMLUtil;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.factory.RemainingPath;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.Principal;

/**
 * Controller for the image preview pane, handles loading/uploading images
 */
@Controller
public class ImagePreviewPaneController {

   public ImagePreviewPaneController(ImagePreviewPaneServiceProxy imagePreviewPaneServiceProxy,
                                     BinaryTransferService binaryTransferService)
   {
      this.imagePreviewPaneServiceProxy = imagePreviewPaneServiceProxy;
      this.binaryTransferService = binaryTransferService;
   }

   /**
    * Get preview image, code from AssetWebHandler.processGetImage and DHTML.processResource
    * @param name       image name
    * @param type       image type
    * @param runtimeId  runtime id
    * @param response   the http response
    */
   @GetMapping(value = "/api/image/composer/vs/image-preview-pane/image/{name}/{type}/**")
   public void getImagePreview(@PathVariable("name") String name,
                               @PathVariable("type") String type,
                               @RemainingPath String runtimeId,
                               Principal principal, HttpServletResponse response)
      throws Exception
   {
      name = Tool.byteDecode(name);
      runtimeId = Tool.byteDecode(runtimeId);
      boolean isTiff = name.toLowerCase().endsWith(".tif") || name.toLowerCase().endsWith(".tiff");

      if(name.toLowerCase().endsWith(".gif")) {
         response.setContentType("image/gif");
      }
      else if(name.toLowerCase().endsWith(".jpg")) {
         response.setContentType("image/jpeg");
      }
      else if(name.toLowerCase().endsWith(".png")) {
         response.setContentType("image/png");
      }
      else if(name.toLowerCase().endsWith(".svg")) {
         response.setContentType("image/svg+xml");
      }
      else if(isTiff) {
         response.setContentType("image/jpeg");
      }

      try {
         if(type.equals(ImageVSAssemblyInfo.SERVER_IMAGE)) {
            final String dir = SreeEnv.getProperty("html.image.directory");

            if(!Tool.isEmptyString(dir)) {
               final String path = FileSystemService.getInstance()
                  .getPath(dir, name).toString();
               HTMLUtil.copyResource(path, response.getOutputStream(), null);
            }
         }
         else if(type.equals(ImageVSAssemblyInfo.SKIN_IMAGE)) {
            InputStream in;

            if(name.equals(ImageVSAssemblyInfo.SKIN_TITLE) ||
               name.equals(ImageVSAssemblyInfo.SKIN_BACKGROUND)) {
               in = HTMLUtil.getPortalResource(name, false, true);
            }
            else {
               in = HTMLUtil.getPortalResource(name, false, false);
            }

            copyResource(in, response.getOutputStream());
         }
         else if(type.equals(ImageVSAssemblyInfo.UPLOADED_IMAGE)) {
            byte[] buf = binaryTransferService.getData(
               imagePreviewPaneServiceProxy
                  .getImagePreviewImageByteBuffer(runtimeId, name, principal));

            if(buf == null || buf.length == 0) {
               Image image = Tool.getImage(this, "/inetsoft/report/images/emptyimage.gif");
               Tool.waitForImage(image);
               buf = VSUtil.getImageBytes(image, 72);

               if(buf == null) {
                  int w = 20, h = 20;
                  Image img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                  Graphics g = img.getGraphics();

                  g.setColor(Color.white);
                  g.fillRect(0, 0, w, h);
                  g.setColor(Color.gray);

                  g.drawRect(0, 0, w - 1, h - 1);
                  g.drawLine(0, 0, w, h);
                  g.drawLine(w, 0, 0, h);

                  g.dispose();
                  buf = VSUtil.getImageBytes(img, 72);
               }
            }

            if(isTiff) {
               buf = VSUtil.transcodeTiffToJpg(buf);
            }

            if(buf != null) {
               response.getOutputStream().write(buf);
            }
         }
      }
      catch(Exception e) {
         SUtil.handleResourceException(e, name);
      }
   }

   @RequestMapping(
      value = "/api/composer/vs/image-preview-pane/upload/**",
      method = RequestMethod.POST)
   @ResponseBody
   public TreeNodeModel uploadImage(@RemainingPath String runtimeId,
                                    @RequestParam("file") MultipartFile mpf,
                                    Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      String key = "/" + ImagePreviewPaneService.class.getName() + "_" + runtimeId + "_" + mpf.getOriginalFilename();
      BinaryTransfer dataTransfer = binaryTransferService.createBinaryTransfer(key);
      binaryTransferService.setData(dataTransfer, mpf.getBytes());
      return imagePreviewPaneServiceProxy.uploadImage(runtimeId, mpf.getOriginalFilename(), dataTransfer, principal);
   }

   @RequestMapping(
      value = "/api/composer/vs/image-preview-pane/delete/{name}/{runtimeId}",
      method = RequestMethod.DELETE
   )
   @ResponseBody
   public boolean deleteImage(@PathVariable("name") String name,
                              @PathVariable("runtimeId") String runtimeId,
                              Principal principal)
      throws Exception
   {
      name = Tool.byteDecode(name);
      runtimeId = Tool.byteDecode(runtimeId);
      return imagePreviewPaneServiceProxy.deleteImage(runtimeId, name, principal);
   }

   /**
    * Copy a resource stream to output. Copy of private method HTMLUtil.copyResource(...)
    */
   private void copyResource(InputStream inp, OutputStream out) {
      try {
         byte[] buf = new byte[40960];
         int cnt;

         while((cnt = inp.read(buf)) > 0) {
            out.write(Tool.convertUserByte(buf), 0, cnt);
         }

         out.flush();
      }
      // @by donio, Tomcat throws a harmless socket exception on flush.
      // Here we swallow the exception so that the user won't be alarmed.
      catch(Exception ignore) {
      }
   }

   private ImagePreviewPaneServiceProxy imagePreviewPaneServiceProxy;
   private BinaryTransferService binaryTransferService;
}
