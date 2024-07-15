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

import inetsoft.web.composer.model.ws.SQLQueryDialogModel;

public class ClauseValue {
   public ClauseValue() {
   }

   public ClauseValue(String type) {
      this.type = type;
   }

   public ClauseValue(String type, String expression) {
      this.type = type;
      this.expression = expression;
   }

   public String getExpression() {
      return expression;
   }

   public void setExpression(String expression) {
      this.expression = expression;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public Column getField() {
      return field;
   }

   public void setField(Column field) {
      this.field = field;
   }

   public SQLQueryDialogModel getQuery() {
      return query;
   }

   public void setQuery(SQLQueryDialogModel query) {
      this.query = query;
   }

   private String expression;
   private String type;
   private Column field;
   private SQLQueryDialogModel query;
}
