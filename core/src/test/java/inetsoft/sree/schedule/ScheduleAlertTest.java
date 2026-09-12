/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.schedule;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Tier: [unit] — pure POJO; no Spring / SreeEnv required.
 *
 * Known source bugs (documented below; NOT fixed at this time — tests record current behavior):
 *
 * [BUG-SA-1] writeXML() emits literal "null" for null field values
 *   Location : ScheduleAlert.java:77 (writeXML)
 *   Actual   : Tool.byteEncode(null) is null; String.format("%s", null) writes attribute value "null";
 *              parseXML reads back the string "null", not Java null.
 *   UI risk  : Low — EM selects element/highlight from viewsheet metadata; both fields normally set.
 *   Status   : Deferred. nullFieldsSerializeAsLiteralNull() documents round-trip behavior.
 */

/*
 * Cases deferred - require integration context:
 *
 * [ScheduleAlert] ViewsheetAction.checkAlerts() runtime trigger logic
 *             -> needs RuntimeViewsheet, highlights, principal; NOT duplicated here
 * [ScheduleAlert] nested under ViewsheetAction task XML export/import
 *             -> covered at action level in ScheduleActionXmlRoundTripTest when present
 */
@Tag("core")
class ScheduleAlertTest {

   // -------------------------------------------------------------------------
   // P1/P2 — compareTo, equals, hashCode
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("Ordering and equality (compareTo / equals / hashCode)")
   class CompareToAndEqualsTests {

      @Test
      @DisplayName("getter and setter expose elementId and highlightName")
      void gettersAndSetters() {
         ScheduleAlert alert = new ScheduleAlert();
         alert.setElementId("Chart1");
         alert.setHighlightName("RedHighlight");

         assertEquals("Chart1", alert.getElementId());
         assertEquals("RedHighlight", alert.getHighlightName());
      }

      @Test
      @DisplayName("null elementId sorts after non-null elementId")
      void nullElementIdSortsAfterNonNull() {
         ScheduleAlert withId = new ScheduleAlert();
         withId.setElementId("Chart1");

         ScheduleAlert withoutId = new ScheduleAlert();

         assertTrue(withoutId.compareTo(withId) > 0);
         assertTrue(withId.compareTo(withoutId) < 0);
      }

      @Test
      @DisplayName("orders by elementId then highlightName when both set")
      void ordersByElementIdThenHighlightName() {
         ScheduleAlert first = new ScheduleAlert();
         first.setElementId("Chart1");
         first.setHighlightName("HighlightA");

         ScheduleAlert laterElement = new ScheduleAlert();
         laterElement.setElementId("Chart2");
         laterElement.setHighlightName("HighlightA");

         ScheduleAlert laterHighlight = new ScheduleAlert();
         laterHighlight.setElementId("Chart1");
         laterHighlight.setHighlightName("HighlightB");

         assertTrue(first.compareTo(laterElement) < 0);
         assertTrue(first.compareTo(laterHighlight) < 0);
      }

      @Test
      @DisplayName("null highlightName sorts after non-null when elementId matches")
      void nullHighlightNameSortsAfterNonNull() {
         ScheduleAlert withHighlight = new ScheduleAlert();
         withHighlight.setElementId("Chart1");
         withHighlight.setHighlightName("HighlightA");

         ScheduleAlert withoutHighlight = new ScheduleAlert();
         withoutHighlight.setElementId("Chart1");

         assertTrue(withoutHighlight.compareTo(withHighlight) > 0);
         assertTrue(withHighlight.compareTo(withoutHighlight) < 0);
      }

      @Test
      @DisplayName("both fields null compare as equal")
      void bothNullFieldsCompareEqual() {
         ScheduleAlert left = new ScheduleAlert();
         ScheduleAlert right = new ScheduleAlert();

         assertEquals(0, left.compareTo(right));
         assertEquals(left, right);
         assertEquals(left.hashCode(), right.hashCode());
      }

      @Test
      @DisplayName("equal instances share hashCode; distinct values are not equal")
      void equalsAndHashCodeContract() {
         ScheduleAlert alert = new ScheduleAlert();
         alert.setElementId("Chart1");
         alert.setHighlightName("HighlightA");

         ScheduleAlert same = new ScheduleAlert();
         same.setElementId("Chart1");
         same.setHighlightName("HighlightA");

         ScheduleAlert different = new ScheduleAlert();
         different.setElementId("Chart1");
         different.setHighlightName("HighlightB");

         assertEquals(alert, same);
         assertEquals(alert.hashCode(), same.hashCode());
         assertNotEquals(alert, different);
      }

      @Test
      @DisplayName("compareTo(null) throws NullPointerException")
      void compareToNullThrows() {
         ScheduleAlert alert = new ScheduleAlert();
         alert.setElementId("Chart1");

         assertThrows(NullPointerException.class, () -> alert.compareTo(null));
      }
   }

   // -------------------------------------------------------------------------
   // P1 — XML round-trip
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("XML round-trip (writeXML / parseXML)")
   class XmlRoundTripTests {

      @Test
      @DisplayName("ASCII element and highlight survive round-trip")
      void asciiValuesRoundTrip() throws Exception {
         ScheduleAlert original = new ScheduleAlert();
         original.setElementId("Table1");
         original.setHighlightName("RangeOutput_Range_1");

         ScheduleAlert loaded = roundTripXml(original);

         assertEquals(original.getElementId(), loaded.getElementId());
         assertEquals(original.getHighlightName(), loaded.getHighlightName());
      }

      @Test
      @DisplayName("non-ASCII highlight name survives byteEncode round-trip")
      void nonAsciiHighlightRoundTrip() throws Exception {
         ScheduleAlert original = new ScheduleAlert();
         original.setElementId("Chart1");
         original.setHighlightName("高亮条件");

         ScheduleAlert loaded = roundTripXml(original);

         assertEquals(original.getElementId(), loaded.getElementId());
         assertEquals(original.getHighlightName(), loaded.getHighlightName());
      }

      @Test
      @DisplayName("special characters in elementId survive round-trip")
      void specialCharactersRoundTrip() throws Exception {
         ScheduleAlert original = new ScheduleAlert();
         original.setElementId("Chart/Area#1");
         original.setHighlightName("Cond&A");

         ScheduleAlert loaded = roundTripXml(original);

         assertEquals(original.getElementId(), loaded.getElementId());
         assertEquals(original.getHighlightName(), loaded.getHighlightName());
      }

      @Test
      @DisplayName("BUG-SA-1 (deferred): null fields serialize as literal attribute string null")
      void nullFieldsSerializeAsLiteralNull() throws Exception {
         ScheduleAlert loaded = roundTripXml(new ScheduleAlert());

         assertEquals("null", loaded.getElementId());
         assertEquals("null", loaded.getHighlightName());
      }
   }

   // -------------------------------------------------------------------------
   // P2 — clone
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("clone")
   class CloneTests {

      @Test
      @DisplayName("clone produces equal instance")
      void cloneIsEqual() {
         ScheduleAlert original = new ScheduleAlert();
         original.setElementId("Chart1");
         original.setHighlightName("HighlightA");

         ScheduleAlert copy = (ScheduleAlert) original.clone();

         assertEquals(0, original.compareTo(copy));
         assertEquals(original, copy);
      }

      @Test
      @DisplayName("mutating clone does not change original String fields")
      void cloneIsIndependent() {
         ScheduleAlert original = new ScheduleAlert();
         original.setElementId("Chart1");
         original.setHighlightName("HighlightA");

         ScheduleAlert copy = (ScheduleAlert) original.clone();
         copy.setElementId("Chart2");
         copy.setHighlightName("HighlightB");

         assertEquals("Chart1", original.getElementId());
         assertEquals("HighlightA", original.getHighlightName());
      }
   }

   private static ScheduleAlert roundTripXml(ScheduleAlert original) throws Exception {
      StringWriter sw = new StringWriter();
      original.writeXML(new PrintWriter(sw));

      Element element = DocumentBuilderFactory.newInstance()
         .newDocumentBuilder()
         .parse(new ByteArrayInputStream(sw.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();

      ScheduleAlert loaded = new ScheduleAlert();
      loaded.parseXML(element);
      return loaded;
   }
}
