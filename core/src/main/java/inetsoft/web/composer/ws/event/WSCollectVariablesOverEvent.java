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
package inetsoft.web.composer.ws.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

@Value.Immutable
@Serial.Structural
@JsonDeserialize(builder = WSCollectVariablesOverEvent.Builder.class)
public abstract class WSCollectVariablesOverEvent {
   public abstract String[][] values();

   public abstract String[] types();

   public abstract String[] names();

   public abstract boolean[] userSelected();

   public abstract boolean[] usedInOneOf();

   @Value.Default
   public boolean refreshColumns() {
      return false;
   }

   @Value.Default
   public boolean initial() {
      return false;
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableWSCollectVariablesOverEvent.Builder {
   }
}
