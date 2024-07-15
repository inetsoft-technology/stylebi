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
package inetsoft.report.io;

import inetsoft.report.*;
import inetsoft.report.event.ProgressListener;
import inetsoft.report.style.XTableStyle;

import java.io.IOException;

/**
 * Formatter is responsible for converting report and report elements
 * into an external format. This defines the API of all formatter
 * classes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface Formatter {
   /**
    * Export a report.
    * @param sheet report to export.
    */
   public void write(ReportSheet sheet) throws IOException;

   /**
    * Write the prolog of report.
    */
   public void prolog(ReportSheet sheet);

   /**
    * Start all headers. for header selected storage.
    */
   default void startHeaders(String headerSelected, String footerSelected) {
      // no op
   }

   /**
    * Start a header section.
    * @param type header/footer type. Defined in ReportSheet.
    */
   public void startHeader(int type);

   /**
    * Start a header selection for element associated header/footer.
    * @param eid element ID.
    * @param header true if header, false if footer.
    */
   public void startHeader(String eid, boolean header);

   /**
    * End a header section.
    */
   public void endHeader();

   /**
    * Start all headers.
    */
   default void endHeaders() {
      // no op
   }

   /**
    * Write embedded table style.
    */
   public void write(XTableStyle style);

   /**
    * Write the text element.
    */
   public void write(TextElement elem);

   /**
    * Write the table element.
    */
   public void write(TableElement elem);

   /**
    * Write the section element.
    */
   public void write(SectionElement elem) throws IOException;

   /**
    * Write the painter element.
    */
   public void write(PainterElement elem);

   /**
    * Write the chart element.
    *
    * @since 10.1
    */
   public void write(ChartElement elem);

   /**
    * Write the textbox element.
    */
   public void write(TextBoxElement elem);

   /**
    * Write the newline element.
    */
   public void write(NewlineElement elem);

   /**
    * Write the pagebreak element.
    */
   public void write(PageBreakElement elem);

   /**
    * Write the conditional page break element.
    */
   public void write(CondPageBreakElement elem);

   /**
    * Write the space element.
    */
   public void write(SpaceElement elem);

   /**
    * Write the end of report.
    */
   public void end() throws IOException;

   /**
    * Add a listener to be notified of export progress.
    */
   public void addProgressListener(ProgressListener listener);

   /**
    * Remove a listener.
    */
   public void removeProgressListener(ProgressListener listener);
}
