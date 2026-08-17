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
package inetsoft.uql.viewsheet;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractVSAssemblyVizMarkTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
   }

   @Test
   void theTwoArgConstructorTakesTheHostsMark() {
      Viewsheet host = new Viewsheet();
      host.getVSAssemblyInfo().setVizMark(VizMark.MODERN_DARK);

      TextVSAssembly assembly = new TextVSAssembly(host, "Text1");

      assertEquals(VizMark.MODERN_DARK, assembly.getVSAssemblyInfo().getVizMark(),
                   "a new assembly inherits the dashboard it is created on");
   }

   @Test
   void theTwoArgConstructorTakesTheHostsAbsenceOfAMark() {
      // adding an object to an old dashboard in a modern product must not modernize the object -
      // the gate is open here so a constructor that reads the gate instead of the host fails this
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      Viewsheet host = new Viewsheet();
      host.getVSAssemblyInfo().setVizMark(null);

      TextVSAssembly assembly = new TextVSAssembly(host, "Text1");

      assertNull(assembly.getVSAssemblyInfo().getVizMark(),
                 "the host's absence of a mark is inherited as firmly as a mark is");
   }

   @Test
   void theNoArgConstructorPlusSetViewsheetDoesNotStamp() {
      // the parse funnel's shape: construct with no args, then attach - it must stay untouched,
      // which is what keeps a loaded assembly from picking up a mark it never had
      Viewsheet host = new Viewsheet();
      host.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      TextVSAssembly assembly = new TextVSAssembly();
      assembly.setViewsheet(host);

      assertNull(assembly.getVSAssemblyInfo().getVizMark(),
                 "only the two-arg constructor stamps; attaching a viewsheet later must not");
   }

   @Test
   void anEmbeddedViewsheetKeepsItsOwnMarkRegardlessOfTheHost() {
      // Viewsheet extends AbstractSheet, not AbstractVSAssembly, so this constructor never runs
      // for it; an embedded viewsheet's mark comes only from its own no-arg construction, stamped
      // from the gate independently of whatever host it is later attached to
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      Viewsheet embedded = new Viewsheet();
      assertEquals(VizMark.MODERN_LIGHT, embedded.getVSAssemblyInfo().getVizMark());

      Viewsheet parent = new Viewsheet();
      parent.getVSAssemblyInfo().setVizMark(null);
      embedded.setViewsheet(parent);

      assertEquals(VizMark.MODERN_LIGHT, embedded.getVSAssemblyInfo().getVizMark(),
                   "attaching to a legacy host must not retroactively unmark an embedded viewsheet");
   }

   @Test
   void aFormatResetOnAnExistingAssemblyNeverClearsItsMark() {
      // a modern assembly pasted into a legacy dashboard must keep its mark across a format
      // reset - this fails if the stamp is ever moved into initDefaultFormat
      Viewsheet host = new Viewsheet();
      host.getVSAssemblyInfo().setVizMark(null);
      TextVSAssembly assembly = new TextVSAssembly(host, "Text1");
      assembly.getVSAssemblyInfo().setVizMark(VizMark.MODERN_DARK);

      assembly.initDefaultFormat();

      assertEquals(VizMark.MODERN_DARK, assembly.getVSAssemblyInfo().getVizMark(),
                   "a format re-init must never fall back to the host's mark");
   }

   @Test
   void aMockedHostWithNoAssemblyInfoStampsNoMark() {
      // a real Viewsheet always has an info; a mocked one returns null for getVSAssemblyInfo(),
      // which must read as an absent mark rather than NPE
      Viewsheet host = mock(Viewsheet.class);

      TextVSAssembly assembly = new TextVSAssembly(host, "Text1");

      assertNull(assembly.getVSAssemblyInfo().getVizMark(),
                 "a host whose provenance can't be read is treated as unmarked");
   }
}
