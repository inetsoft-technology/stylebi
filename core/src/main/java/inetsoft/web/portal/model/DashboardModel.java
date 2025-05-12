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
package inetsoft.web.portal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableDashboardModel.class)
@JsonDeserialize(as = ImmutableDashboardModel.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class DashboardModel {
   public abstract String name();

   @Nullable
   public abstract String label();

   @Nullable
   public abstract String type();

   @Nullable
   public abstract String description();

   @Nullable
   public abstract String path();

   @Nullable
   public abstract String identifier();

   @Value.Default
   public boolean enabled() {
      return true;
   }

   @Value.Default
   public boolean composedDashboard() {
      return false;
   }

   @Value.Default
   public boolean scaleToScreen() {
      return false;
   }

   @Value.Default
   public boolean fitToWidth() {
      return false;
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableDashboardModel.Builder {
   }
}
