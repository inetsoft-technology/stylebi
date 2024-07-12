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
package inetsoft.sree;

import inetsoft.report.*;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;

import java.awt.*;
import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;

/**
 * This defines the server side report engine interface for processing replets.
 *
 * @author InetSoft Technology Corp.
 * @version 7.0
 */
public interface RepletRepository extends Remote {
   /**
    * HTTP session key for the user principal.
    */
   String PRINCIPAL_COOKIE = "sree.security.principal";

   /**
    * Get the value of a specified property.
    * @param name the name of the property.
    * @return the value of the property, or <code>null</code> if the property
    * is not defined.
    * @throws RemoteException if an error occurs.
    */
   String getProperty(String name) throws RemoteException;

   /**
    * Get the list of all folders.
    * @param principal a Principal object identifying the user that is
    * requesting the folder list.
    * @param actions the permissions the user must have on a folder in order
    * for it to be returned. Should be a combination of '<code>r</code>',
    * '<code>w</code>', and '<code>d</code>', which stand for read, write, and
    * delete respectively. If this parameter is <code>null</code>, all folders
    * that the user has any permission on are returned.
    * @return a list of RepositoryEntry objects that represent the folders
    * that are registered.
    * @throws RemoteException if an error occurs while getting the folder list.
    */
   RepositoryEntry[] getFolders(Principal principal, EnumSet<ResourceAction> actions)
      throws RemoteException;

   /**
    * Change the password of the specified user.
    * @param principal a Principal object representing the user for whom to
    * change the password.
    * @param password the new password for the user.
    * @throws SRSecurityException if the password could not be changed.
    * @throws RemoteException if an error occurs while changing the password.
    */
   void changePassword(Principal principal, String password)
      throws SRSecurityException, RemoteException;

   /**
    * Check if a user has permission for the specified access to a resource.
    *
    * @param principal a Principal object representing the user for whom to check the permission.
    * @param type      the type of resource.
    * @param resource  the name of the resource.
    * @param action    the type of permitted action.
    *
    * @throws RemoteException if an error occurs while checking the permission.
    */
   boolean checkPermission(Principal principal, ResourceType type, String resource,
                           ResourceAction action) throws RemoteException;

   boolean checkPermission(Principal principal, ResourceType type, IdentityID identityID,
                           ResourceAction action) throws RemoteException;

   /**
    * Check if a user could access folders.
    */
   boolean containsFolder(Principal principal, ResourceAction action) throws RemoteException;

   /**
    * Set a user's permission of a specific access to a resource.
    *
    * @param principal  a Principal object identifying the user for whom to set the permission.
    * @param type       the type of resource.
    * @param resource   the name of the resource.
    * @param permission the permissions assigned to the user and resource.
    *
    * @throws SRSecurityException if the permissions could not be set.
    * @throws RemoteException if an error occurs while setting the permission.
    */
   void setPermission(Principal principal, ResourceType type, String resource,
                      Permission permission) throws SRSecurityException, RemoteException;

   /**
    * Get available repository entries.
    * @param folder the specified parent folder.
    * @param user the specified user.
    * @param action the permissions the user must have on an entry in order
    * for it to be returned. Should be a combination of '<code>r</code>',
    * '<code>w</code>', and '<code>d</code>', which stand for read, write, and
    * delete respectively. If this parameter is <code>null</code>, all entries
    * that the user has any permission on are returned.
    * @param selector the specified selector, should be one of the repository entry
    * type predefined in <tt>RepositoryEntry</tt> like <tt>FOLDER</tt>.
    * The selector might be the combination of the types as well.
    */
   RepositoryEntry[] getRepositoryEntries(String folder, Principal user, ResourceAction action,
                                          int selector) throws RemoteException;

   /**
    * Get available repository entries.
    * @param folder the specified parent folder.
    * @param user the specified user.
    * @param actions the permissions the user must have on an entry in order
    * for it to be returned. Should be a combination of '<code>r</code>',
    * '<code>w</code>', and '<code>d</code>', which stand for read, write, and
    * delete respectively. If this parameter is <code>null</code>, all entries
    * that the user has any permission on are returned.
    * @param selector the specified selector, should be one of the repository entry
    * type predefined in <tt>RepositoryEntry</tt> like <tt>FOLDER</tt>.
    * The selector might be the combination of the types as well.
    */
   RepositoryEntry[] getRepositoryEntries(String folder, Principal user,
                                          EnumSet<ResourceAction> actions,
                                          int selector) throws RemoteException;

   /**
    * Check if the tree change, response the refresh option to refresh the tree
    * of portal page.
    */
   boolean isFolderChanged(String folder, Principal user)
      throws RemoteException;

   /**
    * Change folder of a repository entry.
    * @param entry the specified repository entry.
    * @param folder the specified new folder.
    * @param user the specified user.
    */
   void changeFolder(RepositoryEntry entry, String folder,
                     Principal user) throws RemoteException;

   /**
    * Add folder of a repository entry.
    * @param entry the specified repository entry.
    * @param name the name of new folder.
    * @param alias the alias of new folder.
    * @param description the description of new folder.
    * @param user the specified user.
    */
   void addFolder(RepositoryEntry entry, String name, String alias,
                  String description, Principal user)
      throws RemoteException;

   /**
    * Remove a repostory entry.
    * @param entry the specified repository entry.
    * @param user the specified user.
    */
   void removeRepositoryEntry(RepositoryEntry entry, Principal user)
      throws RemoteException;

   /**
    * Rename a repository entry.
    * @param entry the specified entry.
    * @param nname the specified new name.
    * @param user the specified user.
    */
   void renameRepositoryEntry(RepositoryEntry entry, String nname,
                              Principal user) throws RemoteException;

   /**
    * Get a list of schedule tasks available to the specified user.
    * @param principal a Principal object identifying the user making the
    * request.
    * @return a list of task names.
    * @throws IOException if the user does not have permission to schedule tasks
    * or a general I/O error occurs.
    * @throws RemoteException if an error occurs.
    */
   String[] getScheduleTasks(Principal principal) throws IOException;

   /**
    * Get a list of schedule tasks available to the specified user.
    * @param principal a Principal object identifying the user making the
    * request.
    * @return a list of task names.
    * @throws IOException if the user does not have permission to schedule tasks
    * or a general I/O error occurs.
    * @throws RemoteException if an error occurs.
    */
   String[] getScheduleTasks(Principal principal,
                             Collection<ScheduleTask> allTasks)
      throws IOException;

   /**
    * Get the schedule task with the specified name.
    * @param name the name of the schedule task.
    * @return the requested ScheduleTask object.
    * @throws RemoteException if an error occurs.
    */
   ScheduleTask getScheduleTask(String name) throws RemoteException;

   /**
    * Get the schedule task with the specified name.
    * @param name the name of the schedule task.
    * @return the requested ScheduleTask object.
    * @throws RemoteException if an error occurs.
    */
   ScheduleTask getScheduleTask(String name, String orgId) throws RemoteException;

   /**
    * Save a schedule task.
    * @param principal a Principal object identifying the user that is saving
    * the task.
    * @param name the name of the saved task.
    * @param task the ScheduleTask object to save.
    * @throws IOException if the specified user does not have permission to
    * schedule tasks.
    * @throws RemoteException if an unspecified error occurs.
    */
   void setScheduleTask(Principal principal, String name,
                        ScheduleTask task) throws Exception;

   /**
    * Remove a scheduled task.
    * @param principal a Principal object identifying the user that is removing
    * the task.
    * @param name the name of the task to remove.
    * @throws IOException if the specified user does not have permission to
    * schedule tasks.
    * @throws RemoteException if an unspecified error occurs.
    */
   void removeScheduleTask(Principal principal, String name)
      throws Exception;
}
