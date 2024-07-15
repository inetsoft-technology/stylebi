/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.test.*;
import inetsoft.util.FileSystemService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;

/**
 * Test that tests rendering a viewsheet chart. This test requires human verification and should be
 * skipped in automated testing.
 */
@SreeHome(importResources = "GraphRenderTest.zip")
@Disabled("Don't run during automated tests, requires human verification of output")
class GraphRenderTest {
   @Test
   void testRenderGraph() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VGraphPair pair = box.getVGraphPair("Chart1", true, null);
      BufferedImage image = pair.getImage(true, 72 * 2);
      String outputDirectory = System.getProperty("test.output.dir", ".");
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File outputFile = fileSystemService.getPath(outputDirectory, "GraphRenderTest.png").toFile();
      ImageIO.write(image, "PNG", outputFile);

      try(PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
         fileSystemService.getPath(outputDirectory, "GraphRenderTest.html"))))
      {
         writer.println("<!doctype html>");
         writer.println("<html>");
         writer.println("<head><title>GraphRenderTest</title></head>");
         writer.println("<body>");
         writer.println("<img src=\"GraphRenderTest.png\"/>");
         writer.println("</body>");
         writer.println("</html>");
      }
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   @Order(1)
   ControllersExtension controllers = new ControllersExtension();

   @RegisterExtension
   @Order(2)
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent(), controllers);

   private static final String ASSET_ID = "1^128^__NULL__^TEST_GraphRender";
}
