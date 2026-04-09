/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz.model.osi;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Expression in a specific SQL dialect (DialectExpression in osi-schema.json).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OsiDialectExpression {
   public String getDialect() {
      return dialect;
   }

   public void setDialect(String dialect) {
      this.dialect = dialect;
   }

   public String getExpression() {
      return expression;
   }

   public void setExpression(String expression) {
      this.expression = expression;
   }

   private String dialect;
   private String expression;
}
