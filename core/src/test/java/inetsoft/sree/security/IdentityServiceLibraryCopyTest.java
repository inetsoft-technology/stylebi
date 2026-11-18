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
package inetsoft.sree.security;

import inetsoft.report.LibManager;
import inetsoft.report.lib.physical.PhysicalLibraryEntry;
import inetsoft.report.lib.physical.StorageLibrary;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81328: cloning an organization or changing its id copies the {@code <org>__library} blob
 * storage. File entries (scripts, table styles) were written with an empty
 * {@code LibManager.Metadata}, which lost the comment and the comment properties (comment,
 * created, modified, createdBy, modifiedBy). Directory entries already kept their metadata.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityServiceLibraryCopyTest {
   private static final String SRC = "b81328a";
   private static final String DST = "b81328b";

   @Autowired
   BlobStorageManager blobStorageManager;

   @Test
   void fileAndDirectoryMetadataSurviveLibraryCopy() throws Exception {
      Properties script = props("script comment", "123", "456", "creator", "modifier");
      Properties style = props(null, "11", "22", "styleCreator", "styleModifier");
      Properties dir = props("folder comment", "7", "8", "dirCreator", "dirModifier");

      try(BlobStorage<LibManager.Metadata> src =
             blobStorageManager.getStorage(SRC + "__library", false);
          BlobStorage<LibManager.Metadata> dst =
             blobStorageManager.getStorage(DST + "__library", false))
      {
         StorageLibrary library = new StorageLibrary(src);
         write(library, "script/cl_fn", script, "var x = 1;");
         write(library, "tablestyle/folder/", dir, null);
         write(library, "tablestyle/folder/st1", style, "<style/>");
         library.close();

         invokeUpdateLibraryStorage(SRC, DST);

         for(String path : new String[] { "script/cl_fn", "tablestyle/folder/st1",
                                           "tablestyle/folder" })
         {
            LibManager.Metadata expected = src.getMetadata(path);
            LibManager.Metadata actual = dst.getMetadata(path);
            assertNotNull(expected, path);
            assertNotNull(actual, path);
            assertEquals(expected, actual, path);
            assertEquals(expected.isDirectory(), actual.isDirectory(), path);
         }

         LibManager.Metadata actual = dst.getMetadata("script/cl_fn");
         assertEquals("script comment", actual.getComment());
         assertEquals("456", actual.getCommentProperties().getProperty("modified"));
         assertEquals("modifier", actual.getCommentProperties().getProperty("modifiedBy"));

         // content unchanged
         try(var in = dst.getInputStream("script/cl_fn")) {
            assertEquals("var x = 1;", new String(in.readAllBytes(), StandardCharsets.UTF_8));
         }

         // the properties are copied, not shared
         Properties srcProps = src.getMetadata("script/cl_fn").getCommentProperties();
         Properties dstProps = actual.getCommentProperties();
         assertNotSame(srcProps, dstProps);
         dstProps.setProperty("comment", "changed");
         assertEquals("script comment", src.getMetadata("script/cl_fn")
            .getCommentProperties().getProperty("comment"));
      }
      finally {
         // the SreeHome is discarded after the class; nothing else to clean up
      }
   }

   private void invokeUpdateLibraryStorage(String from, String to) throws Exception {
      IdentityService service = new IdentityService(
         null, null, null, null, blobStorageManager, null, null, null, null, null, null, null,
         null, null, Optional.empty(), null, null, null, null, null, null, null, null, null, null,
         null, null, null, Optional.empty());
      Method method = IdentityService.class.getDeclaredMethod(
         "updateLibraryStorage", String.class, String.class, boolean.class);
      method.setAccessible(true);
      method.invoke(service, from, to, true);
   }

   private static void write(StorageLibrary library, String path, Properties props, String body)
      throws Exception
   {
      PhysicalLibraryEntry entry = library.createEntry(path, props);

      if(body != null) {
         try(OutputStream out = entry.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
         }
      }
   }

   private static Properties props(String comment, String created, String modified,
                                   String createdBy, String modifiedBy)
   {
      Properties p = new Properties();

      if(comment != null) {
         p.setProperty("comment", comment);
      }

      p.setProperty("created", created);
      p.setProperty("modified", modified);
      p.setProperty("createdBy", createdBy);
      p.setProperty("modifiedBy", modifiedBy);
      return p;
   }
}
