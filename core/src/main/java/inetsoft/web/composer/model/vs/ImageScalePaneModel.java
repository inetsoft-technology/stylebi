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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.awt.*;

/**
 * Data transfer object that represents the {@link ImagePropertyDialogModel} for the
 * image property dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableImageScalePaneModel.class)
@JsonDeserialize(as = ImmutableImageScalePaneModel.class)
public abstract class ImageScalePaneModel {
   @Value.Default
   public boolean scaleImageChecked() {
      return false;
   }

   @Value.Default
   public boolean tile() {
      return false;
   }

   @Value.Default
   public boolean maintainAspectRatio() {
      return false;
   }

   @Value.Default
   public int top() {
      return 0;
   }

   @Value.Default
   public int bottom() {
      return 0;
   }

   @Value.Default
   public int left() {
      return 0;
   }

   @Value.Default
   public int right() {
      return 0;
   }

   @Value.Default
   public int objectHeight() {
      return 0;
   }

   @Value.Default
   public int objectWidth() {
      return 0;
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableImageScalePaneModel.Builder {
      public Builder insets(Insets insets) {
         if(insets == null) {
            return top(0).bottom(0).left(0).right(0);
         }
         else {
            return top(insets.top)
               .bottom(insets.bottom)
               .left(insets.left)
               .right(insets.right);
         }
      }

      public Builder size(Dimension size) {
         if(size == null) {
            return objectHeight(0).objectWidth(0);
         }
         else {
            return objectHeight(size.height).objectWidth(size.width);
         }
      }
   }
}
