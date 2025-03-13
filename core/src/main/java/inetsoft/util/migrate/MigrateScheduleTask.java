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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

public class MigrateScheduleTask extends MigrateDocumentTask {
   public MigrateScheduleTask(AssetEntry entry, AbstractIdentity oOrg, AbstractIdentity nOrg) {
      super(entry, oOrg, nOrg);
   }

   public MigrateScheduleTask(AssetEntry entry, String oname, String nname) {
      super(entry, oname, nname);
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

      String user = task.getAttribute("owner");

      if(user != null) {
         IdentityID identityID = IdentityID.getIdentityIDFromKey(user);

         if(getNewOrganization() == null && getOldOrganization() == null) {
            if(Tool.equals(getOldName(), identityID.getName())) {
               identityID.setName(getNewName());
               task.setAttribute("owner", identityID.convertToKey());
            }
         }
         else {
            if(!Tool.equals(((Organization) getOldOrganization()).getId(), ((Organization) getNewOrganization()).getId())) {
               identityID.setOrgID(((Organization) getNewOrganization()).getId());
               task.setAttribute("owner", identityID.convertToKey());
            }
         }
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

         updateEmailTo(item);
         String type = Tool.getAttribute(item, "type");

         if(type == null) {
            continue;
         }

         if(Tool.equals(type, "Viewsheet")) {
            String viewsheet = Tool.getAttribute(item, "viewsheet");

            if(viewsheet != null && getNewOrganization() != null && getNewOrganization() instanceof Organization) {
               String newIdentifier = MigrateUtil.getNewIdentifier(viewsheet, (Organization) getNewOrganization());
               item.setAttribute("viewsheet", newIdentifier);
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
               else if(Tool.equals(getOldName(), identityID.getName()) &&
                     Tool.equals(getOldOrganization().getOrganizationID(), identityID.getOrgID()))
               {
                  identityID.setOrgID(((Organization)getNewOrganization()).getId());
               }

               bookmark.setAttribute("user", identityID.convertToKey());
            }
         }
         else if("Backup".equals(type)) {
            NodeList childNodes = getChildNodes(item, "//XAsset");

            if(childNodes != null) {
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
                     String userAttribute = assetEle.getAttribute("user");

                     if(!Tool.isEmptyString(userAttribute)) {
                        IdentityID assetUser = IdentityID.getIdentityIDFromKey(assetEle.getAttribute("user"));

                        if(getNewOrganization() == null) {
                           assetUser.setName(getNewName());
                        }
                        else {
                           assetUser.setOrgID(((Organization)getNewOrganization()).getId());
                        }

                        assetEle.setAttribute("user", assetUser.convertToKey());
                     }
                  }
               }
            }
         }
         else if("Batch".equals(type)) {
            String taskName = Tool.getAttribute(item, "taskId");
            String newTaskName;

            if(getOldOrganization() == null || getNewOrganization() == null) {
               newTaskName = MigrateUtil.getNewUserTaskName(Tool.byteDecode(taskName),
                  getOldName(), getNewName());
            }
            else {
               newTaskName = MigrateUtil.getNewOrgTaskName(Tool.byteDecode(taskName),
                  getOldOrganization().getOrganizationID(), getNewOrganization().getOrganizationID());
            }

            item.setAttribute("taskId", newTaskName);
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

   private void updateEmailTo(Element actionNode) {
      if(actionNode == null) {
         return;
      }

      NodeList mailTo = getChildNodes(actionNode, "./MailTo");

      for(int i = 0; mailTo != null && i < mailTo.getLength(); i++) {
         Element mailToItem = (Element) mailTo.item(i);

         if(mailToItem == null) {
            continue;
         }

         String emails = mailToItem.getAttribute("email");
         List<String> emailList = new ArrayList<>();

         for(String email : emails.split("[;,]", 0)) {
            email = StringUtils.normalizeSpace(email);

            if(Tool.matchEmail(email) || !email.endsWith(Identity.USER_SUFFIX)) {
               emailList.add(email);
               continue;
            }

            String userName = email.substring(0, email.lastIndexOf(Identity.USER_SUFFIX));

            if(!Tool.equals(getOldName(), userName)) {
               emailList.add(email);
               continue;
            }

            emailList.add(getNewName() + Identity.USER_SUFFIX);
         }

         mailToItem.setAttribute("email", String.join(",", emailList));
      }
   }
}
