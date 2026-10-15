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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.coord.*;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the createBulletGraph Script Library sample shipped in the development
 * storage (server/src/test/config/storage.zip) under GraalJS and checks the
 * documented opts and color forms. (#78582)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CreateBulletGraphSampleTest {
   @BeforeAll
   static void loadBody() throws Exception {
      // tests run in core/target/test-workdir, resolve from the core module directory
      Path zip = Paths.get(System.getProperty("basedir", "."), "..", "server", "src", "test",
                           "config", "storage.zip");
      Map<String, byte[]> entries = new HashMap<>();

      try(ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
         for(ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
            entries.put(entry.getName(), in.readAllBytes());
         }
      }

      JsonNode index = new ObjectMapper().readTree(entries.get("blob-index.json"));
      String digest = index.path("host-org__library").path("script/createBulletGraph")
         .path("digest").asText(null);
      assertNotNull(digest, "storage.zip has no script/createBulletGraph library entry");
      byte[] blob = entries.get("blobs/" + digest.substring(0, 2) + "/" + digest.substring(2));
      assertNotNull(blob, "storage.zip has no blob for createBulletGraph digest " + digest);
      body = new String(blob, StandardCharsets.UTF_8);
   }

   @BeforeEach
   void setup() {
      // mirror the engine's Context configuration, as HostBeanProxyTest does
      ctx = Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(ScriptHostAccess.classFilter())
         .allowIO(false)
         .allowCreateThread(false)
         .allowNativeAccess(false)
         .allowCreateProcess(false)
         .allowEnvironmentAccess(EnvironmentAccess.NONE)
         .build();
      LegacyJavaShim.install(ctx, ctx.getBindings("js"), ScriptHostAccess.classFilter());
      ctx.eval("js", body);
   }

   @AfterEach
   void teardown() {
      ctx.close();
   }

   @Test
   void labelsAreVisibleWithoutOpts() {
      EGraph graph = run("createBulletGraph('Total', ['r1', 'r2'], 'Target', 0x0000FF, " +
                         "['X'], ['Dim'], null)");

      assertTrue(outer(graph).getXScale().getAxisSpec().isLabelVisible());
      assertTrue(outer(graph).getYScale().getAxisSpec().isLabelVisible());
      assertTrue(measureScale(graph).getAxisSpec().isLabelVisible());
   }

   @Test
   void ylabelFalseHidesYDimensionLabels() {
      EGraph graph = run("createBulletGraph('Total', ['r1', 'r2'], 'Target', 0x0000FF, " +
                         "null, ['Dim'], 'ylabel=false')");

      assertFalse(outer(graph).getYScale().getAxisSpec().isLabelVisible(),
                  "ylabel=false must hide the y dimension labels");
      assertTrue(measureScale(graph).getAxisSpec().isLabelVisible());
   }

   @Test
   void xlabelAndVlabelFalseHideXDimensionAndValueLabels() {
      EGraph graph = run("createBulletGraph('Total', ['r1', 'r2'], 'Target', 0x0000FF, " +
                         "['X'], ['Dim'], 'xlabel=false,vlabel=false')");

      assertFalse(outer(graph).getXScale().getAxisSpec().isLabelVisible(),
                  "xlabel=false must hide the x dimension labels");
      assertTrue(outer(graph).getYScale().getAxisSpec().isLabelVisible());
      assertFalse(measureScale(graph).getAxisSpec().isLabelVisible(),
                  "vlabel=false must hide the measure value labels");
   }

   @Test
   void colorArraySetsMeasureTargetAndRangeColors() {
      EGraph graph = run("createBulletGraph('Total', ['r1', 'r2'], 'Target', " +
                         "[0x0000FF, 0xFF0000, 0xDD0000], null, ['Dim'], null)");

      // elements: ranges, measure bar, target
      CategoricalColorFrame ranges = (CategoricalColorFrame) graph.getElement(0).getColorFrame();
      assertEquals(new Color(0xDD0000), ranges.getColor("r1"));
      assertEquals(new Color(0x0000FF),
                   ((StaticColorFrame) graph.getElement(1).getColorFrame()).getColor());
      assertEquals(new Color(0xFF0000),
                   ((StaticColorFrame) graph.getElement(2).getColorFrame()).getColor());
   }

   @Test
   void singleColorAppliesToMeasureAndTarget() {
      EGraph graph = run("createBulletGraph('Total', ['r1'], 'Target', 0x0000FF, " +
                         "null, null, null)");

      assertEquals(new Color(0x0000FF),
                   ((StaticColorFrame) graph.getElement(1).getColorFrame()).getColor());
      assertEquals(new Color(0x0000FF),
                   ((StaticColorFrame) graph.getElement(2).getColorFrame()).getColor());
   }

   @Test
   void nullArgumentsAreAccepted() {
      // (#76851) ranges may be omitted
      EGraph graph = run("createBulletGraph('Total', null, 'Target', null, null, null, null)");
      assertEquals(3, graph.getElementCount());
   }

   private EGraph run(String call) {
      ctx.eval("js", "globalThis.__graph = " + call + ";");
      return (EGraph) ScriptValueConverter.toHost(ctx.getBindings("js").getMember("__graph"));
   }

   private static RectCoord outer(EGraph graph) {
      Coordinate coord = graph.getCoordinate();
      assertInstanceOf(FacetCoord.class, coord);
      return (RectCoord) ((FacetCoord) coord).getOuterCoordinate();
   }

   private static Scale measureScale(EGraph graph) {
      Coordinate coord = graph.getCoordinate();

      while(coord instanceof FacetCoord facet) {
         coord = facet.getInnerCoordinates()[0];
      }

      return ((RectCoord) coord).getYScale();
   }

   private static String body;
   private Context ctx;
}
