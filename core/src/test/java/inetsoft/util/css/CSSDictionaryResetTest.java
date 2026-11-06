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
package inetsoft.util.css;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.DataChangeEvent;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CSSDictionaryResetTest {
   private static final String DIR = "portal";
   private static final String FILE = "reset-test.css";

   @AfterEach
   void cleanup() throws Exception {
      CSSDictionary.resetDictionaryCache();
      DataSpace space = DataSpace.getDataSpace();

      if(space.exists(DIR, FILE)) {
         space.delete(DIR, FILE);
      }
   }

   @Test
   void aDroppedDictionaryDoesNotRecreateTheDeletedFile() throws Exception {
      DataSpace space = DataSpace.getDataSpace();
      space.withOutputStream(DIR, FILE,
                             out -> out.write("Chart { padding: 5px; }".getBytes(
                                StandardCharsets.UTF_8)));
      CSSDictionary dict = CSSDictionary.getDictionary(DIR, FILE, false);

      CSSDictionary.resetDictionaryCache();
      space.delete(DIR, FILE);
      dict.changeListener.dataChanged(new DataChangeEvent(DIR, FILE, System.currentTimeMillis()));

      assertFalse(space.exists(DIR, FILE), "a dropped dictionary must not rewrite the file");
      assertNotSame(dict, CSSDictionary.getDictionary(DIR, FILE, false));
   }
}
