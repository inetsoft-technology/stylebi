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
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import inetsoft.util.xml.XPathLikeProcessor;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.*;

public class MigrateViewsheetTask extends MigrateDocumentTask {
   public MigrateViewsheetTask(AssetEntry entry, AbstractIdentity oOrg, AbstractIdentity nOrg) {
      super(entry, oOrg, nOrg);
   }

   public MigrateViewsheetTask(AssetEntry entry, String oname, String nname) {
      super(entry, oname, nname);
   }

   public MigrateViewsheetTask(AssetEntry entry, AbstractIdentity oOrg, AbstractIdentity nOrg,
                               Document document)
   {
      super(entry, oOrg, nOrg, document);
   }

   @Override
   protected void processAssemblies(Element root) {
      if(this.getEntry().isViewsheet()) {
         String ouser = root.getAttribute("modifiedBy");

         if(Tool.equals(ouser, getOldName())) {
            root.setAttribute("modifiedBy", getNewName());
         }

         ouser = root.getAttribute("createdBy");

         if(Tool.equals(ouser, getOldName())) {
            root.setAttribute("createdBy", getNewName());
         }
      }

      NodeList list = getChildNodes(root, "./assemblies/oneAssembly/assembly");

      for(int i = 0; i < list.getLength(); i++) {
         processAssembly((Element) list.item(i));
      }

      NodeList childNodes = getChildNodes(root, "//worksheetEntry/assetEntry");

      if(childNodes.getLength() > 0) {
         for(int i = 0; i < childNodes.getLength(); i++) {
            updateAssetEntry((Element) childNodes.item(i));
         }
      }

      list = getChildNodes(root, "//dependencies/assetEntry");

      for(int i = 0; i < list.getLength(); i++) {
         Element entry = (Element) list.item(i);
         updateAssetEntry(entry);
      }

      if(this.getEntry().getType() == AssetEntry.Type.VIEWSHEET_BOOKMARK) {
         String oldUserKey = root.getAttribute("defaultBookmarkUser");

         if(!Tool.isEmptyString(oldUserKey)) {
            IdentityID oldUID = IdentityID.getIdentityIDFromKey(oldUserKey);

            if(Tool.equals(oldUID.name, getOldName())) {
               IdentityID newUID = new IdentityID(getNewName(), oldUID.orgID);
               root.setAttribute("defaultBookmarkUser", newUID.convertToKey());
            }
         }
      }
   }

   private void processAssembly(Element assembly) {
      String clazz = assembly.getAttribute("class");

      if(Viewsheet.class.getName().equals(clazz)) {
         updateViewsheet(assembly);
      }
      else if(TableVSAssembly.class.getName().equals(clazz) ||
            CrosstabVSAssembly.class.getName().equals(clazz) ||
            CalcTableVSAssembly.class.getName().equals(clazz) ||
            EmbeddedTableVSAssembly.class.getName().equals(clazz))
      {
         NodeList list = getChildNodes(assembly, "./assemblyInfo/hyperlinkAttr/tableHyperlinkAttr");

         if(list.getLength() == 0) {
            return;
         }

         Element hyperlinkAttrNode = (Element) list.item(0);

         if(hyperlinkAttrNode == null) {
            return;
         }

         list = getChildNodes(hyperlinkAttrNode, "./aHyperlink/Hyperlink");

         for(int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            updateHyperlink(elem);
         }
      }
      else if(ChartVSAssembly.class.getName().equals(clazz)) {
         NodeList list = xPathLikeProcessor.getChildNodesByTagNamePath(
            assembly, "assemblyInfo", "VSChartInfo", "**", "dataRef");

         for(int i = 0; i < list.getLength(); i++) {
            updateDataRef((Element) list.item(i));
         }
      }
      else if(TextVSAssembly.class.getName().equals(clazz) ||
         GaugeVSAssembly.class.getName().equals(clazz) ||
         ImageVSAssembly.class.getName().equals(clazz))
      {
         NodeList list = getChildNodes(assembly, "./assemblyInfo/hyperLink/Hyperlink");

         if(list.getLength() != 0) {
            updateHyperlink((Element) list.item(0));
         }

         list = getChildNodes(assembly, "./assemblyInfo/hyperLinkRef/HyperlinkDef");

         for(int i = 0; i < list.getLength(); i++) {
            updateHyperlinkDef((Element) list.item(i));
         }
      }
   }

   private void updateHyperlinkDef(Element element) {
      updateHyperlink(element);
      NodeList list = getChildNodes(element, "./ParameterValue[@name='__bookmarkUser__']");

      if(list.getLength() != 0) {
         Element bkUser = (Element) list.item(0);
         String value = Tool.getValue(bkUser);

         if(value != null) {
            replaceElementCDATANode(bkUser, updateIdentity(value));
         }
      }
   }

   private void updateDataRef(Element dataRef) {
      if(dataRef == null) {
         return;
      }

      String clazz = dataRef.getAttribute("class");

      if(VSChartDimensionRef.class.getName().equals(clazz) ||
         VSChartGeoRef.class.getName().equals(clazz) ||
         VSChartAggregateRef.class.getName().equals(clazz))
      {
         Element elem = Tool.getChildNodeByTagName(dataRef, "Hyperlink");
         updateHyperlink(elem);
      }
   }

   private void updateHyperlink(Element hyperlinkElem) {
      if(hyperlinkElem == null) {
         return;
      }

      String linkType = hyperlinkElem.getAttribute("LinkType");

      if(linkType != null && linkType.equals(Hyperlink.VIEWSHEET_LINK + "")) {
         String link = hyperlinkElem.getAttribute("Link");

         if(link != null && !link.isEmpty() ) {
            AssetEntry entry = AssetEntry.createAssetEntry(link);

            if(getNewOrganization() != null && getNewOrganization() instanceof Organization) {
               link = entry.cloneAssetEntry((Organization) getNewOrganization()).toIdentifier();
            }
            else if(getOldName().equals(entry.getUser().getName())) {
               link = entry.cloneAssetEntry(entry.getOrgID(), getNewName()).toIdentifier();
            }

            hyperlinkElem.setAttribute("Link", link);
         }
      }

      String bookmarkUser = hyperlinkElem.getAttribute("BookmarkUser");

      if(bookmarkUser != null) {
         hyperlinkElem.setAttribute("BookmarkUser", updateIdentity(bookmarkUser));
      }
   }

   private void updateViewsheet(Element assembly) {
      processAssemblies(assembly);
      Element node = Tool.getChildNodeByTagName(assembly, "viewsheetEntry");
      Element vsEntry = node == null ? null : Tool.getChildNodeByTagName(node, "assetEntry");
      updateAssetEntry(vsEntry);
   }

   public static String updateBookmarkPath(String path, Organization org) {
      if(StringUtils.isEmpty(path)) {
         return path;
      }

      if(path.indexOf("__NULL__") != -1) {
         path = path.replace("__NULL__", "^^NULL^^");
      }

      String[] arr = path.split("__");

      if(arr.length > 2) {
         String userKey = arr[2];

         if(!"^^NULL^^".equals(userKey)) {
            IdentityID id = IdentityID.getIdentityIDFromKey(userKey);

            if(id != null) {
               id.setOrgID(org.getId());
               arr[2] = id.convertToKey();
            }
         }
      }

      if(arr.length > 4) {
         arr[arr.length - 1] = org.getId();
      }

      path = String.join("__", arr);

      if(path.indexOf("^^NULL^^") != -1) {
         path = path.replace("^^NULL^^", "__NULL__");
      }

      return path;
   }

   public static String updateBookmarkPath(String path, String name) {
      if(StringUtils.isEmpty(path)) {
         return path;
      }

      if(path.indexOf("__NULL__") != -1) {
         path = path.replace("__NULL__", "^^NULL^^");
      }

      String[] arr = path.split("__");

      if(arr.length > 2) {
         String userKey = arr[2];

         if(!"^^NULL^^".equals(userKey)) {
            IdentityID id = IdentityID.getIdentityIDFromKey(userKey);

            if(id != null) {
               id.setName(name);
               arr[2] = id.convertToKey();
            }
         }
      }

      path = String.join("__", arr);

      if(path.indexOf("^^NULL^^") != -1) {
         path = path.replace("^^NULL^^", "__NULL__");
      }

      return path;
   }

   private final XPathLikeProcessor xPathLikeProcessor = new XPathLikeProcessor();
}
