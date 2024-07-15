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
package inetsoft.web.binding.model.table;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BindingModel;

import java.util.*;

@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.PROPERTY,
   property = "type")
@JsonSubTypes({
   @JsonSubTypes.Type(value = TableBindingModel.class, name = "table"),
   @JsonSubTypes.Type(value = CrosstabBindingModel.class, name = "crosstab"),
   @JsonSubTypes.Type(value = CalcTableBindingModel.class, name = "calctable")
})
public abstract class BaseTableBindingModel extends BindingModel {
   /**
    * Set the allrows value.
    * @param the allrows array.
    */
   public void setAllRows(List<String> allrows) {
      this.allRows = allrows;
   }

   /**
    * Get the allrows value.
    * @return the all rows value.
    */
   public List<String> getAllRows() {
      return allRows;
   }

   /**
    * Get the table aggregates.
    * @return table aggregates.
    */
   public List<BAggregateRefModel> getAggregates() {
      return aggregates;
   }

   /**
    * Set table aggregates.
    * @param aggregates the table aggregates.
    */
   public void setAggregates(List<BAggregateRefModel> aggregates) {
      this.aggregates = aggregates;
   }

   /**
    * Add table aggregate.
    * @param aggregate the table aggregate.
    */
   public void addAggregate(BAggregateRefModel aggregate) {
      aggregates.add(aggregate);
   }

   /**
    * @param view             view with applying calc.
    * @param viewWithoutCalc  view without applying calc, will used to display in sortby, rankingby.
    */
   public void addName2Label(String view, String viewWithoutCalc) {
      if(view == null || viewWithoutCalc == null) {
         return;
      }

      name2Labels.put(view, viewWithoutCalc);
   }

   public Map<String, String> getName2Labels() {
      return name2Labels;
   }

   public void setName2Labels(Map<String, String> name2Labels) {
      this.name2Labels = name2Labels;
   }

   private List<String> allRows = null;
   private List<BAggregateRefModel> aggregates = new ArrayList<>();
   private Map<String, String> name2Labels = new HashMap<>();
}
