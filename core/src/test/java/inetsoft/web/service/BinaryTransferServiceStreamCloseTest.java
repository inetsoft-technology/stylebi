/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.service;

import inetsoft.util.FileSystemService;
import inetsoft.util.cachefs.BinaryTransfer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81724: an input stream that is never closed leaks the blob read lock.
 */
@Tag("core")
class BinaryTransferServiceStreamCloseTest {
   private static class TrackingStream extends InputStream {
      final AtomicBoolean closed = new AtomicBoolean();

      @Override
      public int read() throws IOException {
         throw new IOException("simulated read failure");
      }

      @Override
      public void close() {
         closed.set(true);
      }
   }

   private BinaryTransferService spyService(TrackingStream stream, BinaryTransfer transfer)
      throws IOException
   {
      BinaryTransferService service = spy(new BinaryTransferService(mock(FileSystemService.class)));
      doReturn(stream).when(service).getInputStream(transfer);
      doNothing().when(service).clearCacheFile(transfer);
      return service;
   }

   @Test
   void writeDataClosesStreamAndClearsCacheFileWhenCopyFails() throws Exception {
      BinaryTransfer transfer = new BinaryTransfer("x");
      TrackingStream stream = new TrackingStream();
      BinaryTransferService service = spyService(stream, transfer);

      assertThrows(IOException.class, () -> service.writeData(transfer, new ByteArrayOutputStream()));
      assertTrue(stream.closed.get(), "input stream must be closed when the copy fails");
      verify(service).clearCacheFile(transfer);
   }

   @Test
   void getDataClosesStreamAndClearsCacheFileWhenReadFails() throws Exception {
      BinaryTransfer transfer = new BinaryTransfer("x");
      TrackingStream stream = new TrackingStream();
      BinaryTransferService service = spyService(stream, transfer);

      assertThrows(IOException.class, () -> service.getData(transfer));
      assertTrue(stream.closed.get(), "input stream must be closed when the read fails");
      verify(service).clearCacheFile(transfer);
   }
}
