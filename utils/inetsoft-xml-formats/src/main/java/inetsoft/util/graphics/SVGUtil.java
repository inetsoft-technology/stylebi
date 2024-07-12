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

import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.util.Tool;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.dom.*;
import org.apache.batik.parser.AWTTransformProducer;
import org.apache.batik.parser.TransformListParser;
import org.apache.batik.util.*;
import org.apache.batik.svggen.*;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.svg2svg.SVGTranscoder;
import org.w3c.dom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.text.DecimalFormat;
import java.util.Base64;

/**
 *
 * @version 13.1
 * @author InetSoft Technology Corp
 */
public class SVGUtil {
   /**
    * Create and return an SVG document
    */
   public static SVGGraphics2D getSVGDocument() {
      DOMImplementation impl = GenericDOMImplementation.getDOMImplementation();
      //Document myFactory = impl.createDocument(SVG_NAME_SPACE, "svg", null);
      Document myFactory = new MyDocument(null, impl);
      myFactory.appendChild(myFactory.createElementNS(SVG_NAME_SPACE, "svg"));
      SVGGeneratorContext ctx = SVGGeneratorContext.createDefault(myFactory);
      ctx.setExtensionHandler(new GradientExtensionHandler());

      return new SVGGraphics2D(ctx, true);
   }

   /**
    * Create and return an SVG document
    */
   public static SVGGraphics2D getSVGGraphics2DByUri(String url, Dimension contentSize,
                                                     boolean isShadow, Color bg, double scale,
                                                     int borderRadius)
   {
      try {
         SVGGraphics2D g = getSVGDocument();
         Element svgElement = g.getDOMFactory().getDocumentElement();
         svgElement.setAttribute("height", contentSize.getHeight() + "");
         svgElement.setAttribute("width", contentSize.getWidth() + "");
         Element gElement = g.getDOMFactory().createElementNS(SVG_NAME_SPACE, "g");

         AffineTransform trans = AffineTransform.getScaleInstance(scale, scale);
         mergeSvgDocument(g, url, trans);

         if(bg != null) {
            ExportUtil.drawBackground(g, new Point(0, 0), contentSize, bg, borderRadius);
         }

         if(isShadow) {
            SVGUtil.mergerShadowToSvg(g, gElement, contentSize);
         }

         // Strings and lines will be drawn inside the g tag
         if(svgElement.getTagName().equals("svg")) {
            svgElement.appendChild(gElement);
            g.setTopLevelGroup(gElement);
         }

         return g;
      }
      catch(Exception ex) {
         throw new RuntimeException("Unable to find or load image: " + url, ex);
      }
   }

   /**
    * Create shadow nodes in <g> element tag.
    */
   public static Element mergerShadowToSvg(SVGGraphics2D g, Element gElement, Dimension size) {
      Document document = g.getDOMFactory();
      Element svgElement = g.getDOMFactory().getDocumentElement();

      // set svg's viewBox attribute
      String viewBoxValue = svgElement.getAttribute("viewBox");
      String[] viewValues = viewBoxValue.split(" ");

      if(viewValues.length != 4) {
         viewValues = new String[]{"0", "0", size.width + "", size.height + ""};
      }

      String newViewBoxValue = viewValues[0] + " " + viewValues[1] + " " +
         (Integer.parseInt(viewValues[2]) + 10) + " " + (Integer.parseInt(viewValues[3]) + 13);
      svgElement.setAttribute("viewBox", newViewBoxValue);
      boolean filterId = false;

      if(svgElement.getElementsByTagName("circle").getLength() > 0) {
         ((Element) svgElement.getElementsByTagName("circle").item(0))
                              .setAttribute("filter", "url(#shadow_xxx_shadow)");
         filterId = true;
      }

      if(svgElement.getElementsByTagName("path").getLength() > 0 && !filterId) {
         ((Element) svgElement.getElementsByTagName("path").item(0))
                              .setAttribute("filter", "url(#shadow_xxx_shadow)");
         filterId = true;
      }

      Element shadowElement = document.createElementNS(SVG_NAME_SPACE, "defs");
      Element filter = document.createElementNS(SVG_NAME_SPACE, "filter");
      filter.setAttribute("id", "shadow_xxx_shadow");
      filter.setAttribute("width", "200%");
      filter.setAttribute("height", "200%");
      Element feGaussianBlur = document.createElementNS(SVG_NAME_SPACE, "feGaussianBlur");
      feGaussianBlur.setAttribute("in", "SourceAlpha");
      feGaussianBlur.setAttribute("stdDeviation", "3");
      Element feOffset = document.createElementNS(SVG_NAME_SPACE, "feOffset");
      feOffset.setAttribute("dx", "3");
      feOffset.setAttribute("dy", "6");
      Element feMerge = document.createElementNS(SVG_NAME_SPACE, "feMerge");
      Element feMergeNode = document.createElementNS(SVG_NAME_SPACE, "feMergeNode");
      Element feMergeNode2 = document.createElementNS(SVG_NAME_SPACE, "feMergeNode");
      feMergeNode2.setAttribute("in", "SourceGraphic");
      feMerge.appendChild(feMergeNode);
      feMerge.appendChild(feMergeNode2);
      filter.appendChild(feGaussianBlur);
      filter.appendChild(feOffset);
      filter.appendChild(feMerge);
      shadowElement.appendChild(filter);
      gElement.appendChild(shadowElement);

      return gElement;
   }

   /**
    * Add svg pointed t oby path into the g2d document.
    */
   public static void mergeSvgDocument(SVGGraphics2D g2d, String path, AffineTransform trans) {
      try {
         String parser = XMLResourceDescriptor.getXMLParserClassName();
         SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
         Document doc = factory.createDocument(path);
         // path's doc node
         int num = doc.getElementsByTagName("svg").getLength();
         NodeList childNodes = doc.getElementsByTagName("svg").item(num - 1).getChildNodes();

         for(int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);

            // filter empty node
            if(node instanceof Element) {
               // import another doc's node to this g2d's doc.
               node = g2d.getDOMFactory().importNode(node, true);
               g2d.getDOMFactory().getElementsByTagName("svg").item(0).appendChild(node);

               if(trans != null) {
                  transformElement((Element) node, trans);
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to draw svg: " + path);
      }
   }

   /**
    * Transcode one doc to byte[] by SVGTranscoder.
    **/
   public static byte[] transcodeToSVG(Document doc) throws TranscoderException {
      try {
         //Determine output type:
         SVGTranscoder t = new SVGTranscoder();
         //Set transcoder input/output
         TranscoderInput input = new TranscoderInput(doc);
         ByteArrayOutputStream bytestream = new ByteArrayOutputStream();
         OutputStreamWriter ostream = new OutputStreamWriter(bytestream, StandardCharsets.UTF_8);
         TranscoderOutput output = new TranscoderOutput(ostream);
         //Perform transcoding
         t.transcode(input, output);
         ostream.flush();
         return bytestream.toByteArray();
      }
      catch (IOException e) {
         // TODO Auto-generated catch block
         LOG.warn("Failed to transcode svg.");
      }

      return null;
   }

   // apply transformation to the element (attach to transform attribute).
   public static void transformElement(final Element element, final AffineTransform transform) {
      final AffineTransform oldTransform = getElementTransform(element);
      transform.concatenate(oldTransform);
      element.setAttributeNS(null, SVGConstants.SVG_TRANSFORM_ATTRIBUTE,
                             affineTransformToString(transform));
   }

   public static AffineTransform getElementTransform(final Element element) {
      final String oldTransformStr = element.getAttributeNS(null,
                                                            SVGConstants.SVG_TRANSFORM_ATTRIBUTE);
      final TransformListParser tListParser = new TransformListParser();
      final AWTTransformProducer tProducer = new AWTTransformProducer();

      tListParser.setTransformListHandler(tProducer);
      tListParser.parse(oldTransformStr);
      return tProducer.getAffineTransform();
   }

   private static String affineTransformToString(final AffineTransform at) {
      double[] matrix = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
      at.getMatrix(matrix);
      return matrixArrayToString(matrix);
   }

   private static String matrixArrayToString(double[] vals) {
      return "matrix(" + vals[0] + " " +
         vals[1] + " " +
         vals[2] + " " +
         vals[3] + " " +
         vals[4] + " " +
         vals[5] + ") ";
   }

   public static Document createDocument(URL url) throws IOException {
      return createDocument(url.openStream());
   }

   public static Document createDocument(SVGGraphics2D graphics) throws IOException {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      try(OutputStreamWriter writer = new OutputStreamWriter(buffer, StandardCharsets.UTF_8)) {
         graphics.stream(writer);
      }

      return createDocument(new ByteArrayInputStream(buffer.toByteArray()));
   }

   public static Document createDocument(InputStream svgStream) throws IOException {
      String parser = XMLResourceDescriptor.getXMLParserClassName();
      SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
      return factory.createDocument("file://", svgStream);
   }

   // batik has trouble with some png file, convert to jpeg to work around it
   public static void fixPNG(Document doc) {
      try {
         NodeList images = doc.getElementsByTagName("image");

         for(int i = 0; i < images.getLength(); i++) {
            Element image = (Element) images.item(i);
            String xlink = image.getAttributeNS("http://www.w3.org/1999/xlink", "href");
            String prefix = "data:image/png;base64,";

            if(xlink == null) {
               continue;
            }

            if(xlink.startsWith(prefix)) {
               byte[] buf = Base64.getDecoder()
                  .decode(xlink.substring(prefix.length()).replace(" ", ""));
               BufferedImage img = fixColorSpace(ImageIO.read(new ByteArrayInputStream(buf)));
               ByteArrayOutputStream output = new ByteArrayOutputStream();
               ImageIO.write(img, "jpeg", output);
               String encoded = Base64.getEncoder().encodeToString(output.toByteArray());
               String prefix2 = "data:image/jpeg;base64,";
               image.setAttributeNS("http://www.w3.org/1999/xlink", "href", prefix2 + encoded);
            }
            else if(!xlink.startsWith("data:image")) {
               // user uploaded svg with file protocol shouldn't be allowed so just remove
               // the image when that's the case
               ParsedURLData parsedURLData = ParsedURL.parseURL(xlink);

               if("file".equalsIgnoreCase(parsedURLData.protocol)) {
                  Node parentNode = image.getParentNode();

                  if(parentNode != null) {
                     parentNode.removeChild(image);
                  }
               }
            }
         }
      }
      catch(IOException e) {
         LOG.warn("Failed to convert embedded PNG to JPEG", e);
      }
   }

   /**
    * OpenJDK no longer supports writing a buffered image with an alpha channel as JPEG, so we need
    * to change the color space.
    */
   private static BufferedImage fixColorSpace(BufferedImage in) {
      if(in.getType() == BufferedImage.TYPE_INT_RGB) {
         return in;
      }

      Tool.waitForImage(in);
      int width = in.getWidth();
      int height = in.getHeight();
      BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      out.getGraphics().drawImage(in, 0, 0, width, height, Color.WHITE, null);
      Tool.waitForImage(out);
      return out;
   }

   // copied from SVGGeneratorContext.createDefault
   // used to insert MyGeneratorContext
   public static SVGGeneratorContext createDefault(Document domFactory) {
      SVGGeneratorContext ctx = new MyGeneratorContext(domFactory);
      ctx.setIDGenerator(new SVGIDGenerator());
      ctx.setExtensionHandler(new DefaultExtensionHandler());
      ctx.setImageHandler(new ImageHandlerBase64Encoder());
      ctx.setStyleHandler(new DefaultStyleHandler());
      ctx.setComment("Generated by the Batik Graphics2D SVG Generator");
      ctx.setErrorHandler(new DefaultErrorHandler());
      return ctx;
   }

   // optimization
   private static class MyGeneratorContext extends SVGGeneratorContext {
      protected MyGeneratorContext(Document domFactory) {
         super(domFactory);
         // batik uses static (shared) format, which causes different threads
         // blocking each other. create one per context to avoid locking
         decimalFormat = new DecimalFormat("#.###");
      }
   }

   // optimization, batik calls str.intern() on all strings when adding to element,
   // causing severe performance problem. the implementation copies from batik code
   // but removes all intern() calls
   private static class MyDocument extends GenericDocument {
      public MyDocument(DocumentType dt, DOMImplementation impl) {
         super(dt, impl);
      }

      public Element createElement(String tagName) throws DOMException {
         return new GenericElement(tagName, this);
      }

      public Attr createAttribute(String name) throws DOMException {
         return new GenericAttr(name, this);
      }

      public Element createElementNS(String namespaceURI, String qualifiedName)
         throws DOMException
      {
         if(namespaceURI != null && namespaceURI.length() == 0) {
            namespaceURI = null;
         }

         return (Element) (namespaceURI == null ? new GenericElement(qualifiedName, this)
            : new GenericElementNS(namespaceURI, qualifiedName, this));
      }

      public Attr createAttributeNS(String namespaceURI, String qualifiedName) throws DOMException {
         if(namespaceURI != null && namespaceURI.length() == 0) {
            namespaceURI = null;
         }

         return namespaceURI == null ? new GenericAttr(qualifiedName, this)
            : new GenericAttrNS(namespaceURI, qualifiedName, this);
      }
   }

   public static final String SVG_NAME_SPACE = "http://www.w3.org/2000/svg";
   private static final Logger LOG = LoggerFactory.getLogger(SVGUtil.class);
}
