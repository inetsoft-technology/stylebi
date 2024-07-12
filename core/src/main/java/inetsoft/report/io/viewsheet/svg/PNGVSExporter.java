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
package inetsoft.report.io.viewsheet.svg;

import inetsoft.uql.viewsheet.FileFormatInfo;

import java.io.OutputStream;

/**
 * Viewsheet exporter that creates PNG images.
 *
 * @since 12.1
 */
public class PNGVSExporter extends SVGVSExporter {
   /**
    * Creates a new instance of <tt>PNGVSExporter</tt>.
    *
    * @param output the output stream to which the image will be written.
    */
   public PNGVSExporter(OutputStream output) {
      super(output, new PNGCoordinateHelper());
   }

   @Override
   public int getFileFormatType() {
      return FileFormatInfo.EXPORT_TYPE_PNG;
   }
}