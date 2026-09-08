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

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTaskMetaData;
import inetsoft.sree.security.IdentityID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.*;

/**
 * BatchAction describes an action that executes another task with parameters.
 */
@Validated
@Schema(description = "An action that executes another task with parameters.")
public class BatchAction extends ScheduleAction {
   /**
    * Creates a new instance of {@code BatchAction}.
    */
   public BatchAction() {
      setActionType(ActionType.BATCH);
   }

   /**
    * Creates a new instance of {@code BatchAction}.
    *
    * @param action the action object being represented.
    */
   public BatchAction(inetsoft.sree.schedule.BatchAction action) {
      setActionType(ActionType.BATCH);
      ScheduleTaskMetaData taskMetaData = ScheduleManager.getTaskMetaData(action.getTaskId());
      setTaskName(taskMetaData.getTaskName());
      setOwner(IdentityID.getIdentityIDFromKey(taskMetaData.getTaskOwnerId()).name);

      if(action.getQueryEntry() != null) {
         setQueryEntry(action.getQueryEntry().toIdentifier());
      }

      setQueryParameters(action.getQueryParameters());
      setEmbeddedParameters(action.getEmbeddedParameters());
   }

   /**
    * Gets the name of the task to execute.
    *
    * @return the name of the task.
    */
   @NotNull
   @Schema(description = "The name of the task to execute.", example = "Task1")
   public String getTaskName() {
      return taskName;
   }

   /**
    * Sets the name of the task to execute.
    *
    * @param taskName the name of the task to execute.
    */
   public void setTaskName(String taskName) {
      this.taskName = taskName;
   }

   /**
    * Gets the name of the task to execute.
    *
    * @return the name of the task.
    */
   @NotNull
   @Schema(description = "The owner of the task to execute.", example = "user0")
   public String getOwner() {
      return owner;
   }

   /**
    * Sets the owner of the task to execute.
    *
    * @param owner the owner of the task to execute.
    */
   public void setOwner(String owner) {
      this.owner = owner;
   }

   /**
    * Gets the query used to execute the task.
    *
    * @return the query used to execute the task.
    */
   @Schema(description = "The query used to execute the task.")
   public String getQueryEntry() {
      return queryEntry;
   }

   /**
    * Sets the query used to execute the task.
    *
    * @param queryEntry the query used to execute the task.
    */
   public void setQueryEntry(String queryEntry) {
      this.queryEntry = queryEntry;
   }

   /**
    * Gets the query parameters used in the query.
    *
    * @return the query parameters used in the query.
    */
   @Schema(description = "The query parameters used in the query..")
   public Map<String, Object> getQueryParameters() {
      return queryParameters;
   }

   /**
    * Sets the query parameters used in the query.
    *
    * @param queryParameters the query parameters used in the query.
    */
   public void setQueryParameters(Map<String, Object> queryParameters) {
      this.queryParameters = queryParameters;
   }

   /**
    * Gets the parameters used to execute the task.
    *
    * @return the parameters used to execute the task.
    */
   @Schema(description = "The query parameters used in the query..",
      example = "[{ \"clientId\": 1, " +
         "\"expressionParam\": {\"value\": \"=2+4\", \"dataType\": \"integer\", \"type\": \"EXPRESSION\"}, " +
         "\"arrayParam\": {\"value\": \"value1,value2\", \"array\": true, \"dataType\": \"string\", \"type\": \"VALUE\"} }]"
   )
   public List<Map<String, Object>> getEmbeddedParameters() {
      return embeddedParameters;
   }

   /**
    * Sets the parameters used to execute the task.
    *
    * @param embeddedParameters the parameters used to execute the task.
    */
   public void setEmbeddedParameters(List<Map<String, Object>> embeddedParameters) {
      this.embeddedParameters = embeddedParameters;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      BatchAction that = (BatchAction) o;
      return Objects.equals(taskName, that.taskName) &&
         Objects.equals(queryEntry, that.queryEntry) &&
         Objects.equals(queryParameters, that.queryParameters) &&
         Objects.equals(embeddedParameters, that.embeddedParameters);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         super.hashCode(), taskName, queryEntry, queryParameters, embeddedParameters);
   }

   @Override
   public String toString() {
      return "BatchAction{" +
         "taskName='" + taskName + '\'' +
         ", queryEntry='" + queryEntry  + '\'' +
         ", queryParameters=" + queryParameters  +
         ", embeddedParameters=" + embeddedParameters +
         '}';
   }

   private String taskName;
   private String owner;
   private String queryEntry = "null";
   private Map<String, Object> queryParameters = new LinkedHashMap<>();
   private List<Map<String, Object>> embeddedParameters = new ArrayList<>();
}
