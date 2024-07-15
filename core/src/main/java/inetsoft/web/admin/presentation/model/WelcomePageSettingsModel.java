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
package inetsoft.web.admin.presentation.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableWelcomePageSettingsModel.class)
@JsonDeserialize(as = ImmutableWelcomePageSettingsModel.class)
public interface WelcomePageSettingsModel {
   @Value.Default
   default int type() { return 0; }

   @Value.Default
   @Nullable
   default String source() { return ""; }

   static WelcomePageSettingsModel.Builder builder() {
      return new WelcomePageSettingsModel.Builder();
   }
   final class Builder extends ImmutableWelcomePageSettingsModel.Builder {

   }
}
