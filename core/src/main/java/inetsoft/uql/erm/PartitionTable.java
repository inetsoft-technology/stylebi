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
package inetsoft.uql.erm;

public class PartitionTable {
   /**
    * Flag indicating that a table is physical table.
    */
   public static final int PHYSICAL = 0;

   /**
    * Flag indicating that a table is embedded view.
    */
   public static final int VIEW = 1;

   /**
    * Flag indicating that a selection event is part of a subselection.
    */
   static final int SUBSELECTION = 4;
}
