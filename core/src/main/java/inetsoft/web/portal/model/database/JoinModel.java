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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.erm.XRelationship;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JoinModel {
   public JoinType getType() {
      return type;
   }

   public void setType(JoinType type) {
      this.type = type;
   }

   public int getOrderPriority() {
      return orderPriority;
   }

   public void setOrderPriority(int orderPriority) {
      this.orderPriority = orderPriority;
   }

   public MergingRule getMergingRule() {
      return mergingRule;
   }

   public void setMergingRule(MergingRule mergingRule) {
      this.mergingRule = mergingRule;
   }

   public JoinCardinality getCardinality() {
      return cardinality;
   }

   public void setCardinality(JoinCardinality cardinality) {
      this.cardinality = cardinality;
   }

   public boolean isWeak() {
      return weak;
   }

   public void setWeak(boolean weak) {
      this.weak = weak;
   }

   public String getTable() {
      return table;
   }

   public void setTable(String table) {
      this.table = table;
   }

   public String getColumn() {
      return column;
   }

   public void setColumn(String column) {
      this.column = column;
   }

   public String getForeignTable() {
      return foreignTable;
   }

   public void setForeignTable(String foreignTable) {
      this.foreignTable = foreignTable;
   }

   public String getForeignColumn() {
      return foreignColumn;
   }

   public void setForeignColumn(String foreignColumn) {
      this.foreignColumn = foreignColumn;
   }

   public XRelationship getRelationship() {
      return relationship;
   }

   public void setRelationship(XRelationship relationship) {
      this.relationship = relationship;
   }

   public boolean isCycle() {
      return cycle;
   }

   public void setCycle(boolean cycle) {
      this.cycle = cycle;
   }

   public boolean isSupportFullOuter() {
      return supportFullOuter;
   }

   public void setSupportFullOuter(boolean supportFullOuter) {
      this.supportFullOuter = supportFullOuter;
   }

   public boolean isBaseJoin() {
      return baseJoin;
   }

   public void setBaseJoin(boolean baseJoin) {
      this.baseJoin = baseJoin;
   }

   public void store(String sourceTable, XRelationship relationship) {
      relationship.setDependent(sourceTable, getColumn());
      relationship.setIndependent(
         getForeignTable(), getForeignColumn());
      relationship.setJoinType(getType().getType());
      relationship.setMerging(getMergingRule().getType());
      relationship.setOrder(getOrderPriority());
      relationship.setWeakJoin(isWeak());

      if(getCardinality() == JoinCardinality.ONE_TO_ONE) {
         relationship.setDependentCardinality(XRelationship.ONE);
         relationship.setIndependentCardinality(XRelationship.ONE);
      }
      else if(getCardinality() == JoinCardinality.ONE_TO_MANY) {
         relationship.setDependentCardinality(XRelationship.ONE);
         relationship.setIndependentCardinality(XRelationship.MANY);
      }
      else {
         relationship.setDependentCardinality(XRelationship.MANY);

         if(getCardinality() == JoinCardinality.MANY_TO_ONE) {
            relationship.setIndependentCardinality(XRelationship.ONE);
         }
         else {
            relationship.setIndependentCardinality(XRelationship.MANY);
         }
      }
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      }

      if (o == null || getClass() != o.getClass()) {
         return false;
      }

      JoinModel joinModel = (JoinModel) o;

      return orderPriority == joinModel.orderPriority &&
         weak == joinModel.weak &&
         cycle == joinModel.cycle &&
         type == joinModel.type &&
         mergingRule == joinModel.mergingRule &&
         cardinality == joinModel.cardinality &&
         column.equals(joinModel.column) &&
         foreignTable.equals(joinModel.foreignTable) &&
         foreignColumn.equals(joinModel.foreignColumn);
   }

   @Override
   public int hashCode() {
      return Objects.hash(type, orderPriority, mergingRule, cardinality,
         weak, column, foreignTable, foreignColumn, cycle);
   }

   @Override
   public String toString() {
      return "JoinModel{" +
         "type=" + type +
         ", orderPriority=" + orderPriority +
         ", mergingRule=" + mergingRule +
         ", cardinality=" + cardinality +
         ", weak=" + weak +
         ", column='" + column + '\'' +
         ", foreignTable='" + foreignTable + '\'' +
         ", foreignColumn='" + foreignColumn + '\'' +
         ", cycle=" + cycle +
         ", supportFullOuter=" + supportFullOuter +
         ", baseJoin=" + baseJoin +
         ", relationship=" + relationship +
         '}';
   }

   private JoinType type;
   private int orderPriority;
   private MergingRule mergingRule;
   private JoinCardinality cardinality;
   private boolean weak;
   private String table;
   private String column;
   private String foreignTable;
   private String foreignColumn;
   // A loop or cycle join. readonly
   private boolean cycle;
   private boolean supportFullOuter;
   private boolean baseJoin;
   @JsonIgnore
   private XRelationship relationship;
}