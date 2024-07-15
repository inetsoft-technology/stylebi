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
package inetsoft.report.composition.region;

import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.SchemaElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.internal.*;
import inetsoft.graph.schema.StockPainter;
import inetsoft.graph.visual.*;
import inetsoft.report.composition.graph.IntervalDataSet;
import inetsoft.report.internal.*;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.*;

/**
 * VisualObjectArea defines the method of write data to an OutputStream and
 * parse it from an InputStream.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class VisualObjectArea extends InteractiveArea implements MenuArea {
   /**
    * Create an instance of visual object area.
    */
   public VisualObjectArea(ElementVO vobj, AffineTransform trans,
                           IndexedSet<String> palette, Rectangle plotBounds,
                           double topY, int lineIdx)
   {
      super(vobj, trans, palette);

      String measure = vobj.getMeasureName();

      if(measure != null && measure.startsWith(IntervalDataSet.TOP_PREFIX)) {
         measure = measure.substring(IntervalDataSet.TOP_PREFIX.length());
      }

      setMeasureName(measure);
      setRowIndexes(vobj.getRowIndexes());
      setColIndex(vobj.getColIndex());
      this.plotBounds = plotBounds;
      this.topY = topY;
      this.lineIdx = lineIdx;
   }

    /**
    * Get the class name.
    */
   @Override
   protected String getClassName() {
      // @by billh, special case to save space
      return "IGRV"; // inetsoft.report.composition.region.VisualObjectArea
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);

      // write to show reference line
      output.writeBoolean(isSupportReference());

      // write to show point
      boolean supportPoint = false ;

      if(vobj instanceof ElementVO) {
         Geometry geo = ((ElementVO) vobj).getGeometry();

         if(geo instanceof LineGeometry || geo instanceof AreaGeometry) {
            supportPoint = true;
         }
      }

      output.writeBoolean(supportPoint);
      output.writeInt(lineIdx);
   }

   /**
    * Method to test whether regions contain the specified point.
    * @param point the specified point.
    */
   @Override
   public boolean contains(Point point) {
      for(int i = 0; i < getRegions().length; i++) {
         if(getRegions()[i].contains(point.x, point.y)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      // schema is too complex, just show as a rectangle for selection area
      Shape[] shapes = vobj instanceof SchemaVO ? new Shape[] { vobj.getBounds() }
         : ((ElementVO) vobj).getShapes();

      if(shapes.length == 0 && vobj instanceof PointVO) {
         shapes = new Shape[] { vobj.getBounds() };
      }

      List<Region> regions = new ArrayList();

      for(int i = 0 ; i < shapes.length; i++) {
         regions.addAll(Arrays.asList(getRegions(shapes[i])));
      }

      return regions.toArray(new Region[regions.size()]);
   }

   /**
    * Get the region of the shape with absolute position.
    */
   public Region[] getRegions(Shape shape) {
      return getRegions(shape, false);
   }

   /**
    * Get region.
    */
   public Region[] getRegions(Shape shape, boolean ignoreRel) {
      Region region = null;
      Point2D p = ignoreRel ? new Point2D.Float(0, 0) : getRelPos();

      if(shape instanceof Donut) {
         Arc2D inner = ((Donut) shape).getInnerArc();

         if(inner.getWidth() == 0 || inner.getHeight() == 0) {
            shape = ((Donut) shape).getOuterArc();
         }
      }

      if(shape instanceof Donut || shape instanceof Arc2D) {
         if(shape instanceof Arc2D) {
            // java Arc2D path doesn't close even if the type is PIE.
            // force a lineTo to the start point to close the shape.
            if(((Arc2D) shape).getArcType() == Arc2D.PIE) {
               Path2D path = new Path2D.Double(shape);
               PathIterator iter = path.getPathIterator(null);
               float[] pts = new float[8];
               iter.currentSegment(pts);
               path.lineTo(pts[0], pts[1]);
               shape = path;
            }
         }

         AffineTransform trans2 = new AffineTransform();
         // flip the coordinates. take care of case where legend is on top.
         trans2.translate(-p.getX(), topY);
         shape = GDefaults.FLIPY.createTransformedShape(shape);
         shape = trans2.createTransformedShape(shape);
         region = new AreaRegion(shape);
      }
      else if(shape instanceof Ellipse2D) {
         Rectangle2D tmp = shape.getBounds2D();

	      // make sure size is not too small so the selection is easier
         if(tmp.getWidth() < 6 && tmp.getHeight() < 6) {
            tmp = new Rectangle2D.Double(tmp.getX() - 2, tmp.getY() - 2,
                                         tmp.getWidth() + 4, tmp.getHeight()+4);
         }

         Point2D pt1 = new Point2D.Double(tmp.getX(), tmp.getY());
         pt1 = trans.transform(pt1, null);

         region = new EllipseRegion(
            "", pt1.getX() - p.getX(),
                pt1.getY() - p.getY() - tmp.getHeight(),
                tmp.getWidth(), tmp.getHeight());
      }
      else if(shape instanceof Polygon) {
         Polygon tmp = (Polygon) shape;
         int[] xps = tmp.xpoints;
         int[] yps = tmp.ypoints;

         for(int i = 0; i < tmp.npoints; i++) {
            Point2D pt = new Point2D.Double(xps[i], yps[i]);
            pt = trans.transform(pt, null);
            xps[i] = (int) (pt.getX() - p.getX());
            yps[i] = (int) (pt.getY() - p.getY() - tmp.getBounds().getHeight());
         }

         region = new PolygonRegion("", xps, yps, tmp.npoints, false);
      }
      else if(shape instanceof Line2D) {
         Point2D p1 = ((Line2D) shape).getP1();
         Point2D p2 = ((Line2D) shape).getP2();
         p1 = trans.transform(p1, null);
         p2 = trans.transform(p2, null);
         region = new LineRegion("", new Line2D.Double(
            p1.getX() - p.getX(), p1.getY() - p.getY(),
            p2.getX() - p.getX(), p2.getY() - p.getY()));
      }
      else if(vobj instanceof PolygonVO) {
         GeneralPath path = shape instanceof GeneralPath ? (GeneralPath) shape :
            new GeneralPath(shape);
         PathIterator iterator = path.getPathIterator(trans);
         List<Point2D.Float> points = new ArrayList<>();
         List<Region> regions = new ArrayList<>();
         int scaleFactor = 100;

         while(!iterator.isDone()) {
            float[] pts = new float[8];
            int type = iterator.currentSegment(pts);

            if(pts[0] != 0 && pts[1] != 0) {
               points.add(new Point2D.Float(pts[0], pts[1]));
            }

            iterator.next();

            if(type == PathIterator.SEG_CLOSE) {
               int cnt = points.size();
               int[] xps = new int[cnt];
               int[] yps = new int[cnt];

               for(int i = 0; i < cnt; i++) {
                  Point2D point = points.get(i);
                  xps[i] = (int) ((point.getX() - p.getX()) * scaleFactor);
                  yps[i] = (int) ((point.getY() - p.getY()) * scaleFactor);
               }

               regions.add(new PolygonRegion("", xps, yps, cnt, false, scaleFactor));
               points.clear();
            }
         }

         return regions.toArray(new Region[regions.size()]);
      }
      else {
         boolean isLine = shape instanceof Line2D;
         Rectangle2D.Double rect =
            (Rectangle2D.Double) GTool.transform(shape.getBounds2D(), trans);

         rect.x = rect.x - p.getX();
         // @by davyc, the visual should keep in chart in client side,
         // so here the rect should in chart bounds, means rect.y should >= 0,
         // and rect.y + rect.height should <= chart height,
         // if we modify the rect.y, then the height should also be modified
         double oldy = rect.y - p.getY();
         rect.y = Math.max(rect.y - p.getY(), 0);
         rect.height = rect.height - (rect.y - oldy);

         // make sure the shape is not too small so it's easier to select
         if(rect.width < 3) {
            rect.x--;
            rect.width = 3;
         }

         if(rect.height < 3) {
            rect.y--;
            rect.height = 3;
         }

         // Candle and Stock line should expand to 3 pixels
         if((vobj instanceof SchemaVO) && isLine) {
            processSchemaVO((SchemaVO) vobj, rect);
         }
         else {
            rect.y = rect.y - 1;
         }

         if(!ignoreRel) {
            rect.width = Math.min(rect.width, plotBounds.getWidth() - rect.x);
            rect.height = Math.min(rect.height, plotBounds.getHeight() - rect.y);
         }

         region = new RectangleRegion(rect);
      }

      return new Region[] {region};
   }

   /**
    * Get the fixed value. for x, second and third quadrant use Math.ceil(),
    * others use Math.floor() - 1, for y, first and second quadrant use
    * Math.ceil(), others use Math.floor() - 1.
    */
   private int getFixedValue(double value, double angle, boolean isX) {
      int result = 0;
      angle = angle % (2 * Math.PI);

      if(isX) {
         result = angle > Math.PI / 2 && angle < Math.PI * 3 / 2 ?
            (int) Math.ceil(value) : (int) Math.floor(value) - 1;
      }
      else {
         result = angle > 0 && angle < Math.PI ? (int) Math.ceil(value) :
                                                 (int) Math.floor(value) - 1;
      }

      return result;
   }

   /**
    * If is schema vo, should specified process.
    */
   private void processSchemaVO(SchemaVO svo, Rectangle2D.Double rect) {
      if(rect.width <= 1) {
         rect.x = rect.x - 2;

         if(rect.x < 0) {
            rect.x = 0;
         }

         // if height to small, make it more easy to select
         if(rect.height <= 1) {
            rect.y = rect.y - 1;
            rect.y = Math.max(rect.y, 0);
            rect.height = 3;
         }

         rect.width = rect.width + 3;
         return;
      }

      Geometry geom = svo.getGeometry();

      if(!(geom instanceof SchemaGeometry)) {
         return;
      }

      GraphElement element = ((SchemaGeometry) geom).getElement();

      if(!(element instanceof SchemaElement)) {
         return;
      }

      SchemaElement sement = (SchemaElement) element;

      if(!(sement.getPainter() instanceof StockPainter)) {
         return;
      }

      if(rect.height <= 1) {
         // means this is the close line of the Stock
         Shape[] shapes = svo.getShapes();

         if(shapes.length > 1) {
            // shapes[0] is high low line, shapes[1] is close line
            Rectangle2D.Double rect1 = (Rectangle2D.Double) GTool.transform(
               shapes[0].getBounds(), trans);
            Rectangle2D.Double rect2 = (Rectangle2D.Double) GTool.transform(
               shapes[1].getBounds(), trans);
            double gap1 = rect1.y - rect2.y;
            double gap2 = rect1.y + rect1.height - rect2.y - rect2.height;

            // if rect1.height is too small, process it especially
            if(rect1.height <= 1) {
               rect.y = rect.y - 2;
               rect.height = rect.height + 3;
               rect.y = Math.max(rect.y, 0);
               return;
            }

            // if close line nears high low line's top
            // if the close line is in high low line, rect.x to add 1 pixel to
            // make it seems more comfortable
            if(gap1 > 0 && gap1 < 1) {
               rect.y = rect.y - 3;
            }
            else if(gap1 <= 0 && Math.abs(gap1) < 1) {
               rect.y = rect.y;
               rect.x = rect.x + 1;
            }
            // if close line nears high low line's bottom
            else if(gap2 >= 0 && gap2 < 1) {
               rect.y = rect.y - 3;
               rect.x = rect.x + 1;
            }
            else if(gap2 < 0 && Math.abs(gap2) < 1) {
               rect.y = rect.y;
            }
            else {
               rect.y = rect.y - 2;

               if(gap1 <= 0 && gap2 >= 0) {
                  rect.x = rect.x + 1;
               }
            }

            rect.y = Math.max(rect.y, 0);
         }

         rect.height = rect.height + 3;
      }
   }

   /**
    * Get the data info for this area.
    */
   @Override
   public ChartAreaInfo getChartAreaInfo() {
      ChartAreaInfo info = ChartAreaInfo.createVO();
      info.setProperty("column", getMeasureName());

      return info;
   }

   /**
    * Check if show reference line.
    */
   public boolean isSupportReference() {
      return vobj instanceof PointVO || vobj instanceof ElementVO
         && ((ElementVO) vobj).getGeometry() instanceof LineGeometry;
   }

   /**
    * Check if show point.
    */
   public boolean isSupportPoint() {
      if(vobj instanceof ElementVO) {
         Geometry geo = ((ElementVO) vobj).getGeometry();

         if(geo instanceof LineGeometry || geo instanceof AreaGeometry) {
            return true;
         }
      }

      return false;
   }

   public int getLineIndex() {
      return lineIdx;
   }

   private Rectangle plotBounds;
   private final double topY;
   private int lineIdx = -1;
}
