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
package inetsoft.report.io;

import java.io.OutputStream;

/**
 * Interface for factory classes that create export generators and formatters.
 *
 * @author InetSoft Technology
 * @since  7.0
 */
public interface ExportFactory {
   /**
    * Type flag indicating that the factory creates a Generator instance.
    */
   public static final int GENERATOR = 1;

   /**
    * Type flag indicating that the factory creates a Formatter instance.
    */
   public static final int FORMATTER = 2;

   /**
    * The exporter will not access style page.
    */
   public static final int ZERO = 0;
   /**
    * The exporter will access every style page once.
    */
   public static final int ONCE = 1;
   /**
    * The exporter will access every style page for multiple times.
    */
   public static final int MULTIPLE_TIMES = 2;

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
   public Object createExporter(OutputStream output, Object data);

   /**
    * Gets the type of exporter created by this factory. Returns either
    * <code>GENERATOR</code> or <code>FORMATTER</code>.
    *
    * @return the type of exporter.
    */
   public int getExporterType();

   /**
    * Get the page access type, 0 - not required, 1 - once, 2 - more than once.
    */
   public int getPageAccess();
}
