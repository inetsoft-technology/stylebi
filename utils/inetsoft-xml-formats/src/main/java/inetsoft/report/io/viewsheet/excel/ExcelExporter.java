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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.report.internal.Util;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.image.BufferedImage;

/**
 * Encapsulate the workbook and related resources, to implement ExcelContext.
 *
 * @version 8.5, 7/19/2006
 * @author InetSoft Technology Corp
 */
public class ExcelExporter implements ExcelContext {
   public ExcelExporter() {

   }

   public ExcelExporter(int memoryCacheRowCount) {
      this.memoryCacheRowCount = memoryCacheRowCount;
   }
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
      book = memoryCacheRowCount > 0 ? new SXSSFWorkbook(memoryCacheRowCount) : new XSSFWorkbook();

      try {
         LicenseManager licenseManager = LicenseManager.getInstance();

         if(licenseManager.isElasticLicense() && licenseManager.getElasticRemainingHours() == 0) {
            BufferedImage image = Util.createWatermarkImage();
            byte[] buf = VSUtil.getImageBytes(image, 72 * 2);
            bgId = book.addPicture(buf, Workbook.PICTURE_TYPE_PNG);
         }
      }
      catch(Exception ignore) {
      }

      return true;
   }

   @Override
   public int getBackgroupPictureId() {
      return bgId;
   }

   private Workbook book = null;
   private int memoryCacheRowCount = -1;
   private int bgId = -1;
}
