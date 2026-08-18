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

import inetsoft.web.composer.model.vs.*;

import java.util.*;

/**
 * Short names for the deep dialog-model paths.
 *
 * <p>Setting one boolean on a gauge otherwise means writing
 * {@code gaugeGeneralPaneModel.outputGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.visible}.
 * That depth is the entire reason this layer exists.
 *
 * <p>Two invariants hold this honest:
 *
 * <ol>
 *   <li><b>Every declared alias resolves.</b> {@code PropertyAliasesTest} reflects over this
 *       registry and asserts each path exists on its model class. When the composer renames a
 *       pane field the build breaks rather than production.</li>
 *   <li><b>Aliases never widen the surface.</b> An alias is only ever a shorter name for a
 *       path that already exists — nothing here computes, combines, or defaults a value. That
 *       would put property semantics in two places, which is the drift this design prevents.</li>
 * </ol>
 *
 * <p>Resolution order is exact alias → raw dotted path → error. A key that is neither is never
 * dropped.
 */
public final class PropertyAliases {
   /** The viewsheet's own property vocabulary. Not "viewsheet" -- see the register call below. */
   public static final String SHEET = "sheet";

   /** One assembly type's dialog model and its alias vocabulary. */
   public record TypeAliases(String assemblyType, Class<?> modelClass,
                             Map<String, String> aliases) {}

   private static final Map<String, TypeAliases> REGISTRY = registry();

   private PropertyAliases() {
   }

   public static TypeAliases forType(String assemblyType) {
      TypeAliases entry = REGISTRY.get(normalize(assemblyType));

      if(entry == null) {
         throw new IllegalArgumentException(
            "No property vocabulary for assembly type '" + assemblyType + "' yet. Types " +
            "covered: " + String.join(", ", new TreeSet<>(REGISTRY.keySet())) + ". Raw dotted " +
            "paths are not available for uncovered types either, because the dialog model is " +
            "not known.");
      }

      return entry;
   }

   public static boolean covers(String assemblyType) {
      return REGISTRY.containsKey(normalize(assemblyType));
   }

   public static Set<String> coveredTypes() {
      return Collections.unmodifiableSet(REGISTRY.keySet());
   }

   /**
    * Resolves a key for a <b>write</b>, on top of {@link #resolve}.
    *
    * <p>The viewsheet's own vocabulary has properties that are readable — they come back from
    * {@code list_viewsheet_properties} / {@code get_viewsheet_properties} — but refuse to be
    * <i>written</i> through a properties patch, because the write would be a hazard the
    * ordinary "set a scalar" contract does not cover: authoring script through a second,
    * ungoverned path, or replacing a structure that is relational to other assemblies in the
    * sheet rather than a simple {@code info.setX}. {@code vsScriptPane} is refused outright —
    * it is not even in the vocabulary, since it is never offered as something to set at all.
    *
    * <p>The refusal checks the <b>resolved path</b>, not just the input key, so the raw-dotted-
    * path escape hatch cannot reach the same field under a different spelling — e.g.
    * {@code vsScriptPane.onInit} is refused exactly like {@code vsScriptPane} is.
    */
   public static String resolveForWrite(String assemblyType, String key) {
      String normalizedType = normalize(assemblyType);
      String refusal = viewsheetWriteRefusal(normalizedType, key);

      if(refusal != null) {
         throw new IllegalArgumentException(refusal);
      }

      String path = resolve(assemblyType, key);
      refusal = viewsheetWriteRefusal(normalizedType, path);

      if(refusal != null) {
         throw new IllegalArgumentException(refusal);
      }

      return path;
   }

   /**
    * Resolves an agent-supplied key to a model path: exact alias first, then the key as a raw
    * dotted path. A key that is neither throws with near-matches — it is never dropped.
    */
   public static String resolve(String assemblyType, String key) {
      TypeAliases entry = forType(assemblyType);
      String alias = entry.aliases().get(key);

      if(alias != null) {
         return alias;
      }

      if(key != null && key.contains(".")) {
         // A dotted key is the documented raw escape hatch. PropertyPath validates it and
         // fails loud if it does not resolve, so nothing silently no-ops here.
         return key;
      }

      throw new IllegalArgumentException(
         "'" + key + "' is not a property of " + entry.assemblyType() + "." +
         nearest(key, entry.aliases().keySet()) +
         " Known names: " + String.join(", ", new TreeSet<>(entry.aliases().keySet())) +
         ". A raw model path (containing a '.') is also accepted.");
   }

   /**
    * The viewsheet-level write refusals. Checked against both the input key (so
    * {@code vsScriptPane}, which is not in the vocabulary at all, still gets a specific,
    * explanatory refusal rather than a generic "unknown key") and the resolved path (so the
    * same field cannot be reached by a different spelling — a raw dotted path, or a registered
    * alias that happens to map onto it).
    */
   private static String viewsheetWriteRefusal(String normalizedType, String pathOrKey) {
      if(!SHEET.equals(normalizedType) || pathOrKey == null) {
         return null;
      }

      if(isOrUnder(pathOrKey, "vsScriptPane")) {
         return "'vsScriptPane' is not settable through set_viewsheet_properties. It carries " +
            "the viewsheet's onInit/onLoad script; writing it through a properties patch would " +
            "be a second, ungoverned path to authoring viewsheet script. Use update_script " +
            "instead. Reading it is fine — call get_viewsheet_properties with raw=true.";
      }

      if(isOrUnder(pathOrKey, "filtersPane")) {
         return "'filtersPane' is read-only through set_viewsheet_properties in this version. " +
            "Its filter-id assignments are relational to the sheet's own selection assemblies, " +
            "not a simple scalar write.";
      }

      if(isOrUnder(pathOrKey, "localizationPane")) {
         return "'localizationPane' is read-only through set_viewsheet_properties in this " +
            "version. Its entries are keyed to the sheet's own component tree, not a simple " +
            "scalar write.";
      }

      if(isOrUnder(pathOrKey, "screensPane")) {
         return "'screensPane' is not settable through set_viewsheet_properties. Device " +
            "layouts, print layout and screen sizing are their own capability, not a corner of " +
            "a properties patch.";
      }

      return null;
   }

   private static boolean isOrUnder(String path, String prefix) {
      return path.equals(prefix) || path.startsWith(prefix + ".");
   }

   private static String nearest(String key, Set<String> candidates) {
      String best = null;
      int bestDistance = Integer.MAX_VALUE;

      for(String candidate : candidates) {
         int distance = distance(key == null ? "" : key.toLowerCase(), candidate.toLowerCase());

         if(distance < bestDistance) {
            bestDistance = distance;
            best = candidate;
         }
      }

      return best != null && bestDistance <= 3 ? " Did you mean '" + best + "'?" : "";
   }

   private static int distance(String a, String b) {
      int[] previous = new int[b.length() + 1];
      int[] current = new int[b.length() + 1];

      for(int j = 0; j <= b.length(); j++) {
         previous[j] = j;
      }

      for(int i = 1; i <= a.length(); i++) {
         current[0] = i;

         for(int j = 1; j <= b.length(); j++) {
            int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
            current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                                  previous[j - 1] + cost);
         }

         int[] swap = previous;
         previous = current;
         current = swap;
      }

      return previous[b.length()];
   }

   private static String normalize(String assemblyType) {
      return assemblyType == null ? "" : assemblyType.trim().toLowerCase();
   }

   // ── the registry ──────────────────────────────────────────────────────────

   private static Map<String, TypeAliases> registry() {
      Map<String, TypeAliases> registry = new LinkedHashMap<>();
      register(registry, "gauge", GaugePropertyDialogModel.class, gauge());
      register(registry, "text", TextPropertyDialogModel.class, text());
      // Immutables models. These were "not covered" until PropertyPath learned to read a bare
      // Immutables accessor and to rebuild immutable levels through withX — the paths below run
      // through two immutable levels into a mutable OutputGeneralPaneModel.
      register(registry, "image", ImagePropertyDialogModel.class, image());

      register(registry, "chart", ChartPropertyDialogModel.class, chart());
      register(registry, "table", TableViewPropertyDialogModel.class, table());
      register(registry, "crosstab", CrosstabPropertyDialogModel.class, crosstab());
      register(registry, "selectionlist", SelectionListPropertyDialogModel.class,
               selectionList());
      register(registry, "selectiontree", SelectionTreePropertyDialogModel.class,
               selectionTree());

      // Phase 3 batch a — the input assemblies, all on VSInputService, plus range slider,
      // calendar and tab.
      register(registry, "checkbox", CheckboxPropertyDialogModel.class,
               listInput("checkboxGeneralPaneModel", true));
      register(registry, "combobox", ComboboxPropertyDialogModel.class,
               listInput("comboboxGeneralPaneModel", false));
      register(registry, "radiobutton", RadioButtonPropertyDialogModel.class,
               listInput("radioButtonGeneralPaneModel", true));
      register(registry, "slider", SliderPropertyDialogModel.class, slider());
      register(registry, "spinner", SpinnerPropertyDialogModel.class, spinner());
      register(registry, "textinput", TextInputPropertyDialogModel.class, textInput());
      register(registry, "timeslider", RangeSliderPropertyDialogModel.class, rangeSlider());
      register(registry, "calendar", CalendarPropertyDialogModel.class, calendar());
      register(registry, "tab", TabPropertyDialogModel.class, tab());

      // Phase 3 batch b — containers, shapes, submit, calc table.
      register(registry, "calctable", CalcTablePropertyDialogModel.class, calcTable());
      register(registry, "groupcontainer", GroupContainerPropertyDialogModel.class,
               groupContainer());
      register(registry, "line", LinePropertyDialogModel.class, shape());
      register(registry, "oval", OvalPropertyDialogModel.class, shape());
      register(registry, "rectangle", RectanglePropertyDialogModel.class, shape());
      register(registry, "selectioncontainer", SelectionContainerPropertyDialogModel.class,
               selectionContainer());
      register(registry, "submit", SubmitPropertyDialogModel.class, submit());

      // The viewsheet's own properties -- the assembly-less target.
      //
      // Keyed "sheet", not "viewsheet", because "viewsheet" is already an ASSEMBLY type name:
      // Viewsheet implements VSAssembly, so AssemblyPropertyService.typeOf derives "viewsheet"
      // from the class of an embedded-viewsheet assembly. Registering the sheet's own vocabulary
      // under that name made covers("viewsheet") true and replaced the clear "'X' is a Viewsheet,
      // whose properties are not covered yet" with "No property service wired for assembly type
      // 'viewsheet'" -- which reads as an internal wiring bug rather than an unsupported assembly.
      register(registry, SHEET, ViewsheetPropertyDialogModel.class, viewsheet());
      return Collections.unmodifiableMap(registry);
   }

   private static void register(Map<String, TypeAliases> registry, String type,
                                Class<?> modelClass, Map<String, String> aliases)
   {
      registry.put(type, new TypeAliases(type, modelClass, Collections.unmodifiableMap(aliases)));
   }

   /**
    * The general pane an <i>output</i> assembly shares — gauge, text and friends nest it one
    * level deeper, under {@code outputGeneralPaneModel}.
    */
   private static void outputGeneral(Map<String, String> aliases, String prefix) {
      basicGeneral(aliases, prefix + ".outputGeneralPaneModel.generalPropPaneModel");
   }

   /**
    * The general pane a <i>data</i> assembly shares — chart, table, crosstab and the selection
    * assemblies hold {@code generalPropPaneModel} directly. The two shapes differ by exactly
    * one level, which is the kind of near-miss the invariant test exists to catch.
    */
   private static void dataGeneral(Map<String, String> aliases, String prefix) {
      basicGeneral(aliases, prefix + ".generalPropPaneModel");
   }

   /**
    * Shapes — line, oval, rectangle — hold {@code basicGeneralPaneModel} directly, with no
    * {@code generalPropPaneModel} between. A third shape, one level shallower than data
    * assemblies and two shallower than output ones.
    */
   private static void shapeGeneral(Map<String, String> aliases, String prefix) {
      String basic = prefix + ".basicGeneralPaneModel";
      aliases.put("name", basic + ".name");
      aliases.put("visible", basic + ".visible");
      aliases.put("primary", basic + ".primary");
   }

   private static void basicGeneral(Map<String, String> aliases, String generalPrefix) {
      String basic = generalPrefix + ".basicGeneralPaneModel";
      aliases.put("name", basic + ".name");
      aliases.put("visible", basic + ".visible");
      aliases.put("enabled", basic + ".enabled");
      aliases.put("shadow", basic + ".shadow");
      aliases.put("primary", basic + ".primary");
      aliases.put("refresh", basic + ".refresh");
   }

   /** Title bar, on the assemblies that have one. */
   private static void title(Map<String, String> aliases, String prefix) {
      aliases.put("titleVisible", prefix + ".titlePropPaneModel.visible");
      aliases.put("title", prefix + ".titlePropPaneModel.title");
   }

   private static void sizePosition(Map<String, String> aliases, String prefix) {
      String size = prefix + ".sizePositionPaneModel";
      aliases.put("top", size + ".top");
      aliases.put("left", size + ".left");
      aliases.put("width", size + ".width");
      aliases.put("height", size + ".height");
      aliases.put("locked", size + ".locked");
   }

   private static Map<String, String> gauge() {
      Map<String, String> aliases = new LinkedHashMap<>();
      outputGeneral(aliases, "gaugeGeneralPaneModel");
      sizePosition(aliases, "gaugeGeneralPaneModel");
      aliases.put("min", "gaugeGeneralPaneModel.numberRangePaneModel.min");
      aliases.put("max", "gaugeGeneralPaneModel.numberRangePaneModel.max");
      aliases.put("majorIncrement", "gaugeGeneralPaneModel.numberRangePaneModel.majorIncrement");
      aliases.put("minorIncrement", "gaugeGeneralPaneModel.numberRangePaneModel.minorIncrement");
      aliases.put("showValue", "gaugeAdvancedPaneModel.showValue");
      return aliases;
   }

   private static Map<String, String> text() {
      Map<String, String> aliases = new LinkedHashMap<>();
      outputGeneral(aliases, "textGeneralPaneModel");
      sizePosition(aliases, "textGeneralPaneModel");
      aliases.put("alpha", "textGeneralPaneModel.alpha");
      aliases.put("popComponent", "textGeneralPaneModel.popComponent");
      return aliases;
   }

   /**
    * The image assembly. Its dialog model and general pane are both Immutables, so every path
    * here crosses at least one immutable level — {@code PropertyPath} rebuilds them through
    * {@code withX} on the way back up.
    */
   private static Map<String, String> image() {
      Map<String, String> aliases = new LinkedHashMap<>();
      outputGeneral(aliases, "imageGeneralPaneModel");
      sizePosition(aliases, "imageGeneralPaneModel");
      return aliases;
   }

   private static Map<String, String> chart() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "chartGeneralPaneModel");
      sizePosition(aliases, "chartGeneralPaneModel");
      title(aliases, "chartGeneralPaneModel");
      aliases.put("enableAdhocEditing", "chartAdvancedPaneModel.enableAdhocEditing");
      aliases.put("glossyEffect", "chartAdvancedPaneModel.glossyEffect");
      aliases.put("sparkline", "chartAdvancedPaneModel.sparkline");

      // The line pane — trend lines, grid lines, facet grid. What a user means by "add a trend
      // line" lives here, one pane below the general/advanced panes.
      //
      // Deliberately absent: pointLine and the word-cloud font scale are PlotDescriptor fields
      // that ChartPropertyDialogModel never surfaces, so there is no path to alias. They are not
      // reachable through this engine, and claiming otherwise would be worse than the gap.
      aliases.put("gridLineVisible", "chartLinePaneModel.gridLineVisible");
      aliases.put("innerLineVisible", "chartLinePaneModel.innerLineVisible");
      aliases.put("trendLineType", "chartLinePaneModel.trendLineType");
      aliases.put("trendLineStyle", "chartLinePaneModel.trendLineStyle");
      aliases.put("trendLineColor", "chartLinePaneModel.trendLineColor");
      aliases.put("trendLineVisible", "chartLinePaneModel.trendLineVisible");
      aliases.put("trendPerColor", "chartLinePaneModel.trendPerColor");
      aliases.put("projectForward", "chartLinePaneModel.projectForward");
      aliases.put("facetGrid", "chartLinePaneModel.facetGrid");
      aliases.put("facetGridColor", "chartLinePaneModel.facetGridColor");
      aliases.put("facetGridVisible", "chartLinePaneModel.facetGridVisible");
      aliases.put("diagonalLineStyle", "chartLinePaneModel.diagonalLineStyle");
      aliases.put("diagonalLineColor", "chartLinePaneModel.diagonalLineColor");
      aliases.put("quadrantGridLineStyle", "chartLinePaneModel.quadrantGridLineStyle");
      aliases.put("quadrantGridLineColor", "chartLinePaneModel.quadrantGridLineColor");
      return aliases;
   }

   private static Map<String, String> table() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "tableViewGeneralPaneModel");
      sizePosition(aliases, "tableViewGeneralPaneModel");
      title(aliases, "tableViewGeneralPaneModel");
      aliases.put("maxRows", "tableViewGeneralPaneModel.maxRows");
      aliases.put("submitOnChange", "tableViewGeneralPaneModel.submitOnChange");
      aliases.put("shrink", "tableAdvancedPaneModel.shrink");
      aliases.put("form", "tableAdvancedPaneModel.form");
      aliases.put("enableAdhoc", "tableAdvancedPaneModel.enableAdhoc");
      return aliases;
   }

   private static Map<String, String> crosstab() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "tableViewGeneralPaneModel");
      sizePosition(aliases, "tableViewGeneralPaneModel");
      title(aliases, "tableViewGeneralPaneModel");
      aliases.put("maxRows", "tableViewGeneralPaneModel.maxRows");
      aliases.put("fillBlankWithZero", "crosstabAdvancedPaneModel.fillBlankWithZero");
      aliases.put("summarySideBySide", "crosstabAdvancedPaneModel.summarySideBySide");
      aliases.put("mergeSpan", "crosstabAdvancedPaneModel.mergeSpan");
      aliases.put("shrink", "crosstabAdvancedPaneModel.shrink");
      aliases.put("drillEnabled", "crosstabAdvancedPaneModel.drillEnabled");
      return aliases;
   }

   private static Map<String, String> selectionList() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "selectionGeneralPaneModel");
      sizePosition(aliases, "selectionGeneralPaneModel");
      title(aliases, "selectionGeneralPaneModel");
      selectionGeneral(aliases);
      return aliases;
   }

   private static Map<String, String> selectionTree() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "selectionGeneralPaneModel");
      sizePosition(aliases, "selectionGeneralPaneModel");
      title(aliases, "selectionGeneralPaneModel");
      selectionGeneral(aliases);
      aliases.put("selectChildren", "selectionTreePaneModel.selectChildren");
      aliases.put("mode", "selectionTreePaneModel.mode");
      return aliases;
   }

   private static void selectionGeneral(Map<String, String> aliases) {
      aliases.put("showType", "selectionGeneralPaneModel.showType");
      aliases.put("listHeight", "selectionGeneralPaneModel.listHeight");
      aliases.put("sortType", "selectionGeneralPaneModel.sortType");
      aliases.put("singleSelection", "selectionGeneralPaneModel.singleSelection");
      aliases.put("submitOnChange", "selectionGeneralPaneModel.submitOnChange");
      aliases.put("suppressBlank", "selectionGeneralPaneModel.suppressBlank");
   }

   // ── Phase 3 batch a ───────────────────────────────────────────────────────

   /** Check box, combo box and radio button share a list-values general pane. */
   private static Map<String, String> listInput(String prefix, boolean hasTitle) {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, prefix);
      sizePosition(aliases, prefix);

      if(hasTitle) {
         title(aliases, prefix);
      }

      return aliases;
   }

   private static Map<String, String> slider() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "sliderGeneralPaneModel");
      sizePosition(aliases, "sliderGeneralPaneModel");
      numericRange(aliases, "sliderGeneralPaneModel");
      aliases.put("snap", "sliderAdvancedPaneModel.snap");
      return aliases;
   }

   private static Map<String, String> spinner() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "spinnerGeneralPaneModel");
      sizePosition(aliases, "spinnerGeneralPaneModel");
      numericRange(aliases, "spinnerGeneralPaneModel");
      return aliases;
   }

   private static Map<String, String> textInput() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "textInputGeneralPaneModel");
      sizePosition(aliases, "textInputGeneralPaneModel");
      return aliases;
   }

   private static Map<String, String> rangeSlider() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "rangeSliderGeneralPaneModel");
      sizePosition(aliases, "rangeSliderGeneralPaneModel");
      title(aliases, "rangeSliderGeneralPaneModel");
      return aliases;
   }

   private static Map<String, String> calendar() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "calendarGeneralPaneModel");
      sizePosition(aliases, "calendarGeneralPaneModel");
      title(aliases, "calendarGeneralPaneModel");
      return aliases;
   }

   private static Map<String, String> tab() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "tabGeneralPaneModel");
      sizePosition(aliases, "tabGeneralPaneModel");
      return aliases;
   }

   private static void numericRange(Map<String, String> aliases, String prefix) {
      // The pane is numericRangePaneModel here and numberRangePaneModel on a gauge, with
      // maximum/minimum rather than max/min. The short names hide that.
      aliases.put("min", prefix + ".numericRangePaneModel.minimum");
      aliases.put("max", prefix + ".numericRangePaneModel.maximum");
      aliases.put("increment", prefix + ".numericRangePaneModel.increment");
   }

   // ── Phase 3 batch b ───────────────────────────────────────────────────────

   private static Map<String, String> calcTable() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "tableViewGeneralPaneModel");
      sizePosition(aliases, "tableViewGeneralPaneModel");
      title(aliases, "tableViewGeneralPaneModel");
      aliases.put("shrink", "calcTableAdvancedPaneModel.shrink");
      aliases.put("fillBlankWithZero", "calcTableAdvancedPaneModel.fillBlankWithZero");
      aliases.put("sortOthersLast", "calcTableAdvancedPaneModel.sortOthersLast");
      aliases.put("headerRowCount", "calcTableAdvancedPaneModel.headerRowCount");
      aliases.put("headerColCount", "calcTableAdvancedPaneModel.headerColCount");
      return aliases;
   }

   private static Map<String, String> groupContainer() {
      Map<String, String> aliases = new LinkedHashMap<>();
      // Note the pane accessor is groupContainerGeneralPane — no "Model" suffix, unlike every
      // other type. Another reason these paths are declared rather than derived.
      basicGeneral(aliases, "groupContainerGeneralPane.generalPropPane");
      sizePosition(aliases, "groupContainerGeneralPane");
      return aliases;
   }

   /** Line, oval and rectangle share {@code ShapeGeneralPaneModel}. */
   private static Map<String, String> shape() {
      Map<String, String> aliases = new LinkedHashMap<>();
      shapeGeneral(aliases, "shapeGeneralPaneModel");
      sizePosition(aliases, "shapeGeneralPaneModel");
      return aliases;
   }

   private static Map<String, String> selectionContainer() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "selectionContainerGeneralPaneModel");
      sizePosition(aliases, "selectionContainerGeneralPaneModel");
      title(aliases, "selectionContainerGeneralPaneModel");
      return aliases;
   }

   private static Map<String, String> submit() {
      Map<String, String> aliases = new LinkedHashMap<>();
      dataGeneral(aliases, "submitGeneralPaneModel");
      sizePosition(aliases, "submitGeneralPaneModel");
      aliases.put("label", "submitGeneralPaneModel.labelPropPaneModel.label");
      return aliases;
   }

   // ── the viewsheet's own properties ────────────────────────────────────────

   /**
    * {@code VSOptionsPaneModel} has no name field of its own — {@code alias} is the viewsheet
    * asset's display name, writable here exactly as {@code set_worksheet_properties} already
    * exposes it for the sibling worksheet type. {@code screensPane} is excluded entirely: it
    * reaches device layouts, print layout and screen sizing through {@code refLayoutName}, which
    * is its own capability, not a corner of this one. {@code vsScriptPane} is excluded from the
    * vocabulary the same way — see {@link #resolveForWrite} for why both write-refuse, and why
    * {@code filtersPane}/{@code localizationPane} are listed here (readable) but still refuse a
    * write.
    */
   private static Map<String, String> viewsheet() {
      Map<String, String> aliases = new LinkedHashMap<>();
      aliases.put("alias", "vsOptionsPane.alias");
      aliases.put("desc", "vsOptionsPane.desc");
      aliases.put("maxRows", "vsOptionsPane.maxRows");
      aliases.put("snapGrid", "vsOptionsPane.snapGrid");
      aliases.put("useMetaData", "vsOptionsPane.useMetaData");
      aliases.put("promptForParams", "vsOptionsPane.promptForParams");
      aliases.put("selectionAssociation", "vsOptionsPane.selectionAssociation");
      aliases.put("createMv", "vsOptionsPane.createMv");
      aliases.put("serverSideUpdate", "vsOptionsPane.serverSideUpdate");
      aliases.put("touchInterval", "vsOptionsPane.touchInterval");
      aliases.put("maxRowsWarning", "vsOptionsPane.maxRowsWarning");
      aliases.put("hideNotifications", "vsOptionsPane.hideNotifications");
      aliases.put("listOnPortalTree", "vsOptionsPane.listOnPortalTree");
      // filtersPane and localizationPane are deliberately NOT aliased.
      //
      // They are read-only, and they are whole object graphs rather than properties: aliasing them
      // made every list/get response carry the entire localization component tree -- ~350 lines on
      // a small sheet, and it grows with assembly count. That is a curated vocabulary paying a
      // large cost for something it cannot even write. Reading them is still possible, and is what
      // `raw: true` is for; resolveForWrite still refuses them by name, since it matches on the
      // path rather than on membership in this map.
      // width/height/preview are deliberately absent. They are not sheet state: getViewsheetInfo
      // never populates them, so they read back as the Immutables defaults 0/0/false, and
      // setViewsheetInfo reads them only to size a one-off refresh. Writing one reported success
      // and changed nothing -- the silent no-op this layer exists to prevent -- and preview:true
      // additionally reached VSEventUtil.clearScale, discarding assembly scaling as a side effect
      // of "setting a property".
      //
      // onDemandMvEnabled is absent for the same reason: it is a capability flag computed in the
      // getter from SreeEnv, never read by the setter. createMv is the real property.
      return aliases;
   }
}
