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
package inetsoft.web.viewsheet.model.dialog;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

/**
 * Data transfer object that represents the {@link EmailDialogModel}
 */
@Value.Immutable
@JsonSerialize(as = ImmutableEmailDialogModel.class)
@JsonDeserialize(as = ImmutableEmailDialogModel.class)
@Serial.Structural
public interface EmailDialogModel {
   @Value.Default
   default boolean historyEnabled() {
      return false;
   }
   
   @Value.Default
   default FileFormatPaneModel fileFormatPaneModel() {
      return ImmutableFileFormatPaneModel.builder().build();
   }

   @Value.Default
   default EmailPaneModel emailPaneModel() {
      return EmailPaneModel.builder().build();
   }

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableEmailDialogModel.Builder {
   }
}
