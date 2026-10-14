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
package inetsoft.report.script.viewsheet;

import com.sun.net.httpserver.HttpServer;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.painter.ImagePresenter;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.script.FormulaContext;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78490: when an admin sets {@code javascript.getImageFunction.disabled=true}, a
 * restricted script must not be able to make the server load an image by URL through
 * any script-reachable loader, not only the {@code getImage} script function. Each
 * route runs through the real viewsheet script path ({@link ViewsheetScope#execute},
 * which sets {@code FormulaContext.setRestricted(true)} itself) or a real worksheet
 * expression column, against a local HTTP listener that records every request. With
 * the switch on the listener must see no request; with the switch unset the route
 * still fetches the image (#33237 parity).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class ScriptImageFunctionSwitchTest {
   private static final String PROPERTY = "javascript.getImageFunction.disabled";

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private ViewsheetSandbox sandbox;
   private HttpServer server;
   private String base;
   private final List<String> hits = Collections.synchronizedList(new ArrayList<>());
   private String oldProperty;

   @BeforeEach
   void setUp() throws Exception {
      sandbox = viewsheetResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      oldProperty = SreeEnv.getProperty(PROPERTY);

      BufferedImage img = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(img, "png", png);
      byte[] body = png.toByteArray();

      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      server.createContext("/", exchange -> {
         hits.add(exchange.getRequestURI().toString());
         exchange.getResponseHeaders().add("Content-Type", "image/png");
         exchange.sendResponseHeaders(200, body.length);

         try(OutputStream out = exchange.getResponseBody()) {
            out.write(body);
         }
      });
      server.start();
      base = "http://127.0.0.1:" + server.getAddress().getPort();
   }

   @AfterEach
   void tearDown() {
      if(server != null) {
         server.stop(0);
      }

      if(oldProperty == null) {
         SreeEnv.remove(PROPERTY);
      }
      else {
         SreeEnv.setProperty(PROPERTY, oldProperty);
      }

      FormulaContext.setRestricted(false);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // the getImage script function (control: it already honored the switch)
      "getImage(URL)",
      "Java.type('inetsoft.graph.mxgraph.util.mxUtils').loadImage(URL)",
      "inetsoft.graph.mxgraph.util.mxUtils.loadImage(URL)",
      // mxBasicCanvas.loadImage delegates to mxUtils.loadImage
      "new (Java.type('inetsoft.graph.mxgraph.canvas.mxGraphics2DCanvas'))().loadImage(URL)",
      "Java.type('inetsoft.uql.viewsheet.internal.VSUtil').getVSImage(null, URL, " +
         "new (Java.type('inetsoft.uql.viewsheet.Viewsheet'))(), 0, 0, 0, 0, null, null)",
      "Java.type('inetsoft.uql.viewsheet.internal.VSUtil').getVSImageBytes(null, URL, " +
         "new (Java.type('inetsoft.uql.viewsheet.Viewsheet'))(), 0, 0, null, null)",
      "Java.type('inetsoft.uql.viewsheet.internal.VSUtil').getVSImageBytes(null, URL + '&.svg', " +
         "new (Java.type('inetsoft.uql.viewsheet.Viewsheet'))(), 0, 0, null, null)",
      "new (Java.type('inetsoft.report.painter.ImagePresenter'))().getPreferredSize(URL)",
      "Java.type('inetsoft.report.script.PropertyDescriptor').convert(URL, Java.type('java.awt.Image'))",
      "setupGoogleMapsPlot(new (Java.type('inetsoft.graph.EGraph'))(), URL + '&', " +
         "[['lat','lon'],[10,20],[11,21]], 'lat', 'lon', 100, 100, 100, 100)",
      // an ImageLocation-built MetaImage carries the URL to any loader that takes an Image
      "var L = new (Java.type('inetsoft.report.internal.ImageLocation'))('.'); " +
         "L.setPath(URL); L.setPathType(4); " +
         "Java.type('inetsoft.uql.viewsheet.internal.VSUtil').getVSImage(L.getImage(), null, " +
         "new (Java.type('inetsoft.uql.viewsheet.Viewsheet'))(), 0, 0, 0, 0, null, null)",
      "var L = new (Java.type('inetsoft.report.internal.ImageLocation'))('.'); " +
         "L.setPath(URL); L.setPathType(4); " +
         "Java.type('inetsoft.uql.viewsheet.internal.VSUtil').getVSImageBytes(L.getImage(), null, " +
         "new (Java.type('inetsoft.uql.viewsheet.Viewsheet'))(), 0, 0, null, null)"
   })
   void viewsheetScriptHonorsSwitch(String call) throws Exception {
      String assembly = sandbox.getViewsheet().getAssemblies()[0].getAbsoluteName();
      ViewsheetScope scope = sandbox.getScope();

      SreeEnv.setProperty(PROPERTY, "true");
      String url = newUrl("on");
      execute(scope, call, url, assembly);
      assertEquals(List.of(), hitsFor(url), "switch on: " + call + " fetched " + url);

      SreeEnv.remove(PROPERTY);
      url = newUrl("off");
      execute(scope, call, url, assembly);
      assertFalse(hitsFor(url).isEmpty(), "switch unset: " + call + " no longer fetches");
   }

   @Test
   void worksheetExpressionColumnHonorsSwitch() throws Exception {
      SreeEnv.setProperty(PROPERTY, "true");
      String url = newUrl("ws-on");
      evaluate(queryWithExpression("c78490a", loadImageExpression(url)));
      assertEquals(List.of(), hitsFor(url), "switch on: expression column fetched " + url);

      SreeEnv.remove(PROPERTY);
      url = newUrl("ws-off");
      evaluate(queryWithExpression("c78490b", loadImageExpression(url)));
      assertFalse(hitsFor(url).isEmpty(), "switch unset: expression column no longer fetches");
   }

   /**
    * ImagePresenter.paint has its own loader; a script-thread paint is refused too.
    */
   @Test
   void imagePresenterPaintHonorsSwitch() {
      BufferedImage target = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = target.createGraphics();

      try {
         FormulaContext.setRestricted(true);
         SreeEnv.setProperty(PROPERTY, "true");
         String url = newUrl("paint-on");
         new ImagePresenter().paint(g, url, 0, 0, 10, 10);
         assertEquals(List.of(), hitsFor(url), "switch on: paint fetched " + url);

         SreeEnv.remove(PROPERTY);
         url = newUrl("paint-off");
         new ImagePresenter().paint(g, url, 0, 0, 10, 10);
         assertFalse(hitsFor(url).isEmpty(), "switch unset: paint no longer fetches");
      }
      finally {
         FormulaContext.setRestricted(false);
         g.dispose();
      }
   }

   /**
    * A local file path or file: URL is refused too (Tool.getImage opens local files
    * itself, mxUtils.loadImage reads any URL).
    */
   @ParameterizedTest
   @ValueSource(strings = {
      "Java.type('inetsoft.graph.mxgraph.util.mxUtils').loadImage(FILE_URL)",
      "Java.type('inetsoft.uql.viewsheet.internal.VSUtil').getVSImage(null, FILE_PATH, " +
         "new (Java.type('inetsoft.uql.viewsheet.Viewsheet'))(), 0, 0, 0, 0, null, null)",
      "Java.type('inetsoft.uql.viewsheet.internal.VSUtil').getVSImageBytes(null, FILE_PATH, " +
         "new (Java.type('inetsoft.uql.viewsheet.Viewsheet'))(), 0, 0, null, null)",
      "Java.type('inetsoft.report.script.PropertyDescriptor').convert(FILE_PATH, " +
         "Java.type('java.awt.Image'))",
      // a MetaImage built from a full-path ImageLocation reads the file when it is loaded
      "(function() { var L = new (Java.type('inetsoft.report.internal.ImageLocation'))('.'); " +
         "L.setPath(FILE_PATH); L.setPathType(1); " +
         "return Java.type('inetsoft.uql.viewsheet.internal.VSUtil').getVSImage(L.getImage(), " +
         "null, new (Java.type('inetsoft.uql.viewsheet.Viewsheet'))(), 0, 0, 0, 0, null, null); })()"
   })
   void localFileHonorsSwitch(String call) throws Exception {
      String assembly = sandbox.getViewsheet().getAssemblies()[0].getAbsoluteName();
      ViewsheetScope scope = sandbox.getScope();
      File file = File.createTempFile("bug78490", ".png");

      try {
         ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", file);
         String script = "var FILE_URL = '" + file.toURI() + "'; " +
            "var FILE_PATH = '" + file.getAbsolutePath().replace('\\', '/') + "'; " +
            "try { var r = " + call + "; r == null ? 'NULL' : 'LOADED' } " +
            "catch(ex) { 'ERR:' + ex }";

         SreeEnv.setProperty(PROPERTY, "true");
         // VSUtil.getVSImage fails on a MetaImage that yields no image (as for a missing
         // file), so a refusal shows as NULL or an error, never as LOADED
         assertNotEquals("LOADED", String.valueOf(scope.execute(script, assembly)),
                         "switch on: " + call + " loaded a local file");

         SreeEnv.remove(PROPERTY);
         assertEquals("LOADED", String.valueOf(scope.execute(script, assembly)),
                      "switch unset: " + call + " no longer loads a local file");
      }
      finally {
         // the Tool.getImage file stream may still hold the file open on Windows
         if(!file.delete()) {
            file.deleteOnExit();
         }
      }
   }

   /**
    * A "java:presenter:" image path builds an ImagePresenter for a script-supplied value.
    */
   @Test
   void presenterImagePathHonorsSwitch() {
      String assembly = sandbox.getViewsheet().getAssemblies()[0].getAbsoluteName();
      ViewsheetScope scope = sandbox.getScope();
      String call = "Java.type('inetsoft.uql.viewsheet.internal.VSUtil').getVSImage(null, " +
         "'java:presenter:inetsoft.report.painter.ImagePresenter;value=' + URL, " +
         "new (Java.type('inetsoft.uql.viewsheet.Viewsheet'))(), 0, 0, 0, 0, null, null)";

      // the presenter path splits on '=' and ';', so the unique part is in the path
      SreeEnv.setProperty(PROPERTY, "true");
      String url = base + "/presenter-on-" + System.nanoTime() + ".png";
      execute(scope, call, url, assembly);
      assertEquals(List.of(), hitsFor(url), "switch on: presenter image path fetched " + url);

      SreeEnv.remove(PROPERTY);
      url = base + "/presenter-off-" + System.nanoTime() + ".png";
      execute(scope, call, url, assembly);
      assertFalse(hitsFor(url).isEmpty(), "switch unset: presenter image path no longer fetches");
   }

   /**
    * A data: URI is decoded in memory, no fetch, so it still works with the switch on.
    */
   @Test
   void dataUriStillDecodedWithSwitchOn() throws Exception {
      String assembly = sandbox.getViewsheet().getAssemblies()[0].getAbsoluteName();
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", png);
      String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
      String script = "var r = Java.type('inetsoft.graph.mxgraph.util.mxUtils').loadImage('" +
         uri + "'); r == null ? 'NULL' : r.getWidth() + 'x' + r.getHeight()";

      SreeEnv.setProperty(PROPERTY, "true");
      assertEquals("3x2", String.valueOf(sandbox.getScope().execute(script, assembly)));
   }

   /**
    * The switch refuses only URL fetches and local file reads: a classpath resource
    * involves neither, so a restricted script still loads it with the switch on.
    */
   @Test
   void classpathResourceStillLoadsWithSwitchOn() throws Exception {
      String assembly = sandbox.getViewsheet().getAssemblies()[0].getAbsoluteName();
      String script = "var r = Java.type('inetsoft.report.script.PropertyDescriptor').convert(" +
         "'/inetsoft/report/gui/composition/images/join.png', Java.type('java.awt.Image')); " +
         "r == null ? 'NULL' : 'LOADED'";

      SreeEnv.setProperty(PROPERTY, "true");
      assertEquals("LOADED", String.valueOf(sandbox.getScope().execute(script, assembly)));
   }

   /**
    * Outside a script (rendering, export) the loaders are not affected by the switch.
    */
   @Test
   void unrestrictedCallerIsNotAffected() {
      SreeEnv.setProperty(PROPERTY, "true");
      String url = newUrl("unrestricted");
      assertNotNull(inetsoft.graph.mxgraph.util.mxUtils.loadImage(url));
      assertFalse(hitsFor(url).isEmpty(), "unrestricted mxUtils.loadImage must still fetch");

      // an Image assembly URL and an image presenter rendered outside a script still fetch
      url = newUrl("unrestricted-vsimage");
      assertNotNull(VSUtil.getVSImage(null, url, new Viewsheet(), 0, 0, 0, 0, null, null));
      assertFalse(hitsFor(url).isEmpty(), "unrestricted VSUtil.getVSImage must still fetch");

      url = newUrl("unrestricted-presenter");
      assertEquals(new Dimension(3, 2), new ImagePresenter().getPreferredSize(url));
      assertFalse(hitsFor(url).isEmpty(), "unrestricted ImagePresenter must still fetch");
   }

   private void execute(ViewsheetScope scope, String call, String url, String assembly) {
      String script = "var URL = '" + url + "'; try { " + call + "; 'DONE' } " +
         "catch(ex) { 'ERR:' + ex }";

      try {
         scope.execute(script, assembly);
      }
      catch(Exception ignore) {
         // the assertion is on the listener, not on the script result
      }
   }

   private static String loadImageExpression(String url) {
      return "var i = Java.type('inetsoft.graph.mxgraph.util.mxUtils').loadImage('" + url +
         "'); i == null ? 0 : 1";
   }

   /** A unique URL per run, so no image cache can hide a fetch. */
   private String newUrl(String tag) {
      return base + "/img.png?u=" + tag + "-" + System.nanoTime();
   }

   private List<String> hitsFor(String url) {
      String query = url.substring(base.length());

      synchronized(hits) {
         return hits.stream().filter(h -> h.startsWith(query)).toList();
      }
   }

   private static void evaluate(TableLens lens) {
      for(int r = 0; lens.moreRows(r); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            lens.getObject(r, c);
         }
      }
   }

   private TableLens queryWithExpression(String name, String script) throws Exception {
      AssetQuerySandbox wbox = sandbox.getAssetQuerySandbox();
      TableAssembly table = (TableAssembly) wbox.getWorksheet().getAssembly("Query1");
      assertNotNull(table, "test worksheet has no Query1");

      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setName(name);
      exp.setExpression(script);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.DOUBLE);
      ColumnSelection columns = table.getColumnSelection();
      columns.addAttribute(column);
      table.setColumnSelection(columns);
      wbox.resetTableLens("Query1");

      TableLens lens = wbox.getTableLens("Query1", AssetQuerySandbox.RUNTIME_MODE);
      assertNotNull(lens, "no table lens for Query1");
      return lens;
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }
}
