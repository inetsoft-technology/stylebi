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

/**
 * Data transfer object that represents the {@link ViewsheetObjectPropertyDialogModel}
 * for the embedded viewsheet property dialog
 */
@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableViewsheetObjectPropertyDialogModel.class)
@JsonDeserialize(as = ImmutableViewsheetObjectPropertyDialogModel.class)
public abstract class ViewsheetObjectPropertyDialogModel {
   @Value.Default
   public GeneralPropPaneModel generalPropPaneModel() {
      return new GeneralPropPaneModel();
   }

   @Value.Default
   public VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel() {
      return VSAssemblyScriptPaneModel.builder().build();
   }

   @Value.Default
   public SizePositionPaneModel sizePositionPaneModel() {
      return new SizePositionPaneModel();
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableViewsheetObjectPropertyDialogModel.Builder {
   }
}