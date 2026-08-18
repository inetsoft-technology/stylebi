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
package inetsoft.uql.rest.datasource.jira;

import inetsoft.uql.rest.json.AbstractEndpoint;
import inetsoft.uql.rest.pagination.PaginationType;

import java.util.Objects;

public class JiraEndpoint extends AbstractEndpoint {
   /**
    * How this endpoint pages, when it does not page the way the rest of Jira does.
    *
    * <p>Nearly every Jira endpoint pages by {@code startAt} against a {@code total} the response
    * carries. The enhanced issue search does not: it returns an opaque {@code nextPageToken} and no
    * total at all. Null means the usual offset paging, so the property only has to be set on the
    * exceptions.</p>
    */
   public PaginationType getPageType() {
      return pageType;
   }

   public void setPageType(PaginationType pageType) {
      this.pageType = pageType;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      JiraEndpoint that = (JiraEndpoint) o;
      return pageType == that.pageType;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), pageType);
   }

   private PaginationType pageType;
}
