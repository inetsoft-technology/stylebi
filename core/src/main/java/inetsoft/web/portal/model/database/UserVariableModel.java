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
package inetsoft.web.portal.model.database;

import inetsoft.uql.schema.UserVariable;

public class UserVariableModel extends XVariableModel {
   public UserVariableModel(UserVariable variable) {
      super(variable);

      setLabel(variable.getAlias());
      setToolTip(variable.getToolTip());
      setPrompt(variable.isPrompt());
      setSortValue(variable.isSortValue());
      setEmbedded(variable.isEmbedded());
      setMultipleSelection(variable.isMultipleSelection());
      setChoiceQuery(variable.getChoiceQuery());
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getToolTip() {
      return toolTip;
   }

   public void setToolTip(String toolTip) {
      this.toolTip = toolTip;
   }

   public boolean isPrompt() {
      return prompt;
   }

   public void setPrompt(boolean prompt) {
      this.prompt = prompt;
   }

   public boolean isSortValue() {
      return sortValue;
   }

   public void setSortValue(boolean sortValue) {
      this.sortValue = sortValue;
   }

   public boolean isEmbedded() {
      return embedded;
   }

   public void setEmbedded(boolean embedded) {
      this.embedded = embedded;
   }

   public boolean isMultipleSelection() {
      return multipleSelection;
   }

   public void setMultipleSelection(boolean multipleSelection) {
      this.multipleSelection = multipleSelection;
   }

   public String getChoiceQuery() {
      return choiceQuery;
   }

   public void setChoiceQuery(String choiceQuery) {
      this.choiceQuery = choiceQuery;
   }

   private String label = null;
   private String toolTip = "";
   private boolean prompt = true;
   private boolean sortValue = true;
   private boolean embedded = false;
   private boolean multipleSelection = false;
   private boolean customization = false; // true if used in customization
   private String choiceQuery = null;
}
