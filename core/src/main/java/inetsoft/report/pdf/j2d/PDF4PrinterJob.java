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
package inetsoft.report.pdf.j2d;

import inetsoft.report.pdf.PDF3Printer;
import inetsoft.report.pdf.PDF4Printer;

import java.io.*;

/**
 * Custom printer driver for PDF environment.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PDF4PrinterJob extends PDF3PrinterJob {
   /**
    * Create a PDF4PrinterJob. The printDialog() must be called to allow
    * users to select a file to output the file.
    */
   public PDF4PrinterJob() {
   }

   /**
    * Create a PDF4PrinterJob.
    */
   public PDF4PrinterJob(OutputStream output) {
      super(output);
   }

   /**
    * Create a PDF4PrinterJob.
    */
   public PDF4PrinterJob(File output) throws IOException {
      this(new FileOutputStream(output));
   }

   /**
    * Create a PDF3Printer2D for printing.
    */
   @Override
   protected PDF3Printer createPrinter() {
      return new PDF4Printer();
   }

   /**
    * Set whether to embed cmaps in PDF.
    * @param embed true to embed cmaps.
    */
   public void setEmbedCMap(boolean embed) {
      ((PDF4Printer) psg).setEmbedCMap(embed);
   }

   /**
    * Check whether to embed cmaps in PDF.
    */
   public boolean isEmbedCMap() {
      return ((PDF4Printer) psg).isEmbedCMap();
   }
}

