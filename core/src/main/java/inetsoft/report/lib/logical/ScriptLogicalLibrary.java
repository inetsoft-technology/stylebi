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
import inetsoft.report.lib.ScriptEntry;
import inetsoft.report.lib.physical.*;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;

public class ScriptLogicalLibrary extends AbstractLogicalLibrary<ScriptEntry> {
   protected ScriptLogicalLibrary(LibrarySecurity security) {
      super(security);
   }

   @Override
   public Enumeration<String> toSecureEnumeration() {
      lock.readLock().lock();

      try {
         final List<String> names = new ArrayList<>(getNameToEntryMap(null).keySet());
         Collections.sort(names);
         return new FilteredEnumeration(
            value -> checkPermission(getResourceType(), value, ResourceAction.READ),
            names);
      }
      finally {
         lock.readLock().unlock();
      }
   }

   @Override
   protected LogicalLibraryEntry<ScriptEntry> renameEntry(LogicalLibraryEntry<ScriptEntry> oldEntry,
                                                          String oldName,
                                                          String newName)
   {
      final ScriptEntry oldScript = oldEntry.asset();
      final String newSignature = Optional.ofNullable(oldScript.signature())
         .map(sig -> sig.replaceFirst(oldName, newName))
         .orElse(null);

      final ScriptEntry newScript = ScriptEntry.builder()
         .from(oldScript)
         .signature(newSignature)
         .build();

      return LogicalLibraryEntry.<ScriptEntry>builder()
         .from(oldEntry)
         .asset(newScript)
         .build();
   }

   @Override
   protected LibraryAssetReader<ScriptEntry> getReader() {
      return new ScriptReader();
   }

   @Override
   protected LibraryAssetWriter getWriter(PhysicalLibrary library) {
      return new ScriptWriter(library, this);
   }

   @Override
   protected ResourceType getResourceType() {
      return ResourceType.SCRIPT;
   }

   @Override
   protected ResourceType getResourceLibraryType() {
      return ResourceType.SCRIPT_LIBRARY;
   }

   @Override
   protected String getEntryName() {
      return "script";
   }

   @Override
   protected int getAddedFlag() {
      return LibManager.SCRIPT_ADDED;
   }

   @Override
   protected int getModifiedFlag() {
      return LibManager.SCRIPT_MODIFIED;
   }

   @Override
   protected void logFailedLoad(String name, Exception ex) {
      LOG.warn("Can't read a script: " + name, ex);
   }

   @Override
   public String getAssetPrefix() {
      return LibManager.PREFIX_SCRIPT;
   }

   public long getLastModifiedTime(String name) {
      LogicalLibraryEntry logicalLibraryEntry = getLogicalLibraryEntry(name);

      return logicalLibraryEntry == null ? 0 : logicalLibraryEntry.modified();
   }

   public String getFunction(String name) {
      return Optional.ofNullable(get(name))
         .map(ScriptEntry::function)
         .orElse(null);
   }

   public String getSignature(String name) {
      final int parenIdx = name.indexOf('(');

      if(parenIdx == -1) {
         return null;
      }

      final String nameWithoutParens = name.substring(0, parenIdx);
      return Optional.ofNullable(get(nameWithoutParens))
         .map(ScriptEntry::signature)
         .orElse(null);
   }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
