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
package inetsoft.graph.mxgraph.shape;

import inetsoft.graph.mxgraph.canvas.mxGraphics2DCanvas;
import inetsoft.graph.mxgraph.util.mxConstants;
import inetsoft.graph.mxgraph.util.mxUtils;
import inetsoft.graph.mxgraph.view.mxCellState;

import java.awt.*;

/**
 * A rectangular shape that contains a single image. See mxImageBundle for
 * creating a lookup table with images which can then be referenced by key.
 */
public class mxImageShape extends mxRectangleShape {

   /**
    *
    */
   public void paintShape(mxGraphics2DCanvas canvas, mxCellState state)
   {
      super.paintShape(canvas, state);

      boolean flipH = mxUtils.isTrue(state.getStyle(),
                                     mxConstants.STYLE_IMAGE_FLIPH, false);
      boolean flipV = mxUtils.isTrue(state.getStyle(),
                                     mxConstants.STYLE_IMAGE_FLIPV, false);

      canvas.drawImage(getImageBounds(canvas, state),
                       getImageForStyle(canvas, state),
                       mxGraphics2DCanvas.PRESERVE_IMAGE_ASPECT, flipH, flipV);
   }

   /**
    *
    */
   public Rectangle getImageBounds(mxGraphics2DCanvas canvas, mxCellState state)
   {
      return state.getRectangle();
   }

   /**
    *
    */
   public boolean hasGradient(mxGraphics2DCanvas canvas, mxCellState state)
   {
      return false;
   }

   /**
    *
    */
   public String getImageForStyle(mxGraphics2DCanvas canvas, mxCellState state)
   {
      return canvas.getImageForStyle(state.getStyle());
   }

   /**
    *
    */
   public Color getFillColor(mxGraphics2DCanvas canvas, mxCellState state)
   {
      return mxUtils.getColor(state.getStyle(),
                              mxConstants.STYLE_IMAGE_BACKGROUND);
   }

   /**
    *
    */
   public Color getStrokeColor(mxGraphics2DCanvas canvas, mxCellState state)
   {
      return mxUtils.getColor(state.getStyle(),
                              mxConstants.STYLE_IMAGE_BORDER);
   }

}
