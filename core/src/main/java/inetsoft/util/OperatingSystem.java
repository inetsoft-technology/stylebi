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

import java.io.File;

/**
 * Contains operating system detection routines.
 */
public class OperatingSystem {
   /**
    * If this application is running under Windows, get the command program.
    * This will be either command.com or cmd.exe.
    */
   public static String getWinCommand() {
      if(!isWindows()) {
         return null;
      }

      if(isWindowsNT()) {
         return "cmd.exe";
      }

      return "command.com";
   }

   /**
    * Returns if we're running Windows 95/98/ME/NT/2000/XP.
    */
   public static final boolean isWindows() {
      return os == WINDOWS_9x || os == WINDOWS_NT;
   }

   /**
    * Returns if we're running Windows 95/98/ME.
    */
   public static final boolean isWindows9x() {
      return os == WINDOWS_9x;
   }

   /**
    * Returns if we're running Windows NT/2000/XP.
    */
   public static final boolean isWindowsNT() {
      return os == WINDOWS_NT;
   }

   /**
    * Returns if we're running Unix (this includes MacOS X).
    */
   public static final boolean isUnix() {
      return os == UNIX || os == MAC_OS_X;
   }

   /**
    * Returns if we're running MacOS X.
    */
   public static final boolean isMacOS() {
      return os == MAC_OS_X;
   }

   // Private members
   private static final int UNIX = 0;
   private static final int WINDOWS_9x = 1;
   private static final int WINDOWS_NT = 2;
   private static final int MAC_OS_X = 3;
   private static final int UNKNOWN = 4;
   private static int os;
   // Class initializer
   static {
      String osName = System.getProperty("os.name");

      if(osName.indexOf("Windows 9") != -1 ||
         osName.indexOf("Windows ME") != -1) {
         os = WINDOWS_9x;
      }
      else if(osName.indexOf("Windows") != -1) {
         os = WINDOWS_NT;
      }
      else if(File.separatorChar == '/' &&
         FileSystemService.getInstance().getFile("/dev").isDirectory())
      {
         if(osName.indexOf("Mac OS X") != -1) {
            os = MAC_OS_X;
         }
         else {
            os = UNIX;
         }
      }
      else {
         os = UNKNOWN;
      }
   }
}

