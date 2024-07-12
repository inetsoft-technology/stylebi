/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
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
package inetsoft.util.graphics;

import inetsoft.uql.viewsheet.internal.SVGImageTranscoder;
import org.apache.batik.anim.dom.*;
import org.apache.batik.bridge.BridgeException;
import org.apache.batik.dom.svg.SVGContext;
import org.apache.batik.ext.awt.image.codec.png.PNGImageWriter;
import org.apache.batik.ext.awt.image.spi.ImageWriterRegistry;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.transcoder.*;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.transcoder.print.PrintTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGSVGElement;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class BatikSVGSupport implements SVGSupport {
   @Override
   public Document createSVGDocument(URL url) throws IOException {
      return SVGUtil.createDocument(url);
   }

   @Override
   public Document createSVGDocument(InputStream input) throws IOException {
      return SVGUtil.createDocument(input);
   }

   @Override
   public Document createSVGDocument(Graphics2D graphics) throws IOException {
      return SVGUtil.createDocument((SVGGraphics2D) graphics);
   }

   @Override
   public Graphics2D createSVGGraphics() {
      return SVGUtil.getSVGDocument();
   }

   @Override
   public Graphics2D getSVGGraphics(String url, Dimension contentSize, boolean isShadow, Color bg,
                                    double scale, int borderRadius)
   {
      return SVGUtil.getSVGGraphics2DByUri(url, contentSize, isShadow, bg, scale, borderRadius);
   }

   public Element getSVGRootElement(Graphics2D graphics) {
      return ((SVGGraphics2D) graphics).getRoot();
   }

   public Document getSVGDocument(Graphics2D graphics) {
      return ((SVGGraphics2D) graphics).getDOMFactory();
   }

   @Override
   public void setCanvasSize(Graphics2D graphics, Dimension size) {
      ((SVGGraphics2D) graphics).setSVGCanvasSize(size);
   }

   @Override
   public boolean isSVGGraphics(Graphics graphics) {
      return graphics instanceof SVGGraphics2D;
   }

   @Override
   public Image getSVGImage(InputStream svgStream, float width, float height) throws Exception {
      Document doc = SVGUtil.createDocument(svgStream);
      SVGImageTranscoder imageTranscoder = new SVGImageTranscoder();

      if(width > 0F && height > 0F) {
         imageTranscoder.addTranscodingHint(SVGImageTranscoder.KEY_WIDTH, width);
         imageTranscoder.addTranscodingHint(SVGImageTranscoder.KEY_HEIGHT, height);
      }

      try {
         imageTranscoder.transcode(new TranscoderInput(doc), null);
         return imageTranscoder.getImage();
      }
      catch(TranscoderException ex) {
         if(ex.getException() instanceof BridgeException) {
            SVGUtil.fixPNG(doc);
            imageTranscoder.transcode(new TranscoderInput(doc), null);
            return imageTranscoder.getImage();
         }

         throw ex;
      }
   }

   @Override
   public byte[] transcodeSVGImage(Document document) throws Exception {
      return SVGUtil.transcodeToSVG(document);
   }

   @Override
   public void writeSVG(Graphics2D graphics, Writer writer) throws IOException {
      ((SVGGraphics2D) graphics).stream(writer);
   }

   @Override
   public void writeSVG(Graphics2D graphics, Writer writer, boolean useCss) throws IOException {
      ((SVGGraphics2D) graphics).stream(writer, useCss);
   }

   @Override
   public void writeSVG(Graphics2D graphics, OutputStream output) throws IOException {
      OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
      // using css is very expensive, set to false
      ((SVGGraphics2D) graphics).stream(writer, false);
   }

   @Override
   public void printSVG(Graphics g, double x, double y, double width, double height, Document svg) {
      Graphics2D g2 = (Graphics2D) g.create();
      PageFormat pg = new PageFormat();

      Paper pp = new Paper();
      pp.setImageableArea(x, y, width, height);
      pg.setPaper(pp);

      PrintTranscoder prm = new PrintTranscoder();

      prm.addTranscodingHint(SVGImageTranscoder.KEY_WIDTH, (float) width);
      prm.addTranscodingHint(SVGImageTranscoder.KEY_HEIGHT, (float) height);
      SVGUtil.fixPNG(svg);
      prm.transcode(new TranscoderInput(svg), null);
      prm.print(g2, pg, 0);

      g2.dispose();
   }

   @Override
   public void printSVG(Graphics2D g2, PageFormat pg, URL url) {
      PrintTranscoder prm = new PrintTranscoder();

      try {
         Document document = SVGUtil.createDocument(url);
         SVGUtil.fixPNG(document);
         prm.transcode(new TranscoderInput(document), null);
      }
      catch(IOException e) {
         LOG.warn("Failed to create SVG document", e);
         prm.transcode(new TranscoderInput(getSafeUrl(url)), null);
      }

      prm.print(g2, pg, 0);
   }

   @Override
   public Dimension getSVGSize(URL url) throws IOException {
      String parser = XMLResourceDescriptor.getXMLParserClassName();
      SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
      Document doc = factory.createDocument(getSafeUrl(url));
      return getSVGSize((SVGOMDocument) doc, -1, -1);
   }

   public Dimension getSVGSize(URL url, int contextWidth, int contextHeight) throws IOException {
      String parser = XMLResourceDescriptor.getXMLParserClassName();
      SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
      Document doc = factory.createDocument(getSafeUrl(url));
      return getSVGSize((SVGOMDocument) doc, contextWidth, contextHeight);
   }

   @Override
   public Dimension getSVGSize(InputStream input) throws IOException {
      String parser = XMLResourceDescriptor.getXMLParserClassName();
      SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
      Document doc = factory.createDocument("http://svg.batik", input);
      return getSVGSize((SVGOMDocument) doc, -1, -1);
   }

   public Dimension getSVGSize(InputStream input, int contextWidth, int contextHeight) throws IOException {
      String parser = XMLResourceDescriptor.getXMLParserClassName();
      SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
      Document doc = factory.createDocument("http://svg.batik", input);
      return getSVGSize((SVGOMDocument) doc, contextWidth, contextHeight);
   }

   private Dimension getSVGSize(SVGOMDocument svgDoc, int contextWidth, int contextHeight) {
      SVGSVGElement root = svgDoc.getRootElement();
      DefaultSVGContext context = new DefaultSVGContext();

      if(contextWidth > 0 && contextHeight > 0) {
         context.contextWidth = contextWidth;
         context.contextHeight = contextHeight;
      }

      svgDoc.setSVGContext(context);

      if(root instanceof SVGOMElement) {
         context = new DefaultSVGContext();

         if(contextWidth > 0 && contextHeight > 0) {
            context.contextWidth = contextWidth;
            context.contextHeight = contextHeight;
         }

         ((SVGOMElement) root).setSVGContext(context);
      }

      int width = 1000;
      int height = 1000;

      try {
         width = (int) root.getWidth().getBaseVal().getValue();
         height = (int) root.getHeight().getBaseVal().getValue();
      }
      catch(Exception e) {
         LOG.warn("No default size is specified in the svg file");
      }

      return new Dimension(width, height);
   }

   @Override
   public void mergeSVGDocument(Graphics2D g, String url, AffineTransform transform) {
      SVGUtil.mergeSvgDocument((SVGGraphics2D) g, url, transform);
   }

   @Override
   public SVGTransformer createSVGTransformer(URL url) throws IOException {
      String parser = XMLResourceDescriptor.getXMLParserClassName();
      SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
      Document doc = factory.createDocument(getSafeUrl(url));
      return new BatikSVGTransformer(doc);
   }

   @Override
   public void fixPNG(Document document) {
      SVGUtil.fixPNG(document);
   }

   @Override
   public BufferedImage generateBufferedImage(InputStream input, int width, int height,
                                              String theme, String color)
   {
      TranscoderInput transcoderInput = new TranscoderInput(input);
      BufferedImageTranscoder t = new BufferedImageTranscoder(theme, color);

      if(width != 0 && height != 0) {
         t.setDimensions(width, height);
      }

      try {
         t.transcode(transcoderInput, null);
      }
      catch(Exception e) {
         e.printStackTrace();
      }

      return t.getImage();
   }

   private String getSafeUrl(URL url) {
      String urlString = url + "";
      int index = urlString.indexOf("zip:");

      // fix weblogic url
      if(index == 0) {
         urlString = "jar:file" + urlString.substring(3);
      }

      // fix websphere url
      if(urlString.startsWith("wsjar:")) {
         urlString = urlString.substring(2);
      }

      return urlString;
   }

   private static final Logger LOG = LoggerFactory.getLogger(BatikSVGSupport.class);

   static {
      ImageWriterRegistry.getInstance().register(new PNGImageWriter());
   }

   private static class DefaultSVGContext implements SVGContext {
      public float getPixelUnitToMillimeter() {
         return 0.352778f;
      }

      public float getPixelToMM() {
         return 0.0352778f;
      }

      public Rectangle2D getBBox() {
         return new Rectangle2D.Double(0, 0, contextWidth, contextHeight);
      }

      public AffineTransform getScreenTransform() {
         return trans;
      }

      public void setScreenTransform(AffineTransform at) {
         this.trans = at;
      }

      public AffineTransform getCTM() {
         return new AffineTransform();
      }

      public AffineTransform getGlobalTransform() {
         return new AffineTransform();
      }

      public float getViewportWidth() {
         return contextWidth;
      }

      public float getViewportHeight() {
         return contextHeight;
      }

      public float getFontSize() {
         return 10;
      }

      private AffineTransform trans = new AffineTransform();
      private int contextWidth = 1000;
      private int contextHeight = 1000;
   }

   private static final class BufferedImageTranscoder extends ImageTranscoder {
      public BufferedImageTranscoder(String theme, String color) {
         String colorTheme = color == null ? theme : color;
         String iconCSS = Objects.requireNonNull(
            getClass().getResource("/inetsoft/gui/css/" + colorTheme + "-icon.css"))
            .toString();
         hints.put(ImageTranscoder.KEY_USER_STYLESHEET_URI, iconCSS);
         // CVE-2022-44729, BATIK-1349, this is safe because only built-in icons from the classpath
         // are ever loaded. Also, because the output is a buffered image, no external information
         // could be exposed.
         hints.put(ImageTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES, Boolean.TRUE);
      }

      /**
       * Creates a new ARGB image with the specified dimension.
       * @param width the image width in pixels
       * @param height the image height in pixels
       */
      @Override
      public BufferedImage createImage(int width, int height) {
         bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
         return bufferedImage;
      }

      /**
       * Writes the specified image to the specified output.
       * @param img the image to write
       * @param output the output where to store the image
       */
      public void writeImage(BufferedImage img, TranscoderOutput output) {
      }

      /**
       * Returns the BufferedImage generated from the SVG document.
       */
      public BufferedImage getImage() {
         return bufferedImage;
      }

      /**
       * Set the dimensions to be used for the image.
       */
      public void setDimensions(int w, int h) {
         hints.put(KEY_WIDTH, (float) w);
         hints.put(KEY_HEIGHT, (float) h);
      }

      BufferedImage bufferedImage;
   }
}
