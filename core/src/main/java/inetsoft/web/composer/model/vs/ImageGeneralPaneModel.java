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

/**
 * Data transfer object that represents the {@link ImagePropertyDialogModel} for the
 * image property dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableImageGeneralPaneModel.class)
@JsonDeserialize(as = ImmutableImageGeneralPaneModel.class)
public abstract class ImageGeneralPaneModel {
   @Value.Default
   public OutputGeneralPaneModel outputGeneralPaneModel() {
      return new OutputGeneralPaneModel();
   }

   @Value.Default
   public StaticImagePaneModel staticImagePaneModel() {
      return ImmutableStaticImagePaneModel.builder().build();
   }

   @Value.Default
   public TipPaneModel tipPaneModel() {
      return new TipPaneModel();
   }

   @Value.Default
   public SizePositionPaneModel sizePositionPaneModel() {
      return new SizePositionPaneModel();
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableImageGeneralPaneModel.Builder {
   }
}
