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
package inetsoft.web;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import inetsoft.util.migrate.MigrateViewsheetTask;
import inetsoft.util.migrate.MigrateWorksheetTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.*;
import java.io.*;
import java.nio.file.NoSuchFileException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility methods operating for recycle bin auto save files.
 *
 * @author InetSoft Technology Corp
 * @version 13.4
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
      String ip = paths.length > 4 ? paths[4] : null;

      String typeStr = "VIEWSHEET".equals(type) ? "^128^" : "^2^";
      AssetEntry entry = AssetEntry.createAssetEntry(scope + typeStr + ouser + "^" + name +
                                                        "^" + ip);
      entry.setProperty("openAutoSaved", "true");
      entry.setProperty("autoFileName", autoFile);
      entry.setProperty("isRecycle", "true");

      return entry;
   }

   // If saved vs, get its auto saved file by create file name.
   // If unsaved vs(untitled vs), should get its auto save file from file name.  For its file name
   // is fixed, will not changed by login user.
   public static String getAutoSavedFile(AssetEntry entry, Principal user) {
      String fileName = entry.getProperty("autoFileName");
      boolean isRecycle = "true".equals(entry.getProperty("isRecycle"));

      if(entry.getScope() == AssetRepository.TEMPORARY_SCOPE && fileName != null) {
         fileName = SUtil.addAutoSaveOrganization(fileName);
         return getAutoSavedByName(fileName, isRecycle);
      }

      return getAutoSavedFile(entry, user, false);
   }

   public static List<String> getUserAutoSaveFiles(Principal principal) {
      List<String> autoSaveFiles = new ArrayList<>();
      String userName = getUserName(principal);
      List<String> list = getAutoSavedFiles(principal, false);
      String ipAddress = getIPAddress(principal);
      String ipString = Tool.replaceAll(ipAddress, ":", "_") + "~";

      if(list != null) {
         for(String file : list) {
            String asset = getName(file);
            String[] attrs = Tool.split(asset, '^');

            if(attrs.length > 3) {
               String user = attrs[2];

               if(!Tool.equals(AssetRepository.TEMPORARY_SCOPE + "", attrs[0])) {
                  continue;
               }

               if(Tool.equals(userName, user) && Tool.equals(ipString, attrs[4])) {
                  try {
                     if("WORKSHEET".equals(attrs[1])) {
                        IdentityID realUser = "_NULL_".equals(attrs[2]) ? null : new IdentityID(attrs[2], OrganizationManager.getInstance().getCurrentOrgID());
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

         if(sheet == null) {
            IndexedStorage storage =  assetRepository.getStorage(entry);
            XMLSerializable obj = ((AbstractIndexedStorage) storage).getAutoSavedSheet(entry, user);

            if(obj == null) {
               return false;
            }

            sheet = (Worksheet)obj;
         }
      }
      catch(Exception e) {
         // ignore
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
      List<String> list = getAutoSavedFiles(user, false);
      String ipAddress = getIPAddress(user);
      String ipString = Tool.replaceAll(ipAddress, ":", "_") + "~";

      if(list != null) {
         for(String file : list) {
            String asset = getName(file);
            String[] attrs = Tool.split(asset, '^');

            if(attrs.length > 3) {
               String fileUser = attrs[2];

               if(!Tool.equals(AssetRepository.TEMPORARY_SCOPE + "", attrs[0])) {
                  continue;
               }

               if(Tool.equals(userName, fileUser) && Tool.equals(ipString, attrs[4])) {
                  String recycleFile = getAutoSavedByName(asset, true);
                  renameAutoSaveFile(file, recycleFile, user);
               }
            }
         }
      }
   }

   private static String getUserName(Principal user) {
      return user != null ? user.getName() :
         new IdentityID("_NULL_",Organization.getDefaultOrganizationID()).convertToKey();
   }

   public static List<String> getAutoSavedFiles(Principal principal, boolean recycle) {
      BlobStorage<Metadata> blobStorage = getStorage(principal);
      return blobStorage.paths().filter(path -> {
         boolean isRecyclePath = path.startsWith(RECYCLE_PREFIX);
         return (recycle && isRecyclePath) || (!recycle && !isRecyclePath);
      }).collect(Collectors.toList());
   }

   private static List<String> getAutoSavedFiles(Principal principal) {
      BlobStorage<Metadata> blobStorage = getStorage(principal);
      return blobStorage.paths().collect(Collectors.toList());
   }

   public static void renameAutoSaveFile(String source, String target, Principal principal) {
      try {
         BlobStorage<Metadata> blobStorage = getStorage(principal);
         blobStorage.rename(source, target);
      }
      catch(Exception e) {
         LOG.debug("Failed to write auto save file to recycle bin {}", source, e);
      }
   }

   // Delete auto save file not in recycle bin
   public static void deleteAutoSaveFile(AssetEntry entry, Principal user) {
      try {
         String file = getAutoSavedFile(entry, user, false);

         if(!exists(file, user)) {
            return;
         }

         deleteFileFromStorage(file, user);
      }
      catch(FileNotFoundException ignore){
      }
      catch(Exception e) {
         LOG.debug("Failed to delete auto save file {}", entry, e);
      }
   }

   public static void deleteAutoSaveFile(String id, Principal principal) {
      try {
         String file = getAutoSavedByName(id, true);

         if(!exists(file, principal)) {
            return;
         }

         deleteFileFromStorage(file, principal);
      }
      catch(Exception e) {
         LOG.debug("Failed to delete auto save file to recycle bin {}", id, e);
      }
   }

   public static void deleteRecycledAutoSaveFiles(Principal principal) {
      try {
         BlobStorage<Metadata> blobStorage = getStorage(principal);
         List<String> files = getAutoSavedFiles(principal, true);

         for(String file : files) {
            blobStorage.delete(file);
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
   public static String getAutoSavedFile(AssetEntry entry, Principal user, boolean recycle) {
      String userName = user != null ? user.getName() : null;
      String ipAddress = getIPAddress(user);
      String nullUser = new IdentityID("_NULL_",Organization.getDefaultOrganizationID()).convertToKey();

      String name = entry.getScope() + "^" + entry.getType() + "^" +
              (userName == null ? nullUser : userName) + "^" +
              Tool.normalizeFileName(entry.getPath()) + "^" +
              Tool.replaceAll(ipAddress, ":", "_") + "~";

      return getAutoSavedByName(name, recycle);
   }

   public static String getAutoSavedTime(String name, Principal principal, String clientTimeZone) {
      String file = getAutoSavedByName(name, true);
      BlobStorage<Metadata> blobStorage = getStorage(principal);
      long time = 0;

      try {
         time = blobStorage.getLastModified(file).toEpochMilli();
      }
      catch(FileNotFoundException e) {
         return "";
      }

      Date date = new Date(time);
      SimpleDateFormat format = new SimpleDateFormat(SreeEnv.getProperty("format.date.time"));
      format.setTimeZone(TimeZone.getTimeZone(clientTimeZone));
      return format.format(date);
   }

   private static String getAutoSavedByName(String name, boolean recycle) {
      return recycle ? RECYCLE_PREFIX + name : name;
   }

   public static BlobStorage<Metadata> getStorage(Principal principal) {
      if(principal == null) {
         principal = ThreadContext.getContextPrincipal();
      }

      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);

      if(orgId == null) {
         orgId = OrganizationManager.getInstance().getCurrentOrgID();
      }

      return SingletonManager.getInstance(BlobStorage.class, orgId.toLowerCase() + "__autoSave", true);
   }

   public static long getLastModified(AssetEntry entry, Principal principal) {
      String file = getAutoSavedFile(entry, principal);
      return getLastModified(file, principal);
   }

   public static long getLastModified(String file, Principal principal) {
      BlobStorage<Metadata> blobStorage = getStorage(principal);

      try {
         return blobStorage.getLastModified(file).toEpochMilli();
      }
      catch(FileNotFoundException e) {
         // do nothing
      }

      return 0;
   }

   public static boolean exists(AssetEntry entry, Principal principal) {
      String file = getAutoSavedFile(entry, principal);
      return exists(file, principal);
   }

   public static boolean exists(String file, Principal principal) {
      BlobStorage<Metadata> blobStorage = getStorage(principal);
      return blobStorage.exists(file);
   }

   public static String getName(String file) {
      if(file != null && file.startsWith(RECYCLE_PREFIX)) {
         return file.substring(RECYCLE_PREFIX.length());
      }

      return file;
   }

   public static InputStream getInputStream(AssetEntry entry, Principal principal) throws IOException {
      String file = getAutoSavedFile(entry, principal);
      return getInputStream(file, principal);
   }

   public static InputStream getInputStream(String file, Principal principal) throws IOException {
      BlobStorage<Metadata> blobStorage = getStorage(principal);

      try {
         return blobStorage.getInputStream(file);
      }
      catch(FileNotFoundException | NoSuchFileException ignore) {
         return null;
      }
   }

   public static void writeAutoSaveFile(byte[] data, AssetEntry entry, Principal principal) {
      BlobStorage<Metadata> blobStorage = getStorage(principal);
      String file = getAutoSavedFile(entry, principal);

      try(BlobTransaction<Metadata> tx = blobStorage.beginTransaction();
          OutputStream out = tx.newStream(file, null))
      {
         out.write(data);
         out.flush();
         tx.commit();
      }
      catch(IOException ex) {
         LOG.error("Failed to write to the blob storage: {}", file, ex);
      }
   }

   public static void migrateAutoSaveFiles(Organization oorg, Organization norg, Principal principal) {
      if(oorg.getId().equals(norg.getId())) {
         return;
      }

      List<String> list = AutoSaveUtils.getAutoSavedFiles(principal);

      if(list.isEmpty()) {
         return;
      }

      for(String file : list) {
         String asset = AutoSaveUtils.getName(file);
         String[] attrs = Tool.split(asset, '^');

         if(attrs.length <= 3) {
            continue;
         }

         String user = attrs[2];
         user = "anonymous".equals(user) ? "_NULL_" : user;

         if(user == null || Tool.equals(user, "_NULL_")) {
            continue;
         }

         IdentityID userID = IdentityID.getIdentityIDFromKey(user);

         if(!oorg.getId().equals(userID.getOrgID())) {
            continue;
         }

         userID.setOrgID(norg.getId());
         attrs[2] = userID.convertToKey();
         String newFilePath = String.join("^", attrs);
         AutoSaveUtils.renameAutoSaveFile(file, newFilePath, principal);
         Document document = null;

         try(InputStream in = AutoSaveUtils.getInputStream(newFilePath, principal)) {
            AssetEntry assetEntry = AutoSaveUtils.createAssetEntry(
               file.startsWith(AutoSaveUtils.RECYCLE_PREFIX) ?
                  file.substring(AutoSaveUtils.RECYCLE_PREFIX.length()) : file);
            document = Tool.parseXML(in);

            if(assetEntry != null) {
               if(assetEntry.isViewsheet()) {
                  new MigrateViewsheetTask(assetEntry, oorg, norg, document).process();
               }
               else if(assetEntry.isWorksheet()) {
                  new MigrateWorksheetTask(assetEntry, oorg, norg, document).process();
               }
            }
         }
         catch(Exception e) {
            LOG.error("Failed to migrate the auto save sheet", e);
         }

         if(document != null) {
            migrateProcessingInstructionAndWrite(document, norg, newFilePath, principal);
         }
      }
   }

   private static void migrateProcessingInstructionAndWrite(Document document, Organization norg,
                                                            String newFilePath, Principal principal)
   {
      if(document == null) {
         return;
      }

      ByteArrayOutputStream bout = new ByteArrayOutputStream();

      NodeList childNodes = document.getChildNodes();
      String identifier = null;
      String className = null;

      for(int i = 0; i < childNodes.getLength(); i++) {
         Node childNode = childNodes.item(i);

         if(childNode.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
            String nodeValue = childNode.getNodeValue();
            StringBuilder builder = new StringBuilder();
            builder.append("<");
            builder.append(childNode.getNodeName());
            builder.append(" ");
            builder.append(nodeValue);
            builder.append("/>");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder;

            try {
               docBuilder = factory.newDocumentBuilder();
               Document newDocument = docBuilder.parse(new InputSource(new StringReader(builder.toString())));
               Element documentElement = newDocument.getDocumentElement();
               NamedNodeMap attributes = documentElement.getAttributes();

               for(int j = 0; j < attributes.getLength(); j++) {
                  Node attr = attributes.item(j);
                  String attrName = attr.getNodeName();

                  if(Tool.equals(attrName, "identifier")) {
                     AssetEntry assetEntry = AssetEntry.createAssetEntry(attr.getNodeValue());
                     identifier = assetEntry.cloneAssetEntry(norg).toIdentifier();
                  }
                  else if(Tool.equals(attrName, "classname")) {
                     className = attr.getNodeValue();
                  }
               }

               if(identifier != null && className != null) {
                  break;
               }
            }
            catch(Exception e) {
               LOG.error("Failed to migrate the processing instruction", e);
            }
         }
      }

      XMLTool.writeAssets(document, bout, className, identifier);
      AutoSaveUtils.writeAutoSaveFile(bout.toByteArray(), newFilePath, principal);
   }

   public static void writeAutoSaveFile(byte[] data, String file, Principal principal) {
      BlobStorage<Metadata> blobStorage = getStorage(principal);

      try(BlobTransaction<Metadata> tx = blobStorage.beginTransaction();
          OutputStream out = tx.newStream(file, null))
      {
         out.write(data);
         out.flush();
         tx.commit();
      }
      catch(IOException ex) {
         LOG.error("Failed to write to the blob storage: {}", file, ex);
      }
   }

   public static void writeAutoSaveFile(InputStream in, String file, Principal principal) {
      BlobStorage<Metadata> blobStorage = getStorage(principal);

      try(BlobTransaction<Metadata> tx = blobStorage.beginTransaction();
          OutputStream out = tx.newStream(file, null))
      {
         Tool.copyTo(in, out);
         tx.commit();
      }
      catch(IOException ex) {
         LOG.error("Failed to write to the blob storage: {}", file, ex);
      }
   }

   private static void deleteFileFromStorage(String file, Principal principal) throws Exception {
      BlobStorage<Metadata> blobStorage = getStorage(principal);
      blobStorage.delete(file);
   }

   public static final String RECYCLE_PREFIX = "recycle/";
   private static final Logger LOG = LoggerFactory.getLogger(AutoSaveUtils.class);

   public static final class Metadata implements Serializable {
   }
}
