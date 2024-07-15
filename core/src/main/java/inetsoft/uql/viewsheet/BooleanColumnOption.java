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
package inetsoft.uql.viewsheet;

import java.io.PrintWriter;

/**
 * BooleanColumnOption stores column options for FormRef.
 *
 * @version 11.5
 * @author InetSoft Technology Corp
 */
public class BooleanColumnOption extends ColumnOption {
   /**
    * Constructor
    */
   public BooleanColumnOption() {
   }

   /**
    * Constructor
    */
   public BooleanColumnOption(boolean form) {
      this.form = form;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      super.writeXML(writer);
   }

   /**
    * Get column option type.
    */
   @Override
   public String getType() {
      return ColumnOption.BOOLEAN;
   }
}