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
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableLogSettingsModel.class)
@JsonDeserialize(as = ImmutableLogSettingsModel.class)
public interface LogSettingsModel {
   @Value.Default
   default String provider() {
      return "file";
   }

   @Nullable
   FileLogSettingsModel fileSettings();

   @Nullable
   FluentdLogSettingsModel fluentdSettings();

   boolean outputToStd();

   @Nullable
   String detailLevel();

   @Nullable
   List<LogLevelDTO> logLevels();

   static Builder builder() {
      return new LogSettingsModel.Builder();
   }

   final class Builder extends ImmutableLogSettingsModel.Builder {
   }
}
