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
package inetsoft.report.io.viewsheet.excel;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Encapsulate the workbook and related resources, to implement ExcelContext.
 *
 * @version 8.5, 7/19/2006
 * @author InetSoft Technology Corp
 */
public class ExcelExporter implements ExcelContext {
   /**
    * Get work book.
    * If other people got workbook, they can made uncontrollerable change to it.
    * @return the specified Workbook created in setUp method.
    */
   @Override
   public Workbook getWorkbook() {
      return book;
   }

   /**
    * Create a workbook.
    */
   public boolean setUp() {
      book = new XSSFWorkbook();

      return true;
   }

   private Workbook book = null;
}
