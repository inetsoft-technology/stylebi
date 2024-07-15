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
package inetsoft.report.io.rtf;

import inetsoft.report.pdf.PDFDevice;

import java.awt.*;
import java.awt.RenderingHints.Key;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.List;
import java.util.*;

/**
 * RichTextGraphics can be used to get the RichText info.
 *
 * @version 10.2, 7/21/2009
 * @author InetSoft Technology Corp
 */
public class RichTextGraphics extends Graphics2D {
   /**
    * Creates a new RichTextGraphics.
    */
   public RichTextGraphics(Graphics2D g2D) {
      g = g2D;
      richTextList = new ArrayList();
   }

   /**
    * Get the richText List.
    */
   public List getRichTexts() {
      return richTextList;
   }

   @Override
   public void rotate(double theta) {
      g.rotate(theta);
   }

   @Override
   public void scale(double sx, double sy) {
      g.scale(sx, sy);
   }

   @Override
   public void shear(double shx, double shy) {
      g.shear(shx, shy);
   }

   @Override
   public void translate(double tx, double ty) {
      g.translate(tx, ty);
   }

   @Override
   public void rotate(double theta, double x, double y) {
      g.rotate(theta, x, y);
   }

   @Override
   public void translate(int x, int y) {
      g.translate(x, y);
   }

   @Override
   public Color getBackground() {
      return g.getBackground();
   }

   @Override
   public void setBackground(Color c) {
      g.setBackground(c);
   }

   @Override
   public Composite getComposite() {
      return g.getComposite();
   }

   @Override
   public void setComposite(Composite comp) {
      g.setComposite(comp);
   }

   @Override
   public GraphicsConfiguration getDeviceConfiguration() {
      return g.getDeviceConfiguration();
   }

   @Override
   public Paint getPaint() {
      return g.getPaint();
   }

   @Override
   public void setPaint(Paint paint) {
      g.setPaint(paint);
   }

   @Override
   public RenderingHints getRenderingHints() {
      return g.getRenderingHints();
   }

   @Override
   public void clip(Shape s) {
      g.clip(s);
   }

   @Override
   public void draw(Shape s) {
      g.draw(s);
   }

   @Override
   public void fill(Shape s) {
      g.fill(s);
   }

   @Override
   public Stroke getStroke() {
      return g.getStroke();
   }

   @Override
   public void setStroke(Stroke s) {
      g.setStroke(s);
   }

   @Override
   public FontRenderContext getFontRenderContext() {
      return g.getFontRenderContext();
   }

   @Override
   public void drawGlyphVector(GlyphVector gv, float x, float y) {
      g.drawGlyphVector(gv, x, y);
   }

   @Override
   public AffineTransform getTransform() {
      return g.getTransform();
   }

   @Override
   public void setTransform(AffineTransform Tx) {
      g.setTransform(Tx);
   }

   @Override
   public void transform(AffineTransform Tx) {
      g.transform(Tx);
   }

   @Override
   public void drawString(String s, float x, float y) {
      g.drawString(s, x, y);
      RichText richText = new RichText(g, s, x, y);
      richTextList.add(richText);
   }

   /**
    * Draws the specified String using the current font and color.
    * The x,y position is the starting point of the baseline of the String.
    * @param s text to draw
    * @param x x position
    * @param y y position
    */
   @Override
   public void drawString(String s, int x, int y) {
      g.drawString(s, x, y);
      RichText richText = new RichText(g, s, x, y);
      richTextList.add(richText);
   }

   @Override
   public void drawString(AttributedCharacterIterator iterator,
                          float x, float y) {
      g.drawString(iterator, x, y);
   }

   @Override
   public void drawString(AttributedCharacterIterator iterator, int x, int y) {
      g.drawString(iterator, x, y);
   }

   @Override
   public void addRenderingHints(Map hints) {
      g.addRenderingHints(hints);
   }

   @Override
   public void setRenderingHints(Map hints) {
      g.setRenderingHints(hints);
   }

   @Override
   public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
      return g.hit(rect, s, onStroke);
   }

   @Override
   public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
      g.drawRenderedImage(img, xform);
   }

   @Override
   public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
      g.drawRenderableImage(img, xform);
   }

   @Override
   public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
      g.drawImage(img, op, x, y);
   }

   @Override
   public Object getRenderingHint(Key hintKey) {
      return g.getRenderingHint(hintKey);
   }

   @Override
   public void setRenderingHint(Key hintKey, Object hintValue) {
      g.setRenderingHint(hintKey, hintValue);
   }

   @Override
   public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs)
   {
      return g.drawImage(img, xform, obs);
   }

   @Override
   public void dispose() {
      g.dispose();
   }

   @Override
   public void setPaintMode() {
      g.setPaintMode();
   }

   @Override
   public void clearRect(int x, int y, int width, int height) {
      g.clearRect(x, y, width, height);
   }

   @Override
   public void clipRect(int x, int y, int width, int height) {
      g.clipRect(x, y, width, height);
   }

   @Override
   public void drawLine(int x1, int y1, int x2, int y2) {
      g.drawLine(x1, y1, x2, y2);
   }

   @Override
   public void drawOval(int x, int y, int width, int height) {
      g.drawOval(x, y, width, height);
   }

   @Override
   public void fillOval(int x, int y, int width, int height) {
      g.fillOval(x, y, width, height);
   }

   @Override
   public void fillRect(int x, int y, int width, int height) {
      g.fillRect(x, y, width, height);
   }

   @Override
   public void setClip(int x, int y, int width, int height) {
      g.setClip(x, y, width, height);
   }

   @Override
   public void copyArea(int x, int y, int width, int height, int dx, int dy) {
      g.copyArea(x, y, width, height, dx, dy);
   }

   @Override
   public void drawArc(int x, int y, int width, int height, int startAngle,
                       int arcAngle) {
      g.drawArc(x, y, width, height, startAngle, arcAngle);
   }

   @Override
   public void drawRoundRect(int x, int y, int width, int height, int arcWidth,
                             int arcHeight) {
      g.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
   }

   @Override
   public void fillArc(int x, int y, int width, int height, int startAngle,
                       int arcAngle) {
      g.fillArc(x, y, width, height, startAngle, arcAngle);
   }

   @Override
   public void fillRoundRect(int x, int y, int width, int height, int arcWidth,
                             int arcHeight) {
      g.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
   }

   @Override
   public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
      g.drawPolygon(xPoints, yPoints, nPoints);
   }

   @Override
   public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
      g.drawPolyline(xPoints, yPoints, nPoints);
   }

   @Override
   public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
      g.fillPolygon(xPoints, yPoints, nPoints);
   }

   @Override
   public Color getColor() {
      return g.getColor();
   }

   @Override
   public void setColor(Color c) {
      g.setColor(c);
   }

   @Override
   public void setXORMode(Color c1) {
      g.setXORMode(c1);
   }

   @Override
   public Font getFont() {
      return g.getFont();
   }

   /**
    * Set the text font.
    * @param font the text's font
    */
   @Override
   public void setFont(Font font) {
      if(!(font instanceof RichTextFont) &&
         (g.getFont() instanceof RichTextFont))
      {
         g.setFont(g.getFont());
      }
      else {
         g.setFont(font);
      }
   }

   @Override
   public Graphics create() {
      return g.create();
   }

   @Override
   public Rectangle getClipBounds() {
      return g.getClipBounds();
   }

   @Override
   public Shape getClip() {
      return g.getClip();
   }

   @Override
   public void setClip(Shape clip) {
      g.setClip(clip);
   }

   @Override
   public FontMetrics getFontMetrics(Font f) {
      return g.getFontMetrics(f);
   }

   @Override
   public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                            int sx1, int sy1, int sx2, int sy2,
                            ImageObserver observer) {
      return g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
   }

   @Override
   public boolean drawImage(Image img, int x, int y, int width, int height,
                            ImageObserver observer) {
      return g.drawImage(img, x, y, width, height, observer);
   }

   @Override
   public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
      return g.drawImage(img, x, y, observer);
   }

   @Override
   public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                            int sx1, int sy1, int sx2, int sy2, Color bgcolor,
                            ImageObserver observer) {
      return g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1,
                       sx2, sy2, bgcolor, observer);
   }

   @Override
   public boolean drawImage(Image img, int x, int y, int width, int height,
                            Color bgcolor, ImageObserver observer) {
      return g.drawImage(img, x, y, width, height, bgcolor, observer);
   }

   @Override
   public boolean drawImage(Image img, int x, int y, Color bgcolor,
                            ImageObserver observer) {
      return g.drawImage(img, x, y, bgcolor, observer);
   }

   @Override
   public void finalize() {
      // pdf printer's dispose cause a new page to be added to output
      if(g instanceof PDFDevice) {
         return;
      }

      super.finalize();
   }

   private Graphics2D g;
   private List richTextList;
}
