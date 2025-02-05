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
package inetsoft.uql.rest.datasource.shopify;

import inetsoft.uql.rest.datasource.graphql.GraphQLQuery;
import inetsoft.uql.rest.pagination.PaginationType;
import inetsoft.uql.tabular.*;

@View(vertical = true, value = {
   @View1(
      type = ViewType.PANEL,
      vertical = true,
      colspan = 2,
      elements = {
         @View2(type = ViewType.LABEL, text = "Query"),
         @View2(type = ViewType.EDITOR, value = "queryString"),
         @View2(type = ViewType.LABEL, text = "Variables"),
         @View2(type = ViewType.EDITOR, value = "variables"),
      }
   ),
   @View1("usePagination"),
   @View1(
      type = ViewType.PANEL,
      vertical = true,
      colspan = 2,
      elements = {
         @View2(
            type = ViewType.LABEL,
            text = "Enter the name of your cursor variable without the $", col = 1,
            visibleMethod = "isUsePagination"
         ),
         @View2(value = "paginationVariable", visibleMethod = "isUsePagination"),
         @View2(
            type = ViewType.LABEL,
            text = "JSON Path for the last cursor in the response", col = 1,
            visibleMethod = "isUsePagination"
         ),
         @View2(value = "paginationCountPath", visibleMethod = "isUsePagination")
      }
   ),
   @View1("jsonPath"),
   @View1(type = ViewType.LABEL, text = "Example: $.store.book"),
   @View1(type = ViewType.PANEL, colspan = 2, elements = {
      @View2(value = "expanded", paddingRight = 30),
      @View2(value = "expandTop", visibleMethod = "isExpanded", align = ViewAlign.FILL)
   }),
   @View1(value = "expandedPath", visibleMethod = "isExpandedPathEnabled"),
   @View1(value = "timeout"),
})
public class ShopifyQuery extends GraphQLQuery {
   public ShopifyQuery() {
      super(ShopifyDataSource.TYPE);
      setCursorPagination(true);
   }

   @Override
   public String getContentType() {
      return "application/json";
   }

   public String getRequestType() {
      return "POST";
   }
}
