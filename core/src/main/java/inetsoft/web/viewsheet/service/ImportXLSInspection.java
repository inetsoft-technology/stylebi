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
package inetsoft.web.viewsheet.service;

import java.util.List;

/**
 * What an offline-edited Excel export looks like, read without touching the runtime viewsheet.
 * Produced by {@link ImportXLSService#inspect} so that a caller can refuse an import that would
 * damage the form tables, before anything is mutated. Holds no POI types: the importer that fills
 * it in lives in the xml-formats module.
 *
 * @param readable       the file opened as a workbook of the type it was uploaded as
 * @param hasCurrentView it has the (localized) "Current View" sheet the import reads
 * @param hasBackup      it has the hidden "Current View_Backup" sheet that holds the row states
 * @param tables         one entry per table assembly of the viewsheet, embedded viewsheets
 *                       included, in assembly order
 */
public record ImportXLSInspection(boolean readable, boolean hasCurrentView, boolean hasBackup,
                                  List<TableCheck> tables)
{
   /**
    * @param name        the table's absolute name
    * @param visible     the table is visible; an invisible table is never imported
    * @param formTable   the runtime has a Form table lens for it
    * @param inFile      the file carries named ranges for it, by the importer's own matching
    * @param dataRows    data rows of the table in the file
    * @param lensRows    data rows the live table holds now
    * @param namedRows   how many of the file's data rows have a row name in their first cell
    * @param unbackedRows how many of those have no row in the backup sheet. The importer reads such
    *                    a row as ADDED, so applying it would write the existing row a second time
    * @param fileColumns width of the table's header row in the file, or -1 when the file has no
    *                    header range to measure
    * @param lensColumns the live table's column count
    */
   public record TableCheck(String name, boolean visible, boolean formTable, boolean inFile,
                            int dataRows, int lensRows, int namedRows, int unbackedRows,
                            int fileColumns, int lensColumns)
   {
      public boolean columnCountDiffers() {
         return fileColumns >= 0 && fileColumns != lensColumns;
      }
   }
}
