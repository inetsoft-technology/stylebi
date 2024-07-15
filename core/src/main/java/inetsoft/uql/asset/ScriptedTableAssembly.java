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
package inetsoft.uql.asset;

/**
 * {@code ScriptedTableAssembly} is an interface for tables that are bound to scripted queries.
 */
public interface ScriptedTableAssembly {
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
