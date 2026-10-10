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

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static inetsoft.report.composition.execution.EmbeddedOnLoadFixture.ONLOAD;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78222: a parameter written by the wrapper's onLoad was invisible to an embedded
 * viewsheet in the viewer (and the current-view export/email that reuse the viewer's boxes),
 * but visible on the bookmark export/email paths, which seed the new root with the viewer's
 * live variable table. The root's onLoad changes are now pushed into the existing embedded
 * boxes; a value the embedded viewsheet wrote itself still wins.
 *
 * <p>Layout: see {@link EmbeddedOnLoadFixture}. The canvas case adds a Text directly on the
 * wrapper, next to the embedded child (sheet {@code wrappercanvas} of the report).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxEmbeddedOnLoadParamTest {
   private Principal savedPrincipal;
   private EmbeddedOnLoadFixture f;

   @BeforeEach
   void savePrincipal() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      f = new EmbeddedOnLoadFixture();
   }

   @AfterEach
   void cleanup() {
      f.dispose();
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void viewerChildSeesWrapperOnLoad() {
      ViewsheetSandbox box = f.wrapperOnLoad(ONLOAD).viewer();
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
   }

   @Test
   void canvasChildSeesWrapperOnLoadAndRootTextIsUnaffected() {
      ViewsheetSandbox box = f.canvas().wrapperOnLoad(ONLOAD).viewer();
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
      assertEquals("R=ONLOADW", f.rootText(box));

      f.reset(box);
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
      assertEquals("R=ONLOADW", f.rootText(box));
   }

   @Test
   void canvasScaledViewerChildSeesWrapperOnLoad() throws Exception {
      f.canvas().wrapperOnLoad(ONLOAD);
      ViewsheetSandbox box = f.sandbox();
      box.processOnInit();
      box.processOnLoadIf();
      f.reset(box);
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
      assertEquals("R=ONLOADW", f.rootText(box));
   }

   @Test
   void canvasBookmarkWithLiveVarsChildSeesWrapperOnLoad() throws Exception {
      ViewsheetSandbox live = f.canvas().wrapperOnLoad(ONLOAD).viewer();
      EmbeddedOnLoadFixture g = new EmbeddedOnLoadFixture().canvas().wrapperOnLoad(ONLOAD);

      try {
         ViewsheetSandbox box = g.sandbox();
         box.getAssetQuerySandbox().refreshVariableTable(live.getVariableTable());
         box.processOnInit();
         g.reset(box);
         assertEquals("P=ONLOADW", g.text(box, "Viewsheet1"));
         assertEquals("R=ONLOADW", g.rootText(box));
      }
      finally {
         g.dispose();
      }
   }

   @Test
   void scaledViewerChildSeesWrapperOnLoad() throws Exception {
      ViewsheetSandbox box = f.wrapperOnLoad(ONLOAD).sandbox();
      box.processOnInit();
      box.processOnLoadIf();
      f.reset(box);
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
   }

   @Test
   void bookmarkWithLiveVarsChildSeesWrapperOnLoad() throws Exception {
      ViewsheetSandbox live = f.wrapperOnLoad(ONLOAD).viewer();
      EmbeddedOnLoadFixture g = new EmbeddedOnLoadFixture().wrapperOnLoad(ONLOAD);

      try {
         ViewsheetSandbox box = g.sandbox();
         box.getAssetQuerySandbox().refreshVariableTable(live.getVariableTable());
         box.processOnInit();
         g.reset(box);
         assertEquals("P=ONLOADW", g.text(box, "Viewsheet1"));
      }
      finally {
         g.dispose();
      }
   }

   @Test
   void viewerRefreshKeepsValue() {
      ViewsheetSandbox box = f.wrapperOnLoad(ONLOAD).viewer();
      f.reset(box);
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
   }

   @Test
   void childOnInitWriteWins() {
      ViewsheetSandbox box = f.wrapperOnLoad(ONLOAD).childOnInit("parameter.p77 = 'CINIT';")
         .viewer();
      assertEquals("P=CINIT", f.text(box, "Viewsheet1"));
   }

   @Test
   void childOnLoadWriteWins() {
      ViewsheetSandbox box = f.wrapperOnLoad(ONLOAD).childOnLoad("parameter.p77 = 'CLOAD';")
         .viewer();
      assertEquals("P=CLOAD", f.text(box, "Viewsheet1"));
   }

   @Test
   void wrapperOnInitStillReachesChild() {
      ViewsheetSandbox box = f.wrapperOnInit("parameter.p77 = 'ONINITW';").viewer();
      assertEquals("P=ONINITW", f.text(box, "Viewsheet1"));
   }

   @Test
   void rootTableChangedAfterChildCreationIsNotMistakenForChildWrite() throws Exception {
      ViewsheetSandbox box = f.wrapperOnLoad(ONLOAD).sandbox();
      box.processOnInit();
      // creates the child without running the wrapper's onLoad
      box.reset(null, f.wrapper.getAssemblies(), new ChangedAssemblyList(), true, false, null);
      assertEquals("P=undefined", f.text(box, "Viewsheet1"));

      // the root table changes after the child copied it, then onLoad overwrites the value
      VariableTable vt = new VariableTable();
      vt.put("p77", "URLV");
      box.getAssetQuerySandbox().refreshVariableTable(vt);
      f.reset(box);
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
   }

   @Test
   void nestedChildrenSeeWrapperOnLoad() throws Exception {
      ViewsheetSandbox box = f.nested().wrapperOnLoad(ONLOAD).viewer();
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1.Viewsheet2"));
   }

   @Test
   void onLoadRemovedKeyIsRemovedFromChild() throws Exception {
      ViewsheetSandbox box = f.wrapperOnLoad("delete parameter.p77;").sandbox();
      VariableTable vt = new VariableTable();
      vt.put("p77", "SEED");
      box.getAssetQuerySandbox().refreshVariableTable(vt);
      box.processOnInit();
      // the child is created with p77=SEED, then the wrapper's onLoad removes it
      f.reset(box);
      assertEquals("P=undefined", f.text(box, "Viewsheet1"));
   }
}
