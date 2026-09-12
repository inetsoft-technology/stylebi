/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.sree.internal.sync;

import inetsoft.util.dep.*;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link QueryDependenciesFinder}.
 *
 * <p>The public entry point {@code collectDependencies(File, ...)} requires file I/O and an
 * XML parsing helper that delegates to actual files on disk, so those paths are not unit-tested
 * here. Instead, the tests focus on the static helper methods that perform string/name processing:
 * <ul>
 *   <li>{@code getFileName(File, Map)} — maps a file's base name through a names map</li>
 *   <li>{@code getQueryName(File, Map)} — extracts the query name from a mapped file name</li>
 *   <li>{@code collectDependenciesForAutoDrills} — populates the dependencies map from an
 *       in-memory DOM element, avoiding all I/O</li>
 * </ul>
 */
class QueryDependenciesFinderTest {

   // ---- Helper: build a minimal DOM document ----

   private Document newDocument() throws Exception {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
   }

   // ---- getFileName ----

   @Test
   void getFileName_keyPresent_returnsValue() {
      File file = new File("/tmp/abc123.dat");
      Map<String, String> names = new HashMap<>();
      names.put("abc123.dat", "XQUERY^datasource^myQuery");
      assertEquals("XQUERY^datasource^myQuery",
         QueryDependenciesFinder.getFileName(file, names));
   }

   @Test
   void getFileName_keyAbsent_returnsNull() {
      File file = new File("/tmp/unknown.dat");
      Map<String, String> names = new HashMap<>();
      assertNull(QueryDependenciesFinder.getFileName(file, names));
   }

   @Test
   void getFileName_usesBaseNameNotFullPath() {
      File file = new File("/some/deep/dir/myfile.dat");
      Map<String, String> names = new HashMap<>();
      names.put("myfile.dat", "WORKSHEET^path");
      assertEquals("WORKSHEET^path",
         QueryDependenciesFinder.getFileName(file, names));
   }

   // ---- getQueryName ----

   @Test
   void getQueryName_xqueryFileWithCaret_returnsLastSegment() {
      File file = new File("/tmp/q.dat");
      Map<String, String> names = new HashMap<>();
      // Format: XQUERY^datasource^queryName
      names.put("q.dat", XQueryAsset.XQUERY + "^datasource^salesQuery");
      String result = QueryDependenciesFinder.getQueryName(file, names);
      assertEquals("salesQuery", result);
   }

   @Test
   void getQueryName_xqueryFileWithNoCaret_returnsNull() {
      File file = new File("/tmp/q.dat");
      Map<String, String> names = new HashMap<>();
      // No caret in mapped name → idx == -1 → null
      names.put("q.dat", XQueryAsset.XQUERY + "noCaret");
      String result = QueryDependenciesFinder.getQueryName(file, names);
      assertNull(result);
   }

   @Test
   void getQueryName_nonXqueryFile_returnsNull() {
      File file = new File("/tmp/ws.dat");
      Map<String, String> names = new HashMap<>();
      names.put("ws.dat", WorksheetAsset.WORKSHEET + "^some^path");
      String result = QueryDependenciesFinder.getQueryName(file, names);
      assertNull(result);
   }

   @Test
   void getQueryName_fileNotInNames_returnsNull() {
      File file = new File("/tmp/missing.dat");
      Map<String, String> names = new HashMap<>();
      String result = QueryDependenciesFinder.getQueryName(file, names);
      assertNull(result);
   }

   // ---- Asset type prefix constants ----
   // The private getAssetType() is driven by these prefix strings; confirm constants
   // that the method uses are correct so any refactor would break tests.

   @Test
   void assetTypeConstant_xquery() {
      assertEquals("XQUERY", XQueryAsset.XQUERY);
   }

   @Test
   void assetTypeConstant_xlogicalModel() {
      assertEquals("XLOGICALMODEL", XLogicalModelAsset.XLOGICALMODEL);
   }

   @Test
   void assetTypeConstant_worksheet() {
      assertEquals("WORKSHEET", WorksheetAsset.WORKSHEET);
   }

   @Test
   void assetTypeConstant_viewsheet() {
      assertEquals("VIEWSHEET", ViewsheetAsset.VIEWSHEET);
   }

   @Test
   void assetTypeConstant_xdatasource() {
      assertEquals("XDATASOURCE", XDataSourceAsset.XDATASOURCE);
   }

   // ---- collectDependenciesForAutoDrills ----
   // This method accepts a DOM Element and populates dependenciesMap; the DOM is built
   // entirely in-memory with no file I/O.

   @Test
   void collectDependenciesForAutoDrills_noSubqueryNodes_leavesMapEmpty() throws Exception {
      Document doc = newDocument();
      Element root = doc.createElement("root");
      doc.appendChild(root);

      Map<String, List<String>> deps = new HashMap<>();
      QueryDependenciesFinder.collectDependenciesForAutoDrills("file.dat", root, deps);

      assertTrue(deps.isEmpty());
   }

   @Test
   void collectDependenciesForAutoDrills_subqueryWithQname_addsEntry() throws Exception {
      // Build: <root><XDrillInfo><drillPath><subquery qname="salesQuery"/></drillPath></XDrillInfo></root>
      Document doc = newDocument();
      Element root = doc.createElement("root");
      Element drillInfo = doc.createElement("XDrillInfo");
      Element drillPath = doc.createElement("drillPath");
      Element subquery = doc.createElement("subquery");
      subquery.setAttribute("qname", "salesQuery");

      drillPath.appendChild(subquery);
      drillInfo.appendChild(drillPath);
      root.appendChild(drillInfo);
      doc.appendChild(root);

      Map<String, List<String>> deps = new HashMap<>();
      QueryDependenciesFinder.collectDependenciesForAutoDrills("myFile.dat", root, deps);

      assertTrue(deps.containsKey("salesQuery"));
      assertThat(deps.get("salesQuery"), contains("myFile.dat"));
   }

   @Test
   void collectDependenciesForAutoDrills_subqueryWithEmptyQname_ignored() throws Exception {
      Document doc = newDocument();
      Element root = doc.createElement("root");
      Element drillInfo = doc.createElement("XDrillInfo");
      Element drillPath = doc.createElement("drillPath");
      Element subquery = doc.createElement("subquery");
      subquery.setAttribute("qname", ""); // empty qname

      drillPath.appendChild(subquery);
      drillInfo.appendChild(drillPath);
      root.appendChild(drillInfo);
      doc.appendChild(root);

      Map<String, List<String>> deps = new HashMap<>();
      QueryDependenciesFinder.collectDependenciesForAutoDrills("myFile.dat", root, deps);

      assertTrue(deps.isEmpty());
   }

   @Test
   void collectDependenciesForAutoDrills_multipleSubqueries_allAdded() throws Exception {
      Document doc = newDocument();
      Element root = doc.createElement("root");
      Element drillInfo = doc.createElement("XDrillInfo");
      Element drillPath = doc.createElement("drillPath");

      Element sq1 = doc.createElement("subquery");
      sq1.setAttribute("qname", "queryA");
      Element sq2 = doc.createElement("subquery");
      sq2.setAttribute("qname", "queryB");

      drillPath.appendChild(sq1);
      drillPath.appendChild(sq2);
      drillInfo.appendChild(drillPath);
      root.appendChild(drillInfo);
      doc.appendChild(root);

      Map<String, List<String>> deps = new HashMap<>();
      QueryDependenciesFinder.collectDependenciesForAutoDrills("source.dat", root, deps);

      assertTrue(deps.containsKey("queryA"));
      assertTrue(deps.containsKey("queryB"));
      assertThat(deps.get("queryA"), contains("source.dat"));
      assertThat(deps.get("queryB"), contains("source.dat"));
   }

   @Test
   void collectDependenciesForAutoDrills_sameQueryFromMultipleCalls_accumulates()
      throws Exception
   {
      // Build a document with one subquery reference
      Document doc = newDocument();
      Element root = doc.createElement("root");
      Element drillInfo = doc.createElement("XDrillInfo");
      Element drillPath = doc.createElement("drillPath");
      Element sq = doc.createElement("subquery");
      sq.setAttribute("qname", "sharedQuery");
      drillPath.appendChild(sq);
      drillInfo.appendChild(drillPath);
      root.appendChild(drillInfo);
      doc.appendChild(root);

      Map<String, List<String>> deps = new HashMap<>();
      // Simulate two different files referencing the same query
      QueryDependenciesFinder.collectDependenciesForAutoDrills("file1.dat", root, deps);
      QueryDependenciesFinder.collectDependenciesForAutoDrills("file2.dat", root, deps);

      assertThat(deps.get("sharedQuery"), containsInAnyOrder("file1.dat", "file2.dat"));
   }
}
