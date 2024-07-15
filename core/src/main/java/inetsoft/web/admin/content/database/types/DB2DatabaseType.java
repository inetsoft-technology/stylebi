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
package inetsoft.web.admin.content.database.types;

import org.springframework.stereotype.Component;

/**
 * Implementation of <tt>DatabaseType</tt> for DB2 databases.
 */
@Component
public class DB2DatabaseType extends AbstractDB2DatabaseType {
   /**
    * Creates a new instance of <tt>DB2DatabaseType</tt>.
    */
   public DB2DatabaseType() {
      super(DRIVER);
   }

   private static final String DRIVER = "com.ibm.db2.jcc.DB2Driver";
}
