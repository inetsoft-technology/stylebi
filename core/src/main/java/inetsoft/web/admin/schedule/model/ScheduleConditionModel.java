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
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeConditionModel;

import javax.annotation.Nullable;

/**
 * Model representing a schedule condition
 */
@JsonTypeInfo(
   include = JsonTypeInfo.As.EXISTING_PROPERTY,
   use = JsonTypeInfo.Id.NAME,
   property = "conditionType")
@JsonSubTypes({
   @JsonSubTypes.Type(value = TimeConditionModel.class, name = "TimeCondition"),
   @JsonSubTypes.Type(value = CompletionConditionModel.class, name = "CompletionCondition")
})
public abstract class ScheduleConditionModel {
   @Nullable
   public abstract String label();
   @Nullable
   public abstract String conditionType();
}
