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
package inetsoft.web.admin.server;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@Value.Modifiable
@JsonSerialize(as = ImmutableServerUsage.class)
@JsonDeserialize(as = ImmutableServerUsage.class)
public interface ServerUsage {
   long timestamp();
   String host();

   @Value.Default
   default float cpuUsage() {
      return 0F;
   }

   @Value.Default
   default long memoryUsage() {
      return 0L;
   }

   @Value.Default
   default long gcCount() {
      return 0L;
   }

   @Value.Default
   default long gcTime() {
      return 0L;
   }

   @Value.Default
   default int executingViewsheets() {
      return 0;
   }

   @Value.Default
   default int executingQueries() {
      return 0;
   }

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableServerUsage.Builder {
   }
}
