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
import java.util.ArrayList;
import java.util.List;

/**
 * Text file reader for fixed-width text files.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public class FixedFileReader implements TextFileReader {
   /**
    * Creates a new instance of <tt>FixedFileReader</tt>.
    */
   public FixedFileReader() {
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
      if(output.getFileType() != TextFileType.FIXED) {
         throw new IllegalArgumentException("Wrong file type");
      }

      BufferedReader reader = null;

      if(encoding == null) {
         reader = new BufferedReader(new InputStreamReader(input));
      }
      else {
         reader = new BufferedReader(new InputStreamReader(input, encoding));
      }

      return new FixedTableNode(reader, query, columns, rows, firstRowHeader, removeQuote);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XTypeNode importHeader(InputStream input, String encoding,
                                 TextOutput output, int rowLimit, int colLimit) throws Exception
   {
      XTypeNode meta = new XTypeNode("table");
      BufferedReader reader = null;

      if(encoding == null) {
         reader = new BufferedReader(new InputStreamReader(input));
      }
      else {
         reader = new BufferedReader(new InputStreamReader(input, encoding));
      }

      String fline = reader.readLine();

      if(fline != null) {
         String sline = reader.readLine();

         if(output.getHeaderInfo() != null) {
            int length = ((FixedFileInfo) output.getHeaderInfo()).getLength();

            if(length == 0) {
               throw new IllegalArgumentException("Zero-length header");
            }

            List<String> names = new ArrayList<>();

            for(int i = 0; i < fline.length(); i += length) {
               int headerEnd = Math.min(i + length, fline.length());
               names.add(fline.substring(i, headerEnd).trim());
            }

            if(sline != null) {
               for(int i = 0, start = 0, end = 0; i < names.size(); i++) {
                  int len = FixedFileInfo.DEF_LENGTH;
                  end = Math.min(start + len, sline.length());
                  String hdr = sline.substring(start, end).trim();

                  TextUtil.TypeFormat typeFormat = TextUtil.getType(hdr);
                  XTypeNode col =
                     XSchema.createPrimitiveType(typeFormat.getType());
                  col.setName(names.get(i));
                  col.setFormat(typeFormat.getFormat());
                  col.setAttribute("length", length);
                  meta.addChild(col);

                  start = end;

                  if(start >= sline.length()) {
                     break;
                  }
               }
            }
         }
         else {
            int headerLength = FixedFileInfo.DEF_LENGTH;

            for(int i = 0; i < fline.length(); i += headerLength) {
               int headerEnd = Math.min(i + headerLength, fline.length());
               String hdr = fline.substring(i, headerEnd);

               TextUtil.TypeFormat typeFormat = TextUtil.getType(hdr);
               XTypeNode col =
                     XSchema.createPrimitiveType(typeFormat.getType());
               col.setName(TextUtil.createColumnName(i));
               col.setFormat(typeFormat.getFormat());
               col.setAttribute("length", FixedFileInfo.DEF_LENGTH);
               meta.addChild(col);
            }
         }
      }

      return meta;
   }

   private static final class FixedTableNode extends TextTableNode {
      public FixedTableNode(BufferedReader reader, XQuery query, int columnCount,
                            int maxRows, boolean firstRowHeader, boolean removeQuote)
              throws Exception
      {
         super(reader, query, columnCount, maxRows, firstRowHeader, null, removeQuote);
      }

      @Override
      protected String[] parseRow(String line, XQuery query, String delimiter, boolean removeQuote)
              throws Exception
      {
         return TextUtil.readFixedLengthLine(line, new int[0]);
      }
   }
}
