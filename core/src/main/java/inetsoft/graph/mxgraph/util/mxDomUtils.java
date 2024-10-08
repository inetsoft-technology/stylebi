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
package inetsoft.graph.mxgraph.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Contains various DOM API helper methods for use with mxGraph.
 */
public class mxDomUtils {

   /**
    * Returns a new, empty DOM document.
    *
    * @return Returns a new DOM document.
    */
   public static Document createDocument()
   {
      return mxXmlUtils.getDocumentBuilder().newDocument();
   }

   /**
    * Creates a new SVG document for the given width and height.
    */
   public static Document createSvgDocument(int width, int height)
   {
      Document document = createDocument();
      Element root = document.createElement("svg");

      String w = String.valueOf(width);
      String h = String.valueOf(height);

      root.setAttribute("width", w);
      root.setAttribute("height", h);
      root.setAttribute("viewBox", "0 0 " + w + " " + h);
      root.setAttribute("version", "1.1");
      root.setAttribute("xmlns", mxConstants.NS_SVG);
      root.setAttribute("xmlns:xlink", mxConstants.NS_XLINK);

      document.appendChild(root);

      return document;
   }

   /**
    *
    */
   public static Document createVmlDocument()
   {
      Document document = createDocument();

      Element root = document.createElement("html");
      root.setAttribute("xmlns:v", "urn:schemas-microsoft-com:vml");
      root.setAttribute("xmlns:o", "urn:schemas-microsoft-com:office:office");

      document.appendChild(root);

      Element head = document.createElement("head");

      Element style = document.createElement("style");
      style.setAttribute("type", "text/css");
      style.appendChild(document.createTextNode(
         "<!-- v\\:* {behavior: url(#default#VML);} -->"));

      head.appendChild(style);
      root.appendChild(head);

      Element body = document.createElement("body");
      root.appendChild(body);

      return document;
   }

   /**
    * Returns a document with a HTML node containing a HEAD and BODY node.
    */
   public static Document createHtmlDocument()
   {
      Document document = createDocument();

      Element root = document.createElement("html");

      document.appendChild(root);

      Element head = document.createElement("head");
      root.appendChild(head);

      Element body = document.createElement("body");
      root.appendChild(body);

      return document;
   }
}
