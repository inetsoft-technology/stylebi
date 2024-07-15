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

import inetsoft.graph.element.PointElement;
import inetsoft.graph.visual.CircularVOText;
import inetsoft.graph.visual.VOText;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.*;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * TextArea defines the method of write data to an OutputStream and parse it
 * from an InputStream.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class TextArea extends InteractiveArea implements MenuArea, RollOverArea,
                                                         GraphComponentArea
{
   /**
    * Constructor.
    */
   public TextArea(VOText text, AffineTransform trans, IndexedSet<String> palette) {
      super(text, trans, palette);

      this.votext = text;
      setMeasureName(GraphUtil.getOriginalCol(text.getMeasureName()));
      setColIndex(text.getColIndex());

      if(text.getGraphElement() instanceof PointElement) {
         PointElement point = (PointElement) text.getGraphElement();

         if(point.isWordCloud() && point.getTextFrame() != null) {
            setMeasureName(point.getTextFrame().getField());
         }
      }
   }

   @Override
   public Region[] getRegions() {
      if(votext instanceof CircularVOText) {
         Polygon poly = ((CircularVOText) votext).getPolyBounds();

         if(poly == null) {
            return new Region[0];
         }

         Point2D p = getRelPos();
         int[] ypoints = new int[poly.ypoints.length];
         int[] xpoints = new int[poly.xpoints.length];

         for(int i = 0; i < ypoints.length; i++) {
            Point2D ptSrc = new Point2D.Double(poly.xpoints[i], poly.ypoints[i]);
            Point2D ptDst = new Point2D.Double();
            ptDst = trans.transform(ptSrc, ptDst);
            ypoints[i] = (int) (ptDst.getY() - p.getY());
            xpoints[i] = (int) (ptDst.getX() - p.getX());
         }

         return new Region[] { new PolygonRegion(xpoints, ypoints, poly.npoints) };
      }

      return super.getRegions();
   }

   /**
    * Get the data info for this area.
    */
   @Override
   public ChartAreaInfo getChartAreaInfo() {
      boolean wordcloud = false;

      if(votext != null) {
         if(votext.getGraphElement() instanceof PointElement) {
            wordcloud = ((PointElement) votext.getGraphElement()).isWordCloud();
         }
      }

      ChartAreaInfo info = ChartAreaInfo.createText(
              votext != null && votext.isValueText());
      info.setProperty("column", getMeasureName());
      info.setProperty("row", getRowIndex());

      return info;
   }

   /**
    * Paint area.
    * @param g the graphic of the area.
    */
   @Override
   public void paintArea(Graphics g, Color color) {
      Region region = getRegion();

      if(region instanceof RectangleRegion) {
         ((RectangleRegion) region).fill(g, color);
      }
      else if(region instanceof PolygonRegion) {
         g.setColor(color);
         ((PolygonRegion) region).fillPolygon(g);
      }
   }

   /**
    * Get all the area which this contains.
    */
   @Override
   public DefaultArea[] getAllAreas() {
      return new DefaultArea[] {this};
   }

   /**
    * Method to test whether regions contain the specified point.
    * @param point the specified point.
    */
   @Override
   public boolean contains(Point point) {
      return getRegion().contains(point.x, point.y);
   }

   private VOText votext;
}
