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
package inetsoft.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.storage.JsonXmlTranscoder;
import inetsoft.test.*;
import inetsoft.uql.XCubeMember;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Element types read from viewsheet and dependency storage XML must be the type the
 * writers produce, not any XMLSerializable or AssetObject the XML names (Bug #77816).
 * The foreign element always names {@link AggregateInfo}, a real product class that is
 * both an XMLSerializable and an AssetObject.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssetXmlElementTypeTest {
   @Test
   void vsDimensionKeepsOnlyDimensionMembers() throws Exception {
      VSDimension dim = new VSDimension();
      dim.setName("dim");
      dim.addLevel(member("state"));
      dim.addLevel(member("city"));

      Element elem = parse(dim);
      Element members = Tool.getChildNodeByTagName(elem, "members");
      members.insertBefore(foreignElement(elem.getOwnerDocument()), members.getFirstChild());

      // the generic ItemList parse still constructs the named class
      ItemList generic = new ItemList();
      generic.parseXML(members);
      assertEquals(3, generic.size());
      assertInstanceOf(AggregateInfo.class, generic.getItem(0));

      VSDimension result = new VSDimension();
      result.parseXML(elem);

      assertEquals(2, result.getLevelCount());
      XCubeMember[] levels = result.getLevels();
      assertInstanceOf(VSDimensionMember.class, levels[0]);
      assertInstanceOf(VSDimensionMember.class, levels[1]);
      assertEquals("state", levels[0].getName());
      assertEquals("city", levels[1].getName());
   }

   @Test
   void vsDimensionRoundTripsMembers() throws Exception {
      VSDimension dim = new VSDimension();
      dim.setName("dim");
      dim.addLevel(member("state"));

      VSDimension result = new VSDimension();
      result.parseXML(parse(dim));

      assertEquals(1, result.getLevelCount());
      assertEquals("state", result.getLevelAt(0).getName());
   }

   @Test
   void orderInfoManualOrderReadsOnlyStrings() throws Exception {
      OrderInfo info = new OrderInfo();
      info.setManualOrder(new ArrayList<>(List.of("b", "a")));

      OrderInfo result = new OrderInfo();
      result.parseXML(parse(info));
      assertEquals(List.of("b", "a"), result.getManualOrder());

      Element elem = parse(info);
      replaceChildren(Tool.getChildNodeByTagName(elem, "manualOrderList"));

      result = new OrderInfo();
      result.parseXML(elem);
      assertNotNull(result.getManualOrder());
      assertTrue(result.getManualOrder().isEmpty(), String.valueOf(result.getManualOrder()));
   }

   @Test
   void vsDimensionRefManualOrderReadsOnlyStrings() throws Exception {
      VSDimensionRef ref = new VSDimensionRef(new AttributeRef(null, "state"));
      ref.setManualOrderList(new ArrayList<>(List.of("NJ", "NY")));

      VSDimensionRef result = new VSDimensionRef();
      result.parseXML(parse(ref));
      assertEquals(List.of("NJ", "NY"), result.getManualOrderList());

      Element elem = parse(ref);
      replaceChildren(Tool.getChildNodeByTagName(elem, "manualOrderList"));

      result = new VSDimensionRef();
      result.parseXML(elem);
      assertNotNull(result.getManualOrderList());
      assertTrue(result.getManualOrderList().isEmpty(),
                 String.valueOf(result.getManualOrderList()));
   }

   @Test
   void dependenciesInfoSkipsNonAssetEntryRows() throws Exception {
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(new ArrayList<>(List.of(entry("vs1"), entry("vs2"))));
      info.setEmbedDependencies(new ArrayList<>(List.of(entry("vs3"), entry("vs4"))));

      DependenciesInfo legit = jacksonRoundTrip(parse(info));
      assertEquals(List.of(entry("vs1"), entry("vs2")), legit.getDependencies());
      assertEquals(List.of(entry("vs3"), entry("vs4")), legit.getEmbedDependencies());

      Element elem = parse(info);
      renameAssetObjectClass(elem, "vs2");
      renameAssetObjectClass(elem, "vs3");

      DependenciesInfo result = jacksonRoundTrip(elem);
      assertEquals(List.of(entry("vs1")), result.getDependencies());
      assertEquals(List.of(entry("vs4")), result.getEmbedDependencies());
   }

   @Test
   void renameDependencyInfoSkipsNonAssetEntryKeys() throws Exception {
      RenameDependencyInfo info = new RenameDependencyInfo();
      RenameInfo rinfo = new RenameInfo("old", "new", RenameInfo.ASSET);
      info.addRenameInfo(entry("vs1"), rinfo);
      info.addRenameInfo(entry("vs2"), rinfo);

      RenameDependencyInfo legit = new RenameDependencyInfo();
      legit.parseXML(parse(info));
      assertEquals(Set.of(entry("vs1"), entry("vs2")), legit.getDependencyMap().keySet());
      assertEquals(1, legit.getRenameInfo(entry("vs1")).size());

      Element elem = parse(info);
      renameAssetObjectClass(elem, "vs2");

      RenameDependencyInfo result = new RenameDependencyInfo();
      result.parseXML(elem);

      Map<AssetObject, List<RenameInfo>> map = result.getDependencyMap();
      assertEquals(Set.of(entry("vs1")), map.keySet());
      map.keySet().forEach(key -> assertInstanceOf(AssetEntry.class, key));
      assertEquals("old", map.get(entry("vs1")).get(0).getOldName());
   }

   private static VSDimensionMember member(String name) {
      VSDimensionMember member = new VSDimensionMember();
      member.setDataRef(new AttributeRef(null, name));
      return member;
   }

   private static AssetEntry entry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null);
   }

   private static Element parse(XMLSerializable obj) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      obj.writeXML(writer);
      writer.flush();
      return Tool.parseXML(new StringReader(buf.toString())).getDocumentElement();
   }

   /**
    * A real XMLSerializable/AssetObject that is not the type any of these lists hold.
    */
   private static Element foreignElement(Document doc) throws Exception {
      Element elem = (Element) doc.importNode(parse(new AggregateInfo()), true);
      elem.setAttribute("class", AggregateInfo.class.getName());
      return elem;
   }

   private static void replaceChildren(Element list) throws Exception {
      while(list.getFirstChild() != null) {
         list.removeChild(list.getFirstChild());
      }

      list.appendChild(foreignElement(list.getOwnerDocument()));
   }

   private static void renameAssetObjectClass(Element root, String path) throws Exception {
      NodeList nodes = root.getElementsByTagName("assetObject");
      int found = 0;

      for(int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         AssetEntry entry = new AssetEntry();
         entry.parseXML(Tool.getFirstChildNode(node));

         if(path.equals(entry.getPath())) {
            node.setAttribute("class", AggregateInfo.class.getName());
            found++;
         }
      }

      assertEquals(1, found, path);
   }

   /**
    * Read the XML through the real Jackson deserializer that dependency storage uses.
    */
   private static DependenciesInfo jacksonRoundTrip(Element elem) throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode root = mapper.createObjectNode();
      root.set("dependenciesInfo",
               new JsonXmlTranscoder().transcodeToJson(elem.getOwnerDocument()));
      return mapper.readValue(mapper.writeValueAsString(root), DependenciesInfo.class);
   }
}
