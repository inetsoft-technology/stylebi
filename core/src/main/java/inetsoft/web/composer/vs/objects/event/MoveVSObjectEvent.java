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

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * Class that encapsulates the parameters for moving an object.
 *
 * @since 12.3
 */
public class MoveVSObjectEvent extends VSObjectEvent implements Serializable {
   /**
    * Gets the x offset of the object.
    *
    * @return the x offset of the object.
    */
   public int getxOffset() {
      return xOffset;
   }

   /**
    * Sets the x offset of the object.
    *
    * @param xOffset the xOffset of the object.
    */
   public void setxOffset(int xOffset) {
      this.xOffset = xOffset;
   }

   /**
    * Gets the y offset of the object.
    *
    * @return the y offset of the object.
    */
   public int getyOffset() {
      return yOffset;
   }

   /**
    * Sets the yOffset of the object.
    *
    * @param yOffset the yOffset of the object.
    */
   public void setyOffset(int yOffset) {
      this.yOffset = yOffset;
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

   /**
    * Gets the row count of the viewsheet wizard gird.
    * @return the row count
    */
   public int getWizardGridRows() {
      return wizardGridRows;
   }

   /**
    * Sets the row count of the viewsheet wizard gird.
    * @param wizardGridRows
    */
   @Nullable
   public void setWizardGridRows(int wizardGridRows) {
      this.wizardGridRows = wizardGridRows;
   }

   /**
    * Gets the col count of the viewsheet wizard gird.
    * @return the row count
    */
   public int getWizardGridCols() {
      return wizardGridCols;
   }

   /**
    * Sets the col count of the viewsheet wizard gird.
    * @param wizardGridCols
    */
   @Nullable
   public void setWizardGridCols(int wizardGridCols) {
      this.wizardGridCols = wizardGridCols;
   }

   /**
    * for vs wizard. weather auto layout in horizontal.
    */
   public boolean isAutoLayoutHorizontal() {
      return autoLayoutHorizontal;
   }

   /**
    * for vs wizard. set auto layout in horizontal.
    * @param autoLayoutHorizontal
    */
   @Nullable
   public void setAutoLayoutHorizontal(boolean autoLayoutHorizontal) {
      this.autoLayoutHorizontal = autoLayoutHorizontal;
   }

   /**
    * for vs wizard. weather move all row or col.
    */
   public boolean isMoveRowOrCol() {
      return moveRowOrCol;
   }

   /**
    * for vs wizard. set move all row or col.
    * @param moveRowOrCol
    */
   @Nullable
   public void setMoveRowOrCol(boolean moveRowOrCol) {
      this.moveRowOrCol = moveRowOrCol;
   }

   @Override
   public String toString() {
      return "MoveVSObjectEvent{" +
         "name='" + this.getName() + '\'' +
         ", xOffset=" + xOffset +
         ", yOffset=" + yOffset +
         ", scale=" + scale +
         '}';
   }

   private int xOffset;
   private int yOffset;
   private float scale;
   private int wizardGridRows;
   private int wizardGridCols;
   private boolean autoLayoutHorizontal;
   private boolean moveRowOrCol;
}
