/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
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
