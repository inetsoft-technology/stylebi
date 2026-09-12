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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link XDimDictionary#read} is a <em>delayed</em> read: it takes only size, hashCode and
 * dataType off the channel, marks the dictionary invalid and returns. The {@code overflow} flag
 * is assigned by {@code read0()}, which does not run until {@code validate()} is triggered.
 *
 * <p>{@link XDimDictionary#checkOverflow()} forces that read, so it reports truthfully on a cold
 * dictionary. {@link XDimDictionary#isOverflow()} deliberately does not -- it is read on the
 * MV build/incremental paths where the extra deserialization would be too costly, and where
 * changing the answer would alter the on-disk index layout of appended blocks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class XDimDictionaryOverflowReadTest {
   @Test
   void reportsOverflowOnAFreshlyReadDictionary(@TempDir Path dir) throws Exception {
      XDimDictionary read = roundTrip(dir, true);

      // the failure this guards: read() leaves overflow unassigned, so without
      // validating this is false and the MV overflow warning never fires
      assertTrue(read.checkOverflow(),
                 "overflow must survive a delayed read without an explicit access first");
   }

   @Test
   void doesNotReportOverflowForANormalDictionary(@TempDir Path dir) throws Exception {
      assertFalse(roundTrip(dir, false).checkOverflow());
   }

   /**
    * isOverflow() must stay a plain field read. Making it validate would silently re-activate
    * the overflow branch in MVBuilder.hasNext() that has never run for storage-loaded
    * dictionaries, changing the index layout of incrementally appended blocks.
    */
   @Test
   void isOverflowStaysCheapAndDoesNotForceTheRead(@TempDir Path dir) throws Exception {
      XDimDictionary read = roundTrip(dir, true);

      assertFalse(read.isOverflow(), "isOverflow() must not trigger the delayed read");
      // and once validated, both agree
      assertTrue(read.checkOverflow());
      assertTrue(read.isOverflow());
   }

   /** Write a dictionary out and read it back through the delayed-read path. */
   private static XDimDictionary roundTrip(Path dir, boolean overflow) throws Exception {
      File file = dir.resolve("dict.bin").toFile();
      ChannelProvider provider = ChannelProvider.file(file);

      XDimDictionary dict = new XDimDictionary();

      if(overflow) {
         dict.setOverflow(true);
      }

      dict.addValue("Boston");
      dict.complete();

      try(SeekableByteChannel channel = provider.newWriteChannel()) {
         dict.write(channel);
      }

      XDimDictionary read = new XDimDictionary();

      try(SeekableByteChannel channel = provider.newReadChannel()) {
         read.read(provider, channel);
      }

      return read;
   }
}
