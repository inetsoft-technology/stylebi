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
package inetsoft.util.swap;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A ByteBuffer pool.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public final class ByteBufferPool {
   /**
    * Get a byte buffer with at least the specified size.
    */
   public static ByteBuffer getByteBuffer(int size) {
      ByteBuffer buf;
      int nsize = roundSize(size);

      Deque<ByteBufferWrapper> bucket = buffers.get(nsize);
      ByteBufferWrapper wrapper = null;

      if(bucket != null) {
         synchronized(bucket) {
            wrapper = bucket.poll();
         }
      }

      if(wrapper == null) {
         buf = ByteBuffer.allocate(nsize);
      }
      else {
         buf = wrapper.getByteBuffer();
         XSwapUtil.clear(buf);
      }

      XSwapUtil.limit(buf, size);

      return buf;
   }

   /**
    * Put the buffer back into the pool to be reused.
    */
   public static void releaseByteBuffer(ByteBuffer buf) {
      ByteBufferWrapper wrapper = new ByteBufferWrapper(buf);
      final int nsize = buf.capacity();

      // ignore buffer is not returned by the pool
      if(nsize != roundSize(nsize)) {
         return;
      }

      Deque<ByteBufferWrapper> bucket =
         buffers.computeIfAbsent(nsize, k -> new ArrayDeque<>());

      synchronized(bucket) {
         bucket.add(wrapper);
      }
   }

   private static int roundSize(int size) {
      return (int) (Math.ceil(size / 10000.0) * 10000);
   }

   private static class ByteBufferWrapper extends XSwappable {
      public ByteBufferWrapper(ByteBuffer buf) {
         this.buf = buf;
         complete();
      }

      @Override
      public double getSwapPriority() {
         return 10;
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
         return true;
      }

      @Override
      public boolean swap() {
         dispose();
         return true;
      }

      @Override
      public void dispose() {
         final int nsize = buf.capacity();
         Deque<ByteBufferWrapper> bucket = buffers.get(nsize);

         if(bucket != null) {
            synchronized(bucket) {
               bucket.remove(this);
            }

            if(bucket.isEmpty()) {
               buffers.remove(nsize);
            }
         }

         XSwapper.deregister(this);
      }

      public ByteBuffer getByteBuffer() {
         return buf;
      }

      private ByteBuffer buf;
   }

   private static final ConcurrentMap<Integer, Deque<ByteBufferWrapper>> buffers =
      new ConcurrentHashMap<>();
}
