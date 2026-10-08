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

package inetsoft.util.migrate;

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.*;
import inetsoft.util.MigrateUtil;
import inetsoft.util.Tool;
import inetsoft.util.dep.*;
import org.w3c.dom.*;

import java.net.*;

public class MigrateScheduleTask extends MigrateDocumentTask {
   public MigrateScheduleTask(AssetEntry entry, AbstractIdentity oOrg, AbstractIdentity nOrg) {
      super(entry, oOrg, nOrg);
   }

   public MigrateScheduleTask(AssetEntry entry, String oname, String nname) {
      super(entry, oname, nname);
   }

   public MigrateScheduleTask(AssetEntry entry, String oname, String nname, Organization currOrg) {
      super(entry, oname, nname, currOrg);
   }

   public MigrateScheduleTask(AssetEntry entry, String oname, String nname, Organization currOrg,
                              int identityType)
   {
      super(entry, oname, nname, currOrg, identityType);
   }

   @Override
   void processAssemblies(Element elem) {
      NodeList list = getChildNodes(elem, "//Task");

      if(list == null || list.getLength() == 0) {
         return;
      }

      Element task = (Element) list.item(0);

      if(task == null) {
         return;
      }

      if(getIdentityType() == Identity.GROUP) {
         processGroupRename(task);
         return;
      }

      String name = task.getAttribute("name");

      if(getOldOrganization() == null && getNewOrganization() == null) {
         // Bug #77883, the name of a task with an owner key is the bare task name, which may
         // contain ':', only a legacy name or a name prefixed by the owner key has an owner
         String owner = task.getAttribute("owner");
         int index = name.indexOf(':');
         boolean bareName = !Tool.isEmptyString(owner) &&
            owner.contains(IdentityID.KEY_DELIMITER) &&
            (index < 0 || !name.substring(0, index).contains(IdentityID.KEY_DELIMITER));

         if(!bareName) {
            task.setAttribute("name", MigrateUtil.getNewUserTaskName(name, getOldName(), getNewName()));
         }
      }
      else {
         task.setAttribute("name", MigrateUtil.getNewOrgTaskName(name, ((Organization) getOldOrganization()).getId(),
                                                                 ((Organization) getNewOrganization()).getId()));
      }

      syncIdentityAttribute(task, "owner");
      syncIdentityAttribute(task, "user");

      if(isRenamedIdentityType(task)) {
         syncIdentityAttribute(task, "idname");
      }

      list = getChildNodes(task, "./Condition");

      if(list != null) {
         for(int i = 0; i < list.getLength(); i++) {
            Element item = (Element) list.item(i);

            if(item == null) {
               continue;
            }

            String type = Tool.getAttribute(item, "type");

            if(type == null) {
               continue;
            }

            if(Tool.equals(type, "Completion")) {
               String completionTask = Tool.getAttribute(item, "task");

               if(getOldOrganization() == null && getNewOrganization() == null) {
                  item.setAttribute("task", MigrateUtil.getNewUserTaskName(completionTask,
                     getOldName(), getNewName()));
               }
               else {
                  item.setAttribute("task", MigrateUtil.getNewOrgTaskName(completionTask,
                                             ((Organization)getOldOrganization()).getId(), ((Organization) getNewOrganization()).getId()));
               }
            }
         }
      }

      list = getChildNodes(task, "./Action");

      if(list == null) {
         return;
      }

      for(int i = 0; i < list.getLength(); i++) {
         Element item = (Element) list.item(i);

         if(item == null) {
            continue;
         }

         updateEmailAttribute(item, "MailTo", "email", "ccAddresses", "bccAddresses");
         updateEmailAttribute(item, "Notify", "email");
         String type = Tool.getAttribute(item, "type");

         if(type == null) {
            continue;
         }

         if(Tool.equals(type, "Viewsheet")) {
            String viewsheet = Tool.getAttribute(item, "viewsheet");

            if(viewsheet != null && getNewOrganization() != null && getNewOrganization() instanceof Organization) {
               String newIdentifier = MigrateUtil.getNewIdentifier(viewsheet, (Organization) getNewOrganization());
               item.setAttribute("viewsheet", newIdentifier);
               processLinkUrl(item, getOldOrganization().getOrganizationID(),
                  getNewOrganization().getOrganizationID());
            }

            NodeList childNodes = getChildNodes(item, "./Bookmark ");

            if(childNodes == null || childNodes.getLength() == 0) {
               continue;
            }

            for(int j = 0; j < childNodes.getLength(); j++) {
               Element bookmark = (Element) childNodes.item(j);

               if(bookmark == null) {
                  continue;
               }

               IdentityID identityID = IdentityID.getIdentityIDFromKey(bookmark.getAttribute("user"));

               if(getNewOrganization() == null) {
                  if(Tool.equals(getOldName(), identityID.getName())) {
                     identityID.setName(getNewName());
                  }
               }
               else if(Tool.equals(getOldOrganization().getOrganizationID(), identityID.getOrgID())) {
                  identityID.setOrgID(((Organization)getNewOrganization()).getId());
               }

               bookmark.setAttribute("user", identityID.convertToKey());
            }
         }
         else if("Backup".equals(type)) {
            processBackupAction(item);
         }
         else if("Batch".equals(type)) {
            processBatchAction(item);
         }
         else if("MV".equals(type)) {
            NodeList childNodes = getChildNodes(item, "./MVDef ");
            Element element;

            if(childNodes == null || childNodes.getLength() == 0) {
               continue;
            }

            for(int j = 0; j < childNodes.getLength(); j++) {
               element = (Element) childNodes.item(j);
               updateMVDef(element);
            }
         }
      }
   }

   /**
    * A group owns no task, so a group rename only changes the "execute as" group and the
    * <tt>(Group)</tt> email recipients. The other references to the old name belong to a
    * same-named user.
    */
   private void processGroupRename(Element task) {
      if(isRenamedIdentityType(task)) {
         syncIdentityAttribute(task, "idname");
      }

      NodeList list = getChildNodes(task, "./Action");

      for(int i = 0; list != null && i < list.getLength(); i++) {
         Element item = (Element) list.item(i);
         updateEmailAttribute(item, "MailTo", "email", "ccAddresses", "bccAddresses");
         updateEmailAttribute(item, "Notify", "email");
      }
   }

   /**
    * Checks if the "execute as" identity of the task has the type of the renamed identity. The
    * type defaults to a user, as in ScheduleTask. An organization migration moves the identity
    * whatever its type.
    */
   private boolean isRenamedIdentityType(Element task) {
      if(getOldName() == null) {
         return true;
      }

      int idtype = Identity.USER;

      try {
         idtype = Integer.parseInt(Tool.getAttribute(task, "idtype"));
      }
      catch(NumberFormatException ignore) {
      }

      return idtype == getIdentityType();
   }

   private void processLinkUrl(Element actionNode, String oldOrg, String newOrg) {
      NodeList linkURI = getChildNodes(actionNode, "LinkURI");

      if(linkURI == null || linkURI.getLength() == 0) {
         return;
      }

      Element linkNode = (Element) linkURI.item(0);
      String value = linkNode.getAttribute("uri");

      if(Tool.isEmptyString(value)) {
         return;
      }

      try {
         URI originalUri = URI.create(value);
         String newHost = originalUri.getHost().replace(oldOrg + ".", newOrg + ".");

         URI newUri = new URI(
            originalUri.getScheme(),
            originalUri.getUserInfo(),
            newHost,
            originalUri.getPort(),
            originalUri.getPath(),
            originalUri.getQuery(),
            originalUri.getFragment()
         );

         linkNode.setAttribute("uri", newUri.toString());
      }
      catch(Exception ignore) {
      }
   }

   private void processBackupAction(Element element) {
      NodeList childNodes = getChildNodes(element, "//XAsset");

      for(int j = 0; j < childNodes.getLength(); j++) {
         Element assetEle = (Element) childNodes.item(j);

         if(assetEle == null) {
            continue;
         }

         String assetType = assetEle.getAttribute("type");

         if(Tool.equals(assetType, ScheduleTaskAsset.SCHEDULETASK) ||
            Tool.equals(assetType, ViewsheetAsset.VIEWSHEET) ||
            Tool.equals(assetType, WorksheetAsset.WORKSHEET) ||
            Tool.equals(assetType, DashboardAsset.DASHBOARD))
         {
            syncIdentityAttribute(assetEle, "user");
         }

         if(Tool.equals(assetType, ScheduleTaskAsset.SCHEDULETASK)) {
            String path = assetEle.getAttribute("path");
            String npath = processTaskId(path);

            if(!Tool.equals(path, npath)) {
               assetEle.setAttribute("path", npath);
            }
         }
      }
   }

   private String processTaskId(String taskId) {
      if(Tool.isEmptyString(taskId)) {
         return taskId;
      }

      String nOrgID = getNewOrganization() == null ? null : getNewOrganization().getOrganizationID();
      // Bug #77883, the owner key ends at the first ':', the task name may contain ':'
      int index = taskId.indexOf(':');
      String owner = index > 0 ? taskId.substring(0, index) : null;

      if(owner != null && owner.indexOf(IdentityID.KEY_DELIMITER) > 0) {
         String[] userNames = owner.split(IdentityID.KEY_DELIMITER);

         if(nOrgID == null) {
            userNames[0] = getNewName();
         }
         else {
            userNames[1] = nOrgID;
         }

         return Tool.buildString(userNames[0], IdentityID.KEY_DELIMITER, userNames[1],
                                 taskId.substring(index));
      }

      return taskId;
   }

   private void processBatchAction(Element action) {
      if(action == null) {
         return;
      }

      String taskName = Tool.getAttribute(action, "taskId");
      String newTaskName;

      if(getOldOrganization() == null || getNewOrganization() == null) {
         newTaskName = MigrateUtil.getNewUserTaskName(Tool.byteDecode(taskName),
                                                      getOldName(), getNewName());
      }
      else {
         newTaskName = MigrateUtil.getNewOrgTaskName(Tool.byteDecode(taskName),
                                                     getOldOrganization().getOrganizationID(), getNewOrganization().getOrganizationID());
      }

      action.setAttribute("taskId", newTaskName);

      if(getOldOrganization() == null || getNewOrganization() == null) {
         return;
      }

      NodeList queryOrg = getChildNodes(action, "./queryEntry/assetEntry/organizationID");

      if(queryOrg != null && queryOrg.getLength() > 0 && queryOrg.item(0) != null) {
         String oldOrg = Tool.getValue(queryOrg.item(0));

         if(Tool.equals(oldOrg, this.getOldOrganization().getOrganizationID())) {
            this.replaceElementCDATANode(queryOrg.item(0), getNewOrganization().getOrganizationID());
         }
      }

      NodeList queryUser = getChildNodes(action, "./queryEntry/assetEntry/user");

      if(queryUser != null && queryUser.getLength() > 0 && queryUser.item(0) != null) {
         IdentityID user = IdentityID.getIdentityIDFromKey(Tool.getValue(queryUser.item(0)));

         if(!Tool.equals(user.orgID, getOldOrganization())) {
            user.orgID = getNewOrganization().getOrganizationID();
            this.replaceElementCDATANode(queryUser.item(0), user.convertToKey());
         }
      }
   }

   private void syncIdentityAttribute(Element task, String attrName) {
      String user = task.getAttribute(attrName);

      if(Tool.isEmptyString(user)) {
         return;
      }

      IdentityID identityID = IdentityID.getIdentityIDFromKey(user);
      String oOrgID = getOldOrganization() == null ? null : getOldOrganization().getOrganizationID();
      String nOrgID = getNewOrganization() == null ? null : getNewOrganization().getOrganizationID();

      if(Tool.equals(oOrgID, nOrgID)) {
         if(Tool.equals(getOldName(), identityID.getName())) {
            identityID.setName(getNewName());
            task.setAttribute(attrName, identityID.convertToKey());
         }
      }
      else {
         identityID.setOrgID(nOrgID);
         task.setAttribute(attrName, identityID.convertToKey());
      }
   }

   /**
    * Renames the recipients that denote the renamed identity in the recipient attributes of
    * the child elements of an action. The attribute is decoded as the action parser does, so a
    * legacy byte-encoded value is matched too. An attribute is only rewritten (as raw text, as
    * the current writer writes it) when one of its recipients is renamed, so the other values
    * keep their bytes.
    */
   private void updateEmailAttribute(Element actionNode, String childNodeName, String... attrs) {
      if(actionNode == null) {
         return;
      }

      NodeList nodes = getChildNodes(actionNode, "./" + childNodeName);

      for(int i = 0; nodes != null && i < nodes.getLength(); i++) {
         Element item = (Element) nodes.item(i);

         if(item == null) {
            continue;
         }

         for(String attr : attrs) {
            if(!item.hasAttribute(attr)) {
               continue;
            }

            String emails = Tool.byteDecode(item.getAttribute(attr));
            String updated = ScheduleManager.updateNotifications(
               emails, getOldName(), getNewName(), getIdentityType());

            if(updated != null) {
               item.setAttribute(attr, updated);
            }
         }
      }
   }
}
