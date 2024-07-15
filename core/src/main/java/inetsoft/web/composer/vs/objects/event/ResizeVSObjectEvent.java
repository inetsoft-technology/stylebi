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
package inetsoft.web.composer.vs.objects.event;

/**
 * Class that encapsulates the parameters for resizing an object.
 *
 * @since 12.3
 */
public class ResizeVSObjectEvent extends MoveVSObjectEvent {
   /**
    * Gets the width of the object.
    *
    * @return the width of the object.
    */
   public int getWidth() {
      return width;
   }

   /**
    * Sets the width of the object.
    *
    * @param width the width of the object.
    */
   public void setWidth(int width) {
      this.width = width;
   }

   /**
    * Gets the height of the object.
    *
    * @return the height of the object.
    */
   public int getHeight() {
      return height;
   }

   /**
    * Sets the height of the object.
    *
    * @param height the height of the object.
    */
   public void setHeight(int height) {
      this.height = height;
   }

   /**
    * Gets the viewsheet scale.
    *
    * @return the current scale.
    */
   public float getScale() {
      return scale;
   }

   /**
    * Sets the viewsheet scale.
    *
    * @param scale the current scale.
    */
   public void setScale(float scale) {
      this.scale = scale;
   }

   @Override
   public String toString() {
      return "ResizeVSObjectEvent{" +
         "name='" + this.getName() + '\'' +
         ", width=" + width +
         ", height=" + height +
         ", scale=" + scale +
         '}';
   }

   private int width;
   private int height;
   private float scale;
}
