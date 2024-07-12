/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.content.database.types;

/**
 * Implementation of <tt>DatabaseType</tt> for Sybase databases for jdbc3.
 */
public final class SybaseJDBC3DatabaseType extends AbstractSybaseDatabaseType {
   /**
    * Creates a new instance of <tt>SybaseDatabaseType</tt>.
    */
   public SybaseJDBC3DatabaseType() {
      super(DRIVER);
   }

   private static final String DRIVER = "com.sybase.jdbc3.jdbc.SybDriver";
}
