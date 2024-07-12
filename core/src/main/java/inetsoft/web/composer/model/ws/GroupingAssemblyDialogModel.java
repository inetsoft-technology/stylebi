/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.model.ws;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.ConditionExpression;

public class GroupingAssemblyDialogModel {
   public String getNewName() {
      return newName;
   }

   public void setNewName(String newName) {
      this.newName = newName;
   }

   public String getOldName() {
      return oldName;
   }

   public void setOldName(String oldName) {
      this.oldName = oldName;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public AssetEntry getOnlyFor() {
      return onlyFor;
   }

   public void setOnlyFor(AssetEntry onlyFor) {
      this.onlyFor = onlyFor;
   }

   public DataRefModel getAttribute() {
      return attribute;
   }

   public void setAttribute(DataRefModel attribute) {
      this.attribute = attribute;
   }

   public boolean getGroupAllOthers() {
      return groupAllOthers;
   }

   public void setGroupAllOthers(boolean groupAllOthers) {
      this.groupAllOthers = groupAllOthers;
   }

   public ConditionExpression[] getConditionExpressions() {
      if(conditionExpressions == null) {
         conditionExpressions = new ConditionExpression[0];
      }

      return conditionExpressions;
   }

   public void setConditionExpressions(ConditionExpression[] conditionExpressions) {
      this.conditionExpressions = conditionExpressions;
   }

   public String[] getVariableNames() {
      if(variableNames == null) {
         variableNames = new String[0];
      }

      return variableNames;
   }

   public void setVariableNames(String[] variableNames) {
      this.variableNames = variableNames;
   }

   private String newName;
   private String oldName;
   private String type;
   private AssetEntry onlyFor;
   private DataRefModel attribute;
   private boolean groupAllOthers;
   private ConditionExpression[] conditionExpressions;
   private String[] variableNames;
}
