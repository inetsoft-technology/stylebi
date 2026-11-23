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

import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

/**
 * Stand-in for a query such as {@code GraphQLQuery}: a generic REST query (no {@code endpoint}
 * property) whose request body is built from other properties, so {@code getRequestBody} is
 * overridden (and re-annotated, so it stays a visible property) and a body written through {@code setRequestBody} is never the one sent.
 */
@View(vertical = true, value = {
   @View1("suffix"),
   @View1("requestType"),
   @View1("contentType"),
   @View1("requestBody"),
})
public class FakeDerivedBodyRestQuery extends FakeRestPostQuery {
   @Override
   @Property(label = "Request Body")
   public String getRequestBody() {
      return "{\"query\":\"derived\"}";
   }
}
