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

import inetsoft.report.internal.Util;
import inetsoft.sree.internal.HttpXMLSerializable;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.storage.ExternalStorageService;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Tool;
import inetsoft.util.dep.AbstractSheetAsset;
import inetsoft.util.dep.XAsset;
import inetsoft.web.admin.deploy.DeployUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.PrintWriter;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.*;

/**
 * A schedule action that backs up a specified asset file to specified location.
 *
 * @version 13.1
 * @author InetSoft Technology Corp
 */
public class IndividualAssetBackupAction implements ScheduleAction, HttpXMLSerializable {
   /**
    * Execute the action.
    * @param principal represents an entity
    */
   @Override
   public void run(Principal principal) throws Throwable {
      if(assets.isEmpty()) {
         return;
      }

      Object[] msgParams = { assets.getFirst().getPath(), new Date() };
      File srcFile = deploy();

      String path = pathInfo.getPath();
      int idx = path.indexOf("?");
      boolean append = false;

      if(pathInfo != null && pathInfo.isFTP() && idx != -1 && path.length() > idx + 1) {
         String queryParam = path.substring(idx + 1);
         HashMap<String, String> map = Util.getQueryParamMap(queryParam);
         append = "true".equals(map.get("append"));
         path = path.substring(0, idx);
      }

      try {
         String str = path + ".zip";

         try {
            str = MessageFormat.format(str, msgParams);
         }
         catch(IllegalArgumentException iae) {
            // ignore it
            LOG.error("Failed to format path " + str + " using parameters " + Arrays.toString(msgParams), iae);
         }

         String backupFileName;

         if(pathInfo != null && pathInfo.isFTP()) {
            int index = Tool.replaceAll(str, "\\", "/").lastIndexOf("/");
            backupFileName = "backup/" + str.substring(index + 1);
         }
         else {
            backupFileName = str;
         }

         ExternalStorageService.getInstance().write(backupFileName, srcFile.toPath(), principal);

         if(pathInfo != null && pathInfo.isFTP()) {
            FTPUtil.uploadToFTP(str, srcFile, pathInfo, append);
         }
      }
      catch(Throwable ex) {
         LOG.error("Failed to backup assets.", ex);
         throw ex;
      }
      finally {
         Tool.deleteFile(srcFile);
      }
   }

   private File deploy() throws Exception {
      List<XAsset> dependencies = DeployUtil.getDependentAssetsList(assets);

      List<XAsset> allAssets = new ArrayList<>(assets);
      allAssets.addAll(dependencies);
      testAssetsExist(allAssets);

      return DeployUtil.deploy("backupAssetFile_TEMP", true, assets, dependencies);
   }

   private void testAssetsExist(List<XAsset> dependencies) {
      //by nickgovus, 2023.11.20, Bug #63146, add to failed if not exists, throw failed list
      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
      List<XAsset> failedAssets = new ArrayList<>();
      AssetEntry assetEntry;

      for(XAsset asset : dependencies) {

         if(asset instanceof AbstractSheetAsset) {
            assetEntry = ((AbstractSheetAsset) asset).getAssetEntry();

            try {
               AssetEntry regE = assetRepository.getAssetEntry(assetEntry);

               if(regE == null) {
                  failedAssets.add(asset);
               }
            }
            catch(Exception e) {
               throw new RuntimeException("Error retrieving asset from repository: "+assetEntry);
            }
         }
      }

      if(!failedAssets.isEmpty()) {
         throw new RuntimeException("Failed to retrieve asset(s):" + failedAssets);
      }
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<Action type=\"Backup\"");
      writer.println(">");
      pathInfo.writeXML(writer);

      for(XAsset asset : assets) {
         writer.println("<XAsset type=\"" + asset.getType() + "\" path=\"" +
                           byteEncode(asset.getPath()) + "\" user=\"" +
                           (asset.getUser() != null ? asset.getUser().convertToKey() : "") + "\">");
         writer.println("</XAsset>");
      }

      writer.println("</Action>");
   }

   @Override
   public void parseXML(Element action) throws Exception {
      String filePath = Tool.getAttribute(action, "path");
      ServerPathInfo pathInfo = new ServerPathInfo();

      if(filePath == null) {
         Element node = Tool.getChildNodeByTagName(action, "ServerPath");
         assert node != null;
         pathInfo.parseXML(node);
      }
      else {
         filePath = byteDecode(filePath);
         pathInfo.setPath(filePath);
      }

      this.pathInfo = pathInfo;
      final NodeList nodes = action.getChildNodes();
      assets = new ArrayList<>();

      for(int i = 0; i < nodes.getLength(); i++) {
         if(nodes.item(i) instanceof Element node && !node.getNodeName().equals("ServerPath")) {
            String path = byteDecode(node.getAttribute("path"));
            String type = node.getAttribute("type");
            String userString = node.getAttribute("user");
            IdentityID user = "null".equals(userString) || IdentityID.KEY_DELIMITER.equals(userString) ||
               Tool.isEmptyString(userString) ? null : IdentityID.getIdentityIDFromKey(userString);
            XAsset xAsset = SUtil.getXAsset(type, path, user);
            assets.add(xAsset);
         }
      }
   }

   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source source string.
    * @return encoded string.
    */
   @Override
   public String byteEncode(String source) {
      return encoding ? Tool.byteEncode2(source) : source;
   }

   /**
    * Convert the encoded string to the original unencoded string.
    * @param encString a string encoded using the byteEncode method.
    * @return original string.
    */
   @Override
   public String byteDecode(String encString) {
      return encoding ? Tool.byteDecode(encString) : encString;
   }

   @Override
   public String toString() {
      return "Backup";
   }

   @Override
   public boolean isEncoding() {
      return this.encoding;
   }

   @Override
   public void setEncoding(boolean encoding) {
      this.encoding = encoding;
   }

   public List<XAsset> getAssets() {
      return assets;
   }

   public void setAssets(List<XAsset> assets) {
      this.assets = assets;
   }

   public String getPath() {
      return pathInfo.getPath();
   }

   public ServerPathInfo getServerPath() {
      return pathInfo;
   }

   public void setPaths(String path) {
      if(pathInfo == null) {
         pathInfo = new ServerPathInfo(path);
      }
      else {
         this.pathInfo.setPath(path);
      }
   }

   public void setServerPaths(ServerPathInfo path) {
      this.pathInfo = path;
   }

   private List<XAsset> assets = new ArrayList<>();
   private ServerPathInfo pathInfo;
   private boolean encoding = true;

   private static final Logger LOG =
      LoggerFactory.getLogger(IndividualAssetBackupAction.class);
}
