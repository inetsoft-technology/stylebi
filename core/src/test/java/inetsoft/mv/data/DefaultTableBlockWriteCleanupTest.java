/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link DefaultTableBlock#write(WritableByteChannel)} does not leave its
 * {@code mvcolswap*.dat} column temp files in the cache directory when a column write fails.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DefaultTableBlockWriteCleanupTest {
   @Test
   void failedColumnWriteDeletesTempFiles() throws Exception {
      int before = swapFiles().length;

      DefaultTableBlock block = newBlock(3, true);
      assertThrows(IOException.class, () -> block.write(sink()));

      assertEquals(before, swapFiles().length);
   }

   @Test
   void successfulWriteDeletesTempFiles() throws Exception {
      int before = swapFiles().length;

      DefaultTableBlock block = newBlock(3, false);
      block.write(sink());

      assertEquals(before, swapFiles().length);
   }

   /**
    * Creates a block of {@code dcnt} dimensions whose second column write fails when
    * {@code fail} is true; each column writes a few bytes to its temp file.
    */
   private static DefaultTableBlock newBlock(int dcnt, boolean fail) throws IOException {
      DefaultTableBlock block = new DefaultTableBlock(1, dcnt, 0);
      String[] names = new String[dcnt];
      Arrays.fill(names, "col");
      block.init(dcnt, 0, names, names, 1);

      for(int i = 0; i < dcnt; i++) {
         MVDimColumn col = mock(MVDimColumn.class);
         int index = i;

         when(col.write(any(), any())).thenAnswer(inv -> {
            if(fail && index == 1) {
               throw new IOException("injected: No space left on device");
            }

            FileChannel channel = inv.getArgument(0);
            channel.write(ByteBuffer.wrap(new byte[16]));
            return inv.getArgument(1);
         });

         block.dcols[i] = col;
      }

      return block;
   }

   private static File[] swapFiles() throws IOException {
      File dir = new File(FileSystemService.getInstance().getCacheDirectory());
      File[] files = dir.listFiles((d, name) -> name.startsWith("mvcolswap"));
      return files == null ? new File[0] : files;
   }

   private static WritableByteChannel sink() {
      return Channels.newChannel(new ByteArrayOutputStream());
   }
}
