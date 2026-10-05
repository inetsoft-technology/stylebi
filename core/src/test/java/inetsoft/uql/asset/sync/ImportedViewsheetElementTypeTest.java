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
package inetsoft.uql.asset.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.uql.XCubeMember;
import inetsoft.uql.XDimension;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CubeVSAssemblyInfo;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77816 end to end: a viewsheet from the bundled examples, parsed the way import parses it
 * (XML parse, version transform, {@link Viewsheet#parseXML}), and a dependency row read through
 * the storage-transfer object mapper into {@link DependencyStorageService}. A foreign element in
 * VSDimension members or a manualOrderList, or a non-AssetEntry dependency row, must not be
 * constructed or kept, and the unmodified example must parse to the same members and manual
 * order as before the fix.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ImportedViewsheetElementTypeTest {
   public static final class Counter {
      public static int STATIC = 0;
      public static int CTOR = 0;
      public static int PARSE = 0;
   }

   public static class Probe implements XMLSerializable {
      static { Counter.STATIC++; }
      public Probe() { Counter.CTOR++; }
      @Override public void writeXML(PrintWriter writer) { }
      @Override public void parseXML(Element tag) { Counter.PARSE++; }
   }

   public static class ProbeAsset implements AssetObject {
      static { Counter.STATIC++; }
      public ProbeAsset() { Counter.CTOR++; }
      @Override public void writeXML(PrintWriter writer) { }
      @Override public void parseXML(Element tag) { Counter.PARSE++; }
      @Override public Object clone() { return this; }
   }

   @Autowired
   private KeyValueStorageManager keyValueStorageManager;

   static final String RETURN = "Return Analysis";
   static final String CALL = "Call Center Monitoring";

   @BeforeEach
   void reset() {
      Counter.CTOR = 0;
      Counter.PARSE = 0;
   }

   // ---- control: product-saved examples round-trip unchanged ----
   @Test
   void controlExamplesRoundTripMembersAndManualOrder() throws Exception {
      for(String name : List.of(RETURN, CALL)) {
         Document src = load(name);
         TransformerManager.getManager(TransformerManager.VIEWSHEET).transform(src);
         List<String> srcSig = signature(src.getDocumentElement());
         Viewsheet vs = new Viewsheet();
         vs.parseXML(src.getDocumentElement(), false);
         List<String> outSig = signature(write(vs));
         assertEquals(srcSig.stream().filter(x -> x.startsWith("members:")).toList(),
                      outSig.stream().filter(x -> x.startsWith("members:")).toList(), name);
      }

      // the real VSCube in Return Analysis keeps its VSDimensionMembers
      Document src = load(RETURN);
      TransformerManager.getManager(TransformerManager.VIEWSHEET).transform(src);
      Viewsheet vs = new Viewsheet();
      vs.parseXML(src.getDocumentElement(), false);
      assertEquals(2, cubeLevels(vs));
   }

   // ---- attack: crafted imported viewsheet ----
   @Test
   void importedViewsheetIgnoresForeignMemberAndManualOrderElements() throws Exception {
      Document src = load(RETURN);
      NodeList members = src.getElementsByTagName("members");
      int injectedMembers = 0;

      for(int i = 0; i < members.getLength(); i++) {
         Element m = (Element) members.item(i);
         m.insertBefore(foreign(src), m.getFirstChild());
         m.insertBefore(probe(src, Probe.class), m.getFirstChild());
         injectedMembers++;
      }

      assertTrue(injectedMembers > 0);
      Document clean = load(RETURN);
      TransformerManager.getManager(TransformerManager.VIEWSHEET).transform(clean);
      List<String> cleanSig = signature(clean.getDocumentElement());

      TransformerManager.getManager(TransformerManager.VIEWSHEET).transform(src);
      Viewsheet vs = new Viewsheet();
      vs.parseXML(src.getDocumentElement(), false);

      assertEquals(0, Counter.CTOR, "probe constructed via members");
      assertEquals(0, Counter.STATIC, "probe static init ran via members");
      List<String> attackedSig = signature(write(vs));
      assertEquals(cleanSig.stream().filter(x -> x.startsWith("members:")).toList(),
                   attackedSig.stream().filter(x -> x.startsWith("members:")).toList());
      assertTrue(attackedSig.stream().noneMatch(x -> x.contains("Probe") || x.contains("AggregateInfo")));
      assertEquals(2, cubeLevels(vs));

      // manual order: VSDimensionRef list replaced with foreign elements, OrderInfo empty lists
      // given foreign elements
      Document call = load(CALL);
      NodeList lists = call.getElementsByTagName("manualOrderList");
      int replacedRef = 0, injectedEmpty = 0;

      for(int i = 0; i < lists.getLength(); i++) {
         Element l = (Element) lists.item(i);

         if(Tool.getChildNodesByTagName(l, "item").getLength() > 0) {
            if(replacedRef == 0) {
               while(l.getFirstChild() != null) {
                  l.removeChild(l.getFirstChild());
               }

               l.appendChild(probe(call, Probe.class));
               l.appendChild(foreign(call));
               replacedRef++;
            }
         }
         else {
            l.appendChild(probe(call, Probe.class));
            l.appendChild(foreign(call));
            injectedEmpty++;
         }
      }

      assertEquals(1, replacedRef);
      assertTrue(injectedEmpty > 0);
      TransformerManager.getManager(TransformerManager.VIEWSHEET).transform(call);
      Viewsheet cvs = new Viewsheet();
      cvs.parseXML(call.getDocumentElement(), false);
      assertEquals(0, Counter.CTOR, "probe constructed via manualOrderList");
      assertEquals(0, Counter.STATIC, "probe static init ran via manualOrderList");

      List<String> sig = signature(write(cvs));
      assertTrue(sig.stream().noneMatch(s -> s.contains("Probe") || s.contains("AggregateInfo")),
                 String.valueOf(sig));
      // the replaced list is now empty; every other list still holds its strings
      long nonEmpty = sig.stream().filter(s -> s.startsWith("manual:") && !s.equals("manual:[]"))
         .count();
      Document cc = load(CALL);
      TransformerManager.getManager(TransformerManager.VIEWSHEET).transform(cc);
      Viewsheet cleanVs = new Viewsheet();
      cleanVs.parseXML(cc.getDocumentElement(), false);
      long cleanNonEmpty = signature(write(cleanVs)).stream()
         .filter(s -> s.startsWith("manual:") && !s.equals("manual:[]")).count();
      assertTrue(nonEmpty <= cleanNonEmpty && nonEmpty >= cleanNonEmpty - 1);
   }

   // ---- dependency row through the storage-transfer Jackson mapper + real DSS ----
   @Test
   void dependencyRowThroughStorageService() throws Exception {
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(new ArrayList<>(List.of(entry("vs1"), entry("vs2"))));
      info.setEmbedDependencies(new ArrayList<>(List.of(entry("vs3"))));

      ObjectMapper mapper = KeyValueEngine.createObjectMapper();
      JsonNode legitJson = mapper.valueToTree(info);
      DependenciesInfo legit = mapper.convertValue(legitJson, DependenciesInfo.class);
      assertEquals(info.getDependencies(), legit.getDependencies());

      Element elem = xml(info);
      setClass(elem, "vs2", ProbeAsset.class.getName());
      setClass(elem, "vs3", AggregateInfo.class.getName());
      ObjectNode root = mapper.createObjectNode();
      root.set("dependenciesInfo", new JsonXmlTranscoder().transcodeToJson(elem.getOwnerDocument()));
      DependenciesInfo crafted = mapper.convertValue(root, DependenciesInfo.class);

      DependencyStorageService service = new DependencyStorageService(keyValueStorageManager);
      String key = entry("target").toIdentifier(true);
      service.put(key, crafted);
      DependenciesInfo read = (DependenciesInfo) service.get(key);

      assertEquals(0, Counter.CTOR, "ProbeAsset constructed");
      assertEquals(List.of(entry("vs1")), read.getDependencies());
      assertTrue(read.getEmbedDependencies().isEmpty(), String.valueOf(read.getEmbedDependencies()));
      service.remove(key);
   }

   // ---- helpers ----
   private static int cubeLevels(Viewsheet vs) {
      int levels = 0;

      for(Assembly a : vs.getAssemblies()) {
         if(a instanceof VSAssembly va && va.getVSAssemblyInfo() instanceof CubeVSAssemblyInfo ci) {
            Object cube = ci.getXCube();

            if(cube instanceof VSCube vc) {
               for(Enumeration<XDimension> e = vc.getDimensions(); e.hasMoreElements(); ) {
                  VSDimension d = (VSDimension) e.nextElement();
                  XCubeMember[] arr = d.getLevels();

                  for(XCubeMember m : arr) {
                     assertInstanceOf(VSDimensionMember.class, m);
                  }

                  levels += arr.length;
               }
            }
         }
      }

      return levels;
   }

   /** members child classes + manual order contents, in document order. */
   private static List<String> signature(Element root) {
      List<String> sig = new ArrayList<>();
      NodeList members = root.getElementsByTagName("members");

      for(int i = 0; i < members.getLength(); i++) {
         List<String> cls = new ArrayList<>();
         NodeList kids = members.item(i).getChildNodes();

         for(int j = 0; j < kids.getLength(); j++) {
            if(kids.item(j) instanceof Element e) {
               cls.add(e.getTagName() + "/" + e.getAttribute("class"));
            }
         }

         sig.add("members:" + cls);
      }

      NodeList lists = root.getElementsByTagName("manualOrderList");

      for(int i = 0; i < lists.getLength(); i++) {
         List<String> vals = new ArrayList<>();
         NodeList kids = lists.item(i).getChildNodes();

         for(int j = 0; j < kids.getLength(); j++) {
            if(kids.item(j) instanceof Element e) {
               vals.add("item".equals(e.getTagName()) ? Tool.getValue(e) :
                        e.getTagName() + "/" + e.getAttribute("class"));
            }
         }

         sig.add("manual:" + vals);
      }

      return sig;
   }

   private static Document load(String vsName) throws Exception {
      Path classes = Path.of(ImportedViewsheetElementTypeTest.class.getResource("/").toURI());
      Path zip = classes.resolve("../../../community-examples/examples.zip").normalize();

      try(ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
         for(ZipEntry e; (e = in.getNextEntry()) != null; ) {
            if(e.getName().startsWith("VIEWSHEET_") && e.getName().contains("^" + vsName + "^")) {
               return Tool.parseXML(new ByteArrayInputStream(in.readAllBytes()));
            }
         }
      }

      throw new FileNotFoundException(vsName);
   }

   private static Element write(XMLSerializable obj) throws Exception {
      return xml(obj);
   }

   private static Element xml(XMLSerializable obj) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      obj.writeXML(writer);
      writer.flush();
      return Tool.parseXML(new StringReader(buf.toString())).getDocumentElement();
   }

   private static Element probe(Document doc, Class<?> cls) {
      Element e = doc.createElement("probe");
      e.setAttribute("class", cls.getName());
      return e;
   }

   private static Element foreign(Document doc) throws Exception {
      Element e = (Element) doc.importNode(xml(new AggregateInfo()), true);
      e.setAttribute("class", AggregateInfo.class.getName());
      return e;
   }

   private static AssetEntry entry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null);
   }

   private static void setClass(Element root, String path, String cls) throws Exception {
      NodeList nodes = root.getElementsByTagName("assetObject");
      int found = 0;

      for(int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         AssetEntry entry = new AssetEntry();
         entry.parseXML(Tool.getFirstChildNode(node));

         if(path.equals(entry.getPath())) {
            node.setAttribute("class", cls);
            found++;
         }
      }

      assertEquals(1, found, path);
   }
}
