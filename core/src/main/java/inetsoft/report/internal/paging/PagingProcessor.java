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
package inetsoft.report.internal.paging;

import inetsoft.report.ReportSheet;

/**
 * PagingProcessor generates style pages.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface PagingProcessor {
   /**
    * Check if the generated style pages will only be used once, and need not 
    * been swapped.
    */
   public boolean isOneOff();

   /**
    * Check if the generated style pages will only be used once.
    */
   public boolean isInitialOneOff();

   /**
    * Check if the report sheet is created for interactive.
    */
   public boolean isInteractive();

   /**
    * Check if the report must wait for the entire report to be processed.
    */
   public boolean isBatchWaiting();

   /**
    * Get the current available page count.
    */
   public int getPageCount();

   /**
    * Check if the paging process is completed.
    */
   public boolean isCompleted();

   /**
    * Get the processed report sheet.
    */
   public ReportSheet getReport();

   /**
    * Wait for the processor to finish.
    */
   public void join(boolean internal) throws Exception;
}
