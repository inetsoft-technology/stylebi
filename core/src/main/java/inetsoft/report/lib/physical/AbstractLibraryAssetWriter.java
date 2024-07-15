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
package inetsoft.report.lib.physical;

import inetsoft.report.SaveOptions;
import inetsoft.report.lib.Transaction;
import inetsoft.report.lib.TransactionSimplifier;
import inetsoft.report.lib.logical.LogicalLibrary;
import inetsoft.report.lib.logical.LogicalLibraryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public abstract class AbstractLibraryAssetWriter<T> implements LibraryAssetWriter {
   protected AbstractLibraryAssetWriter(PhysicalLibrary physicalLibrary,
                                        LogicalLibrary<T> logicalLibrary,
                                        LibraryAssetWriterStrategy<T> writerStrategy)
   {
      this.physicalLibrary = physicalLibrary;
      this.logicalLibrary = logicalLibrary;
      this.writerStrategy = writerStrategy;
      this.transactionSimplifier = new TransactionSimplifier<>();
   }

   protected abstract String getFailedMessage(String name);

   @Override
   public void write(SaveOptions options) throws IOException {
      final List<Transaction<LogicalLibraryEntry<T>>> transactions =
         logicalLibrary.flushTransactions();

      if(options.forceSave()) {
         forceSave(options);
      }
      else {
         processTransactions(transactions);
      }
   }

   protected void forceSave(SaveOptions options) throws IOException {
      for(Map.Entry<String, LogicalLibraryEntry<T>> e : getLogicalEntries()) {
         final String name = e.getKey();
         final LogicalLibraryEntry<T> libEntry = e.getValue();

         if(options.filterAudit() && libEntry.audit()) {
            continue;
         }

         final PhysicalLibraryEntry entry = createEntry(name, libEntry);
         writeEntry(name, libEntry, entry);
      }
   }

   protected Collection<Map.Entry<String, LogicalLibraryEntry<T>>> getLogicalEntries() {
      return logicalLibrary.toEntrySet();
   }

   private void processTransactions(List<Transaction<LogicalLibraryEntry<T>>> transactions) throws IOException {
      final List<Transaction<LogicalLibraryEntry<T>>> simplifiedTransactions =
         transactionSimplifier.simplifyTransactions(transactions);
      Set<String> added = new HashSet<>();

      for(Transaction<LogicalLibraryEntry<T>> transaction : simplifiedTransactions) {
         final String name = transaction.identifier();

         // ignore duplicate names (possible during import). (45351)
         if(added.contains(name)) {
            continue;
         }

         added.add(name);
         final LogicalLibraryEntry<T> libEntry = transaction.payload();
         final PhysicalLibraryEntry entry = createEntry(name, libEntry);

         switch(transaction.type()) {
            case CREATE:
            case MODIFY:
               writeEntry(name, libEntry, entry);
               break;
            case DELETE:
               entry.remove();
               break;
            default:
               throw new IllegalStateException("Unexpected value: " + transaction.type());
         }
      }
   }

   protected PhysicalLibraryEntry createEntry(String name,
                                              LogicalLibraryEntry<T> libEntry) throws IOException
   {
      Properties properties = new Properties();

      if(libEntry != null) {
         if(libEntry.comment() != null) {
            properties.put("comment", libEntry.comment());
         }

         if(libEntry.created() != 0) {
            properties.put("created", libEntry.created() + "");
         }

         if(libEntry.modified() != 0) {
            properties.put("modified", libEntry.modified() + "");
         }

         if(libEntry.createdBy() != null) {
            properties.put("createdBy", libEntry.createdBy());
         }

         if(libEntry.modifiedBy() != null) {
            properties.put("modifiedBy", libEntry.modifiedBy());
         }
      }

      return physicalLibrary.createEntry(logicalLibrary.getAssetPrefix() + name, properties);
   }

   protected void writeEntry(String name,
                             LogicalLibraryEntry<T> libEntry,
                             PhysicalLibraryEntry entry) throws IOException
   {
      try(OutputStream output = entry.getOutputStream()) {
         try {
            writerStrategy.write(output, libEntry.asset());
         }
         catch(Exception ex) {
            LOG.error(getFailedMessage(name), ex);
         }
      }
   }

   private final LibraryAssetWriterStrategy<T> writerStrategy;
   private final TransactionSimplifier<LogicalLibraryEntry<T>> transactionSimplifier;

   protected final PhysicalLibrary physicalLibrary;
   protected final LogicalLibrary<T> logicalLibrary;

   private static final Logger LOG = LoggerFactory.getLogger(AbstractLibraryAssetWriter.class);
}
