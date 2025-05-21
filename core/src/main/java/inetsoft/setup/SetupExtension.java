/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.setup;

import java.io.File;

/**
 * Interface for classes that extend the setup process during storage initialization.
 * <p>
 * The methods are called in the following order:
 * <ol>
 *    <li>{@link #start(Context)}</li>
 *    <li>{@link #afterPropertiesSet(Context)}/li>
 *    <li>{@link #afterPluginsInstalled(Context)}</li>
 *    <li>{@link #afterSecurityConfigured(Context)}</li>
 *    <li>{@link #afterFilesImported(Context)}</li>
 *    <li>{@link #afterAssetsInstalled(Context)}</li>
 * </ol>
 */
public interface SetupExtension {
   /**
    * Called at the start of setup before any other actions have been performed.
    *
    * @param context the setup context.
    *
    * @return the context with any modifications made by the extension.
    */
   default Context start(Context context) {
      return context;
   }

   /**
    * Called after the initial application properties have been applied.
    *
    * @param context the setup context.
    *
    * @return the context with any modifications made by the extension.
    */
   default Context afterPropertiesSet(Context context) {
      return context;
   }

   /**
    * Called after the plugins have been installed into storage.
    *
    * @param context the setup context.
    *
    * @return the context with any modifications made by the extension.
    */
   default Context afterPluginsInstalled(Context context) {
      return context;
   }

   /**
    * Called after the initial security settings have been applied.
    *
    * @param context the setup context.
    *
    * @return the context with any modifications made by the extension.
    */
   default Context afterSecurityConfigured(Context context) {
      return context;
   }

   /**
    * Called after the files have been imported into the data space.
    *
    * @param context the setup context.
    *
    * @return the context with any modifications made by the extension.
    */
   default Context afterFilesImported(Context context) {
      return context;
   }

   /**
    * Called after the assets have been imported into storage.
    *
    * @param context the setup context.
    *
    * @return the context with any modifications made by the extension.
    */
   default Context afterAssetsInstalled(Context context) {
      return context;
   }

   /**
    * The context for the setup process.
    *
    * @param initialized      {@code true} if the storage has already been initialized and this is
    *                         an update of an existing storage. {@code false} if this is the initial
    *                         setup of the storage.
    * @param configDirectory  the path to the configuration directory.
    * @param pluginsDirectory the path to the directory containing the plugins to be installed.
    * @param filesDirectory   the path to the directory containing files to be imported into the
    *                         data space.
    * @param assetsDirectory  the path to the directory containing the assets to be imported.
    * @param scriptsDirectory the path to the directory containing the scripts to execute.
    */
   record Context(boolean initialized, File configDirectory, File pluginsDirectory,
                  File filesDirectory, File assetsDirectory, File scriptsDirectory)
   {
   }
}
