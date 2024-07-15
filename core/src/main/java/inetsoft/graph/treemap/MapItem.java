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
package inetsoft.graph.treemap;

/**
 * A simple implementation of the Mappable interface.
 */
public class MapItem implements Mappable {
   public void setDepth(int depth) {
      this.depth = depth;
   }

   public int getDepth() {
      return depth;
   }

   public MapItem() {
      this(1, 0);
   }

   public MapItem(double size, int order) {
      this.size = size;
      this.order = order;
      bounds = new Rect();
   }

   public double getSize() {
      return size;
   }

   public void setSize(double size) {
      this.size = size;
   }

   public Rect getBounds() {
      return bounds;
   }

   public void setBounds(Rect bounds) {
      this.bounds = bounds;
   }

   public void setBounds(double x, double y, double w, double h) {
      bounds.setRect(x, y, w, h);
   }

   public int getOrder() {
      return order;
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public Object getShape() {
      return shape;
   }

   public void setShape(Object shape) {
      this.shape = shape;
   }

   private double size;
   private Object shape;
   private Rect bounds;
   private int order = 0;
   private int depth;
}
