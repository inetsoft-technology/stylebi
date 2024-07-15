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
package inetsoft.web.binding.model;

import inetsoft.web.binding.drm.AggregateRefModel;

import java.util.ArrayList;
import java.util.List;

public class AggregateInfoModel {
   public AggregateInfoModel() {
   }

   /**
    * Get all the groups.
    * @return all the groups.
    */
   public List<GroupRefModel> getGroups() {
      return groups;
   }

   /**
    * Set all the groups.
    */
   public void setGroups(List<GroupRefModel> groups) {
      this.groups = groups;
   }

   /**
    * Add a group to groups.
    * @param group the specified group model.
    */
   public void addGroup(GroupRefModel group) {
      groups.add(group);
   }

   /**
    * Get the aggregates.
    * @return the aggregates of the attribute.
    */
   public List<AggregateRefModel> getAggregates() {
      return aggregates;
   }

   /**
    * Set the aggregates.
    * @param aggregates the aggregates of the attribute.
    */
   public void setAggregates(List<AggregateRefModel> aggregates) {
      this.aggregates = aggregates;
   }

   /**
    * Add a aggregate to aggregates.
    * @param aggregate the specified aggregate of the attribute.
    */
   public void addAggregate(AggregateRefModel aggregate) {
      aggregates.add(aggregate);
   }

   /**
    * Get all the secondary aggregates.
    * @return all the secondary aggregates.
    */
   public List<AggregateRefModel> getSecondaryAggregates() {
      return aggregates2;
   }

   /**
    * Set the secondary aggregates.
    * @param aggregates2 the secondary aggregates.
    */
   public void setSecondaryAggregates(List<AggregateRefModel> aggregates2) {
      this.aggregates2 = aggregates2;
   }

   /**
    * Add a aggregate to secondary aggregates.
    * @param aggregate2 the aggregate of the secondary aggregates.
    */
   public void addSecondaryAggregate(AggregateRefModel aggregate2) {
      aggregates2.add(aggregate2);
   }

   /**
    * Check if is a crosstab.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isCrosstab() {
      return crosstab;
   }

   /**
    * Set the crosstab option.
    * @param crosstab <tt>true</tt> if a crosstab.
    */
   public void setCrosstab(boolean crosstab) {
      this.crosstab = crosstab;
   }

   private List<AggregateRefModel> aggregates = new ArrayList<>();
   private List<AggregateRefModel> aggregates2 = new ArrayList<>();
   private List<GroupRefModel> groups = new ArrayList<>();
   private boolean crosstab;
}