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
import inetsoft.graph.mxgraph.util.*;
import inetsoft.graph.mxgraph.view.mxCellState;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class mxCurveShape extends mxConnectorShape {
   /**
    * Cache of the points between which drawing straight lines views as a
    * curve
    */
   protected mxCurve curve;

   /**
    *
    */
   public mxCurveShape()
   {
      this(new mxCurve());
   }

   /**
    *
    */
   public mxCurveShape(mxCurve curve)
   {
      this.curve = curve;
   }

   /**
    *
    */
   public mxCurve getCurve()
   {
      return curve;
   }

   /**
    *
    */
   public void paintShape(mxGraphics2DCanvas canvas, mxCellState state)
   {
      Object keyStrokeHint = canvas.getGraphics().getRenderingHint(
         RenderingHints.KEY_STROKE_CONTROL);
      canvas.getGraphics().setRenderingHint(
         RenderingHints.KEY_STROKE_CONTROL,
         RenderingHints.VALUE_STROKE_PURE);

      super.paintShape(canvas, state);

      canvas.getGraphics().setRenderingHint(
         RenderingHints.KEY_STROKE_CONTROL, keyStrokeHint);
   }

   /**
    *
    */
   protected void paintPolyline(mxGraphics2DCanvas canvas,
                                List<mxPoint> points, Map<String, Object> style)
   {
      double scale = canvas.getScale();
      validateCurve(points, scale, style);

      canvas.paintPolyline(curve.getCurvePoints(mxCurve.CORE_CURVE), false);
   }

   /**
    * Forces underlying curve to a valid state
    *
    * @param points
    */
   public void validateCurve(List<mxPoint> points, double scale,
                             Map<String, Object> style)
   {
      if(curve == null) {
         curve = new mxCurve(points);
      }
      else {
         curve.updateCurve(points);
      }

      curve.setLabelBuffer(scale * mxConstants.DEFAULT_LABEL_BUFFER);
   }

   /**
    * Hook to override creation of the vector that the marker is drawn along
    * since it may not be the same as the vector between any two control
    * points
    *
    * @param points     the guide points of the connector
    * @param source     whether the marker is at the source end
    * @param markerSize the scaled maximum length of the marker
    *
    * @return a line describing the vector the marker should be drawn along
    */
   protected mxLine getMarkerVector(List<mxPoint> points, boolean source,
                                    double markerSize)
   {
      double curveLength = curve.getCurveLength(mxCurve.CORE_CURVE);
      double markerRatio = markerSize / curveLength;
      if(markerRatio >= 1.0) {
         markerRatio = 1.0;
      }

      if(source) {
         mxLine sourceVector = curve.getCurveParallel(mxCurve.CORE_CURVE,
                                                      markerRatio);
         return new mxLine(sourceVector.getX(), sourceVector.getY(),
                           points.get(0));
      }
      else {
         mxLine targetVector = curve.getCurveParallel(mxCurve.CORE_CURVE,
                                                      1.0 - markerRatio);
         int pointCount = points.size();
         return new mxLine(targetVector.getX(), targetVector.getY(),
                           points.get(pointCount - 1));
      }
   }
}
