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

public interface CalcAggregate extends DataRefWrapper {
   /**
    * Get the formula.
    * @return the formula name.
    */
   String getFormulaName();

   /**
    * Set the formula name to the aggregate ref.
    * @param f name the specified formula.
    */
   void setFormulaName(String f);

   /**
    * Get the percentage type.
    *
    * @return the percentage type
    */
   int getPercentageType();

   /**
    * Set the percentage type.
    *
    * @param type the specified percentage type
    */
   void setPercentageType(int type);
}