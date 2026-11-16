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
package inetsoft.sree;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #81192: a folder whose name contains XML special characters must keep its favorites when
 * the registry is saved, and a login name with such characters must not corrupt the file.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RepletRegistryFolderFavoritesEscapeTest {
   @Test
   void favoritesOfSpecialNamedFolderSurviveSave() throws Exception {
      String org = "test81192_folder";
      String folder = "A&B<C>\"D'E";
      String user = "u&1~;~host-org";
      RepletRegistry registry = new RepletRegistry(org);
      registry.addFolder(folder);
      registry.addFolderFavoritesUser(folder, user);
      registry.save();
      registry.shutdown();

      RepletRegistry stored = new RepletRegistry(org);
      assertEquals(user, stored.getFolderFavoritesUser(folder));
      stored.shutdown();
   }

   @Test
   void specialCharactersInUserDoNotCorruptFile() throws Exception {
      String org = "test81192_user";
      String folder = "plain";
      String user = "a<b&c\"d~;~host-org";
      RepletRegistry registry = new RepletRegistry(org);
      registry.addFolder(folder);
      registry.addFolderFavoritesUser(folder, user);
      registry.save();
      registry.shutdown();

      RepletRegistry stored = new RepletRegistry(org);
      assertEquals(user, stored.getFolderFavoritesUser(folder));
      stored.shutdown();
   }
}
