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
import org.immutables.serial.Serial;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link TextPropertyDialogModel} for the
 * text property dialog
 */
@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableDynamicImagePaneModel.class)
@JsonDeserialize(as = ImmutableDynamicImagePaneModel.class)
public abstract class DynamicImagePaneModel {
   @Value.Default
   public boolean dynamicImageSelected() {
      return false;
   }

   @Nullable
   public abstract String dynamicImageValue();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableDynamicImagePaneModel.Builder {
   }
}