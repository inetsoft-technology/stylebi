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

/*
 * Bug #79856 (row R5 / part B): copyDataSpace(replace=true) moves {oldOrg}/repository.xml away at
 * the start of an org id change. A registry object of the old org that is still loaded on a node
 * and saved later (a late folder add) writes the file again: the folder is neither copied to the
 * new org nor refused, and a stale {oldOrg}/repository.xml blocks reuse of the old id.
 */

import inetsoft.sree.security.OrgIdChangeGuard;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RepletRegistryLateSaveAfterRenameTest {
   /** R5: during an id change the late save is refused and does not recreate the old file. */
   @Test
   void lateSaveDuringIdChangeIsRefusedAndDoesNotRecreateFile() throws Exception {
      String orgId = "bug79856reg";
      String file = orgId + "/repository.xml";
      DataSpace space = DataSpace.getDataSpace();
      RepletRegistry registry = new RepletRegistry(orgId);
      registry.addFolder("early");
      registry.save();
      assertTrue(space.exists(null, file), "setup: registry file written");
      AtomicReference<Throwable> error = new AtomicReference<>();

      OrgIdChangeGuard.begin(orgId);

      try {
         // copyDataSpace(replace=true) moves the old org's file away
         space.delete(null, file);
         registry.addFolder("late");
         Thread t = new Thread(() -> {
            try {
               registry.save();
            }
            catch(Throwable e) {
               error.set(e);
            }
         });
         t.start();
         t.join();
      }
      finally {
         OrgIdChangeGuard.end(orgId, true);
      }

      try {
         assertInstanceOf(OrgIdChangeGuard.OrganizationChangingException.class, error.get(),
                          "the late save returned normally");
         assertFalse(space.exists(null, file), "late save recreated " + file);
      }
      finally {
         OrgIdChangeGuard.clear(orgId);
         registry.shutdown();
      }
   }
}
