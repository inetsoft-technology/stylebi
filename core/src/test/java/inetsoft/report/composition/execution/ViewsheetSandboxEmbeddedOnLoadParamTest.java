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
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78222: a parameter written by the wrapper's onLoad was invisible to an embedded
 * viewsheet in the viewer (and the current-view export/email that reuse the viewer's boxes),
 * but visible on the bookmark export/email paths, which seed the new root with the viewer's
 * live variable table. The root's onLoad changes are now pushed into the existing embedded
 * boxes; a value the embedded viewsheet wrote itself still wins.
 *
 * <p>Layout: the wrapper (onLoad sets p77) embeds Viewsheet1, whose Text1 shows
 * {@code parameter.p77}; the nested case embeds Viewsheet2, with its own Text1, in Viewsheet1.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxEmbeddedOnLoadParamTest {
   private static final String READ = "text = 'P=' + parameter.p77;";
   private static final String ONLOAD = "parameter.p77 = 'ONLOADW';";

   private Principal savedPrincipal;
   private final List<ViewsheetSandbox> boxes = new ArrayList<>();

   @BeforeEach
   void savePrincipal() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void cleanup() {
      boxes.forEach(ViewsheetSandbox::dispose);
      boxes.clear();
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void viewerChildSeesWrapperOnLoad() throws Exception {
      Fixture f = new Fixture().wrapperOnLoad(ONLOAD);
      ViewsheetSandbox box = f.viewer();
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
   }

   @Test
   void scaledViewerChildSeesWrapperOnLoad() throws Exception {
      Fixture f = new Fixture().wrapperOnLoad(ONLOAD);
      ViewsheetSandbox box = f.sandbox();
      box.processOnInit();
      box.processOnLoadIf();
      reset(box, f);
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
   }

   @Test
   void bookmarkWithLiveVarsChildSeesWrapperOnLoad() throws Exception {
      ViewsheetSandbox live = new Fixture().wrapperOnLoad(ONLOAD).viewer();
      Fixture g = new Fixture().wrapperOnLoad(ONLOAD);
      ViewsheetSandbox box = g.sandbox();
      box.getAssetQuerySandbox().refreshVariableTable(live.getVariableTable());
      box.processOnInit();
      reset(box, g);
      assertEquals("P=ONLOADW", g.text(box, "Viewsheet1"));
   }

   @Test
   void bookmarkWithoutLiveVarsChildSeesWrapperOnLoad() throws Exception {
      // the bookmark recipe minus the live-variable copy is the plain viewer sequence on a
      // fresh sandbox
      Fixture f = new Fixture().wrapperOnLoad(ONLOAD);
      ViewsheetSandbox box = f.viewer();
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
   }

   @Test
   void viewerRefreshKeepsValue() throws Exception {
      Fixture f = new Fixture().wrapperOnLoad(ONLOAD);
      ViewsheetSandbox box = f.viewer();
      reset(box, f);
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
   }

   @Test
   void childOnInitWriteWins() throws Exception {
      Fixture f = new Fixture().wrapperOnLoad(ONLOAD).childOnInit("parameter.p77 = 'CINIT';");
      ViewsheetSandbox box = f.viewer();
      assertEquals("P=CINIT", f.text(box, "Viewsheet1"));
   }

   @Test
   void childOnLoadWriteWins() throws Exception {
      Fixture f = new Fixture().wrapperOnLoad(ONLOAD).childOnLoad("parameter.p77 = 'CLOAD';");
      ViewsheetSandbox box = f.viewer();
      assertEquals("P=CLOAD", f.text(box, "Viewsheet1"));
   }

   @Test
   void wrapperOnInitStillReachesChild() throws Exception {
      Fixture f = new Fixture().wrapperOnInit("parameter.p77 = 'ONINITW';");
      ViewsheetSandbox box = f.viewer();
      assertEquals("P=ONINITW", f.text(box, "Viewsheet1"));
   }

   @Test
   void rootTableChangedAfterChildCreationIsNotMistakenForChildWrite() throws Exception {
      Fixture f = new Fixture().wrapperOnLoad(ONLOAD);
      ViewsheetSandbox box = f.sandbox();
      box.processOnInit();
      // creates the child without running the wrapper's onLoad
      box.reset(null, f.wrapper.getAssemblies(), new ChangedAssemblyList(), true, false, null);
      assertEquals("P=undefined", f.text(box, "Viewsheet1"));

      // the root table changes after the child copied it, then onLoad overwrites the value
      VariableTable vt = new VariableTable();
      vt.put("p77", "URLV");
      box.getAssetQuerySandbox().refreshVariableTable(vt);
      reset(box, f);
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
   }

   @Test
   void nestedChildrenSeeWrapperOnLoad() throws Exception {
      Fixture f = new Fixture().nested().wrapperOnLoad(ONLOAD);
      ViewsheetSandbox box = f.viewer();
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"));
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1.Viewsheet2"));
   }

   @Test
   void onLoadRemovedKeyIsRemovedFromChild() throws Exception {
      Fixture f = new Fixture().wrapperOnLoad("delete parameter.p77;");
      ViewsheetSandbox box = f.sandbox();
      VariableTable vt = new VariableTable();
      vt.put("p77", "SEED");
      box.getAssetQuerySandbox().refreshVariableTable(vt);
      box.processOnInit();
      // the child is created with p77=SEED, then the wrapper's onLoad removes it
      reset(box, f);
      assertEquals("P=undefined", f.text(box, "Viewsheet1"));
   }

   private static void reset(ViewsheetSandbox box, Fixture f) {
      box.reset(null, f.wrapper.getAssemblies(), new ChangedAssemblyList(), true, true, null);
   }

   private class Fixture {
      final Viewsheet wrapper = new Viewsheet();
      final Viewsheet child = new Viewsheet();

      Fixture() throws Exception {
         setWorksheet(wrapper);
         setWorksheet(child);
         wrapper.setEntry(entry("wrapper"));
         child.setEntry(entry("child"));
         wrapper.getViewsheetInfo().setScriptEnabled(true);
         child.getViewsheetInfo().setScriptEnabled(true);
         child.createVSAssembly("Viewsheet1");
         child.addAssembly(text(child));
         wrapper.addAssembly(child);
      }

      Fixture nested() throws Exception {
         Viewsheet grand = new Viewsheet();
         setWorksheet(grand);
         grand.setEntry(entry("grand"));
         grand.getViewsheetInfo().setScriptEnabled(true);
         grand.createVSAssembly("Viewsheet2");
         grand.addAssembly(text(grand));
         child.addAssembly(grand);
         return this;
      }

      Fixture wrapperOnLoad(String s) {
         wrapper.getViewsheetInfo().setOnLoad(s);
         return this;
      }

      Fixture wrapperOnInit(String s) {
         wrapper.getViewsheetInfo().setOnInit(s);
         return this;
      }

      Fixture childOnInit(String s) {
         child.getViewsheetInfo().setOnInit(s);
         return this;
      }

      Fixture childOnLoad(String s) {
         child.getViewsheetInfo().setOnLoad(s);
         return this;
      }

      ViewsheetSandbox sandbox() {
         ViewsheetSandbox box = new ViewsheetSandbox(
            wrapper, AbstractSheet.SHEET_RUNTIME_MODE, null, false, wrapper.getEntry());
         boxes.add(box);
         return box;
      }

      ViewsheetSandbox viewer() {
         ViewsheetSandbox box = sandbox();
         box.processOnInit();
         reset(box, this);
         return box;
      }

      String text(ViewsheetSandbox box, String name) {
         ViewsheetSandbox cbox = box.getSandbox(name);
         TextVSAssembly t = (TextVSAssembly) cbox.getViewsheet().getAssembly("Text1");
         return ((TextVSAssemblyInfo) t.getVSAssemblyInfo()).getText();
      }

      private TextVSAssembly text(Viewsheet vs) {
         TextVSAssembly text = new TextVSAssembly(vs, "Text1");
         text.getVSAssemblyInfo().setScriptEnabled(true);
         text.getVSAssemblyInfo().setScript(READ);
         return text;
      }
   }

   private static void setWorksheet(Viewsheet vs) throws Exception {
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, new Worksheet());
   }

   private static AssetEntry entry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/ViewsheetSandboxEmbeddedOnLoadParamTest/" + name, null,
         Organization.getDefaultOrganizationID());
   }
}
