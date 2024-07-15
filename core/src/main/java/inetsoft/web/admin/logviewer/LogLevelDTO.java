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
package inetsoft.web.admin.logviewer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.log.LogLevelSetting;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableLogLevelDTO.class)
@JsonDeserialize(as = ImmutableLogLevelDTO.class)
public interface LogLevelDTO {
   String context();

   String name();

   String level();

   static Builder builder() {
      return new LogLevelDTO.Builder();
   }

   final class Builder extends ImmutableLogLevelDTO.Builder {
      public LogLevelDTO from(LogLevelSetting setting) {
         return context(setting.getContext().name())
            .name(setting.getName())
            .level(setting.getLevel().level())
            .build();
      }
   }
}
