/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.mxgraph.reader;

import inetsoft.graph.mxgraph.canvas.mxICanvas;
import inetsoft.graph.mxgraph.util.*;
import inetsoft.graph.mxgraph.view.mxCellState;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.*;

/**
 * An abstract converter that renders display XML data onto a canvas.
 */
public abstract class mxGraphViewReader extends DefaultHandler {

   /**
    * Holds the canvas to be used for rendering the graph.
    */
   protected mxICanvas canvas;

   /**
    * Holds the global scale of the graph. This is set just before
    * createCanvas is called.
    */
   protected double scale = 1;

   /**
    * Specifies if labels should be rendered as HTML markup.
    */
   protected boolean htmlLabels = false;

   /**
    * Parses the list of points into an object-oriented representation.
    *
    * @param pts String containing a list of points.
    *
    * @return Returns the points as a list of mxPoints.
    */
   public static List<mxPoint> parsePoints(String pts)
   {
      List<mxPoint> result = new ArrayList<mxPoint>();

      if(pts != null) {
         int len = pts.length();
         String tmp = "";
         String x = null;

         for(int i = 0; i < len; i++) {
            char c = pts.charAt(i);

            if(c == ',' || c == ' ') {
               if(x == null) {
                  x = tmp;
               }
               else {
                  result.add(new mxPoint(Double.parseDouble(x), Double
                     .parseDouble(tmp)));
                  x = null;
               }
               tmp = "";
            }
            else {
               tmp += c;
            }
         }

         result.add(new mxPoint(Double.parseDouble(x), Double
            .parseDouble(tmp)));
      }

      return result;
   }

   /**
    * Returns the htmlLabels switch.
    */
   public boolean isHtmlLabels()
   {
      return htmlLabels;
   }

   /**
    * Sets the htmlLabels switch.
    */
   public void setHtmlLabels(boolean value)
   {
      htmlLabels = value;
   }

   /**
    * Returns the canvas to be used for rendering.
    *
    * @param attrs Specifies the attributes of the new canvas.
    *
    * @return Returns a new canvas.
    */
   public abstract mxICanvas createCanvas(Map<String, Object> attrs);

   /**
    * Returns the canvas that is used for rendering the graph.
    *
    * @return Returns the canvas.
    */
   public mxICanvas getCanvas()
   {
      return canvas;
   }

   /* (non-Javadoc)
    * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
    */
   public void startElement(String uri, String localName, String qName,
                            Attributes atts) throws SAXException
   {
      String tagName = qName.toUpperCase();
      Map<String, Object> attrs = new Hashtable<String, Object>();

      for(int i = 0; i < atts.getLength(); i++) {
         String name = atts.getQName(i);

         // Workaround for possible null name
         if(name == null || name.length() == 0) {
            name = atts.getLocalName(i);
         }

         attrs.put(name, atts.getValue(i));
      }

      parseElement(tagName, attrs);
   }

   /**
    * Parses the given element and paints it onto the canvas.
    *
    * @param tagName Name of the node to be parsed.
    * @param attrs   Attributes of the node to be parsed.
    */
   public void parseElement(String tagName, Map<String, Object> attrs)
   {
      if(canvas == null && tagName.equalsIgnoreCase("graph")) {
         scale = mxUtils.getDouble(attrs, "scale", 1);
         canvas = createCanvas(attrs);

         if(canvas != null) {
            canvas.setScale(scale);
         }
      }
      else if(canvas != null) {
         boolean edge = tagName.equalsIgnoreCase("edge");
         boolean group = tagName.equalsIgnoreCase("group");
         boolean vertex = tagName.equalsIgnoreCase("vertex");

         if((edge && attrs.containsKey("points"))
            || ((vertex || group) && attrs.containsKey("x")
            && attrs.containsKey("y")
            && attrs.containsKey("width") && attrs
            .containsKey("height")))
         {
            mxCellState state = new mxCellState(null, null, attrs);

            String label = parseState(state, edge);
            canvas.drawCell(state);
            canvas.drawLabel(label, state, isHtmlLabels());
         }
      }
   }

   /**
    * Parses the bounds, absolute points and label information from the style
    * of the state into its respective fields and returns the label of the
    * cell.
    */
   public String parseState(mxCellState state, boolean edge)
   {
      Map<String, Object> style = state.getStyle();

      // Parses the bounds
      state.setX(mxUtils.getDouble(style, "x"));
      state.setY(mxUtils.getDouble(style, "y"));
      state.setWidth(mxUtils.getDouble(style, "width"));
      state.setHeight(mxUtils.getDouble(style, "height"));

      // Parses the absolute points list
      List<mxPoint> pts = parsePoints(mxUtils.getString(style, "points"));

      if(pts.size() > 0) {
         state.setAbsolutePoints(pts);
      }

      // Parses the label and label bounds
      String label = mxUtils.getString(style, "label");

      if(label != null && label.length() > 0) {
         mxPoint offset = new mxPoint(mxUtils.getDouble(style, "dx"),
                                      mxUtils.getDouble(style, "dy"));
         mxRectangle vertexBounds = (!edge) ? state : null;
         state.setLabelBounds(mxUtils.getLabelPaintBounds(label, state
                                                             .getStyle(), mxUtils.isTrue(style, "html", false), offset,
                                                          vertexBounds, scale));
      }

      return label;
   }

}
