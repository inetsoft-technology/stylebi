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
package inetsoft.web.api.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.*;

/**
 * ScheduleConditionList contains a list of schedule conditions.
 */
@Validated
@Schema(description = "A list of schedule conditions.")
public class ScheduleConditionList {
   /**
    * Gets the list of conditions.
    *
    * @return the condition list.
    */
   @NotNull
   @Schema(description = "The conditions.")
   public List<ScheduleCondition> getConditions() {
      if(conditions == null) {
         conditions = new ArrayList<>();
      }

      return conditions;
   }

   /**
    * Sets the list of conditions.
    *
    * @param conditions the condition list.
    */
   public void setConditions(List<ScheduleCondition> conditions) {
      this.conditions = conditions;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ScheduleConditionList that = (ScheduleConditionList) o;
      return Objects.equals(conditions, that.conditions);
   }

   @Override
   public int hashCode() {
      return Objects.hash(conditions);
   }

   @Override
   public String toString() {
      return "ScheduleConditionList{" +
         "conditions=" + conditions +
         '}';
   }

   private List<ScheduleCondition> conditions;
}
