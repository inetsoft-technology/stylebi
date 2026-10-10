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
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Bug #78222 test fixture: a wrapper viewsheet embedding the child Viewsheet1, whose Text1 shows
 * {@code parameter.p77}. Optionally the wrapper has its own root-level Text ("canvas" variant)
 * and the child embeds Viewsheet2 (nested). Shared by the sandbox and the export/email service
 * tests.
 */
public class EmbeddedOnLoadFixture {
   public static final String READ = "text = 'P=' + parameter.p77;";
   public static final String ONLOAD = "parameter.p77 = 'ONLOADW';";

   public final Viewsheet wrapper = new Viewsheet();
   public final Viewsheet child = new Viewsheet();
   public final List<ViewsheetSandbox> boxes = new ArrayList<>();

   public EmbeddedOnLoadFixture() throws Exception {
      setWorksheet(wrapper);
      setWorksheet(child);
      wrapper.setEntry(entry("wrapper"));
      child.setEntry(entry("child"));
      wrapper.getViewsheetInfo().setScriptEnabled(true);
      child.getViewsheetInfo().setScriptEnabled(true);
      child.createVSAssembly("Viewsheet1");
      child.addAssembly(text(child, "Text1", READ));
      wrapper.addAssembly(child);
   }

   /** The canvas variant: a Text directly on the wrapper, next to the embedded child. */
   public EmbeddedOnLoadFixture canvas() {
      wrapper.addAssembly(text(wrapper, "RootText", "text = 'R=' + parameter.p77;"));
      return this;
   }

   /** Viewsheet2, with its own Text1, embedded in Viewsheet1. */
   public EmbeddedOnLoadFixture nested() throws Exception {
      Viewsheet grand = new Viewsheet();
      setWorksheet(grand);
      grand.setEntry(entry("grand"));
      grand.getViewsheetInfo().setScriptEnabled(true);
      grand.createVSAssembly("Viewsheet2");
      grand.addAssembly(text(grand, "Text1", READ));
      child.addAssembly(grand);
      return this;
   }

   public EmbeddedOnLoadFixture wrapperOnLoad(String script) {
      wrapper.getViewsheetInfo().setOnLoad(script);
      return this;
   }

   public EmbeddedOnLoadFixture wrapperOnInit(String script) {
      wrapper.getViewsheetInfo().setOnInit(script);
      return this;
   }

   public EmbeddedOnLoadFixture childOnInit(String script) {
      child.getViewsheetInfo().setOnInit(script);
      return this;
   }

   public EmbeddedOnLoadFixture childOnLoad(String script) {
      child.getViewsheetInfo().setOnLoad(script);
      return this;
   }

   public ViewsheetSandbox sandbox() {
      ViewsheetSandbox box = new ViewsheetSandbox(
         wrapper, AbstractSheet.SHEET_RUNTIME_MODE, null, false, wrapper.getEntry());
      boxes.add(box);
      return box;
   }

   /** The viewer open sequence: onInit, then a reset that runs onLoad. */
   public ViewsheetSandbox viewer() {
      ViewsheetSandbox box = sandbox();
      box.processOnInit();
      reset(box);
      return box;
   }

   public void reset(ViewsheetSandbox box) {
      box.reset(null, wrapper.getAssemblies(), new ChangedAssemblyList(), true, true, null);
   }

   /** The text shown by Text1 of the embedded viewsheet with the specified name. */
   public String text(ViewsheetSandbox box, String name) {
      ViewsheetSandbox cbox = box.getSandbox(name);
      return textOf(cbox.getViewsheet(), "Text1");
   }

   /** The text shown by the wrapper's own RootText (canvas variant). */
   public String rootText(ViewsheetSandbox box) {
      return textOf(box.getViewsheet(), "RootText");
   }

   public void dispose() {
      boxes.forEach(ViewsheetSandbox::dispose);
      boxes.clear();
   }

   private static String textOf(Viewsheet vs, String name) {
      TextVSAssembly t = (TextVSAssembly) vs.getAssembly(name);
      return ((TextVSAssemblyInfo) t.getVSAssemblyInfo()).getText();
   }

   private static TextVSAssembly text(Viewsheet vs, String name, String script) {
      TextVSAssembly text = new TextVSAssembly(vs, name);
      text.getVSAssemblyInfo().setScriptEnabled(true);
      text.getVSAssemblyInfo().setScript(script);
      return text;
   }

   private static void setWorksheet(Viewsheet vs) throws Exception {
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, new Worksheet());
   }

   private static AssetEntry entry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/EmbeddedOnLoadFixture/" + name, null, Organization.getDefaultOrganizationID());
   }
}
