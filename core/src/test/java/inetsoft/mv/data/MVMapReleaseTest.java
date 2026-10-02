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

import inetsoft.mv.util.FileSeekableInputStream;
import inetsoft.mv.util.SeekableChannel;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.test.*;
import inetsoft.util.swap.XSwapUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every {@code map()} in an MV read must be released by {@code unmap()} even when the read
 * throws. A corrupt block, which makes the lz4 decompression throw, is the same event that
 * makes {@code ViewsheetSandbox.recreateMVOnDemand()} rebuild the MV, and the removed
 * {@code System.gc()} there was never able to release a leaked mapping (it is a no-op under
 * {@code -XX:+DisableExplicitGC}). See Bug #77592.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVMapReleaseTest {
   // ByteIntBuf

   @Test
   void intBufReadsAValidBlockAndUnmapsIt(@TempDir Path dir) throws Exception {
      byte[] values = { 1, 2, 3, 4 };
      TrackingStream in = open(dir, intBufHeader(compress(values), values.length, values.length));
      ByteIntBuf buf = new ByteIntBuf(null);

      buf.read(in);

      assertEquals(3, buf.getValue(2));
      assertMappingsReleased(in, 1);
   }

   @Test
   void intBufUnmapsWhenTheBlockIsCorrupt(@TempDir Path dir) throws Exception {
      TrackingStream in = open(dir, intBufHeader(CORRUPT_LZ4, 4, 4));

      assertNotCastFailure(assertThrows(Exception.class, () -> new ByteIntBuf(null).read(in)));
      assertMappingsReleased(in, 1);
   }

   @Test
   void intBufKeepsTheMapFailure(@TempDir Path dir) throws Exception {
      TrackingStream in = open(dir, intBufHeader(CORRUPT_LZ4, 4, 4));
      in.failMap = true;

      IOException ex = assertThrows(IOException.class, () -> new ByteIntBuf(null).read(in));

      assertEquals(MAP_FAILED, ex.getMessage());
      assertMappingsReleased(in, 0);
   }

   // PackedIntBuf

   @Test
   void packedIntBufUnmapsWhenTheBlockIsCorrupt(@TempDir Path dir) throws Exception {
      TrackingStream in = open(dir, packedIntBufHeader(CORRUPT_LZ4));

      assertNotCastFailure(assertThrows(Exception.class,
                                        () -> new PackedIntBuf(8, null).read(in)));
      assertMappingsReleased(in, 1);
   }

   @Test
   void packedIntBufKeepsTheMapFailure(@TempDir Path dir) throws Exception {
      TrackingStream in = open(dir, packedIntBufHeader(CORRUPT_LZ4));
      in.failMap = true;

      IOException ex = assertThrows(IOException.class, () -> new PackedIntBuf(8, null).read(in));

      assertEquals(MAP_FAILED, ex.getMessage());
      assertMappingsReleased(in, 0);
   }

   // BitDimIndex

   @Test
   void bitDimIndexUnmapsWhenTheBlockIsCorrupt(@TempDir Path dir) throws Exception {
      TrackingStream in = open(dir, bitDimIndexHeader(CORRUPT_LZ4));

      assertNotCastFailure(assertThrows(Exception.class, () -> new BitDimIndex().read0(in)));
      assertMappingsReleased(in, 1);
   }

   @Test
   void bitDimIndexKeepsTheMapFailure(@TempDir Path dir) throws Exception {
      TrackingStream in = open(dir, bitDimIndexHeader(CORRUPT_LZ4));
      in.failMap = true;

      IOException ex = assertThrows(IOException.class, () -> new BitDimIndex().read0(in));

      assertEquals(MAP_FAILED, ex.getMessage());
      assertMappingsReleased(in, 0);
   }

   // MVDecimalColumn.readBlock

   @Test
   void readBlockReturnsAHeapCopyOfAValidBlock(@TempDir Path dir) throws Exception {
      ByteBuffer data = ByteBuffer.allocate(2 * 8);
      data.putDouble(1.5).putDouble(2.5);
      TrackingStream in = open(dir, decimalColumnHeader(compress(data.array())));

      ByteBuffer row = new MVDoubleColumn(in, 0, null, 2, false).readBlock(0);

      assertFalse(row.isDirect(), "the returned buffer must not be the mapped one");
      assertEquals(2.5, row.getDouble(8));
      assertMappingsReleased(in, 1);
   }

   @Test
   void readBlockThrowsAndUnmapsWhenTheBlockIsCorrupt(@TempDir Path dir) throws Exception {
      TrackingStream in = open(dir, decimalColumnHeader(CORRUPT_LZ4));
      MVDoubleColumn col = new MVDoubleColumn(in, 0, null, 2, false);

      // before the fix: the error was logged and null returned, with the block left mapped
      RuntimeException ex = assertThrows(RuntimeException.class, () -> col.readBlock(0));

      assertNotCastFailure(ex);
      assertMappingsReleased(in, 1);
   }

   @Test
   void readBlockNeverReturnsTheUnmappedBufferWhenTheMagicIsMissing(@TempDir Path dir)
      throws Exception
   {
      // uncompressByteBuffer returns its argument (the mapped buffer) when the magic is missing
      byte[] noMagic = new byte[16];
      TrackingStream in = open(dir, decimalColumnHeader(noMagic));
      MVDoubleColumn col = new MVDoubleColumn(in, 0, null, 2, false);

      // before the fix: the mapped buffer was returned after it had been unmapped
      assertThrows(RuntimeException.class, () -> col.readBlock(0));
      assertMappingsReleased(in, 1);
   }

   @Test
   void readBlockKeepsTheMapFailure(@TempDir Path dir) throws Exception {
      TrackingStream in = open(dir, decimalColumnHeader(CORRUPT_LZ4));
      in.failMap = true;
      MVDoubleColumn col = new MVDoubleColumn(in, 0, null, 2, false);

      RuntimeException ex = assertThrows(RuntimeException.class, () -> col.readBlock(0));

      assertInstanceOf(IOException.class, ex.getCause());
      assertEquals(MAP_FAILED, ex.getCause().getMessage());
      assertMappingsReleased(in, 0);
      assertTrue(in.isOpen(), "readBlock must not close the column's own channel");
   }

   @Test
   void readBlockClosesTheChannelItReopenedWhenTheMapFails(@TempDir Path dir) throws Exception {
      byte[] content = decimalColumnHeader(CORRUPT_LZ4);
      TrackingStream in = open(dir, content);
      TrackingStream reopened = open(dir, content);
      reopened.failMap = true;
      in.reopenWith = reopened;
      MVDoubleColumn col = new MVDoubleColumn(in, 0, null, 2, false);
      in.close();

      // before the fix: the channel was closed only after a successful map, leaking its fd
      RuntimeException ex = assertThrows(RuntimeException.class, () -> col.readBlock(0));

      assertEquals(MAP_FAILED, ex.getCause().getMessage());
      assertFalse(reopened.isOpen(), "the channel readBlock reopened must be closed");
   }

   // block layouts

   private static byte[] intBufHeader(byte[] payload, int nkeys, int size) {
      return block(13, payload, b -> b.putInt(payload.length).put((byte) 1)
         .putInt(nkeys).putInt(size));
   }

   private static byte[] packedIntBufHeader(byte[] payload) {
      return block(17, payload, b -> b.putInt(payload.length).put((byte) 1)
         .putInt(8).putInt(8).putInt(1));
   }

   private static byte[] bitDimIndexHeader(byte[] payload) {
      return block(5, payload, b -> b.putInt(payload.length).put((byte) 1));
   }

   // one fragment: total length, compressed flag, then the block offset and length
   private static byte[] decimalColumnHeader(byte[] payload) {
      return block(17, payload, b -> b.putInt(payload.length).put((byte) 1)
         .putLong(17).putInt(payload.length));
   }

   private static byte[] block(int headerLength, byte[] payload, Consumer<ByteBuffer> header) {
      ByteBuffer buf = ByteBuffer.allocate(headerLength + payload.length);
      header.accept(buf);
      assertEquals(headerLength, buf.position());
      buf.put(payload);
      return buf.array();
   }

   private static byte[] compress(byte[] data) {
      ByteBuffer buf = XSwapUtil.compressByteBuffer(ByteBuffer.wrap(data));
      byte[] arr = new byte[buf.remaining()];
      buf.get(arr);
      return arr;
   }

   private static TrackingStream open(Path dir, byte[] content) throws IOException {
      File file = dir.resolve("block.bin").toFile();
      Files.write(file.toPath(), content);
      FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ);
      return new TrackingStream(new FileSeekableInputStream(fc, file));
   }

   private static void assertMappingsReleased(TrackingStream in, int maps) {
      assertEquals(maps, in.mapCount, "map() calls");
      assertEquals(maps, in.unmapCount, "every mapped buffer must be unmapped exactly once");
      assertTrue(in.mapped.isEmpty(), "a mapping was left open");
   }

   private static void assertNotCastFailure(Throwable ex) {
      for(Throwable t = ex; t != null; t = t.getCause()) {
         assertFalse(t instanceof ClassCastException,
                     "unmap of a heap buffer masked the real failure: " + t);
      }
   }

   /** Delegates to a file stream and records which mapped buffers are still mapped. */
   private static final class TrackingStream implements SeekableInputStream {
      TrackingStream(FileSeekableInputStream delegate) {
         this.delegate = delegate;
      }

      @Override
      public ByteBuffer map(long pos, long size) throws IOException {
         if(failMap) {
            throw new IOException(MAP_FAILED);
         }

         ByteBuffer buf = delegate.map(pos, size);
         mapCount++;
         mapped.add(buf);
         return buf;
      }

      @Override
      public void unmap(ByteBuffer buf) throws IOException {
         assertTrue(mapped.remove(buf), "unmap of a buffer that is not mapped: " + buf);
         unmapCount++;
         delegate.unmap(buf);
      }

      @Override
      public SeekableInputStream reopen() throws IOException {
         return reopenWith != null ? reopenWith : delegate.reopen();
      }

      @Override
      public long getModificationTime() throws IOException {
         return delegate.getModificationTime();
      }

      @Override
      public Object getFilePath() {
         return delegate.getFilePath();
      }

      @Override
      public SeekableChannel position(long pos) throws IOException {
         delegate.position(pos);
         return this;
      }

      @Override
      public long position() throws IOException {
         return delegate.position();
      }

      @Override
      public long size() throws IOException {
         return delegate.size();
      }

      @Override
      public int read(ByteBuffer dst) throws IOException {
         return delegate.read(dst);
      }

      @Override
      public boolean isOpen() {
         return delegate.isOpen();
      }

      @Override
      public void close() throws IOException {
         delegate.close();
      }

      private final FileSeekableInputStream delegate;
      // identity set: ByteBuffer.equals compares contents
      private final Set<ByteBuffer> mapped = Collections.newSetFromMap(new IdentityHashMap<>());
      private int mapCount;
      private int unmapCount;
      private boolean failMap;
      private TrackingStream reopenWith;
   }

   private static final String MAP_FAILED = "map failed";
   // lz4j magic, an uncompressed length of 64, then bytes that are not a valid lz4 stream
   private static final byte[] CORRUPT_LZ4 = {
      'l', 'z', '4', 'j', 0, 0, 0, 64,
      (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
      (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
   };
}
