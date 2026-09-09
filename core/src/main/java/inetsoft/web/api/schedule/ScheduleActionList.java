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
 * ScheduleActionList is a list of schedule actions.
 */
@Validated
@Schema(description = "A list of schedule actions")
public class ScheduleActionList {
   /**
    * Gets the list of actions.
    *
    * @return the action list.
    */
   @NotNull
   @Schema(description = "The actions.")
   public List<ScheduleAction> getActions() {
      if(actions == null) {
         actions = new ArrayList<>();
      }

      return actions;
   }

   /**
    * Sets the list of actions.
    *
    * @param actions the action list.
    */
   public void setActions(List<ScheduleAction> actions) {
      this.actions = actions;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ScheduleActionList that = (ScheduleActionList) o;
      return Objects.equals(actions, that.actions);
   }

   @Override
   public int hashCode() {
      return Objects.hash(actions);
   }

   @Override
   public String toString() {
      return "ScheduleActionList{" +
         "actions=" + actions +
         '}';
   }

   private List<ScheduleAction> actions;
}
