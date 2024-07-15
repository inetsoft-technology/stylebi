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
package inetsoft.mv.data;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.*;
import inetsoft.util.SingletonManager;
import inetsoft.util.Tool;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.channels.SeekableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MVStorage implements AutoCloseable {
   public MVStorage() {
      if(getStorage() == null) {
         throw new RuntimeException("Failed to create MV definition storage");
      }
   }

   public static MVStorage getInstance() {
      return SingletonManager.getInstance(MVStorage.class);
   }

   public static String getFile(String name) {
      return Tool.getUniqueName(name, 61) + ".mv";
   }

   public String getNameForDef(String defname) {
      String f = getFile(defname);
      return getNameFromFile(f);
   }

   String getNameFromFile(String f) {
      return f.substring(0, f.length() - 3); // .mv
   }

   public List<String> list() {
      return getStorage().stream()
         .map(Blob::getPath)
         .map(this::getNameFromFile)
         .collect(Collectors.toList());
   }

   public List<String> listFiles() {
      return getStorage().stream()
         .map(Blob::getPath)
         .collect(Collectors.toList());
   }

   public boolean exists(String name) {
      return getStorage().exists(name);
   }

   public MV get(String name) throws IOException {
      MV mv = cache.get(name);

      if(mv == null) {
         lock.lock();

         try {
            mv = cache.get(name);

            if(mv == null) {
               mv = load(name);
               cache.put(name, mv);
            }
         }
         finally {
            lock.unlock();
         }
      }

      if(!mv.isValid()) {
         lock.lock();

         try {
            cache.remove(name);
         }
         finally {
            lock.unlock();
         }

         mv = get(name);
      }

      return mv;
   }

   public long getLastModified(String name) {
      try {
         return getStorage().getLastModified(name).toEpochMilli();
      }
      catch(Exception e) {
         return 0L;
      }
   }

   public long getLength(String name) {
      try {
         return getStorage().getLength(name);
      }
      catch(FileNotFoundException e) {
         return 0L;
      }
   }

   public void put(String name, MV mv) throws IOException {
      try(BlobTransaction<Metadata> tx = getStorage().beginTransaction();
          BlobChannel channel = tx.newChannel(name, new Metadata()))
      {
         mv.save(channel);
         tx.commit();
      }
   }

   public void remove(String name) throws IOException {
      getStorage().delete(name);
      invalidate(name);
   }

   public void invalidate(String name) {
      lock.lock();

      try {
         cache.remove(name);
      }
      finally {
         lock.unlock();
      }
   }

   public void rename(String oname, String nname) throws IOException {
      getStorage().rename(oname, nname);
      lock.lock();

      try {
         invalidate(oname);
         invalidate(nname);
      }
      finally {
         lock.unlock();
      }
   }

   public void copy(String oname, String nname) throws IOException {
      BlobStorage<Metadata> storage = getStorage();

      try(InputStream input = storage.getInputStream(oname);
          BlobTransaction<Metadata> tx = storage.beginTransaction();
          OutputStream output = tx.newStream(nname, new Metadata()))
      {
         IOUtils.copy(input, output);
         tx.commit();
      }

      invalidate(nname);
   }

   BlobChannel openReadChannel(String name) throws IOException {
      return getStorage().getReadChannel(name);
   }

   BlobTransaction<Metadata> beginTransaction() {
      return getStorage().beginTransaction();
   }

   ChannelProvider createChannelProvider(String name) {
      return new BlobChannelProvider(name, getStorage(), n -> new Metadata());
   }

   @Override
   public void close() throws Exception {
      getStorage().close();
   }

   private MV load(String name) throws IOException {
      BlobStorage<Metadata> storage = getStorage();
      MV mv = new MV(name, storage.getLastModified(name).toEpochMilli());

      try(BlobChannel channel = storage.getReadChannel(name)) {
         mv.load(channel);
      }

      return mv;
   }

   private BlobStorage<Metadata> getStorage() {
      String storeID = OrganizationManager.getInstance().getCurrentOrgID() + "__" + "mv";
      return SingletonManager.getInstance(BlobStorage.class, storeID, true);
   }

   private final Map<String, MV> cache = new ConcurrentHashMap<>();
   private final Lock lock = new ReentrantLock();

   public static final class Metadata implements Serializable {
   }

   private static final class BlobChannelProvider implements ChannelProvider {
      public BlobChannelProvider(String name, BlobStorage<Metadata> storage,
                                 Function<String, Metadata> producer)
      {
         this.name = name;
         this.producer = producer;
         this.storage = storage;
      }

      @Override
      public String getName() {
         return name;
      }

      @Override
      public boolean exists() {
         return storage.exists(name);
      }

      @Override
      public SeekableByteChannel newReadChannel() throws IOException {
         return storage.getReadChannel(name);
      }

      @Override
      public SeekableByteChannel newWriteChannel() throws IOException {
         BlobTransaction<Metadata> tx = storage.beginTransaction();

         try {
            BlobChannel channel = tx.newChannel(name, producer.apply(name));
            return new DelegatingChannel(channel) {
               @Override
               public void close() throws IOException {
                  try {
                     tx.commit();
                     super.close();
                  }
                  finally {
                     tx.close();
                  }
               }
            };
         }
         catch(IOException e) {
            tx.close();
            throw e;
         }
      }

      private final String name;
      private final Function<String, Metadata> producer;
      private final BlobStorage<Metadata> storage;
   }
}
