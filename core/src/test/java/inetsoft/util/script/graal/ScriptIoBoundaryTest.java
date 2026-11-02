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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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

   // ---- Util.verifyDirectory: mkdir + existence oracle, relocated out of Util ----------

   /**
    * Bug #78079 (review round 1, finding I1): {@code Util.verifyDirectory(String,String)}
    * was a reachable mkdir + directory-existence oracle on the script-admitted
    * {@code inetsoft.report.internal.Util}. It was relocated to its only caller
    * {@code inetsoft.sree.internal.SUtil} (not script-admitted), so a restricted script
    * can no longer invoke it: the member is gone from {@code Util}, the call is refused,
    * and the directory is not created.
    */
   @Test
   void utilVerifyDirectoryIsRefused() {
      assertAll(engines().stream().map(e -> () -> {
         File dir = tmp.resolve("verify-" + e.name()).toFile();
         dir.delete();
         String src = "Java.type('inetsoft.report.internal.Util')"
            + ".verifyDirectory('" + js(dir) + "', 'log'); 'done'";
         run(e.engine(), src);
         assertFalse(dir.exists(),
                     e.name() + ": Util.verifyDirectory created a directory " + dir);
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

   // ---- mxSvgCanvas embedded image: raw URL/file body into the SVG DOM ----------------

   private static final String MARKER = "MARKER-78079-SVG-BODY";
   private static final String SVG_CANVAS = "inetsoft.graph.mxgraph.canvas.mxSvgCanvas";
   private static final String VIEW_READER = "inetsoft.graph.mxgraph.reader.mxGraphViewImageReader";

   /**
    * Draws an image shape for {@code url} on an embedded mxSvgCanvas built by
    * {@code newCanvas} and returns the SVG, or the caught error as {@code REFUSED:...}
    * (caught inside the script so the report engine does not swallow the reason).
    */
   private static String svgCanvasScript(String newCanvas, String url) {
      return "try {" +
         " var doc = Java.type('inetsoft.graph.mxgraph.util.mxDomUtils').createSvgDocument(100, 100);" +
         " var c = " + newCanvas + ";" +
         " c.setEmbedded(true);" +
         " var st = new (Java.type('java.util.HashMap'))();" +
         " st.put('shape', 'image'); st.put('image', '" + url + "');" +
         " c.drawShape(0, 0, 10, 10, st);" +
         " 'LEAK:' + Java.type('inetsoft.graph.mxgraph.util.mxXmlUtils').getXml(c.getDocument());" +
         "} catch(ex) { 'REFUSED:' + String(ex); }";
   }

   @Test
   void mxSvgCanvasEmbeddedImageIsRefused() throws Exception {
      AtomicInteger hits = new AtomicInteger();
      HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      server.createContext("/", ex -> {
         hits.incrementAndGet();
         byte[] body = MARKER.getBytes(StandardCharsets.UTF_8);
         ex.sendResponseHeaders(200, body.length);

         try(OutputStream out = ex.getResponseBody()) {
            out.write(body);
         }
      });
      server.start();

      try {
         String base = "http://127.0.0.1:" + server.getAddress().getPort();
         List<String> urls = List.of(base + "/x.png", base + "/any?x=.png",
                                     secretFile.toURI() + "#.png");
         List<String> routes = List.of("new (Java.type('" + SVG_CANVAS + "'))(doc)",
                                       "new " + SVG_CANVAS + "(doc)");
         String b64Marker = Base64.getEncoder().encodeToString(MARKER.getBytes(StandardCharsets.UTF_8));
         String b64Secret = Base64.getEncoder().encodeToString(SECRET.getBytes(StandardCharsets.UTF_8));
         List<Executable> checks = new ArrayList<>();

         for(Eng e : engines()) {
            for(String route : routes) {
               for(String url : urls) {
                  checks.add(() -> {
                     String r = runString(e.engine(), svgCanvasScript(route, url));
                     String what = e.name() + " [" + route + "] " + url + ": ";
                     assertNotNull(r, what + "no result");
                     assertFalse(r.contains(b64Marker) || r.contains(MARKER),
                                 what + "listener body reached the script: " + r);
                     assertFalse(r.contains(b64Secret) || r.contains(SECRET),
                                 what + "file content reached the script: " + r);
                     assertTrue(r.startsWith("REFUSED:") && r.contains(
                                   "instantiate on " + SVG_CANVAS + " failed due to: Message not supported."),
                                what + "expected the type-deny refusal, got " + r);
                  });
               }
            }
         }

         assertAll(checks.stream());
         assertEquals(0, hits.get(), "mxSvgCanvas made a server-side request for a script");
      }
      finally {
         server.stop(0);
      }
   }

   // ---- mxGraphViewImageReader.convert(String): server-file existence oracle ----------

   @Test
   void mxGraphViewImageReaderConvertIsRefused() {
      String missing = js(tmp.resolve("missing-78079.txt").toFile());
      List<String> routes = List.of("Java.type('" + VIEW_READER + "')", VIEW_READER);

      assertAll(engines().stream().flatMap(e -> routes.stream().<Executable>map(route -> () -> {
         String what = e.name() + " [" + route + "]: ";
         String existing = runString(e.engine(), convertScript(route, js(secretFile)));
         String absent = runString(e.engine(), convertScript(route, missing));

         for(String r : new String[] { existing, absent }) {
            assertNotNull(r, what + "no result");
            assertFalse(r.contains("FileNotFoundException") || r.contains("SAXParseException"),
                        what + "convert opened the path: " + r);
            assertTrue(r.startsWith("REFUSED:") && r.contains("Unknown identifier: convert"),
                       what + "expected the type-deny refusal, got " + r);
         }

         // the refusal must not depend on whether the file exists (no oracle)
         assertEquals(normalize(existing, js(secretFile)), normalize(absent, missing),
                      what + "existing and missing paths are distinguishable");
      })));
   }

   private static String normalize(String r, String path) {
      return r.replace(path, "<path>").replaceAll("@[0-9a-f]+", "@<id>");
   }

   private static String convertScript(String route, String path) {
      return "try { " + route + ".convert('" + path + "', null); 'OPENED'; }" +
         " catch(ex) { 'REFUSED:' + String(ex); }";
   }

   @Test
   void mxgraphSinkOwnersAreTypeDenied() throws Exception {
      assertTrue(ScriptHostAccess.isTypeDenied(Class.forName(SVG_CANVAS)),
                 "mxSvgCanvas (getResource/createDataUrl sink owner) must be denied by type");
      assertTrue(ScriptHostAccess.isTypeDenied(Class.forName(VIEW_READER)),
                 "mxGraphViewImageReader (convert(String) sink owner) must be denied by type");
   }

   // ---- Bug #78490: java.awt.Toolkit decodes a local image file for the script --------

   @Test
   void awtToolkitImageLoadIsRefused() throws Exception {
      File png = tmp.resolve("toolkit-78490.png").toFile();
      javax.imageio.ImageIO.write(
         new java.awt.image.BufferedImage(3, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
         "png", png);
      // by name, by the legacy package shim, and an instance from an admitted java.awt type
      List<String> routes = List.of(
         "Java.type('java.awt.Toolkit').getDefaultToolkit()",
         "java.awt.Toolkit.getDefaultToolkit()",
         "new (Java.type('java.awt.Canvas'))().getToolkit()");

      assertAll(engines().stream().flatMap(e -> routes.stream().<Executable>map(route -> () -> {
         String src = "try { var t = " + route + "; var a = t.getImage('" + js(png) + "');" +
            " var b = t.createImage('" + js(png) + "'); 'LOADED:' + (a != null || b != null); }" +
            " catch(ex) { 'REFUSED:' + String(ex); }";
         String r = runString(e.engine(), src);
         assertTrue(r == null || r.startsWith("REFUSED:"),
                    e.name() + " [" + route + "]: Toolkit loaded a local image: " + r);
      })));

      assertTrue(ScriptHostAccess.isTypeDenied(java.awt.Toolkit.class),
                 "java.awt.Toolkit (getImage/createImage(String) sink owner) must be denied by type");
   }

   /** java.awt stays admitted: an ordinary value type still works. */
   @Test
   void awtValueTypeStillWorks() {
      String src = "new (Java.type('java.awt.Color'))(1, 2, 3).getGreen()";

      assertAll(engines().stream().map(e -> () -> {
         Object r = run(e.engine(), src);
         assertNotNull(r, e.name() + ": java.awt.Color must stay reachable");
         assertEquals(2.0, ((Number) r).doubleValue(), 1e-9, e.name() + ": Color.getGreen()");
      }));
   }

   // ---- Bug #79846: JDK String-path file primitives (Formatter, ICC_Profile) ----------

   private static final String ORIGINAL_79846 = "ORIGINAL-79846";

   /** Run with the given restricted flag; null if the engine refused/threw. */
   private String runString(GraalJavaScriptEngine engine, String src, boolean restricted) {
      boolean old = FormulaContext.isRestricted();

      try {
         FormulaContext.setRestricted(restricted);
         Object o = engine.exec(engine.compile(src), null, null);
         return o == null ? null : o.toString();
      }
      catch(Throwable t) {
         return null;
      }
      finally {
         FormulaContext.setRestricted(old);
      }
   }

   /** Wraps a script body so a refusal comes back as "REFUSED:..." from both engines. */
   private static String guarded(String body) {
      return "try { " + body + "; 'DONE'; } catch(ex) { 'REFUSED:' + String(ex); }";
   }

   private static void assertRefused(String r, String what) {
      assertTrue(r == null || r.startsWith("REFUSED:"), what + ": expected a refusal, got " + r);
   }

   /** Every engine x restricted/unrestricted: BLOCKED_CLASSES applies in both modes. */
   private List<Executable> eachEngineAndMode(ProbeCheck check) {
      List<Executable> checks = new ArrayList<>();

      for(Eng e : engines()) {
         for(boolean restricted : new boolean[] { true, false }) {
            String what = e.name() + (restricted ? " restricted" : " unrestricted");
            checks.add(() -> check.run(e.engine(), restricted, what));
         }
      }

      return checks;
   }

   @FunctionalInterface
   private interface ProbeCheck {
      void run(GraalJavaScriptEngine engine, boolean restricted, String what) throws Exception;
   }

   private static List<String> formatterRoutes(String path) {
      String use = "; f.format('%s', 'pwned-79846'); f.close()";
      return List.of(
         "var F = Java.type('java.util.Formatter'); var f = new F('" + path + "')" + use,
         "var f = new java.util.Formatter('" + path + "')" + use,
         "var f = new Packages.java.util.Formatter('" + path + "')" + use,
         "var f = java.util.Formatter('" + path + "')" + use,
         "importClass(java.util.Formatter); var f = new Formatter('" + path + "')" + use,
         "var f = new java.util.Formatter('" + path + "', 'UTF-8')" + use);
   }

   @Test
   void formatterCannotCreateFile() {
      assertAll(eachEngineAndMode((engine, restricted, what) -> {
         List<String> routes = formatterRoutes("<p>");

         for(int i = 0; i < routes.size(); i++) {
            File out = tmp.resolve("formatter-" + what.replace(' ', '-') + "-" + i + ".txt")
               .toFile();
            String r = runString(engine, guarded(routes.get(i).replace("<p>", js(out))),
                                 restricted);
            assertRefused(r, what + " route " + i);
            assertFalse(out.exists(), what + " route " + i + ": Formatter created " + out);
         }
      }));
   }

   @Test
   void formatterCannotTruncateExistingFile() {
      assertAll(eachEngineAndMode((engine, restricted, what) -> {
         File victim = tmp.resolve("formatter-victim-" + what.replace(' ', '-') + ".txt")
            .toFile();
         Files.writeString(victim.toPath(), ORIGINAL_79846, StandardCharsets.UTF_8);
         List<String> routes = formatterRoutes(js(victim));

         for(int i = 0; i < routes.size(); i++) {
            String r = runString(engine, guarded(routes.get(i)), restricted);
            assertRefused(r, what + " route " + i);
            assertEquals(ORIGINAL_79846,
                         Files.readString(victim.toPath(), StandardCharsets.UTF_8),
                         what + " route " + i + ": Formatter truncated/overwrote the file");
         }
      }));
   }

   /** ICC_Profile instances reached by name and, with no name lookup, from admitted APIs. */
   private static final List<String> ICC_INSTANCE_ROUTES = List.of(
      "Java.type('java.awt.color.ICC_Profile').getInstance(" +
         "Java.type('java.awt.color.ColorSpace').CS_GRAY)",
      "java.awt.color.ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_GRAY)",
      "Java.type('java.awt.color.ColorSpace').getInstance(" +
         "Java.type('java.awt.color.ColorSpace').CS_sRGB).getProfile()",
      "new java.awt.image.BufferedImage(1, 1, 1).getColorModel().getColorSpace().getProfile()");

   @Test
   void iccProfileCannotWriteFile() {
      assertAll(eachEngineAndMode((engine, restricted, what) -> {
         for(int i = 0; i < ICC_INSTANCE_ROUTES.size(); i++) {
            String tag = what.replace(' ', '-') + "-" + i;
            File out = tmp.resolve("icc-new-" + tag + ".icc").toFile();
            File victim = tmp.resolve("icc-victim-" + tag + ".txt").toFile();
            Files.writeString(victim.toPath(), ORIGINAL_79846, StandardCharsets.UTF_8);
            String route = ICC_INSTANCE_ROUTES.get(i);

            String r = runString(engine, guarded(route + ".write('" + js(out) + "')"),
                                 restricted);
            assertRefused(r, what + " route " + i + " (new file)");
            assertFalse(out.exists(), what + " route " + i + ": ICC_Profile.write created " + out);

            r = runString(engine, guarded(route + ".write('" + js(victim) + "')"), restricted);
            assertRefused(r, what + " route " + i + " (victim)");
            assertEquals(ORIGINAL_79846,
                         Files.readString(victim.toPath(), StandardCharsets.UTF_8),
                         what + " route " + i + ": ICC_Profile.write overwrote the file");
         }
      }));
   }

   @Test
   void iccProfileGetInstanceIsNotAnExistenceOracle() {
      String missing = js(tmp.resolve("missing-79846.icc").toFile());
      List<String> routes = List.of(
         "Java.type('java.awt.color.ICC_Profile')",
         "java.awt.color.ICC_Profile",
         "Java.type('java.awt.color.ICC_ProfileRGB')",
         "Java.type('java.awt.color.ICC_ProfileGray')");

      assertAll(eachEngineAndMode((engine, restricted, what) -> {
         for(String route : routes) {
            String w = what + " [" + route + "]";
            String existing = runString(
               engine, guarded("var p = " + route + ".getInstance('" + js(secretFile) + "')"),
               restricted);
            String absent = runString(
               engine, guarded("var p = " + route + ".getInstance('" + missing + "')"),
               restricted);

            for(String r : new String[] { existing, absent }) {
               assertRefused(r, w);
               assertFalse(r != null && (r.contains("Cannot open file") ||
                                         r.contains("Invalid ICC Profile")),
                           w + ": getInstance opened the path: " + r);
            }

            String e1 = existing == null ? null : normalize(existing, js(secretFile));
            String e2 = absent == null ? null : normalize(absent, missing);
            assertEquals(e1, e2, w + ": existing and missing paths are distinguishable");
         }
      }));
   }

   @Test
   void iccProfileClassesAreRefusedByNameAndType() {
      List<String> names = List.of("java.awt.color.ICC_Profile",
                                   "java.awt.color.ICC_ProfileRGB",
                                   "java.awt.color.ICC_ProfileGray",
                                   "java.util.Formatter");

      assertAll(eachEngineAndMode((engine, restricted, what) -> {
         for(String name : names) {
            String r = runString(engine, guarded("var t = Java.type('" + name + "')"),
                                 restricted);
            assertTrue(r == null || r.startsWith("REFUSED:") && r.contains("not allowed"),
                       what + ": Java.type('" + name + "') must be refused, got " + r);
         }
      }));

      assertTrue(ScriptHostAccess.isTypeDenied(java.util.Formatter.class));
      assertTrue(ScriptHostAccess.isTypeDenied(java.awt.color.ICC_Profile.class));
      assertTrue(ScriptHostAccess.isTypeDenied(java.awt.color.ICC_ProfileRGB.class));
      assertTrue(ScriptHostAccess.isTypeDenied(java.awt.color.ICC_ProfileGray.class));
   }

   /** The neighbours of the blocked classes must keep working. */
   @Test
   void jdkControlsStillWork79846() {
      Map<String, String> controls = new LinkedHashMap<>();
      controls.put("new java.awt.Color(1, 2, 3).getGreen()", "2");
      controls.put("java.awt.color.ColorSpace.getInstance(" +
                      "java.awt.color.ColorSpace.CS_sRGB).getNumComponents()", "3");
      controls.put("var p = new java.util.Properties(); p.setProperty('k', 'v79846');" +
                      " p.getProperty('k')", "v79846");
      controls.put("java.lang.String.format('%05d', 42)", "00042");
      controls.put("new java.util.Scanner('1 2').nextInt()", "1");

      assertAll(eachEngineAndMode((engine, restricted, what) -> {
         for(Map.Entry<String, String> c : controls.entrySet()) {
            String r = runString(engine, c.getKey(), restricted);
            assertNotNull(r, what + ": control must still work: " + c.getKey());
            assertEquals(c.getValue(), r.replaceAll("\\.0$", ""),
                         what + ": control " + c.getKey());
         }
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
