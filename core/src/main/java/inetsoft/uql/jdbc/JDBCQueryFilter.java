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
package inetsoft.uql.jdbc;

import inetsoft.uql.VariableTable;

import java.security.Principal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;

/**
 * Interface for classes that filter JDBC queries about to be executed.
 *
 * @author InetSoft Technology
 * @since  12.0
 */
public abstract class JDBCQueryFilter {
   /**
    * Filters a SQL statement about to be executed.
    *
    * @param dataSource the data source to which the query applies.
    * @param connection the database connection that will be used to execute
    *                   the query.
    * @param sql        the SQL statement to be executed.
    * @param parameters the parameters used when executing the query.
    * @param user       the user for whom the query is being executed.
    *
    * @return the modified SQL statement to be executed or <tt>null</tt> if the
    *         original statement should be used.
    *
    * @throws SQLException if a database error occurs.
    */
   public abstract String filterQuery(JDBCDataSource dataSource,
                                      Connection connection,
                                      String sql, VariableTable parameters,
                                      Principal user)
      throws SQLException;

   /**
    * Gets the parameters that are used by this filter to modify queries.
    *
    * @param parameters the input parameters.
    *
    * @return the set of included parameter names.
    */
   public Set<String> getIncludedParameters(VariableTable parameters) {
      return Collections.emptySet();
   }
}
