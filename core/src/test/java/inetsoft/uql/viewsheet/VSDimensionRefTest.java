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
package inetsoft.uql.viewsheet;

import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code groupInfo} widened from {@code SNamedGroupInfo} to {@code XNamedGroupInfo}
 * (chart/table/crosstab dimension named-group consumer redesign, Phase 0). Confirms the existing
 * {@code SNamedGroupInfo} path round-trips unchanged, then exercises the newly-reachable
 * {@code ExpertNamedGroupInfo} shape directly (Part 2 wiring, Phase 3, is what will eventually
 * construct a ref this way through the wiz layer -- this test bypasses it, matching Phase 0's own
 * "not exercised by any live path yet" scope).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSDimensionRefTest {
   private static VSDimensionRef regionRef() {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setDataRef(new ColumnRef(new AttributeRef("REGION")));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   private static ExpertNamedGroupInfo westGroup() {
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("CA");
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new AttributeRef("REGION"), cond, 0));
      info.setGroupCondition("West", conds);
      return info;
   }

   // ── SNamedGroupInfo round-trip safety (pure refactor-safety bar) ────────────

   @Test
   void simpleNamedGroupInfoStillRoundTripsThroughCreateGroupRef() {
      VSDimensionRef ref = regionRef();
      SNamedGroupInfo info = new SNamedGroupInfo();
      info.setDataRef(new AttributeRef("REGION"));
      info.setGroupValue("West", List.of("CA", "OR"));
      ref.setNamedGroupInfo(info);

      GroupRef groupRef = ref.createGroupRef(null);

      assertNotNull(groupRef);
      DataRef inner = ((ColumnRef) groupRef.getDataRef()).getDataRef();
      assertInstanceOf(NamedRangeRef.class, inner);
      String expr = ((NamedRangeRef) inner).getExpression();
      assertTrue(expr.contains("West"), "SNamedGroupInfo grouping expression: " + expr);
   }

   @Test
   void simpleNamedGroupInfoStillRoundTripsThroughXmlAndClone() throws Exception {
      VSDimensionRef ref = regionRef();
      SNamedGroupInfo info = new SNamedGroupInfo();
      info.setDataRef(new AttributeRef("REGION"));
      info.setGroupValue("West", List.of("CA", "OR"));
      ref.setNamedGroupInfo(info);

      VSDimensionRef restored = xmlRoundTrip(ref);
      assertInstanceOf(SNamedGroupInfo.class, restored.getNamedGroupInfo());
      assertEquals(List.of("West"), List.of(restored.getNamedGroupInfo().getGroups()));

      VSDimensionRef cloned = (VSDimensionRef) ref.clone();
      assertInstanceOf(SNamedGroupInfo.class, cloned.getNamedGroupInfo());
      assertNotSame(info, cloned.getNamedGroupInfo(), "clone must be a deep copy");
   }

   // ── new behavior: ExpertNamedGroupInfo, direct construction (Phase 0) ───────

   @Test
   void setNamedGroupInfoAcceptsAnExpertNamedGroupInfo() {
      VSDimensionRef ref = regionRef();
      assertDoesNotThrow(() -> ref.setNamedGroupInfo(westGroup()));
      assertInstanceOf(ExpertNamedGroupInfo.class, ref.getNamedGroupInfo());
   }

   @Test
   void createGroupRefBuildsANamedRangeRefFromAnExpertNamedGroupInfo() {
      VSDimensionRef ref = regionRef();
      ref.setNamedGroupInfo(westGroup());

      GroupRef groupRef = ref.createGroupRef(null);

      assertNotNull(groupRef);
      DataRef inner = ((ColumnRef) groupRef.getDataRef()).getDataRef();
      assertInstanceOf(NamedRangeRef.class, inner);

      NamedRangeRef namedRange = (NamedRangeRef) inner;
      String sqlExpr = namedRange.getExpression();
      String scriptExpr = namedRange.getScriptExpression();

      assertTrue(sqlExpr.contains("West"), "SQL CASE expression: " + sqlExpr);
      assertTrue(sqlExpr.contains("'CA'"), "SQL CASE expression: " + sqlExpr);
      assertTrue(scriptExpr.contains("West"), "script expression: " + scriptExpr);
   }

   @Test
   void xmlRoundTripPreservesAnExpertNamedGroupInfoAndItsConditions() throws Exception {
      VSDimensionRef ref = regionRef();
      ref.setNamedGroupInfo(westGroup());

      VSDimensionRef restored = xmlRoundTrip(ref);

      assertInstanceOf(ExpertNamedGroupInfo.class, restored.getNamedGroupInfo(),
         "the missing class= attribute gap (Decision 5) would deserialize this as a plain " +
         "SNamedGroupInfo instead, or throw, depending on the reflective lookup's fallback");
      assertEquals(List.of("West"), List.of(restored.getNamedGroupInfo().getGroups()));
      ConditionList restoredConds = restored.getNamedGroupInfo().getGroupCondition("West");
      assertEquals(1, restoredConds.getConditionSize());
   }

   @Test
   void cloneDeepCopiesAnExpertNamedGroupInfo() {
      VSDimensionRef ref = regionRef();
      ExpertNamedGroupInfo info = westGroup();
      ref.setNamedGroupInfo(info);

      VSDimensionRef cloned = (VSDimensionRef) ref.clone();

      assertInstanceOf(ExpertNamedGroupInfo.class, cloned.getNamedGroupInfo());
      assertNotSame(info, cloned.getNamedGroupInfo(), "clone must be a deep copy");
      assertEquals(info, cloned.getNamedGroupInfo());
   }

   private static VSDimensionRef xmlRoundTrip(VSDimensionRef ref) throws Exception {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      ref.writeXML(pw);
      pw.flush();

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      Document doc = factory.newDocumentBuilder()
         .parse(new ByteArrayInputStream(sw.toString().getBytes()));
      Element elem = doc.getDocumentElement();

      VSDimensionRef restored = new VSDimensionRef();
      restored.parseXML(elem);
      return restored;
   }
}
