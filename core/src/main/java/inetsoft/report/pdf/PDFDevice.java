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
package inetsoft.report.pdf;

import inetsoft.report.Size;
import inetsoft.report.internal.CustomGraphics;

import java.awt.*;
import java.io.OutputStream;

/**
 * PDFDevice defines the common API for all pdf generation classes.
 *
 * @version 5.1, 9/20/2003
 * @author Inetsoft Technology
 */
public interface PDFDevice extends PrintGraphics, java.io.Serializable, 
                                   Cloneable, CustomGraphics {
   /**
    * Set the output stream for the PDF output.
    */
   void setOutput(OutputStream o);
   
   /**
    * Set whether to compress the text object and streams in the PDF.
    * Currently only Zip compression is supported, as the consequence
    * the output is only compatible with Acrobat 3.0 and later versions.
    * By default this is true.
    * @param comp compression option.
    */
   void setCompressText(boolean comp);
   
   /**
    * Check if compression is on.
    * @return true if text objects are compressed.
    */
   boolean isCompressText();
   
   /**
    * Set whether the output should only contain 7 bits ascii code only.
    * It defaults to false.
    * @param ascii output ascii only.
    */
   void setAsciiOnly(boolean ascii);
   
   /**
    * Check if the output is ascii only.
    * @return true if ascii only.
    */
   boolean isAsciiOnly();
   
   /**
    * Set whether to compress the image object and streams in the PDF.
    * Currently only Zip compression is supported, as the consequence
    * the output is only compatible with Acrobat 3.0 and later versions.
    * By default this is true.
    * @param comp compression option.
    */
   void setCompressImage(boolean comp);
   
   /**
    * Check if compression is on.
    * @return true if image objects are compressed.
    */
   boolean isCompressImage();
   
   /**
    * Set whether to map unicode characters for greek and math symbols to
    * symbol font characters.
    */
   void setMapSymbols(boolean map);
   
   /**
    * Check if symbol mapping is enabled.
    */
   boolean isMapSymbols();
   
   /**
    * Get the current page size.
    */
   Size getPageSize();
   
   /**
    * Set the page size in inches. Common paper sizes are defined
    * as constants in StyleConstants.
    * @param size Size object in inches. 
    */
   void setPageSize(Size size);
   
   /**
    * Set page orientation.
    * @param orient orientation, StyleConstants.PORTRAIT or 
    * StyleConstants.LANDSCAPE.
    */
   @Override
   void setOrientation(int orient);
   
   /**
    * Get the pdf font name corresponding to the Java font name.
    * @param font Java font.
    * @return pdf font name.
    */
   String getFontName(Font font);
   
   /**
    * Add the mapping for the pdf font name corresponding to the 
    * Java font name.
    * @param javaName Java font name.
    * @param psFontName mapped font name.
    */
   void putFontName(String javaName, String psFontName);
   
   /**
    * Get the printjob object associated with this object, which contains
    * the page size and resolution information.
    * @return print job object.
    */
   @Override
   PrintJob getPrintJob();
   
   /**
    * Close the pdf output stream. This MUST be called to complete the file.
    */
   void close();

   /**
    * Marks the beginning of a paragraph of text. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @param linkId the identifier of the link annotation associated with the
    *               paragraph.
    *
    * @since 11.4
    */
   void startParagraph(Integer linkId);

   /**
    * Marks the end of a paragraph of text. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @since 11.4
    */
   void endParagraph();

   /**
    * Marks the start of a heading. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @param level the heading level.
    *
    * @since 11.4
    */
   void startHeading(int level);

   /**
    * Marks the end of a heading. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @since 11.4
    */
   void endHeading();

   /**
    * Marks the beginning of a figure. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @param altText the alternate text for the chart graphic.
    *
    * @since 11.4
    */
   void startFigure(String altText);

   /**
    * Marks the end of a figure. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @since 11.4
    */
   void endFigure();

   /**
    * Marks the beginning of a table. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @since 11.4
    */
   void startTable();

   /**
    * Marks the end of a table. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @since 11.4
    */
   void endTable();

   /**
    * Marks the beginning of a table row. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @since 11.4
    */
   void startTableRow();

   /**
    * Marks the beginning of a table header cell. This method has no effect if
    * an accessible PDF is not being generated. The default implementation of this method
    * calls {@link #startTableHeader(Integer, int, int)} with {@code -1} passed
    * for the <i>row</i> and <i>col</i> parameters.
    *
    * @param linkId the identifier of the link annotation associated with the
    *               paragraph.
    *
    * @since 11.4
    */
   default void startTableHeader(Integer linkId) {
      startTableHeader(linkId, -1, -1);
   }

   /**
    * Marks the beginning of a table header cell. This method has no effect if an
    * accessible PDF is not being generated.
    *
    * @param linkId   the identifier of the link annotation associated with the paragraph.
    * @param row the index of the row if a column header or {@code -1} if not.
    * @param col the index of the column if a row header or {@code -1} if not.
    *
    * @since 2019
    */
   void startTableHeader(Integer linkId, int row, int col);

   /**
    * Marks the end of a table header cell. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @since 11.4
    */
   void endTableHeader();

   /**
    * Marks the beginning of a table data cell. This method has no effect if
    * an accessible PDF is not being generated. The default implementation of this method
    * calls {@link #startTableCell(Integer, int, int)}, passing {@code -1} for the
    * <i>row</i> and <id>col</id> parameters. This cell will not be associated with any
    * row or column headers in this case.
    *
    * @param linkId the identifier of the link annotation associated with the
    *               paragraph.
    *
    * @since 11.4
    */
   default void startTableCell(Integer linkId) {
      startTableCell(linkId, -1, -1);
   }

   /**
    * Marks the beginning of a table data cell. This method has no effect if an accessible
    * PDF is not being generated.
    *
    * @param linkId the identifier of the link annotation associated with the paragraph.
    * @param row    the index of the table row.
    * @param col    the index of the table cell.
    *
    * @since 2019
    */
   void startTableCell(Integer linkId, int row, int col);

   /**
    * Marks the end of a table data cell. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @since 11.4
    */
   void endTableCell();

   /**
    * Marks the start of a non-content artifact. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @since 11.4
    */
   void startArtifact();

   /**
    * Marks the end of a non-content artifact. This method has no effect if
    * an accessible PDF is not being generated.
    *
    * @since 11.4
    */
   void endArtifact();
}

