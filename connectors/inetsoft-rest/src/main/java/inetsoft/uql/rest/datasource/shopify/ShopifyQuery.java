/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
         @View2(type = ViewType.EDITOR, value = "queryString")
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
   }

   @Override
   public String getRequestBody() {
      return getQueryString();
   }

   @Override
   public PaginationType getPaginationType() {
      return PaginationType.NONE;
   }

   @Override
   public String getContentType() {
      return "application/graphql";
   }

   public String getRequestType() {
      return "POST";
   }
}
