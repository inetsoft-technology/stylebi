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
package inetsoft.web.wiz.service;

import inetsoft.uql.tabular.DerivedRequestBodyQuery;
import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.PropertyEditor;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

/**
 * Mirrors the real {@code GraphQLQuery} shape: {@code getRequestBody} is overridden WITHOUT
 * {@code @Property} (so {@code requestBody} is not a contract property at all) and builds the
 * body from the settable {@code queryString}/{@code variables}; the query defaults to GET.
 */
@View(vertical = true, value = {
   @View1("suffix"),
   @View1("requestType"),
   @View1("queryString"),
   @View1("variables"),
})
public class FakeGraphQlLikeQuery extends TabularQuery implements DerivedRequestBodyQuery {
   public FakeGraphQlLikeQuery() {
      super("FakeGraphQlLike");
   }

   @Property(label = "Suffix")
   public String getSuffix() {
      return suffix;
   }

   public void setSuffix(String suffix) {
      this.suffix = suffix;
   }

   @Property(label = "Request Type")
   @PropertyEditor(tags = {"POST", "GET"})
   public String getRequestType() {
      return requestType;
   }

   public void setRequestType(String requestType) {
      this.requestType = requestType;
   }

   @Property(label = "Query String")
   public String getQueryString() {
      return queryString;
   }

   public void setQueryString(String queryString) {
      this.queryString = queryString;
   }

   @Property(label = "Variables")
   public String getVariables() {
      return variables;
   }

   public void setVariables(String variables) {
      this.variables = variables;
   }

   public String getRequestBody() {
      return "{\"query\":\"" + queryString + "\",\"variables\":" + variables + "}";
   }

   public void setRequestBody(String requestBody) {
   }

   private String suffix;
   private String requestType = "GET";
   private String queryString;
   private String variables = "{}";
}
