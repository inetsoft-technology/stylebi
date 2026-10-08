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
package inetsoft.util.script.graal;

import inetsoft.report.script.graal.ReportGraalJavaScriptEngine;
import inetsoft.test.*;
import inetsoft.util.script.FormulaContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78079: a restricted script (viewsheet/report script surface;
 * {@code FormulaContext.setRestricted(true)}) must not be able to do file or
 * network I/O through public members of the script-admitted
 * {@code inetsoft.graph.}/{@code inetsoft.report.}/{@code inetsoft.uql.} prefixes,
 * even though it cannot name {@code java.io.*}/{@code java.nio.*}/{@code java.net.*}
 * itself. The sandbox keys on class name, not on what a method does, so any public
 * member of an admitted, non-type-denied class that reads/writes/parses a
 * script-controlled path or mints a {@code File} runs that I/O for the script.
 *
 * <p>This test asserts the DESIRED post-fix behaviour: each offending route is
 * refused (returns no raw content / performs no write). It therefore FAILS (red)
 * on the unfixed base, where the routes succeed, and PASSES (green) once the
 * builder type-denies the offending classes. Both engines are exercised:
 * {@link GraalJavaScriptEngine} (throws on refusal) and
 * {@link ReportGraalJavaScriptEngine} (swallows the error and returns null);
 * {@link #runString}/{@link #run} normalize both to "null == refused".
 *
 * <p>Load-bearing routes from 02-root-cause.md: LB1 (mxUtils), LB2 (MetaImage +
 * the #33237 getImage control), LB3 (CMap), LB4 (TransformDescriptor-minted File
 * into CSVLoader read and DriverPluginGenerator write, no mxgraph on the path).
 * LB5 (image-only members) is deliberately NOT asserted as a defect.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptIoBoundaryTest {
   private static final String SECRET = "TOP-SECRET-78079";

   private GraalJavaScriptEngine baseEngine;
   private ReportGraalJavaScriptEngine reportEngine;

   @TempDir
   Path tmp;

   private File secretFile;

   @BeforeEach
   void setup() throws Exception {
      FormulaContext.setRestricted(false);
      baseEngine = new GraalJavaScriptEngine();
      baseEngine.init(new HashMap<>());
      reportEngine = new ReportGraalJavaScriptEngine();
      reportEngine.init(new HashMap<>());

      secretFile = tmp.resolve("secret.txt").toFile();
      // first line is the probe secret; second line present so CSV has >1 row
      Files.writeString(secretFile.toPath(), SECRET + "\nsecond-line-78079\n",
                        StandardCharsets.UTF_8);
   }

   @AfterEach
   void teardown() {
      FormulaContext.setRestricted(false);
      if(baseEngine != null) {
         baseEngine.close();
      }
      if(reportEngine != null) {
         reportEngine.close();
      }
   }

   private record Eng(String name, GraalJavaScriptEngine engine) {}

   private List<Eng> engines() {
      return List.of(new Eng("base", baseEngine), new Eng("report", reportEngine));
   }

   /** Run restricted; return the result's string form, or null if the route was refused/threw. */
   private String runString(GraalJavaScriptEngine engine, String src) {
      Object o = run(engine, src);
      return o == null ? null : o.toString();
   }

   /** Run restricted; return the raw result, or null if the route was refused/threw. */
   private Object run(GraalJavaScriptEngine engine, String src) {
      boolean old = FormulaContext.isRestricted();

      try {
         FormulaContext.setRestricted(true);
         return engine.exec(engine.compile(src), null, null);
      }
      catch(Throwable t) {
         return null; // base engine throws on a refusal; report engine already returns null
      }
      finally {
         FormulaContext.setRestricted(old);
      }
   }

   /** JS-literal-safe path: forward slashes work for java.io.File on Windows and Unix. */
   private static String js(File f) {
      return f.getAbsolutePath().replace('\\', '/');
   }

   // ---- LB1: mxUtils.readFile / writeFile / loadDocument (String) ---------------------

   @Test
   void mxUtilsReadFileIsRefused() {
      String src = "'' + Java.type('inetsoft.graph.mxgraph.util.mxUtils').readFile('"
         + js(secretFile) + "')";

      assertAll(engines().stream().map(e -> () -> {
         String r = runString(e.engine(), src);
         assertFalse(r != null && r.contains(SECRET),
                     e.name() + ": mxUtils.readFile returned file content: " + r);
      }));
   }

   @Test
   void mxUtilsWriteFileIsRefused() {
      assertAll(engines().stream().map(e -> () -> {
         File out = tmp.resolve("mx-write-" + e.name() + ".txt").toFile();
         out.delete();
         String src = "Java.type('inetsoft.graph.mxgraph.util.mxUtils')"
            + ".writeFile('W-78079','" + js(out) + "'); 'done'";
         run(e.engine(), src);
         assertFalse(out.exists(), e.name() + ": mxUtils.writeFile created " + out);
      }));
   }

   @Test
   void mxUtilsLoadDocumentIsRefused() throws Exception {
      File xml = tmp.resolve("doc.xml").toFile();
      Files.writeString(xml.toPath(), "<probe78079/>", StandardCharsets.UTF_8);
      String uri = xml.toURI().toString();
      String src = "var d = Java.type('inetsoft.graph.mxgraph.util.mxUtils').loadDocument('"
         + uri + "'); d == null ? null : d.getDocumentElement().getTagName()";

      assertAll(engines().stream().map(e -> () -> {
         String r = runString(e.engine(), src);
         assertNull(r, e.name() + ": mxUtils.loadDocument parsed a document, root=" + r);
      }));
   }

   /**
    * Bug #78079 fix pin: the three mxUtils I/O statics were relocated to
    * {@code inetsoft.graph.mxgraph.io.mxFileIO} (mxUtils itself stays reachable for
    * the benign {@code eval}/{@code loadImage} helpers). That package is blocked by
    * name, so a script must not be able to reach mxFileIO and call the sinks there.
    * This guards against the relocation being undone by moving mxFileIO to a
    * script-admitted package.
    */
   @Test
   void relocatedMxFileIoIsNotReachableByName() {
      String src = "var t = Java.type('inetsoft.graph.mxgraph.io.mxFileIO');" +
         "t == null ? null : 'REACHED'";

      assertAll(engines().stream().map(e -> () -> {
         String r = runString(e.engine(), src);
         assertNull(r, e.name() + ": mxFileIO must not be reachable from a script, got " + r);
      }));
   }

   // ---- LB2: MetaImage raw read; getImage(text) control must stay null ----------------

   @Test
   void metaImageGetInputStreamIsRefused() {
      String src =
         "var L = Java.type('inetsoft.report.internal.ImageLocation');" +
         "var l = new L('.'); l.setPath('" + js(secretFile) + "'); l.setPathType(1);" +
         "var s = new (Java.type('inetsoft.report.internal.MetaImage'))(l).getInputStream();" +
         "'' + new (Java.type('java.lang.String'))(s.readAllBytes(), 'UTF-8')";

      assertAll(engines().stream().map(e -> () -> {
         String r = runString(e.engine(), src);
         assertFalse(r != null && r.contains(SECRET),
                     e.name() + ": MetaImage.getInputStream returned raw file bytes: " + r);
      }));
   }

   /**
    * F6 (inv-report), the one route the engine-level repro above cannot cover:
    * {@code BeanUtil.readPropertyValue(parseXml(...))} builds a
    * {@code new MetaImage(iloc)} around any {@code ImageLocation}, so the raw-read
    * sink {@code MetaImage.getInputStream()} is reachable even when only the input
    * builder {@code ImageLocation} is denied. The fix therefore denies the sink
    * OWNER {@code MetaImage} by type, which denies that member on every MetaImage
    * instance however it was constructed. {@link #metaImageGetInputStreamIsRefused}
    * builds MetaImage directly and so would pass if only ImageLocation were denied;
    * this white-box assertion pins the F6 conclusion that the deny sits on MetaImage
    * (closing the parseXml -> readPropertyValue route), not merely on ImageLocation.
    */
   @Test
   void metaImageSinkOwnerIsTypeDeniedClosingBeanUtilRoute() {
      assertTrue(ScriptHostAccess.isTypeDenied(inetsoft.report.internal.MetaImage.class),
                 "MetaImage (the getInputStream sink owner that BeanUtil.readPropertyValue " +
                 "constructs from a parsed XML node) must be denied by type");
   }

   /**
    * #33237 control: the sanctioned getImage path decodes to an Image and returns null
    * for non-image (text) content. This must stay green both before and after the fix;
    * it proves the boundary the deny is measured against is intact and getImage is bound.
    */
   @Test
   void getImageControlReturnsNullForTextFile() {
      String src = "var i = getImage('" + js(secretFile) + "'); i == null ? 'NULL' : 'IMG'";

      assertAll(engines().stream().map(e -> () -> {
         String r = runString(e.engine(), src);
         assertEquals("NULL", r,
                      e.name() + ": getImage(text file) must return null (not " + r + ")");
      }));
   }

   // ---- LB3: CMap.getCMapData(absolute path) raw read ---------------------------------

   @Test
   void cmapGetCMapDataIsRefused() {
      String src = "'' + new (Java.type('java.lang.String'))("
         + "Java.type('inetsoft.report.pdf.CMap').getCMapData('" + js(secretFile)
         + "').readAllBytes(), 'UTF-8')";

      assertAll(engines().stream().map(e -> () -> {
         String r = runString(e.engine(), src);
         assertFalse(r != null && r.contains(SECRET),
                     e.name() + ": CMap.getCMapData returned raw file content: " + r);
      }));
   }

   // ---- LB4: minted File -> CSVLoader read / DriverPluginGenerator write, no mxgraph --

   @Test
   void csvLoaderThroughMintedFileIsRefused() {
      String src =
         "var p = new (Java.type('java.util.Properties'))();" +
         "p.setProperty('from','" + js(secretFile) + "');" +
         "var f = new (Java.type('inetsoft.uql.erm.transform.TransformDescriptor'))(p).getInputFile();" +
         "var ty = new (Java.type('java.util.ArrayList'))(); ty.add('string');" +
         "var t = Java.type('inetsoft.uql.util.filereader.CSVLoader').readCSV(" +
         "   f,'UTF-8',false,String.fromCharCode(1),false,false," +
         "   new (Java.type('java.util.HashMap'))(), ty, false, null, 5, 1000, 10, null);" +
         "t.moreRows(3); var sb='';" +
         "for(var r=0;r<3;r++){ try { sb += t.getObject(r,0) + '|'; } catch(ex) {} } sb";

      assertAll(engines().stream().map(e -> () -> {
         String r = runString(e.engine(), src);
         assertFalse(r != null && r.contains(SECRET),
                     e.name() + ": CSVLoader via minted File read file content: " + r);
      }));
   }

   @Test
   void driverPluginGeneratorThroughMintedFileIsRefused() {
      assertAll(engines().stream().map(e -> () -> {
         File out = tmp.resolve("driver-plugin-" + e.name() + ".zip").toFile();
         out.delete();
         String src =
            "var p = new (Java.type('java.util.Properties'))();" +
            "p.setProperty('from','" + js(out) + "');" +
            "var f = new (Java.type('inetsoft.uql.erm.transform.TransformDescriptor'))(p).getInputFile();" +
            "new (Java.type('inetsoft.uql.jdbc.drivers.DriverPluginGenerator'))()" +
            "   .generatePlugin('p','1','n',[],f); 'done'";
         run(e.engine(), src);
         assertFalse(out.exists(),
                     e.name() + ": DriverPluginGenerator wrote a file via minted File: " + out);
      }));
   }

   // ---- Positive control: a benign admitted report value object must keep working -----

   @Test
   void benignReportValueObjectStillWorks() {
      String src = "new (Java.type('inetsoft.report.Size'))(2, 3).width";

      assertAll(engines().stream().map(e -> () -> {
         Object r = run(e.engine(), src);
         assertNotNull(r, e.name() + ": benign inetsoft.report.Size must stay reachable");
         assertEquals(2.0, ((Number) r).doubleValue(), 1e-9,
                      e.name() + ": inetsoft.report.Size(2,3).width");
      }));
   }
}
