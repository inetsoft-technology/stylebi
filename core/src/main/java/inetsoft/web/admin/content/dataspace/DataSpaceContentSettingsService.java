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
package inetsoft.web.admin.content.dataspace;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.io.Builder;
import inetsoft.report.io.ExportType;
import inetsoft.sree.internal.HTMLUtil;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.dataspace.model.DataSpaceTreeModel;
import inetsoft.web.admin.content.dataspace.model.DataSpaceTreeNodeModel;
import inetsoft.web.viewsheet.AuditObjectName;
import inetsoft.web.viewsheet.Audited;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URLEncoder;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;

@Service
public class DataSpaceContentSettingsService {
   public DataSpaceTreeModel getTree(String parentPath) throws Exception {
      DataSpace space = DataSpace.getDataSpace();
      String[] childEntries = space.list(parentPath);
      childEntries = sortEntry(parentPath, childEntries, space);
      List<DataSpaceTreeNodeModel> nodes = new ArrayList<>();

      for(String name : childEntries) {
         String nonOrgName = getDisplayName(name);
         String path = getPath(parentPath, name);

         DataSpaceTreeNodeModel node = DataSpaceTreeNodeModel.builder()
            .label(nonOrgName)
            .path(path)
            .folder(space.isDirectory(path))
            .build();
         nodes.add(node);
      }

      // if root
      if(parentPath == null) {
         DataSpaceTreeNodeModel rootNode = DataSpaceTreeNodeModel.builder()
            .label(Catalog.getCatalog().getString("Data Space"))
            .path("/")
            .folder(true)
            .children(nodes)
            .build();
         nodes = new ArrayList<>();
         nodes.add(rootNode);
      }

      return DataSpaceTreeModel.builder()
         .nodes(nodes)
         .build();
   }


   public DataSpaceTreeModel getTree(String parentPath, List<String> expandNodes) throws Exception {
      DataSpace space = DataSpace.getDataSpace();
      String[] childEntries = space.list(parentPath);
      childEntries = sortEntry(parentPath, childEntries, space);
      List<DataSpaceTreeNodeModel> nodes = new ArrayList<>();

      for(String name : childEntries) {
         String nonOrgName = getDisplayName(name);
         String path = getPath(parentPath, name);

         if(expandNodes.isEmpty() || !space.isDirectory(path)) {
            DataSpaceTreeNodeModel node = DataSpaceTreeNodeModel.builder()
               .label(nonOrgName)
               .path(path)
               .folder(space.isDirectory(path))
               .build();
            nodes.add(node);
         }
         else {
            List<DataSpaceTreeNodeModel> children = getSubTree(path, expandNodes);
            DataSpaceTreeNodeModel node = DataSpaceTreeNodeModel.builder()
               .label(getDisplayName(path))
               .path(path)
               .folder(true)
               .children(children)
               .build();
            nodes.add(node);
         }
      }

      // if root
      if(parentPath == null) {
         DataSpaceTreeNodeModel rootNode = DataSpaceTreeNodeModel.builder()
            .label(Catalog.getCatalog().getString("Data Space"))
            .path("/")
            .folder(true)
            .children(nodes)
            .build();
         nodes = new ArrayList<>();
         nodes.add(rootNode);
      }

      return DataSpaceTreeModel.builder()
         .nodes(nodes)
         .build();
   }

   private List<DataSpaceTreeNodeModel> getSubTree(String folderPath, List<String> expandNodes) throws Exception {
      DataSpace space = DataSpace.getDataSpace();
      String[] childEntries = space.list(folderPath);
      childEntries = sortEntry(folderPath, childEntries, space);
      List<DataSpaceTreeNodeModel> children = new ArrayList<>();

      for(String name : childEntries) {
         String nonOrgName = getDisplayName(name);
         String path = getPath(folderPath, name);

         if(space.isDirectory(path)) {
            DataSpaceTreeNodeModel node = DataSpaceTreeNodeModel.builder()
               .label(nonOrgName)
               .path(path)
               .folder(true)
               .build();

            for(String expandPath : expandNodes) {
               if(path.equals(expandPath)) {
                  List<DataSpaceTreeNodeModel> subChildren = getSubTree(path, expandNodes);
                  node = DataSpaceTreeNodeModel.builder()
                     .label(nonOrgName)
                     .path(path)
                     .folder(true)
                     .children(subChildren)
                     .build();
               }
            }

            children.add(node);
         }
         else {
            DataSpaceTreeNodeModel node = DataSpaceTreeNodeModel.builder()
               .label(nonOrgName)
               .path(path)
               .folder(false)
               .build();
            children.add(node);
         }
      }

      return children;
   }


   public DataSpaceTreeNodeModel getTreeNode(String path) {
      DataSpace space = DataSpace.getDataSpace();
      return DataSpaceTreeNodeModel.builder()
         .label(getFileName(path))
         .path(path)
         .folder(space.isDirectory(path))
         .build();
   }

   public void repairFiles() {
      throw new UnsupportedOperationException("TODO: this is no longer supported, remove it");
   }

   protected String getFileName(String path) {
      if("/".equals(path)) {
         return Catalog.getCatalog().getString("Data Space");
      }
      else {
         int index = path.lastIndexOf("/");
         index = index < 0 ? 0 : index + 1;
         return path.substring(index);
      }
   }

   /**
    * Delete a node and all of its children from the data space
    *
    * @param path The path used to navigate to data space node
    */
   public void deleteDataSpaceNode(String path, boolean isFolder) {
      DataSpace dataSpace = DataSpace.getDataSpace();
      Principal principal = ThreadContext.getContextPrincipal();
      String objectType = isFolder ? ActionRecord.OBJECT_TYPE_FOLDER :
         ActionRecord.OBJECT_TYPE_FILE;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal),
                                                   ActionRecord.ACTION_NAME_DELETE, path,
                                                   objectType, actionTimestamp,
                                                   ActionRecord.ACTION_STATUS_SUCCESS, null);

      if(dataSpace.isDirectory(path)) {
         deleteChildren(path, dataSpace);
      }

      dataSpace.delete(null, path);

      if(path.startsWith("portal/" + OrganizationManager.getInstance().getCurrentOrgID() + "/shapes/") ||
         path.startsWith("portal/shapes/"))
      {
         ImageShapes.clearShapes();
      }

      CustomThemesManager.getManager().reloadThemes(path);
      Audit.getInstance().auditAction(actionRecord, principal);
   }

   // refresh last modified for the directory
   public void updateFolder(String path) {
      DataSpace.updateFolder(path);
   }

   /**
    * Delete all children of a directory from the data space.
    */
   private void deleteChildren(String path, DataSpace space) {
      if(!space.isDirectory(path)) {
         return;
      }

      String[] tentries = space.list(path);

      for(int i = 0; i < tentries.length; i++) {
         String vpath = getPath(path, tentries[i]);

         if(space.isDirectory(vpath)) {
            deleteChildren(vpath, space);
         }

         space.delete(null, vpath);
      }
   }

   /**
    * Sort the entries get from the dataspace.
    */
   private String[] sortEntry(String parent, String[] entries, DataSpace space)
      throws Exception
   {
      List folderEntry = new ArrayList();
      List fileEntry = new ArrayList();
      String[] folders;
      String[] files;

      for(int i = 0; i < entries.length; i++) {
         String name = entries[i];
         String path = getPath(parent, name);

         if(space.isDirectory(path)) {
            folderEntry.add(name);
         }
         else {
            fileEntry.add(name);
         }
      }

      folders = new String[folderEntry.size()];
      files = new String[fileEntry.size()];

      for(int i = 0; i < folderEntry.size(); i++) {
         folders[i] = (String) folderEntry.get(i);
      }

      for(int i = 0; i < fileEntry.size(); i++) {
         files[i] = (String) fileEntry.get(i);
      }

      Tool.qsort(folders, true);
      Tool.qsort(files, true);

      return (String[]) Tool.mergeArray(folders, files);
   }

   /**
    * Get full path.
    */
   protected String getPath(String parentPath, String name) {
      return (parentPath == null || "/".equals(parentPath) ? "" : parentPath + "/") + name;
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_DOWNLOAD,
      objectType = ActionRecord.OBJECT_TYPE_FILE
   )
   public void downloadFile(String path, @AuditObjectName String name, HttpServletResponse response,
                            HttpServletRequest request) throws Exception
   {
      DataSpace space = DataSpace.getDataSpace();
      OutputStream out = response.getOutputStream();
      String mime = "application/octet-stream";
      String suffix = "";

      if((name.indexOf(".") > 0)) {
         suffix = name.substring(name.lastIndexOf(".") + 1);
      }

      if("HTML_BUNDLE".equalsIgnoreCase(suffix) ||
         "HTML_BUNDLE_NO_PAGINATION".equalsIgnoreCase(suffix))
      {
         suffix = "html";
         mime = "text/html";
      }
      else if("ZIP".equalsIgnoreCase(suffix)) {
         mime = "application/zip";
      }
      else if("XLS".equalsIgnoreCase(suffix)) {
         mime = "application/x-excel";
      }
      else {
         int formatType = Builder.getType(suffix);
         ExportType exportType = Builder.getExportType(formatType);

         if(exportType != null) {
            suffix = exportType.getExtension();
            mime = exportType.getMimeType();
         }
      }

      if(!SUtil.isHttpHeadersValid(suffix) && !path.startsWith("autoSavedFile")) {
         throw new Exception("extension is invalid: " + suffix);
      }

      response.setHeader("extension", Tool.cleanseCRLF(suffix));
      String header = Tool.encodeNL(name);
      String fileName = StringUtils.normalizeSpace(header);

      //The Content-Disposition header does not support the direct use of /,
      //so / will be converted to _. Therefore, we will temporarily use ／ (full-width slash) instead of /.
      fileName = fileName.replace("/", "／");
      String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");

      if(!SUtil.isHttpHeadersValid(header)) {
         throw new Exception("name is invalid: " + header);
      }

      if(HTMLUtil.isIE(request)) {
         response.setHeader("Content-disposition", "attachment; filename=" +
            encodedFileName);

         response.setHeader("Cache-Control", "");

         if("https".equals(request.getScheme())) {
            response.setHeader("Pragma", "");
         }
      }
      else {
         response.setHeader("Content-disposition",
                            "attachment; filename*=utf-8'en-us'" +
                               encodedFileName);
      }

      try(InputStream in = space.getInputStream(null, path)) {
         response.setContentType(mime);
         int length = in.available();
         ByteArrayOutputStream bout = null;

         if(length == 0) {
            bout = new ByteArrayOutputStream();
            Tool.copyTo(in, bout);
            length = bout.size();
         }

         response.setContentLength(length);

         if(bout != null) {
            ByteArrayInputStream bin =
               new ByteArrayInputStream(bout.toByteArray());
            Tool.copyTo(bin, out);
         }
         else {
            Tool.copyTo(in, out);
         }

         out.close();
      }
   }

   public String getDisplayName(String fullName) {
      if(LicenseManager.getInstance().isEnterprise()) {
         return fullName;
      }

      String defaultOrgFolderStr = IdentityID.KEY_DELIMITER +
         Organization.getDefaultOrganizationID();

      if(fullName.endsWith(defaultOrgFolderStr)) {
         return fullName.substring(0, fullName.length() - defaultOrgFolderStr.length());
      }

      String defaultOrgXmlStr = defaultOrgFolderStr + ".xml";

      if(fullName.endsWith(defaultOrgXmlStr)) {
         return fullName.substring(0, fullName.length() - defaultOrgXmlStr.length()) + ".xml";
      }

      String hostOrgStr = Organization.getDefaultOrganizationID()+"__";

      if(fullName.startsWith(hostOrgStr)) {
         return fullName.substring(hostOrgStr.length());
      }

      return fullName;
   }

   public String getNewPath(String oldPath, String oldName, String newName) {
      String oldPrefix = oldPath.substring(0, oldPath.lastIndexOf(oldName));

      if(LicenseManager.getInstance().isEnterprise() || newName == null) {
         return oldPrefix + newName;
      }

      String defaultOrgStr = IdentityID.KEY_DELIMITER +
         Organization.getDefaultOrganizationID() + ".xml";

      if(oldName.endsWith(defaultOrgStr) && newName.endsWith(".xml")) {
         newName = newName.substring(0, newName.length() - 4) + defaultOrgStr;
      }

      String hostOrgStr = Organization.getDefaultOrganizationID()+ "__";

      if(oldName.startsWith(hostOrgStr)) {
         newName = hostOrgStr + newName;
      }

      return oldPrefix + newName;
   }

   public String getDisplayPath(String path, String originalName, String displayName) {
      if(LicenseManager.getInstance().isEnterprise() || path == null) {
         return path;
      }

      String displayPath = path;

      if(!Objects.equals(originalName, displayName)) {
         int index = path.indexOf(originalName);

         if(index >= 0) {
            displayPath = path.substring(0, index) + path.substring(index).replace(originalName, displayName);
         }
      }

      if(displayPath.startsWith(DEFAULT_ORG_FOLDER)) {
         return "portal" + displayPath.substring(DEFAULT_ORG_FOLDER.length());
      }

      return displayPath;
   }

   private boolean isDefaultOrgFolder(String path) {
      return DEFAULT_ORG_FOLDER.equals(path);
   }

   private void loadDefaultOrgFolderChildren(DataSpace space, List<DataSpaceTreeNodeModel> nodes)
      throws Exception
   {
      String[] childEntries = space.list(DEFAULT_ORG_FOLDER);
      childEntries = sortEntry(DEFAULT_ORG_FOLDER, childEntries, space);

      for(String name : childEntries) {
         String path = getPath(DEFAULT_ORG_FOLDER, name);

         DataSpaceTreeNodeModel node = DataSpaceTreeNodeModel.builder()
            .label(name)
            .path(path)
            .folder(space.isDirectory(path))
            .build();
         nodes.add(node);
      }
   }

   private static final String DEFAULT_ORG_FOLDER = "portal/" + Organization.getDefaultOrganizationID();
   private static final Logger LOG = LoggerFactory.getLogger(DataSpaceContentSettingsService.class);
}
