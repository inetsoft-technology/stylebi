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
package inetsoft.util;

import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

/**
 * This class checks periodically whether the cache limit has been exceeded or
 * if the disk is low on space. If so then it tries to delete old cache files.
 *
 * @version 12.0
 * @author InetSoft Technology Corp
 */
public class ClearOldCacheFilesRunnable extends TimedQueue.TimedRunnable {
   public ClearOldCacheFilesRunnable() {
      super(1800000);
   }

   @Override
   public boolean isRecurring() {
      return true;
   }

   /**
    * Returns report cache size in bytes
    */
   private long getReportCacheSize() {
      long size = 0;
      File file = FileSystemService.getInstance().getFile(
         Tool.convertUserFileName(Tool.getCacheDirectory()));

      if(file.isDirectory()) {
          HashMap<String, Long> reportCacheMapCopy = (HashMap<String, Long>)reportCacheMap.clone();
          File[] files = file.listFiles();

          for(int i = 0; files != null && i < files.length; i++) {
             if(!files[i].isDirectory()) {
                String fileName = files[i].getName();
                long fileSize = 0;

                if(reportCacheMapCopy.containsKey(fileName)) {
                   fileSize = reportCacheMapCopy.remove(fileName);
                }
                else {
                   if(fileName.matches(".*\\.\\d+")) {
                      fileSize = files[i].length();
                      reportCacheMap.put(fileName, fileSize);
                   }
                }

                size += fileSize;
             }
          }

          //remove stale entries
          for(Map.Entry<String, Long> entry : reportCacheMapCopy.entrySet()) {
             reportCacheMap.remove(entry.getKey());
          }
      }

      return size;
   }

   /**
    * Returns data cache size in bytes
    */
   private long getDataCacheSize() {
      long size = 0;
      File file = FileSystemService.getInstance().getFile(
         Tool.convertUserFileName(Tool.getCacheDirectory()));

      if(file.isDirectory()) {
          HashMap<String, Long> dataCacheMapCopy = (HashMap<String, Long>)dataCacheMap.clone();
          File[] files = file.listFiles();

          for(int i = 0; files != null && i < files.length; i++) {
             if(!files[i].isDirectory()) {
                String fileName = files[i].getName();
                long fileSize = 0;

                if(dataCacheMapCopy.containsKey(fileName)) {
                   fileSize = dataCacheMapCopy.remove(fileName);
                }
                else {
                   if(fileName.endsWith(".tdat")) {
                      fileSize = files[i].length();
                      dataCacheMap.put(fileName, fileSize);
                   }
                }

                size += fileSize;
             }
          }

          //remove stale entries
          for(Map.Entry<String, Long> entry : dataCacheMapCopy.entrySet()) {
             dataCacheMap.remove(entry.getKey());
          }
      }

      return size;
   }

   /**
    * Checks whether the disk is low on space
    */
   private boolean isLowDisk() {
      File file = FileSystemService.getInstance().getFile(
         Tool.convertUserFileName(Tool.getCacheDirectory()));
      return file.getFreeSpace() < LOW_DISK_SPACE ? true : false;
   }

   /**
    * Clears report cache files
    */
   private void clearReportCache() {
      long reportCacheLimit = Long.parseLong(SreeEnv.getProperty("report.cache.file.limit"));
      long reportCacheSize = getReportCacheSize();

      File file = FileSystemService.getInstance().getFile(
         Tool.convertUserFileName(Tool.getCacheDirectory()));

      if(file.isDirectory()) {
         File[] files = file.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
               return name.matches(".*\\.\\d+");
         }});

         Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.compare(f1.lastModified(), f2.lastModified());
            }});

         long curr = System.currentTimeMillis();

         for(int i = 0; files != null && i < files.length; i++) {
            if(files[i].isDirectory() || curr - files[i].lastModified() < REPORT_TIMEOUT) {
               continue;
            }

            long size = reportCacheMap.get(files[i].getName());

            if(size == 0) {
               size = files[i].length();
            }

            if(files[i].delete()) {
               reportCacheMap.remove(files[i].getName());
               reportCacheSize -= size;

               if((reportCacheLimit <= 0 || reportCacheSize < reportCacheLimit)
                  && !isLowDisk())
               {
                  break;
               }
            }
         }
      }
   }

   /**
    * Clears data cache files
    */
   private void clearDataCache() {
      long dataCacheLimit = Long.parseLong(SreeEnv.getProperty("data.cache.file.limit"));
      long dataCacheSize = getDataCacheSize();

      File file = FileSystemService.getInstance().getFile(
         Tool.convertUserFileName(Tool.getCacheDirectory()));

      if(file.isDirectory()) {
         File[] files = file.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
               return name.endsWith(".tdat");
         }});

         Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.compare(f1.lastModified(), f2.lastModified());
            }});

         long curr = System.currentTimeMillis();

         for(int i = 0; files != null && i < files.length; i++) {
            if(files[i].isDirectory() || curr - files[i].lastModified() < DATA_TIMEOUT) {
               continue;
            }

            long size = dataCacheMap.get(files[i].getName());

            if(size == 0) {
               size = files[i].length();
            }

            if(files[i].delete()) {
               dataCacheMap.remove(files[i].getName());
               dataCacheSize -= size;

               if((dataCacheLimit <= 0 || dataCacheSize < dataCacheLimit)
                  && !isLowDisk())
               {
                  break;
               }
            }
         }
      }
   }

   @Override
   public void run() {
      if(isLowDisk()) {
         LOG.error("Low disk space. Trying to delete existing cache files.");
         clearReportCache();
         clearDataCache();
         return;
      }

      long reportCacheLimit = Long.parseLong(SreeEnv.getProperty("report.cache.file.limit"));

      if(reportCacheLimit > 0 && getReportCacheSize() > reportCacheLimit) {
         LOG.error(
            "The report disk cache exceeds the maximum configured size " +
            "[report.cache.file.limit]: " + reportCacheLimit);
         clearReportCache();
      }

      long dataCacheLimit = Long.parseLong(SreeEnv.getProperty("data.cache.file.limit"));

      if(dataCacheLimit > 0 && getDataCacheSize() > dataCacheLimit) {
         LOG.error(
            "The data disk cache exceeds the maximum configured size " +
            "[data.cache.file.limit]: " + dataCacheLimit);
         clearDataCache();
      }
   }

   private HashMap<String, Long> reportCacheMap = new HashMap<>();
   private HashMap<String, Long> dataCacheMap = new HashMap<>();

   //Time after which it is considered safe to delete these files
   private static final long REPORT_TIMEOUT = 7200000L;
   private static final long DATA_TIMEOUT = 7200000L;
   private static final long LOW_DISK_SPACE = 5 * 1024L * 1024L * 1024L;//5GB

   private static final Logger LOG =
      LoggerFactory.getLogger(ClearOldCacheFilesRunnable.class);
}