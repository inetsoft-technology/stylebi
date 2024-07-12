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
package inetsoft.report.internal;

import java.awt.*;
import java.awt.RenderingHints.Key;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

/**
 * Create a graphics object that does not do anything.
 */
public class EmptyGraphics extends Graphics2D {
   public EmptyGraphics() {
      this(new Rectangle(0, 0, 2000, 2000));
   }

   public EmptyGraphics(Rectangle clip) {
      this.clip = clip;
   }
   
   @Override
   public Graphics create() {
      return this;
   }

   @Override
   public Graphics create(int x, int y, int width, int height) {
      return this;
   }

   @Override
   public void translate(int x, int y) {
   }

   @Override
   public Color getColor() {
      return fg;
   }

   @Override
   public void setColor(Color c) {
   }

   @Override
   public void setPaintMode() {
   }

   @Override
   public void setXORMode(Color c1) {
   }

   @Override
   public Font getFont() {
      return font;
   }

   @Override
   public void setFont(Font font) {
      this.font = font;
   }

   @Override
   public FontMetrics getFontMetrics(Font f) {
      return inetsoft.report.internal.Common.getFontMetrics(font);
   }

   @Override
   public Rectangle getClipBounds() {
      return clip;
   }

   @Override
   public void clipRect(int x, int y, int width, int height) {
   }

   @Override
   public void setClip(int x, int y, int width, int height) {
   }

   @Override
   public Shape getClip() {
      return clip;
   }

   @Override
   public void setClip(Shape clip) {
   }

   @Override
   public void copyArea(int x, int y, int width, int height, int dx, int dy) {
   }

   @Override
   public void drawLine(int x1, int y1, int x2, int y2) {
   }

   @Override
   public void fillRect(int x, int y, int width, int height) {
   }

   @Override
   public void clearRect(int x, int y, int width, int height) {
   }

   @Override
   public void drawRoundRect(int x, int y, int width, int height,
                             int arcWidth, int arcHeight) {
   }

   @Override
   public void fillRoundRect(int x, int y, int width, int height,
                             int arcWidth, int arcHeight) {
   }

   @Override
   public void drawOval(int x, int y, int width, int height) {
   }

   @Override
   public void fillOval(int x, int y, int width, int height) {
   }

   @Override
   public void drawArc(int x, int y, int width, int height,
                       int startAngle, int arcAngle) {
   }

   @Override
   public void fillArc(int x, int y, int width, int height,
                       int startAngle, int arcAngle) {
   }

   @Override
   public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
   }

   @Override
   public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
   }

   @Override
   public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
   }

   @Override
   public void drawString(String str, int x, int y) {
   }

   @Override
   public void drawString(AttributedCharacterIterator iterator, int x, int y) {
   }

   @Override
   public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
      return true;
   }

   @Override
   public boolean drawImage(Image img, int x, int y, int width, int height,
                            ImageObserver observer) {
      return true;
   }

   @Override
   public boolean drawImage(Image img, int x, int y, Color bgcolor,
                            ImageObserver observer) {
      return true;
   }

   @Override
   public boolean drawImage(Image img, int x, int y, int width, int height,
                            Color bgcolor, ImageObserver observer) {
      return true;
   }

   @Override
   public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                            int sx1, int sy1, int sx2, int sy2,
                            ImageObserver observer) {
      return true;
   }

   @Override
   public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                            int sx1, int sy1, int sx2, int sy2, Color bgcolor,
                            ImageObserver observer) {
      return true;
   }

   @Override
   public void dispose() {
   }

   @Override
   public void draw(Shape s) {
   }

   @Override
   public boolean drawImage(Image img, AffineTransform xform,
                            ImageObserver obs) {
      return true;
   }

   @Override
   public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
   }

   @Override
   public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
   }

   @Override
   public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
   }

   @Override
   public void drawString(String s, float x, float y) {
   }

   @Override
   public void drawString(AttributedCharacterIterator iterator,
                          float x, float y) {
   }

   @Override
   public void drawGlyphVector(GlyphVector g, float x, float y) {
   }

   @Override
   public void fill(Shape s) {
   }

   @Override
   public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
      return true;
   }

   @Override
   public GraphicsConfiguration getDeviceConfiguration() {
      return null;
   }

   @Override
   public void setComposite(Composite comp) {
   }

   @Override
   public void setPaint(Paint paint) {
   }

   @Override
   public void setStroke(Stroke s) {
   }

   @Override
   public void setRenderingHint(Key hintKey, Object hintValue) {
   }

   @Override
   public Object getRenderingHint(Key hintKey) {
      return null;
   }

   @Override
   public void setRenderingHints(Map hints) {
   }

   @Override
   public void addRenderingHints(Map hints) {
   }

   @Override
   public RenderingHints getRenderingHints() {
      return null;
   }

   @Override
   public void translate(double tx, double ty) {
   }

   @Override
   public void rotate(double theta) {
   }

   @Override
   public void rotate(double theta, double x, double y) {
   }

   @Override
   public void scale(double sx, double sy) {
   }

   @Override
   public void shear(double shx, double shy) {
   }

   @Override
   public void transform(AffineTransform Tx) {
   }

   @Override
   public void setTransform(AffineTransform Tx) {
   }

   @Override
   public AffineTransform getTransform() {
      return null;
   }

   @Override
   public Paint getPaint() {
      return fg;
   }

   @Override
   public Composite getComposite() {
      return null;
   }

   @Override
   public void setBackground(Color color) {
   }

   @Override
   public Color getBackground() {
      return null;
   }

   @Override
   public Stroke getStroke() {
      return null;
   }

   @Override
   public void clip(Shape s) {
   }

   @Override
   public FontRenderContext getFontRenderContext() {
      return new FontRenderContext(new AffineTransform(), true, true);
   }

   private Color fg = Color.black;
   private Font font = new Font("Dialog", Font.PLAIN, 10);
   private Rectangle clip;
}

