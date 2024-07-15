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

import java.util.HashMap;
import java.util.Map;

/**
 * Class that represents the number format sections of a shared format table.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public class ExcelStyleTable {
   /**
    * Creates a new instance of <tt>ExcelStyleTable</tt>.
    */
   public ExcelStyleTable() {
      // default constructor
   }
   
   /**
    * Adds a reference to a shared format.
    * 
    * @param xfId     the cell style ID.
    * @param numFmtId the format ID.
    */
   public final void addFormatReference(int xfId, int numFmtId) {
      xfMap.put(xfId, numFmtId);
   }
   
   /**
    * Adds a shared format.
    * 
    * @param numFmtId   the format ID.
    * @param formatCode the format pattern.
    */
   public final void addFormat(int numFmtId, String formatCode) {
      fmtMap.put(numFmtId, formatCode);
   }
   
   /**
    * Gets the ID of the shared format for the specified cell style.
    * 
    * @param xfId the cell style ID.
    * 
    * @return the format ID.
    */
   public final int getFormatId(int xfId) {
      Integer fmtId = xfMap.get(xfId);
      return fmtId == null ? 0 : fmtId;
   }
   
   /**
    * Gets the pattern of the shared format for the specified cell style.
    * 
    * @param xfId the cell style ID.
    * 
    * @return the format pattern or <tt>null</tt> if none is defined.
    */
   public final String getFormatCode(int xfId) {
      int fmtId = getFormatId(xfId);
      String formatCode = fmtMap.get(fmtId);
      return formatCode == null ?
         ExcelFileSupport.getInstance().getBuiltinFormat(fmtId) : formatCode;
   }
   
   private final Map<Integer, Integer> xfMap = new HashMap<>();
   private final Map<Integer, String> fmtMap = new HashMap<>();
}
