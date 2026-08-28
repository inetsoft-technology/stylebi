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
package inetsoft.web.wiz.controller;

import inetsoft.uql.tabular.*;

import java.io.File;

/**
 * Minimal, REAL (non-mock) local-filesystem browsable connector, mirroring
 * {@code ServerFileQuery}'s own {@code getFileFolder()} shape ({@code relativeTo}/
 * {@code acceptTypes} editor properties on a {@code java.io.File}-typed property) closely enough
 * to exercise {@code WizTabularController}'s pre-existing {@code findFileView}/{@code collect()}
 * path without depending on the {@code inetsoft-serverfile} connector module (core does not depend
 * on it). A TOP-LEVEL public class, not a nested one: {@code TabularUtil.callEditorMethods}
 * resolves {@code relativeTo} by reflectively invoking {@code getRootFolder()} from a different
 * package, which silently fails (caught, logged, left null) against a non-public declaring class.
 */
@View(vertical = false, value = { @View1("fileFolder") })
public class FakeLocalFileQuery extends SelectableTabularQuery {
   public FakeLocalFileQuery() {
      super("FakeLocalFile");
   }

   @Property(label = "File/Folder", required = true)
   @PropertyEditor(editorProperties = {
      @EditorProperty(name = "relativeTo", method = "getRootFolder"),
      @EditorProperty(name = "acceptTypes", value = ".txt,.csv,.xls,.xlsx")
   })
   public File getFileFolder() {
      return fileFolder;
   }

   public void setFileFolder(File fileFolder) {
      this.fileFolder = fileFolder;
   }

   public String getRootFolder() {
      return rootFolder;
   }

   public void setRootFolder(String rootFolder) {
      this.rootFolder = rootFolder;
   }

   @Override
   protected ColumnDefinition[] loadColumns() {
      return new ColumnDefinition[0];
   }

   private File fileFolder;
   private String rootFolder;
}
