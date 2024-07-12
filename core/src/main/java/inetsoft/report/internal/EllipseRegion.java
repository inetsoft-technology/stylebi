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

import inetsoft.util.DEllipse2D;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;

/**
 * The EllipseRegion class provides operations for ellipse shape region.
 *
 * @version 8.0, 9/22/2005
 * @author InetSoft Technology Corp
 */
public class EllipseRegion implements Region {
   /**
    * Constructor.
    */
   public EllipseRegion() {
   }

   /**
    * Constructor.
    */
   public EllipseRegion(String name, double x, double y, double width, double height) {
      this.name = name;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.bounds = new Rectangle((int) x, (int) y, (int) width, (int) height);
   }

   /**
    * Get region name.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Get the class name.
    */
   @Override
   public String getClassName() {
      // @by billh, special case to save space
      return "IRIE";
   }

   /**
    * Gets the bounding box of this region.
    */
   @Override
   public Rectangle getBounds() {
      return bounds;
   }

   /**
    * Create corredsponding shape.
    */
   public Shape createShape() {
      return new DEllipse2D(x, y, width, height);
   }

   /**
    * Draw region border on the graphics.
    *
    * @param g the graphics.
    */
   @Override
   public void drawBorder(Graphics g) {
      Common.drawOval(g, (float) x, (float) y, (float) width, (float) height);
   }

   /**
    * Draw region border with the specified color on the graphics.
    *
    * @param g the graphics.
    * @param color the specified color.
    */
   @Override
   public void drawBorder(Graphics g, Color color) {
      Color old = g.getColor();
      g.setColor(color);
      drawBorder(g);
      g.setColor(old);
   }

   /**
    * Draw region border with the specified line on the graphics.
    *
    * @param g the graphics.
    * @param style the specified line style.
    */
   @Override
   public void drawBorder(Graphics g, int style) {
      Common.drawOval(g, (float) x, (float) y, (float) width, (float) height,
                      style);
   }

   /**
    * Draw region border with the specified on the graphics.
    *
    * @param g the graphics.
    * @param color the specified color.
    * @param style the specified line style.
    */
   @Override
   public void drawBorder(Graphics g, Color color, int style) {
      Color old = g.getColor();
      g.setColor(color);
      drawBorder(g, style);
      g.setColor(old);
   }

   /**
    * Tests if a specified position is inside the boundary of the region.
    *
    * @param xpos the x coordinate of position.
    * @param ypos the x coordinate of position.
    */
   @Override
   public boolean contains(float xpos, float ypos) {
      return xpos >= x && ypos >= y && xpos < x + width && ypos < y + height;
   }

   /**
    * Tests if a specified rectangle is intersects the boundary of the region.
    *
    * @param rectangle the specified rectangle.
    */
   @Override
   public boolean intersects(Rectangle rectangle) {
      double x = rectangle.x;
      double y = rectangle.y;
      double w = rectangle.width;
      double h = rectangle.height;

      if(w <= 0 || h <= 0) {
         return false;
      }

      // Normalize the rectangular coordinates compared to the ellipse
      // having a center at 0,0 and a radius of 0.5.
      double ellw = this.width;
      double ellh = this.height;

      if(ellw <= 0 || ellh <= 0) {
         return false;
      }

      double normx0 = (x - this.x) / ellw - 0.5;
      double normx1 = normx0 + w / ellw;
      double normy0 = (y - this.y) / ellh - 0.5;
      double normy1 = normy0 + h / ellh;

      // find nearest x (left edge, right edge, 0.0)
      // find nearest y (top edge, bottom edge, 0.0)
      // if nearest x,y is inside circle of radius 0.5, then intersects
      double nearx, neary;

      if(normx0 > 0) {
         // center to left of X extents
         nearx = normx0;
      }
      else if(normx1 < 0) {
         // center to right of X extents
         nearx = normx1;
      }
      else {
         nearx = 0;
      }

      if(normy0 > 0) {
         // center above Y extents
         neary = normy0;
      }
      else if(normy1 < 0) {
         // center below Y extents
         neary = normy1;
      }
      else {
         neary = 0;
      }

      return (nearx * nearx + neary * neary) < 0.25;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<region name=\"" + name + "\" ");
      writer.println("class=\"" + this.getClass().getName() + "\" ");
      writer.println("x=\"" + x + "\" y=\"" + y + "\"");
      writer.println("width=\"" + width + "\" height=\"" + height + "\"/>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      name = Tool.getAttribute(tag, "name");
      x = Double.parseDouble(Tool.getAttribute(tag, "x"));
      y = Double.parseDouble(Tool.getAttribute(tag, "y"));
      width = Double.parseDouble(Tool.getAttribute(tag, "width"));
      height = Double.parseDouble(Tool.getAttribute(tag, "height"));
      bounds = new Rectangle((int) x, (int) y, (int) width, (int) height);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException Exception.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      output.writeUTF(name);
      output.writeDouble(x);
      output.writeDouble(y);
      output.writeDouble(width);
      output.writeDouble(height);
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }

   private String name;
   private double x;
   private double y;
   private double width;
   private double height;
   private Rectangle bounds = null;
}
