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
package inetsoft.util;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.uql.DriverCache;
import inetsoft.util.swap.XSwapper;
import inetsoft.web.service.LocalizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Place holder service which should be used for any File System access and manipulation.
 *
 * @version 13.1
 * @author InetSoft Technology Corp
 */
@SingletonManager.ShutdownOrder(after = IgniteCluster.class)
public class FileSystemService {
   public static FileSystemService getInstance() {
      return SingletonManager.getInstance(FileSystemService.class);
   }

   /**
    * Retrieves the specified file from the File System.
    *
    * @param fileName Full path of the File to retrieve from the File System.
    *
    * @param more Additional files to get from the full path.
    *
    * @return the Path representation of the File.
    *
    * @throws InvalidPathException if the fileName contains an invalid character.
    */
   public Path getPath(String fileName, String... more) throws InvalidPathException {
      return Paths.get(fileName, more);
   }

   /**
    * Retrieves the specified file from the File System.
    *
    * @param fileName Full path of the File to retrieve from the File System.
    *
    * @return the Path representation of the File.
    *
    * @throws InvalidPathException if the fileName contains an invalid character.
    */
   public File getFile(String fileName) throws InvalidPathException {
      return getPath(fileName).toFile();
   }

   /**
    * Retrieves the specified file from the File System given the parent directory.
    *
    * @param parentFile The parent directory from which to retrieve the file from.
    *
    * @param fileName File name of the File to retrieve from the File System.
    *
    * @return the File representation of the File.
    *
    * @throws InvalidPathException if the fileName contains an invalid character.
    */
   public File getFile(File parentFile, String fileName) throws InvalidPathException {
      return getFile(parentFile.getAbsolutePath(), fileName);
   }

   /**
    * Retrieves the specified file from the File System given the parent directory.
    *
    * @param parent The parent directory from which to retrieve the file from.
    *
    * @param fileName File name of the File to retrieve from the File System.
    *
    * @return the File representation of the File.
    *
    * @throws InvalidPathException if the fileName contains an invalid character.
    */
   public File getFile(String parent, String fileName) throws InvalidPathException {
      return getPath(parent).resolve(fileName).toFile();
   }

   /**
    * Attempts to create a temp file which does not already exist fo the specified filename.
    *
    * @param parent Full path of the File to retrieve from the File System.
    *
    * @param fileName The name of the file.
    *
    * @param suffix The suffix of the file.
    *
    * @param separator A separator character between the index and filename.
    *
    * @return  The file representation of the indexed entry.
    *
    * @throws InvalidPathException if the fileName contains an invalid character.
    */
   public File getIndexedFile(String parent, String fileName, String suffix, String separator)
      throws InvalidPathException
   {
      Path filePath = getPath(parent).resolve(fileName + "." + suffix);

      for(int i = 0; Files.exists(filePath); i++ ) {
          filePath = getPath(parent).resolve(fileName + separator + (i + 1) + "." +suffix);
      }

      return filePath.toFile();
   }

   /**
    * Creates the specified file from the File System.
    *
    * @param fileName Full path of the file to retrieve from the File System.
    *
    * @return the Path representation of the File.
    *
    * @throws IOException if an I/O error occurs or the file already exists on the system.
    */
   public Path createPath(String fileName) throws IOException {
      Path p = getPath(fileName);

      try {
         Files.createFile(p);
      }
      catch (FileAlreadyExistsException faException) {
         LOG.warn("Attempted to create file, but it already exists: {} ", fileName);
      }

      return p;
   }

   /**
    * Recursively delete the specific file/directory from the file system.
    *
    * @param fileName Full path of the file/directory to recursively delete.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void deleteFile(String fileName) throws IOException {
      Path rootPath = getPath(fileName);

      try(Stream<Path> pathStream = Files.walk(rootPath)) {
         List<Path> paths = pathStream
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());

         for(Path path : paths) {
            try {
               Files.delete(path);
            }
            catch(IOException e) {
               if(LOG.isDebugEnabled()) {
                  LOG.warn("Failed to delete file {}", path, e);
               }
               else {
                  LOG.warn("Failed to delete file {}", path);
               }
            }
         }
      }
   }

   /**
    * Delete a specific file from the file system.
    *
    * @param file File to delete.
    *
    */
   public void deleteFile(File file) {
      if(file == null) {
         return;
      }

      Path path = file.toPath();

      try {
         Files.delete(path);
      }
      catch(IOException e) {
         if(LOG.isDebugEnabled()) {
            LOG.warn("Failed to delete file {}", path, e);
         }
         else {
            LOG.warn("Failed to delete file {}", path);
         }
      }
   }

   /**
    * Set the last modified time of a specific file from the file system.
    *
    * @param file File to set modified time.
    * @param modifiedTime The modified time.
    *
    */
   public void setLastModifiedTime(File file, long modifiedTime) {
      if(file == null) {
         return;
      }

      Path path = file.toPath();

      try {
         Files.setLastModifiedTime(path, FileTime.fromMillis(modifiedTime));
      }
      catch(IOException e) {
         if(LOG.isDebugEnabled()) {
            LOG.warn("Failed to delete file {}", path, e);
         }
         else {
            LOG.warn("Failed to delete file {}", path);
         }
      }
   }

   /**
    * Place holder replacement method for Tool.getCacheDirectory().
    *
    * @return The string representation of the cache directory.
    *
    * @throws IOException if an I/O error occurs.
    */
   public String getCacheDirectory() throws IOException {
      String cdir = SreeEnv.getProperty("replet.cache.directory");

      // for spark nodes
      if(cdir == null) {
         cdir = System.getenv("SREE_CACHE_DIR");
      }

      // optimization
      if(CACHE != null && Tool.equals(SREE_CACHE, cdir)) {
         return CACHE;
      }

      SREE_CACHE = cdir;

      if(cdir == null) {
         String server = SreeEnv.getProperty("server.type");

         // if running in a cluster, defaults to the local temp directory
         // to avoid network/disk contention
         if("server_cluster".equals(SreeEnv.getProperty("server.type"))) {
            cdir = System.getProperty("java.io.tmpdir");
         }
         else {
            cdir = SreeEnv.getProperty("sree.home");
         }

         if(cdir == null || cdir.equals(".")) {
            cdir = System.getProperty("java.io.tmpdir");
         }

         cdir = cdir + "/cache";
      }

      Path cachePath = getPath(cdir);

      if(Files.notExists(cachePath)) {
         try {
            Files.createDirectories(cachePath);
         }
         catch(Exception ex) {
            LOG.warn("Cache directory does not exist, and can't be created: {}", cdir);
            // Otherwise, try java's tmpdir
            cdir = System.getProperty("java.io.tmpdir");

            cachePath = getPath(cdir);

            if(Files.notExists(cachePath)) {
               try {
                  Files.createDirectories(cachePath);
               }
               catch(Exception ex2) {
                  LOG.warn("Cache directory does not exist, and can't be created: {}", cdir);
                  throw ex2;
               }
            }
         }
      }

      LOG.info("Cache directory for temporary files: {}", cdir);
      CACHE = cdir;
      return cdir;
   }

   /**
    * Retrieve the cache directory as a File object.
    *
    * @return The cache directory.
    */
   public File getCacheFolder() throws IOException {
      return getPath(getCacheDirectory()).toFile();
   }

   /**
    * The new mechanism which should be used to retrieve and create cache files and directories.
    *
    * @param fileName Full path of the file/directory to create or retrieve from the File System.
    *
    * @return The cache file.
    */
   public File getCacheFile(String fileName) {
      try {
         Path cacheFilePath = getPath(getCacheDirectory()).resolve(fileName);
         return cacheFilePath.toFile();
      }
      catch(IOException e) {
         LOG.info("Unable to retrieve or create cache file: {}", fileName );
      }

      return null;
   }

   /**
    * Create a temporary file in the cache directory.
    */
   public File getCacheTempFile(String prefix, String suffix) {
      try {
         String cdir = getCacheDirectory();
         long findex = System.currentTimeMillis();
         prefix = Tool.toFileName(prefix);
         Path tempFile = getPath(cdir).resolve(prefix + findex++ + "." + suffix);

         while(true) {
            try {
               Files.createFile(tempFile);

               if(Files.exists(tempFile)) {
                  break;
               }
            }
            catch(FileAlreadyExistsException aeException) {
               //If file already exists, retry with modified suffix.
            }
            catch(IOException ex) {
               // this should not happen and we should terminate here
               // otherwise it may stuck in an infinite loop
               LOG.error("Creating temp file caused IO Error: " + tempFile.getFileName(), ex);
               return null;
            }
            catch(Exception ex) {
               LOG.error("Failed to create temp file: " + tempFile.getFileName(), ex);
               return null;
            }

            tempFile = getPath(cdir).resolve(prefix + findex++ + "." + suffix);
         }

         return tempFile.toFile();
      }
      catch (IOException ioException) {
         LOG.error("Creating temp file caused IO Error ", ioException);
         return null;
      }
   }

   public File createTempDirectory() {
      try {
         final Path cdir = getPath(getCacheDirectory());
         return Files.createTempDirectory(cdir, null).toFile();
      }
      catch(IOException ex) {
         LOG.error("Failed to create temp dir", ex);
         return null;
      }
   }

   public boolean rename(File ffile, File tfile) {
      int max = 100;

      while(tfile.exists() && !tfile.delete()) {
         LOG.debug("Waiting to delete file \"{}\".", tfile);
         max--;

         if(max < 0) {
            throw new RuntimeException("Failed to remove file \"" + tfile + "\".");
         }

         try {
            Thread.sleep(100);
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      max = 100;

      while(ffile.exists() && !ffile.renameTo(tfile)) {
         LOG.debug("Waiting to rename file \"" +
                      ffile + "\" to \"" + tfile + "\".");
         max--;

         if(max < 0) {
            throw new RuntimeException(
               "Failed to rename file \"" + ffile + "\" to \"" + tfile + "\".");
         }

         try {
            Thread.sleep(100);
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      return !ffile.exists() && tfile.exists();
   }

   /**
    * Remove file in a period.
    *
    * @param file the specified file to be removed.
    *
    * @param period the specified time period.
    *
    */
   public synchronized void remove(File file, int period) {
      if(file == null || !file.exists()) {
         return;
      }

      FileEntry entry = new FileEntry(file, period);
      int pos = Collections.binarySearch(list, entry);
      pos = pos >= 0 ? pos : -pos - 1;
      list.add(pos, entry);

      if(list.size() == 1) {
         TimedQueue.add(runnable);
      }
   }

   /**
    * Remove removable files.
    */
   private synchronized void remove() {
      for(int i = list.size() - 1; i >= 0; i--) {
         FileEntry entry = list.get(i);

         if(!entry.isRemovable()) {
            break;
         }

         entry.remove();
         list.remove(i);
      }

      if(list.isEmpty()) {
         TimedQueue.remove(runnable);
      }
   }

   /**
    * Clear cache files.
    *
    * @param caller the caller who calls this function, one of:
    *  1: null, no caller
    *  2: designer
    *  3: viewer
    */
   public void clearCacheFiles(String caller) {
      Path filePath;
      String cacheDir;

      try {
         cacheDir = getCacheDirectory();
      }
      catch(IOException ioException) {
         LOG.error("Cache Directory is not defined, unable to clear cache files", ioException);
         return;
      }

      filePath = getPath(Tool.convertUserFileName(cacheDir));

      if(caller != null) {
         String name = Tool.PERSISTENT_PREFIX + caller + "_lock__";
         Path nf = getCacheFile(name).toPath();

         if(Files.notExists(nf)) {
            try {
               Files.createFile(nf);
            }
            catch(Exception ex) {
               // ignore it
            }
         }

         nf.toFile().deleteOnExit();

         String dname = Tool.PERSISTENT_PREFIX + "designer_lock__";
         File df = getCacheFile(dname);
         String vname = Tool.PERSISTENT_PREFIX + "viewer_lock__";
         File vf = getCacheFile(vname);

         // @by stephenwebster, For bug1422993135997
         // If any of these persistent locks gets orphaned (the server process
         // shuts down improperly), then you can get into a situation where
         // the cache never cleans up.
         // I have removed the call to this method from AdmServlet (em),
         // as the EM does not need to lock the cache. In a typical production
         // setting, customers should not be starting studio on the same
         // machine pointing to the same sree.home.  Even if they do,
         // it is less likely that studio will orphan the file.
         // If it does, we have to communicate that this is not the
         // recommended best practice.
         if(Tool.cacheLocked(name, dname, vf) || Tool.cacheLocked(name, vname, df)) {
            return;
         }
      }

      if(Files.isDirectory(filePath)) {
         final File[] files = filePath.toFile().listFiles();
         localizationService = localizationService == null ?
            new LocalizationService() : localizationService;

         // delete in background in case there is a large number of files
         (new Thread() {
            {
               setPriority(Thread.MIN_PRIORITY);
            }

            @Override
            public void run() {
               try {
                  localizationService.clearI18nCache();
               }
               catch(IOException e) {
                  if(LOG.isDebugEnabled()) {
                     LOG.warn("Failed to delete I18n cache", e);
                  }
                  else {
                     LOG.warn("Failed to delete I18n cache");
                  }
               }


               Cluster cluster = Cluster.getInstance();
               Lock lock = cluster.getLock(XSwapper.SWAP_FILE_MAP_LOCK);
               lock.lock();

               try {
                  Map<String, Integer> map = cluster.getMap(XSwapper.SWAP_FILE_MAP);

                  for(int i = 0; files != null && i < files.length; i++) {
                     if(!files[i].isDirectory() &&
                        !files[i].getName().startsWith(Tool.PERSISTENT_PREFIX) &&
                        !files[i].getName().startsWith(DriverCache.DRIVER_CACHE_FILE_NAME) &&
                        !map.containsKey(files[i].getAbsolutePath()))
                     {
                        Path path = files[i].toPath();

                        try {
                           if(Files.exists(path)) {
                              Files.delete(path);
                           }
                        }
                        catch(IOException e) {
                           if(LOG.isDebugEnabled()) {
                              LOG.warn("Failed to delete cache file {}", path, e);
                           }
                           else {
                              LOG.warn("Failed to delete cache file {}", path);
                           }
                        }
                     }
                  }
               }
               finally {
                  lock.unlock();
               }
            }
         }).start();
      }
   }

   public boolean isCacheFile(File file) {
      if(file != null) {
         try {
            if(file.toPath().startsWith(getPath(getCacheDirectory()))) {
               return true;
            }
         }
         catch(IOException e) {
            return false;
         }
      }

      return false;
   }

   /**
    * File entry stores file and the moment to remove the file.
    */
   private static class FileEntry implements Comparable<FileEntry> {
      public FileEntry(File file, int period) {
         super();

         this.file = file;
         this.ts = System.currentTimeMillis() + period;
      }

      public boolean isRemovable() {
         return System.currentTimeMillis() >= ts;
      }

      public void remove() {
         try {
            if(file.exists()) {
               boolean removed = file.delete();

               if(!removed) {
                  LOG.info("Fail to remove file in FileTool: " +
                              file.getAbsolutePath());
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Error removing file in FileTool: " +
                         file, ex);
         }
      }

      @Override
      public int compareTo(FileEntry entry) {
         long val = ts - entry.ts;
         return val == 0 ? 0 : (val < 0 ? 1 : -1);
      }

      public String toString() {
         return "FileEntry[" + ts + ", " + file.getAbsolutePath() + "]";
      }

      private File file;
      private long ts;
   }

   /**
    * Runnable to remove removable files.
    */
   private TimedQueue.TimedRunnable runnable =
      new TimedQueue.TimedRunnable(10000) {
         @Override
         public void run() {
            remove();
         }

         @Override
         public boolean isRecurring() {
            return true;
         }
      };

   private List<FileEntry> list = new ArrayList<>();
   private String SREE_CACHE = null;
   private String CACHE = null;
   private LocalizationService localizationService;

   private static final Logger LOG =
      LoggerFactory.getLogger(FileSystemService.class);
}
