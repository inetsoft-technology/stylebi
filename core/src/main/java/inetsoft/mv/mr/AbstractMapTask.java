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
package inetsoft.mv.mr;

import inetsoft.mv.comm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * AbstractMapTask, implements common APIs of XMapTask.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public abstract class AbstractMapTask implements XMapTask {
   /**
    * Create an instance of AbstractMapTask.
    */
   public AbstractMapTask() {
      super();
   }

   /**
    * Get the id of this job.
    */
   @Override
   public final String getID() {
      return id;
   }

   /**
    * Set the job id.
    */
   @Override
   public final void setID(String id) {
      this.id = id;
   }

   /**
    * Get the value for the given key.
    */
   @Override
   public final String getProperty(String key) {
      return props.getProperty(key);
   }

   /**
    * Set the key-value pair.
    */
   @Override
   public void setProperty(String key, String val) {
      if(val == null) {
         props.remove(key);
      }
      else {
         props.setProperty(key, val);
      }
   }

   /**
    * Get the XTransferable for the given name.
    */
   @Override
   public final XTransferable get(String name) {
      return map.get(name);
   }

   /**
    * Set the XTransferable key-value pair.
    */
   @Override
   public final void set(String name, XTransferable obj) {
      if(obj == null) {
         map.remove(name);
      }
      else {
         map.put(name, obj);
      }
   }

   /**
    * Get the id of the XBlock to be accessed.
    */
   @Override
   public final String getXBlock() {
      return bid;
   }

   /**
    * Set the id of the XBlock to be accessed.
    */
   @Override
   public final void setXBlock(String bid) {
      this.bid = bid;
   }

   /**
    * Get the data node to execute this map task.
    */
   @Override
   public final String getHost() {
      return host;
   }

   /**
    * Set the data node to execute this map task.
    */
   @Override
   public final void setHost(String host) {
      this.host = host;
   }

   /**
    * Cancel the task.
    */
   @Override
   public void cancel() {
      cancelled = true;
   }
   
   /**
    * Check if the task is cancelled.
    */
   @Override
   public boolean isCancelled() {
      return cancelled;
   }
   
   /**
    * Check if the task is cancelled.

   /**
    * Read this transferable.
    */
   @Override
   public void read(XReadBuffer buf) throws IOException {
      id = buf.readString();
      bid = buf.readString();
      host = buf.readString();

      // read properties
      int cnt = buf.readShort();

      for(int i = 0; i < cnt; i++) {
         String key = buf.readString();
         String val = buf.readString();
         props.setProperty(key, val);
      }

      // read transferables
      cnt = buf.readShort();

      try {
         for(int i = 0; i < cnt; i++) {
            String key = buf.readString();
            String cname = buf.readString();
            Class cls = CommService.getClass(cname);
            XTransferable obj = (XTransferable) cls.newInstance();
            obj.read(buf);
            map.put(key, obj);
         }
      }
      catch(IllegalAccessException | ClassNotFoundException | InstantiationException ex) {
         LOG.error(ex.getMessage(), ex);
         throw new IOException(ex.getMessage());
      }
   }

   /**
    * Write this transferable.
    */
   @Override
   public void write(XWriteBuffer buf) throws IOException {
      buf.writeString(id);
      buf.writeString(bid);
      buf.writeString(host);

      // write properties
      int cnt = props.size();
      buf.writeShort((short) cnt);
      Iterator keys = props.keySet().iterator();

      while(keys.hasNext()) {
         String key = (String) keys.next();
         String val = props.getProperty(key);
         buf.writeString(key);
         buf.writeString(val);
      }

      // write transferables
      cnt = map.size();
      buf.writeShort((short) cnt);
      keys = map.keySet().iterator();

      while(keys.hasNext()) {
         String key = (String) keys.next();
         XTransferable val = map.get(key);
         buf.writeString(key);
         String cls = CommService.getClassName(val.getClass());
         buf.writeString(cls);
         val.write(buf);
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      String cls = getClass().getName();
      int index = cls.lastIndexOf(".");
      cls = index < 0 ? cls : cls.substring(index + 1);
      return cls + '-' + bid + "<id:" + id + ",host:"+ host + ",prop:"+ props +
         ",map:" + map + '>';
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractMapTask.class);
   private final Map<String, XTransferable> map = new HashMap<>();
   private final Properties props = new Properties();
   private String id;
   private String bid;
   private String host;
   private boolean cancelled;
}
