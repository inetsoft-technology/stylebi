/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.service;

import inetsoft.util.Catalog;
import inetsoft.util.MessageException;

import java.util.*;

/**
 * Thrown when a data source or a data source folder could not be renamed or moved because a
 * write failed partway through. The rename stops at the first failure. Nothing is lost: an
 * object whose write failed is still at its old path. The data sources that were moved before
 * the failure are listed, so that the dependencies of exactly those can be renamed.
 */
public class DataSourceRenameException extends MessageException {
   /**
    * Creates a new instance of DataSourceRenameException.
    *
    * @param oldPath    the old path of the data source or folder that was renamed.
    * @param newPath    the new path of the data source or folder.
    * @param failedPath the path of the object whose write failed.
    * @param moved      the data sources that were moved before the failure, old path to new path.
    * @param cause      the failure.
    */
   public DataSourceRenameException(String oldPath, String newPath, String failedPath,
                                    Map<String, String> moved, Exception cause)
   {
      super(oldPath, cause);
      this.oldPath = oldPath;
      this.newPath = newPath;
      this.failedPath = failedPath;

      if(moved != null) {
         this.moved.putAll(moved);
      }
   }

   /**
    * Gets the data sources that were moved before the failure.
    *
    * @return the old paths mapped to the new paths, in the order they were moved.
    */
   public Map<String, String> getMovedDataSources() {
      return Collections.unmodifiableMap(moved);
   }

   /**
    * Checks if a data source was moved before the failure.
    *
    * @param oldPath the old path of the data source.
    *
    * @return {@code true} if it is at its new path.
    */
   public boolean isMoved(String oldPath) {
      return moved.containsKey(oldPath);
   }

   /**
    * Adds the data sources that an enclosing rename moved before the one that failed, and makes
    * the enclosing rename the one reported.
    *
    * @param oldPath the old path of the enclosing data source folder.
    * @param newPath the new path of the enclosing data source folder.
    * @param moved   the data sources the enclosing rename moved, old path to new path.
    */
   public void addMovedDataSources(String oldPath, String newPath, Map<String, String> moved) {
      Map<String, String> all = new LinkedHashMap<>(moved);
      all.putAll(this.moved);
      this.moved.clear();
      this.moved.putAll(all);
      this.oldPath = oldPath;
      this.newPath = newPath;
   }

   public String getOldPath() {
      return oldPath;
   }

   public String getNewPath() {
      return newPath;
   }

   public String getFailedPath() {
      return failedPath;
   }

   @Override
   public String getMessage() {
      String list = moved.isEmpty() ? "-" : String.join(", ", moved.keySet());
      return Catalog.getCatalog().getString(
         "common.datasource.renameFailed", oldPath, newPath, failedPath, list);
   }

   private String oldPath;
   private String newPath;
   private final String failedPath;
   private final Map<String, String> moved = new LinkedHashMap<>();
}
