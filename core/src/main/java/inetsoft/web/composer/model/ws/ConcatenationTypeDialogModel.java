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
package inetsoft.web.composer.model.ws;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableConcatenationTypeDialogModel.class)
@JsonDeserialize(builder = ConcatenationTypeDialogModel.Builder.class)
public abstract class ConcatenationTypeDialogModel {
   public abstract String concatenatedTableName();

   public abstract String leftTableName();

   public abstract String rightTableName();

   @Value.Default
   public boolean all() {
      return false;
   }

   public abstract TableAssemblyOperatorModel operator();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableConcatenationTypeDialogModel.Builder {
   }
}