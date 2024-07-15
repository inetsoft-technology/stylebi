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
package inetsoft.graph.mxgraph.canvas;

import inetsoft.graph.mxgraph.util.mxPoint;
import inetsoft.graph.mxgraph.util.mxUtils;
import inetsoft.graph.mxgraph.view.mxCellState;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * An implementation of a canvas that uses Graphics2D for painting. To use an
 * image canvas for an existing graphics canvas and create an image the
 * following code is used:
 *
 * <code>BufferedImage image = mxCellRenderer.createBufferedImage(graph, cells, 1, Color.white, true, null, canvas);</code>
 */
public class mxImageCanvas implements mxICanvas {

   /**
    *
    */
   protected mxGraphics2DCanvas canvas;

   /**
    *
    */
   protected Graphics2D previousGraphics;

   /**
    *
    */
   protected BufferedImage image;

   /**
    *
    */
   public mxImageCanvas(mxGraphics2DCanvas canvas, int width, int height,
                        Color background, boolean antiAlias)
   {
      this(canvas, width, height, background, antiAlias, true);
   }

   /**
    *
    */
   public mxImageCanvas(mxGraphics2DCanvas canvas, int width, int height,
                        Color background, boolean antiAlias, boolean textAntiAlias)
   {
      this.canvas = canvas;
      previousGraphics = canvas.getGraphics();
      image = mxUtils.createBufferedImage(width, height, background);

      if(image != null) {
         Graphics2D g = image.createGraphics();
         mxUtils.setAntiAlias(g, antiAlias, textAntiAlias);
         canvas.setGraphics(g);
      }
   }

   /**
    *
    */
   public mxGraphics2DCanvas getGraphicsCanvas()
   {
      return canvas;
   }

   /**
    *
    */
   public BufferedImage getImage()
   {
      return image;
   }

   /**
    *
    */
   public Object drawCell(mxCellState state)
   {
      return canvas.drawCell(state);
   }

   /**
    *
    */
   public Object drawLabel(String label, mxCellState state, boolean html)
   {
      return canvas.drawLabel(label, state, html);
   }

   /**
    *
    */
   public double getScale()
   {
      return canvas.getScale();
   }

   /**
    *
    */
   public void setScale(double scale)
   {
      canvas.setScale(scale);
   }

   /**
    *
    */
   public mxPoint getTranslate()
   {
      return canvas.getTranslate();
   }

   /**
    *
    */
   public void setTranslate(double dx, double dy)
   {
      canvas.setTranslate(dx, dy);
   }

   /**
    *
    */
   public BufferedImage destroy()
   {
      BufferedImage tmp = image;

      if(canvas.getGraphics() != null) {
         canvas.getGraphics().dispose();
      }

      canvas.setGraphics(previousGraphics);

      previousGraphics = null;
      canvas = null;
      image = null;

      return tmp;
   }

}
