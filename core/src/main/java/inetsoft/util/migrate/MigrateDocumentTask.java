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

import inetsoft.report.Hyperlink;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.xmla.Domain;
import inetsoft.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.rmi.RemoteException;

public abstract class MigrateDocumentTask implements MigrateTask {
   public MigrateDocumentTask(AssetEntry entry, AbstractIdentity oOrg, AbstractIdentity nOrg) {
      this(entry, oOrg, nOrg, null);
   }

   public MigrateDocumentTask(AssetEntry entry, AbstractIdentity oOrg, AbstractIdentity nOrg, Document document) {
      super();

      this.entry = entry;
      this.oldOrganization = oOrg;
      this.newOrganization = nOrg;
      this.document = document;
   }

   public MigrateDocumentTask(AssetEntry entry, String oname, String nname) {
      super();

      this.entry = entry;
      this.oname = oname;
      this.nname = nname;
   }

   public MigrateDocumentTask(AssetEntry entry, String oname, String nname, Organization currOrg) {
      super();

      this.entry = entry;
      this.oname = oname;
      this.nname = nname;

      this.oldOrganization = currOrg;
      this.newOrganization = currOrg;
   }

   @Override
   public void process() {
      try {
         AssetEntry entry = getEntry();
         String oldKey = entry.toIdentifier();

         if(getOldOrganization() instanceof Organization &&
            getNewOrganization() instanceof Organization)
         {
            Document document = getDocument(((Organization)getOldOrganization()).getId(), oldKey);

            if(Tool.equals(((Organization) oldOrganization).getId(),
                           ((Organization) newOrganization).getId()) && document != null)
            {
               getIndexStorage().remove(oldKey);
            }

            processAssemblies(document.getDocumentElement());
            AssetEntry newEntry = entry.cloneAssetEntry((Organization) getNewOrganization());
            String newKey = newEntry.toIdentifier(true);

            if(this.document == null && document.getChildNodes().getLength() == 2 &&
               document.getFirstChild().getNodeValue().indexOf(oldKey) > 0)
            {
               document.removeChild(document.getFirstChild());
            }

            setDocument(((Organization) getNewOrganization()).getId(), newKey, document);
         }
      }
      catch(Exception e) {
         LOG.error("failed to migrate entry:{}", entry.toIdentifier(), e);
      }
   }

   private void updateScheduleServerTask(String task, String orgId) {
      ScheduleManager scheduleManager = ScheduleManager.getScheduleManager();

      if(scheduleManager == null) {
         return;
      }

      ScheduleTask scheduleTask = scheduleManager.getScheduleTask(task, orgId);

      if(scheduleTask == null) {
         return;
      }

      try {
         ScheduleClient.getScheduleClient().taskAdded(scheduleTask);
      }
      catch(RemoteException e) {
         LOG.error("Failed to update scheduler with extension task: " +
                      scheduleTask.getTaskId(), e);
      }
   }

   protected Document getDocument(String orgId, String key) {
      return document != null ? document : getIndexStorage().getDocument(key, orgId);
   }

   protected void setDocument(String orgId, String key, Document document) {
      if(this.document != null) {
         return;
      }

      getIndexStorage().putDocument(key, document, getAssetClassName(entry), orgId);
   }

   @Override
   public void updateNameProcess() {
      AssetEntry entry = getEntry();
      String key = entry.toIdentifier();
      Document document = getIndexStorage().getDocument(key, null);

      if(document == null) {
         return;
      }

      processAssemblies(document.getDocumentElement());
      AssetEntry newEntry = entry.cloneAssetEntry(entry.getOrgID(), nname);

      if(Tool.equals(oname, newEntry.getCreatedUsername())) {
         newEntry.setCreatedUsername(nname);
      }

      if(Tool.equals(oname, newEntry.getModifiedUsername())) {
         newEntry.setModifiedUsername(nname);
      }

      String newKey = newEntry.toIdentifier(true);

      if(entry.isScheduleTask()) {
         String newTaskName =
            MigrateUtil.getNewUserTaskName(entry.getName(), getOldName(), getNewName());

         if(Tool.equals(entry.getName(), newTaskName)) {
            newKey = key;
         }
      }

      getIndexStorage().putDocument(newKey, document, getAssetClassName(entry));

      if(!Tool.equals(key, newKey)) {
         getIndexStorage().remove(key);
      }

      if(entry.isScheduleTask()) {
         String newTaskName =
            MigrateUtil.getNewUserTaskName(entry.getName(), getOldName(), getNewName());
         updateScheduleServerTask(newTaskName, entry.getOrgID());
      }

      //any private assets user created must be updated, and all dependent assets as well
      DependencyHandler.getInstance().renameDependencies(entry, newEntry);
   }

   abstract void processAssemblies(Element elem);

   public IndexedStorage getIndexStorage() {
      return IndexedStorage.getIndexedStorage();
   }

   public NodeList getChildNodes(Element elem, String path) {
      return DependencyTool.getChildNodes(xpath, elem, path);
   }

   protected void replaceElementCDATANode(Node elem, String value) {
      DependencyTransformer.replaceElementCDATANode(elem, value);
   }

   public AssetEntry getEntry() {
      return entry;
   }

   public void setEntry(AssetEntry entry) {
      this.entry = entry;
   }

   public AbstractIdentity getOldOrganization() {
      return oldOrganization;
   }

   public void setOldOrganization(Organization oldOrganization) {
      this.oldOrganization = oldOrganization;
   }

   public AbstractIdentity getNewOrganization() {
      return newOrganization;
   }

   public void setNewOrganization(Organization newOrganization) {
      this.newOrganization = newOrganization;
   }

   public String getNewName() {
      return nname;
   }

   public String getOldName() {
      return oname;
   }

   protected void updateDrillPaths(Element root) {
      NodeList list = getChildNodes(root, "//XDrillInfo/drillPath");

      for(int i = 0; i < list.getLength(); i++) {
         updateDrillPath((Element) list.item(i));
      }
   }

   protected void updateDrillPath(Element drillPath) {
      String linkType = Tool.getAttribute(drillPath, "linkType");
      String link = Tool.getAttribute(drillPath, "link");

      if((Hyperlink.VIEWSHEET_LINK + "").equals(linkType) &&
         getNewOrganization() instanceof Organization)
      {
         link = AssetEntry.createAssetEntry(link).cloneAssetEntry((Organization) getNewOrganization())
            .toIdentifier(true);
         drillPath.setAttribute("link", link);
      }

      NodeList assets = getChildNodes(drillPath, "./subquery/worksheetEntry/assetEntry");

      for(int j = 0; j < assets.getLength(); j++) {
         Element entry = (Element) assets.item(j);

         if(entry == null) {
            continue;
         }

         try {
            updateAssetEntry(entry);
         }
         catch(Exception ignore) {
         }
      }
   }

   protected void updateMVDef(Element element) {
      Element name = Tool.getChildNodeByTagName(element, "name");

      if(name != null) {
         String mvName = Tool.getValue(name);

         if(mvName != null && mvName.contains(getOldOrganization().getOrganizationID())) {
            mvName = mvName.replace(getOldOrganization().getOrganizationID(), getNewOrganization().getOrganizationID());
            replaceElementCDATANode(name, mvName);
         }

      }

      Element vsId = Tool.getChildNodeByTagName(element, "vsId");

      if(vsId != null) {
         String vsId0 = Tool.getValue(vsId);

         if(vsId0 != null && vsId0.contains(getOldOrganization().getOrganizationID())) {
            vsId0 = vsId0.replace(getOldOrganization().getOrganizationID(), getNewOrganization().getOrganizationID());
            replaceElementCDATANode(vsId, vsId0);
         }
      }


      Element wsId = Tool.getChildNodeByTagName(element, "wsId");

      if(wsId != null) {
         String wsId0 = Tool.getValue(wsId);

         if(wsId0 != null && wsId0.contains(getOldOrganization().getOrganizationID())) {
            wsId0 = wsId0.replace(getOldOrganization().getOrganizationID(), getNewOrganization().getOrganizationID());
            replaceElementCDATANode(wsId, wsId0);
         }
      }

      Element user = Tool.getChildNodeByTagName(element, "user");
      Element organization = Tool.getChildNodeByTagName(user, "organization");

      if(organization != null) {
         String orgId = Tool.getValue(organization);
         if(orgId != null && orgId.contains(getOldOrganization().getOrganizationID())) {
            orgId = orgId.replace(getOldOrganization().getOrganizationID(), getNewOrganization().getOrganizationID());
            replaceElementCDATANode(organization, orgId);
         }
      }

      updateMVMetaData(element);
   }

   protected void updateMVMetaData(Element element) {
      Element elem = Tool.getChildNodeByTagName(element, "MVMetaData");
      Element wsId = Tool.getChildNodeByTagName(elem, "wsId");

      if(wsId != null) {
         String wsId0 = Tool.getValue(wsId);

         if(wsId0 != null && wsId0.contains(getOldOrganization().getOrganizationID())) {
            wsId0 = wsId0.replace(getOldOrganization().getOrganizationID(), getNewOrganization().getOrganizationID());
            replaceElementCDATANode(wsId, wsId0);
         }
      }


      NodeList sheetIdsList = Tool.getChildNodesByTagName(elem, "sheetIds");
      Element elem0;

      for(int i = 0; i < sheetIdsList.getLength(); i++) {
         elem0 = (Element) sheetIdsList.item(i);
         Element sheetId = Tool.getChildNodeByTagName(elem0, "sheetId");

         if(sheetId != null) {
            String sheetId0 = Tool.getValue(sheetId);

            if(sheetId0 != null && sheetId0.contains(getOldOrganization().getOrganizationID())) {
               sheetId0 = sheetId0.replace(getOldOrganization().getOrganizationID(), getNewOrganization().getOrganizationID());
               replaceElementCDATANode(sheetId, sheetId0);
            }
         }
      }
   }

   protected void updateAssetEntry(Element entry) {
      if(entry == null) {
         return;
      }

      String entryType = Tool.getAttribute(entry, "type");
      boolean forVS = Tool.equals(AssetEntry.Type.VIEWSHEET.id() + "", entryType);
      Element user = Tool.getChildNodeByTagName(entry, "user");

      if(user != null) {
         String key = Tool.getValue(user);

         if(key != null) {
            key = updateIdentity(key);
            replaceElementCDATANode(user, key);
         }

      }

      Element organizationID = Tool.getChildNodeByTagName(entry, "organizationID");

      if(organizationID != null && getNewOrganization() instanceof Organization) {
         String orgID = Tool.getValue(organizationID);

         if(orgID != null) {
            replaceElementCDATANode(organizationID, ((Organization)getNewOrganization()).getId());
         }
      }

      if(!forVS) {
         return;
      }

      Element favoritesUsersNode = Tool.getChildNodeByTagName(entry, "favoritesUser");

      if(favoritesUsersNode != null) {
         String favoritesUsers = Tool.getValue(favoritesUsersNode);

         if(favoritesUsers != null) {
            String[] users = favoritesUsers.split("^_^");

            for(int i = 0; i < users.length; i++) {
               users[i] = updateIdentity(users[i]);
            }

            favoritesUsers = String.join("^_^", users);
            replaceElementCDATANode(favoritesUsersNode, favoritesUsers);
         }
      }

      String type = entry.getAttribute("type");

      if(String.valueOf(AssetEntry.Type.VIEWSHEET_BOOKMARK.id()).equals(type)) {
         Element path = Tool.getChildNodeByTagName(entry, "path");

         if(path != null) {
            String pathStr = Tool.getValue(path);
            pathStr = updateBookmarkPath(pathStr, getNewOrganization());
            replaceElementCDATANode(path, pathStr);
         }
      }

      NodeList propertyList = getChildNodes(entry, "./properties/property");

      for(int i = 0; i < propertyList.getLength(); i++) {
         Element property = (Element) propertyList.item(i);
         Element key = Tool.getChildNodeByTagName(property, "key");

         if(Tool.equals("__bookmark_id__", Tool.getValue(key))) {
            Element value = Tool.getChildNodeByTagName(property, "value");
            String valueTxt = Tool.getValue(value);

            if(valueTxt != null) {
               valueTxt = updateBookmarkPath(valueTxt, getNewOrganization());
               replaceElementCDATANode(value, valueTxt);
            }
         }
      }
   }

   protected String updateIdentity(String identityKey) {
      IdentityID identityID = IdentityID.getIdentityIDFromKey(identityKey);

      if(getNewOrganization() != null) {
         identityID.setOrgID(((Organization)getNewOrganization()).getId());
      }

      if(Tool.equals(identityID.getName(), oname)) {
         identityID.setName(nname);
      }

      return identityID.convertToKey();
   }

   private static String updateBookmarkPath(String path, AbstractIdentity org) {
      if(StringUtils.isEmpty(path) && !(org instanceof Organization)) {
         return path;
      }

      if(path.indexOf("__NULL__") != -1) {
         path = path.replace("__NULL__", "^^NULL^^");
      }

      String[] arr = path.split("__");

      if(arr.length > 2) {
         String userKey = arr[2];
         IdentityID id = IdentityID.getIdentityIDFromKey(userKey);

         if(id != null && org != null) {
            id.setOrgID(org.getOrganizationID());
            arr[2] = id.convertToKey();
         }
      }

      if(arr.length > 4 && org != null) {
         arr[arr.length - 1] = ((Organization) org).getId();
      }

      path = String.join("__", arr);

      if(path.indexOf("^^NULL^^") != -1) {
         path = path.replace("^^NULL^^", "__NULL__");
      }

      return path;
   }

   /**
    * @return asset sheet class name.
    */
   private String getAssetClassName(AssetEntry asset) {
      if(asset.isWorksheet()) {
         return Worksheet.class.getName();
      }

      if(asset.isViewsheet()) {
         return Viewsheet.class.getName();
      }

      if(asset.isLogicModel()) {
         return XLogicalModel.class.getName();
      }

      if(asset.getType() == AssetEntry.Type.VIEWSHEET_BOOKMARK) {
         return VSBookmark.class.getName();
      }

      if(asset.isScheduleTask()) {
         return ScheduleTask.class.getName();
      }

      if(asset.isDomain()) {
         return Domain.class.getName();
      }

      return null;
   }

   private AssetEntry entry;
   private AbstractIdentity oldOrganization;
   private AbstractIdentity newOrganization;
   private String oname;
   private String nname;
   private Document document;
   protected static final XPath xpath = XPathFactory.newInstance().newXPath();
   private static final Logger LOG = LoggerFactory.getLogger(MigrateDocumentTask.class);
}
