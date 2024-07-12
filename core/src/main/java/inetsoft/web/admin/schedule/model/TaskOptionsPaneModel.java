/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link TaskOptionsPaneModel} for the
 * image property dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableTaskOptionsPaneModel.class)
@JsonDeserialize(as = ImmutableTaskOptionsPaneModel.class)
public abstract class TaskOptionsPaneModel {
   public abstract boolean enabled();

   public abstract boolean deleteIfNotScheduledToRun();

   @Value.Default
   public long startFrom() {
      return 0L;
   }

   @Value.Default
   public long stopOn() {
      return 0L;
   }

   @Nullable
   public abstract String adminName();

   @Nullable
   public abstract String organizationName();

   public abstract boolean securityEnabled();

   @Value.Default
   public Boolean selfOrg() {
      return false;
   }

   @Value.Default
   public String[] users() {
      return new String[0];
   }

   @Value.Default
   public String[] groups() {
      return new String[0];
   }

   @Nullable
   public abstract String locale();

   @Value.Default
   public String[] locales() {
      return new String[0];
   }

   @Nullable
   public abstract String owner();

   @Nullable
   public abstract String description();

   @Nullable
   public abstract String idName();

   @Value.Default
   public int idType() {
      return 0;
   }

   @Nullable
   public abstract String ownerAlias();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableTaskOptionsPaneModel.Builder {
   }
}
