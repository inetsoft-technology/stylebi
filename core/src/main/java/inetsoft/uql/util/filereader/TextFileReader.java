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
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.text.TextOutput;

import java.io.InputStream;

/**
 * Interface for classes that read data from text source files.
 *
 * @author InetSoft Technology
 * @since  11.1
 */
public interface TextFileReader {
   /**
    * Imports meta-data for the text file based on the header row(s).
    *
    * @param input    the input stream to read.
    * @param encoding the character encoding of the file.
    * @param output   the query output specification.
    * @param rowLimit the number of rows to check if > 0.
    *
    * @return the result set meta-data.
    *
    * @throws Exception if the file could not be read.
    */
   XTypeNode importHeader(InputStream input, String encoding, TextOutput output,
                          int rowLimit, int colLimit)
      throws Exception;

   /**
    * Imports meta-data for the text file based on the header row(s).
    *
    * @param input    the input stream to read.
    * @param encoding the character encoding of the file.
    * @param output   the query output specification.
    * @param rowLimit the number of rows to check if > 0.
    * @param colLimit the number of columns limit.
    * @param parseInfo the info about date parse.
    *
    * @return the result set meta-data.
    *
    * @throws Exception if the file could not be read.
    */
   default XTypeNode importHeader(InputStream input, String encoding, TextOutput output,
                          int rowLimit, int colLimit, DateParseInfo parseInfo)
      throws Exception
   {
      return importHeader(input, encoding, output, rowLimit, colLimit);
   }

   /**
    * Reads data from a text source file.
    *
    * @param input    the input stream from which to read the file.
    * @param encoding the character encoding of the file.
    * @param query    the query to execute.
    * @param output   the query output specification.
    * @param rows     the number of rows to return.
    * @param columns  the number of columns to return.
    *
    * @return the parsed data.
    *
    * @throws Exception if the data could not be read.
    */
   XTableNode read(InputStream input, String encoding, XQuery query,
                   TextOutput output, int rows, int columns, boolean firstRowHeader,
                   String delimiter, boolean removeQuote)
           throws Exception;

   default XTableNode read(InputStream input, String encoding, XQuery query,
                           TextOutput output, int rows, int columns, boolean firstRowHeader,
                           String delimiter, boolean removeQuote, DateParseInfo parseInfo)
      throws Exception
   {
      return read(
         input, encoding, query, output, rows, columns, firstRowHeader, delimiter, removeQuote);
   }
}
