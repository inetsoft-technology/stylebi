/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.model.dialog;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.report.StyleConstants;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableAnnotationFormatDialogModel.class)
@JsonDeserialize(as = ImmutableAnnotationFormatDialogModel.class)
public interface AnnotationFormatDialogModel {
   // rectangle border style
   String getBoxBorderStyle();

   // rectangle border color
   @Nullable
   String getBoxBorderColor();

   // round corner value
   int getBoxBorderRadius();

   // rectangle background color
   @Nullable
   String getBoxFillColor();

   int getBoxAlpha();

   @Value.Default
   default boolean getLineVisible() {
      return false;
   }

   @Nullable
   String getLineStyle();

   @Value.Default
   default int getLineEnd() {
      return StyleConstants.ARROW_LINE_1;
   }

   @Nullable
   String getLineColor();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableAnnotationFormatDialogModel.Builder {
   }
}
