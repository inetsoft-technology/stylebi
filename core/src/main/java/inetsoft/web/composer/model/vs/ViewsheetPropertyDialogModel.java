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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableViewsheetPropertyDialogModel.class)
@JsonDeserialize(as = ImmutableViewsheetPropertyDialogModel.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface ViewsheetPropertyDialogModel {
   @Nullable String id();

   @Value.Default
   default VSOptionsPaneModel vsOptionsPane() {
      return new VSOptionsPaneModel();
   }

   @Value.Default
   default FiltersPaneModel filtersPane() {
      return new FiltersPaneModel();
   }

   @Value.Default
   default ScreensPaneModel screensPane() {
      return new ScreensPaneModel();
   }

   @Nullable LocalizationPaneModel localizationPane();

   @Value.Default
   default VSScriptPaneModel vsScriptPane() {
      return VSScriptPaneModel.builder().build();
   }

   @Value.Default
   default int width() {
      return 0;
   }

   @Value.Default
   default int height() {
      return 0;
   }

   @Value.Default
   default boolean preview() {
      return false;
   };

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableViewsheetPropertyDialogModel.Builder {
   }
}
