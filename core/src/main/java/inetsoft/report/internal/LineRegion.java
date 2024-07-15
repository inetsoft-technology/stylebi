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

import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Line2D;
import java.io.*;

/**
 * A single line region.
 *
 * @version 13.4, 4/12/2022
 * @author InetSoft Technology Corp
 */
public class LineRegion implements Region {
   public LineRegion() {
   }

   public LineRegion(String name, Line2D line) {
      this.name = name;
      this.line = line;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public String getClassName() {
      return "L";
   }

   @Override
   public Shape createShape() {
      return line;
   }

   @Override
   public Rectangle getBounds() {
      return line.getBounds();
   }

   @Override
   public void drawBorder(Graphics g) {
      ((Graphics2D) g).draw(line);
   }

   @Override
   public void drawBorder(Graphics g, Color color) {
      Color old = g.getColor();
      g.setColor(color);
      drawBorder(g);
      g.setColor(old);
   }

   @Override
   public void drawBorder(Graphics g, int style) {
      ((Graphics2D) g).draw(line);
   }

   @Override
   public void drawBorder(Graphics g, Color color, int style) {
      Color old = g.getColor();
      g.setColor(color);
      drawBorder(g, style);
      g.setColor(old);
   }

   public void fillPolygon(Graphics g) {
      ((Graphics2D) g).draw(line);
   }

   @Override
   public boolean contains(float xpos, float ypos) {
      return line.contains(xpos, ypos);
   }

   @Override
   public boolean intersects(Rectangle rec) {
      return line.intersects(rec);
   }

   @Override
   public void writeData(DataOutputStream output) throws IOException {
   }

   @Override
   public boolean parseData(DataInputStream input) {
      return false;
   }

   @Override
   public void writeXML(PrintWriter writer) {
   }

   @Override
   public void parseXML(Element tag) throws Exception {
   }

   private static final long serialVersionUID = 1L;

   private String name;
   private Line2D line;
}
