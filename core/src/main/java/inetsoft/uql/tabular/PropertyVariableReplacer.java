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
package inetsoft.uql.tabular;

import inetsoft.uql.VariableTable;

/**
 * Implemented by a tabular query bean whose text properties need their own variable
 * substitution instead of the default URL-encoding one (Bug #79001).
 */
public interface PropertyVariableReplacer {
   /**
    * Replaces the {@code $(name)} references in the value of a property.
    *
    * @param property the bean property name.
    * @param value    the property text, with environment variables already resolved.
    * @param vars     the variables.
    *
    * @return the substituted text, or {@code null} to use the default substitution.
    */
   String replaceVariables(String property, String value, VariableTable vars);
}
