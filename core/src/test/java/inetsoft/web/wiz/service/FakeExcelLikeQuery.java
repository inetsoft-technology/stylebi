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
package inetsoft.web.wiz.service;

import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.PropertyEditor;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

import java.io.File;

/**
 * Minimal, REAL (non-mock) stand-in for a path-addressed tabular query such as
 * {@code ServerFileQuery} -- a {@code java.io.File} property ({@code fileFolder}) resolved
 * against {@code getRootFolder()} (reached by name, not {@code @Property}, exactly like the real
 * class), plus {@code isExcel()}/{@code getExcelSheetNames()} (also reached by name) and an
 * {@code excelSheet} String property whose {@code @PropertyEditor(dependsOn = {"fileFolder"},
 * tagsMethod = "getExcelSheetNames")} matches the capability 5b sheet-property heuristic exactly.
 *
 * <p>{@code isExcel()}/the sheet list are test-controlled directly (not derived from real file
 * content) via {@link #setExcelForTest}/{@link #setSheetNamesForTest} -- this fixture exists to
 * test the AMBIGUITY REFUSAL logic, not a real spreadsheet parser.</p>
 *
 * <p>{@code @View} is REQUIRED -- see {@link FakeNamedConnectorQuery}'s own doc for why.</p>
 */
@View(vertical = true, value = { @View1("fileFolder"), @View1("excelSheet") })
public class FakeExcelLikeQuery extends TabularQuery {
   public FakeExcelLikeQuery() {
      super("FakeExcelLike");
   }

   @Property(label = "File Folder", required = true)
   public File getFileFolder() {
      return fileFolder;
   }

   public void setFileFolder(File fileFolder) {
      this.fileFolder = fileFolder;
   }

   @Property(label = "Sheet")
   @PropertyEditor(dependsOn = {"fileFolder"}, tagsMethod = "getExcelSheetNames")
   public String getExcelSheet() {
      return excelSheet;
   }

   public void setExcelSheet(String excelSheet) {
      this.excelSheet = excelSheet;
   }

   // Non-@Property reflection surface, mirroring ServerFileQuery exactly.

   public String getRootFolder() {
      return rootFolder;
   }

   public boolean isExcel() {
      return excel;
   }

   public String[] getExcelSheetNames() {
      return sheetNames;
   }

   // Test-only setup, not @Property -- never reachable through the query contract.

   public void setRootFolderForTest(String rootFolder) {
      this.rootFolder = rootFolder;
   }

   public void setExcelForTest(boolean excel) {
      this.excel = excel;
   }

   public void setSheetNamesForTest(String[] sheetNames) {
      this.sheetNames = sheetNames;
   }

   private File fileFolder;
   private String excelSheet;
   private String rootFolder;
   private boolean excel;
   private String[] sheetNames = new String[0];
}
