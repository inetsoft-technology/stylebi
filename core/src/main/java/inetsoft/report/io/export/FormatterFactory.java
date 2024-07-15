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

import inetsoft.report.io.ExportFactory;
import inetsoft.report.io.Formatter;

import java.io.OutputStream;

/**
 * Abstract base class for all ExportFactory implementations that create
 * Formatter objects.
 *
 * @author InetSoft Technology
 * @since  7.0
 */
public abstract class FormatterFactory implements ExportFactory {
   /**
    * Creates the generator or formatter for the export type supported by this
    * factory. The returned object must be an instance of Generator or
    * Formatter as determined by the value returned by <code>getType</code>.
    *
    * @param output the output stream to which the exported file will be
    *               written.
    * @param data data specific to the type of export.
    *
    * @return a Generator or Formatter object used to export a report.
    */
   @Override
   public final Object createExporter(OutputStream output, Object data) {
      return createFormatter(output, data);
   }

   /**
    * Gets the type of exporter created by this factory. Returns either
    * <code>GENERATOR</code> or <code>FORMATTER</code>.
    *
    * @return the type of exporter.
    */
   @Override
   public final int getExporterType() {
      return FORMATTER;
   }

   /**
    * Get the page access type, 0 - not required, 1 - once, 2 - more than once.
    */
   @Override
   public int getPageAccess() {
      return ZERO;
   }

   /**
    * Creates the formatter for the export type supported by this factory.
    *
    * @param output the output stream to which the exported file will be
    *               written.
    * @param data data specific to the type of export.
    *
    * @return a Formatter object used to export a report.
    */
   protected abstract Formatter createFormatter(OutputStream output,
                                                Object data);
}
