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
package inetsoft.report.lib.logical;

import inetsoft.report.LibManager;
import inetsoft.report.lib.TransactionType;
import inetsoft.report.lib.physical.*;
import inetsoft.sree.security.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.SecurityException;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.stream.Collectors;

public class TableStyleFolderLogicalLibrary extends AbstractLogicalLibrary<String> {
   protected TableStyleFolderLogicalLibrary(LibrarySecurity security) {
      super(security);
   }

   @Override
   protected LibraryAssetReader<String> getReader() {
      return null; // unused
   }

   @Override
   protected LibraryAssetWriter getWriter(PhysicalLibrary library) {
      return new TableStyleFolderWriter(library, this);
   }

   @Override
   protected ResourceType getResourceType() {
      return ResourceType.TABLE_STYLE;
   }

   @Override
   protected ResourceType getResourceLibraryType() {
      return ResourceType.TABLE_STYLE_LIBRARY;
   }

   @Override
   protected String getEntryName() {
      return "table style folder";
   }

   @Override
   protected int getAddedFlag() {
      return -1; // unused
   }

   @Override
   protected int getModifiedFlag() {
      return -1; // unused
   }

   @Override
   protected void logFailedLoad(String name, Exception ex) {
      // unused
   }

   @Override
   public String getAssetPrefix() {
      return LibManager.PREFIX_STYLE;
   }

   public List<String> getTableStyleFolders(String folder, boolean filterAudit) {
      final boolean root = folder == null;
      lock.readLock().lock();

      try {
         return getNameToEntryMap(null).values().stream()
            .filter(e -> !filterAudit || !e.audit())
            .map(LogicalLibraryEntry::asset)
            .filter(name -> {
               final int index = name.lastIndexOf(LibManager.SEPARATOR);

               if(index == -1) {
                  return root;
               }
               else {
                  return Objects.equals(folder, name.substring(0, index));
               }
            })
            .filter(name -> checkPermission(getResourceType(), name, ResourceAction.READ))
            .collect(Collectors.toList());
      }
      finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public void load(PhysicalLibrary library, LoadOptions options) throws IOException {
      final Map<String, LogicalLibraryEntry<String>> loadingMap = new HashMap<>();
      final Map<String, LogicalLibraryEntry<String>> oldEntries =
         options.init() ? Collections.emptyMap() : toMap();

      final String entryPrefix = getAssetPrefix();
      final Iterator<PhysicalLibraryEntry> entries = library.getEntries();

      while(entries.hasNext()) {
         final PhysicalLibraryEntry entry = entries.next();
         final String path = entry.getPath();

         if(path.startsWith(entryPrefix)) {
            final String name = path.substring(entryPrefix.length());

            if(name.length() == 0 || name.endsWith(LibManager.COMMENT_SUFFIX) ||
               isTempFile(name))
            {
               continue;
            }

            if("folders.xml".equals(name)) {
               try(final InputStream input = entry.getInputStream()) {
                  Document doc = CoreTool.parseXML(input);
                  NodeList nlist = doc.getElementsByTagName("folders");

                  if(nlist.getLength() > 0) {
                     Element elem = (Element) nlist.item(0);
                     nlist = CoreTool.getChildNodesByTagName(elem, "folder");

                     for(int i = 0; i < nlist.getLength(); i++) {
                        String folder = Tool.byteDecode(CoreTool.getAttribute(
                           (Element) nlist.item(i), "name"));

                        if(folder != null && !oldEntries.containsKey(folder)) {
                           loadingMap.put(folder, LogicalLibraryEntry.<String>builder()
                                             .asset(folder)
                                             .audit(options.audit())
                                             .build());
                        }
                     }
                  }
               }
               catch(Exception ex) {
                  LOG.error("Failed to read table style folders", ex);
               }

               break;
            }
         }
      }

      if(options.requiresUserDefinedStyleFolder()) {
         loadingMap.computeIfAbsent(
            LibManager.USER_DEFINE, k -> LogicalLibraryEntry.<String>builder()
               .asset(LibManager.USER_DEFINE)
               .audit(false)
               .build());
      }

      lock.writeLock().lock();

      try {
         if(options.init()) {
            clear();
         }

         getNameToEntryMap(null).putAll(loadingMap);
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * "Add" used over "put" because in this library the folder name itself is the asset, so a
    * name-value mapping is not necessary.
    */
   public void add(String folderName) {
      lock.writeLock().lock();

      try {
         final LogicalLibraryEntry<String> oldEntry = getNameToEntryMap(null).get(folderName);

         if(oldEntry != null && !oldEntry.audit()) {
            return;
         }

         Resource parent;
         int index = folderName.lastIndexOf(LibManager.SEPARATOR);

         if(index < 0) {
            parent = new Resource(ResourceType.TABLE_STYLE_LIBRARY, "*");
         }
         else {
            parent = new Resource(ResourceType.TABLE_STYLE, folderName.substring(0, index));
         }

         if(!checkPermission(parent.getType(), parent.getPath(), ResourceAction.WRITE)) {
            throw new SecurityException(Catalog.getCatalog().getString(
               "Permission denied to create folder in selected folder"));
         }

         final LogicalLibraryEntry<String> newEntry = LogicalLibraryEntry.<String>builder()
            .audit(false)
            .asset(folderName)
            .build();

         getNameToEntryMap(null).put(folderName, newEntry);
         recordTransaction(TransactionType.CREATE, folderName, newEntry);
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   protected LogicalLibraryEntry<String> renameEntry(LogicalLibraryEntry<String> oldEntry,
                                                     String oldName,
                                                     String newName)
   {
      return LogicalLibraryEntry.<String>builder()
         .from(oldEntry)
         .asset(newName)
         .build();
   }

   public boolean contains(String name) {
      return Objects.equals(get(name), name);
   }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
