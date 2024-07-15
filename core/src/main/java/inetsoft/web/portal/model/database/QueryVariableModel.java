/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.portal.model.database;

import inetsoft.uql.schema.QueryVariable;

public class QueryVariableModel extends XVariableModel {
   public QueryVariableModel(QueryVariable variable) {
      super(variable);

      setQuery(variable.getQuery());
      setAggregate(variable.getAggregate());
   }

   public String getQuery() {
      return query;
   }

   public void setQuery(String query) {
      this.query = query;
   }

   public String getAggregate() {
      return aggregate;
   }

   public void setAggregate(String aggregate) {
      this.aggregate = aggregate;
   }

   private String query;
   private String aggregate;
}
