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
import inetsoft.report.SaveOptions;
import inetsoft.report.lib.Transaction;
import inetsoft.report.lib.TransactionType;
import inetsoft.report.lib.physical.*;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Default LogicalLibrary implementation.
 *
 * @param <T> the asset type.
 */
public abstract class AbstractLogicalLibrary<T> implements LogicalLibrary<T> {
   protected abstract LibraryAssetReader<T> getReader();
   protected abstract LibraryAssetWriter getWriter(PhysicalLibrary library);
   protected abstract ResourceType getResourceType();
   protected abstract ResourceType getResourceLibraryType();
   protected abstract String getEntryName();
   protected abstract int getAddedFlag();
   protected abstract int getModifiedFlag();
   protected abstract void logFailedLoad(String name, Exception ex);

   protected AbstractLogicalLibrary(LibrarySecurity security) {
      this.security = security;
   }

   @Override
   public List<String> toSecureList() {
      lock.readLock().lock();

      try {
         return getNameToEntryMap(null).keySet().stream()
            .filter(name -> checkPermission(getResourceType(), name, ResourceAction.READ))
            .collect(Collectors.toList());
      }
      finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public Enumeration<String> toSecureEnumeration() {
      lock.readLock().lock();

      try {
         return new FilteredEnumeration(
            name -> checkPermission(getResourceType(), name, ResourceAction.READ),
            getNameToEntryMap(null).keySet());
      }
      finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public Set<Map.Entry<String, LogicalLibraryEntry<T>>> toEntrySet() {
      return toMap().entrySet();
   }

   protected Map<String, LogicalLibraryEntry<T>> toMap() {
      lock.readLock().lock();

      try {
         return new HashMap<>(getNameToEntryMap(null));
      }
      finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public String caseInsensitiveFindName(String name, boolean secure) {
      final HashSet<String> temp;
      lock.readLock().lock();

      try {
         temp = new HashSet<>(getNameToEntryMap(null).keySet());
      }
      finally {
         lock.readLock().unlock();
      }

      return caseInsensitiveFindName(temp, name, secure);
   }

   protected String caseInsensitiveFindName(Set<String> names, String name, boolean secure) {
      return names.stream()
         .filter(Objects::nonNull)
         .filter(key -> key.equalsIgnoreCase(name))
         .filter(key -> !secure || checkPermission(getResourceType(), key, ResourceAction.READ))
         .findAny()
         .orElse(null);
   }

   @Override
   public void load(PhysicalLibrary library, LoadOptions options) throws IOException {
      final Map<String, LogicalLibraryEntry<T>> loadingMap = new HashMap<>();
      final Map<String, LogicalLibraryEntry<T>> oldEntries = options.init() ?
         Collections.emptyMap() : toMap();

      final LibraryAssetReader<T> reader = getReader();
      final String entryPrefix = getAssetPrefix();
      final Iterator<PhysicalLibraryEntry> entries = library.getEntries();

      while(entries.hasNext()) {
         final PhysicalLibraryEntry entry = entries.next();
         final String path = entry.getPath();

         if(path.startsWith(entryPrefix)) {
            String name = path.substring(entryPrefix.length());

            if(name.length() == 0 || name.endsWith(LibManager.COMMENT_SUFFIX) || isTempFile(name) ||
               shouldNotLoad(name))
            {
               continue;
            }

            try {
               final String comment = entry.getComment();
               Properties properties = entry.getCommentProperties();
               final T asset = reader.read(name, entry);

               if(asset == null) {
                  LOG.warn("Failed to load {}: {}", getEntryName(), name);
               }
               else {
                  name = processAssetDuringLoad(name, asset);
                  final boolean exists = oldEntries.containsKey(name);

                  if(exists && !options.overwrite()) {
                     continue;
                  }

                  // @by henryh, avoid to copy components into library
                  // with different case name. It is not allowed from GUI.
                  if((exists || caseInsensitiveFindName(oldEntries.keySet(), name, false) == null) &&
                     properties != null && properties.size() > 0)
                  {
                     loadingMap.put(name, LogicalLibraryEntry.<T>builder()
                        .asset(asset)
                        .audit(options.audit())
                        .comment(comment)
                        .created(properties.get("created") == null ? 0 :
                           Long.parseLong((String) properties.get("created")))
                        .modified(properties.get("modified") == null ? 0 :
                           Long.parseLong((String) properties.get("modified")))
                        .createdBy((String) properties.get("createdBy"))
                        .modifiedBy((String) properties.get("modifiedBy"))
                        .build());
                  }
                  else if(exists || caseInsensitiveFindName(oldEntries.keySet(), name, false) == null) {
                     loadingMap.put(name, LogicalLibraryEntry.<T>builder()
                        .asset(asset)
                        .audit(options.audit())
                        .comment(comment)
                        .build());
                  }
               }
            }
            catch(Exception ex) {
               logFailedLoad(name, ex);
            }
         }
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

   protected boolean shouldNotLoad(String name) {
      return false;
   }

   protected String processAssetDuringLoad(String name, T asset) {
      return name;
   }

   protected boolean isTempFile(String name) {
      String regex = "^(.+)([.]tmp\\d+)$";
      return name.matches(regex);
   }

   @Override
   public void save(PhysicalLibrary library, SaveOptions options) throws IOException {
      getWriter(library).write(options);
   }

   @Override
   public int put(String name, T asset) {
      final int id;
      final TransactionType transactionType;
      lock.writeLock().lock();

      try {
         final LogicalLibraryEntry<T> oldEntry = getNameToEntryMap(null).get(name);

         if(oldEntry != null) {
            if(!checkPermission(getResourceType(), name, ResourceAction.WRITE)) {
               throw new SecurityException(Catalog.getCatalog().getString(
                  "security.nopermission.overwrite", getEntryName()));
            }

            id = getModifiedFlag();
            transactionType = TransactionType.MODIFY;
         }
         else {
            if(!checkPermission(getResourceLibraryType(), "*", ResourceAction.WRITE)) {
               throw new SecurityException(Catalog.getCatalog().getString(
                  "security.nopermission.create", getEntryName()));
            }

            id = getAddedFlag();
            transactionType = TransactionType.CREATE;
         }

         LogicalLibraryEntry.Builder<T> builder = LogicalLibraryEntry.builder();

         if(oldEntry != null) {
            builder = builder.from(oldEntry);
         }
         else {
            builder = builder.audit(false);
         }

         LogicalLibraryEntry<T> newEntry = null;

         if(oldEntry != null) {
            newEntry = builder
               .asset(asset)
               .comment(oldEntry.comment())
               .modified(System.currentTimeMillis())
               .build();
         }
         else {
            newEntry = builder
               .asset(asset)
               .created(System.currentTimeMillis())
               .modified(System.currentTimeMillis())
               .build();
         }

         getNameToEntryMap(null).put(name, newEntry);
         recordTransaction(transactionType, name, newEntry);

         return id;
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   public LogicalLibraryEntry getLogicalLibraryEntry(String name) {
      return getLogicalLibraryEntry(name, null);
   }

   public LogicalLibraryEntry getLogicalLibraryEntry(String name, String orgID) {
      lock.readLock().lock();

      try {
         LogicalLibraryEntry entry = null;

         if(checkPermission(getResourceType(), name, ResourceAction.READ)) {
            entry = Optional.ofNullable(getNameToEntryMap(orgID).get(name))
               .orElse(null);
         }

         return entry;
      }
      finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public T get(String name) {
      LogicalLibraryEntry<T> logicalLibraryEntry = getLogicalLibraryEntry(name);

      if(logicalLibraryEntry == null) {
         return null;
      }

      return logicalLibraryEntry.asset();
   }

   public T get(String name, String orgID) {
      LogicalLibraryEntry<T> logicalLibraryEntry = getLogicalLibraryEntry(name, orgID);

      if(logicalLibraryEntry == null) {
         return null;
      }

      return logicalLibraryEntry.asset();
   }

   @Override
   public void remove(String name) {
      if(!checkPermission(getResourceType(), name, ResourceAction.DELETE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "Permission denied to delete " + getEntryName()));
      }

      lock.writeLock().lock();

      try {
         getNameToEntryMap(null).remove(name);
         recordTransaction(TransactionType.DELETE, name);
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public boolean isAudit(String name) {
      lock.readLock().lock();

      try {
         return Optional.ofNullable(getNameToEntryMap(null).get(name))
            .map(LogicalLibraryEntry::audit)
            .orElse(false);
      }
      finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public boolean rename(String oldName, String newName) {
      lock.writeLock().lock();

      try {
         if(!getNameToEntryMap(null).containsKey(oldName)) {
            return false;
         }

         if(!checkPermission(getResourceType(), oldName, ResourceAction.DELETE)) {
            throw new SecurityException(Catalog.getCatalog().getString(
               "Permission denied to delete " + getEntryName()));
         }

         if(!checkPermission(getResourceType(), oldName, ResourceAction.WRITE)) {
            throw new SecurityException(Catalog.getCatalog().getString(
               "Permission denied to write " + getEntryName()));
         }

         final LogicalLibraryEntry<T> oldEntry = getNameToEntryMap(null).remove(oldName);
         final LogicalLibraryEntry<T> newEntry = renameEntry(oldEntry, oldName, newName);

         getNameToEntryMap(null).put(newName, newEntry);

         recordTransaction(TransactionType.DELETE, oldName);
         recordTransaction(TransactionType.CREATE, newName, oldEntry);
         movePermission(getResourceType(), oldName, getResourceType(), newName);
         return true;
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   protected LogicalLibraryEntry<T> renameEntry(LogicalLibraryEntry<T> oldEntry, String oldName,
                                                String newName)
   {
      return oldEntry;
   }

   @Override
   public String getComment(String name) {
      lock.readLock().lock();

      try {
         return Optional.ofNullable(getNameToEntryMap(null).get(name))
            .map(LogicalLibraryEntry::comment)
            .orElse(null);
      }
      finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public void putComment(String name, String comment) {
      Properties properties = new Properties();

      if(comment != null) {
         properties.put("comment", comment);
      }

      putCommentProperties(name, properties, false);
   }

   @Override
   public void putCommentProperties(String name, Properties importProp, boolean isImport) {
      if(!checkPermission(getResourceType(), name, ResourceAction.WRITE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "Permission denied to modify " + getEntryName()));
      }

      lock.writeLock().lock();

      try {
         LogicalLibraryEntry<T> newEntry = null;

         if(isImport && importProp != null && importProp.size() > 0) {
            newEntry = getNameToEntryMap(null).computeIfPresent(
               name, (k, entry) -> LogicalLibraryEntry.<T>builder()
                  .from(entry)
                  .comment((String) importProp.get("comment"))
                  .created(importProp.get("created") == null ? 0 :
                     Long.parseLong((String) importProp.get("created")))
                  .modified(importProp.get("modified") == null ? 0 :
                     Long.parseLong((String) importProp.get("modified")))
                  .createdBy((String) importProp.get("createdBy"))
                  .modifiedBy((String) importProp.get("modifiedBy"))
                  .build());
         }
         else {
            newEntry = getNameToEntryMap(null).computeIfPresent(
               name, (k, entry) -> LogicalLibraryEntry.<T>builder()
                  .from(entry)
                  .modified(System.currentTimeMillis())
                  .comment(importProp == null ? null : (String) importProp.get("comment"))
                  .build());
         }

         if(newEntry != null) {
            recordTransaction(TransactionType.MODIFY, name, newEntry);
         }
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public int size() {
      return getNameToEntryMap(null).size();
   }

   @Override
   public void clear() {
      lock.writeLock().lock();

      try {
         getNameToEntryMap(null).clear();
         transactions.clear();
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public void clear(String orgId) {
      lock.writeLock().lock();

      try {
         getNameToEntryMap(orgId).clear();
         transactions.clear();
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public List<Transaction<LogicalLibraryEntry<T>>> flushTransactions() {
      lock.readLock().lock();

      try {
         final List<Transaction<LogicalLibraryEntry<T>>> transactionRecord =
            new ArrayList<>(transactions);

         transactions.clear();

         return transactionRecord;
      }
      finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public boolean hasTransactions() {
      return !transactions.isEmpty();
   }

   protected void recordTransaction(TransactionType type, String name) {
      recordTransaction(type, name, null);
   }

   /**
    * To be called from within a write lock.
    *
    * @param type         the transaction type.
    * @param name         the entry name.
    * @param libraryEntry the library entry.
    */
   protected void recordTransaction(TransactionType type, String name,
                                    LogicalLibraryEntry<T> libraryEntry)
   {
      transactions.add(Transaction.<LogicalLibraryEntry<T>>builder()
                          .type(type)
                          .identifier(name)
                          .payload(libraryEntry)
                          .build());
   }

   protected boolean checkPermission(ResourceType type, String resource, ResourceAction action) {
      return security.checkPermission(type, resource, action);
   }

   protected void movePermission(ResourceType fromType, String fromResource, ResourceType toType,
                                 String toResource)
   {
      security.movePermission(fromType, fromResource, toType, toResource);
   }

   protected Map<String, LogicalLibraryEntry<T>> getNameToEntryMap(String orgID) {
      if(orgID == null) {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }

      if(nameToEntryMaps.containsKey(orgID)) {
         return nameToEntryMaps.get(orgID);
      }
      else {
         Map<String, LogicalLibraryEntry<T>> nameToEntry = new HashMap<>();
         nameToEntryMaps.put(orgID, nameToEntry);
         return nameToEntry;
      }
   }

   private final LibrarySecurity security;

   protected final ReadWriteLock lock = new ReentrantReadWriteLock();
   private final Map<String, Map<String, LogicalLibraryEntry<T>>> nameToEntryMaps = new HashMap<>();
   protected final Deque<Transaction<LogicalLibraryEntry<T>>> transactions = new ArrayDeque<>();

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
