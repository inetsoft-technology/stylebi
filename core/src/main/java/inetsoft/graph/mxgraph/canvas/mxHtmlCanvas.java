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
package inetsoft.graph.mxgraph.canvas;

import inetsoft.graph.mxgraph.util.*;
import inetsoft.graph.mxgraph.view.mxCellState;
import org.w3c.dom.*;

import java.util.*;

/**
 * An implementation of a canvas that uses HTML for painting.
 */
public class mxHtmlCanvas extends mxBasicCanvas {

   /**
    * Holds the HTML document that represents the canvas.
    */
   protected Document document;

   /**
    * Constructs a new HTML canvas for the specified dimension and scale.
    */
   public mxHtmlCanvas()
   {
      this(null);
   }

   /**
    * Constructs a new HTML canvas for the specified bounds, scale and
    * background color.
    */
   public mxHtmlCanvas(Document document)
   {
      setDocument(document);
   }

   /**
    *
    */
   public void appendHtmlElement(Element node)
   {
      if(document != null) {
         Node body = document.getDocumentElement().getFirstChild()
            .getNextSibling();

         if(body != null) {
            body.appendChild(node);
         }
      }
   }

   /**
    * Returns a reference to the document that represents the canvas.
    *
    * @return Returns the document.
    */
   public Document getDocument()
   {
      return document;
   }

   /**
    *
    */
   public void setDocument(Document document)
   {
      this.document = document;
   }

   /*
    * (non-Javadoc)
    * @see inetsoft.graph.mxgraph.canvas.mxICanvas#drawCell()
    */
   public Object drawCell(mxCellState state)
   {
      Map<String, Object> style = state.getStyle();

      if(state.getAbsolutePointCount() > 1) {
         List<mxPoint> pts = state.getAbsolutePoints();

         // Transpose all points by cloning into a new array
         pts = mxUtils.translatePoints(pts, translate.getX(), translate.getY());
         drawLine(pts, style);
      }
      else {
         int x = (int) (state.getX() + translate.getX());
         int y = (int) (state.getY() + translate.getY());
         int w = (int) state.getWidth();
         int h = (int) state.getHeight();

         if(!mxUtils.getString(style, mxConstants.STYLE_SHAPE, "").equals(
            mxConstants.SHAPE_SWIMLANE))
         {
            drawShape(x, y, w, h, style);
         }
         else {
            int start = (int) Math.round(mxUtils.getInt(style,
                                                        mxConstants.STYLE_STARTSIZE,
                                                        mxConstants.DEFAULT_STARTSIZE)
                                            * scale);

            // Removes some styles to draw the content area
            Map<String, Object> cloned = new Hashtable<String, Object>(
               style);
            cloned.remove(mxConstants.STYLE_FILLCOLOR);
            cloned.remove(mxConstants.STYLE_ROUNDED);

            if(mxUtils.isTrue(style, mxConstants.STYLE_HORIZONTAL, true)) {
               drawShape(x, y, w, start, style);
               drawShape(x, y + start, w, h - start, cloned);
            }
            else {
               drawShape(x, y, start, h, style);
               drawShape(x + start, y, w - start, h, cloned);
            }
         }
      }

      return null;
   }

   /*
    * (non-Javadoc)
    * @see inetsoft.graph.mxgraph.canvas.mxICanvas#drawLabel()
    */
   public Object drawLabel(String label, mxCellState state, boolean html)
   {
      mxRectangle bounds = state.getLabelBounds();

      if(drawLabels && bounds != null) {
         int x = (int) (bounds.getX() + translate.getY());
         int y = (int) (bounds.getY() + translate.getY());
         int w = (int) bounds.getWidth();
         int h = (int) bounds.getHeight();
         Map<String, Object> style = state.getStyle();

         return drawText(label, x, y, w, h, style);
      }

      return null;
   }

   /**
    * Draws the shape specified with the STYLE_SHAPE key in the given style.
    *
    * @param x     X-coordinate of the shape.
    * @param y     Y-coordinate of the shape.
    * @param w     Width of the shape.
    * @param h     Height of the shape.
    * @param style Style of the the shape.
    */
   public Element drawShape(int x, int y, int w, int h,
                            Map<String, Object> style)
   {
      String fillColor = mxUtils
         .getString(style, mxConstants.STYLE_FILLCOLOR);
      String strokeColor = mxUtils.getString(style,
                                             mxConstants.STYLE_STROKECOLOR);
      float strokeWidth = (float) (mxUtils.getFloat(style,
                                                    mxConstants.STYLE_STROKEWIDTH, 1) * scale);

      // Draws the shape
      String shape = mxUtils.getString(style, mxConstants.STYLE_SHAPE);

      Element elem = document.createElement("div");

      if(shape.equals(mxConstants.SHAPE_LINE)) {
         String direction = mxUtils.getString(style,
                                              mxConstants.STYLE_DIRECTION, mxConstants.DIRECTION_EAST);

         if(direction.equals(mxConstants.DIRECTION_EAST)
            || direction.equals(mxConstants.DIRECTION_WEST))
         {
            y = Math.round(y + h / 2);
            h = 1;
         }
         else {
            x = Math.round(y + w / 2);
            w = 1;
         }
      }

      if(mxUtils.isTrue(style, mxConstants.STYLE_SHADOW, false)
         && fillColor != null)
      {
         Element shadow = (Element) elem.cloneNode(true);

         String s = "overflow:hidden;position:absolute;" + "left:"
            + (x + mxConstants.SHADOW_OFFSETX) + "px;"
            + "top:" + (y + mxConstants.SHADOW_OFFSETY)
            + "px;" + "width:" + w + "px;" + "height:"
            + h + "px;background:"
            + mxConstants.W3C_SHADOWCOLOR
            + ";border-style:solid;border-color:"
            + mxConstants.W3C_SHADOWCOLOR + ";border-width:"
            + Math.round(strokeWidth) + ";";
         shadow.setAttribute("style", s);

         appendHtmlElement(shadow);
      }

      if(shape.equals(mxConstants.SHAPE_IMAGE)) {
         String img = getImageForStyle(style);

         if(img != null) {
            elem = document.createElement("img");
            elem.setAttribute("border", "0");
            elem.setAttribute("src", img);
         }
      }

      // TODO: Draw other shapes. eg. SHAPE_LINE here

      String s = "overflow:hidden;position:absolute;" + "left:"
         + x + "px;" + "top:" + y
         + "px;" + "width:" + w + "px;" + "height:"
         + h + "px;background:" + fillColor + ";"
         + ";border-style:solid;border-color:" + strokeColor
         + ";border-width:" + Math.round(strokeWidth)
         + ";";
      elem.setAttribute("style", s);

      appendHtmlElement(elem);

      return elem;
   }

   /**
    * Draws the given lines as segments between all points of the given list
    * of mxPoints.
    *
    * @param pts   List of points that define the line.
    * @param style Style to be used for painting the line.
    */
   public void drawLine(List<mxPoint> pts, Map<String, Object> style)
   {
      String strokeColor = mxUtils.getString(style,
                                             mxConstants.STYLE_STROKECOLOR);
      int strokeWidth = (int) (mxUtils.getInt(style,
                                              mxConstants.STYLE_STROKEWIDTH, 1) * scale);

      if(strokeColor != null && strokeWidth > 0) {

         mxPoint last = pts.get(0);

         for(int i = 1; i < pts.size(); i++) {
            mxPoint pt = pts.get(i);

            drawSegment((int) last.getX(), (int) last.getY(), (int) pt
               .getX(), (int) pt.getY(), strokeColor, strokeWidth);

            last = pt;
         }
      }
   }

   /**
    * Draws the specified segment of a line.
    *
    * @param x0          X-coordinate of the start point.
    * @param y0          Y-coordinate of the start point.
    * @param x1          X-coordinate of the end point.
    * @param y1          Y-coordinate of the end point.
    * @param strokeColor Color of the stroke to be painted.
    * @param strokeWidth Width of the stroke to be painted.
    */
   protected void drawSegment(int x0, int y0, int x1, int y1,
                              String strokeColor, int strokeWidth)
   {
      int tmpX = Math.min(x0, x1);
      int tmpY = Math.min(y0, y1);

      int width = Math.max(x0, x1) - tmpX;
      int height = Math.max(y0, y1) - tmpY;

      x0 = tmpX;
      y0 = tmpY;

      if(width == 0 || height == 0) {
         String s = "overflow:hidden;position:absolute;" + "left:"
            + x0 + "px;" + "top:" + y0
            + "px;" + "width:" + width + "px;"
            + "height:" + height + "px;"
            + "border-color:" + strokeColor + ";"
            + "border-style:solid;" + "border-width:1 1 0 0px;";

         Element elem = document.createElement("div");
         elem.setAttribute("style", s);

         appendHtmlElement(elem);
      }
      else {
         int x = x0 + (x1 - x0) / 2;

         drawSegment(x0, y0, x, y0, strokeColor, strokeWidth);
         drawSegment(x, y0, x, y1, strokeColor, strokeWidth);
         drawSegment(x, y1, x1, y1, strokeColor, strokeWidth);
      }
   }

   /**
    * Draws the specified text either using drawHtmlString or using drawString.
    *
    * @param text  Text to be painted.
    * @param x     X-coordinate of the text.
    * @param y     Y-coordinate of the text.
    * @param w     Width of the text.
    * @param h     Height of the text.
    * @param style Style to be used for painting the text.
    */
   public Element drawText(String text, int x, int y, int w, int h,
                           Map<String, Object> style)
   {
      Element table = mxUtils.createTable(document, text, x, y, w, h, scale,
                                          style);
      appendHtmlElement(table);

      return table;
   }

}
