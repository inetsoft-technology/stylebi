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

public class WarningModel {
   public String getMessage() {
      return message;
   }

   public void setMessage(String message) {
      this.message = message;
   }

   public String getTable1() {
      return table1;
   }

   public void setTable1(String table1) {
      this.table1 = table1;
   }

   public String getTable2() {
      return table2;
   }

   public void setTable2(String table2) {
      this.table2 = table2;
   }

   public boolean isCanContinue() {
      return canContinue;
   }

   public void setCanContinue(boolean canContinue) {
      this.canContinue = canContinue;
   }

   private String message;
   private String table1;
   private String table2;
   private boolean canContinue = true;
}