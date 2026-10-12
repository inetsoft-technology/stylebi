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
package inetsoft.util.dep;

import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.web.dashboard.VSDashboard;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.util.Tool;
import inetsoft.util.TransformerManager;
import inetsoft.web.admin.deploy.PartialDeploymentJarInfo;
import org.w3c.dom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Invariants of parsing the XML entries of an imported asset bundle: viewsheets, worksheets,
 * partitions, logical models, devices, dashboards, schedule tasks and the {@code JarFileInfo.xml}
 * manifest.
 * The other deployable types are not covered because they do not round-trip as a single
 * standalone XML object here: data sources need {@code Config} to map the source type (e.g.
 * {@code jdbc}) to a class, which the unit-test harness does not load, so the source parses to
 * null; table styles are read from an element but written through a helper (no symmetric
 * {@code writeXML}); scripts are JavaScript text, not XML; and schedule tasks, VPMs, queries,
 * auto-drills and data cycles are not in the example bundle, so there is no seed to validate
 * them. Shared by {@code ExampleAssetsSeedTest}, which replays the bundled
 * examples, and the enterprise fuzzer ({@code test/fuzzer}).
 * <p>
 * An input is the entry content. {@link #check(byte[], boolean)} parses it the way import
 * does (XML parse, version transform, {@code parseXML}). When fuzzing, an entry that fails to
 * parse is simply rejected; for the bundled examples, failing to parse is itself a failure.
 * For an entry that parses, it verifies that:
 * <ol>
 *    <li>no {@link Error} (stack overflow, out of memory, ...) escapes, since import only
 *        handles exceptions;</li>
 *    <li>the XML written for the parsed object parses again;</li>
 *    <li>writing that second object yields the same XML, so an asset does not keep
 *        changing every time it is saved and reloaded. The comparison ignores the order of
 *        sibling elements and attributes, because some maps are written in hash order,
 *        treats an empty element as missing, and ignores the attributes in
 *        {@link #KNOWN_ISSUES}.</li>
 * </ol>
 */
public final class ImportedAssetProperties {
   private ImportedAssetProperties() {
   }

   /**
    * @param requireParse true to fail when the entry does not parse, false to accept that
    *                     as a rejected input.
    *
    * @return false if the content is not XML of an entry type that is checked here.
    */
   public static boolean check(byte[] content, boolean requireParse) throws Exception {
      if(content.length > MAX_INPUT) {
         return true;
      }

      Kind kind;
      Object parsed;

      try {
         Element root = read(content, null);

         if(root == null || (kind = kindOf(root)) == null) {
            return false;
         }

         parsed = parse(kind, content);
      }
      catch(Exception ex) {
         if(requireParse) {
            throw new AssertionError("The entry does not parse: " + ex, ex);
         }

         return true;
      }
      catch(Throwable ex) {
         throw new AssertionError("Parsing the entry threw " + ex, ex);
      }

      String written = write(kind, parsed);
      Object reparsed;

      try {
         reparsed = parse(kind, written.getBytes(StandardCharsets.UTF_8));
      }
      catch(Throwable ex) {
         throw new AssertionError("The XML written for a parsed " + kind + " does not " +
                                  "parse: " + ex + "\n" + excerpt(written, 0), ex);
      }

      String rewritten = write(kind, reparsed);
      String expected;

      try {
         expected = canonical(written);
      }
      catch(Throwable ex) {
         throw new AssertionError("The XML written for a parsed " + kind + " does not " +
                                  "parse: " + ex + "\n" + excerpt(written, 0), ex);
      }

      String actual = canonical(rewritten);

      if(!expected.equals(actual)) {
         int at = firstDifference(expected, actual);
         throw new AssertionError("A " + kind + " changes when saved and reloaded, first " +
                                  "difference at char " + at + " of the canonical XML:\nwritten:   " +
                                  excerpt(expected, at) + "\nrewritten: " + excerpt(actual, at));
      }

      return true;
   }

   private enum Kind {
      VIEWSHEET, WORKSHEET, JAR_INFO, PARTITION, LOGICAL_MODEL, DEVICE, DASHBOARD, SCHEDULE_TASK
   }

   private static Kind kindOf(Element root) {
      return switch(root.getTagName()) {
         case "viewsheet" -> Kind.VIEWSHEET;
         case "assembly" -> Viewsheet.class.getName().equals(root.getAttribute("class")) ?
            Kind.VIEWSHEET : null;
         case "worksheet" -> Kind.WORKSHEET;
         case "jarinfo" -> Kind.JAR_INFO;
         case "partition" -> Kind.PARTITION;
         case "LogicalModel" -> Kind.LOGICAL_MODEL;
         case "deviceInfo" -> Kind.DEVICE;
         case "dashboardAsset" -> Kind.DASHBOARD;
         case "ScheduleTask" -> Kind.SCHEDULE_TASK;
         default -> null;
      };
   }

   /**
    * Parse the XML and, for sheets, apply the version transform as
    * {@link AbstractSheetAsset#parseContent} does.
    */
   private static Element read(byte[] content, String transformer) throws Exception {
      Document doc = Tool.parseXML(new ByteArrayInputStream(content));

      if(doc == null) {
         return null;
      }

      if(transformer != null) {
         TransformerManager.getManager(transformer).transform(doc);
      }

      return doc.getDocumentElement();
   }

   private static Object parse(Kind kind, byte[] content) throws Exception {
      switch(kind) {
      case VIEWSHEET:
         Viewsheet vs = new Viewsheet();
         vs.parseXML(read(content, TransformerManager.VIEWSHEET), false);
         return vs;
      case WORKSHEET:
         Worksheet ws = new Worksheet();
         ws.parseXML(read(content, TransformerManager.WORKSHEET), false);
         return ws;
      case PARTITION:
         XPartition partition = new XPartition();
         partition.parseXML(read(content, null));
         return partition;
      case LOGICAL_MODEL:
         XLogicalModel model = new XLogicalModel("");
         model.parseXML(read(content, null), false);
         return model;
      case DEVICE:
         DeviceInfo device = new DeviceInfo();
         device.parseXML(read(content, null));
         return device;
      case DASHBOARD:
         Element dashboard = Tool.getChildNodeByTagName(read(content, null), "dashboard");
         VSDashboard board = new VSDashboard();
         board.parseXML(dashboard, false);
         return board;
      case SCHEDULE_TASK:
         Element taskElem = Tool.getChildNodeByTagName(read(content, null), "Task");
         ScheduleTask task = new ScheduleTask();
         task.parseXML(taskElem, false);
         return task;
      default:
         PartialDeploymentJarInfo info = new PartialDeploymentJarInfo();
         info.parseXML(read(content, null));
         return info;
      }
   }

   /**
    * Write the object the way export does, see {@code ViewsheetAsset.writeContent0()}.
    */
   private static String write(Kind kind, Object object) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);

      try {
         switch(kind) {
         case VIEWSHEET:
            writer.println("<viewsheet>");
            ((Viewsheet) object).writeXML(writer);
            writer.println("</viewsheet>");
            break;
         case WORKSHEET:
            ((Worksheet) object).writeXML(writer);
            break;
         case PARTITION:
            ((XPartition) object).writeXML(writer);
            break;
         case LOGICAL_MODEL:
            ((XLogicalModel) object).writeXML(writer);
            break;
         case DEVICE:
            ((DeviceInfo) object).writeXML(writer);
            break;
         case DASHBOARD:
            writer.println("<dashboardAsset>");
            ((VSDashboard) object).writeXML(writer);
            writer.println("</dashboardAsset>");
            break;
         case SCHEDULE_TASK:
            writer.println("<ScheduleTask>");
            ((ScheduleTask) object).writeXML(writer);
            writer.println("</ScheduleTask>");
            break;
         default:
            ((PartialDeploymentJarInfo) object).writeXML(writer);
         }
      }
      catch(Throwable ex) {
         throw new AssertionError("Writing a parsed " + kind + " threw " + ex, ex);
      }

      writer.flush();
      return buffer.toString();
   }

   /**
    * An order-insensitive form of the XML: attributes sorted, each element's child elements
    * sorted by their own canonical form, and empty elements dropped.
    */
   private static String canonical(String xml) throws Exception {
      return canonical(Tool.parseXML(new ByteArrayInputStream(
         xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement());
   }

   private static String canonical(Node node) {
      if(node instanceof Element element) {
         List<String> attributes = new ArrayList<>();
         NamedNodeMap map = element.getAttributes();

         for(int i = 0; i < map.getLength(); i++) {
            Node attribute = map.item(i);

            // an empty attribute (x="") reloads as absent, so treat the two as the same
            if(!KNOWN_ISSUES.contains(attribute.getNodeName()) &&
               !attribute.getNodeValue().isEmpty())
            {
               attributes.add(attribute.getNodeName() + "=\"" + attribute.getNodeValue() + "\"");
            }
         }

         // text stays in document order, a long CDATA section may be split into several nodes
         StringBuilder text = new StringBuilder();
         List<String> children = new ArrayList<>();
         NodeList list = element.getChildNodes();

         for(int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);

            if(child instanceof CharacterData data && !(child instanceof Comment)) {
               text.append(data.getData());
            }
            else if(child instanceof Element && !KNOWN_ISSUE_ELEMENTS.contains(
               ((Element) child).getTagName()))
            {
               String canonical = canonical(child);

               if(!canonical.isEmpty()) {
                  children.add(canonical);
               }
            }
         }

         String content = text.toString().strip();

         // an empty element means the same as a missing one (null vs "" is not preserved)
         if(attributes.isEmpty() && content.isEmpty() && children.isEmpty()) {
            return "";
         }

         Collections.sort(attributes);
         Collections.sort(children);
         return "<" + element.getTagName() + " " + String.join(" ", attributes) + ">" +
            text.toString().strip() + String.join("", children) + "</" + element.getTagName() + ">";
      }

      return "";
   }

   private static int firstDifference(String a, String b) {
      int n = Math.min(a.length(), b.length());

      for(int i = 0; i < n; i++) {
         if(a.charAt(i) != b.charAt(i)) {
            return i;
         }
      }

      return n;
   }

   private static String excerpt(String text, int at) {
      int start = Math.max(0, at - 120);
      int end = Math.min(text.length(), at + 120);
      return "..." + text.substring(start, end).replace("\n", "\\n") + "...";
   }

   /**
    * Attributes left out of the save-and-reload comparison because they are known to change.
    * Remove an entry together with the fix for its bug.
    */
   public static final Set<String> KNOWN_ISSUES = Set.of();

   /**
    * Child elements left out of the comparison because they are known to change.
    * Remove an entry together with the fix for its bug.
    * <ul>
    *    <li>{@code Notify}: a schedule-task action saved without a {@code <Notify>} reloads
    *        with a default {@code <Notify link="false" onError="false">}, so the task's XML
    *        grows on every save and reload.</li>
    * </ul>
    */
   public static final Set<String> KNOWN_ISSUE_ELEMENTS = Set.of("Notify");

   /**
    * Larger entries are skipped.
    */
   public static final int MAX_INPUT = 8 * 1024 * 1024;
}
