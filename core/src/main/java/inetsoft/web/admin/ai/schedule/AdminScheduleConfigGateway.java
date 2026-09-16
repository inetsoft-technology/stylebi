/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.ai.schedule;

import inetsoft.sree.schedule.ScheduleCondition;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.schedule.TimeRange;
import inetsoft.web.admin.schedule.SchedulerConfigurationService;
import inetsoft.web.admin.schedule.model.ScheduleConfigurationModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code AdminScheduleConfigGateway} wraps {@link SchedulerConfigurationService}'s existing
 * {@code getConfiguration}/{@code setConfiguration} directly -- there is no enterprise Public API
 * layer for Server Locations/Time Ranges to delegate to (see {@code 01-design.md}'s "No enterprise
 * Public API layer exists" section). Every read here is a fresh {@code getConfiguration} call, and
 * every write is a FULL {@code ScheduleConfigurationModel} write -- there is no per-field PATCH on
 * the underlying service, so a caller of this gateway must always splice its own change into a
 * freshly read model and write the whole thing back (see {@link ScheduleConfigChangePlanService}/
 * {@link ScheduleConfigChangesetApplyService}, which do exactly that).
 */
@Service
public class AdminScheduleConfigGateway {
   @Autowired
   public AdminScheduleConfigGateway(SchedulerConfigurationService configService,
                                     ScheduleManager scheduleManager)
   {
      this.configService = configService;
      this.scheduleManager = scheduleManager;
   }

   /** Reads the full model fresh -- includes Track B's own ~18 scalar fields, which callers here
    * must always carry through unchanged (never reconstruct a partial model). */
   public ScheduleConfigurationModel getFullConfig(Principal user) throws Exception {
      return configService.getConfiguration(user);
   }

   /** Writes the full model back. {@code model} must be {@code current} with only the caller's own
    * intended field(s) changed -- see this class's own javadoc. */
   public void writeFullConfig(ScheduleConfigurationModel model, Principal user) throws Exception {
      configService.setConfiguration(model, user);
   }

   /**
    * Scans every live schedule task for a {@code TimeCondition} that currently resolves, BY NAME,
    * to {@code rangeName} -- the same live-name-reference relationship {@code TaskBalancer} itself
    * relies on (a task's stored condition carries a frozen snapshot of the {@code TimeRange} it was
    * last balanced against, re-resolved against whatever the current list is by name, not object
    * identity). For each match, computes what {@code TimeRange.getMatchingTimeRange} would silently
    * reassign that task to once {@code remainingRanges} takes effect -- the exact closest-match
    * calculation {@code TaskBalancer.balanceTasks} performs, so the advisory this produces is exact,
    * not a vague "something might change" warning.
    *
    * @param remainingRanges the time range list as it would stand AFTER the plan under
    *                        consideration is applied (i.e. with {@code rangeName}'s own entry
    *                        already removed/changed) -- the candidate pool a reassignment would be
    *                        chosen from.
    */
   public List<TimeRangeDependency> findTimeRangeDependents(
      String rangeName, List<TimeRange> remainingRanges) throws Exception
   {
      List<TimeRangeDependency> found = new ArrayList<>();

      for(ScheduleTask task : scheduleManager.getScheduleTasks()) {
         for(int i = 0; i < task.getConditionCount(); i++) {
            ScheduleCondition condition = task.getCondition(i);

            if(!(condition instanceof TimeCondition timeCondition)) {
               continue;
            }

            TimeRange current = timeCondition.getTimeRange();

            if(current == null || !rangeName.equals(current.getName())) {
               continue;
            }

            TimeRange reassignedTo = remainingRanges.isEmpty()
               ? null : TimeRange.getMatchingTimeRange(current, remainingRanges);
            found.add(new TimeRangeDependency(
               task.getTaskId(), reassignedTo == null ? null : reassignedTo.getName()));
         }
      }

      return found;
   }

   private final SchedulerConfigurationService configService;
   private final ScheduleManager scheduleManager;
}
