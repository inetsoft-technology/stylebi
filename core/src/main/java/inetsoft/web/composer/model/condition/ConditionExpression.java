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
package inetsoft.web.composer.model.condition;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.report.composition.WorksheetService;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.web.binding.service.DataRefModelFactoryService;

import java.io.Serializable;
import java.security.Principal;

/**
 * Contains a Condition/Junction list along with a String identifier.
 */
public class ConditionExpression implements Serializable {

   public ConditionList extractConditionList(
      AssetEntry entry, WorksheetService worksheetService,
      Principal principal) throws Exception
   {
      SourceInfo sourceInfo = null;

      if(entry != null) {
         sourceInfo = new SourceInfo(Integer.parseInt(entry.getProperty("type")),
                                     entry.getProperty("prefix"), entry.getProperty("source"));
      }

      return ConditionUtil.fromModelToConditionList(
         getList(), sourceInfo, worksheetService, principal);
   }

   public void populateConditionListModel(
      ConditionList conditionList,
      DataRefModelFactoryService refModelFactoryService)
   {
      Object[] conditionListModel = ConditionUtil.fromConditionListToModel(
         conditionList,
         refModelFactoryService);
      this.setList(conditionListModel);
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public Object[] getList() {
      if(list == null) {
         list = new Object[0];
      }
      return list;
   }

   public void setList(Object[] list) {
      this.list = list;
   }

   private String name;
   @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "jsonType")
   @JsonSubTypes({ @JsonSubTypes.Type(value = ConditionModel.class, name = "condition"),
                   @JsonSubTypes.Type(value = JunctionOperatorModel.class, name = "junction") })
   private Object[] list;
}
