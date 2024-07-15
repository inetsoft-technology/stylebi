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
package inetsoft.report.lib.physical;

import inetsoft.report.SaveOptions;
import inetsoft.report.lib.Transaction;
import inetsoft.report.lib.logical.LogicalLibraryEntry;
import inetsoft.report.lib.logical.TableStyleFolderLogicalLibrary;
import inetsoft.util.Tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class TableStyleFolderWriter implements LibraryAssetWriter {

   public TableStyleFolderWriter(PhysicalLibrary library,
                                 TableStyleFolderLogicalLibrary styleFolders)
   {
      this.library = library;
      this.styleFolders = styleFolders;
   }

   @Override
   public void write(SaveOptions options) throws IOException {
      final List<Transaction<LogicalLibraryEntry<String>>> transactions =
         styleFolders.flushTransactions();

      if(options.forceSave() || !transactions.isEmpty()) {
         write(library, options);
      }
   }

   private void write(PhysicalLibrary library, SaveOptions options) throws IOException {
      final PhysicalLibraryEntry libEntry = library.createEntry("style/folders.xml", null);

      try(PrintWriter writer = new PrintWriter(libEntry.getOutputStream())) {
         writer.println("<folders>");

         for(final Map.Entry<String, LogicalLibraryEntry<String>> entry : styleFolders.toEntrySet())
         {
            if(options.filterAudit() && entry.getValue().audit()) {
               continue;
            }

            final String folder = entry.getKey();
            writer.print("<folder name=\"");
            writer.print(Tool.byteEncode(folder));
            writer.println("\"></folder>");
         }

         writer.println("</folders>");
         writer.flush();
      }
   }

   private final PhysicalLibrary library;
   private final TableStyleFolderLogicalLibrary styleFolders;
}
