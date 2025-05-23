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

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.*;
import inetsoft.util.MigrateUtil;
import inetsoft.util.Tool;
import inetsoft.util.dep.*;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.*;

import java.net.*;
import java.util.ArrayList;
import java.util.List;

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

      String name = task.getAttribute("name");

      if(getOldOrganization() == null && getNewOrganization() == null) {
         task.setAttribute("name", MigrateUtil.getNewUserTaskName(name, getOldName(), getNewName()));
      }
      else {
         task.setAttribute("name", MigrateUtil.getNewOrgTaskName(name, ((Organization) getOldOrganization()).getId(),
                                                                 ((Organization) getNewOrganization()).getId()));
      }

      syncIdentityAttribute(task, "owner");
      syncIdentityAttribute(task, "user");
      syncIdentityAttribute(task, "idname");
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

         updateEmailAttribute(item, "MailTo");
         updateEmailAttribute(item, "Notify");
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
               return;
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
               return;
            }

            for(int j = 0; j < childNodes.getLength(); j++) {
               element = (Element) childNodes.item(j);
               updateMVDef(element);
            }
         }
      }
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
      String[] names = taskId.split(":");

      if(names.length > 1 && names[0].indexOf(IdentityID.KEY_DELIMITER) > 0) {
         String[] userNames = names[0].split(IdentityID.KEY_DELIMITER);

         if(nOrgID == null) {
            userNames[0] = getNewName();
         }
         else {
            userNames[1] = nOrgID;
         }

         return Tool.buildString(userNames[0], IdentityID.KEY_DELIMITER, userNames[1], ":", names[1]);
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

   private void updateEmailAttribute(Element actionNode, String childNodeName) {
      if(actionNode == null) {
         return;
      }

      NodeList nodes = getChildNodes(actionNode, "./" + childNodeName);

      for(int i = 0; nodes != null && i < nodes.getLength(); i++) {
         Element item = (Element) nodes.item(i);

         if(item == null) {
            continue;
         }

         String emails = item.getAttribute("email");
         List<String> emailList = new ArrayList<>();

         for(String email : emails.split("[;,]", 0)) {
            email = StringUtils.normalizeSpace(email);
            String suffix = email.endsWith(Identity.USER_SUFFIX) ? Identity.USER_SUFFIX : Identity.GROUP_SUFFIX;

            if(Tool.matchEmail(email) || (!email.endsWith(Identity.USER_SUFFIX) &&
               !email.endsWith(Identity.GROUP_SUFFIX))) {
               emailList.add(email);
               continue;
            }

            String userName = email.substring(0, email.lastIndexOf(suffix));

            if(!Tool.equals(getOldName(), userName)) {
               emailList.add(email);
               continue;
            }

            emailList.add(getNewName() + suffix);
         }

         item.setAttribute("email", String.join(",", emailList));
      }
   }
}
