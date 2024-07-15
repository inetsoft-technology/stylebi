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
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.*;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AssemblyConditionDialogModel {
   public boolean isAdvanced() {
      return advanced;
   }

   public void setAdvanced(boolean advanced) {
      this.advanced = advanced;
   }

   public DataRefModel[] getPreAggregateFields() {
      return preAggregateFields;
   }

   public void setPreAggregateFields(DataRefModel[] preAggregateFields) {
      this.preAggregateFields = preAggregateFields;
   }

   public DataRefModel[] getPostAggregateFields() {
      return postAggregateFields;
   }

   public void setPostAggregateFields(DataRefModel[] postAggregateFields) {
      this.postAggregateFields = postAggregateFields;
   }

   public Object[] getPreAggregateConditionList() {
      return preAggregateConditionList;
   }

   public void setPreAggregateConditionList(Object[] preAggregateConditionList) {
      this.preAggregateConditionList = preAggregateConditionList;
   }

   public Object[] getPostAggregateConditionList() {
      return postAggregateConditionList;
   }

   public void setPostAggregateConditionList(Object[] postAggregateConditionList) {
      this.postAggregateConditionList = postAggregateConditionList;
   }

   public Object[] getRankingConditionList() {
      return rankingConditionList;
   }

   public void setRankingConditionList(Object[] rankingConditionList) {
      this.rankingConditionList = rankingConditionList;
   }

   public MVConditionPaneModel getMvConditionPaneModel() {
      if(mvConditionPaneModel == null) {
         mvConditionPaneModel = new MVConditionPaneModel();
      }

      return mvConditionPaneModel;
   }

   public void setMvConditionPaneModel(
      MVConditionPaneModel mvConditionPaneModel)
   {
      this.mvConditionPaneModel = mvConditionPaneModel;
   }

   public SubqueryTableModel[] getSubqueryTables() {
      return subqueryTables;
   }

   public void setSubqueryTables(
      SubqueryTableModel[] subqueryTables)
   {
      this.subqueryTables = subqueryTables;
   }

   public List<String> getVariableNames() {
      return variableNames;
   }

   public void setVariableNames(List<String> variableNames) {
      this.variableNames = variableNames;
   }

   public List<ColumnRefModel> getExpressionFields() {
      return expressionFields;
   }

   public void setExpressionFields(
      List<ColumnRefModel> expressionFields)
   {
      this.expressionFields = expressionFields;
   }

   public ObjectNode getScriptDefinitions() {
      return scriptDefinitions;
   }

   public void setScriptDefinitions(ObjectNode scriptDefinitions) {
      this.scriptDefinitions = scriptDefinitions;
   }

   private boolean advanced;
   private DataRefModel[] preAggregateFields;
   private DataRefModel[] postAggregateFields;
   @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "jsonType")
   @JsonSubTypes({ @JsonSubTypes.Type(value = ConditionModel.class, name = "condition"),
                   @JsonSubTypes.Type(value = JunctionOperatorModel.class, name = "junction") })
   private Object[] preAggregateConditionList;
   @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "jsonType")
   @JsonSubTypes({ @JsonSubTypes.Type(value = ConditionModel.class, name = "condition"),
                   @JsonSubTypes.Type(value = JunctionOperatorModel.class, name = "junction") })
   private Object[] postAggregateConditionList;
   @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "jsonType")
   @JsonSubTypes({ @JsonSubTypes.Type(value = ConditionModel.class, name = "condition"),
                   @JsonSubTypes.Type(value = JunctionOperatorModel.class, name = "junction") })
   private Object[] rankingConditionList;
   private MVConditionPaneModel mvConditionPaneModel;
   private SubqueryTableModel[] subqueryTables;
   private List<String> variableNames;
   private List<ColumnRefModel> expressionFields;
   private ObjectNode scriptDefinitions;
}
