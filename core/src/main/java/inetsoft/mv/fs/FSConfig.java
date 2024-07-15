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
package inetsoft.mv.fs;

/**
 * FSConfig defines file system configuration. An implementation of
 * this interface should build configuration from data space, or somewhere else.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public interface FSConfig {
   /**
    * Check if this File system is desktop. For a desktop file system, file will
    * not be divided into pieces.
    */
   boolean isDesktop();

   /**
    * Get the job period.
    */
   int getJobTimeout();

   /**
    * Get the job check period.
    */
   int getJobCheckPeriod();

   /**
    * Get the expired period for a map task.
    */
   int getExpired();

   /**
    * Get the work directory for the specified data node. The directory will
    * be used to store block files.
    */
   String getWorkDir(String node);
}
