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
package inetsoft.web;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Utility methods operating for recycle bin auto save files.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public final class AutoSaveUtils {
    // Create asset entry according to auto save file's file name
    // property file name will get right file to open
    // isCycle means get auto save file from recycle bin.
    public static AssetEntry createAssetEntry(String autoFile) {
       String[] paths = Tool.split(autoFile, '^');

       if(paths.length < 3) {
          return null;
       }

       String scope = paths[0];
       String type = paths[1];
       String ouser = paths[2];
       String name = paths[3];
       String orgId = paths.length > 4 ? paths[4] : null;

       String typeStr = "VIEWSHEET".equals(type) ? "^128^" : "^2^";
       AssetEntry entry = AssetEntry.createAssetEntry(scope + typeStr + ouser + "^" + name +
                                                         "^" + orgId);
       entry.setProperty("openAutoSaved", "true");
       entry.setProperty("autoFileName", autoFile);
       entry.setProperty("isRecycle", "true");

       return entry;
    }

   // If saved vs, get its auto saved file by create file name.
   // If unsaved vs(untitled vs), should get its auto save file from file name.  For its file name
   // is fixed, will not changed by login user.
   public static File getAutoSavedFile(AssetEntry entry, Principal user) {
      String fileName = entry.getProperty("autoFileName");
      fileName = SUtil.addAutoSaveOrganization(fileName);
      boolean isCycle = "true".equals(entry.getProperty("isRecycle"));

      if(entry.getScope() == AssetRepository.TEMPORARY_SCOPE && fileName != null) {
         return getAutoSavedByName(fileName, isCycle);
      }

      return getAutoSavedFile(entry, user, false);
   }

   public static List<String> getUserAutoSaveFiles(Principal principal) {
      List<String> autoSaveFiles = new ArrayList<>();
      String userName = getUserName(principal);
      File[] list = getAutoSaveFiles();
      String ipAddress = getIPAddress(principal);
      String ipString = Tool.replaceAll(ipAddress, ":", "_") + "~";

      if(list != null) {
         for(File file : list) {
            String asset = file.getName();
            String[] attrs = Tool.split(asset, '^');

            if(file.isFile() && attrs.length > 3) {
               String user = attrs[2];

               if(!Tool.equals(AssetRepository.TEMPORARY_SCOPE + "", attrs[0])) {
                  continue;
               }

               if(Tool.equals(userName, user) && Tool.equals(ipString, attrs[4])) {
                  try {
                     if("WORKSHEET".equals(attrs[1])) {
                        IdentityID realUser = "_NULL_".equals(attrs[2]) ? null : new IdentityID(attrs[2], OrganizationManager.getCurrentOrgName());
                        AssetEntry entry = new AssetEntry(
                           Integer.parseInt(attrs[0]), AssetEntry.Type.WORKSHEET, attrs[3], realUser);

                        if(!isUndoable(entry, principal)) {
                           continue;
                        }
                     }
                  }
                  catch(Exception ex) {
                     // ignore
                  }

                  autoSaveFiles.add(asset);
               }
            }
         }
      }

      return autoSaveFiles;
   }

   public static boolean isUndoable(AssetEntry entry, Principal user) {
       if(!entry.isWorksheet()) {
          return true;
       }

      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
      Worksheet sheet = null;

      try {
         sheet = (Worksheet)
            assetRepository.getSheet(entry, user, false, AssetContent.NO_DATA);
      }
      catch(Exception e) {
         // ignore
      }

      if(sheet == null) {
         return false;
      }

      for(Assembly assembly : sheet.getAssemblies()) {
         if(!assembly.isUndoable()) {
            return false;
         }
      }

      return true;
   }

   public static void recycleUserAutoSave(Principal user) {
      String userName = getUserName(user);
      File[] list = getAutoSaveFiles();
      String ipAddress = getIPAddress(user);
      String ipString = Tool.replaceAll(ipAddress, ":", "_") + "~";

      if(list != null) {
         for(File file : list) {
            String asset = file.getName();
            String[] attrs = Tool.split(asset, '^');

            if(file.isFile() && attrs.length > 3) {
               String fileUser = attrs[2];

               if(!Tool.equals(AssetRepository.TEMPORARY_SCOPE + "", attrs[0])) {
                  continue;
               }

               if(Tool.equals(userName, fileUser) && Tool.equals(ipString, attrs[4])) {
                  recycleAutoSaveFile(file, getRecycleFile(asset));
               }
            }
         }
      }
   }

   private static String getUserName(Principal user) {
      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      return user != null && securityEngine.isSecurityEnabled() ? user.getName() : "_NULL_";
   }

   private static File getRecycleFile(String name) {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      String folderPath = SreeEnv.getProperty("sree.home") + "/" + "autoSavedFile/recycle/";
      String path = folderPath + name;
      File folder = fileSystemService.getFile(folderPath);
      File file = fileSystemService.getFile(path);

      try {
         if(!folder.exists()) {
            folder.mkdirs();
         }

         if(!file.exists()) {
            file.createNewFile();
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to get recycle file {}", file, e);
      }

      return file;
   }

   private static File[] getAutoSaveFiles() {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      String path = SreeEnv.getProperty("sree.home") + "/" + "autoSavedFile";
      File folder = fileSystemService.getFile(path);

      if(!folder.exists()) {
         return null;
      }

      return folder.listFiles();
   }

   public static void recycleAutoSaveFile(File source, File target) {
      try {
         if(!target.exists()) {
            target.createNewFile();
         }

         FileSystemService fileSystemService = FileSystemService.getInstance();
         fileSystemService.rename(source, target);
      }
      catch(Exception e) {
         LOG.debug("Failed to write auto save file to recycle bin {}", source, e);
      }
   }

   // Delete auto save file not in recycle bin
   public static void deleteAutoSaveFile(AssetEntry entry, Principal user) {
      try {
         File file = getAutoSavedFile(entry, user, false);

         if(!file.exists()) {
            return;
         }

         FileSystemService fileSystemService = FileSystemService.getInstance();
         fileSystemService.deleteFile(file);
      }
      catch(Exception e) {
         LOG.debug("Failed to delete auto save file {}", entry, e);
      }
   }

   public static void deleteAutoSaveFile(String id) {
      try {
         File file = getAutoSavedByName(id, true);

         if(!file.exists()) {
            return;
         }

         FileSystemService fileSystemService = FileSystemService.getInstance();
         fileSystemService.deleteFile(file);
      }
      catch(Exception e) {
         LOG.debug("Failed to delete auto save file to recycle bin {}", id, e);
      }
   }

   public static void deleteAllAutoSaveFile() {
      try {
         String path = SreeEnv.getProperty("sree.home") + "/" + "autoSavedFile/recycle";
         FileSystemService fileSystemService = FileSystemService.getInstance();
         File folder = fileSystemService.getFile(path);

         if(folder.exists()) {
            File[] files = folder.listFiles();

            for(int i = 0; files != null && i < files.length; i++) {
               fileSystemService.deleteFile(files[i]);
            }
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to delete auto save file to recycle bin");
      }
   }

   private static String getIPAddress(Principal user) {
      String ipAddress = null;

      if(user instanceof SRPrincipal) {
         SRPrincipal srprincipal = (SRPrincipal) user;
         ipAddress = srprincipal.getUser().getIPAddress();
      }

      return ipAddress;
   }

   /**
    * Get the path of auto saved file.
    */
   public static File getAutoSavedFile(AssetEntry entry, Principal user, boolean recycle) {
      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      String userName = user != null && securityEngine.isSecurityEnabled() ? user.getName() : null;
      String ipAddress = getIPAddress(user);

      String name = entry.getScope() + "^" + entry.getType() + "^" +
              (userName == null ? "_NULL_" : userName) + "^" +
              Tool.normalizeFileName(entry.getPath()) + "^" +
              Tool.replaceAll(ipAddress, ":", "_") + "~";

      return getAutoSavedByName(name, recycle);
   }

   public static String getAutoSavedTime(String name) {
      File file = getAutoSavedByName(name, true);
      long time = file.lastModified();
      Date date = new Date(time);
      SimpleDateFormat format = new SimpleDateFormat(SreeEnv.getProperty("format.date.time"));

      return format.format(date);
   }

   private static File getAutoSavedByName(String name, boolean recycle) {
      SreeEnv.getProperty("sree.home");
      String path = SreeEnv.getProperty("sree.home") + "/" + "autoSavedFile";

      if(recycle) {
         path = path + "/recycle";
      }

      FileSystemService fileSystemService = FileSystemService.getInstance();
      File folder = fileSystemService.getFile(path);

      if(!folder.exists()) {
         folder.mkdirs();
      }

      return fileSystemService.getFile(folder.getPath() + "/" + name);
   }

   private static final Logger LOG = LoggerFactory.getLogger(AutoSaveUtils.class);
}
