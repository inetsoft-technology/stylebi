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
package inetsoft.report.filter;

/**
 * Formula Object implemented this interface to support percentage.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface PercentageFormula extends Formula {
   /**
    * Get percentage type.
    */
   public int getPercentageType();

   /**
    * Set percentage type.
    * three types: StyleConstants.PERCENTAGE_NONE,
    *              StyleConstants.PERCENTAGE_OF_GROUP,
    *              StyleConstants.PERCENTAGE_OF_GRANDTOTAL.
    */
   public void setPercentageType(int percentage);

   /**
    * Add percentage object. The total is used to calculate the percentage
    * of a summarization.
    * If percentage type is PERCENTAGE_NONE, it is ineffective to
    * invoke the method.
    */
   public void setTotal(Object total);

   /**
    * Get the original formula result without percentage.
    */
   public Object getOriginalResult();
}

