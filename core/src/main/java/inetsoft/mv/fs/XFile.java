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
package inetsoft.mv.fs;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * XFile, the logic file map to logic file block.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class XFile implements Cloneable, XMLSerializable, Serializable {
   /**
    * Constructor.
    */
   public XFile() {
      super();
   }

   /**
    * Get the read lock.
    */
   public Lock getReadLock() {
      return lock.readLock();
   }

   /**
    * Get the write lock.
    */
   public Lock getWriteLock() {
      return lock.writeLock();
   }

   /**
    * Get the read/write lock.
    */
   public ReentrantReadWriteLock getReadWriteLock() {
      return lock;
   }

   /**
    * Constructor.
    */
   public XFile(String name, List<SBlock> blocks) {
      super();

      this.name = name;
      this.blocks = blocks;
      this.ts = System.currentTimeMillis();
   }

   /**
    * Get file name.
    */
   public String getName() {
      return name;
   }

   /**
    * Get the block by block id.
    */
   public SBlock getBlock(String bid) {
      return blocks.stream()
         .filter(b -> b.getID().equals(bid))
         .findAny()
         .orElse(null);
   }

   /**
    * Get the blocks of this file.
    */
   public List<SBlock> getBlocks() {
      return blocks;
   }

   /**
    * List the blocks.
    */
   public SBlock[] list() {
      return blocks.toArray(new SBlock[0]);
   }

   /**
    * List the blocks match the condition.
    */
   public SBlock[] list(XBlockFilter filter) {
      if(filter == null) {
         return list();
      }

      return blocks.stream()
         .filter(filter::accept)
         .toArray(SBlock[]::new);
   }

   /**
    * Get the last modified moment.
    */
   public long lastModified() {
      return ts;
   }

   /**
    * Set version.
    * @param version the specified version number.
    */
   public void setVersion(int version) {
      this.version = version;
   }

   /**
    * Get version.
    * @return version number.
    */
   public int getVersion() {
      return version;
   }

   /**
    * Get next version.
    * @return next version number.
    */
   public int getNextVersion() {
      return version + 1;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<XFile name=\"" + Tool.escape(name) +
         "\" version=\"" + version + "\" ts=\"" + ts + "\">");

      for(SBlock sblock : blocks) {
         sblock.writeXML(writer);
      }

      writer.println("</XFile>");
   }

   /**
    * Method to parse an xml segment about parameter element information.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      this.name = Tool.getAttribute(tag, "name");
      String attr;

      if((attr = Tool.getAttribute(tag, "ts")) != null) {
         this.ts = Long.parseLong(attr);
      }

      if((attr = Tool.getAttribute(tag, "version")) != null) {
         this.version = Integer.parseInt(attr);
      }

      NodeList celems = Tool.getChildNodesByTagName(tag, "XBlock");
      blocks.clear();

      for(int i = 0; i < celems.getLength(); i++) {
         Element celem = (Element) celems.item(i);
         SBlock com = new SBlock();
         com.parseXML(celem);
         blocks.add(com);
      }
   }

   /**
    * Check if equals another object.
    * @param obj the specified opject to compare.
    * @return true if equals, false otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof XFile)) {
         return false;
      }

      XFile file = (XFile) obj;
      return Tool.equals(file.name, name);
   }

   /**
    * Clone this object.
    * @return a clone of this <tt>XFile</tt> instance.
    */
   @Override
   public Object clone() {
      try {
         XFile nfile = (XFile) super.clone();
         nfile.blocks = new ArrayList<>();

         for(int i = 0; i < blocks.size(); i++) {
            nfile.blocks.add((SBlock) blocks.get(i).clone());
         }

         return nfile;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone file", ex);
      }

      return null;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString() + '-' + name + '<' + blocks + '>';
   }

   private void readObject(ObjectInputStream input) throws ClassNotFoundException, IOException {
      name = input.readUTF();
      ts = input.readLong();
      version = input.readInt();
      int blockCount = input.readInt();
      blocks = new ArrayList<>(blockCount);

      for(int i = 0; i < blockCount; i++) {
         blocks.add((SBlock) input.readObject());
      }

      lock = new ReentrantReadWriteLock();
   }

   private void writeObject(ObjectOutputStream output) throws IOException {
      output.writeUTF(name);
      output.writeLong(ts);
      output.writeInt(version);
      output.writeInt(blocks.size());

      for(SBlock block : blocks) {
         output.writeObject(block);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(XFile.class);
   private String name;
   private long ts;
   private int version;
   private List<SBlock> blocks = new ArrayList<>();
   private transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
}
