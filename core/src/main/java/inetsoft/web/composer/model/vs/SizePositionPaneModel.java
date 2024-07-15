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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.awt.*;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SizePositionPaneModel {
   public int getTop() {
      return top;
   }

   public void setTop(int top) {
      this.top = top;
   }

   public int getLeft() {
      return left;
   }

   public void setLeft(int left) {
      this.left = left;
   }

   public int getWidth() {
      return width;
   }

   public void setWidth(int width) {
      this.width = width;
   }

   public int getHeight() {
      return height;
   }

   public void setHeight(int height) {
      this.height = height;
   }

   public int getTitleHeight() {
      return titleHeight;
   }

   public void setTitleHeight(int titleHeight) {
      this.titleHeight = titleHeight;
   }

   public int getCellHeight() {
      return cellHeight;
   }

   public void setCellHeight(int cellHeight) {
      this.cellHeight = cellHeight;
   }

   public boolean isContainer() {
      return container;
   }

   public void setContainer(boolean container) {
      this.container = container;
   }

   public boolean isLocked() {
      return locked;
   }

   public void setLocked(boolean locked) {
      this.locked = locked;
   }

   public void setPositions(Point pos, Dimension size) {
      Objects.requireNonNull(pos, "Assembly position must be non-null");
      Objects.requireNonNull(size, "Assembly size must be non-null");

      left = pos.x;
      top = pos.y;
      width = size.width;
      height = size.height;
   }

   public boolean isScaleVertical() {
      return scaleVertical;
   }

   public void setScaleVertical(boolean scaleVertical) {
      this.scaleVertical = scaleVertical;
   }

   private int top;
   private int left;
   private int width;
   private int height;
   private int titleHeight;
   private int cellHeight;
   private boolean container;
   private boolean locked = false;
   private boolean scaleVertical = false;
}
