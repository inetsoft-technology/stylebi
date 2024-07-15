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
package inetsoft.mv.data;

import inetsoft.mv.MVTool;
import inetsoft.util.FileSystemService;
import inetsoft.util.swap.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.List;

/**
 * An object list that can swap the objects to a MV file.
 *
 * @author InetSoft Technology Corp
 * @version 11.4
 */
public class DimValueList {
   /**
    * Add a value to the list.
    */
   public void add(Object val) {
      // values are hold in one list when constructing a dimension value list
      currFragment[currIdx++] = val;

      // write to file when full
      if(currIdx == FRAGMENT_SIZE) {
         writeCurrentFragment();
      }
   }

   // write current fragment to file and reset the index (to start next fragment)
   private void writeCurrentFragment() {
      File file = getFile(fragmentFiles.size());
      Object[] arr = currFragment;

      if(currIdx != FRAGMENT_SIZE) {
         arr = new Object[currIdx];
         System.arraycopy(currFragment, 0, arr, 0, currIdx);
      }

      try(OutputStream output = new FileOutputStream(file)) {
         ByteBuffer buf = MVTool.getObjectsByteBuffer(arr, true);
         WritableByteChannel channel = Channels.newChannel(output);

         while(buf.hasRemaining()) {
            channel.write(buf);
         }

         fragmentFiles.add(file);
      }
      catch(ClassCastException ex) {
         throw ex;
      }
      catch(Exception ex) {
         LOG.error("Failed to write data: " + file, ex);
      }
      finally {
         currIdx = 0;
      }
   }

   private File getFile(int idx) {
      if(prefix == null) {
         prefix = "dimvalue" + System.currentTimeMillis() + hashCode();
      }

      return FileSystemService.getInstance().getCacheFile(prefix + "_" + idx + ".tdat");
   }

   /**
    * Get the specified value.
    */
   public Object get(int idx) {
      Object val = null;

      // values are held in fragments when reading dimension values from
      // file so we can load it on demand
      if(channelProvider != null && segpos != null) {
         int n = idx / FRAGMENT_SIZE;
         val = fragments[n].get(idx % FRAGMENT_SIZE);
      }
      else if(idx < fragmentFiles.size() * FRAGMENT_SIZE) {
         throw new RuntimeException("Fragment already not in memory and " +
                                       "can't be accessed: " + idx);
      }
      else {
         val = currFragment[currIdx];
      }

      return (val instanceof CompactString) ? val.toString() : val;
   }

   /**
    * Get the number of items in the list.
    */
   public int size() {
      return size != 0 ? size : fragmentFiles.size() * FRAGMENT_SIZE + currIdx;
   }

   /**
    * Write value to file.
    */
   public void write(WritableByteChannel channel0) throws IOException {
      SeekableByteChannel channel = (SeekableByteChannel) channel0;
      ByteBuffer buf = ByteBuffer.allocate(4);
      int cnt = size();
      buf.putInt(cnt);
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      long pos = channel.position();
      int n = (int) Math.ceil(cnt / (float) FRAGMENT_SIZE);
      long[] segpos = new long[n + 1];

      // reserve place for fragment positions
      channel.position(pos + segpos.length * 8);

      if(currIdx > 0) {
         writeCurrentFragment();
      }

      for(int i = 0, seg = 0; i < cnt; i += FRAGMENT_SIZE, seg++) {
         segpos[seg] = channel.position();

         // for incremental MV
         if(channelProvider != null && segpos != null) {
            int size = Math.min(FRAGMENT_SIZE, cnt - i);
            Object[] arr = new Object[size];

            for(int k = 0; k < size; k++) {
               // incremental may cause rewrite it again
               // fix bug1363703090104
               // arr[k] = values.get(i + k);
               arr[k] = get(i + k);
            }

            buf = MVTool.getObjectsByteBuffer(arr, true);

            while(buf.hasRemaining()) {
               channel.write(buf);
            }
         }
         else {
            File file = getFile(i / FRAGMENT_SIZE);

            try(FileChannel fc = new RandomAccessFile(file, "r").getChannel()) {
               buf = fc.map(FileChannel.MapMode.READ_ONLY, 0, file.length());

               while(buf.hasRemaining()) {
                  channel.write(buf);
               }

               MVTool.unmap((MappedByteBuffer) buf);
            }
         }
      }

      segpos[n] = channel.position();
      buf = ByteBuffer.allocate(segpos.length * 8);
      buf.asLongBuffer().put(segpos);
      XSwapUtil.position(buf, buf.position() + segpos.length * 8);
      XSwapUtil.flip(buf);
      // record the fragment positions
      channel.position(pos);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      // jump to the end of the file
      channel.position(segpos[n]);
   }

   /**
    * Read value from file.
    */
   public void read(ChannelProvider channelProvider, ReadableByteChannel channel0)
      throws IOException
   {
      this.channelProvider = channelProvider;
      this.pos = ((SeekableByteChannel) channel0).position();

      SeekableByteChannel channel = (SeekableByteChannel) channel0;
      ByteBuffer buf = ByteBuffer.allocate(4);
      channel.read(buf);
      XSwapUtil.flip(buf);
      size = buf.getInt();
      int n = (int) Math.ceil(size / (float) FRAGMENT_SIZE);

      segpos = new long[n + 1];
      fragments = new ValueFragment[n];

      for(int i = 0; i < n; i++) {
         fragments[i] = new ValueFragment(i);
      }

      buf = ByteBufferPool.getByteBuffer(segpos.length * 8);
      channel.read(buf);
      XSwapUtil.flip(buf);

      buf.asLongBuffer().get(segpos);
      channel.position(segpos[n]);
      ByteBufferPool.releaseByteBuffer(buf);
   }

   private class ValueFragment extends XSwappable {
      /**
       * @param n fragment index.
       */
      public ValueFragment(int n) {
         this.n = n;
      }

      @Override
      public double getSwapPriority() {
         if(!isValid()) {
            return 0;
         }

         return getAgePriority(XSwapper.cur - accessed, alive * 2);
      }

      @Override
      public boolean isCompleted() {
         return true;
      }

      @Override
      public boolean isSwappable() {
         return true;
      }

      @Override
      public boolean isValid() {
         return arr != null;
      }

      @Override
      public synchronized boolean swap() {
         arr = null;
         XSwapper.deregister(this);
         return true;
      }

      @Override
      public synchronized void dispose() {
         arr = null;
      }

      public final Object get(int idx) {
         Object[] arr = this.arr;

         if(arr == null) {
            synchronized(this) {
               read0();
               arr = this.arr;
            }
         }

         accessed = XSwapper.cur;
         return arr[idx];
      }

      // read the specified fragment
      private void read0() {
         SeekableByteChannel channel = null;

         try {
            XSwapper.getSwapper().waitForMemory();
            channel = channelProvider.newReadChannel();
            channel.position(segpos[n]);

            arr = MVTool.readObjects(channel, true);
            accessed = XSwapper.cur;
            XSwapper.register(this);
         }
         catch(Exception ex) {
            LOG.error("Failed to read fragment: {}", n, ex);
         }
         finally {
            IOUtils.closeQuietly(channel);
         }
      }

      private int n;
      private long accessed = 0;
      private Object[] arr;
   }

   @Override
   public void finalize() {
      dispose();
   }

   /**
    * Clean up.
    */
   public void dispose() {
      for(File file : fragmentFiles) {
         FileSystemService.getInstance().remove(file, 30000);
      }

      fragmentFiles.clear();
   }

   // number of objects in each fragment
   private static final int FRAGMENT_SIZE = 10000;

   // only used in creation of the MV
   private String prefix;
   private Object[] currFragment = new Object[FRAGMENT_SIZE];
   private int currIdx = 0;
   private List<File> fragmentFiles = new ArrayList<>();
   // used when list read back from a file
   private ChannelProvider channelProvider;
   private long pos; // starting position in file

   // used in reading the data from mv file
   private int size = 0;
   private long[] segpos;
   private ValueFragment[] fragments = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(DimValueList.class);
}
