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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Catalog;
import inetsoft.web.admin.schedule.ScheduleService;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.stream.Collectors;

@Value.Immutable
@JsonSerialize(as = ImmutableScheduleTaskModel.class)
@JsonDeserialize(as = ImmutableScheduleTaskModel.class)
public interface ScheduleTaskModel {
   String name();
   String label();
   String description();
   IdentityID owner();
   @Nullable String ownerAlias();
   @Nullable String path();
   @Nullable ScheduleTaskStatusModel status();
   String schedule();
   boolean editable();
   boolean removable();
   @Value.Default
   default boolean canDelete() {
      return false;
   }
   boolean enabled();
   @Nullable
   TaskDistribution distribution();
   @Nullable
   String lastRunTime();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableScheduleTaskModel.Builder {
      public Builder fromTask(ScheduleTask task, ScheduleService scheduleService, Catalog catalog) {
         TaskActivity activity = scheduleService.getActivity(task.getName());
         boolean securityEnabled = scheduleService.isSecurityEnabled();
         return fromTask(task, activity, catalog, securityEnabled);
      }

      public Builder fromTask(ScheduleTask task, TaskActivity activity,
                              Catalog catalog, boolean securityEnabled)
      {
         String name = task.getName();

         if(task.getCycleInfo() != null && name.indexOf("__") != -1) {
            name = name.substring(name.indexOf("__") + 2);
         }

         if(!securityEnabled && !name.startsWith(DataCycleManager.TASK_PREFIX) &&
            !name.startsWith("MV Task:") && !name.startsWith("MV Task Stage 2:") &&
            name.indexOf(":") != -1)
         {
            name = name.substring(name.indexOf(":") + 1);
         }

         name(name);
         owner(task.getOwner());
         ownerAlias(SUtil.getUserAlias(task.getOwner()));
         path(task.getPath());
         description(task.getDescription() == null ? "" : task.getDescription());
         editable(task.isEditable());
         removable(task.isRemovable());
         canDelete(true);
         schedule(task.getConditionStream()
                  .filter(Objects::nonNull)
                  .map(ScheduleCondition::toString)
                  .collect(Collectors.joining(", ")));

         if(activity != null) {
            status(ScheduleTaskStatusModel.builder().from(activity).build());
         }

         int index = task.getName().indexOf(':');

         if(index >= 0) {
            label(task.getName().substring(index + 1));
         }
         else if(ScheduleManager.isInternalTask(task.getName())) {
            label(catalog.getString(task.getName()));
         }
         else {
            label(task.getName());
         }

         enabled(task.isEnabled());
         distribution(TaskDistribution.builder().from(task, catalog).build());

         return this;
      }
   }
}
