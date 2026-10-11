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
package inetsoft.web.wiz.controller;

import java.util.regex.Pattern;

/**
 * The data source and data source folder name rules the native editors enforce client-side only
 * (Angular {@code FormValidators.DATASOURCE_NAME_REGEXP} and {@code isValidDataSourceFolderName}),
 * applied on the server so a name the native UI would refuse is refused here with the rule named
 * rather than failing deep in the registry as a 500.
 *
 * <p>Both patterns are matched against the whole string. Failures are
 * {@code IllegalArgumentException}s: the wiz advice and the admin data source controller each map
 * that to a 400 carrying the message.</p>
 *
 * <p>Two different rules on purpose, as in the native UI: a data source name may not contain a
 * {@code .} anywhere, a folder name may. Folders additionally refuse the reserved name
 * {@code Cubes}, and {@code .} / {@code ..} as a whole name -- stricter than native, because those
 * are parent-directory references nothing downstream is prepared for.</p>
 */
public final class WizDatasourceNames {
   private WizDatasourceNames() {
   }

   /**
    * Validates a data source (database or tabular) name.
    *
    * @throws IllegalArgumentException naming the forbidden characters.
    */
   public static void requireValidDatasourceName(String name) {
      requireValidDatasourceName("Data source name", name);
   }

   /**
    * Validates a folder name.
    *
    * @throws IllegalArgumentException naming the forbidden characters or the reserved name.
    */
   public static void requireValidFolderName(String name) {
      requireValidFolderName("Folder name", name);
   }

   /**
    * Validates a data source path: every segment but the last is a folder name, the last a data
    * source name. {@code field} prefixes the message so the caller can tell which input is wrong.
    */
   public static void requireValidDatasourcePath(String field, String path) {
      String[] segments = path.split("/", -1);

      for(int i = 0; i < segments.length - 1; i++) {
         requireValidFolderName(field + ": folder name", segments[i]);
      }

      requireValidDatasourceName(field + ": data source name", segments[segments.length - 1]);
   }

   /** Validates a folder path, every segment as a folder name. */
   public static void requireValidFolderPath(String field, String path) {
      for(String segment : path.split("/", -1)) {
         requireValidFolderName(field + ": folder name", segment);
      }
   }

   private static void requireValidDatasourceName(String label, String name) {
      if(name == null || !DATASOURCE_NAME.matcher(name).matches()) {
         throw new IllegalArgumentException(
            label + " \"" + name + "\" is invalid: it must not be empty or contain any of " +
            FORBIDDEN_DATASOURCE_CHARS);
      }
   }

   private static void requireValidFolderName(String label, String name) {
      if(name == null || name.isEmpty()) {
         throw new IllegalArgumentException(label + " \"" + name + "\" is invalid: it is empty");
      }

      if(!FOLDER_NAME.matcher(name).matches()) {
         throw new IllegalArgumentException(
            label + " \"" + name + "\" is invalid: it must not contain any of " +
            FORBIDDEN_FOLDER_CHARS);
      }

      if(RESERVED_FOLDER.equals(name)) {
         throw new IllegalArgumentException(
            label + " \"" + name + "\" is invalid: \"" + RESERVED_FOLDER + "\" is a reserved name");
      }

      if(".".equals(name) || "..".equals(name)) {
         throw new IllegalArgumentException(
            label + " \"" + name + "\" is invalid: it must not be \".\" or \"..\"");
      }
   }

   private static final String FORBIDDEN_DATASOURCE_CHARS = "\\ / ? \" * : < > | . ^";
   private static final String FORBIDDEN_FOLDER_CHARS = "\\ / ? \" * : < > | ^";
   private static final String RESERVED_FOLDER = "Cubes";
   private static final Pattern DATASOURCE_NAME =
      Pattern.compile("[^\\\\/?\"*:<>|.^][^\\\\/?\"*:<>|.^]*");
   private static final Pattern FOLDER_NAME = Pattern.compile("[^\\\\/?\"*:<>|^]*");
}
