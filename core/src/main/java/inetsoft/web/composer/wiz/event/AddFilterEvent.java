/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.composer.wiz.event;

import java.io.Serializable;

/**
 * Event sent when a filter tree node is dropped onto the viewsheet pane.
 */
public class AddFilterEvent implements Serializable {
   /**
    * The data of the dragged filter node (e.g. field/column identifier).
    */
   public Object getEntry() {
      return entry;
   }

   public void setEntry(Object entry) {
      this.entry = entry;
   }

   public int getxOffset() {
      return xOffset;
   }

   public void setxOffset(int xOffset) {
      this.xOffset = xOffset;
   }

   public int getyOffset() {
      return yOffset;
   }

   public void setyOffset(int yOffset) {
      this.yOffset = yOffset;
   }

   public float getScale() {
      return scale;
   }

   public void setScale(float scale) {
      this.scale = scale;
   }

   private Object entry;
   private int xOffset;
   private int yOffset;
   private float scale;
}
