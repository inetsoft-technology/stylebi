/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.SubmitVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SubmitVSAssemblyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SubmitVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private SubmitVSAScriptable submitVSAScriptable;
   private SubmitVSAssemblyInfo submitVSAssemblyInfo;
   private SubmitVSAssembly submitVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      submitVSAssembly = new SubmitVSAssembly();
      submitVSAssemblyInfo = (SubmitVSAssemblyInfo) submitVSAssembly.getVSAssemblyInfo();
      submitVSAssemblyInfo.setName("Submit1");
      viewsheet.addAssembly(submitVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      submitVSAScriptable = new SubmitVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      submitVSAScriptable.setAssembly("Submit1");
      vsaScriptable.setAssembly("Submit1");
   }

   @Test
   void testGetClassName() {
      assertEquals("SubmitVSA", submitVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      submitVSAScriptable.addProperties();
      assert submitVSAScriptable.getMember("refreshAfterSubmit") instanceof Boolean;

      //check isPublicProperty()
      String[] privatePropertys = {"hyperlink", "value", "shadow"};
      for (String property : privatePropertys) {
         assertFalse(submitVSAScriptable.isPublicProperty(property));
      }
      assertTrue(submitVSAScriptable.isPublicProperty("visible"));
   }

   /**
    * "label" is not a wired scriptable property for Submit (SubmitVSAScriptable's
    * addOutputProperties() is a no-op), so the write falls back to the expando map in
    * putMember() and must be recorded as unrecognized instead of persisting.
    */
   @Test
   void testPutMemberOnUnregisteredPropertyRecordedAsUnrecognizedAndNotApplied() {
      submitVSAScriptable.putMember("label", "ClauseTest42");

      assertEquals(List.of("label"), submitVSAScriptable.getUnrecognizedWrites());
      assertEquals("Submit", submitVSAssemblyInfo.getLabelName());
   }

   @Test
   void testPutMemberOnRegisteredPropertyNotRecordedAsUnrecognizedAndIsApplied() {
      assertTrue(submitVSAssemblyInfo.isEnabled());

      submitVSAScriptable.putMember("enabled", false);

      assertEquals(List.of(), submitVSAScriptable.getUnrecognizedWrites());
      assertFalse(submitVSAssemblyInfo.isEnabled());
   }

   /**
    * resetUnrecognizedWrites() must scope tracking to a single execution, not accumulate
    * across calls.
    */
   @Test
   void testResetUnrecognizedWritesClearsPriorTracking() {
      submitVSAScriptable.putMember("label", "ClauseTest42");
      assertEquals(List.of("label"), submitVSAScriptable.getUnrecognizedWrites());

      submitVSAScriptable.resetUnrecognizedWrites();
      assertEquals(List.of(), submitVSAScriptable.getUnrecognizedWrites());
   }

   /**
    * A genuinely ad hoc scratch var (not shadowing any real property) must still execute
    * cleanly via the same expando fallback -- this instrumentation is additive only and must
    * not turn legitimate expando usage into an error.
    */
   @Test
   void testPutMemberOnAdHocScratchVarStillSucceeds() {
      assertDoesNotThrow(() -> submitVSAScriptable.putMember("myScratchFlag", true));

      assertEquals(List.of("myScratchFlag"), submitVSAScriptable.getUnrecognizedWrites());
      assertEquals(true, submitVSAScriptable.getMember("myScratchFlag"));
   }
}