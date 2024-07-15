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

import inetsoft.report.DocumentInfo;
import inetsoft.report.ReportSheet;
import inetsoft.report.event.ProgressListener;
import inetsoft.report.internal.paging.ReportCache;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;

/**
 * This interface is the common interface for all report export generators.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface Generator {
   /**
    * Set the document info to export.
    */
   void setDocumentInfo(DocumentInfo info);

   /**
    * Set the output stream of this generator.
    */
   void setOutput(OutputStream output);
   
   /**
    * Get the output stream used by the generator.
    */
   OutputStream getOutput();
   
   /**
    * Export a report.
    * @param sheet report to export.
    */
   void generate(ReportSheet sheet) throws IOException;
   
   /**
    * Write a collection of pages to text.
    */
   void generate(ReportSheet sheet, Enumeration<?> pages) throws IOException;

   /**
    * Write a collection of pages to text. This is an optional operation and may not be implemented
    * by all generators.
    *
    * @param pages the pages to write.
    *
    * @throws IOException if an I/O error occurs.
    */
   default void generate(Enumeration<?> pages) throws IOException {
      throw new UnsupportedOperationException();
   }
   
   /**
    * Add a listener to be notified of export progress.
    */
   void addProgressListener(ProgressListener listener);
   
   /**
    * Remove a listener.
    */
   void removeProgressListener(ProgressListener listener);

   /**
    * Set report cache.
    */
   void setReportCache(ReportCache repcache);
   
   /**
    * Get report cache;
    */
   ReportCache getReportCache();

   /**
    * Set report ID;
    */
   void setReportId(Object repId);

   /**
    * Get report ID;
    */
   Object getReportId();

   /**
    * Cancel the generation.
    */
   void cancel();
}

