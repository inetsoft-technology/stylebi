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
import java.util.Map;

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
 *    <li>{@link #installAssets(Context)}</li>
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
    * Called to import assets into storage.
    *
    * @param context the setup context.
    *
    * @return the context with any modifications made by the extension.
    */
   default Context installAssets(Context context) {
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
    * @param attributes       arbitrary attributes that can be used to share state between
    *                         extensions. Any values that implement {@link AutoCloseable} will be
    *                         closed when the setup is complete.
    */
   record Context(boolean initialized, File configDirectory, File pluginsDirectory,
                  File filesDirectory, File assetsDirectory, File scriptsDirectory,
                  Map<String, Object> attributes)
   {
   }

   /**
    * Enumeration of the different setup phases.
    */
   enum Phase {
      /**
       * Phase at the start of setup before any other actions have been performed.
       */
      START("start", false),
      /**
       * Phase after the initial application properties have been applied.
       */
      AFTER_PROPERTIES_SET("afterPropertiesSet", false),
      /**
       * Phase after the plugins have been installed into storage.
       */
      AFTER_PLUGINS_INSTALLED("afterPluginsInstalled", false),
      /**
       * Phase after the initial security settings have been applied.
       */
      AFTER_SECURITY_CONFIGURED("afterSecurityConfigured", false),
      /**
       * Phase after the files have been imported into the data space.
       */
      AFTER_FILES_IMPORTED("afterFilesImported", false),
      /**
       * Phase to import assets into storage.
       */
      INSTALL_ASSETS("installAssets", true),
      /**
       * Phase after the assets have been imported into storage.
       */
      AFTER_ASSETS_INSTALLED("afterAssetsInstalled", true);

      private final String phase;
      private final boolean clientApiAvailable;

      Phase(String phase, boolean clientApiAvailable) {
         this.phase = phase;
         this.clientApiAvailable = clientApiAvailable;
      }

      /**
       * Gets the name of the phase as used in setup scripts.
       *
       * @return the name of the phase.
       */
      public String getPhase() {
         return phase;
      }

      /**
       * Determines if the client API is available during this phase.
       *
       * @return {@code true} if the client API is available, {@code false} otherwise.
       */
      public boolean isClientApiAvailable() {
         return clientApiAvailable;
      }
   }
}
