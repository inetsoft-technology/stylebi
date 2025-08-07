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
package inetsoft.sree.schedule;

import inetsoft.uql.util.Identity;

import java.util.List;


/**
 * Defines a common interface to supply the ScheduleManager
 * with the additional tasks not specified in the regular
 * schedule.xml file.
 *
 * @author  InetSoft Technology
 * @version 7.0
 */
public interface ScheduleExt extends Iterable<ScheduleTask> {
   /**
    * Get all the schedule tasks.
    */
   List<ScheduleTask> getTasks();

   /**
    * Get all the schedule tasks for target org.
    */
   List<ScheduleTask> getTasks(String orgID);

   /**
    * Check if contains a task.
    */
   boolean containsTask(String name, String orgId);

   /**
    * Delete a given task.
    * @return <tt>true</tt> if the extension changed, <tt>false</tt> otherwise.
    */
   boolean deleteTask(String name);

   /**
    * Check if a given task is enabled.
    */
   boolean isEnable(String name, String orgId);

   /**
    * Enable/Disable a given task.
    * @return <tt>true</tt> if the extension changed, <tt>false</tt> otherwise.
    */
   boolean setEnable(String name, String orgId, boolean enable);

   /**
    * Method will be invoked when a replet is removed.
    * @param replet the specified replet.
    * @param owner the specified user.
    * @return <tt>true</tt> if the extension changed, <tt>false</tt> otherwise.
    */
   boolean repletRemoved(String replet, String owner);

   /**
    * Method will be invoked when a replet is renamed.
    * @param oreplet the specified old replet.
    * @param nreplet the specified new replet.
    * @param owner the specified user.
    * @return <tt>true</tt> if the extension changed, <tt>false</tt> otherwise.
    */
   boolean repletRenamed(String oreplet, String nreplet,
                         String owner);

   /**
    * Method will be invoked when a viewsheet is renamed.
    * @param oviewsheet the specified old viewsheet.
    * @param nviewsheet the specified new viewsheet.
    * @param owner the specified user.
    * @return <tt>true</tt> if the extension changed, <tt>false</tt> otherwise.
    */
   boolean viewsheetRenamed(String oviewsheet,
                            String nviewsheet, String owner);

   /**
    * Method will be invoked when an archive is renamed.
    * @param opath the specified old archive path.
    * @param npath the specified new archive path.
    * @param owner the specified user.
    * @return <tt>true</tt> if the extension changed, <tt>false</tt> otherwise.
    */
   boolean archiveRenamed(String opath, String npath,
                          String owner);
   /**
    * Method will be invoked when a folder is renamed.
    * @param opath the specified old folder path.
    * @param npath the specified new folder path.
    * @param owner the specified user.
    * @return <tt>true</tt> if the extension changed, <tt>false</tt> otherwise.
    */
   boolean folderRenamed(String opath, String npath,
                         String owner);
   /**
    * Method will be invoked when a user is removed.
    */
   void identityRemoved(Identity identity);

   /**
    * Method will be invoked when a user is renamed.
    */
   void identityRenamed(String oname, Identity identity);}