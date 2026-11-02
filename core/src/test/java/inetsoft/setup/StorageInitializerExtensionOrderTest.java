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

package inetsoft.setup;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that {@link StorageInitializer} calls the setup extensions in an order that does not depend
 * on the order in which the service loader found them. The service loader follows the class path,
 * and the image's wildcard class path ({@code libs/*}) is listed in unsorted directory order that
 * differs between hosts. Bug #78731: the staging extension ran after the script extension, which
 * had already loaded its scripts from the unstaged directory.
 */
@Tag("core")
class StorageInitializerExtensionOrderTest {
   @Test
   void sortsByOrderThenClassNameRegardlessOfLoadOrder() {
      List<SetupExtension> expected = List.of(
         new ZzzStaging(), new AlphaDefault(), new ZuluDefault(), new Late());

      for(List<SetupExtension> loaded : permutations(expected)) {
         List<SetupExtension> sorted = StorageInitializer.sortExtensions(loaded);
         assertEquals(names(expected), names(sorted), "load order " + names(loaded));
      }
   }

   @Test
   void defaultOrderIsDefaultOrderConstant() {
      assertEquals(SetupExtension.DEFAULT_ORDER, new AlphaDefault().getOrder());
   }

   @Test
   void sortReturnsNewListAndLeavesInputUnchanged() {
      List<SetupExtension> loaded = List.of(new Late(), new ZzzStaging());
      List<SetupExtension> sorted = StorageInitializer.sortExtensions(loaded);
      assertEquals(List.of(Late.class.getName(), ZzzStaging.class.getName()), names(loaded));
      assertEquals(List.of(ZzzStaging.class.getName(), Late.class.getName()), names(sorted));
   }

   private static List<String> names(List<SetupExtension> extensions) {
      return extensions.stream().map(e -> e.getClass().getName()).toList();
   }

   private static List<List<SetupExtension>> permutations(List<SetupExtension> items) {
      List<List<SetupExtension>> result = new ArrayList<>();

      if(items.isEmpty()) {
         result.add(new ArrayList<>());
         return result;
      }

      for(int i = 0; i < items.size(); i++) {
         List<SetupExtension> rest = new ArrayList<>(items);
         SetupExtension first = rest.remove(i);

         for(List<SetupExtension> tail : permutations(rest)) {
            tail.add(0, first);
            result.add(tail);
         }
      }

      return result;
   }

   // Named so that class-name order alone would put it last; its order must put it first.
   static final class ZzzStaging implements SetupExtension {
      @Override
      public int getOrder() {
         return EARLIEST_ORDER;
      }
   }

   static final class AlphaDefault implements SetupExtension {
   }

   static final class ZuluDefault implements SetupExtension {
   }

   static final class Late implements SetupExtension {
      @Override
      public int getOrder() {
         return 10;
      }
   }
}
