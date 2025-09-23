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
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates a hash for an image and stores the image information so that the image can
 * be later retrieved given the hash
 */
public class ImageHashService implements XMLSerializable {
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

   public AssemblyImageService.ImageRenderResult sendImageResponse(
      String hash, Viewsheet viewsheet, String runtimeId,
      BinaryTransferService binaryTransferService) throws Exception
   {
      ImageInfo imageInfo = hashToInfo.get(hash);

      if(imageInfo == null) {
         return null;
      }

      byte[] buf = getImageBytes(imageInfo, viewsheet);
      boolean dynamicImage = imageInfo.shadow || imageInfo.highlight || imageInfo.scale9 != null;
      boolean svg = imageInfo.path != null && imageInfo.path.endsWith(".svg");
      boolean isPNG = !svg || dynamicImage;

      String key = "/" + ImageHashService.class.getName() + "_" + runtimeId + "_" + hash;
      BinaryTransfer imageData = binaryTransferService.createBinaryTransfer(key);
      binaryTransferService.setData(imageData, buf);
      return new AssemblyImageService.ImageRenderResult(
         isPNG, imageData, imageInfo.width, imageInfo.height);
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
   private static class ImageInfo implements XMLSerializable {
      public ImageInfo() {

      }
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

      public void writeXML(PrintWriter writer) {
         writer.print(" <imageInfo ");
         writer.print(" path=\"" + path + "\"");
         writer.print(" width=\"" + width + "\"");
         writer.print(" height=\"" + height + "\"");
         writer.print(" rawBytes=\"" + rawBytes + "\"");
         writer.print(" assemblyName=\"" + assemblyName + "\"");
         writer.print(" shadow=\"" + shadow + "\"");
         writer.print(" highlight=\"" + highlight + "\"");
         writer.print(" scale9=\"" + scale9 + "\"");
         writer.print(" scaled=\"" + scaled + "\"");
         writer.print(" bgColorValue=\"" + bgColorValue + "\"");
         writer.print(" align=\"" + align + "\"");
         writer.print(" embeddedViewsheetName=\"" + embeddedViewsheetName + "\"");
         writer.print(" ></imageInfo>");
      }

      public void parseXML(Element tag) throws Exception {
         path = Tool.getAttribute(tag, "path");
         width = Integer.parseInt(Tool.getAttribute(tag, "width"));
         height = Integer.parseInt(Tool.getAttribute(tag, "height"));
         rawBytes = "true".equals(Tool.getAttribute(tag, "rawBytes"));
         assemblyName = Tool.getAttribute(tag, "assemblyName");
         shadow = "true".equals(Tool.getAttribute(tag, "shadow"));
         highlight = "true".equals(Tool.getAttribute(tag, "highlight"));
         scale9 = Tool.getAttribute(tag, "scale9");
         scaled = "true".equals(Tool.getAttribute(tag, "scaled"));
         bgColorValue = Integer.parseInt(Tool.getAttribute(tag, "bgColorValue"));
         align = Integer.parseInt(Tool.getAttribute(tag, "align"));
         embeddedViewsheetName = Tool.getAttribute(tag, "embeddedViewsheetName");
      }

      private String path;
      private int width;
      private int height;
      private boolean rawBytes;
      private String assemblyName;
      private boolean shadow;
      private boolean highlight;
      private String scale9;
      private boolean scaled;
      private int bgColorValue;
      private int align;
      private String embeddedViewsheetName;
   }

   public void writeXML(PrintWriter writer) {
      writer.println("<imageHashService>");
      writer.println("<infoToHash>");

      for(ImageInfo key : infoToHash.keySet()) {
         writer.println("<entry>");

         writer.print("<key>");
         key.writeXML(writer);
         writer.print("</key>");

         String value = infoToHash.get(key);
         writer.print("<value>");
         writer.print("<![CDATA[" + value + "]]>");
         writer.print("</value>");

         writer.println("</entry>");
      }

      writer.println("</infoToHash>");
      writer.println("<hashToInfo>");

      for(String key : hashToInfo.keySet()) {
         writer.println("<entry>");

         writer.print("<key>");
         writer.print("<![CDATA[" + key + "]]>");
         writer.print("</key>");

         ImageInfo imageInfo = hashToInfo.get(key);
         writer.print("<value>");
         imageInfo.writeXML(writer);
         writer.print("</value>");

         writer.println("</entry>");
      }

      writer.println("</hashToInfo>");
      writer.println("</imageHashService>");
   }

   public void parseXML(Element tag) throws Exception {
      Element toHash = Tool.getChildNodeByTagName(tag, "infoToHash");

      if(toHash != null) {
         Map<ImageInfo, String> map = new ConcurrentHashMap<>();
         NodeList list = toHash.getChildNodes();

         for(int i = 0; i < list.getLength(); i++) {
            if(!(list.item(i) instanceof Element)) {
               continue;
            }

            Element propNode = (Element) list.item(i);
            Element keyNode = Tool.getChildNodeByTagName(propNode, "key");
            Element imageNode = Tool.getChildNodeByTagName(keyNode, "imageInfo");
            ImageInfo image = new ImageInfo();
            image.parseXML(imageNode);
            Element valueNode = Tool.getChildNodeByTagName(propNode, "value");
            String value = Tool.getValue(valueNode);
            map.put(image, value);
         }
      }

      Element toInfo = Tool.getChildNodeByTagName(tag, "hashToInfo");

      if(toInfo != null) {
         Map<String, ImageInfo> map = new ConcurrentHashMap<>();
         NodeList list = toInfo.getChildNodes();

         for(int i = 0; i < list.getLength(); i++) {
            if(!(list.item(i) instanceof Element)) {
               continue;
            }

            Element propNode = (Element) list.item(i);
            Element keyNode = Tool.getChildNodeByTagName(propNode, "key");
            String key = Tool.getValue(keyNode);
            Element valueNode = Tool.getChildNodeByTagName(propNode, "value");
            Element imageNode = Tool.getChildNodeByTagName(valueNode, "imageInfo");
            ImageInfo image = new ImageInfo();
            image.parseXML(imageNode);
            map.put(key, image);
         }
      }
   }

   private ConcurrentHashMap<ImageInfo, String> infoToHash = new ConcurrentHashMap<>();
   private ConcurrentHashMap<String, ImageInfo> hashToInfo = new ConcurrentHashMap<>();
   private final VSPortalHelper vsPortalHelper = new VSPortalHelper();
}
