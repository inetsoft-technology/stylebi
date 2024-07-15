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
package inetsoft.report;

/**
 * A newline element forces a report to advanced to the next line.
 */
public interface NewlineElement extends ReportElement {
   /**
    * Get the number of linefeeds.
    * @return number of linefeeds.
    */
   public int getCount();
   
   /**
    * Set the number of line feeds. The number of line feed should be at
    * least one.
    * @param count number of line feeds.
    */
   public void setCount(int count);

   /**
    * Check if this is a regular newline or a break.
    */
   public boolean isBreak();

   /**
    * Set this to be break or newline.
    */
   public void setBreak(boolean linefeed);
}

