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
package inetsoft.report.internal;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.*;

/**
 * The RectangleRegion class provides operations for rectangle shape region.
 *
 * @version 8.0, 9/22/2005
 * @author InetSoft Technology Corp
 */
public class RectangleRegion implements Region {
   /**
    * Constructor.
    */
   public RectangleRegion() {
   }

   /**
    * Constructor.
    */
   public RectangleRegion(String name, float x, float y, float width, float height) {
      this.name = name;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.bounds = new Rectangle((int) x, (int) y, (int) width, (int) height);
   }

   /**
    * Constructor.
    */
   public RectangleRegion(Rectangle2D rect) {
      this.name = "";
      this.x = (float) rect.getX();
      this.y = (float) rect.getY();
      this.width = (float) rect.getWidth();
      this.height = (float) rect.getHeight();
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
    * Set region name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the class name.
    */
   @Override
   public String getClassName() {
      // @by billh, special case to save space
      return "IRIR";
   }

   /**
    * Create corredsponding shape.
    */
   public Shape createShape() {
      return new Rectangle((int) x, (int) y, (int) width, (int) height);
   }

   /**
    * Return the floating point boundaries for more precise drawing when not
    * serialized directly.
    */
   public Rectangle2D.Double getDoubleBounds() {
      return new Rectangle2D.Double(this.x, this.y, this.width, this.height);
   }

   /**
    * Gets the bounding box of this region.
    */
   @Override
   public Rectangle getBounds() {
      return bounds;
   }

   /**
    * Draw region border on the graphics.
    *
    * @param g the graphics.
    */
   @Override
   public void drawBorder(Graphics g) {
      g.drawRect(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
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
      Common.drawRect(g, x, y, width, height, style);
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
   * Determines whether or not this <code>Rectangle</code> and the specified
   * <code>Rectangle</code> intersect. Two rectangles intersect if
   * their intersection is nonempty.
   *
   * @param r the specified <code>Rectangle</code>
   * @return    <code>true</code> if the specified <code>Rectangle</code>
   *            and this <code>Rectangle</code> intersect;
   *            <code>false</code> otherwise.
   */
   @Override
   public boolean intersects(Rectangle r) {
      float tw = this.width;
      float th = this.height;
      float rw = r.width;
      float rh = r.height;

      if(rw <= 0 || rh <= 0 || tw <= 0 || th <= 0) {
         return false;
      }

      float tx = this.x;
      float ty = this.y;
      float rx = r.x;
      float ry = r.y;
      rw += rx;
      rh += ry;
      tw += tx;
      th += ty;

      return (rw > tx) && (rh > ty) && (tw > rx) && (th > ry);
   }

   public void fill(Graphics g, Color color) {
      g.setColor(color);
      g.fillRect((int) x, (int) y, (int) width, (int) height);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException Exception.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      output.writeUTF(name);
      output.writeFloat(x);
      output.writeFloat(y);
      output.writeFloat(width);
      output.writeFloat(height);
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
      x = Float.parseFloat(Tool.getAttribute(tag, "x"));
      y = Float.parseFloat(Tool.getAttribute(tag, "y"));
      width = Float.parseFloat(Tool.getAttribute(tag, "width"));
      height = Float.parseFloat(Tool.getAttribute(tag, "height"));
      bounds = new Rectangle((int) x, (int) y, (int) width, (int) height);
   }

   public String toString() {
      return "RectangleRegion[" + x + ", " + y + ", " + width + ", " + height +
         "]";
   }

   private String name;
   private float x;
   private float y;
   private float width;
   private float height;
   private Rectangle bounds = null;
}
