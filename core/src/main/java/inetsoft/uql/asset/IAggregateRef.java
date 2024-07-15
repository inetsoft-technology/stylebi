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
package inetsoft.uql.asset;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.DataRefWrapper;

/**
 * A IAggregateRef object represents a aggregate reference.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface IAggregateRef extends DataRefWrapper {
   /**
    * Get the formula.
    * @return the formula of the aggregate ref.
    */
   public AggregateFormula getFormula();

   /**
    * Set the formula.
    */
   public void setFormula(AggregateFormula formula);

   /**
    * Get the percentage option of this reference.
    * @return the percentage option of this reference.
    */
   public int getPercentageOption();

   /**
    * Set the percentage option of this reference.
    * @param percentage the percentage option of this reference.
    */
   public void setPercentageOption(int percentage);

   /**
    * Get the formula secondary column.
    * @return secondary column.
    */
   public DataRef getSecondaryColumn();

   /**
    * Set the secondary column to be used in the formula.
    * @param ref formula secondary column.
    */
   public void setSecondaryColumn(DataRef ref);

   /**
    * Get the N for formula requiring a N (e.g. nth, pth).
    */
   public int getN();
}