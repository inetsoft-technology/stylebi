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
package inetsoft.uql.tabular;

/**
 * {@code ScriptedQuery} is an interface for query implementations that have pre- or post-processing
 * scripts applied.
 */
public interface ScriptedQuery {
   /**
    * Gets the script used before the query is executed to obtain inputs.
    *
    * @return the input script source or {@code null} if there is none.
    */
   String getInputScript();

   /**
    * Gets the script used after the query is executed to write outputs.
    *
    * @return the output script source or {@code null} if there is none.
    */
   String getOutputScript();
}
