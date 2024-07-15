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
package inetsoft.util.log;

/**
 * Interface for classes that handle log framework-specific initialization.
 */
public interface LogInitializer {
   /**
    * Initializes the logging framework for use during startup before the configuration is
    * available.
    */
   void initializeForStartup();

   /**
    * Initializes the logging framework.
    *
    * @param logFile the base name for the log file.
    * @param logFileDiscriminator the discriminator for the log file name. If {@code null}, the
    *                             local IP address will be used.
    * @param console {@code true} to log to standard error; {@code} false otherwise.
    * @param maxFileSize the maximum size of the log files, in KB.
    * @param maxFileCount the maximum number of log files.
    * @param  performance {@code true} to create a performance log file; {@code} false otherwise.
    */
   void initialize(String logFile, String logFileDiscriminator, boolean console, long maxFileSize,
                   int maxFileCount, boolean performance);

   /**
    * Resets the logging framework to an uninitialized state.
    */
   void reset();
}
