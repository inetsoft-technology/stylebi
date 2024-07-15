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
package inetsoft.graph.mxgraph.util;

import inetsoft.graph.mxgraph.canvas.*;
import inetsoft.graph.mxgraph.view.*;
import org.w3c.dom.Document;

import java.awt.*;
import java.awt.image.BufferedImage;

public class mxCellRenderer {
   /**
    *
    */
   private mxCellRenderer()
   {
      // static class
   }

   /**
    * Draws the given cells using a Graphics2D canvas and returns the buffered image
    * that represents the cells.
    *
    * @param graph Graph to be painted onto the canvas.
    *
    * @return Returns the image that represents the canvas.
    */
   public static mxICanvas drawCells(mxGraph graph, Object[] cells,
                                     double scale, mxRectangle clip, CanvasFactory factory)
   {
      mxICanvas canvas = null;

      if(cells == null) {
         cells = new Object[]{ graph.getModel().getRoot() };
      }

      // Gets the current state of the view
      mxGraphView view = graph.getView();

      // Keeps the existing translation as the cells might
      // be aligned to the grid in a different way in a graph
      // that has a translation other than zero
      boolean eventsEnabled = view.isEventsEnabled();

      // Disables firing of scale events so that there is no
      // repaint or update of the original graph
      view.setEventsEnabled(false);

      // Uses the view to create temporary cell states for each cell
      mxTemporaryCellStates temp = new mxTemporaryCellStates(view, scale,
                                                             cells);

      try {
         if(clip == null) {
            clip = graph.getPaintBounds(cells);
         }

         if(clip != null && clip.getWidth() > 0 && clip.getHeight() > 0) {
            Rectangle rect = clip.getRectangle();
            canvas = factory.createCanvas(rect.width + 1, rect.height + 1);

            if(canvas != null) {
               double previousScale = canvas.getScale();
               mxPoint previousTranslate = canvas.getTranslate();

               try {
                  canvas.setTranslate(-rect.x, -rect.y);
                  canvas.setScale(view.getScale());

                  for(int i = 0; i < cells.length; i++) {
                     graph.drawCell(canvas, cells[i]);
                  }
               }
               finally {
                  canvas.setScale(previousScale);
                  canvas.setTranslate(previousTranslate.getX(),
                                      previousTranslate.getY());
               }
            }
         }
      }
      finally {
         temp.destroy();
         view.setEventsEnabled(eventsEnabled);
      }

      return canvas;
   }

   /**
    *
    */
   public static BufferedImage createBufferedImage(mxGraph graph,
                                                   Object[] cells, double scale, Color background, boolean antiAlias,
                                                   mxRectangle clip)
   {
      return createBufferedImage(graph, cells, scale, background, antiAlias,
                                 clip, new mxGraphics2DCanvas());
   }

   /**
    *
    */
   public static BufferedImage createBufferedImage(mxGraph graph,
                                                   Object[] cells, double scale, final Color background,
                                                   final boolean antiAlias, mxRectangle clip,
                                                   final mxGraphics2DCanvas graphicsCanvas)
   {
      mxImageCanvas canvas = (mxImageCanvas) drawCells(graph, cells, scale,
                                                       clip, new CanvasFactory() {
            public mxICanvas createCanvas(int width, int height)
            {
               return new mxImageCanvas(graphicsCanvas, width, height,
                                        background, antiAlias);
            }

         });

      return (canvas != null) ? canvas.destroy() : null;
   }

   /**
    *
    */
   public static Document createHtmlDocument(mxGraph graph, Object[] cells,
                                             double scale, Color background, mxRectangle clip)
   {
      mxHtmlCanvas canvas = (mxHtmlCanvas) drawCells(graph, cells, scale,
                                                     clip, new CanvasFactory() {
            public mxICanvas createCanvas(int width, int height)
            {
               return new mxHtmlCanvas(mxDomUtils.createHtmlDocument());
            }

         });

      return (canvas != null) ? canvas.getDocument() : null;
   }

   /**
    *
    */
   public static Document createSvgDocument(mxGraph graph, Object[] cells,
                                            double scale, Color background, mxRectangle clip)
   {
      mxSvgCanvas canvas = (mxSvgCanvas) drawCells(graph, cells, scale, clip,
                                                   new CanvasFactory() {
                                                      public mxICanvas createCanvas(int width, int height)
                                                      {
                                                         return new mxSvgCanvas(mxDomUtils.createSvgDocument(width,
                                                                                                             height));
                                                      }

                                                   });

      return (canvas != null) ? canvas.getDocument() : null;
   }

   /**
    *
    */
   public static Document createVmlDocument(mxGraph graph, Object[] cells,
                                            double scale, Color background, mxRectangle clip)
   {
      mxVmlCanvas canvas = (mxVmlCanvas) drawCells(graph, cells, scale, clip,
                                                   new CanvasFactory() {
                                                      public mxICanvas createCanvas(int width, int height)
                                                      {
                                                         return new mxVmlCanvas(mxDomUtils.createVmlDocument());
                                                      }

                                                   });

      return (canvas != null) ? canvas.getDocument() : null;
   }

   /**
    *
    */
   public static abstract class CanvasFactory {

      /**
       * Separates the creation of the canvas from its initialization, when the
       * size of the required graphics buffer / document / container is known.
       */
      public abstract mxICanvas createCanvas(int width, int height);

   }

}
