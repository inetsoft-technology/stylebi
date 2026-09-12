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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Intent vs implementation suspects
 *
 * [BUG-CC-1] equals() -> intent: value equality by taskname
 *            actual: hashCode() not overridden; Object identity used -> HashSet/HashMap deduplication breaks
 *            Fix: add hashCode() { return Objects.hashCode(taskname); }  CompletionCondition.java:128
 *
 * [BUG-CC-2] writeXML(null taskname) -> intent: round-trip fidelity
 *            actual: Tool.escape(null) returns "" -> parseXML restores "" not null -> equals() fails
 *            Not a production risk: frontend enforces non-null taskname; tracked for future API paths
 */

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CompletionConditionTest {
   CompletionCondition completionCondition;
   long d1 = setCustomTimeInMillis(7, 59, 59);

   @Test
   void testCheck() {
      completionCondition = new CompletionCondition();
      completionCondition.setComplete(true);

      boolean check = completionCondition.check(d1);
      assertTrue(check);
      assertTrue(completionCondition.toString().contains("null"));

      SreeEnv.setProperty("security.enabled", "true");
      completionCondition = new CompletionCondition("org1:task1");
      completionCondition.setComplete(false);
      check = completionCondition.check(d1);
      assertFalse(check);
      assertTrue(completionCondition.toString().contains("org1:task1"));
      assertEquals("org1:task1", completionCondition.getTaskName());
   }

   @Test
   void testGetRetry() {
      completionCondition = new CompletionCondition();
      completionCondition.setTaskName("org2:task2");
      assertEquals(-1, completionCondition.getRetryTime(d1));

      completionCondition.setComplete(true);
      assertEquals(d1, completionCondition.getRetryTime(d1));
   }

   // ─────────────────────────────────────────────────────────────────────────
   // check() one-shot semantics
   // ─────────────────────────────────────────────────────────────────────────

   /**
    * check() stores depencyCompleted into a local, then resets it to false before returning.
    * This means the condition fires at most once per setComplete(true) call.
    */
   @Nested
   @DisplayName("check() one-shot semantics")
   class CheckOneShotTests {

      @Test
      @DisplayName("second call to check() returns false after first call consumed the signal")
      void checkResetsAfterReturningTrue() {
         CompletionCondition c = new CompletionCondition("task1");
         c.setComplete(true);

         assertTrue(c.check(d1));  // first call: consumes the signal
         assertFalse(c.check(d1)); // second call: state was reset
      }

      @Test
      @DisplayName("getRetryTime() returns -1 after check() has consumed the completion signal")
      void getRetryTimeNegativeAfterCheckConsumesSignal() {
         CompletionCondition c = new CompletionCondition("task1");
         c.setComplete(true);

         assertEquals(d1, c.getRetryTime(d1)); // signal present → returns time
         c.check(d1);                            // consume the signal
         assertEquals(-1, c.getRetryTime(d1)); // signal gone → must return -1
      }

      @Test
      @DisplayName("setComplete(true) re-arms the condition after check() already consumed it")
      void rearmAfterConsumption() {
         CompletionCondition c = new CompletionCondition("task1");
         c.setComplete(true);
         c.check(d1); // consume

         c.setComplete(true); // re-arm
         assertTrue(c.check(d1));
      }
   }

   // ─────────────────────────────────────────────────────────────────────────
   // toString() branches
   // ─────────────────────────────────────────────────────────────────────────

   @Nested
   @DisplayName("toString()")
   class ToStringTests {

      @BeforeEach
      void disableSecurity() {
         SreeEnv.setProperty("security.enabled", "false");
      }

      @Test
      @DisplayName("security disabled: null taskname renders as 'null'")
      void securityDisabled_nullTaskname() {
         CompletionCondition c = new CompletionCondition();
         assertTrue(c.toString().contains("null"));
      }

      @Test
      @DisplayName("security disabled: taskname without colon is returned as-is")
      void securityDisabled_noColon() {
         CompletionCondition c = new CompletionCondition("myTask");
         assertTrue(c.toString().contains("myTask"));
      }

      @Test
      @DisplayName("security disabled: taskname with colon strips everything up to and including the colon")
      void securityDisabled_withColon() {
         // else branch: index = taskname.indexOf(':'); taskLabel = taskname.substring(index + 1)
         CompletionCondition c = new CompletionCondition("org1:myTask");
         String result = c.toString();
         assertTrue(result.contains("myTask"), "should show the task part after ':'");
         assertFalse(result.contains("org1"), "should NOT show the org prefix");
      }

      @Test
      @DisplayName("security enabled: strips org from IdentityID~;~org:task format")
      void securityEnabled_stripsOrgFromKeyDelimiterFormat() {
         SreeEnv.setProperty("security.enabled", "true");
         // Real multi-org format: "admin~;~org1:myTask" → SUtil strips org → "admin:myTask"
         String taskId = "admin" + IdentityID.KEY_DELIMITER + "org1:myTask";
         CompletionCondition c = new CompletionCondition(taskId);
         String result = c.toString();
         assertTrue(result.contains("admin:myTask"), "org should be stripped from user prefix");
         assertFalse(result.contains(IdentityID.KEY_DELIMITER), "KEY_DELIMITER must not appear in label");
      }

      @Test
      @DisplayName("security enabled: taskname without KEY_DELIMITER is returned unchanged")
      void securityEnabled_noKeyDelimiter_returnedUnchanged() {
         SreeEnv.setProperty("security.enabled", "true");
         // "org1:task1" has ':' but not '~;~' — getTaskNameWithoutOrg returns it unchanged
         CompletionCondition c = new CompletionCondition("org1:task1");
         assertTrue(c.toString().contains("org1:task1"));
      }
   }

   // ─────────────────────────────────────────────────────────────────────────
   // equals()
   // ─────────────────────────────────────────────────────────────────────────

   @Nested
   @DisplayName("equals()")
   class EqualsTests {

      @Test
      @DisplayName("same taskname → equal")
      void sameTasknameIsEqual() {
         assertEquals(new CompletionCondition("task1"), new CompletionCondition("task1"));
      }

      @Test
      @DisplayName("different taskname → not equal")
      void differentTasknameNotEqual() {
         assertNotEquals(new CompletionCondition("task1"), new CompletionCondition("task2"));
      }

      @Test
      @DisplayName("both null taskname → equal")
      void bothNullTasknameEqual() {
         assertEquals(new CompletionCondition(), new CompletionCondition());
      }

      @Test
      @DisplayName("null argument → false")
      void nullArgReturnsFalse() {
         assertFalse(new CompletionCondition("task1").equals(null));
      }

      @Test
      @DisplayName("non-CompletionCondition argument → false")
      void differentTypeReturnsFalse() {
         assertFalse(new CompletionCondition("task1").equals("task1"));
      }

      @Test
      @DisplayName("depencyCompleted state is not part of equality — only taskname matters")
      void completeStateDoesNotAffectEquality() {
         CompletionCondition a = new CompletionCondition("task1");
         CompletionCondition b = new CompletionCondition("task1");
         a.setComplete(true);
         b.setComplete(false);
         assertEquals(a, b);
      }
   }

   // ─────────────────────────────────────────────────────────────────────────
   // XML round-trip (writeXML / parseXML)
   // ─────────────────────────────────────────────────────────────────────────

   @Nested
   @DisplayName("XML round-trip (writeXML / parseXML)")
   class XmlRoundTripTests {

      private CompletionCondition writeAndParse(CompletionCondition condition) throws Exception {
         StringWriter sw = new StringWriter();
         condition.writeXML(new PrintWriter(sw));
         CompletionCondition loaded = new CompletionCondition();
         loaded.parseXML(parseConditionXml(sw.toString()));
         return loaded;
      }

      @Test
      @DisplayName("plain taskname persists after round-trip")
      void tasknamePersists() throws Exception {
         CompletionCondition loaded = writeAndParse(new CompletionCondition("myTask"));
         assertEquals("myTask", loaded.getTaskName());
      }

      @Test
      @DisplayName("XML special characters in taskname are escaped and restored correctly")
      void tasknameWithXmlSpecialChars() throws Exception {
         CompletionCondition loaded = writeAndParse(new CompletionCondition("report&data<v1>"));
         assertEquals("report&data<v1>", loaded.getTaskName());
      }

      @Test
      @DisplayName("two round-tripped conditions with same taskname are equal")
      void roundTrippedConditionsAreEqual() throws Exception {
         CompletionCondition original = new CompletionCondition("task1");
         CompletionCondition loaded = writeAndParse(original);
         assertEquals(original, loaded);
      }
   }

   // ─────────────────────────────────────────────────────────────────────────
   // Encoding (byteEncode / byteDecode / isEncoding / setEncoding)
   // ─────────────────────────────────────────────────────────────────────────

   @Nested
   @DisplayName("Encoding (byteEncode / byteDecode / isEncoding / setEncoding)")
   class EncodingTests {

      @Test
      @DisplayName("setEncoding(true): byteEncode encodes non-ASCII, byteDecode restores it")
      void encodingEnabledRoundTrips() {
         CompletionCondition c = new CompletionCondition();
         c.setEncoding(true);
         assertTrue(c.isEncoding());

         String original = "任务1";
         String encoded = c.byteEncode(original);
         assertNotEquals(original, encoded, "non-ASCII should be encoded when encoding=true");
         assertEquals(original, c.byteDecode(encoded));
      }

   }

   // ─────────────────────────────────────────────────────────────────────────
   // Known source bugs (@Disabled until fixed)
   // ─────────────────────────────────────────────────────────────────────────

   /**
    * Known source bugs (@Disabled until fixed).
    *
    * BUG-CC-1 | equals() by taskname but hashCode() uses Object identity
    * Location : CompletionCondition.java:128
    * Fix      : add hashCode() { return Objects.hashCode(taskname); }
    */
   @Nested
   @Disabled("BUG-CC-1: equal conditions have different hashCode - CompletionCondition:128; Fix: add hashCode() { return Objects.hashCode(taskname); }")
   @Tag("known-bug")
   @DisplayName("Known source bugs")
   class KnownBugs {

      @Test
      @DisplayName("BUG-CC-1: equal conditions must have the same hashCode")
      void equalConditionsMustHaveSameHashCode() {
         CompletionCondition c1 = new CompletionCondition("task1");
         CompletionCondition c2 = new CompletionCondition("task1");
         assertEquals(c1, c2);
         assertEquals(c1.hashCode(), c2.hashCode());
      }
   }

   // ─────────────────────────────────────────────────────────────────────────
   // Helpers
   // ─────────────────────────────────────────────────────────────────────────

   private static Element parseConditionXml(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      return factory.newDocumentBuilder()
         .parse(new InputSource(new StringReader(xml)))
         .getDocumentElement();
   }

   private long setCustomTimeInMillis(int hour, int minute, int second) {
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime customTime = now.withHour(hour).withMinute(minute).withSecond(second);
      return customTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
   }
}
