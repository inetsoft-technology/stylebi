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

@SuppressWarnings({ "unused", "WeakerAccess" })
public class Condition {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public List<DataConditionItem> getClauses() {
      if(clauses == null) {
         clauses = new ArrayList<>();
      }

      return clauses;
   }

   public void setClauses(List<DataConditionItem> clauses) {
      this.clauses = clauses;
   }

   public int getType() {
      return type;
   }

   public void setType(int type) {
      this.type = type;
   }

   public String getTableName() {
      return tableName;
   }

   public void setTableName(String tableName) {
      this.tableName = tableName;
   }

   public String getScript() {
      if(script == null) {
         script = "";
      }

      return script;
   }

   public void setScript(String script) {
      this.script = script;
   }

   private String name;
   private List<DataConditionItem> clauses;
   private int type;
   private String tableName;
   private String script;
}
