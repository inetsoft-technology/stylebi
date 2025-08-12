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
package inetsoft.uql.rest;

import inetsoft.uql.XTableNode;
import inetsoft.uql.util.BaseJsonTable;

public interface QueryRunner {
   /**
    * Executes the strategy and returns a table containing the result. The streaming will be
    * finished after this call if it's not cancelled.
    */
   XTableNode runStream();

   /**
    * Check if the execution is cancelled.
    */
   boolean isCancelled();

   /**
    * Check if this query is for preview in the designer UI.
    */
   boolean isLiveMode();

   /**
    * Runs the query and returns result.
    */
   default XTableNode run() {
      XTableNode table = runStream();

      if(isCancelled() && !isLiveMode()) {
         return null;
      }
      else if(table instanceof BaseJsonTable) {
         ((BaseJsonTable) table).finishStreamedLoading();

         if(((BaseJsonTable) table).size() == 0) {
            return null;
         }
      }

      return table;
   }

   default String generateMetadata() {
      return null;
   }
}
