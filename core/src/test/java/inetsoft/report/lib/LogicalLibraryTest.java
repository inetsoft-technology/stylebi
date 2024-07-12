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
package inetsoft.report.lib;

import inetsoft.report.lib.logical.*;
import inetsoft.report.lib.physical.*;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LogicalLibraryTest {
   @BeforeEach
   void before() {
      libraryPermission = EnumSet.allOf(ResourceAction.class);
      permissions = new HashMap<>();
      permissions.put("allPerm", EnumSet.allOf(ResourceAction.class));
      permissions.put("renameable", EnumSet.of(ResourceAction.DELETE, ResourceAction.WRITE));
      permissions.put("readable", EnumSet.of(ResourceAction.READ));
      permissions.put("writeable", EnumSet.of(ResourceAction.WRITE));

      final AtomicBoolean init = new AtomicBoolean(true);

      final LibrarySecurity security = new LibrarySecurity() {
         @Override
         public boolean checkPermission(ResourceType type,
                                        String resource,
                                        ResourceAction action)
         {
            if(init.get()) {
               return true;
            }
            else if(type == ResourceType.ASSET) {
               return Optional.ofNullable(permissions.get(resource))
                  .orElse(EnumSet.allOf(ResourceAction.class))
                  .contains(action);
            }
            else if(type == ResourceType.LIBRARY) {
               return libraryPermission.contains(action);
            }
            else {
               throw new IllegalArgumentException("Unexpected case");
            }
         }

         @Override
         public void movePermission(ResourceType fromType,
                                    String fromResource,
                                    ResourceType toType,
                                    String toResource)
         {
            permissions.put(toResource, permissions.get(fromResource));
         }
      };

      library = new TestLibrary(security);

      library.put("allPerm", 0);
      library.put("renameable", 1);
      library.put("readable", 2);
      library.put("writeable", 3);

      library.putComment("allPerm", "comment");

      library.put("audit", 4);

      init.set(false);
   }

   @Test
   void toList() {
      final Set<String> set = new HashSet<>(library.toSecureList());
      assertTrue(set.contains("allPerm"));
      assertTrue(set.contains("readable"));
      assertFalse(set.contains("renameable"));
      assertFalse(set.contains("writeable"));
   }

   @Test
   void caseInsensitiveFindName() {
      assertEquals("readable", library.caseInsensitiveFindName("READABLE", true));
      assertEquals("allPerm", library.caseInsensitiveFindName("ALLPERM", true));
      assertNull(library.caseInsensitiveFindName("WRITEABLE", true));
      assertNull(library.caseInsensitiveFindName("RENAMEABLE", true));
   }

   @Test
   void put() {
      library.put("allPerm", 0);
      library.put("writeable", 0);
      library.put("newAsset", 0);

      assertEquals(0, library.get("newAsset").intValue());
   }

   @Test
   void putNoWritePermission() {
      assertThrows(SecurityException.class, () -> library.put("readable", 0));
   }

   @Test
   void putNoLibraryPermissionOverwrite() {
      libraryPermission.remove(ResourceAction.WRITE);
      library.put("allPerm", 0);
   }

   @Test
   void putNoLibraryPermissionNew() {
      libraryPermission.remove(ResourceAction.WRITE);
      assertThrows(SecurityException.class, () -> library.put("newAsset", 0));
   }

   @Test
   void get() {
      assertEquals(2, library.get("readable").intValue());
      assertEquals(0, library.get("allPerm").intValue());
   }

   @Test
   void getNonExistent() {
      assertNull(library.get("nonexistent"));
   }

   @Test
   void getNoReadPermission() {
      assertNull(library.get("writeable"));
   }

   @Test
   void rename() {
      library.rename("renameable", "RENAMEABLE");
      permissions.get("RENAMEABLE").add(ResourceAction.READ);
      assertEquals(1, library.get("RENAMEABLE").intValue());
   }

   @Test
   void renameNonexistent() {
      library.rename("nonexistent", "newName");
      assertNull(library.get("newName"));
   }

   @Test
   void renameWithoutDeletePermission() {
      assertThrows(SecurityException.class, () -> library.rename("writeable", "WRITEABLE"));
   }

   @Test
   void getComment() {
      assertEquals("comment", library.getComment("allPerm"));
      assertNull(library.getComment("writeable"));
      assertNull(library.getComment("nonexistent"));
   }

   @Test
   void putComment() {
      library.putComment("allPerm", "newComment");
      assertEquals("newComment", library.getComment("allPerm"));

      library.putComment("nonExistent", "newComment");
      assertNull(library.getComment("nonExistent"));
   }

   @Test
   void clear() {
      library.clear();
      assertNull(library.get("allPerm"));
      assertNull(library.getComment("allPerm"));
   }

   private static class TestLibrary extends AbstractLogicalLibrary<Integer> {
      protected TestLibrary(LibrarySecurity security) {
         super(security);
      }

      @Override
      protected LibraryAssetReader<Integer> getReader() {
         return null;
      }

      @Override
      protected LibraryAssetWriter getWriter(PhysicalLibrary library) {
         return null;
      }

      @Override
      protected ResourceType getResourceType() {
         return ResourceType.ASSET;
      }

      @Override
      protected ResourceType getResourceLibraryType() {
         return ResourceType.LIBRARY;
      }

      @Override
      protected String getEntryName() {
         return "test";
      }

      @Override
      protected int getAddedFlag() {
         return 0;
      }

      @Override
      protected int getModifiedFlag() {
         return 0;
      }

      @Override
      protected void logFailedLoad(String name, Exception ex) {
      }

      @Override
      public String getAssetPrefix() {
         return null;
      }
   }

   private LogicalLibrary<Integer> library;
   private Map<String, Set<ResourceAction>> permissions;
   private EnumSet<ResourceAction> libraryPermission;
}
