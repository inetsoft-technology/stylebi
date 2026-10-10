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

import inetsoft.report.StyleConstants;
import inetsoft.report.internal.Util;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.GradientColor;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// The bug #76888/#76997 tests build the real DynamicValueModel, whose String constructor calls
// VSUtil -- and VSUtil's static init needs the same running context AssemblyPropertyServiceTest
// sets up; a bare test has no Spring context for it to find.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
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
      public long getBigMax() { return bigMax; }
      public void setBigMax(long bigMax) { this.bigMax = bigMax; }
      public double getRatio() { return ratio; }
      public void setRatio(double ratio) { this.ratio = ratio; }
      public float getScale() { return scale; }
      public void setScale(float scale) { this.scale = scale; }
      public Alignment getAlign() { return align; }
      public void setAlign(Alignment align) { this.align = align; }
      public String getReadOnly() { return "fixed"; }

      private boolean visible;
      private String title;
      private int max;
      private long bigMax;
      private double ratio;
      private float scale;
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

   /**
    * {@code visible} is backed by a {@code DynamicValue} and StyleBI's own Composer UI offers a
    * "Variable" button on it, so a well-formed {@code $(ComponentName)} reference must pass this
    * closed-value gate unresolved -- resolution happens at render time
    * ({@code ViewsheetSandbox.executeDynamicValue}), never here. Existence of the named
    * assembly is deliberately not checked at this layer (see below) -- that is the plugin's job.
    */
   @Test
   void acceptsADynamicReferenceForVisible() {
      StringVisible target = new StringVisible();

      PropertyPath.set(target, "visible", "$(RadioButton1)");

      assertEquals("$(RadioButton1)", target.getVisible(),
                   "a dynamic reference passes through unresolved, not one of the enum tokens");
   }

   /**
    * PropertyPath has no access to the viewsheet's assembly list, so it cannot and must not
    * check whether the referenced component actually exists -- that check belongs to the
    * plugin (existence/type validation against a live model), a separate layer entirely.
    */
   @Test
   void acceptsADynamicReferenceForVisibleRegardlessOfWhetherTheNamedAssemblyExists() {
      StringVisible target = new StringVisible();

      PropertyPath.set(target, "visible", "$(Whatever)");

      assertEquals("$(Whatever)", target.getVisible(),
                   "existence of the named assembly is checked elsewhere, not by this gate");
   }

   /**
    * The other half of the same {@code DynamicValue} convention: StyleBI's Composer offers an
    * "Expression" button beside the "Variable" one on {@code visible}, and
    * {@code VSUtil.isScriptValue} is simply a leading {@code "="}. An expression is the only way to
    * express a DERIVED value -- {@code $(Name)} can only substitute one component's value verbatim
    * -- so this gate must let it through unresolved for the same reason it lets a variable through.
    */
   @Test
   void acceptsAnExpressionForVisible() {
      StringVisible target = new StringVisible();

      PropertyPath.set(target, "visible", "=Gauge1 > 20");

      assertEquals("=Gauge1 > 20", target.getVisible(),
                   "an expression passes through unresolved, not one of the enum tokens");
   }

   /**
    * The gate is opened by the property being {@code DynamicValue}-backed, not by the value's
    * shape: {@code trendLineType} resolves once, design-time, into an index that no
    * {@code executeDynamicValue} path ever touches, so an expression there would be stored and
    * never resolved -- exactly the silent-success failure this gate exists to prevent.
    */
   @Test
   void refusesAnExpressionForAPropertyThatIsNotDynamicValueBacked() {
      ChartLine target = new ChartLine();

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(target, "trendLineType", "=Gauge1 > 20"));

      assertTrue(thrown.getMessage().contains("trendLineType"), "name the property");
   }

   /** A String-typed closed domain that is NOT DynamicValue-backed, mirroring
    *  {@code ChartLinePaneModel.trendLineType}. */
   public static final class ChartLine {
      public String getTrendLineType() { return trendLineType; }
      public void setTrendLineType(String value) { this.trendLineType = value; }

      private String trendLineType = "NONE";
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

   /**
    * A root that is itself immutable, with no mutable holder anywhere above it -- the exact
    * shape {@code ViewsheetPropertyDialogModel} takes for {@code width}/{@code height}/
    * {@code preview}: a direct top-level scalar with only a wither, no nested pane to absorb the
    * rebuild. Every other Immutables case in this suite nests the immutable at least one level
    * under a mutable holder, so the wither's new instance always has somewhere mutable to be
    * written back into. Here there is nothing above the root, so the only way the caller can see
    * the new value is if {@code set} hands the (possibly rebuilt) root back.
    */
   public static final class ImmutableScalarRoot {
      ImmutableScalarRoot(int width) { this.width = width; }

      public int width() { return width; }
      public ImmutableScalarRoot withWidth(int value) { return new ImmutableScalarRoot(value); }

      private final int width;
   }

   @Test
   void rebuildingAnImmutableRootReturnsTheNewInstance() {
      ImmutableScalarRoot root = new ImmutableScalarRoot(0);

      Object result = PropertyPath.set(root, "width", 100);

      assertNotSame(root, result, "the root itself had only a wither, so it had to be rebuilt");
      assertEquals(100, ((ImmutableScalarRoot) result).width());
   }

   @Test
   void theOriginalImmutableRootIsLeftUnchanged() {
      ImmutableScalarRoot root = new ImmutableScalarRoot(0);

      PropertyPath.set(root, "width", 100);

      assertEquals(0, root.width(),
                   "immutable -- a caller that ignores the returned instance and keeps using " +
                   "the original would silently lose the write");
   }

   @Test
   void writingAMutableRootReturnsThatSameRoot() {
      Root root = new Root();

      Object result = PropertyPath.set(root, "middle.leaf.title", "Revenue");

      assertSame(root, result, "a mutable root is updated in place, not replaced");
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
   void acceptsAnIntegerStringOntoALongField() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.bigMax", "5000");

      assertEquals(5000L, root.getMiddle().getLeaf().getBigMax());
   }

   @Test
   void acceptsAnIntegralDoubleOntoALongField() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.bigMax", 5000.0);

      assertEquals(5000L, root.getMiddle().getLeaf().getBigMax());
   }

   @Test
   void widensAnIntOntoADoubleField() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.ratio", 2);

      assertEquals(2.0, root.getMiddle().getLeaf().getRatio());
   }

   /**
    * double/float targets are untouched by the whole-number guard -- they have no narrower
    * range to clamp into and are meant to hold a fraction.
    */
   @Test
   void acceptsAFractionalDoubleOntoADoubleField() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.ratio", 5000.7);

      assertEquals(5000.7, root.getMiddle().getLeaf().getRatio());
   }

   @Test
   void acceptsAFractionalValueOntoAFloatField() {
      Root root = new Root();

      PropertyPath.set(root, "middle.leaf.scale", "5000.7");

      assertEquals(5000.7f, root.getMiddle().getLeaf().getScale());
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

   /**
    * Regression for bug #77041: {@code maxRows: 5000.7} used to be silently truncated onto
    * {@code 5000} with no error -- a plausible-looking number, reported as a success, that was
    * not what the caller asked for.
    */
   @Test
   void refusesANonIntegralDoubleOntoAnIntFieldRatherThanTruncating() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middle.leaf.max", 5000.7));

      assertTrue(thrown.getMessage().contains("middle.leaf.max"));
      assertTrue(thrown.getMessage().contains("5000.7"));
      assertEquals(0, new Root().getMiddle().getLeaf().getMax());
   }

   @Test
   void refusesANonIntegralNumericStringOntoAnIntField() {
      assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middle.leaf.max", "5000.7"));
   }

   /**
    * Regression for bug #77041: {@code 1e12} used to be silently clamped onto
    * {@code Integer.MAX_VALUE} with no error -- again a plausible-looking number that was not
    * what the caller asked for.
    */
   @Test
   void refusesAnOutOfRangeDoubleOntoAnIntFieldRatherThanClamping() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middle.leaf.max", 1e12));

      assertTrue(thrown.getMessage().contains("middle.leaf.max"));
   }

   @Test
   void refusesANonIntegralDoubleOntoALongField() {
      assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middle.leaf.bigMax", 5000.7));
   }

   @Test
   void refusesAnOutOfRangeDoubleOntoALongField() {
      assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Root(), "middle.leaf.bigMax", 1e30));
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

   // ── accessor-name spellings (the writer/reader lookup fix) ────────────────
   //
   // propertiesOf derives a name by stripping get/is and decapitalizing; the writer lookup used
   // to rebuild it with capitalize. Those are not inverses when a lowercase letter follows the
   // prefix, so ChartLinePaneModel.setxGridLineStyle was invisible: xGridLineStyle was refused as
   // unwritable and listed as available in the same message. Both derivations are accepted now,
   // which has to stay a strict superset -- a name that resolved before must not stop resolving.

   /**
    * Mirrors {@code ChartLinePaneModel}: a lowercase letter straight after the prefix
    * ({@code getxGridLineStyle}) alongside a two-capital accessor ({@code getRTChartType}) that
    * only the rebuilt spelling can reach, since decapitalizing {@code RTChartType} yields
    * {@code rTChartType}.
    */
   public static class AwkwardAccessors {
      public String getxGridLineStyle() { return xGridLineStyle; }
      public void setxGridLineStyle(String style) { this.xGridLineStyle = style; }
      public String getRTChartType() { return rtChartType; }
      public void setRTChartType(String type) { this.rtChartType = type; }

      private String xGridLineStyle = "";
      private String rtChartType = "";
   }

   @Test
   void writesThroughAnAccessorWhoseNameIsLowercaseAfterThePrefix() {
      AwkwardAccessors target = new AwkwardAccessors();

      PropertyPath.set(target, "xGridLineStyle", "THIN_LINE");

      assertEquals("THIN_LINE", target.getxGridLineStyle(),
                   "setxGridLineStyle is the real writer; asking for setXGridLineStyle found nothing");
   }

   @Test
   void readsBackTheSameLowercaseAfterPrefixPropertyItCanWrite() {
      AwkwardAccessors target = new AwkwardAccessors();
      target.setxGridLineStyle("THIN_LINE");

      assertEquals("THIN_LINE", PropertyPath.get(target, "xGridLineStyle"),
                   "writable but unreadable by path would be worse than the bug this repaired");
   }

   @Test
   void theAwkwardNameIsTheOnePropertiesOfAdvertises() {
      assertTrue(PropertyPath.propertiesOf(AwkwardAccessors.class).contains("xGridLineStyle"),
                 "the lookup must accept the spelling the enumeration hands out");
   }

   @Test
   void aTwoCapitalAccessorStillResolvesThroughTheRebuiltSpelling() {
      AwkwardAccessors target = new AwkwardAccessors();

      PropertyPath.set(target, "RTChartType", "BAR");

      assertEquals("BAR", target.getRTChartType(),
                   "decapitalizing RTChartType gives rTChartType, so only the rebuilt form reaches it");
   }

   // ── constrained strings return the domain's own spelling ──────────────────

   /** Mirrors {@code ChartLinePaneModel.trendLineType}. */
   public static class TrendLine {
      public String getTrendLineType() { return trendLineType; }
      public void setTrendLineType(String type) { this.trendLineType = type; }

      private String trendLineType = "NONE";
   }

   /**
    * The check is case-insensitive, so {@code "LINEAR"} passes it. Storing the caller's spelling
    * anyway is no better than not checking: {@code getIndexByName} compares with {@code equals}
    * and starts at 0, so the chart stayed on {@code NONE} while the write reported success.
    */
   @Test
   void storesTheDomainsSpellingNotTheCallersForAConstrainedString() {
      TrendLine target = new TrendLine();

      PropertyPath.set(target, "trendLineType", "LINEAR");

      assertEquals("Linear", target.getTrendLineType(),
                   "StyleBI matches this token exactly; 'LINEAR' would have silently meant NONE");
   }

   @Test
   void anExactlySpelledConstrainedStringIsUnchanged() {
      TrendLine target = new TrendLine();

      PropertyPath.set(target, "trendLineType", "Quadratic");

      assertEquals("Quadratic", target.getTrendLineType());
   }

   @Test
   void refusesATrendLineTypeOutsideTheDomainListingTheValid() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new TrendLine(), "trendLineType", "spline"));

      assertTrue(thrown.getMessage().contains("trendLineType"), "name the property");
      assertTrue(thrown.getMessage().contains("Linear"), "list the tokens that do work");
   }

   /**
    * Regression guard: trendLineType is NOT DynamicValue-backed (ChartLinePaneModel/
    * PlotDescriptor resolve it once, design-time, into an int index) -- a "$(...)" string here
    * would be stored and never resolved by anything, so the dynamic-reference bypass added for
    * visible must not extend to this property.
    */
   @Test
   void stillRefusesADynamicReferenceForTrendLineType() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new TrendLine(), "trendLineType", "$(RadioButton1)"));

      assertTrue(thrown.getMessage().contains("trendLineType"), "name the property");
      assertTrue(thrown.getMessage().contains("Linear"), "list the tokens that do work");
   }

   /** Mirrors {@code LegendFormatGeneralPaneModel.position}. */
   public static class Position {
      public String getPosition() { return position; }
      public void setPosition(String position) { this.position = position; }

      private String position = "Top";
   }

   /**
    * {@code LegendFormatDialogModel.getIndexByName} has the identical starts-at-0-on-no-match bug
    * as {@code trendLineType}'s: {@code set_chart_region_properties}'s own docstring example,
    * {@code {position: "right"}} (lowercase), silently landed on {@code "Top"} instead of
    * {@code "Right"}. Found live 2026-09-02.
    */
   @Test
   void storesTheDomainsSpellingNotTheCallersForLegendPosition() {
      Position target = new Position();

      PropertyPath.set(target, "position", "right");

      assertEquals("Right", target.getPosition(),
                   "LegendFormatDialogModel.getIndexByName compares with equals() and starts at " +
                   "0 ('Top'); 'right' would have silently meant 'Top'");
   }

   @Test
   void refusesALegendPositionOutsideTheDomainListingTheValid() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Position(), "position", "sideways"));

      assertTrue(thrown.getMessage().contains("position"), "name the property");
      assertTrue(thrown.getMessage().contains("Right"), "list the tokens that do work");
   }

   /**
    * Regression guard: position is NOT DynamicValue-backed (LegendFormatDialogModel resolves it
    * once, design-time, into an int index) -- same reasoning as trendLineType above.
    */
   @Test
   void stillRefusesADynamicReferenceForLegendPosition() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new Position(), "position", "$(RadioButton1)"));

      assertTrue(thrown.getMessage().contains("position"), "name the property");
      assertTrue(thrown.getMessage().contains("Right"), "list the tokens that do work");
   }

   @Test
   void anUnconstrainedStringKeepsTheCallersSpelling() {
      Leaf leaf = new Leaf();

      PropertyPath.set(leaf, "title", "MiXeD Case");

      assertEquals("MiXeD Case", leaf.getTitle(),
                   "only names with a declared domain get canonicalized");
   }

   // ── arrays ────────────────────────────────────────────────────────────────

   /**
    * Mirrors {@code RangePaneModel.rangeValues}/{@code rangeColorValues} — a {@code String[]}
    * raw path with no alias, so this is the only way to reach it.
    */
   public static class StringArrayHolder {
      public String[] getValues() { return values; }
      public void setValues(String[] values) { this.values = values; }

      private String[] values;
   }

   public static class IntArrayHolder {
      public int[] getValues() { return values; }
      public void setValues(int[] values) { this.values = values; }

      private int[] values;
   }

   @Test
   void setsAStringArrayFromAJsonArrayOfStrings() {
      StringArrayHolder target = new StringArrayHolder();

      PropertyPath.set(target, "values", List.of("60", "90", "100"));

      assertArrayEquals(new String[]{ "60", "90", "100" }, target.getValues());
   }

   /**
    * The exact reported defect: Jackson deserializes a JSON array of numbers into a
    * {@code List<Integer>}, which must still coerce onto a {@code String[]} target.
    */
   @Test
   void setsAStringArrayFromAJsonArrayOfNumbers() {
      StringArrayHolder target = new StringArrayHolder();

      PropertyPath.set(target, "values", List.of(60, 90, 100));

      assertArrayEquals(new String[]{ "60", "90", "100" }, target.getValues());
   }

   @Test
   void setsAStringArrayFromDecimalStrings() {
      StringArrayHolder target = new StringArrayHolder();

      PropertyPath.set(target, "values", List.of("60.0", "90.0", "100.0"));

      assertArrayEquals(new String[]{ "60.0", "90.0", "100.0" }, target.getValues());
   }

   @Test
   void refusesANonArrayValueForAnArrayTarget() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(new StringArrayHolder(), "values", "60,90,100"));

      assertTrue(thrown.getMessage().contains("values"), "name the property");
      assertTrue(thrown.getMessage().contains("JSON array"), "name the expected shape");
   }

   @Test
   void anArrayElementThatFailsCoercionNamesItsIndex() {
      IntArrayHolder target = new IntArrayHolder();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(target, "values", List.of("60", "not-a-number", "100")));

      assertTrue(thrown.getMessage().contains("values[1]"), "name the offending index");
   }

   /**
    * Bug #76771 (VCG-008): {@code coerce} has no bean-construction branch at all -- only
    * primitives, {@code String} and enums -- so a JSON object array for a bean-array field
    * ({@code hierarchyPropertyPaneModel.dimensions}'s {@code VSDimensionModel[]}, mirrored here)
    * is refused exactly like an unsupported scalar element type would be. This documents the gap
    * deliberately left in place: the fix is {@code HierarchyDimensionService}
    * (add_hierarchy_dimension / remove_hierarchy_dimension), not a bean-construction branch here
    * -- see that class's doc for why. This test must keep failing the same way after that fix
    * lands, not start passing.
    */
   public static class DimensionArrayHolder {
      public inetsoft.web.composer.model.vs.VSDimensionModel[] getDimensions() { return dimensions; }
      public void setDimensions(inetsoft.web.composer.model.vs.VSDimensionModel[] dimensions) {
         this.dimensions = dimensions;
      }

      private inetsoft.web.composer.model.vs.VSDimensionModel[] dimensions;
   }

   @Test
   void aBeanArrayElementFromAJsonObjectIsRefusedNamingTheBeanTypeAndIndex() {
      DimensionArrayHolder target = new DimensionArrayHolder();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(target, "dimensions",
                                List.of(Map.of("members", List.of()))));

      assertTrue(thrown.getMessage().contains("dimensions[0]"), "name the offending index");
      assertTrue(thrown.getMessage().contains("VSDimensionModel"), "name the bean type");
   }

   // ── bug #76929: GradientColor is a named exception to the String-ctor gate ──
   //
   // GradientColor (Oval/Rectangle's Fill tab gradient) has no public String constructor, so it
   // used to fall all the way through to the generic "expects GradientColor, which '...' is not"
   // throw -- the same shape as VSDimensionModel above, but here the target is a plain,
   // non-relational value object with nothing to resolve against a pane's own catalog, so it is
   // now built directly by class identity rather than refused. This must not reopen
   // aBeanArrayElementFromAJsonObjectIsRefusedNamingTheBeanTypeAndIndex above or
   // aJsonObjectForABeanWithNoStringConstructorIsStillRefused below -- neither VSDimensionModel
   // nor NoStringConstructor is GradientColor or GradientColor.ColorStop, so both stay refused.

   public static class GradientColorHolder {
      public GradientColor getGradientColor() { return gradientColor; }
      public void setGradientColor(GradientColor gradientColor) {
         this.gradientColor = gradientColor;
      }

      private GradientColor gradientColor;
   }

   @Test
   void buildsAGradientColorFromAJsonObjectIncludingItsColorStopArray() {
      GradientColorHolder target = new GradientColorHolder();

      PropertyPath.set(target, "gradientColor", Map.of(
         "apply", true,
         "direction", "linear",
         "angle", "90",
         "colors", List.of(
            Map.of("color", "#FF0000", "offset", "0"),
            Map.of("color", "#FFFF00", "offset", "100"))));

      GradientColor result = target.getGradientColor();
      assertTrue(result.isApply());
      assertEquals("linear", result.getDirection());
      assertEquals(90, result.getAngle());
      assertEquals(2, result.getColors().length);
      assertEquals("#FF0000", result.getColors()[0].getColor());
      assertEquals(0, result.getColors()[0].getOffset());
      assertEquals("#FFFF00", result.getColors()[1].getColor());
      assertEquals(100, result.getColors()[1].getOffset());
   }

   @Test
   void aGradientColorRoundTripsThroughGetAndSet() {
      GradientColorHolder target = new GradientColorHolder();

      PropertyPath.set(target, "gradientColor",
                       Map.of("apply", true, "colors", List.of(Map.of("color", "#000000",
                                                                      "offset", "0"))));

      Object readBack = PropertyPath.get(target, "gradientColor");
      assertInstanceOf(GradientColor.class, readBack);
      assertTrue(((GradientColor) readBack).isApply());
   }

   @Test
   void anUnrecognizedKeyOnAGradientColorJsonObjectIsRefusedRatherThanSilentlyIgnored() {
      GradientColorHolder target = new GradientColorHolder();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(target, "gradientColor", Map.of("bogus", "x")));

      assertTrue(thrown.getMessage().contains("bogus"), "name the offending key");
      assertTrue(thrown.getMessage().contains("GradientColor"), "name the bean type");
   }

   @Test
   void aNonObjectValueForAGradientColorIsRefused() {
      GradientColorHolder target = new GradientColorHolder();

      assertThrows(IllegalArgumentException.class,
                   () -> PropertyPath.set(target, "gradientColor", "not-an-object"));
   }

   // ── bug #76888: DynamicValueModel bean targets ────────────────────────────
   //
   // CalendarAdvancedPaneModel.min/max (and DatePeriodModel/IntervalPaneModel/
   // StandardPeriodPaneModel's own DynamicValueModel-typed fields) are plain multi-field beans
   // with no primitive/String/enum/array shape of their own, so every raw dotted-path write to
   // one fell through to the unconditional final throw regardless of the JSON's content. These
   // run against the real DynamicValueModel, so its own VSUtil auto-detection is what is tested.

   public static class DynamicValueHolder {
      public DynamicValueModel getBound() { return bound; }
      public void setBound(DynamicValueModel bound) { this.bound = bound; }

      private DynamicValueModel bound;
   }

   @Test
   void buildsADynamicValueModelFromABareString() {
      DynamicValueHolder target = new DynamicValueHolder();

      PropertyPath.set(target, "bound", "2026-01-01");

      assertEquals("2026-01-01", target.getBound().getValue());
      assertEquals(DynamicValueModel.VALUE, target.getBound().getType(),
                   "the one-argument constructor's own auto-detection decides the type");
   }

   @Test
   void aBareStringThatLooksLikeAComponentReferenceAutoDetectsThroughTheStringConstructor() {
      DynamicValueHolder target = new DynamicValueHolder();

      PropertyPath.set(target, "bound", "$(Spinner1)");

      assertEquals("$(Spinner1)", target.getBound().getValue());
      assertEquals(DynamicValueModel.VARIABLE, target.getBound().getType(),
                   "the same auto-detection the plain DynamicValueModel(String) constructor does");
   }

   @Test
   void aBareStringThatLooksLikeAnExpressionAutoDetectsThroughTheStringConstructor() {
      DynamicValueHolder target = new DynamicValueHolder();

      PropertyPath.set(target, "bound", "=new Date()");

      assertEquals("=new Date()", target.getBound().getValue());
      assertEquals(DynamicValueModel.EXPRESSION, target.getBound().getType());
   }

   @Test
   void buildsADynamicValueModelFromAFullJsonObject() {
      DynamicValueHolder target = new DynamicValueHolder();

      PropertyPath.set(target, "bound",
                       Map.of("value", "2026-01-01", "type", "VALUE", "dataType", "date"));

      assertEquals("2026-01-01", target.getBound().getValue());
      assertEquals(DynamicValueModel.VALUE, target.getBound().getType());
      assertEquals("date", target.getBound().getDataType());
   }

   @Test
   void jsonObjectKeysAreReadCaseInsensitively() {
      DynamicValueHolder target = new DynamicValueHolder();

      PropertyPath.set(target, "bound", Map.of("VALUE", "2026-01-01", "TYPE", "VALUE"));

      assertEquals("2026-01-01", target.getBound().getValue());
      assertEquals(DynamicValueModel.VALUE, target.getBound().getType());
   }

   @Test
   void aJsonObjectWithNoExplicitTypeAutoDetectsFromItsOwnValueEntry() {
      DynamicValueHolder target = new DynamicValueHolder();

      PropertyPath.set(target, "bound", Map.of("value", "$(Foo)"));

      assertEquals("$(Foo)", target.getBound().getValue());
      assertEquals(DynamicValueModel.VARIABLE, target.getBound().getType(),
                   "no 'type' key must not silently default to VALUE for a variable reference");
   }

   @Test
   void anExplicitTypeInTheJsonObjectIsNeverSecondGuessedEvenIfItLooksLikeAReference() {
      DynamicValueHolder target = new DynamicValueHolder();

      PropertyPath.set(target, "bound", Map.of("value", "$(Foo)", "type", "EXPRESSION"));

      assertEquals(DynamicValueModel.EXPRESSION, target.getBound().getType(),
                   "an explicit type always wins over auto-detection");
   }

   @Test
   void anUnrecognizedJsonObjectKeyIsRefusedRatherThanSilentlyIgnored() {
      DynamicValueHolder target = new DynamicValueHolder();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(target, "bound", Map.of("value", "x", "bogus", "y")));

      assertTrue(thrown.getMessage().contains("bogus"), "name the offending key");
      assertTrue(thrown.getMessage().contains("not a writable property"),
                 "refused by the setters path, not the final type-mismatch throw");
      assertTrue(thrown.getMessage().contains("DynamicValueModel"), "name the bean type");
   }

   // ── bug #76997: only allow-listed types are built from a String or JSON object ──
   //
   // A public (String) constructor alone is not a safe signal: JDK value types have one too.
   // java.util.Date is reachable today (AssetEntry.createdDate/modifiedDate under
   // vsOptionsPane.selectDataSourceDialogModel.dataSource), and "Jan 2 2024", {"time":0} and
   // {"value":"Jan 2 2024"} were all silently accepted and written to the live base entry.

   public static class DateHolder {
      public java.util.Date getWhen() { return when; }
      public void setWhen(java.util.Date when) { this.when = when; }

      private java.util.Date when;
   }

   @Test
   void aDateLeafIsNotBuiltFromAString() {
      DateHolder target = new DateHolder();

      Exception thrown = assertThrows(
         IllegalArgumentException.class, () -> PropertyPath.set(target, "when", "Jan 2 2024"));

      assertTrue(thrown.getMessage().contains("'when' expects Date"), thrown.getMessage());
      assertNull(target.getWhen());
   }

   @Test
   void aDateLeafIsNotBuiltFromAJsonObjectThroughItsSetters() {
      DateHolder target = new DateHolder();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(target, "when", Map.of("time", 0)));

      assertTrue(thrown.getMessage().contains("'when' expects Date"), thrown.getMessage());
      assertNull(target.getWhen());
   }

   @Test
   void aDateLeafIsNotBuiltFromTheOneKeyValueShortcut() {
      DateHolder target = new DateHolder();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(target, "when", Map.of("value", "Jan 2 2024")));

      assertTrue(thrown.getMessage().contains("'when' expects Date"), thrown.getMessage());
      assertNull(target.getWhen());
   }

   /** Has DynamicValueModel's shape -- a public (String) constructor -- but is not allow-listed. */
   public static class StringConstructible {
      public StringConstructible() {
      }

      public StringConstructible(String value) {
         this.value = value;
      }

      public Object getValue() { return value; }
      public void setValue(Object value) { this.value = value; }

      private Object value;
   }

   public static class StringConstructibleHolder {
      public StringConstructible getBound() { return bound; }
      public void setBound(StringConstructible bound) { this.bound = bound; }

      private StringConstructible bound;
   }

   @Test
   void aStringConstructorAloneDoesNotMakeATypeBuildableFromAStringOrJsonObject() {
      StringConstructibleHolder target = new StringConstructibleHolder();

      for(Object value : List.of("x", Map.of("value", "x"), Map.of("value", "x", "extra", "y"))) {
         Exception thrown = assertThrows(
            IllegalArgumentException.class, () -> PropertyPath.set(target, "bound", value),
            "refuse " + value);

         assertTrue(thrown.getMessage().contains("'bound' expects StringConstructible"),
                    thrown.getMessage());
      }

      assertNull(target.getBound());
   }

   /**
    * Regression guard for the gate itself: a Map value against a bean with no one-argument
    * String constructor (e.g. VSDimensionModel, exercised above) must still be refused -- this
    * fix must not turn into a general JSON-object-to-any-bean capability.
    */
   public static class NoStringConstructor {
      public NoStringConstructor() {
      }

      public String getName() { return name; }
      public void setName(String name) { this.name = name; }

      private String name;
   }

   public static class NoStringConstructorHolder {
      public NoStringConstructor getBean() { return bean; }
      public void setBean(NoStringConstructor bean) { this.bean = bean; }

      private NoStringConstructor bean;
   }

   @Test
   void aJsonObjectForABeanWithNoStringConstructorIsStillRefused() {
      NoStringConstructorHolder target = new NoStringConstructorHolder();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyPath.set(target, "bean", Map.of("name", "x")));

      assertTrue(thrown.getMessage().contains("bean"), "name the property");
      assertTrue(thrown.getMessage().contains("NoStringConstructor"), "name the bean type");
   }

   /**
    * VOF-012. A TextInput's Input Editor type is the same "String-typed enum in disguise" as
    * {@code visible}, but its domain could not be declared the same way: the leaf is just
    * {@code type}, a name a dozen unrelated models also use, so it is keyed by full path instead.
    *
    * <p>What it cost before: {@code VSInputService} dispatches on the token with a case-sensitive
    * {@code optionType.equals(ColumnOption.FLOAT)} ladder, so {@code "float"} matched no branch,
    * {@code setColumnOption} was never called, and the {@code minimum}/{@code maximum} set in the
    * same patch — read only inside the matching branch — went with it. {@code ok:true}, nothing
    * applied.
    */
   public static class ColumnOptionPane {
      public String getType() { return type; }
      public void setType(String type) { this.type = type; }

      private String type = "Text";
   }

   public static class ColumnOptionOwner {
      public ColumnOptionPane getTextInputColumnOptionPaneModel() { return pane; }
      public void setTextInputColumnOptionPaneModel(ColumnOptionPane pane) { this.pane = pane; }

      private ColumnOptionPane pane = new ColumnOptionPane();
   }

   @Test
   void canonicalizesTheInputEditorTypeToTheSpellingStyleBICompares() {
      for(String token : new String[]{ "float", "FLOAT", "Float" }) {
         ColumnOptionOwner target = new ColumnOptionOwner();
         PropertyPath.set(target, "textInputColumnOptionPaneModel.type", token);
         assertEquals("Float", target.getTextInputColumnOptionPaneModel().getType(),
                      token + " must be stored as the token VSInputService compares against");
      }
   }

   @Test
   void acceptsEveryInputEditorTypeTheTextInputDialogOffers() {
      for(String token : new String[]{ "Text", "Date", "Integer", "Float", "Password" }) {
         ColumnOptionOwner target = new ColumnOptionOwner();
         PropertyPath.set(target, "textInputColumnOptionPaneModel.type", token);
         assertEquals(token, target.getTextInputColumnOptionPaneModel().getType());
      }
   }

   @Test
   void refusesAnInputEditorTypeOutsideTheDialogsOwnFiveChoices() {
      // "ComboBox" and "Boolean" are real ColumnOption constants but appear in neither of
      // VSInputService's TextInput ladders, so accepting them would be a second silent no-op.
      for(String token : new String[]{ "decimal", "ComboBox", "Boolean" }) {
         ColumnOptionOwner target = new ColumnOptionOwner();
         Exception thrown = assertThrows(
            IllegalArgumentException.class,
            () -> PropertyPath.set(target, "textInputColumnOptionPaneModel.type", token),
            token + " must be refused");

         assertTrue(thrown.getMessage().contains("Float"), "list the tokens that do work");
         assertEquals("Text", target.getTextInputColumnOptionPaneModel().getType(),
                      "a refused value must leave the default in place");
      }
   }

   /**
    * The path-keyed domain must not leak onto some other model's {@code type} leaf -- the whole
    * reason it is keyed by path rather than by leaf name.
    */
   @Test
   void theInputEditorTypeDomainDoesNotReachAnUnrelatedTypeProperty() {
      ColumnOptionPane target = new ColumnOptionPane();
      assertDoesNotThrow(() -> PropertyPath.set(target, "type", "anything at all"));
      assertEquals("anything at all", target.getType());
   }

   public static class ShapeLinePane {
      public String getStyle() { return style; }
      public void setStyle(String style) { this.style = style; }

      private String style = "THIN_LINE";
   }

   public static class ShapeOwner {
      public ShapeLinePane getLinePropPaneModel() { return pane; }
      public void setLinePropPaneModel(ShapeLinePane pane) { this.pane = pane; }

      private ShapeLinePane pane = new ShapeLinePane();
   }

   public static class ShapeRoot {
      public ShapeOwner getLinePropertyPaneModel() { return o; }
      public void setLinePropertyPaneModel(ShapeOwner o) { this.o = o; }
      public ShapeOwner getOvalPropertyPaneModel() { return o; }
      public void setOvalPropertyPaneModel(ShapeOwner o) { this.o = o; }
      public ShapeOwner getRectanglePropertyPaneModel() { return o; }
      public void setRectanglePropertyPaneModel(ShapeOwner o) { this.o = o; }

      private ShapeOwner o = new ShapeOwner();
   }

   @Test
   void shapeLineStyleCanonicalizesNoBorderAndRefusesUnknownTokens() {
      for(String root : new String[] { "linePropertyPaneModel", "ovalPropertyPaneModel",
                                       "rectanglePropertyPaneModel" })
      {
         String path = root + ".linePropPaneModel.style";
         ShapeRoot target = new ShapeRoot();

         PropertyPath.set(target, path, "NO_BORDER");
         assertEquals("NONE", target.getLinePropertyPaneModel().getLinePropPaneModel().getStyle());
         PropertyPath.set(target, path, "dash_line");
         assertEquals("DASH_LINE",
                      target.getLinePropertyPaneModel().getLinePropPaneModel().getStyle());

         for(String bad : new String[] { "banana", "999", "H_LEFT" }) {
            Exception thrown = assertThrows(IllegalArgumentException.class,
               () -> PropertyPath.set(target, path, bad), path + " " + bad);
            assertTrue(thrown.getMessage().contains("DOUBLE_LINE"), thrown.getMessage());
         }

         assertEquals("DASH_LINE",
                      target.getLinePropertyPaneModel().getLinePropPaneModel().getStyle());
      }
   }

   /** Every name the real readback emitter produces for a dropdown int must be accepted back. */
   @Test
   void shapeLineStyleAcceptsEveryNameTheReadbackEmits() {
      int[] styles = { StyleConstants.NONE, StyleConstants.NO_BORDER, StyleConstants.THIN_LINE,
                       StyleConstants.MEDIUM_LINE, StyleConstants.THICK_LINE,
                       StyleConstants.DOUBLE_LINE, StyleConstants.DOT_LINE,
                       StyleConstants.DASH_LINE, StyleConstants.MEDIUM_DASH,
                       StyleConstants.LARGE_DASH, StyleConstants.THIN_THIN_LINE,
                       StyleConstants.ULTRA_THIN_LINE };

      for(String root : new String[] { "linePropertyPaneModel", "ovalPropertyPaneModel",
                                       "rectanglePropertyPaneModel" })
      {
         String path = root + ".linePropPaneModel.style";

         for(int style : styles) {
            String name = Util.getLineStyleName(style);
            ShapeRoot target = new ShapeRoot();
            assertDoesNotThrow(() -> PropertyPath.set(target, path, name), path + " " + name);
            String expected = "NO_BORDER".equals(name) ? "NONE" : name;
            assertEquals(expected,
                         target.getLinePropertyPaneModel().getLinePropPaneModel().getStyle(),
                         path + " " + name);
         }
      }
   }
}
