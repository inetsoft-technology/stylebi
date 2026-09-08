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

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

/**
 * {@code ScheduleAction} defines the actions to be performed when a schedule task runs.
 */
@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.EXISTING_PROPERTY,
   property = "actionType",
   visible = true
)
@JsonSubTypes({
   @JsonSubTypes.Type(value = ViewsheetAction.class, name = "viewsheet"),
   @JsonSubTypes.Type(value = BatchAction.class, name = "batch")
})
@Validated
@Schema(description = "An action to be performed when a schedule task runs.")
public abstract class ScheduleAction {
   /**
    * Gets the type of action.
    *
    * @return the action type.
    */
   @NotNull
   @Schema(description = "The type of action.")
   public ActionType getActionType() {
      return actionType;
   }

   /**
    * Sets the type of action.
    *
    * @param actionType the action type.
    */
   public void setActionType(ActionType actionType) {
      this.actionType = actionType;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ScheduleAction that = (ScheduleAction) o;
      return actionType == that.actionType;
   }

   @Override
   public int hashCode() {
      return Objects.hash(actionType);
   }

   @Override
   public String toString() {
      return "ScheduleAction{" +
         "actionType=" + actionType +
         '}';
   }

   private ActionType actionType;

   /**
    * Enumeration of the types of schedule actions.
    */
   public enum ActionType {
      /**
       * An action that opens a viewsheet and performs one or more sub-actions on it.
       */
      VIEWSHEET("viewsheet"),

      /**
       * An action that executes another task with parameters.
       */
      BATCH("batch"),

      BACKUP("backup");

      private final String value;

      ActionType(String value) {
         this.value = value;
      }

      @Override
      @JsonValue
      public String toString() {
         return value;
      }

      @SuppressWarnings("unused")
      @JsonCreator
      public static ActionType fromValue(String value) {
         for(ActionType type : values()) {
            if(value.equals(type.value)) {
               return type;
            }
         }

         return null;
      }
   }
}
