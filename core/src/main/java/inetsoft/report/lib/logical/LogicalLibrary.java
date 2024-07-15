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

import inetsoft.report.SaveOptions;
import inetsoft.report.lib.Transaction;
import inetsoft.report.lib.physical.PhysicalLibrary;

import java.io.IOException;
import java.util.*;

/**
 * An in-memory report component library for a particular asset type.
 *
 * @param <T> the asset type.
 */
public interface LogicalLibrary<T> {
   /**
    * @return a secure list of the keys of the library.
    */
   List<String> toSecureList();

   /**
    * @return a secure enumeration of the keys of the library.
    */
   Enumeration<String> toSecureEnumeration();

   /**
    * @return a set of key-value pairs representing the library.
    */
   Set<Map.Entry<String, LogicalLibraryEntry<T>>> toEntrySet();

   /**
    * @param name   the name to match.
    * @param secure whether or not to check the asset's read permission.
    *
    * @return a name which case-insensitively matches the name, or null if one is not found or is
    *         not readable.
    */
   String caseInsensitiveFindName(String name, boolean secure);

   /**
    * Loads the physical library into this logical library.
    *
    * @param library the library to load from.
    * @param options load options to use.
    */
   void load(PhysicalLibrary library, LoadOptions options) throws IOException;

   /**
    * Saves the logical library into the physical library.
    *
    * @param library the library to save into.
    * @param options the save options to use.
    */
   void save(PhysicalLibrary library, SaveOptions options) throws IOException;

   /**
    * Saves an identifier and its corresponding asset into this library.
    *
    * @param name  the name of the asset.
    * @param asset the asset itself.
    *
    * @return an int representing the action that was performed by the library.
    */
   int put(String name, T asset);

   /**
    * @param name the name of the asset.
    *
    * @return the asset which maps to this name, or null if security does not have read permission
    *         for this resource or it does not exist.
    */
   T get(String name);

   /**
    * Removes the name and the asset associated with it from this library.
    *
    * @param name the name of the asset.
    */
   void remove(String name);

   /**
    * Replaces the old name of an asset with a new name.
    *
    * @param oldName the old name of the asset.
    * @param newName the new name of the asset.
    *
    * @return true if the asset was successfully renamed, false otherwise.
    */
   boolean rename(String oldName, String newName);

   /**
    * @param name the name of the asset.
    *
    * @return true if the corresponding asset is an audit asset, false otherwise.
    */
   boolean isAudit(String name);

   /**
    * @param name the name of the asset.
    *
    * @return the comment of the corresponding asset, or null if none exists.
    */
   String getComment(String name);

   /**
    * Associates a comment with the corresponding asset.
    *
    * @param name    the name of the asset.
    * @param comment the literal comment.
    */
   void putComment(String name, String comment);

   void putCommentProperties(String name, Properties importProp, boolean isImport);

   /**
    * @return the size of the library.
    */
   int size();

   /**
    * Removes all assets and transaction history.
    */
   void clear();

   /**
    * Removes all assets and transaction history.
    */
   void clear(String orgId);

   /**
    * Returns and clears the library's transaction history.
    *
    * @return the library's transactions.
    */
   List<Transaction<LogicalLibraryEntry<T>>> flushTransactions();

   /**
    * @return true if the library has a transaction history, false otherwise.
    */
   boolean hasTransactions();

   /**
    * @return the String prefix used to serialize and deserialize this library's asset type.
    */
   String getAssetPrefix();
}
