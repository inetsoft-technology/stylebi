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

import inetsoft.uql.asset.AssetEntry;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * Class that encapsulates the parameters for adding a new viewsheet object.
 *
 * @since 12.3
 */
public class AddNewVSObjectEvent implements Serializable {
   /**
    * Gets the type of object to create.
    *
    * @return the type of object.
    */
   public int getType() {
      return type;
   }

   /**
    * Sets the type of object to create.
    *
    * @param type the type of object.
    */
   public void setType(int type) {
      this.type = type;
   }

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
    * @param xOffset the x offset of the object.
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
    * Sets the y Offset of the object.
    *
    * @param yOffset the y offset of the object.
    */
   public void setyOffset(int yOffset) {
      this.yOffset = yOffset;
   }

   /**
    * Gets the asset entry associated with viewsheet to embed.
    *
    * @return the asset entry of embedded viewsheet.
    */
   public AssetEntry getEntry() {
      return entry;
   }

   /**
    * Sets the asset entry associated with viewsheet to embed.
    *
    * @param entry the asset entry of embedded viewsheet.
    */
   public void setEntry(AssetEntry entry) {
      this.entry = entry;
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
    * Gets whether to force edit mode.
    *
    * @return whether to force edit mode.
    */
   public boolean isForceEditMode() {
      return forceEditMode;
   }

   /**
    * Sets whether to force edit mode on creation.
    *
    * @param forceEditMode whether to force edit mode
    */
   public void setForceEditMode(boolean forceEditMode) {
      this.forceEditMode = forceEditMode;
   }

   /**
    * get the grid row of vs-wizard-pane.
    */
   public int getWizardCurrentGridRow() {
      return wizardCurrentGridRow;
   }

   /**
    * Set the grid row of vs-wizard-pane.
    */
   @Nullable
   public void setWizardCurrentGridRow(int wizardCurrentGridRow) {
      this.wizardCurrentGridRow = wizardCurrentGridRow;
   }

   /**
    * get the grid col of vs-wizard-pane.
    */
   public int getWizardCurrentGridCol() {
      return wizardCurrentGridCol;
   }

   /**
    * Set the grid col of vs-wizard-pane.
    */
   @Nullable
   public void setWizardCurrentGridCol(int wizardCurrentGridCol) {
      this.wizardCurrentGridCol = wizardCurrentGridCol;
   }

   @Override
   public String toString() {
      return "AddNewVSObjectEvent{" +
         "type='" + type + '\'' +
         ", xOffset=" + xOffset +
         ", yOffset=" + yOffset +
         ", scale=" + scale +
         ", entry=" + entry +
         '}';
   }

   private int type;
   private int xOffset;
   private int yOffset;
   private float scale;
   private AssetEntry entry;
   private boolean forceEditMode;
   private int wizardCurrentGridRow;
   private int wizardCurrentGridCol;
}
