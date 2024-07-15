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
package inetsoft.web.composer.vs.objects.event;

/**
 * Class that encapsulates the parameters for resizing a VSLine object.
 *
 * @since 12.3
 */
public class ResizeVSLineEvent extends VSObjectEvent {
   /**
    * Gets the x position of the starting anchor.
    *
    * @return the position.
    */
   public int getStartLeft() {
      return startLeft;
   }

   /**
    * Sets the x position of the starting anchor.
    *
    * @param startLeft the position.
    */
   public void setStartLeft(int startLeft) {
      this.startLeft = startLeft;
   }

   /**
    * Gets the y position of the starting anchor.
    *
    * @return the position.
    */
   public int getStartTop() {
      return startTop;
   }

   /**
    * Sets the y position of the starting anchor.
    *
    * @param startTop the position.
    */
   public void setStartTop(int startTop) {
      this.startTop = startTop;
   }

   /**
    * Gets the x position of the starting anchor.
    *
    * @return the position.
    */
   public int getEndLeft() {
      return endLeft;
   }

   /**
    * Sets the x position of the ending anchor.
    *
    * @param endLeft the position.
    */
   public void setEndLeft(int endLeft) {
      this.endLeft = endLeft;
   }

   /**
    * Gets the y position of the ending anchor.
    *
    * @return the position.
    */
   public int getEndTop() {
      return endTop;
   }

   /**
    * Sets the y position of the ending anchor.
    *
    * @param endTop the position.
    */
   public void setEndTop(int endTop) {
      this.endTop = endTop;
   }

   /**
    * Gets the width of the line image.
    *
    * @return the position.
    */
   public int getWidth() {
      return width;
   }

   /**
    * Sets the width of the line image.
    *
    * @param width the position.
    */
   public void setWidth(int width) {
      this.width = width;
   }

   /**
    * Gets the height of the line image.
    *
    * @return the position.
    */
   public int getHeight() {
      return height;
   }

   /**
    * Sets the height of the line image.
    *
    * @param height the position.
    */
   public void setHeight(int height) {
      this.height = height;
   }

   /**
    * Gets the x offset of the line image.
    *
    * @return the position.
    */
   public int getOffsetX() {
      return offsetX;
   }

   /**
    * Sets the x offset of the line image.
    *
    * @param offsetX the position.
    */
   public void setOffsetX(int offsetX) {
      this.offsetX = offsetX;
   }

   /**
    * Gets the y offset of the line image.
    *
    * @return the position.
    */
   public int getOffsetY() {
      return offsetY;
   }

   /**
    * Sets the y offset of the line image.
    *
    * @param offsetY the position.
    */
   public void setOffsetY(int offsetY) {
      this.offsetY = offsetY;
   }

   public String getStartAnchorId() { return this.startAnchorId; }

   public void setStartAnchorId(String anchorId) { this.startAnchorId = anchorId; }

   public int getStartAnchorPos() { return this.startAnchorPos; }

   public void setStartAnchorPos(int pos) { this.startAnchorPos = pos; }

   public String getEndAnchorId() { return this.endAnchorId; }

   public void setEndAnchorId(String anchorId) { this.endAnchorId = anchorId; }

   public int getEndAnchorPos() { return this.endAnchorPos; }

   public void setEndAnchorPos(int anchorPos) { this.endAnchorPos = anchorPos; }

   @Override
   public String toString() {
      return "ResizeVSLineEvent{" +
         "name='" + this.getName() + '\'' +
         ", startLeft=" + startLeft +
         ", startEnd=" + startTop +
         ", endLeft=" + endLeft +
         ", endTop=" + endTop +
         ", width=" + width +
         ", height=" + height +
         ", offsetX=" + offsetX +
         ", offsetY=" + offsetY +
         '}';
   }

   private int startLeft;
   private int startTop;
   private int endLeft;
   private int endTop;
   private int width;
   private int height;
   private int offsetX;
   private int offsetY;

   private String startAnchorId;
   private int    startAnchorPos;
   private String endAnchorId;
   private int    endAnchorPos;
}
