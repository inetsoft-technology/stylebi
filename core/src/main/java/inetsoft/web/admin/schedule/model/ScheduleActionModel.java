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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import javax.annotation.Nullable;
/**
 * Model representing a schedule action.
 */
@JsonTypeInfo(
   include = JsonTypeInfo.As.EXISTING_PROPERTY,
   use = JsonTypeInfo.Id.NAME,
   property = "actionClass")
@JsonSubTypes({
   @JsonSubTypes.Type(value = BackupActionModel.class, name = "BackupActionModel"),
   @JsonSubTypes.Type(value = GeneralActionModel.class, name = "GeneralActionModel"),
   @JsonSubTypes.Type(value = BatchActionModel.class, name = "BatchActionModel"),
})
public abstract class ScheduleActionModel {
   @Nullable
   public abstract String label();

   public abstract String actionType();

   @Nullable
   public abstract String actionClass();
}
