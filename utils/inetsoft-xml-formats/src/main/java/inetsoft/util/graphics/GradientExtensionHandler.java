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

import org.apache.batik.svggen.*;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import static org.apache.batik.util.SVGConstants.*;

/**
 * Extension of Batik's {@link DefaultExtensionHandler} which
 * handles different kinds of Paint objects
 * <p>
 * I wonder why this is not part of the svggen library.
 *
 * @author Martin Steiger
 */
public class GradientExtensionHandler extends DefaultExtensionHandler {
   @Override
   public SVGPaintDescriptor handlePaint(Paint paint, SVGGeneratorContext genCtx) {
      // Handle LinearGradientPaint
      if(paint instanceof LinearGradientPaint) {
         return getLgpDescriptor((LinearGradientPaint) paint, genCtx);
      }

      // Handle RadialGradientPaint
      if(paint instanceof RadialGradientPaint) {
         return getRgpDescriptor((RadialGradientPaint) paint, genCtx);
      }

      return super.handlePaint(paint, genCtx);
   }

   private SVGPaintDescriptor getRgpDescriptor(RadialGradientPaint gradient,
                                               SVGGeneratorContext genCtx)
   {
      Element gradElem = genCtx.getDOMFactory()
         .createElementNS(SVG_NAMESPACE_URI, SVG_RADIAL_GRADIENT_TAG);

      // Create and set unique XML id
      String id = genCtx.getIDGenerator().generateID("gradient");
      gradElem.setAttribute(SVG_ID_ATTRIBUTE, id);

      // Set x,y pairs
      Point2D centerPt = gradient.getCenterPoint();
      gradElem.setAttribute("cx", String.valueOf(centerPt.getX()));
      gradElem.setAttribute("cy", String.valueOf(centerPt.getY()));

      Point2D focusPt = gradient.getFocusPoint();
      gradElem.setAttribute("fx", String.valueOf(focusPt.getX()));
      gradElem.setAttribute("fy", String.valueOf(focusPt.getY()));

      gradElem.setAttribute("r", String.valueOf(gradient.getRadius()));

      addMgpAttributes(gradElem, genCtx, gradient);

      return new SVGPaintDescriptor("url(#" + id + ")", SVG_OPAQUE_VALUE, gradElem);
   }

   private SVGPaintDescriptor getLgpDescriptor(LinearGradientPaint gradient,
                                               SVGGeneratorContext genCtx)
   {
      Element gradElem = genCtx.getDOMFactory()
         .createElementNS(SVG_NAMESPACE_URI, SVG_LINEAR_GRADIENT_TAG);

      // Create and set unique XML id
      String id = genCtx.getIDGenerator().generateID("gradient");
      gradElem.setAttribute(SVG_ID_ATTRIBUTE, id);

      // Set x,y pairs
      Point2D startPt = gradient.getStartPoint();
      gradElem.setAttribute("x1", String.valueOf(startPt.getX()));
      gradElem.setAttribute("y1", String.valueOf(startPt.getY()));

      Point2D endPt = gradient.getEndPoint();
      gradElem.setAttribute("x2", String.valueOf(endPt.getX()));
      gradElem.setAttribute("y2", String.valueOf(endPt.getY()));

      addMgpAttributes(gradElem, genCtx, gradient);

      return new SVGPaintDescriptor("url(#" + id + ")", SVG_OPAQUE_VALUE, gradElem);
   }

   private void addMgpAttributes(Element gradElem, SVGGeneratorContext genCtx,
                                 MultipleGradientPaint gradient)
   {
      gradElem.setAttribute(SVG_GRADIENT_UNITS_ATTRIBUTE, SVG_USER_SPACE_ON_USE_VALUE);

      // Set cycle method
      switch(gradient.getCycleMethod()) {
      case REFLECT:
         gradElem.setAttribute(SVG_SPREAD_METHOD_ATTRIBUTE, SVG_REFLECT_VALUE);
         break;
      case REPEAT:
         gradElem.setAttribute(SVG_SPREAD_METHOD_ATTRIBUTE, SVG_REPEAT_VALUE);
         break;
      case NO_CYCLE:
         gradElem.setAttribute(SVG_SPREAD_METHOD_ATTRIBUTE, SVG_PAD_VALUE);   // this is the default
         break;
      }

      // Set color space
      switch(gradient.getColorSpace()) {
      case LINEAR_RGB:
         gradElem.setAttribute(SVG_COLOR_INTERPOLATION_ATTRIBUTE, SVG_LINEAR_RGB_VALUE);
         break;

      case SRGB:
         gradElem.setAttribute(SVG_COLOR_INTERPOLATION_ATTRIBUTE, SVG_SRGB_VALUE);
         break;
      }

      // Set transform matrix if not identity
      AffineTransform tf = gradient.getTransform();
      if(!tf.isIdentity()) {
         String matrix = "matrix(" +
            tf.getScaleX() + " " + tf.getShearX() + " " + tf.getTranslateX() + " " +
            tf.getScaleY() + " " + tf.getShearY() + " " + tf.getTranslateY() + ")";
         gradElem.setAttribute(SVG_TRANSFORM_ATTRIBUTE, matrix);
      }

      // Convert gradient stops
      Color[] colors = gradient.getColors();
      float[] fracs = gradient.getFractions();

      for(int i = 0; i < colors.length; i++) {
         Element stop = genCtx.getDOMFactory().createElementNS(SVG_NAMESPACE_URI, SVG_STOP_TAG);
         SVGPaintDescriptor pd = SVGColor.toSVG(colors[i], genCtx);

         stop.setAttribute(SVG_OFFSET_ATTRIBUTE, (int) (fracs[i] * 100.0f) + "%");
         stop.setAttribute(SVG_STOP_COLOR_ATTRIBUTE, pd.getPaintValue());

         if(colors[i].getAlpha() != 255) {
            stop.setAttribute(SVG_STOP_OPACITY_ATTRIBUTE, pd.getOpacityValue());
         }

         gradElem.appendChild(stop);
      }
   }
}
