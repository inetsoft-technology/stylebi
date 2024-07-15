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
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.XPortalHelper;

import java.io.IOException;

/**
 * The interface is for both excel and powerpoint.
 *
 * @version 8.5, 7/19/2006
 * @author InetSoft Technology Corp
 */
public interface VSExporter {
   /**
    * Max export chart size.
    */
   double EXPORT_SIZE = 1000;

   /**
    * Gets the file format type constant.
    *
    * @return the type.
    *
    * @see inetsoft.uql.viewsheet.FileFormatInfo
    */
   default int getFileFormatType() {
      return -1;
   }

   /**
    * Export one single viewsheet to in-mem document (workbook or show).
    */
   void export(ViewsheetSandbox box, String sheetName,
               XPortalHelper helper)
	 throws Exception;

   /**
    * Export one single viewsheet to in-mem document (workbook or show).
    * To fix bug1205749332140, this is poi bug for ppt2007
    */
   void export(ViewsheetSandbox box, String sheetName, int index,
               XPortalHelper helper)
	 throws Exception;

   /**
    * Write the in-mem document (workbook or show) to OutputStream.
    */
   void write() throws IOException;

   /**
    * Check if matches layout.
    * @return <tt>true</tt> if matches layout, <tt>false</tt> otherwise.
    */
   boolean isMatchLayout();

   /**
    * Set whether matches layout.
    * @param match <tt>true</tt> if matches layout, <tt>false</tt> otherwise.
    */
   void setMatchLayout(boolean match);

   /**
    * Check whether to expand selection list/tree.
    */
   boolean isExpandSelections();

   /**
    * Check whether to only export data components.
    */
   boolean isOnlyDataComponents();

   /**
    * Set whether to only export data components.
    */
   void setOnlyDataComponents(boolean onlyDataComponents);

   /**
    * Set whether to expand selection list/tree. It's on used if matchLayout is false.
    */
   void setExpandSelections(boolean expandSelections);

   /**
    * Check if should log execution.
    * @return <tt>true</tt> if should log, <tt>false</tt> otherwise.
    */
   boolean isLogExecution();

   /**
    * Set whether should log execution.
    * @param log <tt>true</tt> if should log, <tt>false</tt> otherwise.
    */
   void setLogExecution(boolean log);
   
   /**
    * Check if should log export.
    * @return <tt>true</tt> if should log, <tt>false</tt> otherwise.
    */
   boolean isLogExport();

   /**
    * Set whether should log export.
    * @param match <tt>true</tt> if should log, <tt>false</tt> otherwise.
    */
   void setLogExport(boolean log);

   /**
    * Get the corresponding asset entry of the viewsheet.
    * @return asset entry.
    */
   AssetEntry getAssetEntry();

   /**
    * Set the corresponding asset entry of the viewsheet.
    * @param entry the specified asset entry.
    */
   void setAssetEntry(AssetEntry entry);

   /**
    * Get the viewsheet for exporting.
    */
   Viewsheet getViewsheet();

   /**
    * Set the ViewsheetSandbox that the exporter will use.
    * @param exportBox the sandbox the exporter will use.
    */
   void setSandbox(ViewsheetSandbox exportBox);
}
