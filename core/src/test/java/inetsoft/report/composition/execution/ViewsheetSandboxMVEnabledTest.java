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
package inetsoft.report.composition.execution;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ViewsheetSandbox#isMVEnabled()} decides whether a viewsheet reads its materialized
 * views. The meta data option of a viewsheet ("meta") is for designing it without data: in the
 * composer (design mode) it turns the MV off, at runtime it doesn't. An MV can also be turned off
 * for the sandbox ({@code setMVDisabled}), and the detail data of a component only comes from the
 * MV with {@code mv.detail.data=true}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxMVEnabledTest {
   private String oldDetailData;

   @BeforeEach
   void setUp() {
      oldDetailData = SreeEnv.getProperty("mv.detail.data");
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("mv.detail.data", oldDetailData);
   }

   // at runtime the MV is used also with the meta data option
   @Test
   void runtimeWithMetaDataUsesTheMV() {
      assertTrue(sandbox(AbstractSheet.SHEET_RUNTIME_MODE, true).isMVEnabled());
      assertTrue(sandbox(AbstractSheet.SHEET_RUNTIME_MODE, false).isMVEnabled());
   }

   // in design mode the meta data option turns the MV off, without it the MV is used
   @Test
   void designModeUsesTheMVOnlyWithoutMetaData() {
      assertFalse(sandbox(AbstractSheet.SHEET_DESIGN_MODE, true).isMVEnabled());
      assertTrue(sandbox(AbstractSheet.SHEET_DESIGN_MODE, false).isMVEnabled());
   }

   // a sandbox with MV processing disabled doesn't use it, until it is enabled again
   @Test
   void disabledSandboxDoesNotUseTheMV() {
      ViewsheetSandbox box = sandbox(AbstractSheet.SHEET_RUNTIME_MODE, false);

      box.setMVDisabled(true);
      assertTrue(box.isMVDisabled());
      assertFalse(box.isMVEnabled());
      assertFalse(box.isMVEnabled(false));

      box.setMVDisabled(false);
      assertTrue(box.isMVEnabled());
   }

   // the detail data comes from the MV only with mv.detail.data=true (the default), and still
   // not in design mode with the meta data option
   @Test
   void detailDataNeedsMVDetailData() {
      ViewsheetSandbox box = sandbox(AbstractSheet.SHEET_RUNTIME_MODE, false);

      SreeEnv.setProperty("mv.detail.data", "false");
      assertTrue(box.isMVEnabled(false));
      assertFalse(box.isMVEnabled(true));

      SreeEnv.setProperty("mv.detail.data", "true");
      assertTrue(box.isMVEnabled(true));
      assertFalse(sandbox(AbstractSheet.SHEET_DESIGN_MODE, true).isMVEnabled(true));

      // not set: defaults.properties has mv.detail.data=true
      SreeEnv.setProperty("mv.detail.data", null);
      assertTrue(box.isMVEnabled(true));
   }

   private static ViewsheetSandbox sandbox(int mode, boolean metaData) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setMetadata(metaData);
      return new ViewsheetSandbox(vs, mode, null, false, null);
   }
}
