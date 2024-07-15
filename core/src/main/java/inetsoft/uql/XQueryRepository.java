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
package inetsoft.uql;

import java.util.Enumeration;

/**
 * Query repository contains query objects.
 * @version 6.5
 * @author InetSoft Technology Corp
 */
public interface XQueryRepository {
   /**
    * Normal query repository.
    */
   int NORMAL_QUERY = 1;
   /**
    * Local query repository.
    */
   int LOCAL_QUERY = 2;

   /**
    * Global scope.
    */
   int GLOBAL_SCOPE = 0;
   /**
    * Local scope
    */
   int LOCAL_SCOPE = 1;

   /**
    * Get query type in the query repository.
    * @return query type which is one of the predefined types in query
    * repository.
    */
   int getQueryType();

   /**
    * Add or replace a query in the query repository.
    * @param dx new query.
    * @param oname old name of the query, if the name has been changed.
    * Otherwise it should be null.
    * @param queryChange
    */
   default void updateQuery(XQuery dx, String oname, boolean queryChange) throws Exception {
   }

   /**
    * Add or replace a query in the query repository.
    * @param dx new query.
    * @param oname old name of the query, if the name has been changed.
       Otherwise it should be null.
    */
   default void updateQuery(XQuery dx, String oname) throws Exception {
      updateQuery(dx, oname, true);
   };
}