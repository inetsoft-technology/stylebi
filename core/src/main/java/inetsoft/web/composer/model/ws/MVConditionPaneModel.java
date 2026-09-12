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
package inetsoft.web.composer.model.ws;

import com.fasterxml.jackson.annotation.*;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.condition.JunctionOperatorModel;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MVConditionPaneModel implements Serializable {
   public boolean isForceAppendUpdates() {
      return forceAppendUpdates;
   }

   public void setForceAppendUpdates(boolean forceAppendUpdates) {
      this.forceAppendUpdates = forceAppendUpdates;
   }

   public DataRefModel[] getPreAggregateFields() {
      return preAggregateFields;
   }

   public void setPreAggregateFields(
      DataRefModel[] preAggregateFields)
   {
      this.preAggregateFields = preAggregateFields;
   }

   public DataRefModel[] getPostAggregateFields() {
      return postAggregateFields;
   }

   public void setPostAggregateFields(
      DataRefModel[] postAggregateFields)
   {
      this.postAggregateFields = postAggregateFields;
   }

   public Object[] getAppendPreAggregateConditionList() {
      return appendPreAggregateConditionList;
   }

   public void setAppendPreAggregateConditionList(
      Object[] appendPreAggregateConditionList)
   {
      this.appendPreAggregateConditionList = appendPreAggregateConditionList;
   }

   public Object[] getAppendPostAggregateConditionList() {
      return appendPostAggregateConditionList;
   }

   public void setAppendPostAggregateConditionList(
      Object[] appendPostAggregateConditionList)
   {
      this.appendPostAggregateConditionList = appendPostAggregateConditionList;
   }

   public Object[] getDeletePreAggregateConditionList() {
      return deletePreAggregateConditionList;
   }

   public void setDeletePreAggregateConditionList(
      Object[] deletePreAggregateConditionList)
   {
      this.deletePreAggregateConditionList = deletePreAggregateConditionList;
   }

   public Object[] getDeletePostAggregateConditionList() {
      return deletePostAggregateConditionList;
   }

   public void setDeletePostAggregateConditionList(
      Object[] deletePostAggregateConditionList)
   {
      this.deletePostAggregateConditionList = deletePostAggregateConditionList;
   }

   private boolean forceAppendUpdates;
   private DataRefModel[] preAggregateFields;
   private DataRefModel[] postAggregateFields;
   @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "jsonType")
   @JsonSubTypes({ @JsonSubTypes.Type(value = ConditionModel.class, name = "condition"),
                   @JsonSubTypes.Type(value = JunctionOperatorModel.class, name = "junction") })
   private Object[] appendPreAggregateConditionList;
   @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "jsonType")
   @JsonSubTypes({ @JsonSubTypes.Type(value = ConditionModel.class, name = "condition"),
                   @JsonSubTypes.Type(value = JunctionOperatorModel.class, name = "junction") })
   private Object[] appendPostAggregateConditionList;
   @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "jsonType")
   @JsonSubTypes({ @JsonSubTypes.Type(value = ConditionModel.class, name = "condition"),
                   @JsonSubTypes.Type(value = JunctionOperatorModel.class, name = "junction") })
   private Object[] deletePreAggregateConditionList;
   @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "jsonType")
   @JsonSubTypes({ @JsonSubTypes.Type(value = ConditionModel.class, name = "condition"),
                   @JsonSubTypes.Type(value = JunctionOperatorModel.class, name = "junction") })
   private Object[] deletePostAggregateConditionList;
}
