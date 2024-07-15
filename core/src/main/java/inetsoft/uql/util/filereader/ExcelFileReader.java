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

import java.io.InputStream;

/**
 * Specialization of <tt>TextFileReader</tt> that adds meta-data retreival
 * methods for Excel files.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public interface ExcelFileReader extends TextFileReader {
   /**
    * Gets the spread sheet names from the specified Excel input stream.
    *
    * @param input the input stream to read.
    *
    * @return the sheet names.
    *
    * @throws Exception if the sheet names could not be read.
    */
   String[] getSheetNames(InputStream input) throws Exception;

   default boolean isXLSX() {
      return false;
   }

   default boolean isExceedLimit() {
      return false;
   }

   default boolean isMixedTypeColumns() {
      return false;
   }

   default boolean isTextExceedLimit() {
      return false;
   }
}
