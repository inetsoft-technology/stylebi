/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.asset;

import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class EmbeddedDataCacheHandler {
   public EmbeddedDataCacheHandler(EmbeddedTableAssembly assembly) {
      this.assembly = assembly;
   }

   public void writeEmbeddedData(PrintWriter writer) {
      XEmbeddedTable xdata = assembly.getEmbeddedData();
      String folder = getAssemblyCacheFolder();

      System.err.println("\n-------cachefolder: " + folder);
      System.err.println("-------worksheet: " + assembly.getWorksheet().getWsID());
      System.err.println("-------getAssemblyEntry: " + assembly.getAssemblyEntry());

      try {
         if(!writeEmbeddedDataWithCache(writer)) {
            xdata.reset();
            int blockIndex = 0;

            while(xdata.hasNextBlock()) {
               Path blockPath = getCacheFilePath(folder, blockIndex);
               ByteArrayOutputStream buf = new ByteArrayOutputStream();

               // write cache blob files
               try(DataOutputStream out = new DataOutputStream(buf)) {
                  xdata.writeData(out, true);
                  byte[] encoded = Base64.getEncoder().encode(buf.toByteArray());
                  writeToCacheAtomically(blockPath, encoded);
               }

               // write embedded data from cache
               try(BufferedReader reader = Files.newBufferedReader(blockPath)) {
                  reader.lines().forEach(writer::println);
               }

               blockIndex++;
            }
         }
      }
      catch(IOException e) {
         Catalog catalog = Catalog.getCatalog();
         String wsId = assembly.getWorksheet().getWsID();
         String wsName = AssetEntry.createAssetEntry(wsId).getName();
         throw new RuntimeException(catalog.getString("Cache operation for {0} in worksheet {1} failed",
                                                      assembly.getAbsoluteName(), wsName), e);
      }
   }

   /**
    * Write embedded data by read block caches.
    */
   private boolean writeEmbeddedDataWithCache(PrintWriter writer) throws IOException {
      String folder = getAssemblyCacheFolder();

      Path cachePath = getCacheFilePath(folder);
      boolean existed = Files.exists(cachePath);

      if(!existed) {
         return false;
      }

      File dir = cachePath.toFile();

      if(dir.isDirectory()) {
         List<File> files = sortedCacheBlocks(dir);
         System.err.println("=======existCache: " + files.size());
         for(int i = 0; i < files.size(); i++) {
            try(BufferedReader reader = Files.newBufferedReader(files.get(i).toPath())) {
               reader.lines().forEach(writer::println);
            }
         }
      }

      return true;
   }

   /**
    * Make sure the blocked files are in asc order.
    */
   private List<File> sortedCacheBlocks(File directory) {
      if (!directory.isDirectory()) {
         throw new IllegalArgumentException("Provided file is not a directory");
      }

      File[] files = directory.listFiles();

      if (files == null) {
         throw new RuntimeException("Failed to list directory files");
      }

      return Arrays.stream(files)
         .filter(File::isFile)
         .sorted(Comparator.comparingInt(file -> {
            try {
               return Integer.parseInt(file.getName());
            }
            catch(NumberFormatException e) {
               return Integer.MAX_VALUE;
            }
         }))
         .collect(Collectors.toList());
   }

   /**
    * Get the cache folder for current worksheet.
    */
   private static String getWorksheetCacheFolder(String wsIdenfifier) {
      AssetEntry entry = AssetEntry.createAssetEntry(wsIdenfifier);

      if(!Tool.isEmptyString(entry.getOrgID())) {
         return Tool.buildString(getOrganizationCacheFolder(entry.getOrgID()),
                                 File.separator, bytesToHex(wsIdenfifier.getBytes()));
      }

      return bytesToHex(wsIdenfifier.getBytes());
   }

   private static String getOrganizationCacheFolder(String orgID) {
      return orgID;
   }

   /**
    * Get the cache folder for emebdded table.
    */
   private String getAssemblyCacheFolder() {
      String wsIdentifier = assembly.getWorksheet().getWsID();
      String tableName = assembly.getAbsoluteName();
      tableName = bytesToHex(tableName.getBytes());


      return Tool.buildString(getWorksheetCacheFolder(wsIdentifier), File.separator, tableName);
   }

   private static String bytesToHex(byte[] bytes) {
      StringBuilder sb = new StringBuilder();

      for(byte b : bytes) {
         sb.append(String.format("%02x", b));
      }

      return sb.toString();
   }

   /**
    * Write block cache file.
    */
   private void writeToCacheAtomically(Path targetPath, byte[] data) throws IOException {
      Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
      getCacheFile(tempPath, false);

      try (BufferedWriter writer = Files.newBufferedWriter(tempPath,
                                                           StandardOpenOption.CREATE,
                                                           StandardOpenOption.TRUNCATE_EXISTING))
      {
         writer.write("<embeddedDatas><![CDATA[");
         writer.newLine();
         writer.write(new String(data));
         writer.newLine();
         writer.write("]]></embeddedDatas>");
      }

      Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
   }

   private Path getCacheFilePath(String folder, int blockIndex) {
      return getCacheFilePath(Tool.buildString(folder, File.separator, blockIndex));
   }

   /**
    * Get path in the file system cache.
    */
   private static Path getCacheFilePath(String path) {
      try {
         String dir = FileSystemService.getInstance().getCacheDirectory();
         dir = Tool.buildString(dir, File.separator, EMBEDDED_CACHE_FOLDER);

         return Paths.get(dir, path);
      }
      catch(IOException e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Get or create cache file.
    */
   public File getCacheFile(Path tempFile, boolean folder) {
      try {
         Path parent = tempFile.getParent();

         if(parent != null && !Files.exists(parent)) {
            getCacheFile(parent, true);
         }

         if(Files.exists(tempFile)) {
            return tempFile.toFile();
         }

         if(folder) {
            Files.createDirectory(tempFile);
         }
         else {
            Files.createFile(tempFile);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to create temp file: " + tempFile.getFileName(), ex);
         return null;
      }

      return tempFile.toFile();
   }

   /**
    * Remove worksheet caches when deleting/renaming ws.
    */
   public static void clearWSCache(String identifier) {
      if(Tool.isEmptyString(identifier)) {
         return;
      }

      clearOrgCache(getWorksheetCacheFolder(identifier));
   }

   /**
    * Remove organization caches when deleting/renaming organization id.
    */
   public static void clearOrgCache(String orgID) {
      if(Tool.isEmptyString(orgID)) {
         return;
      }

      removeCache(getOrganizationCacheFolder(orgID));
   }

   private static void removeCache(String folder) {
      Path path = getCacheFilePath(folder);

      if(path.toFile().exists()) {
         try {
            FileSystemService.getInstance().deleteFile(path.toFile().getAbsolutePath());
         }
         catch(IOException e) {
            throw new RuntimeException(e);
         }
      }
   }

   private EmbeddedTableAssembly assembly;
   private static final String EMBEDDED_CACHE_FOLDER = "EmbeddedData";
   private static final Logger LOG = LoggerFactory.getLogger(EmbeddedDataCacheHandler.class);
}
