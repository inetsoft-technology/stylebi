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
package inetsoft.report.io.export;

import inetsoft.report.io.Generator;
import inetsoft.report.pdf.PDF3Generator;

import java.io.OutputStream;

/**
 * ExportFactory that creates instances of PDF3Generator.
 *
 * @author InetSoft Technology
 * @since  7.0
 */
public class PDF3GeneratorFactory extends GeneratorFactory {
   /**
    * Creates the generator for the export type supported by this factory.
    *
    * @param output the output stream to which the exported file will be
    *               written.
    * @param data data specific to the type of export.
    *
    * @return a Generator object used to export a report.
    */
   @Override
   protected Generator createGenerator(OutputStream output, Object data) {
      return PDF3Generator.getPDFGenerator(output);
   }
}
