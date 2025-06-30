/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.admin.model.NameLabelTuple;
import org.immutables.value.Value;

import java.util.ArrayList;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableScheduleTaskNamesModel.class)
@JsonDeserialize(as = ImmutableScheduleTaskNamesModel.class)
public class ScheduleTaskNamesModel {
   @Value.Default
   public List<NameLabelTuple> allTasks() {
      return new ArrayList<>(0);
   }

   public static ScheduleTaskNamesModel.Builder builder() {
      return new ScheduleTaskNamesModel.Builder();
   }

   public static class Builder extends ImmutableScheduleTaskNamesModel.Builder {}
}
