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

import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;

/**
 * The AreaRegion class provides operations for area shape region.
 *
 * @version 13.2, 4/15/2020
 * @author InetSoft Technology Corp
 */
public class AreaRegion implements Region {
   /**
    * Constructor.
    */
   public AreaRegion() {
   }

   /**
    * Constructor.
    */
   public AreaRegion(Shape area) {
      this.area = area;
   }

   @Override
   public String getName() {
      return name;
   }

   public Shape getArea() {
      return area;
   }

   @Override
   public String getClassName() {
      return "AR";
   }

   @Override
   public Shape createShape() {
      return area;
   }

   @Override
   public Rectangle getBounds() {
      return area.getBounds();
   }

   @Override
   public void drawBorder(Graphics g) {
      drawBorder(g, 0);
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
      ((Graphics2D) g).draw(area);
   }

   @Override
   public void drawBorder(Graphics g, Color color, int style) {
      Color old = g.getColor();
      g.setColor(color);
      drawBorder(g, style);
      g.setColor(old);
   }

   @Override
   public boolean contains(float xpos, float ypos) {
      return area.contains(xpos, ypos);
   }

   @Override
   public boolean intersects(Rectangle rec) {
      return area.intersects(rec);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException Exception.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      // not needed
   }

   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      // not needed
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      // not needed
   }

   private String name;
   private Shape area = null;
}
