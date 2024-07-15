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
package inetsoft.uql.util.filereader;

import inetsoft.uql.XQuery;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.text.TextOutput;

import java.io.*;

/**
 * Text file reader for delimited text files.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public class DelimitedFileReader implements TextFileReader {
   /**
    * Creates a new instance of <tt>DelimitedFileReader</tt>.
    */
   public DelimitedFileReader() {
      // default constructor
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XTableNode read(InputStream input, String encoding, XQuery query,
                          TextOutput output, int rows, int columns, boolean firstRowHeader,
                          String delimiter, boolean removeQuote)
           throws Exception
   {
      if(output.getFileType() != TextFileType.DELIMITED) {
         throw new IllegalArgumentException("Wrong file type");
      }

      BufferedReader reader = null;

      if(encoding == null) {
         reader = new BufferedReader(new InputStreamReader(input));
      }
      else {
         reader = new BufferedReader(new InputStreamReader(input, encoding));
      }

      return new DelimitedTableNode(reader, query, columns, rows,
              firstRowHeader, delimiter, removeQuote);
   }

   /**
    * Imports meta-data for the text file based on the header row(s).
    *
    * @param input    the input stream to read.
    * @param encoding the character encoding of the file.
    * @param output   the query output specification.
    * @param isCSV   the flag to determine whether use TextUtil to read file.
    *
    * @return the result set meta-data.
    *
    * @throws Exception if the file could not be read.
    */
   public XTypeNode importHeader(InputStream input, String encoding,
      TextOutput output, boolean isCSV) throws Exception
   {
      return importHeader(input, encoding, output, isCSV, false);
   }

   /**
    * Imports meta-data for the text file based on the header row(s).
    *
    * @param input    the input stream to read.
    * @param encoding the character encoding of the file.
    * @param output   the query output specification.
    * @param isCSV   the flag to determine whether use TextUtil to read file.
    *
    * @return the result set meta-data.
    *
    * @throws Exception if the file could not be read.
    */
   public XTypeNode importHeader(InputStream input, String encoding,
      TextOutput output, boolean isCSV, boolean isTxt) throws Exception
   {
      XTypeNode meta = new XTypeNode("table");
      BufferedReader reader = null;

      if(encoding == null) {
         reader = new BufferedReader(new InputStreamReader(input));
      }
      else {
         // consume file's bom header which encode is utf8
         if("UTF8".equals(encoding)) {
            input = new PushbackInputStream(input, 3);
            byte[] bom = new byte[3];
            int len = input.read(bom, 0, 3);

            if(!(len == 3 && bom[0] == (byte)0xEF && bom[1] == (byte)0xBB
               && bom[2] == (byte)0xBF))
            {
               ((PushbackInputStream) input).unread(bom);
            }
         }

         reader = new BufferedReader(new InputStreamReader(input, encoding));
      }

      String fline = isCSV ? TextUtil.readCSVLine(reader) : reader.readLine();

      if(fline != null) {
         String sline = isCSV ? TextUtil.readCSVLine(reader) : reader.readLine();
         DelimitedFileInfo info = (DelimitedFileInfo) output.getHeaderInfo();

         if(output.getHeaderInfo() != null) {
            String[] names = TextUtil.split(fline, info.getDelimiter(), true,
                                            info.isRemoveQuotation());

            for(int i = 0; i < names.length; i++) {
               if(names[i] != null) {
                  names[i] = names[i].trim();

                  // txt do not support newline.
                  if(isTxt) {
                     names[i] = names[i].replaceAll("\r\n", " ");
                     names[i] = names[i].replaceAll("\n", " ");
                  }
                  else if(isCSV) {
                     names[i] = CSVLoader.toValidHeader(names[i]);
                  }

                  names[i] = TextUtil.createValidHeaderName(names[i]);
               }
            }

            if(sline != null) {
               DelimitedFileInfo bodyInfo = (DelimitedFileInfo) output.getBodyInfo();
               String[] row = TextUtil.split(sline, bodyInfo.getDelimiter(), true,
                  bodyInfo.isRemoveQuotation());

               for(int i = 0; i < names.length; i++) {
                  TextUtil.TypeFormat typeFormat = i < row.length ?
                        TextUtil.getType(row[i].trim()) :
                        new TextUtil.TypeFormat(XSchema.STRING);
                  XTypeNode col = XSchema.createPrimitiveType(typeFormat.getType());
                  col.setName(names[i]);
                  col.setFormat(typeFormat.getFormat());
                  meta.addChild(col);
               }
            }
            else {
               for(int i = 0; i < names.length; i++) {
                  XTypeNode col = XSchema.createPrimitiveType(XSchema.STRING);
                  col.setName(names[i]);
                  meta.addChild(col);
               }
            }
         }
         else {
            DelimitedFileInfo bodyInfo = (DelimitedFileInfo) output.getBodyInfo();
            String[] cols = TextUtil.split(fline, bodyInfo.getDelimiter(), true,
               bodyInfo.isRemoveQuotation());

            for(int i = 0; i < cols.length; i++) {
               String hdr = cols[i];

               if(hdr != null) {
                  hdr = hdr.trim();

                  if(bodyInfo.isRemoveQuotation()) {
                     hdr = TextUtil.stripOffQuote(hdr);
                  }
               }

               TextUtil.TypeFormat typeFormat = TextUtil.getType(hdr);
               XTypeNode col = XSchema.createPrimitiveType(typeFormat.getType());
               col.setName(TextUtil.createColumnName(i));
               col.setFormat(typeFormat.getFormat());
               meta.addChild(col);
            }
         }
      }

      return meta;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XTypeNode importHeader(InputStream input, String encoding,
                                 TextOutput output, int rowLimit, int colLimit) throws Exception
   {
      return importHeader(input, encoding, output, true);
   }

   private static final class DelimitedTableNode extends TextTableNode {
      public DelimitedTableNode(BufferedReader reader, XQuery query,
                                int columnCount, int maxRows,
                                boolean firstRowHeader, String delimiter,
                                boolean removeQuote)
            throws Exception
      {
         super(reader, query, columnCount, maxRows, firstRowHeader,
               delimiter, removeQuote);
      }

      @Override
      protected String[] parseRow(String line, XQuery query,
				  String delimiter, boolean removeQuote) 
      {
         if(delimiter == null) {
            delimiter = "\n";
         }

         return TextUtil.split(line, delimiter, true, removeQuote);
      }

      @Override
      protected String readLine() throws Exception {
         return isCSV() ? TextUtil.readCSVLine(reader) : reader.readLine();
      }
   }
}
