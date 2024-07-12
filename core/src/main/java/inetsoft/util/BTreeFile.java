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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * This is a generic BTree implementation.
 *
 * @version 8.0, 6/2/2005
 * @author InetSoft Technology Corp
 */
public final class BTreeFile {
   /**
    * Name of the configuration attribute "btree.page.size".
    */
   public static final String CONFIG_PAGESIZE = "btree.page.size";

   /**
    * Name of the configuration attribute "btree.key.size.max".
    */
   public static final String CONFIG_KEYSIZE_MAX = "btree.key.size.max";

   /**
    * Name of the configuration attribute "btree.descriptors.max".
    */
   public static final String CONFIG_DESCRIPTORS_MAX = "btree.descriptor.max";

   /**
    * Name of the configuration attribute "btree.dirty.size.max".
    */
   public static final String CONFIG_DIRTYSIZE_MAX = "btree.dirty.size.max";

   /**
    * Default value of the "pagesize".
    */
   public static final int DEFAULT_PAGESIZE = 4096;

   /**
    * Default value of the "maxkeysize".
    */
   public static final int DEFAULT_KEYSIZE_MAX = 256;

   /**
    * Default value of the maximum number of open random access files paged
    * can have. This number balances resources utilization and parallelism of
    * access to the paged file.
    */
   public static final int DEFAULT_DESCRIPTORS_MAX = 4;

   /**
    * The maximum number of pages that will be held in the dirty cache.
    * Once number reaches the limit, pages are flushed to disk.
    */
   public static final int DEFAULT_DIRTYSIZE_MAX = 4096;

   /**
    * Constructor.
    * @param file btree file.
    * @param config btree properties.
    */
   public BTreeFile(File file, Properties config) {
      descriptorsMax = DEFAULT_DESCRIPTORS_MAX;
      dirtysizeMax = DEFAULT_DIRTYSIZE_MAX;
      fileHeader = createFileHeader();
      cached = false;
      ronly = false;
      config(file, config);
   }

   /**
    * Check if is cached.
    * @return <tt>true</tt> if is cached, <tt>false</tt> otherwise.
    */
   public boolean isCached() {
      return cached;
   }

   /**
    * Set the cached option.
    * @param cached <tt>true</tt> if is cached, <tt>false</tt> otherwise.
    */
   public void setCached(boolean cached) {
      this.cached = cached;
   }

   /**
    * Set the read only option.
    * @param ronly <tt>true</tt> if is read only, <tt>false</tt> otherwise.
    */
   public void setReadOnly(boolean ronly) {
      this.ronly = ronly;
   }

   /**
    * Check if is read only.
    * @return <tt>true</tt> if is read only, <tt>false</tt> otherwise.
    */
   public boolean isReadOnly() {
      return ronly;
   }

   /**
    * Visit the btree.
    * @param visitor the specified btree visitor.
    */
   public synchronized void accept(Visitor visitor) throws Exception {
      // @by stephenwebster, For Bug #4403.
      // Change accept method to synchronized so that we lock
      // BTreeFile before BTreeFile.BTreeNode to prevent deadlock.
      open();
      rootNode.accept(visitor);
   }

   /**
    * Add the specified value with the specified key in this storage.
    * If the storage previously contained a value for this key, the old value is
    * replaced by the specified value.
    * @param key key with which the specified value is to be associated.
    * @param value value to be associated with the specified key.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   public synchronized boolean addRecord(Key key, Value value)
      throws IOException
   {
      if(dropped) {
         return false;
      }

      if(key == null || key.getLength() == 0) {
         return false;
      }

      if(value == null) {
         return false;
      }

      checkOpened();

      long pos = rootNode.findKey(key);
      Page p;

      if(pos >= 0) {
         p = getPage(pos);
      }
      else {
         p = getFreePage();
         rootNode.addKey(key, p.pageNum.longValue());
         fileHeader.recordCount++;
         fileHeader.dirty = true;
      }

      PageHeader ph = p.header;
      ph.status = RECORD;
      ph.dirty = true;
      writeValue(p, value);

      if(!cached) {
         flush();
      }

      return true;
   }

   /**
    * Flush dirty data to file and close.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   public synchronized boolean close() throws IOException {
      try {
         if(dropped) {
            return false;
         }

         opened = false;

         if(!cached) {
            flush();
         }

         // close descriptors
         synchronized(descriptors) {
            final int total = descriptorsCount;

            while(!descriptors.empty()) {
               closeDescriptor((RandomAccessFile) descriptors.pop());
            }

            int n = descriptorsCount;

            while(descriptorsCount > 0 && n > 0) {
               try {
                  descriptors.wait(500);
               }
               catch(InterruptedException ex) {
               }

               if(descriptors.isEmpty()) {
                  n--;
               }
               else {
                  closeDescriptor((RandomAccessFile) descriptors.pop());
               }
            }

            if(descriptorsCount > 0) {
               LOG.error(descriptorsCount + " out of " +
                       total + " files were not closed.");
            }
         }
      }
      catch(IOException ex) {
         opened = true;
         throw ex;
      }

      return true;
   }

   /**
    * Drop btree file.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   public synchronized boolean drop() throws IOException {
      if(dropped) {
         return false;
      }

      close();
      setDropped(true);

      if(file.exists()) {
         return file.delete();
      }
      else {
         return true;
      }
   }

   /**
    * Check if is dropped.
    * @return <tt>true</tt> if dropped, <tt>false</tt> otherwise.
    */
   public boolean isDropped() {
      return dropped;
   }

   /**
    * Set the dropped flag.
    * @param dropped <tt>true</tt> if dropped, <tt>false</tt> otherwise.
    */
   public void setDropped(boolean dropped) {
      this.dropped = dropped;
   }

   /**
    * Open btree file.
    * If btree file does exist, it will be created automatically.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public synchronized boolean open() throws IOException {
      return open(false);
   }

   /**
    * Open btree file.
    * If btree file does exist, it will be created automatically.
    * @param reset <tt>true</tt> to reset the file.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public synchronized boolean open(boolean reset) throws IOException {
      if(reset || !file.exists()) {
         if(!create()) {
            return false;
         }
      }

      pages.clear();
      cache.clear();
      RandomAccessFile raf = null;

      try {
         fileHeader.read();
         raf = getDescriptor();

         if(config != null) {
            String maxkeysize = config.getProperty(CONFIG_KEYSIZE_MAX);
            String descriptors_max = config.getProperty(CONFIG_DESCRIPTORS_MAX);
            String dirtysize_max = config.getProperty(CONFIG_DIRTYSIZE_MAX);

            if(maxkeysize != null) {
               fileHeader.maxKeySize = Short.parseShort(maxkeysize);
            }

            if(descriptors_max != null) {
               descriptorsMax = Integer.parseInt(descriptors_max);
            }

            if(dirtysize_max != null) {
               dirtysizeMax = Integer.parseInt(dirtysize_max);
            }
         }

         long p = fileHeader.rootPage;
         rootNode = getBTreeNode(p, null);
         opened = true;
         return true;
      }
      finally {
         putDescriptor(raf);
      }
   }

   /**
    * Flush dirty data to file.
    */
   public synchronized void flush() throws IOException {
      if(dropped) {
         return;
      }

      int error = 0;
      Collection pages;

      synchronized(dirtyLock) {
         pages = dirty.values();
         dirty = new HashMap();
      }

      Iterator i = pages.iterator();

      while(i.hasNext()) {
         Page p = (Page) i.next();

         try {
            p.flush();
         }
         catch(Exception ex) {
            error++;
         }
      }

      if(fileHeader.dirty) {
         try {
            fileHeader.write();
         }
         catch(Exception ex) {
            error++;
         }
      }

      if(error != 0) {
         throw new IOException("Error performing flush! Failed to flush " +
            error + " pages!");
      }
   }

   /**
    * Return the value to which the specified key is mapped in this storage.
    * @param key key whose associated value is to be returned.
    * @return the value to which this map maps the specified key, or
    * <tt>null</tt> if the storage contains no mapping for this key.
    */
   public synchronized Value getRecord(Key key) throws IOException {
      if(dropped) {
         return null;
      }

      if(key == null || key.getLength() == 0) {
         return null;
      }

      checkOpened();

      long pos = rootNode.findKey(key);

      if(pos >= 0) {
         Page startPage = getPage(pos);
         return readValue(startPage);
      }

      return null;
   }

   /**
    * Get the number of key-data mappings contained in this storage.
    */
   public synchronized long getRecordCount() throws IOException {
      if(dropped) {
         return 0;
      }

      checkOpened();
      return fileHeader.recordCount;
   }

   /**
    * Check if the storage containe the key or not.
    * @param key the specified key.
    * @return <tt>true</tt> if the storage containe the key,
    * <tt>false</tt> otherwise.
    */
   public synchronized boolean containsRecord(Key key) throws IOException {
      if(dropped) {
         return false;
      }

      if(key == null || key.getLength() == 0) {
         return false;
      }

      checkOpened();

      return rootNode.findKey(key) >= 0;
   }

   /**
    * Rename a key.
    * @param okey the specified old key.
    * @param nkey the specified new key.
    * @param overwrite if not overwrite and new key exists then return false
    * otherwise overwrite new key.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public synchronized boolean renameRecord(Key okey, Key nkey,
      boolean overwrite) throws IOException
   {
      if(dropped) {
         return false;
      }

      if(okey == null || okey.getLength() == 0 ||
         nkey == null || nkey.getLength() == 0)
      {
         return false;
      }

      if(okey.equals(nkey)) {
         return true;
      }

      checkOpened();

      long pos = rootNode.findKey(okey);

      if(pos >= 0) {
         if(rootNode.findKey(nkey) >= 0) {
            if(!overwrite) {
               return false;
            }
            else {
               removeRecord(nkey);
            }
         }

         Page p = getPage(pos);
         rootNode.removeKey(okey);
         rootNode.addKey(nkey, p.pageNum.longValue());
         return true;
      }

      return false;
   }

   /**
    * Remove the value for this key from this storage if present.
    * @param key key whose value is to be removed from the storage.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   public synchronized boolean removeRecord(Key key) throws IOException {
      if(dropped) {
         return false;
      }

      if(key == null || key.getLength() == 0) {
         return false;
      }

      checkOpened();

      long pos = rootNode.findKey(key);

      if(pos >= 0) {
         Page p = getPage(pos);
         rootNode.removeKey(key);
         unlinkPages(p);
         fileHeader.recordCount--;
         fileHeader.dirty = true;

         if(!cached) {
            flush();
         }

         return true;
      }

      return false;
   }

   /**
    * Config the btree file.
    */
   private void config(File file, Properties proper) {
      if(file.getParentFile() != null) {
         file.getParentFile().mkdirs();
      }

      this.file = file;
      this.config = proper;
   }

   /**
    * Add a dirty page.
    */
   private void addDirty(Page page) throws IOException {
      synchronized(dirtyLock) {
         dirty.put(page.pageNum, page);

         if(dirty.size() > dirtysizeMax) {
            flush();
         }
      }
   }

   /**
    * Make sure the btree file is open.
    */
   private void checkOpened() throws IOException {
      if(!opened && !open()) {
         LOG.error("Can't open BTree file");
      }
   }

   /**
    * Close a random access file.
    */
   private void closeDescriptor(RandomAccessFile raf) {
      if(raf != null) {
         try {
            raf.close();
         }
         catch(IOException ex) {
         }

         synchronized(descriptors) {
            descriptorsCount--;
         }
      }
   }

   /**
    * Create a default file header.
    */
   private FileHeader createFileHeader() {
      FileHeader header = new FileHeader();
      header.pageCount = 1;
      header.totalCount = 1;
      header.dirty = true;
      return header;
   }

   /**
    * Initialize the btree file.
    */
   private boolean create() throws IOException {
      if(config != null) {
         String tmp = config.getProperty(CONFIG_PAGESIZE);

         if(tmp != null) {
            fileHeader.setPageSize(Integer.parseInt(tmp));
         }
      }

      fileHeader.write();

      if(!cached) {
         flush();
      }

      long p = fileHeader.rootPage;
      rootNode = new BTreeNode(getPage(p), null, new Key[0], new long[0]);
      rootNode.ph.status = LEAF;
      rootNode.ph.dirty = true;
      rootNode.write();

      synchronized(cache) {
         cache.put(rootNode.page.pageNum, new WeakReference(rootNode));
      }

      close();
      return true;
   }

   /**
    * Create a btree node.
    */
   private BTreeNode createBTreeNode(byte status, BTreeNode parent)
      throws IOException
   {
      Page p = getFreePage();
      BTreeNode node = new BTreeNode(p, parent, new Key[0], new long[0]);
      node.ph.status = status;
      node.ph.dirty = true;

      synchronized(cache) {
         cache.put(p.pageNum, new WeakReference(node));
      }

      return node;
   }

   /**
    * Delete a key.
    */
   private static Key[] deleteArrayKey(Key[] keys, int idx) {
      Key[] newVals = new Key[keys.length - 1];

      if(idx > 0) {
         System.arraycopy(keys, 0, newVals, 0, idx);
      }

      if(idx < newVals.length) {
         System.arraycopy(keys, idx + 1, newVals, idx, newVals.length - idx);
      }

      return newVals;
   }

   /**
    * Delete a key.
    */
   private static long[] deleteArrayLong(long[] vals, int idx) {
      long[] newVals = new long[vals.length - 1];

      if(idx > 0) {
         System.arraycopy(vals, 0, newVals, 0, idx);
      }

      if(idx < newVals.length) {
         System.arraycopy(vals, idx + 1, newVals, idx, newVals.length - idx);
      }

      return newVals;
   }

   /**
    * Delete a key.
    */
   private static int[] deleteArrayInt(int[] vals, int idx) {
      int[] newVals = new int[vals.length - 1];

      if(idx > 0) {
         System.arraycopy(vals, 0, newVals, 0, idx);
      }

      if(idx < newVals.length) {
         System.arraycopy(vals, idx + 1, newVals, idx, newVals.length - idx);
      }

      return newVals;
   }

   /**
    * Delete a key.
    */
   private static short[] deleteArrayShort(short[] vals, int idx) {
      short[] newVals = new short[vals.length - 1];

      if(idx > 0) {
         System.arraycopy(vals, 0, newVals, 0, idx);
      }

      if(idx < newVals.length) {
         System.arraycopy(vals, idx + 1, newVals, idx, newVals.length - idx);
      }

      return newVals;
   }

   /**
    * Get a btree node.
    */
   private BTreeNode getBTreeNode(long page, BTreeNode parent) {
      try {
         BTreeNode node = null;

         synchronized(cache) {
            WeakReference ref = (WeakReference) cache.get(Long.valueOf(page));

            if(ref != null) {
               node = (BTreeNode) ref.get();
            }

            if(node == null) {
               node = new BTreeNode(getPage(page), parent);
            }
            else {
               node.parent = parent;
            }

            cache.put(node.page.pageNum, new WeakReference(node));
         }

         node.read();
         return node;
      }
      catch(Exception ex) {
         throw new RuntimeException("File interrupted!");
      }
   }

   /**
    * Get a free page.
    */
   private Page getFreePage() throws IOException {
      Page p = null;

      synchronized(fileHeader) {
         if(fileHeader.firstFreePage != NO_PAGE) {
            p = getPage(fileHeader.firstFreePage);
            fileHeader.firstFreePage = p.header.nextPage;

            if(fileHeader.firstFreePage == NO_PAGE) {
               fileHeader.lastFreePage = NO_PAGE;
            }

            fileHeader.dirty = true;
         }
      }

      if(p == null) {
         fileHeader.totalCount++;
         fileHeader.dirty = true;
         p = getPage(fileHeader.totalCount);
      }

      p.header.nextPage = NO_PAGE;
      p.header.status = UNUSED;
      p.header.dirty = true;
      return p;
   }

   /**
    * Get a random access file.
    */
   private RandomAccessFile getDescriptor() throws IOException {
      synchronized(descriptors) {
         if(!descriptors.empty()) {
            return (RandomAccessFile) descriptors.pop();
         }
         else {
            if(descriptorsCount < descriptorsMax) {
               RandomAccessFile raf =
                  new RandomAccessFile(file, ronly ? "r" : "rw");
               descriptorsCount++;
               return raf;
            }
            else {
               while(true) {
                  try {
                     descriptors.wait();
                     return (RandomAccessFile) descriptors.pop();
                  }
                  catch(InterruptedException ex) {
                  }
                  catch(EmptyStackException ex) {
                  }
               }
            }
         }
      }
   }

   /**
    * Get a page.
    */
   private Page getPage(long pageNum) throws IOException {
      final Long lp = Long.valueOf(pageNum);
      Page p;

      synchronized(this) {
         p = (Page) dirty.get(lp);

         if(p == null) {
            WeakReference ref = (WeakReference) pages.get(lp);

            if(ref != null) {
               p = (Page) ref.get();
            }
         }

         if(p == null) {
            p = new Page(lp);
            pages.put(p.pageNum, new WeakReference(p));
         }
      }

      p.read();
      return p;
   }

   /**
    * Insert a key.
    */
   private static Key[] insertArrayValue(Key[] keys, Key key, int idx) {
      Key[] newKeys = new Key[keys.length + 1];

      if(idx > 0) {
         System.arraycopy(keys, 0, newKeys, 0, idx);
      }

      newKeys[idx] = key;

      if(idx < keys.length) {
         System.arraycopy(keys, idx, newKeys, idx + 1, keys.length - idx);
      }

      return newKeys;
   }

   /**
    * Insert a key.
    */
   private static long[] insertArrayLong(long[] vals, long val, int idx) {
      long[] newVals = new long[vals.length + 1];

      if(idx > 0) {
         System.arraycopy(vals, 0, newVals, 0, idx);
      }

      newVals[idx] = val;

      if(idx < vals.length) {
         System.arraycopy(vals, idx, newVals, idx + 1, vals.length - idx);
      }

      return newVals;
   }

   /**
    * Put a random access file to pool.
    */
   private void putDescriptor(RandomAccessFile raf) {
      if(raf != null) {
         synchronized(descriptors) {
            descriptors.push(raf);
            descriptors.notify();
         }
      }
   }

   /**
    * Read value from page.
    */
   private Value readValue(Page page) throws IOException {
      final PageHeader sph = page.header;
      ByteArrayOutputStream bos = new ByteArrayOutputStream(sph.recordLen);
      Page p = page;

      while(true) {
         PageHeader ph = p.header;
         p.streamTo(bos);
         long nextPage = ph.nextPage;

         if(nextPage == NO_PAGE) {
            break;
         }

         p = getPage(nextPage);
      }

      return new Value(bos.toByteArray());
   }

   /**
    * Unlink pages.
    */
   private void unlinkPages(Page page) throws IOException {
      if(page.pageNum.longValue() < fileHeader.pageCount) {
         long nextPage = page.header.nextPage;
         page.header.status = DELETED;
         page.header.nextPage = NO_PAGE;
         page.header.dirty = true;
         page.write();

         if(nextPage == NO_PAGE) {
            page = null;
         }
         else {
            page = getPage(nextPage);
         }
      }

      if(page != null) {
         long firstPage = page.pageNum.longValue();

         while(page.header.nextPage != NO_PAGE) {
            page = getPage(page.header.nextPage);
         }

         long lastPage = page.pageNum.longValue();

         synchronized(fileHeader) {
            if(fileHeader.lastFreePage != NO_PAGE) {
               Page p = getPage(fileHeader.lastFreePage);
               p.header.nextPage = firstPage;
               p.header.dirty = true;
               p.write();
            }

            if(fileHeader.firstFreePage == NO_PAGE) {
               fileHeader.firstFreePage = firstPage;
            }

            fileHeader.lastFreePage = lastPage;
            fileHeader.dirty = true;
         }
      }
   }

   /**
    * Write value to page.
    */
   private void writeValue(Page page, Value value) throws IOException {
      InputStream is = value.getInputStream();
      PageHeader hdr = page.header;
      hdr.setRecordLen(value.getLength());
      page.streamFrom(is);

      while(is.available() > 0) {
         Page lpage = page;
         PageHeader lhdr = hdr;
         long np = lhdr.nextPage;

         if(np != NO_PAGE) {
            page = getPage(np);
         }
         else {
            page = getFreePage();
            lhdr.nextPage = page.pageNum.longValue();
            lhdr.dirty = true;
         }

         hdr = page.header;
         hdr.status = OVERFLOW;
         hdr.dirty = true;
         page.streamFrom(is);
         lpage.write();
      }

      long np = hdr.nextPage;

      if(np != NO_PAGE) {
         unlinkPages(getPage(np));
      }

      hdr.nextPage = NO_PAGE;
      hdr.dirty = true;
      page.write();
   }

   public static final class BTreeCorruptException extends IOException {
      public BTreeCorruptException(String message) {
         super(message);
      }
   }

   private final class PageHeader {
      public PageHeader() {
      }

      public synchronized void read(DataInput dis) throws IOException {
         status = dis.readByte();
         dirty = false;

         if(status == UNUSED) {
            return;
         }

         keyLen = dis.readShort();
         keyHash = dis.readInt();
         dataLen = dis.readInt();
         recordLen = dis.readInt();
         nextPage = dis.readLong();
         keyCount = dis.readShort();
      }

      public synchronized void write(DataOutput dos) throws IOException {
         dirty = false;
         dos.writeByte(status);
         dos.writeShort(keyLen);
         dos.writeInt(keyHash);
         dos.writeInt(dataLen);
         dos.writeInt(recordLen);
         dos.writeLong(nextPage);
         dos.writeShort(keyCount);
      }

      public synchronized void setKey(Key key) {
         setRecordLen(0);
         dataLen = 0;
         keyHash = key.hashCode();
         keyLen = (short) key.getLength();
         dirty = true;
      }

      public synchronized void setRecordLen(int recordLen) {
         fileHeader.totalBytes = fileHeader.totalBytes - this.recordLen +
            recordLen;
         this.recordLen = recordLen;
      }

      public synchronized short getPointerCount() {
         if(status == BRANCH) {
            return (short) (keyCount + 1);
         }
         else {
            return keyCount;
         }
      }

      private boolean dirty;
      private byte status = UNUSED;
      private short keyLen;
      private int keyHash;
      private int dataLen;
      private int recordLen;
      private long nextPage = -1;
      private short keyCount = 0;
   }

   private final class BTreeNode {
      public BTreeNode(Page page) {
         this(page, null);
      }

      public BTreeNode(Page page, BTreeNode parent) {
         this.page = page;
         this.parent = parent;
         this.ph = page.header;
      }

      public BTreeNode(Page page, BTreeNode parent, Key[] keys, long[] ptrs) {
         this(page, parent);
         set(keys, ptrs);
         this.loaded = true;
      }

      public void set(Key[] keys, long[] ptrs) {
         this.keys = keys;
         this.ph.keyCount = (short) keys.length;
         this.ph.dirty = true;
         this.ptrs = ptrs;
      }

      public synchronized void read() throws IOException {
         if(!this.loaded) {
            Value v = readValue(page);
            DataInputStream is = new DataInputStream(v.getInputStream());
            keys = new Key[ph.keyCount];

            for(int i = 0; i < keys.length; i++) {
               short valSize = is.readShort();
               byte[] b = new byte[valSize];
               is.read(b);
               keys[i] = new Key(b);
            }

            ptrs = new long[ph.getPointerCount()];

            for(int i = 0; i < ptrs.length; i++) {
               ptrs[i] = is.readLong();
            }

            this.loaded = true;
         }
      }

      public synchronized void write() throws IOException {
         ByteArrayOutputStream bos = new ByteArrayOutputStream(
            fileHeader.workSize);
         DataOutputStream os = new DataOutputStream(bos);

         for(int i = 0; i < keys.length; i++) {
            os.writeShort(keys[i].getLength());
            keys[i].streamTo(os);
         }

         for(int i = 0; i < ptrs.length; i++) {
            os.writeLong(ptrs[i]);
         }

         writeValue(page, new Value(bos.toByteArray()));
      }

      public BTreeNode getChildNode(int idx) {
         if(ph.status == BRANCH && idx >= 0 && idx < ptrs.length) {
            return getBTreeNode(ptrs[idx], this);
         }
         else {
            throw new RuntimeException("File interrupted!");
         }
      }

      public synchronized long removeKey(Key key) throws IOException {
         int idx = Arrays.binarySearch(keys, key);

         switch(ph.status) {
         case BRANCH:
            idx = idx < 0 ? -(idx + 1) : idx + 1;
            return getChildNode(idx).removeKey(key);
         case LEAF:
            if(idx < 0) {
               return idx;
            }
            else {
               long oldPtr = ptrs[idx];
               set(deleteArrayKey(keys, idx), deleteArrayLong(ptrs, idx));
               write();
               return oldPtr;
            }
         default:
            throw new BTreeCorruptException("Invalid page type: " + ph.status);
         }
      }

      public synchronized long addKey(Key key, long pointer)
         throws IOException
      {
         int idx = Arrays.binarySearch(keys, key);

         switch(ph.status) {
         case BRANCH:
            idx = idx < 0 ? -(idx + 1) : idx + 1;
            return getChildNode(idx).addKey(key, pointer);
         case LEAF:
            if(idx >= 0) {
               long oldPtr = ptrs[idx];
               ptrs[idx] = pointer;
               set(keys, ptrs);
               write();
               return oldPtr;
            }
            else {
               idx = -(idx + 1);
               boolean split = needSplit(key);
               set(insertArrayValue(keys, key, idx), insertArrayLong(ptrs,
                  pointer, idx));

               if(split) {
                  split();
               }
               else {
                  write();
               }
            }

            return -1;
         default:
            throw new BTreeCorruptException("Invalid page type: " + ph.status);
         }
      }

      public synchronized void promoteValue(Key key, long rightPointer)
         throws IOException
      {
         boolean split = needSplit(key);
         int idx = Arrays.binarySearch(keys, key);
         idx = idx < 0 ? -(idx + 1) : idx + 1;
         set(insertArrayValue(keys, key, idx), insertArrayLong(ptrs,
            rightPointer, idx + 1));

         if(split) {
            split();
         }
         else {
            write();
         }
      }

      public Key getSeparator(Key key1, Key key2) {
         int idx = key1.compareTo(key2);
         byte[] b = new byte[Math.abs(idx)];
         key2.copyTo(b, 0, b.length);
         return new Key(b);
      }

      public boolean needSplit(Key key) {
         return this.keys.length > 4 && (this.ph.dataLen + 8 + key.getLength() +
            2 > BTreeFile.this.fileHeader.workSize);
      }

      public void split() throws IOException {
         Key[] leftVals;
         Key[] rightVals;
         long[] leftPtrs;
         long[] rightPtrs;
         Key separator;
         short vc = ph.keyCount;
         int pivot = vc / 2;

         switch(ph.status) {
         case BRANCH:
            leftVals = new Key[pivot];
            leftPtrs = new long[leftVals.length + 1];
            rightVals = new Key[vc - (pivot + 1)];
            rightPtrs = new long[rightVals.length + 1];
            System.arraycopy(keys, 0, leftVals, 0, leftVals.length);
            System.arraycopy(ptrs, 0, leftPtrs, 0, leftPtrs.length);
            System.arraycopy(keys, leftVals.length + 1, rightVals, 0,
               rightVals.length);
            System.arraycopy(ptrs, leftPtrs.length, rightPtrs, 0,
               rightPtrs.length);
            separator = keys[leftVals.length];
            break;
         case LEAF:
            leftVals = new Key[pivot];
            leftPtrs = new long[leftVals.length];
            rightVals = new Key[vc - pivot];
            rightPtrs = new long[rightVals.length];
            System.arraycopy(keys, 0, leftVals, 0, leftVals.length);
            System.arraycopy(ptrs, 0, leftPtrs, 0, leftPtrs.length);
            System.arraycopy(keys, leftVals.length, rightVals, 0,
               rightVals.length);
            System.arraycopy(ptrs, leftPtrs.length, rightPtrs, 0,
               rightPtrs.length);
            separator = getSeparator(leftVals[leftVals.length - 1],
               rightVals[0]);
            break;
         default:
            throw new BTreeCorruptException("Invalid page type: " + ph.status);
         }

         if(parent == null) {
            BTreeNode rNode = createBTreeNode(ph.status, this);
            rNode.set(rightVals, rightPtrs);
            BTreeNode lNode = createBTreeNode(ph.status, this);
            lNode.set(leftVals, leftPtrs);
            ph.status = BRANCH;
            ph.dirty = true;
            set(new Key[]{separator}, new long[]{
               lNode.page.pageNum.longValue(), rNode.page.pageNum.longValue()});
            write();
            rNode.write();
            lNode.write();
         }
         else {
            set(leftVals, leftPtrs);
            BTreeNode rNode = createBTreeNode(ph.status, parent);
            rNode.set(rightVals, rightPtrs);
            write();
            rNode.write();
            parent.promoteValue(separator, rNode.page.pageNum.longValue());
         }
      }

      public synchronized long findKey(Key key) throws IOException {
         int idx = Arrays.binarySearch(keys, key);

         switch(ph.status) {
         case BRANCH:
            idx = idx < 0 ? -(idx + 1) : idx + 1;
            return getChildNode(idx).findKey(key);
         case LEAF:
            if(idx < 0) {
               return -1;
            }
            else {
               return ptrs[idx];
            }
         default:
            throw new BTreeCorruptException("Invalid page type: " + ph.status);
         }
      }

      public synchronized void accept(Visitor visitor) throws Exception {
         synchronized(BTreeFile.this) {
            if(!opened) {
               throw new InterruptedException("I am interrupted!");
            }
         }

         if(ph.status == BRANCH) {
            for(int i = 0; i < ptrs.length; i++) {
               getChildNode(i).accept(visitor);
            }
         }
         else {
            for(int i = 0; i < keys.length; i++) {
               visitor.visit(keys[i]);
            }
         }
      }

      private final Page page;
      private final PageHeader ph;
      private Key[] keys;
      private long[] ptrs;
      private BTreeNode parent;
      private boolean loaded;
   }

   private final class FileHeader {
      public FileHeader() {
         this(1024, DEFAULT_PAGESIZE);
      }

      public FileHeader(long pageCount, int pageSize) {
         this.pageSize = pageSize;
         this.pageCount = pageCount;
         this.totalCount = pageCount;
         this.headerSize = (short) 4096;
         calculateWorkSize();
      }

      public synchronized void read() throws IOException {
         RandomAccessFile raf = null;

         try {
            raf = getDescriptor();
            raf.seek(0);
            headerSize = raf.readShort();
            pageSize = raf.readInt();
            pageCount = raf.readLong();
            totalCount = raf.readLong();
            firstFreePage = raf.readLong();
            lastFreePage = raf.readLong();
            pageHeaderSize = raf.readByte();
            maxKeySize = raf.readShort();
            recordCount = raf.readLong();
            rootPage = raf.readLong();
            totalBytes = raf.readLong();
            calculateWorkSize();
         }
         finally {
            putDescriptor(raf);
         }
      }

      public synchronized void write() throws IOException {
         if(!dirty) {
            return;
         }

         RandomAccessFile raf = null;

         try {
            raf = getDescriptor();
            raf.seek(0);
            raf.writeShort(headerSize);
            raf.writeInt(pageSize);
            raf.writeLong(pageCount);
            raf.writeLong(totalCount);
            raf.writeLong(firstFreePage);
            raf.writeLong(lastFreePage);
            raf.writeByte(pageHeaderSize);
            raf.writeShort(maxKeySize);
            raf.writeLong(recordCount);
            raf.writeLong(rootPage);
            raf.writeLong(totalBytes);
            dirty = false;
         }
         finally {
            putDescriptor(raf);
         }
      }

      public synchronized void setPageSize(int pageSize) {
         this.pageSize = pageSize;
         calculateWorkSize();
         dirty = true;
      }

      public synchronized void setPageHeaderSize(byte pageHeaderSize) {
         this.pageHeaderSize = pageHeaderSize;
         calculateWorkSize();
         dirty = true;
      }

      public synchronized void calculateWorkSize() {
         workSize = pageSize - pageHeaderSize;
      }

      boolean dirty = false;
      int workSize;
      short headerSize;
      int pageSize;
      long pageCount;
      long totalCount;
      long firstFreePage = -1;
      long lastFreePage = -1;
      byte pageHeaderSize = 64;
      short maxKeySize = DEFAULT_KEYSIZE_MAX;
      long recordCount;
      long rootPage = 0;
      long totalBytes = 0;
   }

   private final class Page implements Comparable {
      public Page(Long pageNum) {
         this.header = new PageHeader();
         this.pageNum = pageNum;
         this.offset = fileHeader.headerSize +
            (pageNum.longValue() * fileHeader.pageSize);
      }

      public synchronized void read() throws IOException {
         if(data == null) {
            RandomAccessFile raf = null;

            try {
               byte[] data = new byte[fileHeader.pageSize];
               raf = getDescriptor();
               raf.seek(this.offset);
               raf.read(data);
               ByteArrayInputStream bis = new ByteArrayInputStream(data);
               this.header.read(new DataInputStream(bis));
               this.keyPos = fileHeader.pageHeaderSize;
               this.dataPos = this.keyPos + this.header.keyLen;
               this.data = data;
            }
            finally {
               putDescriptor(raf);
            }
         }
      }

      public void write() throws IOException {
         synchronized(this) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(
               fileHeader.pageHeaderSize);
            header.write(new DataOutputStream(bos));
            byte[] b = bos.toByteArray();
            System.arraycopy(b, 0, data, 0, b.length);
         }

         BTreeFile.this.addDirty(this);
      }

      public synchronized void flush() throws Exception {
         RandomAccessFile raf = null;

         try {
            raf = getDescriptor();

            if(this.offset >= raf.length()) {
               long o = (fileHeader.headerSize +
                  ((fileHeader.totalCount * 3) / 2) * fileHeader.pageSize) +
                  (fileHeader.pageSize - 1);
               raf.seek(o);
               raf.writeByte(0);
            }

            raf.seek(this.offset);
            raf.write(this.data);
         }
         finally {
            putDescriptor(raf);
         }
      }

      public synchronized void setKey(Key key) {
         header.setKey(key);
         key.copyTo(this.data, this.keyPos);
         this.dataPos = this.keyPos + header.keyLen;
      }

      public synchronized Key getKey() {
         if(header.keyLen > 0) {
            return new Key(this.data, this.keyPos, header.keyLen);
         }
         else {
            return null;
         }
      }

      public synchronized void streamTo(OutputStream os)
         throws IOException
      {
         if(header.dataLen > 0) {
            os.write(this.data, this.dataPos, header.dataLen);
         }
      }

      public synchronized void streamFrom(InputStream is)
         throws IOException
      {
         int avail = is.available();
         header.dataLen = fileHeader.workSize - header.keyLen;

         if(avail < header.dataLen) {
            header.dataLen = avail;
         }

         if(header.dataLen > 0) {
            is.read(this.data, this.keyPos + header.keyLen, header.dataLen);
         }
      }

      @Override
      public int compareTo(Object o) {
         return (int) (this.pageNum.longValue() -
            ((Page) o).pageNum.longValue());
      }

      private final Long pageNum;
      private final PageHeader header;
      private final long offset;
      private byte[] data;
      private int keyPos;
      private int dataPos;
   }

   /**
    * Value is the primary base class for all data storing objects.
    * The content window of Value objects are immutable, but the
    * underlying byte array is not.
    */
   public static class Value implements Comparable {
      /**
       * Constructor.
       */
      public Value(Value value) {
         this.data = value.data;
         this.pos = value.pos;
         this.len = value.len;
      }

      /**
       * Constructor.
       */
      public Value(byte[] data) {
         this.data = data;
         this.len = data.length;
      }

      /**
       * Constructor.
       */
      public Value(byte[] data, int pos, int len) {
         this.data = data;
         this.pos = pos;
         this.len = len;
      }

      /**
       * Constructor.
       */
      public Value(String data) {
         try {
            this.data = data.getBytes("utf-8");
            this.len = this.data.length;
         }
         catch(UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.toString());
         }
      }

      /**
       * Retrieve the data being stored by the Value as a byte array.
       * @return the Data.
       */
      public final byte[] getData() {
         if(len != data.length) {
            byte[] b = new byte[len];
            System.arraycopy(data, pos, b, 0, len);
            return b;
         }
         else {
            return data;
         }
      }

      /**
       * Retrieve the length of the data being stored by the Value.
       * @return the Value length.
       */
      public final int getLength() {
         return len;
      }

      /**
       * Return an InputStream for the Value.
       * @return an InputStream.
       */
      public final InputStream getInputStream() {
         return new ByteArrayInputStream(data, pos, len);
      }

      /**
       * Stream the content of the Value to an OutputStream.
       * @param out the OutputStream.
       */
      public final void streamTo(OutputStream out) throws IOException {
         out.write(data, pos, len);
      }

      public final void copyTo(byte[] tdata, int tpos) {
         System.arraycopy(data, pos, tdata, tpos, len);
      }

      public final void copyTo(byte[] tdata, int tpos, int len) {
         System.arraycopy(data, pos, tdata, tpos, len);
      }

      public final String toString() {
         try {
            return new String(data, pos, len, "utf-8");
         }
         catch(UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.toString());
         }
      }

      public int hashCode() {
         return toString().hashCode();
      }

      public boolean equals(Value value) {
         return len == value.len ? compareTo(value) == 0 : false;
      }

      public boolean equals(Object obj) {
         if(this == obj) {
            return true;
         }

         if(obj instanceof Value) {
            return equals((Value) obj);
         }
         else {
            return equals(new Value(obj.toString()));
         }
      }

      public final int compareTo(Value value) {
         byte[] ddata = value.data;
         int dpos = value.pos;
         int dlen = value.len;
         int stop = len > dlen ? dlen : len;

         for(int i = 0; i < stop; i++) {
            byte b1 = data[pos + i];
            byte b2 = ddata[dpos + i];

            if(b1 == b2) {
               continue;
            }
            else {
               short s1 = (short) (b1 >>> 0);
               short s2 = (short) (b2 >>> 0);
               return s1 > s2 ? (i + 1) : -(i + 1);
            }
         }

         if(len == dlen) {
            return 0;
         }
         else {
            return len > dlen ? stop + 1 : -(stop + 1);
         }
      }

      @Override
      public int compareTo(Object obj) {
         if(obj instanceof Value) {
            return compareTo((Value) obj);
         }
         else {
            return compareTo(new Value(obj.toString()));
         }
      }

      protected byte[] data = null;
      protected int pos = 0;
      protected int len = -1;
   }

   /**
    * Key extends Value by providing a hash value for the Key.
    */
   public static final class Key extends Value {
      /**
       * Constructor.
       */
      public Key(Value value) {
         super(value);
      }

      /**
       * Constructor.
       */
      public Key(byte[] data) {
         super(data);
      }

      /**
       * Constructor.
       */
      public Key(byte[] data, int pos, int len) {
         super(data, pos, len);
      }

      /**
       * Constructor.
       */
      public Key(String data) {
         super(data);
      }

      public boolean equals(Object obj) {
         if(obj instanceof Key) {
            return equals((Key) obj);
         }
         else {
            return super.equals(obj);
         }
      }

      @Override
      public boolean equals(Value value) {
         if(value instanceof Key) {
            Key key = (Key) value;
            return hashCode() == key.hashCode() ? compareTo(key) == 0 : false;
         }
         else {
            return super.equals(value);
         }
      }

      public int hashCode() {
         if(hash == 0) {
            int pl = pos + len;

            for(int i = pos; i < pl; i++) {
               hash = (hash << 5) ^ data[i];
               hash = hash % 1234567891;
            }

            hash = Math.abs(hash);
         }

         return hash;
      }

      private int hash = 0;
   }

   /**
    * Visitor visits a btree.
    */
   public static interface Visitor {
      /**
       * Visit one btree key.
       * @param key the specified btree key.
       */
      public void visit(Key key) throws Exception;
   }

   private static final byte UNUSED = 0;
   private static final byte OVERFLOW = 126;
   private static final byte DELETED = 127;
   private static final int NO_PAGE = -1;
   private static final byte LEAF = 1;
   private static final byte BRANCH = 2;
   private static final byte STREAM = 3;
   private static final byte RECORD = 20;

   private final Map cache = new WeakHashMap();
   private final Object dirtyLock = new Object();
   private final Stack descriptors = new Stack();
   private final FileHeader fileHeader;
   private final Map pages = new WeakHashMap();

   private Map dirty = new HashMap();
   private int descriptorsCount;
   private int descriptorsMax;
   private int dirtysizeMax;
   private Properties config;
   private boolean opened;
   private boolean dropped;
   private boolean cached;
   private boolean ronly;
   private File file;
   private BTreeNode rootNode;

   private static final Logger LOG =
      LoggerFactory.getLogger(BTreeFile.class);
}
