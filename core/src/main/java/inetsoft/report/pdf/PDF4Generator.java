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
package inetsoft.report.pdf;

import java.io.OutputStream;

/**
 * PDF4Generator is a PDF generator that supports generation of Table Of
 * Contents bookmarks in PDF. It also supports CJK characters using Adobe
 * asian font pack. This class should not be used directly. Call
 * PDF3Generator.getPDFGenerator() to get a PDF generator.
 *
 * @version 5.1, 9/20/2003
 * @author Inetsoft Technology
 */
public class PDF4Generator extends PDF3Generator {
   /**
    * Create a PDF generator.
    */
   public PDF4Generator() {
   }

   /**
    * Create a generator to the specified output.
    */
   public PDF4Generator(OutputStream out) {
      super(out);
   }

   /**
    * Get the PDF4Printer used inside this generator.
    */
   @Override
   public PDF3Printer getPrinter() {
      if(printer == null) {
         printer = new PDF4Printer(getOutput());
      }

      return printer;
   }

   /**
    * Output stream.
    */
   PDF4Printer printer = null;
}

