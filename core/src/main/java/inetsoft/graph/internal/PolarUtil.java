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
package inetsoft.graph.internal;

import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.PolarAxis;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.*;
import inetsoft.report.StyleFont;
import inetsoft.report.internal.Common;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.util.List;

/**
 * Common functions for pie.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class PolarUtil {
   /**
    * Get pie radius.
    * @param w pie plot width.
    * @param h pie plot height.
    * @param label visual text or visual dimension label displayed on pie.
    * @param angle an angle, in radians.
    */
   public static double getPieRadius(double w, double h, VLabel label, double angle) {
      double pw = label == null ? 0 : label.getPreferredWidth();
      double ph = label == null ? 0 : label.getPreferredHeight();
      double r1 = (w / 2 - pw) / Math.abs(Math.cos(angle));
      double r2 = (h / 2 - ph) / Math.abs(Math.sin(angle));

      return Math.min(r1, r2);
   }

   /**
    * Get pie radius with text.
    * @param w pie plot width.
    * @param h pie plot height.
    * @param vgraph visual graph.
    */
   public static double getTextPieRadius(double w, double h, VGraph vgraph) {
      double r = Math.min(w / 2, h / 2);
      List vos = GTool.getVOs(vgraph);

      for(int i = 0; i < vos.size(); i++) {
         VisualObject vo = (VisualObject) vos.get(i);

         if(vo instanceof BarVO) {
            BarVO bvo = (BarVO) vo;
            VOText text = bvo.getVOText();

            if(!(text instanceof ArcVOText)) {
               continue;
            }

            Shape shape = bvo.getShape();
            double angle = 0;

            if(shape instanceof Donut) {
               shape = ((Donut) shape).getOuterArc();
            }

            if(shape instanceof Arc2D) {
               Arc2D arc = (Arc2D) shape;
               angle = (arc.getAngleStart() + arc.getAngleExtent() / 2) * Math.PI / 180.0;
            }

            r = Math.min(r, getPieRadius(w, h, text, angle));
         }
      }

      r = Math.max(r, Math.min(MIN_RADIUS, Math.min(w / 4, h / 4)));

      return r;
   }

   /**
    * Get 3d pie radius with text.
    * @param w pie plot width.
    * @param h pie plot height.
    * @param vgraph visual graph.
    */
   public static double getText3DPieRadius(double w, double h, VGraph vgraph) {
      double r = Math.min(w / 2, h / 2);
      List vos = GTool.getVOs(vgraph);

      for(int i = 0; i < vos.size(); i++) {
         VisualObject vo = (VisualObject) vos.get(i);

         if(vo instanceof Pie3DVO) {
            Pie3DVO pvo = (Pie3DVO) vo;
            Shape[] shapes = pvo.getShapes();
            VOText[] texts = pvo.getVOTexts();

            for(int j = 0; j < texts.length; j++) {
               if(texts[j] == null || !(texts[j] instanceof ArcVOText)) {
                  continue;
               }

               Shape shape = shapes[j];
               double angle = 0;
               Arc2D arc = null;

               if(shape instanceof Donut) {
                  arc = ((Donut) shape).getOuterArc();
               }
               else if(shape instanceof Arc2D) {
                  arc = (Arc2D) shape;
               }

               if(arc != null) {
                  angle = (arc.getAngleStart() + arc.getAngleExtent() / 2) *
                     Math.PI / 180.0;
               }

               double r2 = getPieRadius(w, h, texts[j], angle);
               r2 = r2 * get3DPieRadiusRatio(angle* Math.PI / 180.0);

               r = Math.min(r, r2);
            }
         }
      }

      r = Math.max(r, Math.min(MIN_RADIUS, Math.min(w / 4, h / 4)));

      return r;
   }

   /**
    * Get the radius if there is no text labels.
    */
   public static double getDefaultRadius(double w, double h) {
      double r = Math.min(w / 2, h / 2);

      return Math.max(r, Math.min(MIN_RADIUS, Math.min(w / 4, h / 4)));
   }

   /**
    * Get pie radius with axis label.
    * @param w pie plot width.
    * @param h pie plot height.
    */
   public static double getLabelPieRadius(double w, double h, Coordinate coord) {
      double r = Math.min(w / 2, h / 2);
      Axis[] axes = coord.getAxes(false);

      for(int i = 0; i < axes.length; i++) {
         Axis axis = axes[i];

         if(axis instanceof PolarAxis) {
            PolarAxis paxis = (PolarAxis) axis;
            VLabel[] vlabels = paxis.getLabels();
            Scale scale = paxis.getScale();
            double[] angles = getTickLocations(scale, scale.getTicks());
            double rotation = GTool.getRotation(coord.getCoordTransform());

            for(int j = 0; j < vlabels.length; j++) {
               r = Math.min(r, getPieRadius(w, h, vlabels[j],
                                            angles[j] + rotation));
            }
         }
      }

      r = Math.max(r, Math.min(MIN_RADIUS, Math.min(w / 4, h / 4)));

      return r;
   }

   /**
    * Get pie label width.
    * @param w pie plot width.
    * @param r pie radius.
    * @param angle an angle, in radians.
    */
   private static double getPieLabelMaxWidth(double w, double r, double angle) {
      return w / 2 - Math.abs(Math.cos(angle)) * r;
   }

   /**
    * Get pie label width.
    * @param h pie plot height.
    * @param r pie radius.
    * @param angle an angle, in radians.
    */
   private static double getPieLabelMaxHeight(double h, double r, double angle) {
      return h / 2 - Math.abs(Math.sin(angle)) * r;
   }

   /**
    * Get 3D pie label width.
    * @param w pie plot width.
    * @param r pie radius.
    * @param angle an angle, in radians.
    */
   private static double get3DPieLabelMaxWidth(double w, double r, double angle) {
      return getPieLabelMaxWidth(w, r / get3DPieRadiusRatio(angle), angle);
   }

   /**
    * Get 3D pie label width.
    * @param h pie plot height.
    * @param r pie radius.
    * @param angle an angle, in radians.
    */
   private static double get3DPieLabelMaxHeight(double h, double r, double angle){
      return getPieLabelMaxHeight(h, r / get3DPieRadiusRatio(angle), angle);
   }

   /**
    * Get 3D Pie radius ratio.
    */
   private static double get3DPieRadiusRatio(double angle) {
      double cos = Math.cos(angle);
      double sin = Math.sin(angle);

      return Math.pow(cos * cos + 4 * sin * sin, 1.0 / 2);
   }

   /**
    * Get pie label width.
    *
    * @param w        pie plot width.
    * @param r        pie radius.
    * @param angle    an angle, in radians.
    * @param label    visual label.
    * @param moveable if the labels can be moved to corner.
    */
   public static double getPieLabelWidth(double w, double r, double angle, VLabel label,
                                         boolean moveable)
   {
      double preferredWidth = label.getPreferredWidth();
      double minWidth = label.getMinWidth();
      double maxWidth = getPieLabelMaxWidth(w, r, angle);

      if(moveable) {
         maxWidth = Math.max(w / 2 - label.getPreferredHeight(), maxWidth);
      }

      return Math.max(minWidth, Math.min(preferredWidth, maxWidth));
   }

   /**
    * Get pie label height.
    * @param h pie plot height.
    * @param r pie radius.
    * @param angle an angle, in radians.
    * @param label visual label.
    */
   public static double getPieLabelHeight(double h, double r, double angle, VLabel label) {
      double preferredHeight = label.getPreferredHeight();
      double minHeight = label.getMinHeight();
      double maxHeight = getPieLabelMaxHeight(h, r, angle);

      return Math.max(minHeight, Math.min(preferredHeight, maxHeight));
   }

   /**
    * Get 3D pie label width.
    * @param w pie plot width.
    * @param r pie radius.
    * @param angle an angle, in radians.
    * @param label visual label.
    */
   public static double get3DPieLabelWidth(double w, double r, double angle, VLabel label,
                                           boolean moveable)
   {
      double preferredWidth = label.getPreferredWidth();
      double minWidth = label.getMinWidth();
      double maxWidth = get3DPieLabelMaxWidth(w, r, angle);

      if(moveable) {
         maxWidth = Math.max(w / 2 - label.getPreferredHeight(), maxWidth);
      }

      return Math.max(minWidth, Math.min(preferredWidth, maxWidth));
   }

   /**
    * Get 3D pie label height.
    * @param h pie plot height.
    * @param r pie radius.
    * @param angle an angle, in radians.
    * @param label visual label.
    */
   public static double get3DPieLabelHeight(double h, double r, double angle, VLabel label) {
      double preferredHeight = label.getPreferredHeight();
      double minHeight = label.getMinHeight();
      double maxHeight = get3DPieLabelMaxHeight(h, r, angle);

      return Math.max(minHeight, Math.min(preferredHeight, maxHeight));
   }

   /**
    * Get the X location (chart coordinate) of the ticks as an angle in the
    * oval.
    * @param scale the specified scale of axis.
    */
   public static double[] getTickLocations(Scale scale, double[] ticks) {
      for(int i = 0; i < ticks.length; i++) {
         ticks[i] = (ticks[i] - scale.getMin()) * Math.PI * 2 /
            (scale.getMax() - scale.getMin());
      }

      return ticks;
   }

   /**
    * Draw text on a curve.
    * @param start point on the curve to center the text.
    * @param extent range of angles for limit the drawing.
    */
   public static void drawString(Graphics2D g, Point2D center, double start, double extent,
                                 double radius, String content)
   {
      double min = start - extent / 2;
      double max = start + extent / 2 + Math.PI / 90; // allow slight out of bounds?
      FontMetrics fm = g.getFontMetrics();
      radius -= fm.getDescent();
      final double scaleX = 1.1;
      double stringWidth = fm.stringWidth(content) * scaleX;
      double stringRadian = stringWidth / radius;
      Font font = g.getFont();
      FontRenderContext frc = g.getFontRenderContext();
      GlyphVector gv = font.createGlyphVector(frc, content);
      Font fontNoUnderline = new StyleFont(font.getName(), font.getStyle() & ~StyleFont.UNDERLINE,
                                           font.getSize());

      start = Math.max(min, start - stringRadian / 2);

      for(int i = 0; i < content.length(); i++) {
         String str = content.substring(i, i + 1);
         double radian2 = start + gv.getGlyphPosition(i).getX() * scaleX * stringRadian
            / stringWidth;
         Rectangle2D bounds = gv.getGlyphOutline(i).getBounds2D();

         if(radian2 + bounds.getWidth() * stringRadian / stringWidth > max) {
            break;
         }

         Point2D tpos = calculatePoint(center, radius, radian2);
         Graphics2D g2 = (Graphics2D) g.create();
         // underline drawn later so should not draw it here.
         g2.setFont(fontNoUnderline);
         g2.translate((float) tpos.getX(), (float) tpos.getY());
         g2.rotate(-radian2 - Math.PI / 2);
         GTool.drawString(g2, str, 0, 0);
         g2.dispose();
      }

      if(font instanceof StyleFont) {
         StyleFont font2 = (StyleFont) font;

         if((font.getStyle() & StyleFont.UNDERLINE) != 0) {
            g.setStroke(GTool.getStroke(font2.getUnderlineStyle()));
            // move down 1px matches default underline rendering better. (59468)
            g.draw(new Arc2D.Double(center.getX() - radius, center.getY() - radius - 1,
                                    radius * 2, radius * 2, Math.toDegrees(start),
                                    Math.toDegrees(stringRadian), Arc2D.OPEN));
         }

         if((font.getStyle() & StyleFont.STRIKETHROUGH) != 0) {
            double radius2 = radius + fm.getAscent() / 2;
            g.setStroke(GTool.getStroke(font2.getStrikelineStyle()));
            g.draw(new Arc2D.Double(center.getX() - radius2, center.getY() - radius2,
                                    radius2 * 2, radius2 * 2, Math.toDegrees(start),
                                    Math.toDegrees(stringRadian), Arc2D.OPEN));
         }

         g.setStroke(new BasicStroke());
      }
   }

   private static Point2D calculatePoint(Point2D center, double radius, double radian) {
      Point2D.Double point = new Point2D.Double();
      point.x = center.getX() + Math.cos(radian) * radius;
      point.y = center.getY() - Math.sin(radian) * radius;
      return point;
   }

   /**
    * Calculate the x position where a horizontal line intercept ellipse
    * @param a x direction radius
    * @param b y direction radius
    * @param y horizontal line y position
    * @return x position of the intercept
    */
   public static double calcXonEllipse(double a, double b, double y) {
      return Math.sqrt((1 - y * y / (b * b)) * a * a);
   }

   /**
    * Find the font that fills the circle with the text.
    */
   public static Font fillCircle(double r, String str, Font font, int maxSize) {
      while(font.getSize() < maxSize) {
         double fw = Common.stringWidth(str, font);
         double fh = Common.getHeight(font);
         double h = getTextHeight(r, fw, fh);

         if(h > fh + 2) {
            font = font.deriveFont(font.getSize() + 1f);
         }
         else {
            break;
         }
      }

      return font;
   }

   // from the equation:
   // (w / 2) ^ 2 + (h / 2) ^ 2 = r ^ 2
   // w / h = fw / fh
   // @fw string font width
   // @fnt string font height
   // @r radius
   // @return height that fills circle
   private static double getTextHeight(double r, double fw, double fh) {
      return Math.sqrt((r * r * fh * fh * 16) / (4 * fw * fw + 4 * fh * fh));
   }

   private static final int MIN_RADIUS = 20;
}
