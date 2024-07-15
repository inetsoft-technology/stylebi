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
package inetsoft.web.portal.model.database;

import java.util.ArrayList;
import java.util.List;

public class VPMDefinition {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public List<Condition> getConditions() {
      if(conditions == null) {
         conditions = new ArrayList<>();
      }

      return conditions;
   }

   public void setConditions(List<Condition> conditions) {
      this.conditions = conditions;
   }

   public HiddenColumnsModel getHidden() {
      return hidden;
   }

   public void setHidden(HiddenColumnsModel hidden) {
      this.hidden = hidden;
   }

   public String getLookup() {
      return lookup;
   }

   public void setLookup(String lookup) {
      this.lookup = lookup;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   private String name;
   private List<Condition> conditions;
   private HiddenColumnsModel hidden;
   private String lookup;
   private String description;
}
