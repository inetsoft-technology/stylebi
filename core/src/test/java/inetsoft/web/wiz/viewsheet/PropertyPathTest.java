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
package inetsoft.web.wiz.viewsheet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class PropertyPathTest {
   public enum Alignment { LEFT, CENTER, RIGHT }

   public static class Leaf {
      public boolean isVisible() { return visible; }
      public void setVisible(boolean visible) { this.visible = visible; }
      public String getTitle() { return title; }
      public void setTitle(String title) { this.title = title; }
      public int getMax() { return max; }
      public void setMax(int max) { this.max = max; }
      public double getRatio() { return ratio; }
      public void setRatio(double ratio) { this.ratio = ratio; }
      public Alignment getAlign() { return align; }
      public void setAlign(Alignment align) { this.align = align; }
      public String getReadOnly() { return "fixed"; }

      private boolean visible;
      private String title;
      private int max;
      private double ratio;
      private Alignment align;
   }

   public static class Middle {
      public Leaf getLeaf() { return leaf; }
      public void setLeaf(Leaf leaf) { this.leaf = leaf; }

      private Leaf leaf = new Leaf();
   }

   public static class Root {
      public Middle getMiddle() { return middle; }
      public void setMiddle(Middle middle) { this.middle = middle; }
      public Middle getAbsent() { return null; }

      private Middle middle = new Middle();
   }

   // ── reading ───────────────────────────────────────────────────────────────

   @Test
   void readsANestedPath() {
      Root root = new Root();
      root.getMiddle().getLeaf().setTitle("Sales");

      assertEquals("Sales", PropertyPath.get(root, "middle.leaf.title"));
   }

   @Test
   void readsABooleanThroughItsIsGetter() {
      Root root = new Root();
      root.getMiddle().getLeaf().setVisible(true);

      assertEquals(Boolean.TRUE, PropertyPath.get(root, "middle.leaf.visible"));
   }

   @Test
   void readsNullWhenAnIntermediateIsAbsent() {
      assertNull(PropertyPath.get(new Root(), "absent.leaf.title"));
   }

   // ── writing ───────────────────────────────────────────────────────────────

   @Test
   void writesANestedPath() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.title", "Revenue");

      assertEquals("Revenue", root.getMiddle().getLeaf().getTitle());
   }

   /**
    * The whole point of this class: a path that quietly no-ops reports success while changing
    * nothing, which is the defect the plugin family exists to avoid.
    */
   /**
    * `visible` is a String-typed enum in disguise. {@code VSAssemblyInfo} declares its whole domain
    * as {@code {"true","show","false","hide","hide on print and export"}} and maps anything else to
    * the default — so {@code visible: "no"} returned ok, stored "no", and left the assembly on
    * screen. The boolean guard in coerce never fired because the target type is String.
    */
   public static class StringVisible {
      public String getVisible() { return visible; }
      public void setVisible(String visible) { this.visible = visible; }

      private String visible = "show";
   }

   @Test
   void refusesAnAmbiguousVisibleTokenRatherThanLettingStyleBIIgnoreIt() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new StringVisible(), "visible", "no"));

      assertTrue(thrown.getMessage().contains("visible"), "name the property");
      assertTrue(thrown.getMessage().contains("hide"), "list the tokens that do work");
   }

   @Test
   void acceptsEveryVisibleTokenStyleBIUnderstands() {
      for(String token : new String[]{ "show", "hide", "true", "false",
                                       "hide on print and export" })
      {
         StringVisible target = new StringVisible();
         PropertyPath.set(target, "visible", token);
         assertEquals(token, target.getVisible(), token + " is a documented value");
      }
   }

   @Test
   void normalizesABooleanOntoTheStringTypedVisibleProperty() {
      StringVisible target = new StringVisible();
      PropertyPath.set(target, "visible", false);
      assertEquals("false", target.getVisible());
   }

   // ── Immutables support ────────────────────────────────────────────────────
   //
   // Five dialog models are Immutables: accessors are bare (imageGeneralPaneModel(), no "get")
   // and there are no setters, only withX() returning a NEW instance. PropertyPath read get/is
   // and wrote setX, so it could not touch them at all — which is why image and VS-level
   // properties were "not covered". Writing a leaf means rebuilding every immutable level above
   // it, from the leaf upward.

   /** Immutable leaf: no setter, only a wither returning a new instance. */
   public static final class ImmutableLeaf {
      ImmutableLeaf(String title, boolean visible) {
         this.title = title;
         this.visible = visible;
      }

      public String title() { return title; }
      public boolean visible() { return visible; }
      public ImmutableLeaf withTitle(String value) { return new ImmutableLeaf(value, visible); }
      public ImmutableLeaf withVisible(boolean value) { return new ImmutableLeaf(title, value); }

      private final String title;
      private final boolean visible;
   }

   /** Immutable middle holding an immutable leaf. */
   public static final class ImmutableMiddle {
      ImmutableMiddle(ImmutableLeaf leaf) { this.leaf = leaf; }

      public ImmutableLeaf leaf() { return leaf; }
      public ImmutableMiddle withLeaf(ImmutableLeaf value) { return new ImmutableMiddle(value); }

      private final ImmutableLeaf leaf;
   }

   /** Mutable root — the realistic shape: a mutable holder of an immutable tree. */
   public static class MutableRoot {
      public ImmutableMiddle getMiddle() { return middle; }
      public void setMiddle(ImmutableMiddle middle) { this.middle = middle; }

      private ImmutableMiddle middle =
         new ImmutableMiddle(new ImmutableLeaf("original", false));
   }

   @Test
   void readsThroughABareImmutablesAccessor() {
      MutableRoot root = new MutableRoot();

      assertEquals("original", PropertyPath.get(root, "middle.leaf.title"));
      assertEquals(false, PropertyPath.get(root, "middle.leaf.visible"));
   }

   @Test
   void writesAnImmutableLeafByRebuildingEveryLevelAboveIt() {
      MutableRoot root = new MutableRoot();

      PropertyPath.set(root, "middle.leaf.title", "rebuilt");

      assertEquals("rebuilt", PropertyPath.get(root, "middle.leaf.title"),
                   "the rebuilt instances must be stored back up the chain");
      assertEquals(false, PropertyPath.get(root, "middle.leaf.visible"),
                   "rebuilding one field must not reset its siblings");
   }

   @Test
   void coercesOntoAnImmutableWither() {
      MutableRoot root = new MutableRoot();

      PropertyPath.set(root, "middle.leaf.visible", "true");

      assertEquals(true, PropertyPath.get(root, "middle.leaf.visible"),
                   "a wither takes the same coercion as a setter");
   }

   @Test
   void refusesAnUnknownLeafOnAnImmutableOwnerNamingWhatExists() {
      MutableRoot root = new MutableRoot();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(root, "middle.leaf.nope", "x"));
      assertTrue(thrown.getMessage().contains("nope"));
   }

   @Test
   void refusesAnUnknownLeafRatherThanSilentlyDoingNothing() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middle.leaf.nope", "x"));

      assertTrue(thrown.getMessage().contains("nope"));
      assertTrue(thrown.getMessage().contains("title"), "name what was available instead");
   }

   @Test
   void refusesAnUnknownIntermediateNamingTheSegment() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middel.leaf.title", "x"));

      assertTrue(thrown.getMessage().contains("middel"));
   }

   /**
    * An absent pane means the property does not apply to this assembly. Instantiating one
    * would fabricate a shape the composer service never produced.
    */
   @Test
   void refusesToFabricateAnAbsentIntermediate() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "absent.leaf.title", "x"));

      assertTrue(thrown.getMessage().contains("does not apply"));
   }

   @Test
   void refusesAReadOnlyProperty() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middle.leaf.readOnly", "x"));

      assertTrue(thrown.getMessage().contains("readOnly"));
   }

   @Test
   void refusesAnEmptyPath() {
      assertThrows(IllegalArgumentException.class, () -> PropertyPath.set(new Root(), "", "x"));
   }

   // ── coercion: forgiving where unambiguous ─────────────────────────────────

   @Test
   void acceptsAStringSpellingOfABoolean() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.visible", "true");

      assertTrue(root.getMiddle().getLeaf().isVisible());
   }

   @Test
   void acceptsARealBoolean() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.visible", Boolean.TRUE);

      assertTrue(root.getMiddle().getLeaf().isVisible());
   }

   @Test
   void acceptsANumericString() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.max", "100");

      assertEquals(100, root.getMiddle().getLeaf().getMax());
   }

   @Test
   void narrowsAJsonDoubleOntoAnIntField() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.max", 100.0);

      assertEquals(100, root.getMiddle().getLeaf().getMax());
   }

   @Test
   void widensAnIntOntoADoubleField() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.ratio", 2);

      assertEquals(2.0, root.getMiddle().getLeaf().getRatio());
   }

   @Test
   void matchesAnEnumTokenInAnyCase() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.align", "center");

      assertEquals(Alignment.CENTER, root.getMiddle().getLeaf().getAlign());
   }

   // ── coercion: loud otherwise ──────────────────────────────────────────────

   @Test
   void refusesAnAmbiguousBooleanSpellingRatherThanGuessing() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middle.leaf.visible", "yes"));

      assertTrue(thrown.getMessage().contains("yes"));
      assertTrue(thrown.getMessage().contains("true"));
   }

   @Test
   void refusesANonNumericStringForANumber() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middle.leaf.max", "lots"));

      assertTrue(thrown.getMessage().contains("lots"));
   }

   @Test
   void refusesAnUnknownEnumTokenListingTheValid() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middle.leaf.align", "middle"));

      assertTrue(thrown.getMessage().contains("CENTER"));
   }

   @Test
   void refusesNullForAPrimitive() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middle.leaf.visible", null));

      assertTrue(thrown.getMessage().contains("null"));
   }

   // ── introspection, used by the registry's invariant test ──────────────────

   @Test
   void reportsThePathsDeclaredType() {
      assertEquals(boolean.class, PropertyPath.typeOf(Root.class, "middle.leaf.visible"));
      assertEquals(String.class, PropertyPath.typeOf(Root.class, "middle.leaf.title"));
   }

   @Test
   void typeOfRefusesAPathThatDoesNotExist() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.typeOf(Root.class, "middle.leaf.nope"));

      assertTrue(thrown.getMessage().contains("nope"));
   }

   @Test
   void listsReadablePropertiesForDiscovery() {
      List<String> properties = PropertyPath.propertiesOf(Leaf.class);

      assertTrue(properties.contains("visible"));
      assertTrue(properties.contains("title"));
      assertFalse(properties.contains("class"), "Object's own getters are not properties");
   }
}
