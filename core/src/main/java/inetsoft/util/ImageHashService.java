/*
 * Copyright (c) 2025, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */

package inetsoft.util;

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

/**
 * Creates a hash for an image and stores the image information so that the image can
 * be later retrieved given the hash
 */
public class ImageHashService {
   public String getImageHash(VSAssembly assembly) {
      VSAssemblyInfo assemblyInfo = assembly.getVSAssemblyInfo();
      Viewsheet vs = assembly.getViewsheet();
      String assemblyName = null;
      String imagePath = null;
      boolean rawBytes = shouldSendRawBytes(assembly);
      int width = -1;
      int height = -1;

      if(assemblyInfo instanceof ImageVSAssemblyInfo) {
         ImageVSAssemblyInfo imageVSAssemblyInfo = (ImageVSAssemblyInfo) assemblyInfo;

         // only include assemblyName when using rawImage (script set image)
         if(imageVSAssemblyInfo.getRawImage() != null) {
            assemblyName = assemblyInfo.getName();
         }

         imagePath = imageVSAssemblyInfo.getImage();
      }

      if(Tool.isEmptyString(imagePath)) {
         return null;
      }

      boolean svg = imagePath.endsWith(".svg");
      boolean presenter = imagePath.startsWith("java:presenter:");

      // only include width and height when absolutely necessary
      if(!rawBytes && (svg || presenter)) {
         Dimension size = assemblyInfo.getLayoutSize();

         if(size == null || size.width == 0 || size.height == 0) {
            size = assemblyInfo.getPixelSize();
         }

         width = size.width;
         height = size.height;
      }

      ImageInfo imageInfo = new ImageInfo(imagePath, width, height, rawBytes, assemblyName);

      if(infoToHash.containsKey(imageInfo)) {
         return infoToHash.get(imageInfo);
      }

      String hash = getImageHash(imageInfo, vs);

      if(hash != null) {
         infoToHash.put(imageInfo, hash);
         hashToInfo.put(hash, imageInfo);
      }

      return hash;
   }

   public void sendImageResponse(String hash, Viewsheet viewsheet, HttpServletRequest request,
                                 HttpServletResponse response)
      throws Exception
   {
      ImageInfo imageInfo = hashToInfo.get(hash);

      if(imageInfo == null) {
         return;
      }

      response.setHeader("Cache-Control", "public, max-age=86400"); // 1 day
      response.setHeader("Pragma", "");

      byte[] buf = getImageBytes(imageInfo, viewsheet);
      boolean svg = imageInfo.path != null && imageInfo.path.endsWith(".svg");

      if(svg) {
         response.setContentType("image/svg+xml");
      }
      else {
         response.setContentType("image/png");
      }

      final String encodingTypes = request.getHeader("Accept-Encoding");
      final ServletOutputStream outputStream = response.getOutputStream();

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

   private String getImageHash(ImageInfo imageInfo, Viewsheet vs) {
      byte[] imageBytes = null;

      try {
         imageBytes = getImageBytes(imageInfo, vs);
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      if(imageBytes != null) {
         try {
            return computeImageMD5Hash(imageBytes);
         }
         catch(Exception e) {
            return null;
         }
      }

      return null;
   }

   private byte[] getImageBytes(ImageInfo imageInfo, Viewsheet vs) throws Exception {
      if(imageInfo == null) {
         return null;
      }

      Image rawImage = null;

      if(imageInfo.assemblyName != null) {
         VSAssembly vsAssembly = vs.getAssembly(imageInfo.assemblyName);

         if(vsAssembly instanceof ImageVSAssembly) {
            rawImage = ((ImageVSAssemblyInfo) vsAssembly.getVSAssemblyInfo()).getRawImage();
         }
      }

      byte[] buf;

      if(imageInfo.rawBytes) {
         buf = VSUtil.getVSImageBytes(rawImage, imageInfo.path, vs, imageInfo.width,
                                      imageInfo.height, null, vsPortalHelper);
      }
      else {
         final int dpi = 72;
         Image image = VSUtil.getVSImage(rawImage, imageInfo.path, vs, imageInfo.width,
                                         imageInfo.height, null, vsPortalHelper);

         if(image == null) {
            image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ((BufferedImage) image).setRGB(0, 0, 0xffffff);
         }

         buf = VSUtil.getImageBytes(image, dpi);
      }

      return buf;
   }

   private String computeImageMD5Hash(byte[] imageBytes) throws Exception {
      // Compute MD5 hash
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(imageBytes);

      // Convert hash bytes to hex string
      StringBuilder hexString = new StringBuilder();

      for(byte b : digest) {
         hexString.append(String.format("%02x", b));
      }

      return hexString.toString();
   }

   private boolean shouldSendRawBytes(Assembly assembly) {
      if(assembly instanceof ImageVSAssembly) {
         ImageVSAssemblyInfo info = (ImageVSAssemblyInfo) assembly.getInfo();
         boolean isGIF = info.isAnimateGIF();
         boolean isSVG = info.getImage() != null && info.getImage().endsWith(".svg");

         if(isGIF) {
            return true;
         }

         if(isSVG) {
            return !info.isScaleImageValue() || info.isMaintainAspectRatioValue();
         }
      }

      return false;
   }

   /**
    * Image info that can be used to get the actual image
    */
   private static class ImageInfo {
      public ImageInfo(String path, int width, int height, boolean rawBytes, String assemblyName) {
         this.path = path;
         this.width = width;
         this.height = height;
         this.rawBytes = rawBytes;
         this.assemblyName = assemblyName;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         ImageInfo imageInfo = (ImageInfo) o;
         return width == imageInfo.width && height == imageInfo.height &&
            rawBytes == imageInfo.rawBytes && Objects.equals(path, imageInfo.path) &&
            Objects.equals(assemblyName, imageInfo.assemblyName);
      }

      @Override
      public int hashCode() {
         return Objects.hash(path, width, height, rawBytes, assemblyName);
      }

      private final String path;
      private final int width;
      private final int height;
      private final boolean rawBytes;
      private final String assemblyName;
   }

   private final ConcurrentHashMap<ImageInfo, String> infoToHash = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<String, ImageInfo> hashToInfo = new ConcurrentHashMap<>();
   private final VSPortalHelper vsPortalHelper = new VSPortalHelper();
}
