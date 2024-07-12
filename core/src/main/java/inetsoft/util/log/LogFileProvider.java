/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.log;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Interface for classes that query the log files for a specific logging framework.
 */
public interface LogFileProvider {
   /**
    * Gets the pattern used to match log file names in the log file directory.
    *
    * @param baseFileName the base log file name.
    *
    * @return the log file name pattern.
    */
   Pattern getLogFilePattern(String baseFileName);

   /**
    * Gets the comparator used to sort log files.
    *
    * @param baseFileName the base log file name.
    *
    * @return the log file comparator.
    */
   Comparator<Path> getComparator(String baseFileName);

   /**
    * Determines if the specified log file can be rotated.
    *
    * @param baseFileName the base log file name.
    * @param fileName     the log file name to test.
    *
    * @return {@code true} if the file can be rotated; {@code false} otherwise.
    */
   boolean isRotateSupported(String baseFileName, String fileName);

   /**
    * Rotates the log file.
    */
   void rotateLogFile();
}
