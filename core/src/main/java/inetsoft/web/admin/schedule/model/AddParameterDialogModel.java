/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link AddParameterDialogModel} for the
 * schedule dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableAddParameterDialogModel.class)
@JsonDeserialize(as = ImmutableAddParameterDialogModel.class)
public abstract class AddParameterDialogModel {
   public abstract String name();
   public abstract DynamicValueModel value();

   @Nullable
   public abstract String type();

   @Value.Default
   public boolean array() {
      return false;
   }

   @Nullable
   public abstract String parameterType();

   @Nullable
   public abstract String oldName();

   public static AddParameterDialogModel.Builder builder() {
      return new AddParameterDialogModel.Builder();
   }

   public static class Builder extends ImmutableAddParameterDialogModel.Builder {
   }
}
