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
package inetsoft.uql.util;

import java.util.EventObject;

/**
 * Event that is fired when a query execution begins or ends.
 */
public class QueryExecutionEvent extends EventObject {
   /**
    * Creates a new instance of {@code QueryExecutionEvent}.
    *
    * @param source  the source of the event.
    * @param queryId the identifier of the query.
    */
   public QueryExecutionEvent(Object source, String queryId) {
      super(source);
      this.queryId = queryId;
   }

   /**
    * Gets the query identifier.
    *
    * @return the query identifier.
    */
   public String getQueryId() {
      return queryId;
   }

   private final String queryId;
}
