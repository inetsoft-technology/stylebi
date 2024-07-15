/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import inetsoft.storage.FileLock;
import inetsoft.util.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;

public class DriverCache {

   public DriverCache() {
      cacheFile = FileSystemService.getInstance().getCacheFile(DRIVER_CACHE_FILE_NAME).toPath();
   }

   public Set<String> getDrivers(String file, long timestamp) {
      loadCache();
      return drivers.get(new Key(file, timestamp));
   }

   public synchronized void putDrivers(String file, long timestamp, Set<String> drivers) {

      try {
         FileLock lock = getCacheLock();
         lock.lock();

         try {
            this.drivers.put(new Key(file, timestamp), drivers);
            writeCache();
         }
         finally {
            lock.unlock();
         }
      }
      catch(Exception e) {
         log("Failed to write driver cache", e);
      }
   }

   public static void setLogToStandardError(boolean noLog) {
      DriverCache.noLog.set(noLog);
   }

   private synchronized void loadCache() {
      try {
         FileLock lock = getCacheLock();
         lock.lock();

         try {
            if(Files.exists(cacheFile)) {
               FileTime currentTimestamp = Files.getLastModifiedTime(cacheFile);

               if(cacheTimestamp == null || cacheTimestamp.compareTo(currentTimestamp) < 0) {
                  JsonFactory factory = new JsonFactory();
                  Map<Key, Set<String>> loaded = new HashMap<>();

                  try(JsonParser parser = factory.createParser(cacheFile.toFile())) {
                     while(parser.nextToken() != null) {
                        if(parser.getCurrentToken() != JsonToken.START_OBJECT) {
                           throw new IOException(
                              "Malformed driver cache file, expected START_OBJECT, but got " +
                              parser.getCurrentToken() + " at " + parser.getCurrentLocation());
                        }

                        if(parser.nextToken() != JsonToken.FIELD_NAME ||
                           !"jars".equals(parser.getCurrentName()))
                        {
                           throw new IOException(
                              "Malformed driver cache file, expected field jars, but got " +
                              parser.getCurrentName() + " at " + parser.getCurrentLocation());
                        }

                        if(parser.nextToken() != JsonToken.START_ARRAY) {
                           throw new IOException(
                              "Malformed driver cache file, expected START_ARRAY, but got " +
                              parser.getCurrentToken() + " at " + parser.getCurrentLocation());
                        }

                        while(parser.nextToken() != JsonToken.END_ARRAY) {
                           if(parser.getCurrentToken() != JsonToken.START_OBJECT) {
                              throw new IOException(
                                 "Malformed driver cache file, expected START_OBJECT, but got " +
                                 parser.getCurrentToken() + " at " + parser.getCurrentLocation());
                           }

                           String file = null;
                           Long timestamp = null;
                           Set<String> fileDrivers = null;

                           while(parser.nextToken() != JsonToken.END_OBJECT) {
                              if(parser.getCurrentToken() == JsonToken.FIELD_NAME) {
                                 String fieldName = parser.getCurrentName();

                                 if("file".equals(fieldName)) {
                                    if(parser.nextToken() != JsonToken.VALUE_STRING) {
                                       throw new IOException(
                                          "Malformed driver cache file, expected VALUE_STRING, but got " +
                                          parser.getCurrentToken() + " at " +
                                          parser.getCurrentLocation());
                                    }

                                    file = parser.getValueAsString();
                                 }
                                 else if("timestamp".equals(fieldName)) {
                                    if(parser.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                                       throw new IOException(
                                          "Malformed driver cache file, expected VALUE_NUMBER_INT, but got " +
                                          parser.getCurrentToken() + " at " +
                                          parser.getCurrentLocation());
                                    }

                                    timestamp = parser.getValueAsLong();
                                 }
                                 else if("drivers".equals(fieldName)) {
                                    if(parser.nextToken() != JsonToken.START_ARRAY) {
                                       throw new IOException(
                                          "Malformed driver cache file, expected START_ARRAY, but got " +
                                          parser.getCurrentToken() + " at " +
                                          parser.getCurrentLocation());
                                    }

                                    fileDrivers = new HashSet<>();

                                    while(parser.nextToken() != JsonToken.END_ARRAY) {
                                       if(parser.getCurrentToken() != JsonToken.VALUE_STRING) {
                                          throw new IOException(
                                             "Malformed driver cache file, expected VALUE_STRING, but got " +
                                             parser.getCurrentToken() + " at " +
                                             parser.getCurrentLocation());
                                       }

                                       fileDrivers.add(parser.getValueAsString());
                                    }
                                 }
                                 else {
                                    throw new IOException(
                                       "Malformed driver cache file, expected field file, timestamp, " +
                                       "or drivers, but got " + parser.getCurrentName() + " at " +
                                       parser.getCurrentLocation());
                                 }
                              }
                              else {
                                 throw new IOException(
                                    "Malformed driver cache file, expected field jars, but got " +
                                    parser.getCurrentName() + " at " + parser.getCurrentLocation());
                              }
                           }

                           if(file == null) {
                              throw new IOException(
                                 "Malformed driver cache file, missing required field file at " +
                                 parser.getCurrentLocation());
                           }

                           if(timestamp == null) {
                              throw new IOException(
                                 "Malformed driver cache file, missing required field timestamp at " +
                                 parser.getCurrentLocation());
                           }

                           if(fileDrivers == null) {
                              throw new IOException(
                                 "Malformed driver cache file, missing required field drivers at " +
                                 parser.getCurrentLocation());
                           }

                           loaded.put(new Key(file, timestamp), fileDrivers);
                        }

                        if(parser.nextToken() != JsonToken.END_OBJECT) {
                           throw new IOException(
                              "Malformed driver cache file, expected END_OBJECT, but got " +
                              parser.getCurrentToken() + " at " + parser.getCurrentLocation());
                        }
                     }
                  }

                  cacheTimestamp = Files.getLastModifiedTime(cacheFile);
                  drivers = loaded;
               }
            }
            else {
               drivers = new HashMap<>();
               writeCache();
            }
         }
         finally {
            lock.unlock();
         }
      }
      catch(InterruptedException | IOException e) {
         log("Failed to load driver cache", e);

         if(drivers == null) {
            drivers = new HashMap<>();
         }
      }
   }

   private synchronized void writeCache() {
      try {
         JsonFactory factory = new JsonFactory();

         try(OutputStream output = Files.newOutputStream(cacheFile);
             JsonGenerator gen = factory.createGenerator(output, JsonEncoding.UTF8))
         {
            gen.setPrettyPrinter(new DefaultPrettyPrinter());
            gen.writeStartObject();
            gen.writeArrayFieldStart("jars");

            if(drivers != null) {
               for(Map.Entry<Key, Set<String>> e : drivers.entrySet()) {
                  gen.writeStartObject();
                  gen.writeStringField("file", e.getKey().file);
                  gen.writeNumberField("timestamp", e.getKey().timestamp);
                  gen.writeArrayFieldStart("drivers");

                  for(String driver : e.getValue()) {
                     gen.writeString(driver);
                  }

                  gen.writeEndArray();
                  gen.writeEndObject();
               }
            }

            gen.writeEndArray();
            gen.writeEndObject();
         }

         cacheTimestamp = Files.getLastModifiedTime(cacheFile);
      }
      catch(IOException e) {
         log("Failed to write driver cache", e);
      }
   }

   @Override
   public String toString() {
      return "DriverCache{" +
         "drivers=" + drivers +
         ", cacheFile=" + cacheFile +
         ", cacheTimestamp=" + cacheTimestamp +
         '}';
   }

   private void log(String message, Throwable e) {
      if(noLog.get()) {
         System.err.println(message);
         e.printStackTrace();//NOSONAR
      }
      else {
         LOG.error(message, e);
      }
   }

   private static FileLock getCacheLock() throws IOException {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      String dir = fileSystemService.getCacheDirectory();
      return new FileLock(fileSystemService.getPath(dir), DRIVER_CACHE_FILE_NAME + ".lock");
   }

   public static String DRIVER_CACHE_FILE_NAME = "driver-cache.json";

   private Map<Key, Set<String>> drivers;
   private final Path cacheFile;
   private FileTime cacheTimestamp;
   private static final ThreadLocal<Boolean> noLog = ThreadLocal.withInitial(() -> false);
   private static final Logger LOG = LoggerFactory.getLogger(DriverCache.class);

   private static final class Key {
      private final String file;
      private final long timestamp;

      Key(String file, long timestamp) {
         this.file = file;
         this.timestamp = timestamp;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         Key key = (Key) o;
         return timestamp == key.timestamp &&
            Objects.equals(file, key.file);
      }

      @Override
      public int hashCode() {
         return Objects.hash(file, timestamp);
      }
   }
}
