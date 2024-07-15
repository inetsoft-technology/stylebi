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
package inetsoft.util.db;

import inetsoft.util.config.DatabaseConfig;

/**
 * Class that encapsulates the SQL statements specific to a type of database.
 *
 * @since 12.2
 */
public class Database {
   /**
    * Gets the type of database to which the statements apply.
    *
    * @return the database type.
    */
   public DatabaseConfig.DatabaseType getType() {
      return type;
   }

   /**
    * Sets the type of database to which the statements apply.
    *
    * @param type the database type.
    */
   public void setType(DatabaseConfig.DatabaseType type) {
      this.type = type;
   }

   /**
    * Gets the SQL statements.
    *
    * @return the statements.
    */
   public Statements[] getStatements() {
      return statements;
   }

   /**
    * Sets the SQL statements.
    *
    * @param statements the statements.
    */
   public void setStatements(Statements[] statements) {
      this.statements = statements;
   }

   private DatabaseConfig.DatabaseType type;
   private Statements[] statements;
}
