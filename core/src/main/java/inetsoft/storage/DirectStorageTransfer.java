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
package inetsoft.storage;

import com.fasterxml.jackson.core.JsonGenerator;
import inetsoft.sree.FolderContext;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.*;
import inetsoft.web.admin.general.DataSpaceSettingsService;
import liquibase.util.StringUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * {@code DirectStorageTransfer} is an implementation of {@link StorageTransfer} that directly reads
 * from and writes to the storage engines.
 */
public class DirectStorageTransfer extends AbstractStorageTransfer {
   /**
    * Creates a new instance of {@code DirectStorageTransfer}.
    *
    * @param keyValueEngine the key-value storage engine.
    * @param blobEngine     the blob storage engine.
    */
   public DirectStorageTransfer(KeyValueEngine keyValueEngine, BlobEngine blobEngine) {
      this.keyValueEngine = keyValueEngine;
      this.blobEngine = blobEngine;
   }

   @Override
   protected Stream<String> getKeyValueStoreIds() {
      return keyValueEngine.idStream();
   }

   @Override
   protected void exportStore(String id, JsonGenerator kvGenerator, JsonGenerator blobGenerator,
                              Path blobDir)
   {
      AtomicBoolean first = new AtomicBoolean(true);
      AtomicBoolean blob = new AtomicBoolean(false);
      AtomicBoolean empty = new AtomicBoolean(true);
      Set<String> blobIncludedKeys = DataSpaceSettingsService.getBlobIncludedKeys(id);

      keyValueEngine.stream(id).forEach(pair -> {
         try {
            boolean includedInBlob = pair.getValue() instanceof Blob && blobIncludedKeys != null &&
               !blobIncludedKeys.isEmpty() && !blobIncludedKeys.contains(pair.getKey());

            if(!includedInBlob) {
               export(id, pair, first.get(), kvGenerator, blobGenerator, blobDir);

               if(first.compareAndSet(true, false)) {
                  blob.set(pair.getValue() instanceof Blob);
                  empty.set(false);
               }
            }
         }
         catch(RuntimeException e) {
            LoggerFactory.getLogger(AbstractStorageTransfer.class).error(e.getMessage(), e);
         }
      });

      try {
         if(!empty.get()) {
            if(blob.get()) {
               blobGenerator.writeEndObject();
            }
            else {
               kvGenerator.writeEndObject();
            }
         }
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to write JSON", e);
      }
   }

   private void export(String id, KeyValuePair<?> pair, boolean first,
                       JsonGenerator kvGenerator, JsonGenerator blobGenerator, Path blobDir)
   {
      try {
         boolean blob = pair.getValue() instanceof Blob;

         if(first) {
            if(blob) {
               blobGenerator.writeObjectFieldStart(id);
            }
            else {
               kvGenerator.writeObjectFieldStart(id);
            }
         }

         if(blob) {
            exportBlob(id, pair.getKey(), (Blob<?>) pair.getValue(), blobGenerator, blobDir);
         }
         else {
            Wrapper wrapper = new Wrapper(pair.getValue());
            kvGenerator.writeObjectField(pair.getKey(), wrapper);
         }
      }
      catch(IOException e) {
         throw new
            RuntimeException("Failed to export storage. ID:" + id + " Key:" + pair.getKey(), e);
      }
   }

   private void exportBlob(String id, String key, Blob<?> blob, JsonGenerator generator, Path dir)
      throws IOException
   {
      generator.writeObjectField(key, blob);
      String digest = blob.getDigest();

      if(digest != null) {
         String path = digest.substring(0, 2) + "/" + digest.substring(2);
         Path file = dir.resolve(path);
         Files.createDirectories(file.getParent());
         blobEngine.read(id, digest, file);
      }
   }

   @Override
   protected void putKeyValue(String id, String key, Object value) {
      if(id.endsWith("__indexedStorage") && key.startsWith("1^4097^")) {
         // Fix children for imported AssetFolders
         Blob<?> oblob = keyValueEngine.get(id, key);

         if(oblob != null) {
            Blob<?> nblob = (Blob<?>) value;
            AssetFolder newAssetFolder = ((BlobIndexedStorage.Metadata) nblob.getMetadata()).getFolder();
            AssetFolder oldAssetFolder = ((BlobIndexedStorage.Metadata) oblob.getMetadata()).getFolder();

            for(AssetEntry entry : oldAssetFolder.getEntries()) {
               newAssetFolder.addEntry(entry);
            }
         }
      }

      keyValueEngine.put(id, key, value);
   }

   @Override
   protected void saveBlob(String id, String digest, Path file) throws IOException {
      blobEngine.write(id, digest, file);
   }

   @Override
   protected Blob<?> updateRegistryBlob(Blob<?> blob, String id, String key, Path file) throws IOException {
      if(id.equals("dataSpace")) {
         if(registryNames == null) {
            initRegistryNames();
         }

         for(String registryName : registryNames) {
            if(key.equals(registryName)) {
               Blob<?> oldBlob = keyValueEngine.get(id, key);

               if(oldBlob == null) {
                  break;
               }

               // Rewrite the new registry file to the temp file and get the new digest
               Path temp = Files.createTempFile("import-storage", ".dat");
               String oldDigest = oldBlob.getDigest();
               blobEngine.read(id, oldDigest, temp);
               String digest;

               Hashtable<String, Hashtable<String, FolderContext>> folders = new Hashtable<>();

               try(InputStream input = new FileInputStream(file.toFile())) {
                  readFolders(folders, input);
               }

               try(InputStream input = new FileInputStream(temp.toFile())) {
                  readFolders(folders, input);
               }

               try(OutputStream output = new FileOutputStream(file.toFile())) {
                  save(output, folders);
               }

               try(InputStream input = new FileInputStream(file.toFile())) {
                  digest = DigestUtils.md5Hex(input);
               }

               Files.delete(temp);
               return new Blob<>(blob.getPath(), digest, file.toFile().length(),
                                  blob.getLastModified(), blob.getMetadata());
            }
         }
      }

      return blob;
   }

   private void initRegistryNames() {
      String repfiles = keyValueEngine.get("sreeProperties", "replet.repository.file");
      repfiles = repfiles == null ? "repository.xml" : repfiles;
      registryNames = StringUtil.splitAndTrim(repfiles, ";");
   }

   private void readFolders(Hashtable<String, Hashtable<String, FolderContext>> folders,
                            InputStream input) throws IOException
   {
      try {
         Document doc = Tool.parseXML(input);
         NodeList replets = doc.getElementsByTagName("Replet");

         for(int i = 0; i < replets.getLength(); i++) {
            Element elem = (Element) replets.item(i);
            String name = elem.getAttribute("name");
            String alias = elem.getAttribute("alias");
            String orgId = elem.getAttribute("orgID");
            String favoritesUser = elem.getAttribute("favoritesUser");
            String description = elem.getTextContent();
            description = description == null ? "" : description;

            FolderContext context = new FolderContext(name, description, alias);
            context.addFavoritesUser(favoritesUser);

            Hashtable<String, FolderContext> orgFolders =
               folders.computeIfAbsent(orgId, k -> new Hashtable<>());
            orgFolders.putIfAbsent(name, context);
         }
      }
      catch(ParserConfigurationException e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Save the registry to a specific stream.
    *
    * @param stream the registry stream.
    */
   private synchronized void save(OutputStream stream,
                                  Hashtable<String, Hashtable<String, FolderContext>> folders)
      throws IOException
   {
      try {
         PrintWriter writer =
            new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));

         writer.println("<?xml version=\"1.0\"  encoding=\"UTF-8\"?>");
         writer.println("<Registry>");
         writer.println("<Version>" + FileVersions.REPOSITORY + "</Version>");

         writeRegistryFoldersAndProtos(writer, folders);
         writer.println("</Registry>");
         writer.flush();
      }
      catch(Throwable ex) {
         throw new IOException("Failed to save registry file", ex);
      }
   }

   /**
    * Write folder.
    */
   private synchronized void writeRegistryFoldersAndProtos(PrintWriter writer,
                                                           Hashtable<String, Hashtable<String, FolderContext>> folders)
   {
      for(String orgID : folders.keySet()) {
         Hashtable<String, FolderContext> orgFolders = folders.get(orgID);

         for(String name : orgFolders.keySet()) {
            FolderContext folder = orgFolders.get(name);
            String alias = folder.getAlias();
            writer.print("<Replet name=\"" + Tool.escape(folder.getName()) + "\"" +
                            (alias == null ? "" : " alias=\"" + Tool.escape(alias) + "\"") +
                            " orgID=\"" + orgID + "\" folder=\"true\"" +
                            " favoritesUser=\"" + folder.getFavoritesUser() + "\"" + ">");

            if(folder.getDescription() != null) {
               writer.print("<![CDATA[" + folder.getDescription() + "]]>");
            }

            writer.println("</Replet>");
         }
      }
   }

   private final KeyValueEngine keyValueEngine;
   private final BlobEngine blobEngine;
   private List<String> registryNames;
}
