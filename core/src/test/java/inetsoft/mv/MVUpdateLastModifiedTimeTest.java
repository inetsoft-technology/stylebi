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
package inetsoft.mv;

import inetsoft.mv.data.MV;
import inetsoft.mv.data.MVStorage;
import inetsoft.test.*;
import inetsoft.storage.BlobChannel;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.BlobTransaction;
import inetsoft.uql.XMetaInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79854: {@code MV.updateLastModifiedTime(long)} must write the stamp at the real
 * lastUpdateTime offset of the MV file (the {@code shareable} string follows it), so the file
 * keeps the read-start stamp, {@code shareable} survives, and
 * {@code MVDef.updateLastUpdateTimeAfterCreate} keeps the stamp instead of falling back to now().
 * Uses the real MV / MVStorage / MVDef; the stamp and shareable are re-read from the blob.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  MVUpdateLastModifiedTimeTest.StorageConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVUpdateLastModifiedTimeTest {
   @Configuration
   static class StorageConfig {
      @Bean
      MVStorage mvStorage(BlobStorageManager blobStorageManager) {
         return new MVStorage(blobStorageManager);
      }
   }

   private static final String NAME = "mv79854";

   private MVDef saveMV(long stamp, boolean shareable) throws Exception {
      MVDef def = new MVDef();
      def.setMVName(NAME);
      def.setShareable(shareable);
      def.updateLastUpdateTime(stamp);
      MV mv = new MV(0, 0, new String[0], new String[0], new Class<?>[0], new XMetaInfo[0], def);
      String file = MVStorage.getFile(NAME);
      mv.save(file);
      MVStorage.getInstance().invalidate(file);
      return def;
   }

   private MVDef reread() throws Exception {
      String file = MVStorage.getFile(NAME);
      MVStorage.getInstance().invalidate(file);
      return MVStorage.getInstance().get(file).getDef();
   }

   @Test
   void stampSurvivesAndShareableKept() throws Exception {
      saveMV(1000L, true);
      MV loaded = MVStorage.getInstance().get(MVStorage.getFile(NAME));
      loaded.updateLastModifiedTime(5000L);

      MVDef after = reread();
      assertEquals(5000L, after.getLastUpdateTime());
      assertTrue(after.isShareable());
      assertEquals(5000L, MVDef.getStoredUpdateTime(NAME));

      // a second update to a later time is also kept
      MVStorage.getInstance().get(MVStorage.getFile(NAME)).updateLastModifiedTime(7000L);
      after = reread();
      assertEquals(7000L, after.getLastUpdateTime());
      assertTrue(after.isShareable());
   }

   @Test
   void notShareableStaysNotShareable() throws Exception {
      saveMV(1000L, false);
      MVStorage.getInstance().get(MVStorage.getFile(NAME)).updateLastModifiedTime(5000L);

      MVDef after = reread();
      assertEquals(5000L, after.getLastUpdateTime());
      assertFalse(after.isShareable());
   }

   @Test
   void afterCreateKeepsReadStartStamp() throws Exception {
      MVDef def = saveMV(1000L, true);
      MVStorage.getInstance().get(MVStorage.getFile(NAME)).updateLastModifiedTime(5000L);

      long before = System.currentTimeMillis();
      def.updateLastUpdateTimeAfterCreate(1000L, 5000L);
      assertEquals(5000L, def.getLastUpdateTime());
      assertTrue(def.getLastUpdateTime() < before);
   }

   @Test
   void afterCreateWithoutReadUsesNow() throws Exception {
      MVDef def = saveMV(1000L, true);

      long before = System.currentTimeMillis();
      def.updateLastUpdateTimeAfterCreate(1000L, 1000L);
      assertTrue(def.getLastUpdateTime() >= before);
   }

   /**
    * A file already damaged by the old updateDef (the 8 stamp bytes written over the shareable
    * chars) reads back shareable=false but keeps its 4 char string; the stamp must still land on
    * the real slot and the file must still parse.
    */
   @Test
   void damagedByOldWriteIsUpdatedCorrectly() throws Exception {
      saveMV(1000L, true);
      MVDef loaded = MVStorage.getInstance().get(MVStorage.getFile(NAME)).getDef();
      long end = loaded.getFilePosition() + loaded.getLength() + 4;

      try(BlobTransaction<MVStorage.Metadata> tx = MVStorage.getInstance().getStorage(inetsoft.sree.security.OrganizationManager.getInstance().getCurrentOrgID()).beginTransaction();
          BlobChannel channel = tx.newChannel(MVStorage.getFile(NAME), new MVStorage.Metadata()))
      {
         ByteBuffer buf = ByteBuffer.allocate(8);
         buf.putLong(1000L);
         buf.flip();
         channel.position(end - 8);

         while(buf.hasRemaining()) {
            channel.write(buf);
         }

         tx.commit();
      }

      MVDef damaged = reread();
      assertFalse(damaged.isShareable());
      assertEquals(1000L, damaged.getLastUpdateTime());
      assertEquals(12, damaged.getBytesAfterStamp());

      MVStorage.getInstance().get(MVStorage.getFile(NAME)).updateLastModifiedTime(5000L);

      MVDef after = reread();
      assertEquals(5000L, after.getLastUpdateTime());
      assertEquals(12, after.getBytesAfterStamp());
      assertEquals(5000L, MVDef.getStoredUpdateTime(NAME));

      // and a second update keeps working
      MVStorage.getInstance().get(MVStorage.getFile(NAME)).updateLastModifiedTime(8000L);
      assertEquals(8000L, reread().getLastUpdateTime());
   }
}
