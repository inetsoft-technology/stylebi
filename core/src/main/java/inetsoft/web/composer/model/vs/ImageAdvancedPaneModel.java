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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo.PopLocation;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link ImagePropertyDialogModel} for the
 * image property dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableImageAdvancedPaneModel.class)
@JsonDeserialize(as = ImmutableImageAdvancedPaneModel.class)
public abstract class ImageAdvancedPaneModel {
   @Nullable
   public abstract String popComponent();

   @Nullable
   public abstract String alpha();

   @Nullable
   public abstract String[] popComponents();

   @Nullable
   public abstract PopLocation popLocation();

   @Value.Default
   public DynamicImagePaneModel dynamicImagePaneModel() {
      return ImmutableDynamicImagePaneModel.builder().build();
   }

   @Value.Default
   public ImageScalePaneModel imageScalePaneModel() {
      return ImmutableImageScalePaneModel.builder().build();
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableImageAdvancedPaneModel.Builder {
   }
}
