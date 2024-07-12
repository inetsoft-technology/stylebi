/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report;

import inetsoft.report.internal.*;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;

/**
 * RotationTransformer is used to transform point or shape when the
 * PainterPaintable is rotated 90 or 270 degree.
 */
public class RotationTransformer {
   /**
    * Get the RotationTransformer.
    * @param origin the origin point.
    * @param pt the paitable.
    * @return the RotationTransformer.
    */
   public static RotationTransformer getRotationTransformer(Point origin,
                                                            PainterPaintable pt)
   {
      if(pt.getRotation() == 0) {
         return new Rotation0Transformer(origin, pt);
      }
      else if(pt.getRotation() == 90) {
         return new Rotation90Transformer(origin, pt);
      }
      else if(pt.getRotation() == 270) {
         return new Rotation270Transformer(origin, pt);
      }

      return null;
   }

   /**
    * Constructor.
    */
   public RotationTransformer(Point origin, PainterPaintable pt) {
      this.origin = origin;
      this.paintable = pt;
   }

   /**
    * Get the position mapped from rotated to original.
    */
   public Point getRotated2OriginalPos(Point point) {
      Point pt = new Point();
      trans.transform(point, pt);
      return pt;
   }

   /**
    * Get the position mapped from original to rotated.
    */
   public Point getOriginal2RotatedPos(Point point)
      throws NoninvertibleTransformException
   {
      AffineTransform antiTrans = trans.createInverse();
      Point pt = new Point();
      antiTrans.transform(point, pt);
      return pt;
   }

   /**
    * Get the painting region of the original region.
    */
   public Region getPaintingRegion(Region region) {
      if(region instanceof RectangleRegion) {
         return getLocDimDescripingRegion(region);
      }
      else if(region instanceof EllipseRegion) {
         RectangleRegion rg = (RectangleRegion)
                               getLocDimDescripingRegion(region);
         Rectangle rect = (Rectangle) rg.createShape();
         return new EllipseRegion(rg.getName(), rect.x, rect.y,
                                  rect.width, rect.height);
      }
      else if(region instanceof PolygonRegion) {
         return getPointsDiscriptingRegion((PolygonRegion) region);
      }
      else if(region instanceof AreaRegion) {
         return getShapeDiscriptingRegion((AreaRegion) region);
      }

      return region;
   }

   /**
    * Get the rectangle and ellipse region of the original region.
    */
   protected Region getLocDimDescripingRegion(Region region) {
      Point[] srcpos = new Point[4];
      Point loc = region.getBounds().getLocation();
      Dimension dim = region.getBounds().getSize();
      srcpos[0] = loc;
      srcpos[1] = new Point(loc.x, loc.y - dim.height);
      srcpos[2] = new Point(loc.x + dim.width, loc.y - dim.height);
      srcpos[3] = new Point(loc.x + dim.width, loc.y);
      Point[] despos = new Point[]{new Point(), new Point(),
         new Point(), new Point()};

      try {
         trans.createInverse().transform(srcpos, 0, despos, 0, 4);
      }
      catch(NoninvertibleTransformException e) {
      }

      return createPaintingRectangle(region.getName(), despos);
   }

   /**
    * Get the polygon region of the original region.
    */
   protected Region getPointsDiscriptingRegion(PolygonRegion region) {
      Polygon plygon = (Polygon) region.createShape();
      float[] xpoints = new float[plygon.xpoints.length];
      float[] ypoints = new float[plygon.ypoints.length];
      int npoints = plygon.npoints;

      for(int i = 0; i < plygon.xpoints.length; i++) {
         xpoints[i] = plygon.xpoints[i];
      }

      for(int i = 0; i < plygon.ypoints.length; i++) {
         ypoints[i] = plygon.ypoints[i];
      }

      float[] srcxypoints = new float[xpoints.length + ypoints.length];
      float[] desxypoints = new float[xpoints.length + ypoints.length];
      int index = 0;

      for(int i = 0; i < xpoints.length; i++) {
         srcxypoints[index++] = xpoints[i];
         srcxypoints[index++] = ypoints[i];
      }

      try {
         trans.createInverse().transform(srcxypoints, 0, desxypoints, 0,
            srcxypoints.length / 2);
      }
      catch(NoninvertibleTransformException e) {
      }

      int[] rexpoints = new int[xpoints.length];
      int[] reypoints = new int[ypoints.length];

      for(int i = 0; i < plygon.xpoints.length; i++) {
         rexpoints[i] = (int) desxypoints[i * 2];
      }

      for(int i = 0; i < plygon.ypoints.length; i++) {
         reypoints[i] = (int) desxypoints[i * 2 + 1];
      }

      return new PolygonRegion(region.getName(),
                               rexpoints, reypoints, npoints, false);
   }

   protected Region getShapeDiscriptingRegion(AreaRegion region) {
      try {
         return new AreaRegion(trans.createInverse().createTransformedShape(region.getArea()));
      }
      catch(Exception ex) {
         return region;
      }
   }

   /**
    * Create painting rectangle for 90 rotation.
    */
   protected RectangleRegion createPaintingRectangle(String name, Point[] ps)
   {
      // to be override by subclass
      return null;
   }

   /**
    * Convert a coordinate from math to java or from java to math.
    */
   protected Point convertCoordinate(Point point) {
      return new Point(point.x, -point.y);
   }

   protected AffineTransform trans;
   protected PainterPaintable paintable;
   protected Point origin;
}

/**
 * If no rotation exists do not transform.
 */
class Rotation0Transformer extends RotationTransformer {
   /**
    * Constructor.
    */
   public Rotation0Transformer(Point origin, PainterPaintable pt) {
      super(origin, pt);
   }

   /**
    * Get the mouse position which maps to the original region.
    */
   @Override
   public Point getRotated2OriginalPos(Point point) {
      return point;
   }

   /**
    * Get the position mapped from original to rotated.
    */
   @Override
   public Point getOriginal2RotatedPos(Point point) {
      return point;
   }

   /**
    * Get the painting region of the original region.
    */
   @Override
   public Region getPaintingRegion(Region region) {
      return region;
   }
}

/**
 * Transform if the rotation is 90.
 */
class Rotation90Transformer extends RotationTransformer {
   /**
    * Constructor.
    */
   public Rotation90Transformer(Point origin, PainterPaintable pt) {
      super(origin, pt);
      int chartWidth = paintable.getDimension().width;
      Point mathLoc = convertCoordinate(origin);
      trans = AffineTransform.getRotateInstance((Math.PI / 2),
              mathLoc.x, mathLoc.y);
      trans.concatenate(AffineTransform.getTranslateInstance(-chartWidth, 0));
   }

   /**
    * Create painting rectangle for 90 rotation.
    */
   @Override
   protected RectangleRegion createPaintingRectangle(String name, Point[] ps)
   {
      return new RectangleRegion(name, ps[1].x, ps[1].y,
                 Math.abs(ps[1].x - ps[0].x),
                 Math.abs(ps[3].y - ps[0].y));
   }
}

/**
 * Transform if the rotation is 270.
 */
class Rotation270Transformer extends RotationTransformer {
   /**
    * Constructor.
    */
   public Rotation270Transformer(Point origin, PainterPaintable pt) {
      super(origin, pt);
      int chartHeight = paintable.getDimension().height;
      Point mathLoc = convertCoordinate(origin);
      trans = AffineTransform.getRotateInstance(-(Math.PI / 2),
              mathLoc.x + chartHeight, mathLoc.y);
      trans.concatenate(AffineTransform.getTranslateInstance(chartHeight, 0));
   }

   /**
    * Create painting rectangle for 270 rotation.
    */
   @Override
   protected RectangleRegion createPaintingRectangle(String name, Point[] ps)
   {
      return new RectangleRegion(name, ps[3].x, ps[3].y,
                 Math.abs(ps[1].x - ps[0].x),
                 Math.abs(ps[3].y - ps[0].y));
   }
}
