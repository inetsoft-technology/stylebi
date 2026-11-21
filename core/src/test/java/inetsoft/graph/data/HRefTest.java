/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HRef}, the hyperlink value object of the chart data set: link, target frame, tip and
 * parameters.
 */
@Tag("core")
class HRefTest {
   private static List<String> names(HRef ref) {
      List<String> names = new ArrayList<>();

      for(Enumeration<String> e = ref.getParameterNames(); e.hasMoreElements();) {
         names.add(e.nextElement());
      }

      Collections.sort(names);
      return names;
   }

   private static HRef ref(String link) {
      return new HRef(link);
   }

   private static void assertEqualBothWays(HRef a, HRef b) {
      assertTrue(a.equals(b), "a.equals(b)");
      assertTrue(b.equals(a), "b.equals(a)");
   }

   private static void assertDifferentBothWays(HRef a, HRef b) {
      assertFalse(a.equals(b), "a.equals(b)");
      assertFalse(b.equals(a), "b.equals(a)");
   }

   @Test
   void constructorWithLinkSetsLinkNameAndDefaults() {
      HRef ref = ref("u");
      assertEquals("u", ref.getLink());
      assertEquals("u", ref.getName());
      assertEquals("", ref.getTargetFrame());
      assertNull(ref.getToolTip());
      assertEquals(0, ref.getParameterCount());
   }

   @Test
   void nameIsTheLink() {
      HRef ref = new HRef();
      assertNull(ref.getLink());
      assertNull(ref.getName());
      ref.setLink("u2");
      assertEquals("u2", ref.getLink());
      assertEquals("u2", ref.getName());
   }

   @Test
   void parametersAreStoredByName() {
      HRef ref = ref("u");
      ref.setParameter("a", 1);
      ref.setParameter("b", "x");
      assertEquals(2, ref.getParameterCount());
      assertEquals(1, ref.getParameter("a"));
      assertEquals("x", ref.getParameter("b"));
      assertNull(ref.getParameter("missing"));
      assertEquals(List.of("a", "b"), names(ref));
   }

   @Test
   void settingAParameterAgainOverwritesIt() {
      HRef ref = ref("u");
      ref.setParameter("a", 1);
      ref.setParameter("a", 2);
      assertEquals(1, ref.getParameterCount());
      assertEquals(2, ref.getParameter("a"));
   }

   @Test
   void settingANullValueRemovesTheParameter() {
      HRef ref = ref("u");
      ref.setParameter("a", 1);
      ref.setParameter("b", 2);
      ref.setParameter("a", null);
      assertEquals(1, ref.getParameterCount());
      assertNull(ref.getParameter("a"));
      assertEquals(List.of("b"), names(ref));
   }

   @Test
   void settingANullValueOnAnEmptyRefIsHarmless() {
      HRef ref = ref("u");
      assertDoesNotThrow(() -> ref.setParameter("z", null));
      assertEquals(0, ref.getParameterCount());
      assertNull(ref.getParameter("z"));
   }

   @Test
   void removeParameterRemovesOnlyThatName() {
      HRef ref = ref("u");
      ref.setParameter("a", 1);
      ref.setParameter("b", 2);
      ref.removeParameter("b");
      assertEquals(1, ref.getParameterCount());
      assertEquals(List.of("a"), names(ref));
      ref.removeParameter("nope");
      assertEquals(1, ref.getParameterCount());
      assertEquals(List.of("a"), names(ref));
      ref.removeParameter("a");
      assertEquals(0, ref.getParameterCount());
      assertEquals(List.of(), names(ref));
   }

   @Test
   void removeAllParametersClearsEverything() {
      HRef ref = ref("u");
      ref.setParameter("a", 1);
      ref.setParameter("b", 2);
      ref.removeAllParameters();
      assertEquals(0, ref.getParameterCount());
      assertEquals(List.of(), names(ref));
      assertNull(ref.getParameter("a"));
   }

   @Test
   void parameterNamesOfARefWithoutParametersAreEmpty() {
      Enumeration<String> names = ref("u").getParameterNames();
      assertNotNull(names);
      assertFalse(names.hasMoreElements());
   }

   @Test
   void equalsBasics() {
      HRef h = ref("u");
      assertTrue(h.equals(h));
      assertFalse(h.equals(null));
      assertFalse(h.equals("u"));
      assertTrue(h.equals(ref("u")));
   }

   @Test
   void equalsComparesTheLink() {
      assertDifferentBothWays(ref("u"), ref("v"));
      assertDifferentBothWays(ref("u"), new HRef());
      assertEqualBothWays(new HRef(), new HRef());
   }

   @Test
   void equalsComparesTheTip() {
      HRef a = ref("u");
      a.setToolTip("t");
      HRef none = ref("u");
      HRef other = ref("u");
      other.setToolTip("t2");
      HRef same = ref("u");
      same.setToolTip("t");
      assertDifferentBothWays(a, none);
      assertDifferentBothWays(a, other);
      assertEqualBothWays(a, same);
   }

   @Test
   void equalsComparesTheTargetFrame() {
      HRef a = ref("u");
      a.setTargetFrame("_blank");
      HRef empty = ref("u");
      HRef other = ref("u");
      other.setTargetFrame("_self");
      HRef same = ref("u");
      same.setTargetFrame("_blank");
      assertDifferentBothWays(a, empty);
      assertDifferentBothWays(a, other);
      assertEqualBothWays(a, same);
   }

   @Test
   void equalsComparesTheParameters() {
      HRef a1 = ref("u");
      a1.setParameter("a", 1);
      HRef a2 = ref("u");
      a2.setParameter("a", 2);
      HRef none = ref("u");
      HRef ab = ref("u");
      ab.setParameter("a", 1);
      ab.setParameter("b", 2);
      HRef again = ref("u");
      again.setParameter("a", 1);
      assertDifferentBothWays(a1, a2);
      assertDifferentBothWays(a1, none);
      assertDifferentBothWays(a1, ab);
      assertEqualBothWays(a1, again);
   }

   @Test
   void equalsIgnoresTheOrderParametersWereSet() {
      HRef ab = ref("u");
      ab.setParameter("a", 1);
      ab.setParameter("b", 2);
      HRef ba = ref("u");
      ba.setParameter("b", 2);
      ba.setParameter("a", 1);
      assertEqualBothWays(ab, ba);
   }

   @Test
   void emptiedRefsHaveNoParameterAnyRoute() {
      HRef fresh = ref("u");
      HRef viaNull = ref("u");
      viaNull.setParameter("a", 1);
      viaNull.setParameter("a", null);
      HRef viaRemove = ref("u");
      viaRemove.setParameter("a", 1);
      viaRemove.removeParameter("a");
      assertEquals(0, fresh.getParameterCount());
      assertEquals(0, viaNull.getParameterCount());
      assertEquals(0, viaRemove.getParameterCount());
   }

   @Test
   @Disabled("Not filed (not reachable: production never sets a parameter and then sets it to null, and nobody compares HRefs): a ref whose parameters were emptied by setParameter(name, null) is not equal to a fresh ref: "
      + "expected equal in both directions, actual fresh.equals(viaNull) false (empty Hashtable against null)")
   @DisplayName("refs without parameters are equal whichever way the parameters were emptied")
   void emptiedRefsAreEqualWhicheverRoute() {
      HRef fresh = ref("u");
      HRef viaNull = ref("u");
      viaNull.setParameter("a", 1);
      viaNull.setParameter("a", null);
      HRef viaRemove = ref("u");
      viaRemove.setParameter("a", 1);
      viaRemove.removeParameter("a");
      assertEqualBothWays(fresh, viaNull);
      assertEqualBothWays(fresh, viaRemove);
      assertEqualBothWays(viaNull, viaRemove);
   }

   @Test
   @DisplayName("pinned: a null target frame is different from the empty default")
   void nullFrameIsDifferentFromEmpty() {
      HRef nullFrame = ref("u");
      nullFrame.setTargetFrame(null);
      assertDifferentBothWays(nullFrame, ref("u"));
   }

   @Test
   @Disabled("Not filed (not safe to fix alone: PDF3Generator.java:629 keys a LinkedHashMap by Hyperlink.Ref, a content hashCode would merge link areas): HRef overrides equals without hashCode: expected equal hash codes for equal refs, "
      + "actual different identity hash codes")
   @DisplayName("equal refs have equal hash codes and collapse in a hash set")
   void equalRefsHaveEqualHashCodes() {
      HRef a = ref("u");
      a.setToolTip("t");
      a.setTargetFrame("f");
      a.setParameter("a", 1);
      HRef b = ref("u");
      b.setToolTip("t");
      b.setTargetFrame("f");
      b.setParameter("a", 1);
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
      assertEquals(1, new HashSet<>(List.of(a, b)).size());
   }

   @Test
   void serializedCopyIsEqualAndKeepsEveryField() throws Exception {
      HRef ref = ref("u");
      ref.setToolTip("tip");
      ref.setTargetFrame("frame");
      ref.setParameter("a", 1);
      ref.setParameter("b", "x");
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(ref);
      }

      HRef copy;

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         copy = (HRef) in.readObject();
      }

      assertEquals(ref, copy);
      assertEquals("u", copy.getLink());
      assertEquals("tip", copy.getToolTip());
      assertEquals("frame", copy.getTargetFrame());
      assertEquals(2, copy.getParameterCount());
      assertEquals(1, copy.getParameter("a"));
      assertEquals("x", copy.getParameter("b"));
   }

   @Test
   void getParameterWithNullNameWithoutParametersIsNull() {
      assertNull(ref("u").getParameter(null));
   }

   @Test
   @Disabled("Not filed (not reachable: parameter names are never null): getParameter(null) throws NullPointerException when the ref has parameters "
      + "(Hashtable.get(null)); expected null")
   @DisplayName("getParameter with a null name is null when the ref has parameters too")
   void getParameterWithNullNameWithParametersIsNull() {
      HRef ref = ref("u");
      ref.setParameter("a", 1);
      assertNull(ref.getParameter(null));
   }

   @Test
   void tipAndFrameAccessorsRoundTrip() {
      HRef ref = ref("u");
      ref.setToolTip("t");
      ref.setTargetFrame("f");
      assertEquals("t", ref.getToolTip());
      assertEquals("f", ref.getTargetFrame());
      ref.setToolTip(null);
      ref.setTargetFrame(null);
      assertNull(ref.getToolTip());
      assertNull(ref.getTargetFrame());
   }
}
