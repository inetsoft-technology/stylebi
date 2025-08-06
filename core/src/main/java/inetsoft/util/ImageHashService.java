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
import inetsoft.graph.internal.DimensionD;
import inetsoft.report.StyleConstants;
import inetsoft.report.gui.viewsheet.VSImage;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.*;
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
      boolean shadow = isShadow(assemblyInfo);
      boolean highlight = hasHighlight(assemblyInfo);
      String scale9 = getScale9(assemblyInfo);
      boolean dynamicImage = shadow || highlight || scale9 != null;
      boolean rawBytes = !dynamicImage && shouldSendRawBytes(assembly);
      boolean scaled = false;
      int width = -1;
      int height = -1;
      int bgColorValue = 0;
      int align = StyleConstants.H_LEFT | StyleConstants.V_TOP;

      if(assemblyInfo instanceof ImageVSAssemblyInfo) {
         ImageVSAssemblyInfo imageVSAssemblyInfo = (ImageVSAssemblyInfo) assemblyInfo;

         // only include assemblyName when using rawImage (script set image)
         // or when image needs to be dynamically generated
         if(imageVSAssemblyInfo.getRawImage() != null || dynamicImage) {
            assemblyName = assemblyInfo.getName();
         }

         imagePath = imageVSAssemblyInfo.getImage();

         // only set the following for dynamic image as otherwise these properties get applied
         // on the client
         if(dynamicImage) {
            scaled = imageVSAssemblyInfo.isScaleImage();

            VSCompositeFormat format = imageVSAssemblyInfo.getFormat();

            if(format != null) {
               if(format.getBackground() != null) {
                  bgColorValue = format.getBackground().getRGB();
               }

               align = format.getAlignment();
            }
         }
      }

      if(Tool.isEmptyString(imagePath)) {
         return null;
      }

      boolean svg = imagePath.endsWith(".svg");
      boolean presenter = imagePath.startsWith("java:presenter:");

      // only include width and height when absolutely necessary
      if(!rawBytes && (svg || presenter || dynamicImage)) {
         Dimension size = assemblyInfo.getLayoutSize();

         if(size == null || size.width == 0 || size.height == 0) {
            size = assemblyInfo.getPixelSize();
         }

         width = size.width;
         height = size.height;
      }

      String embeddedViewsheetName = vs.isEmbedded() ? vs.getAbsoluteName() : null;
      ImageInfo imageInfo = new ImageInfo(imagePath, width, height, rawBytes, assemblyName,
                                          shadow, highlight, scale9, scaled, bgColorValue, align,
                                          embeddedViewsheetName);

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
      boolean dynamicImage = imageInfo.shadow || imageInfo.highlight || imageInfo.scale9 != null;
      boolean svg = imageInfo.path != null && imageInfo.path.endsWith(".svg");

      if(svg && !dynamicImage) {
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
      boolean dynamicImage = imageInfo.shadow || imageInfo.highlight || imageInfo.scale9 != null;
      boolean svg = imageInfo.path != null && imageInfo.path.endsWith(".svg");
      VSAssemblyInfo info = null;

      if(imageInfo.assemblyName != null) {
         VSAssembly vsAssembly = vs.getAssembly(imageInfo.assemblyName);

         if(vsAssembly instanceof ImageVSAssembly) {
            info = vsAssembly.getVSAssemblyInfo();
            rawImage = ((ImageVSAssemblyInfo) info).getRawImage();
         }
      }

      if(imageInfo.embeddedViewsheetName != null) {
         VSAssembly embeddedVS = vs.getAssembly(imageInfo.embeddedViewsheetName);

         if(embeddedVS instanceof Viewsheet) {
            vs = (Viewsheet) embeddedVS;
         }
      }

      byte[] buf;

      if(imageInfo.rawBytes) {
         buf = VSUtil.getVSImageBytes(rawImage, imageInfo.path, vs, imageInfo.width,
                                      imageInfo.height, null, vsPortalHelper);
      }
      else {
         final int dpi = 72;
         Image image = null;

         if(svg && dynamicImage && imageInfo.scale9 == null && !imageInfo.scaled) {
            int maxWidth = imageInfo.width;
            int maxHeight = imageInfo.height;

            // account for shadow
            if(imageInfo.shadow) {
               maxWidth -= 6;
               maxHeight -= 6;
            }

            // get the svg image with preferred or max size
            image = VSUtil.getVSImage(rawImage, imageInfo.path, vs, -1,
                                      -1, maxWidth, maxHeight,
                                      null, vsPortalHelper);
         }

         image = image != null ? image : VSUtil.getVSImage(rawImage, imageInfo.path, vs,
                                                           imageInfo.width, imageInfo.height,
                                                           null, vsPortalHelper);

         if(image == null) {
            image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ((BufferedImage) image).setRGB(0, 0, 0xffffff);
         }

         if(dynamicImage) {
            if(info instanceof ImageVSAssemblyInfo) {
               VSImage vsImage = new VSImage(vs);
               vsImage.setAssemblyInfo(info);
               vsImage.setRawImage(image);

               // calculate the scaling ratio for viewsheet scaling
               Dimension pixelSize = info.getPixelSize();
               DimensionD scale = new DimensionD((double) imageInfo.width / pixelSize.width,
                                                 (double) imageInfo.height / pixelSize.height);
               ((ImageVSAssemblyInfo) info).setScalingRatio(scale);

               // when image is not scaled then generate the image with the original size so it
               // can be properly aligned on the client
               if(!imageInfo.scaled) {
                  vsImage.setPixelSize(new Dimension(image.getWidth(null),
                                                     image.getHeight(null)));
               }

               image = vsImage.getImage(false);
            }
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

   private boolean isShadow(VSAssemblyInfo assemblyInfo) {
      if(assemblyInfo instanceof ImageVSAssemblyInfo) {
         ImageVSAssemblyInfo info = (ImageVSAssemblyInfo) assemblyInfo;
         return info.isShadow();
      }

      return false;
   }

   private boolean hasHighlight(VSAssemblyInfo assemblyInfo) {
      if(assemblyInfo instanceof ImageVSAssemblyInfo) {
         ImageVSAssemblyInfo info = (ImageVSAssemblyInfo) assemblyInfo;
         Color highlightC = info.getHighlightForeground();
         VSCompositeFormat fmt = info.getFormat();
         highlightC = highlightC == null ? fmt.getForeground() : highlightC;
         highlightC = Color.BLACK.equals(highlightC) ? null : highlightC;
         return highlightC != null;
      }

      return false;
   }

   private String getScale9(VSAssemblyInfo assemblyInfo) {
      if(assemblyInfo instanceof ImageVSAssemblyInfo) {
         ImageVSAssemblyInfo info = (ImageVSAssemblyInfo) assemblyInfo;

         if(info.isScaleImage() && !info.isMaintainAspectRatio() && info.getScale9() != null) {
            Insets insets = info.getScale9();
            return insets.top + "," + insets.left + "," + insets.bottom + "," + insets.right;
         }
      }

      return null;
   }

   /**
    * Image info that can be used to get the actual image
    */
   private static class ImageInfo {
      public ImageInfo(String path, int width, int height, boolean rawBytes, String assemblyName,
                       boolean shadow, boolean highlight, String scale9,
                       boolean scaled, int bgColorValue, int align, String embeddedViewsheetName)
      {
         this.path = path;
         this.width = width;
         this.height = height;
         this.rawBytes = rawBytes;
         this.assemblyName = assemblyName;
         this.shadow = shadow;
         this.highlight = highlight;
         this.scale9 = scale9;
         this.scaled = scaled;
         this.bgColorValue = bgColorValue;
         this.align = align;
         this.embeddedViewsheetName = embeddedViewsheetName;
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
            rawBytes == imageInfo.rawBytes && shadow == imageInfo.shadow &&
            highlight == imageInfo.highlight && scaled == imageInfo.scaled &&
            bgColorValue == imageInfo.bgColorValue && align == imageInfo.align &&
            Objects.equals(path, imageInfo.path) &&
            Objects.equals(assemblyName, imageInfo.assemblyName) &&
            Objects.equals(scale9, imageInfo.scale9) &&
            Objects.equals(embeddedViewsheetName, imageInfo.embeddedViewsheetName);
      }

      @Override
      public int hashCode() {
         return Objects.hash(path, width, height, rawBytes, assemblyName, shadow, highlight, scale9,
                             scaled, bgColorValue, align, embeddedViewsheetName);
      }

      private final String path;
      private final int width;
      private final int height;
      private final boolean rawBytes;
      private final String assemblyName;
      private final boolean shadow;
      private final boolean highlight;
      private final String scale9;
      private final boolean scaled;
      private final int bgColorValue;
      private final int align;
      private final String embeddedViewsheetName;
   }

   private final ConcurrentHashMap<ImageInfo, String> infoToHash = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<String, ImageInfo> hashToInfo = new ConcurrentHashMap<>();
   private final VSPortalHelper vsPortalHelper = new VSPortalHelper();
}
