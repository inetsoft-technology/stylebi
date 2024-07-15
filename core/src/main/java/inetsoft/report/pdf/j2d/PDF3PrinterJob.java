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
package inetsoft.report.pdf.j2d;

import inetsoft.report.*;
import inetsoft.report.pdf.PDF3Printer;
import inetsoft.sree.SreeEnv;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.print.*;
import java.io.*;

/**
 * Custom printer driver for PDF environment.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PDF3PrinterJob extends PrinterJob {
   /**
    * Create a PDF3PrinterJob. The printDialog() must be called to allow
    * users to select a file to output the file.
    */
   public PDF3PrinterJob() {
      psg = createPrinter();
      psg.setPrinterJob(this);
   }

   /**
    * Create a PDF3PrinterJob.
    */
   public PDF3PrinterJob(OutputStream output) {
      psg = createPrinter();
      psg.setOutput(this.output = output);
      psg.setPrinterJob(this);
   }

   /**
    * Create a PDF3PrinterJob.
    */
   public PDF3PrinterJob(File output) throws IOException {
      this(new FileOutputStream(output));
   }

   /**
    * Create a PDF3Printer for printing.
    */
   protected PDF3Printer createPrinter() {
      return new PDF3Printer();
   }

   /**
    * Get the PDF3Printer instance used to generate the PDF file.
    */
   public PDF3Printer getPrinter2D() {
      return psg;
   }

   /**
    * Set the page size in inches.
    * @param width page width.
    * @param height page height.
    */
   public void setPageSize(double width, double height) {
      paper.setSize(width * 72, height * 72);
   }

   /**
    * Set the page size in inches. Common paper sizes are defined
    * as constants in StyleConstants.
    * @param size Size object in inches.
    */
   public void setPageSize(Size size) {
      setPageSize(size.width, size.height);
   }

   /**
    * Get the current default page size.
    */
   public Size getPageSize() {
      return new Size(paper.getWidth() / 72, paper.getHeight() / 72);
   }

   /**
    * Set whether to compress the text object and streams in the PDF.
    * Currently only Zip compression is supported, as the consequence
    * the output is only compatible with Acrobat 3.0 and later versions.
    * By default this is true.
    * @param comp compression option.
    */
   public void setCompressText(boolean comp) {
      psg.setCompressText(comp);
   }

   /**
    * Check if compression is on.
    * @return true if text objects are compressed.
    */
   public boolean isCompressText() {
      return psg.isCompressText();
   }

   /**
    * Set whether the output should only contain 7 bits ascii code only.
    * It defaults to false.
    * @param ascii output ascii only.
    */
   public void setAsciiOnly(boolean ascii) {
      psg.setAsciiOnly(ascii);
   }

   /**
    * Check if the output is ascii only.
    * @return true if ascii only.
    */
   public boolean isAsciiOnly() {
      return psg.isAsciiOnly();
   }

   /**
    * Set whether to compress the image object and streams in the PDF.
    * Currently only Zip compression is supported, as the consequence
    * the output is only compatible with Acrobat 3.0 and later versions.
    * By default this is true.
    * @param comp compression option.
    */
   public void setCompressImage(boolean comp) {
      psg.setCompressImage(comp);
   }

   /**
    * Check if compression is on.
    * @return true if image objects are compressed.
    */
   public boolean isCompressImage() {
      return psg.isCompressImage();
   }

   /**
    * Set whether to use base14 fonts only.
    * @param base14 true to use base14 fonts only.
    */
   public void setBase14Only(boolean base14) {
      psg.setBase14Only(base14);
   }

   /**
    * Check whether to use base14 fonts only.
    */
   public boolean isBase14Only() {
      return psg.isBase14Only();
   }

   /**
    * Set whether to embed fonts in PDF.
    * @param embed true to embed fonts.
    */
   public void setEmbedFont(boolean embed) {
      psg.setEmbedFont(embed);
   }

   /**
    * Check whether to embed fonts in PDF.
    */
   public boolean isEmbedFont() {
      return psg.isEmbedFont();
   }

   /**
    * Sets the names of the fonts that should be fully embedded. All other fonts
    * will only have the subset of those characters used in the PDF embedded.
    *
    * @param fullyEmbeddedFonts the font names.
    */
   public void setFullyEmbeddedFonts(String[] fullyEmbeddedFonts) {
      psg.setFullyEmbeddedFonts(fullyEmbeddedFonts);
   }

   /**
    * Gets the names of the fonts that should be fully embedded. All other fonts
    * will only have the subset of those characters used in the PDF embedded.
    *
    * @return the font names.
    */
   public String[] getFullyEmbeddedFonts() {
      return psg.getFullyEmbeddedFonts();
   }

   /**
    * Calls <code>painter</code> to render the pages.  The pages in the
    * document to be printed by this
    * <code>PrinterJob</code> are rendered by the {@link Printable}
    * object, <code>painter</code>.  The {@link PageFormat} for each page
    * is the default page format.
    * @param painter the <code>Printable</code> that renders each page of
    * the document.
    */
   @Override
   public void setPrintable(Printable painter) {
      this.painter = painter;
   }

   /**
    * Calls <code>painter</code> to render the pages in the specified
    * <code>format</code>.  The pages in the document to be printed by
    * this <code>PrinterJob</code> are rendered by the
    * <code>Printable</code> object, <code>painter</code>. The
    * <code>PageFormat</code> of each page is <code>format</code>.
    * @param painter the <code>Printable</code> called to render
    *		each page of the document
    * @param format the size and orientation of each page to
    *                   be printed
    */
   @Override
   public void setPrintable(Printable painter, PageFormat format) {
      setPrintable(painter);
      this.format = format;
   }

   /**
    * Queries <code>document</code> for the number of pages and
    * the <code>PageFormat</code> and <code>Printable</code> for each
    * page held in the <code>Pageable</code> instance,
    * <code>document</code>.
    * @param document the pages to be printed. It can not be
    * <code>null</code>.
    * @exception NullPointerException the <code>Pageable</code> passed in
    * was <code>null</code>.
    * @see PageFormat
    * @see Printable
    */
   @Override
   public void setPageable(Pageable document) throws NullPointerException {
      this.book = document;
   }

   /**
    * Presents a dialog to the user for changing the properties of
    * the print job.
    * @return <code>true</code> if the user does not cancel the dialog;
    * <code>false</code> otherwise.
    */
   @Override
   public boolean printDialog() {
      FileDialog chooser = new FileDialog(Tool.getInvisibleFrame(),
         Catalog.getCatalog().getString("PDF File"), FileDialog.SAVE);

      chooser.setFile(jobname + ".pdf");
      chooser.setDirectory(".");
      chooser.pack();
      chooser.setVisible(true);

      String fname = chooser.getFile();

      if(fname != null) {
         try {
            File file = FileSystemService.getInstance().getFile(chooser.getDirectory(), fname);

            psg.setOutput(output = new FileOutputStream(file));
            return true;
         }
         catch(Exception e) {
            LOG.error("Failed to open PDF file: " + file, e);
         }
      }

      return false;
   }

   /**
    * Displays a dialog that allows modification of a
    * <code>PageFormat</code> instance.
    * The <code>page</code> argument is used to initialize controls
    * in the page setup dialog.
    * If the user cancels the dialog then this method returns the
    * original <code>page</code> object unmodified.
    * If the user okays the dialog then this method returns a new
    * <code>PageFormat</code> object with the indicated changes.
    * In either case, the original <code>page</code> object is
    * not modified.
    * @param page the default <code>PageFormat</code> presented to the
    *			user for modification
    * @return    the original <code>page</code> object if the dialog
    *            is cancelled; a new <code>PageFormat</code> object
    *		  containing the format indicated by the user if the
    *		  dialog is acknowledged.
    * @since     JDK1.2
    */
   @Override
   public PageFormat pageDialog(PageFormat page) {
      return defJob.pageDialog(page);
   }

   /**
    * Clones the <code>PageFormat</code> argument and alters the
    * clone to describe a default page size and orientation.
    * @param page the <code>PageFormat</code> to be cloned and altered
    * @return clone of <code>page</code>, altered to describe a default
    *                      <code>PageFormat</code>.
    */
   @Override
   public PageFormat defaultPage(PageFormat page) {
      PageFormat fmt = (PageFormat) page.clone();

      fmt.setPaper(paper);
      return fmt;
   }

   /**
    * Alters the <code>PageFormat</code> argument to be usable on
    * this <code>PrinterJob</code> object's current printer.
    * @param page this page description is cloned and then its settings
    *		are altered to be usuable for this
    *		<code>PrinterJob</code>
    * @return a <code>PageFormat</code> that is cloned from
    *		the <code>PageFormat</code> parameter and altered
    *		to conform with this <code>PrinterJob</code>.
    */
   @Override
   public PageFormat validatePage(PageFormat page) {
      page.setPaper(paper);
      return page;
   }

   /**
    * Prints a set of pages.
    * @exception PrinterException an error in the print system
    *            caused the job to be aborted.
    */
   @Override
   public void print() throws PrinterException {
      try {
         if(output == null || toFile) {
            psg.setOutput(output = new FileOutputStream(file));
         }
      }
      catch(Exception e) {
         throw new PrinterException(e.toString());
      }

      synchronized(ReportSheet.class) {
         Margin omargin = ReportSheet.getPrinterMargin();

         if(book != null) {
            int from = 1;
            int to = book.getNumberOfPages();

            psg.startDoc();

            for(int i = from; i <= to; i++) {
               int n = i - 1;
               PageFormat fmt = book.getPageFormat(n);

               if(fmt.getOrientation() == StyleConstants.LANDSCAPE) {
                  psg.setPageSize(fmt.getPaper().getHeight() / 72,
                     fmt.getPaper().getWidth() / 72);
               }
               else {
                  psg.setPageSize(fmt.getPaper().getWidth() / 72,
                     fmt.getPaper().getHeight() / 72);
               }

               book.getPrintable(n).print(psg, fmt, i);
               psg.dispose();
            }
         }
         else if(painter != null) {
            psg.startDoc();

            if(format.getOrientation() == StyleConstants.LANDSCAPE) {
               psg.setPageSize(format.getPaper().getHeight() / 72,
                  format.getPaper().getWidth() / 72);
            }
            else {
               psg.setPageSize(format.getPaper().getWidth() / 72,
                  format.getPaper().getHeight() / 72);
            }

            for(int i = 0; true; i++) {
               if(painter.print(psg, format, i) == Printable.NO_SUCH_PAGE) {
                  break;
               }

               psg.dispose();
            }
         }

         ReportSheet.setPrinterMargin(omargin);
         psg.close();
      }
   }

   /**
    * Sets the number of copies to be printed.
    * @param copies the number of copies to be printed
    */
   @Override
   public void setCopies(int copies) {
      this.copies = copies;
   }

   /**
    * Gets the number of copies to be printed.
    * @return the number of copies to be printed.
    */
   @Override
   public int getCopies() {
      return copies;
   }

   /**
    * Gets the name of the printing user.
    * @return the name of the printing user
    */
   @Override
   public String getUserName() {
      try {
         return SreeEnv.getProperty("user.name");
      }
      catch(Exception e) {
      }

      return "Unknown";
   }

   /**
    * Sets the name of the document to be printed.
    * The document name can not be <code>null</code>.
    * @param jobName the name of the document to be printed
    */
   @Override
   public void setJobName(String jobName) {
      this.jobname = jobName;
   }

   /**
    * Gets the name of the document to be printed.
    * @return the name of the document to be printed.
    */
   @Override
   public String getJobName() {
      return jobname;
   }

   /**
    * Set the file name for 'print to file'.
    * @param file file name.
    */
   public void setFile(String file) {
      this.file = file;
   }

   /**
    * Get the print to file name.
    * @return file name.
    */
   public String getFile() {
      return file;
   }

   /**
    * Set the 'print to file' option.
    * param toFile true to print to file (PDF output)
    */
   public void setPrintToFile(boolean toFile) {
      this.toFile = toFile;
   }

   /**
    * Check if 'print to file' is selected.
    * @return true if 'print to file' is selected.
    */
   public boolean isPrintToFile() {
      return toFile;
   }

   /**
    * Set the destination printer name.
    * @param printer printer name.
    */
   public void setPrinter(String printer) {
      this.printer = printer;
   }

   /**
    * Get the destination printer name.
    * @return printer name.
    */
   public String getPrinter() {
      return printer;
   }

   /**
    * Cancels a print job that is in progress.  If
    * {@link #print() print} has been called but has not
    * returned then this method signals
    * that the job should be cancelled at the next
    * chance. If there is no print job in progress then
    * this call does nothing.
    */
   @Override
   public void cancel() {
      cancelled = true;
   }

   /**
    * Returns <code>true</code> if a print job is
    * in progress, but is going to be cancelled
    * at the next opportunity; otherwise returns
    * <code>false</code>.
    * @return <code>true</code> if the job in progress
    * is going to be cancelled; <code>false</code> otherwise.
    */
   @Override
   public boolean isCancelled() {
      return cancelled;
   }

   PDF3Printer psg;
   OutputStream output; // pdf output stream
   Printable painter = null;
   Pageable book = null;
   PageFormat format = new PageFormat();
   Paper paper = new Paper();
   // parameters set through the dialog
   int copies = 1;
   String jobname = "Report";
   String printer = "";
   String printOption = "";
   String file = "report.pdf";
   boolean toFile = false;
   boolean cancelled = false;
   PrinterJob defJob = PrinterJob.getPrinterJob();

   private static final Logger LOG =
      LoggerFactory.getLogger(PDF3PrinterJob.class);
}
