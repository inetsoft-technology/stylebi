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
package inetsoft.util;

import java.util.*;

/**
 * Event that signals that the contents of an indexed storage have been
 * modified.
 *
 * @since 12.1
 */
public class StorageRefreshEvent extends EventObject {
   /**
    * Creates a new instance of <tt>StorageRefreshEvent</tt>.
    *
    * @param source       the source of the event.
    * @param lastModified the new value for the last modified time of the
    *                     indexed storage.
    */
   public StorageRefreshEvent(Object source, long lastModified) {
      this(source, lastModified, Collections.emptyList());
   }

   /**
    * Creates a new instance of <tt>StorageRefreshEvent</tt>.
    *
    * @param source       the source of the event.
    * @param lastModified the new value for the last modified time of the
    *                     indexed storage.
    * @param changes      changes corresponding to the differences between the old and new storage
    *                     state.
    */
   public StorageRefreshEvent(Object source, long lastModified, List<TimestampIndexChange> changes)
   {
      super(source);
      this.lastModified = lastModified;
      this.changes = changes;
   }

   /**
    * Gets the last modified time of the indexed storage.
    *
    * @return the last modified timestamp.
    */
   public long getLastModified() {
      return lastModified;
   }

   public List<TimestampIndexChange> getChanges() {
      return changes;
   }

   private final long lastModified;
   private final List<TimestampIndexChange> changes;
}
