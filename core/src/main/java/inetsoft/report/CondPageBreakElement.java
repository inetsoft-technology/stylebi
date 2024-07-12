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
package inetsoft.report;

/**
 * A conditional page break forces report to advance to the next page
 * if the remaining space on the current page is less than the specified
 * height.
 */
public interface CondPageBreakElement extends ReportElement {
   /**
    * Get the conditional height in inches.
    */
   public double getCondHeight();
   
   /**
    * Set the conditional height in inches. If the remaining space on
    * the current page is less than the height, the report advances to
    * the next page.
    */
   public void setCondHeight(double inch);
}

