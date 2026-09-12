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
package inetsoft.uql;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Cases deferred - not covered in this pass:
 *
 * [DrillPath] writeXML(PrintWriter) / parseXML(Element) / parseXML(Element, boolean) -
 *             XML serialization round-trip, not branching logic; descoped per reviewer
 *             guidance (see ConditionItemTest in the same package). The isSiteAdminImport=true
 *             branch of parseXML also calls OrganizationManager.getInstance(), which needs an
 *             environment context beyond a plain unit test.
 * [DrillPath] toString() / toString(boolean) - display formatting, not correctness-critical.
 * [DrillPath] contentHashCode() - thin Objects.hash() wrapper over fields already exercised via
 *             equalsContent(); not independently branching logic.
 */
@Tag("core")
class DrillPathTest {

   // ---- Constructors ----

   @Test
   void defaultConstructorUsesEmptyName() {
      DrillPath path = new DrillPath();
      assertEquals("", path.getName());
   }

   @Test
   void nullNameBecomesEmptyString() {
      DrillPath path = new DrillPath(null);
      assertEquals("", path.getName());
   }

   @Test
   void constructorWithNameSetsName() {
      DrillPath path = new DrillPath("MyDrill");
      assertEquals("MyDrill", path.getName());
   }

   // ---- setParameterField / getParameterField ----

   @Test
   void setParameterFieldStoresMapping() {
      DrillPath path = new DrillPath("p");
      path.setParameterField("param1", "field1");
      assertEquals("field1", path.getParameterField("param1"));
   }

   @Test
   void setParameterFieldNullFieldRemovesMapping() {
      DrillPath path = new DrillPath("p");
      path.setParameterField("param1", "field1");
      path.setParameterField("param1", null);
      assertNull(path.getParameterField("param1"));
   }

   @Test
   void getParameterFieldOnAbsentNameReturnsNull() {
      DrillPath path = new DrillPath("p");
      assertNull(path.getParameterField("nonexistent"));
   }

   @Test
   void getParameterCountStartsAtZero() {
      DrillPath path = new DrillPath("p");
      assertEquals(0, path.getParameterCount());
   }

   @Test
   void getParameterCountIncrementsWithAdds() {
      DrillPath path = new DrillPath("p");
      path.setParameterField("p1", "f1");
      assertEquals(1, path.getParameterCount());
      path.setParameterField("p2", "f2");
      assertEquals(2, path.getParameterCount());
   }

   // ---- removeParameterField ----

   @Test
   void removeParameterFieldDeletesEntry() {
      DrillPath path = new DrillPath("p");
      path.setParameterField("p1", "f1");
      path.setParameterField("p2", "f2");
      path.removeParameterField("p1");

      assertNull(path.getParameterField("p1"));
      assertEquals("f2", path.getParameterField("p2"));
   }

   @Test
   void removeParameterFieldSetsParamsToNullWhenEmpty() {
      DrillPath path = new DrillPath("p");
      path.setParameterField("p1", "f1");
      path.removeParameterField("p1");

      assertEquals(0, path.getParameterCount());
   }

   @Test
   void removeParameterFieldAlsoRemovesType() {
      DrillPath path = new DrillPath("p");
      path.setParameterField("p1", "f1");
      path.setParameterType("p1", "string");
      path.removeParameterField("p1");

      assertNull(path.getParameterType("p1"));
   }

   @Test
   void removeAllParameterFieldsClearsAll() {
      DrillPath path = new DrillPath("p");
      path.setParameterField("p1", "f1");
      path.setParameterField("p2", "f2");
      path.setParameterType("p1", "string");
      path.removeAllParameterFields();

      assertEquals(0, path.getParameterCount());
      assertNull(path.getParameterType("p1"));
   }

   // ---- isParameterHardCoded ----

   @Test
   void isParameterHardCodedReturnsFalseWhenNoTypes() {
      DrillPath path = new DrillPath("p");
      path.setParameterField("p1", "f1");
      assertFalse(path.isParameterHardCoded("p1"));
   }

   @Test
   void isParameterHardCodedReturnsTrueWhenTypeIsSet() {
      DrillPath path = new DrillPath("p");
      path.setParameterField("p1", "f1");
      path.setParameterType("p1", "string");
      assertTrue(path.isParameterHardCoded("p1"));
   }

   @Test
   void isParameterHardCodedReturnsFalseForAbsentKey() {
      DrillPath path = new DrillPath("p");
      assertFalse(path.isParameterHardCoded("nope"));
   }

   // ---- equalsContent ----

   @Test
   void equalsContentOnIdenticalPaths() {
      DrillPath a = buildPath("drill", "http://example.com", DrillPath.WEB_LINK);
      DrillPath b = buildPath("drill", "http://example.com", DrillPath.WEB_LINK);
      assertTrue(a.equalsContent(b));
   }

   @Test
   void equalsContentDifferentLink() {
      DrillPath a = buildPath("drill", "http://a.com", DrillPath.WEB_LINK);
      DrillPath b = buildPath("drill", "http://b.com", DrillPath.WEB_LINK);
      assertFalse(a.equalsContent(b));
   }

   @Test
   void equalsContentDifferentLinkType() {
      DrillPath a = buildPath("drill", "http://example.com", DrillPath.WEB_LINK);
      DrillPath b = buildPath("drill", "http://example.com", DrillPath.VIEWSHEET_LINK);
      assertFalse(a.equalsContent(b));
   }

   @Test
   void equalsContentDifferentParams() {
      DrillPath a = new DrillPath("d");
      a.setParameterField("p1", "f1");
      DrillPath b = new DrillPath("d");
      b.setParameterField("p1", "other");
      assertFalse(a.equalsContent(b));
   }

   @Test
   void equalsContentBothParamsNull() {
      DrillPath a = new DrillPath("d");
      DrillPath b = new DrillPath("d");
      assertTrue(a.equalsContent(b));
   }

   @Test
   void equalsContentDisablePromptingDifference() {
      DrillPath a = new DrillPath("d");
      a.setDisablePrompting(true);
      DrillPath b = new DrillPath("d");
      b.setDisablePrompting(false);
      assertFalse(a.equalsContent(b));
   }

   @Test
   void equalsContentPassParamsDifference() {
      DrillPath a = new DrillPath("d");
      a.setSendReportParameters(true);
      DrillPath b = new DrillPath("d");
      b.setSendReportParameters(false);
      assertFalse(a.equalsContent(b));
   }

   @Test
   void equalsContentNotDrillPath() {
      DrillPath path = new DrillPath("d");
      assertFalse(path.equalsContent("something"));
   }

   // ---- copyDrillPath ----

   @Test
   void copyDrillPathCreatesNewInstance() {
      DrillPath original = new DrillPath("original");
      DrillPath copy = original.copyDrillPath("copy");
      assertNotSame(original, copy);
   }

   @Test
   void copyDrillPathSetsNewName() {
      DrillPath original = new DrillPath("original");
      DrillPath copy = original.copyDrillPath("copy");
      assertEquals("copy", copy.getName());
   }

   @Test
   void copyDrillPathPreservesLink() {
      DrillPath original = new DrillPath("original");
      original.setLink("http://example.com");
      DrillPath copy = original.copyDrillPath("copy");
      assertEquals("http://example.com", copy.getLink());
   }

   @Test
   void copyDrillPathPreservesParams() {
      DrillPath original = new DrillPath("original");
      original.setParameterField("p1", "f1");
      DrillPath copy = original.copyDrillPath("copy");
      assertEquals("f1", copy.getParameterField("p1"));
   }

   // ---- clone ----

   @Test
   void cloneProducesNewInstance() {
      DrillPath original = new DrillPath("orig");
      DrillPath cloned = (DrillPath) original.clone();
      assertNotSame(original, cloned);
   }

   @Test
   void clonePreservesName() {
      DrillPath original = new DrillPath("orig");
      DrillPath cloned = (DrillPath) original.clone();
      assertEquals("orig", cloned.getName());
   }

   @Test
   void clonePreservesParams() {
      DrillPath original = new DrillPath("orig");
      original.setParameterField("p1", "f1");
      DrillPath cloned = (DrillPath) original.clone();
      assertEquals("f1", cloned.getParameterField("p1"));
   }

   @Test
   void clonePreservesHardCodedType() {
      DrillPath original = new DrillPath("orig");
      original.setParameterField("p1", "f1");
      original.setParameterType("p1", "string");
      DrillPath cloned = (DrillPath) original.clone();
      assertTrue(cloned.isParameterHardCoded("p1"));
   }

   @Test
   void cloningDoesNotShareParamMap() {
      DrillPath original = new DrillPath("orig");
      original.setParameterField("p1", "f1");

      DrillPath cloned = (DrillPath) original.clone();
      cloned.setParameterField("p1", "changed");

      assertEquals("f1", original.getParameterField("p1"));
   }

   @Test
   void cloningDoesNotShareTypeMap() {
      DrillPath original = new DrillPath("orig");
      original.setParameterField("p1", "f1");
      original.setParameterType("p1", "string");

      DrillPath cloned = (DrillPath) original.clone();
      cloned.setParameterType("p1", "integer");

      assertEquals("string", original.getParameterType("p1"));
   }

   // ---- equals / hashCode ----

   @Test
   void equalsBasedOnName() {
      DrillPath a = new DrillPath("same");
      DrillPath b = new DrillPath("same");
      assertEquals(a, b);
   }

   @Test
   void notEqualsForDifferentName() {
      DrillPath a = new DrillPath("a");
      DrillPath b = new DrillPath("b");
      assertNotEquals(a, b);
   }

   @Test
   void hashCodeBasedOnName() {
      DrillPath a = new DrillPath("name");
      assertEquals("name".hashCode(), a.hashCode());
   }

   // ---- getParameterNames ----

   @Test
   void getParameterNamesEmptyWhenNoParams() {
      DrillPath path = new DrillPath("p");
      Enumeration<String> names = path.getParameterNames();
      assertTrue(Collections.list(names).isEmpty());
   }

   @Test
   void getParameterNamesIncludesAddedParams() {
      DrillPath path = new DrillPath("p");
      path.setParameterField("param1", "field1");
      path.setParameterField("param2", "field2");
      Enumeration<String> names = path.getParameterNames();
      assertTrue(Collections.list(names).contains("param1"));
   }

   // ---- getParameterType / setParameterType ----

   @Nested
   class ParameterTypeTests {

      @Test
      void getParameterTypeOnAbsentNameReturnsNull() {
         DrillPath path = new DrillPath("p");
         assertNull(path.getParameterType("p1"));
      }

      @Test
      void getParameterTypeReturnsSetValue() {
         DrillPath path = new DrillPath("p");
         path.setParameterType("p1", "string");
         assertEquals("string", path.getParameterType("p1"));
      }

      @Test
      void setParameterTypeWithEmptyNameIsIgnored() {
         // setParameterType() guards on name != null && name.length() > 0
         DrillPath path = new DrillPath("p");
         path.setParameterType("", "string");
         assertNull(path.getParameterType(""));
         assertFalse(path.isParameterHardCoded(""));
      }
   }

   // ---- setQuery / getQuery ----

   @Nested
   class QueryTests {

      @Test
      void getQueryDefaultsToNull() {
         DrillPath path = new DrillPath("p");
         assertNull(path.getQuery());
      }

      @Test
      void setQueryStoresSubquery() {
         DrillPath path = new DrillPath("p");
         DrillSubQuery query = new DrillSubQuery();
         query.setQuery("select * from t");
         path.setQuery(query);
         assertSame(query, path.getQuery());
      }

      @Test
      void equalsContentSameQuery_areEqual() {
         DrillPath a = new DrillPath("d");
         DrillSubQuery queryA = new DrillSubQuery();
         queryA.setQuery("select * from t");
         a.setQuery(queryA);

         DrillPath b = new DrillPath("d");
         DrillSubQuery queryB = new DrillSubQuery();
         queryB.setQuery("select * from t");
         b.setQuery(queryB);

         assertTrue(a.equalsContent(b));
      }

      @Test
      void equalsContentDifferentQuery_areNotEqual() {
         DrillPath a = new DrillPath("d");
         DrillSubQuery queryA = new DrillSubQuery();
         queryA.setQuery("select * from t1");
         a.setQuery(queryA);

         DrillPath b = new DrillPath("d");
         DrillSubQuery queryB = new DrillSubQuery();
         queryB.setQuery("select * from t2");
         b.setQuery(queryB);

         assertFalse(a.equalsContent(b));
      }
   }

   // ---- helpers ----

   private DrillPath buildPath(String name, String link, int linkType) {
      DrillPath path = new DrillPath(name);
      path.setLink(link);
      path.setLinkType(linkType);
      return path;
   }
}
