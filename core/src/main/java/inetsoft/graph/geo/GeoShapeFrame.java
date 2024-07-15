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
package inetsoft.graph.geo;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.*;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

/**
 * A GeoShapeFrame contains all shapes for a geographical region. This shape
 * frame can be assigned to a polygon element to create region shape.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class GeoShapeFrame extends StaticShapeFrame {
   /**
    * Create a shape frame.
    */
   public GeoShapeFrame() {
   }

   /**
    * Create a geo shape frame.
    * @param field the name of the original shape geography column.
    */
   public GeoShapeFrame(String field) {
      setField(GeoDataSet.getGeoAreaField(field));
   }

   @Override
   public void init(DataSet data) {
      if(data instanceof GeoDataSet && getField() == null) {
         String field = ((GeoDataSet) data).getAreaField();
         setField(GeoDataSet.getGeoAreaField(field));
      }

      super.init(data);
   }

   /**
    * Set the border color.
    */
   public void setLineColor(Color color) {
      this.linecolor = color;
   }

   /**
    * Get the border color.
    */
   public Color getLineColor() {
      return linecolor;
   }

   /**
    * Set the line style for the specified shape class.
    */
   public void setLine(String style, GLine line) {
      linemap.put(style, line);
   }

   /**
    * Get the line style for for the specified shape class.
    */
   public GLine getLine(String style) {
      return linemap.get(style);
   }

   /**
    * Set the fill color for the specified shape class.
    */
   public void setFill(String style, Color line) {
      fillmap.put(style, line);
   }

   /**
    * Get the fill color for for the specified shape class.
    */
   public Color getFill(String style) {
      return fillmap.get(style);
   }

   /**
    * Set the font of the style.
    */
   public void setFont(String style, Font font) {
      fontmap.put(style, font);
   }

   /**
    * Get the font of the style.
    */
   public Font getFont(String style) {
      return fontmap.get(style);
   }

   /**
    * Set the label color for the specified shape class.
    */
   public void setFontColor(String style, Color line) {
      colormap.put(style, line);
   }

   /**
    * Get the label color for for the specified shape class.
    */
   public Color getFontColor(String style) {
      return colormap.get(style);
   }

   @Override
   public GShape getShape(DataSet data, String col, int row) {
      String field = getField();

      if(field != null) {
         Object val = data.getData(field, row);

         if(val instanceof GeoShape) {
            return new GeoGShape((GeoShape) val);
         }
      }

      return super.getShape(data, col, row);
   }

   private class GeoGShape extends GShape implements PolyShape {
      public GeoGShape(GeoShape geoshape) {
         this.geoshape = geoshape;
         setFill(geoshape.isFill());
         setOutline(geoshape.isOutline());
      }

      @Override
      public Point2D getCenter(Shape shape) {
         Point2D cpt = geoshape.getPrimaryAnchor();
         Rectangle2D box = shape.getBounds2D();
         double xmin = geoshape.getBounds().getMinX();
         double xmax = geoshape.getBounds().getMaxX();
         double ymin = geoshape.getBounds().getMinY();
         double ymax = geoshape.getBounds().getMaxY();
         double xratio = (cpt.getX() - xmin) / (xmax - xmin);
         double yratio = (cpt.getY() - ymin) / (ymax - ymin);

         return new Point2D.Double(box.getX() + box.getWidth() * xratio,
                                   box.getY() + box.getHeight() * yratio);
      }

      @Override
      public Shape getShape(double x, double y, double w, double h) {
         throw new UnsupportedOperationException("Use getShape(Coordinate)");
      }

      @Override
      public Shape getShape(Coordinate coord) {
         return geoshape.getShape(coord);
      }

      @Override
      public void paint(Graphics2D g, Shape shape) {
         g = (Graphics2D) g.create();
         String style = geoshape.getStyle();
         boolean fill = isFill();
         boolean outline = isOutline();
         String label = geoshape.getLabel();

         if(style != null) {
            Color fillcolor = getFill(style);
            GLine line = getLine(style);

            if(fillcolor != null) {
               g.setColor(fillcolor);
            }

            if(line != null) {
               g.setStroke(line.getStroke());
            }
         }

         super.paint(g, shape);

         if(outline && fill && linecolor != null) {
            g.setColor(linecolor);
            g.draw(shape);
         }

         if(label != null) {
            Point2D center = getCenter(shape);
            Font font = null;
            Color fg = null;

            if(style != null) {
               font = getFont(style);
               fg = getFontColor(style);
            }

            if(font == null) {
               font = GDefaults.DEFAULT_SMALL_FONT;
            }

            if(fg == null) {
               fg = GDefaults.DEFAULT_TEXT_COLOR;
            }

            g.setFont(font);
            g.setColor(fg);

            FontMetrics fm = g.getFontMetrics();
            int sw = fm.stringWidth(label);
            double sx = center.getX() - sw / 2;
            double sy = center.getY() - fm.getHeight() / 2 + fm.getMaxDescent();

            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                               RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            GTool.drawString(g, label, sx, sy);
         }

         g.dispose();
      }

      @Override
      public GeoShape getShape() {
         return geoshape;
      }

      @Override
      protected boolean isAntiAlias() {
         return geoshape.isAntiAlias();
      }

      private final GeoShape geoshape;
   }

   private Color linecolor = GDefaults.DEFAULT_LINE_COLOR;
   private Map<String,GLine> linemap = new HashMap<>();
   private Map<String,Color> fillmap = new HashMap<>();
   private Map<String,Font> fontmap = new HashMap<>();
   private Map<String,Color> colormap = new HashMap<>();
}
