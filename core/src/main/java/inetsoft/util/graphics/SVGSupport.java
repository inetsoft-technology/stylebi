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
package inetsoft.util.graphics;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.io.*;
import java.net.URL;

public interface SVGSupport {
   Document createSVGDocument(URL url) throws IOException;

   Document createSVGDocument(InputStream input) throws IOException;

   Document createSVGDocument(Graphics2D graphics) throws IOException;

   Graphics2D createSVGGraphics();

   Graphics2D getSVGGraphics(String url, Dimension contentSize, boolean isShadow, Color bg,
                             double scale, int borderRadius);

   Element getSVGRootElement(Graphics2D graphics);

   Document getSVGDocument(Graphics2D graphics);

   boolean isSVGGraphics(Graphics graphics);

   void setCanvasSize(Graphics2D graphics, Dimension size);

   default Image getSVGImage(InputStream svgStream) throws Exception {
      return getSVGImage(svgStream, 0F, 0F);
   }

   Image getSVGImage(InputStream svgStream, float width, float height) throws Exception;

   byte[] transcodeSVGImage(Document document) throws Exception;

   void writeSVG(Graphics2D graphics, Writer writer) throws IOException;

   void writeSVG(Graphics2D graphics, Writer writer, boolean useCss) throws IOException;

   void writeSVG(Graphics2D graphics, OutputStream output) throws IOException;

   void printSVG(Graphics g, double x, double y, double width, double height, Document svg);

   void printSVG(Graphics2D g2, PageFormat pg, URL url);

   Dimension getSVGSize(URL url) throws IOException;

   Dimension getSVGSize(InputStream input) throws IOException;

   default Dimension getSVGSize(URL url, int contextWidth, int contextHeight) throws IOException {
      return getSVGSize(url);
   }

   default Dimension getSVGSize(InputStream input, int contextWidth, int contextHeight)
      throws IOException
   {
      return getSVGSize(input);
   }

   void mergeSVGDocument(Graphics2D g, String url, AffineTransform transform);

   SVGTransformer createSVGTransformer(URL url) throws IOException;

   void fixPNG(Document document);

   BufferedImage generateBufferedImage(InputStream input, int width, int height, String theme,
                                       String color);

   static SVGSupport getInstance() {
      try {
         Class<?> clazz = SVGSupport.class.getClassLoader()
            .loadClass("inetsoft.util.graphics.BatikSVGSupport");
         return (SVGSupport) clazz.getConstructor().newInstance();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create parser instance", e);
      }
   }
}
