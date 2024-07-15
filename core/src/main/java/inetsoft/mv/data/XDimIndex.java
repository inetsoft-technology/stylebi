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

import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.fs.internal.CacheBlockFile;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.mv.util.TransactionChannel;
import inetsoft.util.FileSystemService;
import inetsoft.util.swap.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * XDimIndex, the index for one dimension.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public abstract class XDimIndex extends XSwappable {
   /**
    * Check if the specified filter operation is supported.
    */
   public static final boolean supportsOperation(int op) {
      return true;//op != XCondition.STARTING_WITH && op != XCondition.CONTAINS;
   }

   /**
    * Create an instance of XDimIndex.
    */
   public XDimIndex() {
      super();
      valid = true;
      completed = false;
      disposed = false;
      XSwapper.cur = System.currentTimeMillis();
      accessed = XSwapper.cur;
   }

   /**
    * Initialize this dimension index.
    */
   public final void init(SeekableInputStream channel, long fpos,
                          BlockFile file, boolean readHeaderInfo)
      throws IOException
   {
      this.file = file;
      this.fpos = fpos;

      if(channel != null) {
         this.channel = channel;
         this.fpos = channel.position();

         if(readHeaderInfo) {
            readHeader();
         }
      }
   }

   /**
    * Read the header information and advance channel position.
    */
   protected void readHeader() throws IOException {
      SeekableInputStream channel = this.channel;

      if((channel == null || !channel.isOpen()) && file != null) {
         channel = file.openInputStream();
      }
      else if(channel != null && !channel.isOpen()) {
         channel = this.channel.reopen();
      }

      ByteBuffer buf = ByteBuffer.allocate(getHeaderLength());

      synchronized(channel) {
         channel.position(fpos);
         channel.readFully(buf);
      }

      XSwapUtil.flip(buf);
      int len = buf.getInt();
      initHeader(buf);
      long pos = channel.position();
      channel.position(pos + len);

      if(channel != this.channel) {
         channel.close();
      }
   }

   protected void initHeader(ByteBuffer buf) {
      setCompressed(buf.get() == 1);
   }

   /**
    * Set compress data.
    */
   public void setCompressed(boolean compressed) {
      // this.compressed = compressed;
   }

   /**
    * Check if data is compressed.
    */
   public boolean isCompressed() {
      return this.compressed;
   }

   /**
    * Associate a row with an index key.
    */
   public abstract void addKey(int key, int row);

   /**
    * Sort keys.
    */
   @Override
   public void complete() {
      if(!completed) {
         completed = true;
         newbuf = true;
         super.complete();
      }
   }

   protected void touch() {
      accessed = XSwapper.cur;

      if(!completed) {
         access();
      }
   }

   /**
    * Access this dim dictionary.
    */
   protected void access() {
      if(!completed && (channel != null || file != null)) {
         XSwapper.getSwapper().waitForMemory();

         synchronized(this) {
            SeekableInputStream channel = this.channel;

            try {
               if((channel == null || !channel.isOpen()) && file != null) {
                  channel = file.openInputStream();
               }
               else if(channel != null && !channel.isOpen()) {
                  channel = this.channel.reopen();
               }
            }
            catch(IOException ignore) {
            }

            if(channel != null) {
               try {
                  synchronized(channel) {
                     channel.position(fpos);
                     read(channel);
                  }
               }
               catch(Exception ex) {
                  LOG.error("Failed to read from file when accessing dictionary ", ex);
               }

               if(channel != this.channel) {
                  try {
                     channel.close();
                  }
                  catch(IOException ignore) {
                  }
               }
            }
         }
      }

      // invalid? validate it
      if(!valid) {
         XSwapper.getSwapper().waitForMemory();

         synchronized(this) {
            validate();
         }
      }
   }

   /**
    * Validate the swappable internally.
    */
   private void validate() {
      if(valid || disposed) {
         return;
      }

      SeekableInputStream channel = this.channel;

      try {
         if((channel == null || !channel.isOpen()) && file != null) {
            channel = file.openInputStream();
         }
         else if(channel != null && !channel.isOpen()) {
            channel = this.channel.reopen();
         }
      }
      catch(IOException ignore) {
      }

      if(channel != null) {
         try {
            synchronized(channel) {
               channel.position(fpos);
               read0(channel);
               valid = true;
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to read from file during validation: ", ex);
         }

         if(channel != this.channel) {
            try {
               channel.close();
            }
            catch(IOException ignore) {
            }
	 }
      }
   }

   /**
    * Get the bit set at the specified index.
    */
   public abstract BitSet getRows(long idx, boolean cnull);

   /**
    * Get the bit set for the specified operation and value.
    */
   public BitSet getRows(String op, long val, boolean cnull) {
      return getRows(op, val, false, cnull);
   }

   /**
    * Set a flag indicating if Nulls should be included in Compare results,
    * instead of discarded
    */
   public void setIncludeNullCompare(boolean inc) {
      includeNullCompare = inc;
   }

   /**
    * Get the flag indicating if Nulls should be included in Compare results,
    * instead of discarded
    */
   public boolean getIncludeNullCompare() {
      return includeNullCompare;
   }

   /**
    * Get the bit set for the specified operation and value.
    */
   public BitSet getRows(String op, long val, boolean not, boolean cnull) {
      BitSet res = null;

      if("=".equals(op) || "null".equals(op)) {
         if(!not) {
            res = getRows(val, cnull);
         }
         else {
            // @by billh, handle the case: column is not null
            cnull = ("null".equals(op)) ? false : cnull;
            BitSet set1 = getRows(Integer.MIN_VALUE, true, val, false, cnull);
            BitSet set2 = getRows(val, false, Integer.MIN_VALUE, true, cnull);
            res = set1 == null ? set2 : set2 == null ? set1 : set1.or(set2);
         }
      }
      else if(!not && ">".equals(op) || not && "<=".equals(op)) {
         res = getRows(val, false, Integer.MIN_VALUE, true, cnull);
      }
      else if(!not && ">=".equals(op) || not && "<".equals(op)) {
         res = getRows(val, true, Integer.MIN_VALUE, true, cnull);
      }
      else if(!not && "<".equals(op) || not && ">=".equals(op)) {
         res = getRows(Integer.MIN_VALUE, true, val, false, cnull);
      }
      else if(!not && "<=".equals(op) || not && ">".equals(op)) {
         res = getRows(Integer.MIN_VALUE, true, val, true, cnull);
      }
      else if("IN".equals(op)) {
         res = getRows(op, new long[] {val}, not, cnull);
      }
      else if("STARTSWITH".equals(op) || "CONTAINS".equals(op) || "LIKE".equals(op)) {
         res = getLikeOpRows(op, val, not);
      }
      else {
         throw new RuntimeException("Unsupported operation: " + op + ", val: " +
            val + " found!");
      }

      return res == null ? new BitSet() : res;
   }

   /**
    * Get the bit set for the specified operation and values.
    */
   public BitSet getRows(String op, long[] val, boolean cnull) {
      return getRows(op, val, false, cnull);
   }

   /**
    * Get the bit set for the specified operation and values.
    */
   public BitSet getRows(String op, long[] val, boolean not, boolean cnull) {
      BitSet res = null;

      if("IN".equals(op)) {
         if(val != null && val.length == 0 && not) {
            res = getAllRows(cnull);
         }
         else {
            for(long aval : val) {
               BitSet set = getRows("=", aval, not, cnull);

               if(set != null) {
                  res = res == null ? set : not ? res.and(set) : res.or(set);
               }
            }
         }
      }
      else if("BETWEEN".equals(op) && val.length == 2) {
         if(!not) {
            res = getRows(val[0], true, val[1], true, cnull);
         }
         else {
            BitSet set1 = getRows("<", val[0], cnull);
            BitSet set2 = getRows(">", val[1], cnull);
            res = set1 == null ? set2 : set2 == null ? set1 : set1.or(set2);
         }
      }
      else {
         throw new RuntimeException("Unsupported operation: " + op + ", val: " +
            val + " found!");
      }

      return res == null ? new BitSet() : res;
   }

   /**
    * Get a bit set for all the rows in the index.
    */
   public BitSet getAllRows(boolean cnull) {
      return getRows(Integer.MIN_VALUE, true, Integer.MIN_VALUE, true, cnull);
   }

   /**
    * Get the bit set for the specified range.
    */
   public abstract BitSet getRows(long from, boolean fincluded, long to,
                                  boolean tincluded, boolean cnull);


   /**
    * Get bit set for the like op.
    */
   public BitSet getLikeOpRows(String op, long val, boolean not) {
      throw new RuntimeException("Unsupported operation \"" + op.toLowerCase()
         + "\" for this data type.");
   }

   /**
    * Read from channel.
    */
   public void read(SeekableInputStream channel) throws IOException {
      try {
         read0(channel);
      }
      catch(IOException ex) {
         throw ex;
      }
      catch(Exception ex) {
         throw new IOException("Failed to read file: " + ex);
      }

      // mark this dimension index to be swappable and register it
      completed = true;
      super.complete();
   }

   /**
    * Read from channel.
    */
   protected abstract void read0(SeekableInputStream channel) throws Exception;

   /**
    * Write to channel.
    */
   public abstract ByteBuffer write(WritableByteChannel channel, ByteBuffer sbuf)
         throws IOException;

   /**
    * Get the data length of this dimension index.
    */
   public abstract int getLength();

   /**
    * Get header length.
    */
   public int getHeaderLength() {
      // 4 -> body (no header) length
      // 1 -> compress
      return 4 + 1;
   }

   @Override
   public double getSwapPriority() {
      if(disposed || !completed || !valid) {
         return 0;
      }

      return getAgePriority(XSwapper.cur - accessed, alive * 2);
   }

   /**
    * Check if the swappable is completed for swap.
    * @return <tt>true</tt> if completed for swap, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isCompleted() {
      return completed;
   }

   /**
    * Check if the swappable is swappable.
    * @return <tt>true</tt> if swappable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isSwappable() {
      return !disposed && completed;
   }

   /**
    * Check if the swappable is in valid state.
    * @return <tt>true</tt> if in valid state, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isValid() {
      return valid;
   }

   /**
    * Swap the swappable.
    * @return <tt>true</tt> if swapped, <tt>false</tt> rejected.
    */
   @Override
   public synchronized boolean swap() {
      if(getSwapPriority() == 0) {
         return false;
      }

      if(newbuf) {
         file = new CacheBlockFile(prefix + EXTENSION);
         fpos = 0;

         try {
            if(!file.exists()) {
               try(TransactionChannel channel = file.openWriteChannel()) {
                  write(channel, null);
                  channel.commit();
               }
            }
         }
         catch(Exception ex) {
            LOG.error(ex.getMessage(), ex);
         }
      }

      newbuf = false;
      valid = false;
      swapped();

      return true;
   }

   /**
    * Called after this index is swapped to file. Clear the in-memory data now.
    */
   protected abstract void swapped();

   /**
    * Dispose the swappable.
    */
   @Override
   public synchronized void dispose() {
      if(disposed) {
         return;
      }

      disposed = true;

      File fileTest = getFile(prefix + EXTENSION);

      if(fileTest.exists()) {
         boolean result = fileTest.delete();

         if(!result) {
            FileSystemService.getInstance().remove(fileTest, 30000);
         }
      }
   }

   /**
    * Finalize the object.
    */
   @Override
   protected final void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   private static final Logger LOG = LoggerFactory.getLogger(XDimIndex.class);

   protected BlockFile file;
   protected final String EXTENSION = ".tdat";
   protected SeekableInputStream channel;
   protected long fpos = -1L;
   protected long accessed;
   private boolean disposed;
   private boolean valid;
   private boolean completed;
   private boolean compressed = true;
   private boolean newbuf = false;
   private transient boolean includeNullCompare = false;
}
