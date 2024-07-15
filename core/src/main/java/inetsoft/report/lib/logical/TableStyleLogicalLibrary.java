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
package inetsoft.report.lib.logical;

import inetsoft.report.LibManager;
import inetsoft.report.lib.TransactionType;
import inetsoft.report.lib.physical.*;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.CoreTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.SecurityException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TableStyleLogicalLibrary extends AbstractLogicalLibrary<XTableStyle> {
   public TableStyleLogicalLibrary(LibrarySecurity security) {
      super(security);
   }

   @Override
   protected LibraryAssetReader<XTableStyle> getReader() {
      return new TableStyleReader();
   }

   @Override
   protected LibraryAssetWriter getWriter(PhysicalLibrary library) {
      return new TableStyleWriter(library, this);
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
      return "table style";
   }

   @Override
   protected int getAddedFlag() {
      return LibManager.STYLE_ADDED;
   }

   @Override
   protected int getModifiedFlag() {
      return LibManager.STYLE_MODIFIED;
   }

   @Override
   protected void logFailedLoad(String name, Exception ex) {
      LOG.warn("Can't read a table style: " + name, ex);
   }

   @Override
   public String getAssetPrefix() {
      return LibManager.PREFIX_STYLE;
   }

   @Override
   protected boolean shouldNotLoad(String name) {
      return "folders.xml".equals(name);
   }

   @Override
   protected String processAssetDuringLoad(String name, XTableStyle style) {
      if(style.getID() == null) {
         requiresUserDefinedStyleFolder = true;
         name = getNextStyleID(style.getName());
         style.setID(name);
         style.setName(LibManager.USER_DEFINE + LibManager.SEPARATOR + style.getName());
      }

      return name;
   }

   public List<XTableStyle> getTableStyles(String folder, boolean filterAudit) {
      final boolean root = folder == null;
      lock.readLock().lock();

      try {
         return getNameToEntryMap(null).values().stream()
            .filter(e -> !filterAudit || !e.audit())
            .map(LogicalLibraryEntry::asset)
            .filter(style -> {
               final String name = style.getName();
               final int index = name.lastIndexOf(LibManager.SEPARATOR);

               if(index == -1) {
                  return root;
               }
               else {
                  return Objects.equals(folder, name.substring(0, index));
               }
            })
            .filter(style -> checkStylePermission(style, ResourceAction.READ))
            .collect(Collectors.toList());
      }
      finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public int put(String name, XTableStyle asset) {
      return put(name, asset, true);
   }

   public int put(String name, XTableStyle style, boolean checkParent) {
      final int id;
      final TransactionType transactionType;
      final String styleName = style.getName();
      lock.writeLock().lock();

      try {
         final LogicalLibraryEntry<XTableStyle> oldEntry = getNameToEntryMap(null).get(name);

         if(oldEntry != null) {
            if(!checkStylePermission(oldEntry.asset(), ResourceAction.WRITE)) {
               throw new SecurityException(Catalog.getCatalog().getString(
                  "security.nopermission.overwrite", getEntryName()));
            }

            id = getModifiedFlag();
            transactionType = TransactionType.MODIFY;
         }
         else {
            if(checkParent) {
               final Resource parent;
               int index = styleName.lastIndexOf(LibManager.SEPARATOR);

               if(index < 0) {
                  parent = new Resource(ResourceType.TABLE_STYLE_LIBRARY, "*");
               }
               else {
                  parent = new Resource(ResourceType.TABLE_STYLE, styleName.substring(0, index));
               }

               if(!checkParentPermission(parent.getType(), parent.getPath(), ResourceAction.WRITE))
               {
                  throw new SecurityException(Catalog.getCatalog().getString(
                     "security.nopermission.create", "table style in folder"));
               }
            }

            id = getAddedFlag();
            transactionType = TransactionType.CREATE;
         }

         LogicalLibraryEntry.Builder<XTableStyle> builder = LogicalLibraryEntry.builder();

         if(oldEntry != null) {
            builder = builder.from(oldEntry);
         }
         else {
            builder = builder.audit(false);
         }

         final ImmutableLogicalLibraryEntry<XTableStyle> newEntry = builder
            .asset(style)
            .build();

         getNameToEntryMap(null).put(name, newEntry);
         recordTransaction(transactionType, name, newEntry);

         return id;
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public boolean rename(String oldName, String newName) {
      throw new UnsupportedOperationException();
   }

   public String rename(String oldName, String newName, String oid) {
      lock.writeLock().lock();

      try {
         if(!getNameToEntryMap(null).containsKey(oid)) {
            return null;
         }

         if(!checkPermission(getResourceType(), oid, ResourceAction.DELETE)) {
            throw new SecurityException(Catalog.getCatalog().getString(
               "Permission denied to delete " + getEntryName()));
         }

         if(!checkPermission(getResourceType(), oid, ResourceAction.WRITE)) {
            throw new SecurityException(Catalog.getCatalog().getString(
               "Permission denied to write " + getEntryName()));
         }

         final LogicalLibraryEntry<XTableStyle> entry = getNameToEntryMap(null).remove(oid);
         final XTableStyle style = entry.asset();
         style.setName(newName);
         style.setID(oid);

         getNameToEntryMap(null).put(oid, entry);

         recordTransaction(TransactionType.DELETE, oid);
         recordTransaction(TransactionType.CREATE, oid, entry);
         movePermission(getResourceType(), oldName, getResourceType(), newName);
         return oid;
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   protected LogicalLibraryEntry<XTableStyle> renameEntry(LogicalLibraryEntry<XTableStyle> oldEntry,
                                                          String oldName,
                                                          String newName)
   {
      final XTableStyle style = oldEntry.asset();
      style.setName(newName);
      final String newId = getNextStyleID(CoreTool.replaceAll(newName, "^", LibManager.SEPARATOR));
      style.setID(newId);

      return oldEntry;
   }

   @Override
   protected boolean checkPermission(ResourceType type, String name, ResourceAction action) {
      if(type == ResourceType.TABLE_STYLE) {
         lock.readLock().lock();

         try {
            final LogicalLibraryEntry<XTableStyle> entry = getNameToEntryMap(null).get(name);

            if(entry != null) {
               return checkStylePermission(entry.asset(), action);
            }
            else {
               return true;
            }
         }
         finally {
            lock.readLock().unlock();
         }
      }
      else {
         return super.checkPermission(type, name, action);
      }
   }

   private boolean checkStylePermission(XTableStyle style, ResourceAction action) {
      return super.checkPermission(ResourceType.TABLE_STYLE, style.getName(), action);
   }

   private boolean checkParentPermission(ResourceType type,
                                         String parentName,
                                         ResourceAction action)
   {
      return super.checkPermission(type, parentName, action);
   }

   public String getNextStyleID(String name) {
      if(name == null) {
         return null;
      }

      int idx = name.lastIndexOf(LibManager.SEPARATOR);
      name = idx >= 0 ? name.substring(idx + 1) : name;
      lock.readLock().lock();

      try {
         if(!getNameToEntryMap(null).containsKey(name)) {
            return name;
         }

         for(int i = 1; i < Integer.MAX_VALUE; i++) {
            String name0 = name + "-" + i;

            if(!getNameToEntryMap(null).containsKey(name0)) {
               return name0;
            }
         }
      }
      finally {
         lock.readLock().unlock();
      }

      return null;
   }

   public boolean requiresUserDefinedStyleFolder() {
      return requiresUserDefinedStyleFolder;
   }

   /**
    * Find table style by name instead of id.
    */
   public XTableStyle getByName(String name) {
      return getNameToEntryMap(null).values().stream()
         .filter(entry -> Objects.equals(name, entry.asset().getName()) ||
            // name could be folder~name
            entry.asset().getName().endsWith("~" + name))
         .map(entry -> entry.asset())
         .findFirst().orElse(null);
   }

   private boolean requiresUserDefinedStyleFolder = false;
   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
