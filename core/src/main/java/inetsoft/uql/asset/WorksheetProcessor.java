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
package inetsoft.uql.asset;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTable;

import java.security.Principal;

/**
 * This interface defines the API for processing and executing worksheets.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface WorksheetProcessor {
   /**
    * Execute a worksheet and return the data for the primary table assembly.
    * If the primary assembly is not a table, a null is returned.
    * @param entry worksheet asset entry.
    * @param vars execution parameters.
    * @param user current user.
    */
   public XTable execute(AssetEntry entry, VariableTable vars, Principal user)
         throws Exception;
}
