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
package inetsoft.report.io.viewsheet;

import inetsoft.report.io.viewsheet.excel.*;
import inetsoft.report.io.viewsheet.ppt.PPTExporter;
import inetsoft.report.io.viewsheet.ppt.PPTVSExporter;
import org.apache.xmlbeans.impl.schema.SchemaTypeSystemImpl;

import java.io.OutputStream;

public class PoiOfficeExporterFactory implements OfficeExporterFactory {
   static {
      SchemaTypeSystemImpl.METADATA_PACKAGE_GEN = "inetsoft/xml/org/apache/xmlbeans/metadata";
   }

   @Override
   public AbstractVSExporter createExcelExporter(OutputStream stream) {
      ExcelExporter book = new ExcelExporter();
      book.setUp();
      return new OfflineExcelVSExporter(book, stream);
   }

   @Override
   public AbstractVSExporter createPowerpointExporter(OutputStream stream) {
      PPTExporter show = new PPTExporter();
      show.setUp();
      return new PPTVSExporter(show, stream);
   }

   @Override
   public WSExporter createWorksheetExporter() {
      return new ExcelWSExporter();
   }
}
