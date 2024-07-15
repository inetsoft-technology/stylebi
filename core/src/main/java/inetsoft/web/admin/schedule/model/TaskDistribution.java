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
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.util.Catalog;
import org.immutables.value.Value;

import java.time.LocalTime;
import java.util.*;

@Value.Immutable
@JsonSerialize(as = ImmutableTaskDistribution.class)
@JsonDeserialize(as = ImmutableTaskDistribution.class)
public interface TaskDistribution {
   List<TaskDistributionGroup> days();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableTaskDistribution.Builder {
      public Builder from(ScheduleTask task, Catalog catalog) {
         List<TaskDistributionGroup> groups = new ArrayList<>();
         task.getConditionStream()
            .filter(TimeCondition.class::isInstance)
            .map(TimeCondition.class::cast)
            .forEach(c -> updateCounts(c, groups));
         // convert to immutable from modifiable
         groups.forEach(g -> addDays(toImmutable(g)));
         return this;
      }

      private void updateCounts(TimeCondition condition,
                                List<TaskDistributionGroup> groups)
      {
         switch(condition.getType()) {
         case TimeCondition.EVERY_HOUR:
            addEveryHourCounts(condition, groups);
            break;
         case TimeCondition.EVERY_DAY:
            addEveryDayCounts(condition, groups);
            break;
         case TimeCondition.EVERY_WEEK:
            addEveryWeekCounts(condition, groups);
            break;
         case TimeCondition.EVERY_MONTH:
            addEveryMonthCounts(condition, groups);
            break;
         }
      }

      private void addEveryHourCounts(TimeCondition condition,
                                      List<TaskDistributionGroup> groups)
      {
         LocalTime startTime = getStartTime(condition);
         LocalTime endTime = getEndTime(condition);

         for(int weekday : condition.getDaysOfWeek()) {
            ModifiableTaskDistributionGroup dayGroup = getGroup(groups, weekday);
            LocalTime time = LocalTime.from(startTime);

            while(!time.isAfter(endTime)) {
               int hour = time.getHour();
               int minute = time.getMinute() / 10;

               incrementHardCount(dayGroup);

               ModifiableTaskDistributionGroup hourGroup = getGroup(dayGroup.children(), hour);
               incrementHardCount(hourGroup);

               ModifiableTaskDistributionGroup minGroup = getGroup(hourGroup.children(), minute);
               incrementHardCount(minGroup);

               time = time.plusHours(1L);

               if(time.getHour() == 0) {
                  break;
               }
            }
         }
      }

      private void addEveryDayCounts(TimeCondition condition,
                                     List<TaskDistributionGroup> groups)
      {
         for(int weekday = Calendar.SUNDAY; weekday <= Calendar.SATURDAY; weekday++) {
            if(!condition.isWeekdayOnly() ||
               weekday != Calendar.SATURDAY && weekday != Calendar.SUNDAY)
            {
               ModifiableTaskDistributionGroup dayGroup = getGroup(groups, weekday);
               incrementHardCount(dayGroup);

               int hour = condition.getHour();
               int minute = condition.getMinute() / 10;

               ModifiableTaskDistributionGroup hourGroup = getGroup(dayGroup.children(), hour);
               incrementHardCount(hourGroup);

               ModifiableTaskDistributionGroup minGroup = getGroup(hourGroup.children(), minute);
               incrementHardCount(minGroup);
            }
         }
      }

      private void addEveryWeekCounts(TimeCondition condition,
                                      List<TaskDistributionGroup> groups)
      {
         for(int weekday : condition.getDaysOfWeek()) {
            int hour = condition.getHour();
            int minute = condition.getMinute() / 10;

            ModifiableTaskDistributionGroup dayGroup = getGroup(groups, weekday);
            ModifiableTaskDistributionGroup hourGroup = getGroup(dayGroup.children(), hour);
            ModifiableTaskDistributionGroup minGroup =  getGroup(hourGroup.children(), minute);

            if(condition.getInterval() == 1) {
               incrementHardCount(dayGroup);
               incrementHardCount(hourGroup);
               incrementHardCount(minGroup);
            }
            else {
               // for other intervals, it doesn't happen every week, so it should be included in the
               // soft count
               incrementSoftCount(dayGroup);
               incrementSoftCount(hourGroup);
               incrementSoftCount(minGroup);
            }
         }
      }

      private void addEveryMonthCounts(TimeCondition condition,
                                       List<TaskDistributionGroup> groups)
      {
         int hour = condition.getHour();
         int minute = condition.getMinute() / 10;

         if(condition.getDayOfMonth() == 0) {
            // only happens once a month, so it is added to the soft count
            ModifiableTaskDistributionGroup dayGroup = getGroup(groups, condition.getDayOfWeek());
            incrementSoftCount(dayGroup);

            ModifiableTaskDistributionGroup hourGroup = getGroup(dayGroup.children(), hour);
            incrementSoftCount(hourGroup);

            ModifiableTaskDistributionGroup minGroup =  getGroup(hourGroup.children(), minute);
            incrementSoftCount(minGroup);
         }
         else {
            // could be any day of the week, soft count
            for(int weekday = Calendar.SUNDAY; weekday <= Calendar.SATURDAY; weekday++) {
               ModifiableTaskDistributionGroup dayGroup = getGroup(groups, weekday);
               incrementSoftCount(dayGroup);

               ModifiableTaskDistributionGroup hourGroup = getGroup(dayGroup.children(), hour);
               incrementSoftCount(hourGroup);

               ModifiableTaskDistributionGroup minGroup =  getGroup(hourGroup.children(), minute);
               incrementSoftCount(minGroup);
            }
         }
      }

      private ModifiableTaskDistributionGroup getGroup(List<TaskDistributionGroup> groups,
                                                       int index)
      {
         for(int i = 0; i < groups.size(); i++) {
            TaskDistributionGroup group = groups.get(i);

            if(group.index() == index) {
               if(group instanceof ModifiableTaskDistributionGroup) {
                  return (ModifiableTaskDistributionGroup) group;
               }

               ModifiableTaskDistributionGroup mGroup = ModifiableTaskDistributionGroup.create()
                  .setIndex(group.index())
                  .setHardCount(group.hardCount())
                  .setSoftCount(group.softCount())
                  .setChildren(group.children());
               groups.set(i, mGroup);
               return mGroup;
            }
         }

         ModifiableTaskDistributionGroup group = ModifiableTaskDistributionGroup.create()
            .setIndex(index)
            .setHardCount(0)
            .setSoftCount(0);
         groups.add(group);
         groups.sort(Comparator.comparing(TaskDistributionGroup::index));
         return group;
      }
      
      private void incrementHardCount(ModifiableTaskDistributionGroup group) {
         group.setHardCount(group.hardCount() + 1);
      }

      private void incrementSoftCount(ModifiableTaskDistributionGroup group) {
         group.setSoftCount(group.softCount() + 1);
      }

      private LocalTime getStartTime(TimeCondition condition) {
         return LocalTime.of(condition.getHour(), condition.getMinute(), condition.getSecond());
      }

      private LocalTime getEndTime(TimeCondition condition) {
         return LocalTime.of(
            condition.getHourEnd(), condition.getMinuteEnd(), condition.getSecondEnd());
      }

      private TaskDistributionGroup toImmutable(TaskDistributionGroup group) {
         TaskDistributionGroup.Builder builder = TaskDistributionGroup.builder()
            .index(group.index())
            .hardCount(group.hardCount())
            .softCount(group.softCount());
         group.children().stream()
            .map(this::toImmutable)
            .forEach(builder::addChildren);
         return builder.build();
      }
   }
}
