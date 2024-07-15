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
package inetsoft.report.internal;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;

/**
 * The PolygonRegion class provides operations for polygon shape region.
 *
 * @version 8.0, 9/22/2005
 * @author InetSoft Technology Corp
 */
public class PolygonRegion implements Region {
   /**
    * Constructor.
    */
   public PolygonRegion() {
   }

   /**
    * Constructor.
    */
   public PolygonRegion(String name, int[] xs, int[] ys, int np, boolean arc) {
      this(xs, ys, np, 1);
      this.name = name;
      this.arc = arc;
   }

   /**
    * Constructor.
    */
   public PolygonRegion(String name, int[] xs, int[] ys, int np, boolean arc, int scaleFactor) {
      this(xs, ys, np, scaleFactor);
      this.name = name;
      this.arc = arc;
   }

   /**
    * Constructor.
    */
   public PolygonRegion(int[] xs, int[] ys, int np) {
      this(xs, ys, np, 1);
   }

   public PolygonRegion(int[] xs, int[] ys, int np, int scaleFactor) {
      this();
      polygon = new ScaledPolygon(xs, ys, np);
      this.scaleFactor = scaleFactor;
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
      return "IRIP";
   }

   /**
    * Create corredsponding shape.
    */
   public Shape createShape() {
      return getOriginalPolygon();
   }

   private Polygon getOriginalPolygon() {
      int[] xpoints = polygon.xpoints;
      int[] ypoints = polygon.ypoints;

      if(scaleFactor != 1) {
         if(xpoints != null) {
            for(int i = 0; i < xpoints.length; i++) {
               xpoints[i] /= scaleFactor;
            }
         }
         if(ypoints != null) {
            for(int i = 0; i < ypoints.length; i++) {
               ypoints[i] /= scaleFactor;
            }
         }
      }
      return new Polygon(xpoints, ypoints, polygon.npoints);
   }

   /**
    * Create corredsponding shape.
    */
   public Shape createScaledShape() {
      return polygon;
   }

   /**
    * Gets the bounding box of this region.
    */
   @Override
   public Rectangle getBounds() {
      return getOriginalPolygon().getBounds();
   }

   /**
    * Draw region border on the graphics.
    *
    * @param g the graphics.
    */
   @Override
   public void drawBorder(Graphics g) {
      Common.drawPolygon(g, getOriginalPolygon());
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
      Common.drawPolygon(g, polygon, style);
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
    * Fill the Polygon.
    * @param g the graphics.
    */
   public void fillPolygon(Graphics g) {
      g.fillPolygon(getOriginalPolygon());
   }

   /**
    * Tests if a specified position is inside the boundary of the region.
    *
    * @param xpos the x coordinate of position.
    * @param ypos the x coordinate of position.
    */
   @Override
   public boolean contains(float xpos, float ypos) {
      return getOriginalPolygon().contains(xpos, ypos);
   }

   /**
    * Tests if the interior of this Polygon intersects the interior of a
    * specified Rectangle.
    *
    * @param rec a specified Rectangle.
    */
   @Override
   public boolean intersects(Rectangle rec) {
      return getOriginalPolygon().intersects(rec);
   }

   /**
    * Check if this is from a Arc or Donut shape.
    */
   public boolean isArc() {
      return arc;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException Exception.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      Polygon polygon = getOriginalPolygon();
      int[] xs = polygon.xpoints;
      int[] ys = polygon.ypoints;
      int n = polygon.npoints;

      StringBuilder xbuf = new StringBuilder();
      StringBuilder ybuf = new StringBuilder();

      for(int i = 0; i < n; i++) {
         xbuf.append(xs[i]);
         xbuf.append(",");

         ybuf.append(ys[i]);
         ybuf.append(",");
      }

      String xstr = xbuf.substring(0, xbuf.length() - 1);
      String ystr = ybuf.substring(0, ybuf.length() - 1);
      output.writeUTF(name);
      byte[] buf = getBytes(xstr);
      output.writeInt(buf.length);
      output.write(buf);
      buf = getBytes(ystr);
      output.writeInt(buf.length);
      output.write(buf);
      output.writeInt(n);
   }

   /**
    * Convert to bytes to avoid 65535 limit.
    */
   private byte[] getBytes(String str) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      PrintWriter writer =
         new PrintWriter(new OutputStreamWriter(out, "utf-8"));
      writer.print(str);
      writer.flush();
      return out.toByteArray();
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
      Polygon polygon = getOriginalPolygon();
      int[] xs = polygon.xpoints;
      int[] ys = polygon.ypoints;
      int n = polygon.npoints;

      StringBuilder xbuf = new StringBuilder();
      StringBuilder ybuf = new StringBuilder();

      for(int i = 0; i < n; i++) {
         xbuf.append(xs[i]);
         xbuf.append(",");

         ybuf.append(ys[i]);
         ybuf.append(",");
      }

      String xstr = xbuf.substring(0, xbuf.length() - 1);
      String ystr = ybuf.substring(0, ybuf.length() - 1);

      writer.println("<region name=\"" + name + "\" ");
      writer.println("class=\"" + this.getClass().getName() + "\" ");
      writer.println("xs=\"" + xstr + "\" ys=\"" + ystr + "\" ");
      writer.println("n=\"" + n + "\"/>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      name = Tool.getAttribute(tag, "name");
      String xstr = Tool.getAttribute(tag, "xs");
      String ystr = Tool.getAttribute(tag, "ys");
      int np = Integer.parseInt(Tool.getAttribute(tag, "np"));

      String[] xsstr = Tool.split(xstr, ',');
      String[] ysstr = Tool.split(ystr, ',');

      int[] xs = new int[np];
      int[] ys = new int[np];

      for(int i = 0; i < np; i++) {
         xs[i] = Integer.parseInt(xsstr[i]);
         ys[i] = Integer.parseInt(ysstr[i]);
      }

      polygon = new Polygon(xs, ys, np);
   }

   private String name;
   private Polygon polygon = null;
   private boolean arc = false;
   private int scaleFactor = 1;

   public class ScaledPolygon extends Polygon {
      public ScaledPolygon(int[] xpoints, int[] ypoints, int npoints) {
         super(xpoints, ypoints, npoints);
      }
      public int getScaleFactor() {
         return scaleFactor;
      }

   }
}

