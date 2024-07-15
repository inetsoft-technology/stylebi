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
package inetsoft.web.composer.model.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.jdbc.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JoinItemModel {
   public JoinItemModel() {}

   public JoinItemModel(XJoin join, UniformSQL sql) {
      setTable1(join.getTable1(sql));
      setTable2(join.getTable2(sql));
      setColumn1(join.getColumn1(sql));
      setColumn2(join.getColumn2(sql));

      if("*=*".equals(join.getOp())) {
         setAll1(true);
         setAll2(true);
         setOperator("=");
      }
      else if("*=".equals(join.getOp())) {
         setAll1(true);
         setOperator("=");
      }
      else if("=*".equals(join.getOp())) {
         setAll2(true);
         setOperator("=");
      }
      else {
         setOperator(join.getOp());
      }
   }

   public XJoin toXJoin() {
      String all1  = isAll1() ? "*" : "";
      String all2 = isAll2() ? "*" : "";
      String operator = all1 + this.operator + all2;

      return new XJoin(new XExpression(table1 + "." + column1, XExpression.FIELD),
                       new XExpression(table2 + "." + column2, XExpression.FIELD),
                       operator);
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

   public String getColumn1() {
      return column1;
   }

   public void setColumn1(String column1) {
      this.column1 = column1;
   }

   public String getColumn2() {
      return column2;
   }

   public void setColumn2(String column2) {
      this.column2 = column2;
   }

   public boolean isAll1() {
      return all1;
   }

   public void setAll1(boolean all1) {
      this.all1 = all1;
   }

   public boolean isAll2() {
      return all2;
   }

   public void setAll2(boolean all2) {
      this.all2 = all2;
   }

   public String getOperator() {
      return operator;
   }

   public void setOperator(String operator) {
      this.operator = operator;
   }

   private String table1;
   private String table2;
   private String column1;
   private String column2;
   private boolean all1;
   private boolean all2;
   private String operator;
}
