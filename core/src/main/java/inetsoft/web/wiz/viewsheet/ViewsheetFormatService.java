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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.RadarChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;
import inetsoft.web.adhoc.model.AlignmentInfo;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.adhoc.model.chart.ChartFormatConstants;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.command.SetCurrentFormatCommand;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.GetVSObjectFormatEvent;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.wiz.binding.CalcTableService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Insets;
import java.security.Principal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies assembly-level formatting through the Composer's own format service.
 *
 * <p>{@code VSObjectFormatInfoModel} is CSS-shaped — {@code color}, {@code backgroundColor},
 * {@code font}, {@code align}, {@code format}/{@code formatSpec}, and the four border sides —
 * so it passes straight through without an alias layer.
 */
@Service
public class ViewsheetFormatService {
   @Autowired
   public ViewsheetFormatService(ViewsheetSessionService sessions, FormatPainterService painter,
                                 CalcTableService calcService,
                                 VSWizardBindingHandler bindingHandler)
   {
      this.sessions = sessions;
      this.painter = painter;
      this.calcService = calcService;
      this.bindingHandler = bindingHandler;
   }

   /**
    * @param assemblies the assemblies to format; at least one
    * @param format     the format to apply; may be null only when {@code reset} is true
    * @param reset      clear formatting back to the default rather than applying {@code format}
    * @param target     {@code "object"} (default, when null/blank) formats the whole assembly;
    *                   {@code "title"} formats only that assembly's own title-bar text, distinct
    *                   from a chart's axis titles or any other sub-region; {@code "text"} formats
    *                   a single chart's {@code text}-aesthetic-bound field (its data labels);
    *                   {@code "data"} formats a Crosstab/Table's body cells directly, the only
    *                   target that reaches rendered body text (e.g. {@code color}/font color) once
    *                   a table style applies; {@code "header"} formats a Crosstab/Table's HEADER
    *                   (row/column dimension label) cells directly, for the same reason -- see
    *                   {@link #requireTarget}
    * @param field      required when {@code target} is {@code "text"} — the column currently
    *                   bound to the chart's text aesthetic channel. Optional when {@code target}
    *                   is {@code "data"} or {@code "header"}: scopes the write to one named
    *                   column of a plain Table's body/header cells (matched against that
    *                   column's currently rendered header text) instead of the whole
    *                   body/header; null/blank keeps today's whole-table behavior. Against a
    *                   Crosstab it names one aggregate (its rendered measure header, its data
    *                   path header, or its base column when unambiguous) and scopes the write to
    *                   that measure's body cells (including totals) or header cells. Required
    *                   when {@code target} is {@code "field"}: the chart field (its full name,
    *                   as the binding reports it) whose value format to set. Unused for any
    *                   other target.
    * @param formatKeys the names of the keys the caller's JSON {@code format} object carried
    *                   with a non-null value, captured before parsing so a key the target cannot
    *                   apply is refused by name rather than mistaken for a model default; null
    *                   for a request built in Java, which skips that check
    */
   public record FormatRequest(List<String> assemblies,
                               VSObjectFormatInfoModel format,
                               boolean reset,
                               String target,
                               String field,
                               Set<String> formatKeys)
   {
      /** Kept for existing callers that predate {@code target}/{@code field} — defaults both. */
      public FormatRequest(List<String> assemblies, VSObjectFormatInfoModel format, boolean reset) {
         this(assemblies, format, reset, null, null, null);
      }

      /** Kept for existing callers that predate {@code field} — defaults it to null. */
      public FormatRequest(List<String> assemblies, VSObjectFormatInfoModel format, boolean reset,
                           String target)
      {
         this(assemblies, format, reset, target, null, null);
      }

      /** Kept for existing callers that predate {@code formatKeys} — no raw keys. */
      public FormatRequest(List<String> assemblies, VSObjectFormatInfoModel format, boolean reset,
                           String target, String field)
      {
         this(assemblies, format, reset, target, field, null);
      }

      /**
       * Reads {@code format} as a plain object, supplying the polymorphic type id ourselves.
       *
       * <p>{@code FormatInfoModel} is annotated {@code @JsonTypeInfo(use = Id.CLASS,
       * property = "type")}, so Jackson rejected any format that did not carry
       * {@code "type": "inetsoft.web.composer.model.vs.VSObjectFormatInfoModel"}. Every documented
       * usage of set_format failed with a 400 — an empty {@code {}} included — and the only way to
       * succeed was for the caller to name an internal Java class, precisely the leak this API
       * exists to prevent.
       *
       * <p>Taking the value as a {@code JsonNode} is what actually bypasses the resolver. Neither
       * {@code @JsonTypeInfo(use = Id.NONE)} nor {@code @JsonDeserialize(using = …)} on the record
       * component works: for a polymorphic property Jackson runs the {@code TypeDeserializer}
       * before either one gets a say.
       */
      @JsonCreator
      public static FormatRequest fromJson(@JsonProperty("assemblies") List<String> assemblies,
                                           @JsonProperty("format") JsonNode format,
                                           @JsonProperty("reset") boolean reset,
                                           @JsonProperty("target") String target,
                                           @JsonProperty("field") String field)
      {
         return new FormatRequest(assemblies, parseFormat(format, "set_format"), reset, target,
                                  field, formatKeys(format));
      }

      /** The keys of a JSON {@code format} object whose value is not JSON null. */
      static Set<String> formatKeys(JsonNode format) {
         Set<String> keys = new LinkedHashSet<>();

         if(format != null && format.isObject()) {
            format.fields().forEachRemaining(entry -> {
               if(entry.getValue() != null && !entry.getValue().isNull()) {
                  keys.add(entry.getKey());
               }
            });
         }

         return keys;
      }
   }

   /**
    * @param assembly the calc table's name
    * @param row      0-based design-grid row
    * @param col      0-based design-grid column
    * @param format   the format to apply; may be null only when {@code reset} is true
    * @param reset    clear this cell's own format back to inherited, rather than applying
    *                 {@code format}
    * @param formatKeys see {@link FormatRequest}; null for a request built in Java
    */
   public record CellFormatRequest(String assembly, Integer row, Integer col,
                                   VSObjectFormatInfoModel format, boolean reset,
                                   Set<String> formatKeys)
   {
      /** Kept for existing callers that predate {@code formatKeys} — no raw keys. */
      public CellFormatRequest(String assembly, Integer row, Integer col,
                               VSObjectFormatInfoModel format, boolean reset)
      {
         this(assembly, row, col, format, reset, null);
      }

      /** @see FormatRequest#fromJson -- same reason: bypasses Jackson's polymorphic resolver. */
      @JsonCreator
      public static CellFormatRequest fromJson(@JsonProperty("assembly") String assembly,
                                               @JsonProperty("row") Integer row,
                                               @JsonProperty("col") Integer col,
                                               @JsonProperty("format") JsonNode format,
                                               @JsonProperty("reset") boolean reset)
      {
         return new CellFormatRequest(assembly, row, col,
                                      parseFormat(format, "set_calc_cell_format"), reset,
                                      FormatRequest.formatKeys(format));
      }
   }

   /**
    * @param toolName the calling MCP tool's name (e.g. {@code "set_format"},
    *                 {@code "set_calc_cell_format"}), named in any thrown message so it points
    *                 at the tool the caller actually used, not whichever one first defined this
    *                 shared parsing.
    * @see FormatRequest#fromJson for why this reads {@code format} as a raw {@code JsonNode}.
    */
   static VSObjectFormatInfoModel parseFormat(JsonNode format, String toolName) {
      if(format == null || format.isNull()) {
         return null;
      }

      ObjectNode object = ((ObjectNode) format).deepCopy();
      object.put("type", VSObjectFormatInfoModel.class.getName());
      coerceAlign(object, toolName);
      coerceBorderStyles(object, toolName);

      VSObjectFormatInfoModel model;

      try {
         model = MAPPER.treeToValue(object, VSObjectFormatInfoModel.class);
      }
      catch(JsonProcessingException e) {
         throw new IllegalArgumentException(
            toolName + " could not read 'format': " + e.getOriginalMessage(), e);
      }

      deriveDateSpec(model);
      validateFormatSpec(model, toolName);
      return model;
   }

   /**
    * Bug #77597: fills in the {@code dateSpec} half of the Composer's two-field date contract.
    *
    * <p>{@code FormatPainterService} reads a {@code DateFormat}'s pattern from {@code formatSpec}
    * only when {@code dateSpec} is {@code "Custom"}; otherwise it uses {@code dateSpec} itself as
    * the pattern. The Composer's own Format pane always sends {@code dateSpec}. This API documents
    * only {@code format}/{@code formatSpec}, so a caller's {@code "MMM dd, yyyy"} arrived with a
    * null {@code dateSpec}, the painter stored a null pattern, and the cells rendered the
    * {@code yyyy-MM-dd} default while the call reported success.
    *
    * <p>Derived the way {@code FormatInfoModel.fixDateSpec} does, but matching the named styles
    * case-insensitively: {@code FULL}/{@code LONG}/{@code MEDIUM}/{@code SHORT} become
    * {@code dateSpec} with no {@code formatSpec}; any other non-empty pattern becomes
    * {@code "Custom"}. An explicit {@code dateSpec} is kept, only canonicalized; an empty
    * {@code formatSpec} is left as it is (the default pattern, as before).
    */
   private static void deriveDateSpec(VSObjectFormatInfoModel model) {
      if(model == null || !XConstants.DATE_FORMAT.equals(model.getFormat())) {
         return;
      }

      String dateSpec = model.getDateSpec();

      if(dateSpec != null && !dateSpec.isBlank()) {
         String named = namedDateStyle(dateSpec);

         if(named != null) {
            model.setDateSpec(named);
         }
         else if(CUSTOM_DATE_SPEC.equalsIgnoreCase(dateSpec.trim())) {
            model.setDateSpec(CUSTOM_DATE_SPEC);
         }

         return;
      }

      String spec = model.getFormatSpec();

      if(spec == null || spec.isEmpty()) {
         return;
      }

      String named = namedDateStyle(spec);

      if(named != null) {
         model.setDateSpec(named);
         model.setFormatSpec(null);
      }
      else {
         model.setDateSpec(CUSTOM_DATE_SPEC);
      }
   }

   /** The upper-case named date style {@code spec} names, ignoring case; null if none. */
   private static String namedDateStyle(String spec) {
      String upper = spec.trim().toUpperCase(Locale.ROOT);
      return NAMED_DATE_STYLES.contains(upper) ? upper : null;
   }

   /**
    * Bug #77597: refuses a pattern the renderer cannot build.
    *
    * <p>{@code TableFormat.getFormat} catches a bad pattern at render time and shows the value
    * unformatted, so a pattern stored verbatim but unusable is another silent no-op. The same
    * date factory the renderer uses, {@code CoreTool.createDateFormat}, is tried for a custom
    * date, time or timestamp pattern. A decimal pattern is tried with the JDK
    * {@code DecimalFormat}, which accepts StyleBI's extended suffix forms ({@code #,##0K},
    * {@code #.#B}, ...); {@code ExtendedDecimalFormat} itself is not used because its static
    * initializer needs the server's configuration, which a request-body parse must not.
    * The message stays on one line: a failure here surfaces through the request-body error
    * handler, which reports only the cause's first line.
    */
   private static void validateFormatSpec(VSObjectFormatInfoModel model, String toolName) {
      if(model == null) {
         return;
      }

      String type = model.getFormat();
      String spec = model.getFormatSpec();
      String dateSpec = model.getDateSpec();

      // A DateFormat whose explicit dateSpec is neither a named style nor "Custom" is a pattern
      // in its own right: the painter stores dateSpec as the pattern. Validate it the same way.
      if(XConstants.DATE_FORMAT.equals(type) && dateSpec != null && !dateSpec.isBlank() &&
         !CUSTOM_DATE_SPEC.equals(dateSpec) && namedDateStyle(dateSpec) == null)
      {
         spec = dateSpec;
      }

      if(type == null || spec == null || spec.isEmpty()) {
         return;
      }

      boolean dateLike = XConstants.DATE_FORMAT.equals(type) &&
         (dateSpec == null || !NAMED_DATE_STYLES.contains(dateSpec)) ||
         XConstants.TIME_FORMAT.equals(type) || XConstants.TIMEINSTANT_FORMAT.equals(type);

      try {
         if(dateLike) {
            CoreTool.createDateFormat(spec, Locale.getDefault());
         }
         else if(XConstants.DECIMAL_FORMAT.equals(type)) {
            new DecimalFormat(spec, new DecimalFormatSymbols(Locale.getDefault()));
         }
      }
      catch(IllegalArgumentException e) {
         String cause = e.getMessage() == null ? e.getClass().getSimpleName() :
            e.getMessage().lines().findFirst().orElse("").trim();
         // Not chained: the body-error handler reports the most specific cause, which must be
         // this message (naming the pattern), not the JDK's bare "Illegal pattern character".
         throw new IllegalArgumentException(
            toolName + " could not use formatSpec '" + spec + "' for " + type + ": " + cause);
      }
   }

   private static final String CUSTOM_DATE_SPEC = "Custom";
   private static final Set<String> NAMED_DATE_STYLES = Set.of("FULL", "LONG", "MEDIUM", "SHORT");

   /**
    * Lets {@code align} be written as a word.
    *
    * <p>This API documents its format as CSS-shaped and lists {@code align} beside
    * {@code color} and {@code backgroundColor}, so a caller writes {@code align: "center"}.
    * Underneath it is an {@code AlignmentInfo} with {@code halign}/{@code valign}, and Jackson
    * threw on the string — during body conversion, where Spring wraps the failure in
    * {@code HttpMessageNotReadableException} and answers with a **bodyless 400**. So the
    * documented usage failed with no message at all.
    *
    * <p>Accepting the word is the right half of the fix: "center" has one sensible meaning, and
    * the alternative is asking callers to learn an internal model this API exists to hide. Both
    * axes are accepted, together or separately, and the object form still works.
    */
   private static void coerceAlign(ObjectNode object, String toolName) {
      JsonNode align = object.get("align");

      if(align == null || !align.isTextual()) {
         return;
      }

      ObjectNode alignment = object.objectNode();

      for(String word : align.asText().trim().toLowerCase().split("\\s+")) {
         if(word.isEmpty()) {
            continue;
         }

         switch(word) {
         case "left" -> alignment.put("halign", "Left");
         case "center" -> alignment.put("halign", "Center");
         case "right" -> alignment.put("halign", "Right");
         case "top" -> alignment.put("valign", "Top");
         case "middle" -> alignment.put("valign", "Middle");
         case "bottom" -> alignment.put("valign", "Bottom");
         default -> throw new IllegalArgumentException(
            toolName + " could not read 'align': '" + word + "' is not an alignment. " +
            "Horizontal: left, center, right. Vertical: top, middle, bottom. " +
            "Both may be given together, as \"center middle\".");
         }
      }

      object.set("align", alignment);
   }

   /**
    * Lets the four border styles be written as CSS words.
    *
    * <p>The underlying model is asymmetric: {@code FormatInfoModel.getBorderStyle} <em>reads</em>
    * "solid"/"dashed"/"dotted"/"double", while the write goes through
    * {@code FormatPainterService}, which does {@code Integer.parseInt} on the same field. So the
    * word this API documents — and the word that comes back out of it — could never be written,
    * and failed with a raw {@code For input string: "solid"} naming no field at all.
    *
    * <p>A number still passes through untouched, for a caller that already has the constant.
    */
   private static void coerceBorderStyles(ObjectNode object, String toolName) {
      for(int i = 0; i < BORDER_STYLES.size(); i++) {
         String side = BORDER_STYLES.get(i);
         String widthField = BORDER_WIDTHS.get(i);

         // Consumed here whatever happens: nothing downstream reads it, so leaving it in the
         // payload is what made it a silent no-op. See coerceBorderWidth.
         JsonNode width = object.remove(widthField);
         JsonNode style = object.get(side);

         if(style == null && width == null) {
            continue;
         }

         if(style != null && !style.isTextual()) {
            continue;
         }

         String word = style == null ? "solid" : style.asText().trim().toLowerCase();

         if(word.chars().allMatch(Character::isDigit)) {
            if(width != null) {
               throw new IllegalArgumentException(
                  toolName + " got both '" + side + "' as a line constant (" + word + ") and '" +
                  widthField + "'. The constant already encodes the weight, so honouring both " +
                  "is ambiguous. Drop '" + widthField + "', or give '" + side + "' as a word.");
            }

            continue;
         }

         object.put(side, String.valueOf(toLineConstant(word, width, side, widthField, toolName)));
      }
   }

   /**
    * Folds a CSS border width into the line constant, which is where StyleBI keeps weight.
    *
    * <p>{@code FormatPainterService} builds its {@code Insets} from the four <em>style</em>
    * fields alone — {@code borderTopWidth} and its siblings are never read on the write path.
    * They are part of {@code FormatInfoModel} and are documented by this tool's own schema, so a
    * caller asking for a 3px border got a thin one and nothing said otherwise. Consuming the
    * field and folding it into the constant makes the documented parameter mean something.
    *
    * <p>Weight only exists for two families: solid (thin/medium/thick) and dash
    * (dash/medium/large). There is no thick dotted or thick double line, so those combinations
    * fail loud rather than quietly rendering a thin one — the same failure in a new disguise.
    */
   private static int toLineConstant(String word, JsonNode width, String side,
                                     String widthField, String toolName)
   {
      Integer px = coerceBorderWidth(width, widthField, toolName);

      if(px != null && px == 0) {
         return StyleConstants.NO_BORDER;
      }

      boolean weighted = px != null && px > 1;

      return switch(word) {
         case "none" -> StyleConstants.NO_BORDER;
         case "solid" -> !weighted ? StyleConstants.THIN_LINE
            : px == 2 ? StyleConstants.MEDIUM_LINE : StyleConstants.THICK_LINE;
         case "dashed" -> !weighted ? StyleConstants.DASH_LINE
            : px == 2 ? StyleConstants.MEDIUM_DASH : StyleConstants.LARGE_DASH;
         case "dotted", "double" -> {
            if(weighted) {
               throw new IllegalArgumentException(
                  toolName + " cannot apply '" + widthField + "' to a " + word + " border: " +
                  "StyleBI has no weighted " + word + " line. Use a solid or dashed border for " +
                  "a thicker line, or drop '" + widthField + "'.");
            }

            yield "dotted".equals(word) ? StyleConstants.DOT_LINE : StyleConstants.DOUBLE_LINE;
         }
         // Weight words carry their own thickness, so a width alongside them is a contradiction
         // rather than extra detail.
         case "thin", "medium", "thick" -> {
            if(px != null) {
               throw new IllegalArgumentException(
                  toolName + " got '" + side + "' as '" + word + "', which already sets the " +
                  "weight, together with '" + widthField + "'. Drop one — use 'solid' with a " +
                  "width, or the weight word on its own.");
            }

            yield "thin".equals(word) ? StyleConstants.THIN_LINE
               : "medium".equals(word) ? StyleConstants.MEDIUM_LINE : StyleConstants.THICK_LINE;
         }
         default -> throw new IllegalArgumentException(
            toolName + " could not read '" + side + "': '" + word + "' is not a border " +
            "style. Accepted: none, solid, dashed, dotted, double, thin, medium, thick. " +
            "A StyleBI line constant is accepted as a number.");
      };
   }

   /** Accepts 3, "3" and "3px"; refuses anything else by name rather than dropping it. */
   private static Integer coerceBorderWidth(JsonNode width, String widthField, String toolName) {
      if(width == null || width.isNull()) {
         return null;
      }

      if(width.isNumber()) {
         return width.asInt();
      }

      String text = width.asText().trim().toLowerCase();

      if(text.endsWith("px")) {
         text = text.substring(0, text.length() - 2).trim();
      }

      try {
         return Integer.valueOf(text);
      }
      catch(NumberFormatException e) {
         throw new IllegalArgumentException(
            toolName + " could not read '" + widthField + "': '" + width.asText() + "' is not a " +
            "width. Give a number of pixels, e.g. 1, 2 or 3 (\"2px\" is accepted).");
      }
   }

   private static final List<String> BORDER_STYLES =
      List.of("borderTopStyle", "borderLeftStyle", "borderBottomStyle", "borderRightStyle");

   /** Index-aligned with {@link #BORDER_STYLES}. */
   private static final List<String> BORDER_WIDTHS =
      List.of("borderTopWidth", "borderLeftWidth", "borderBottomWidth", "borderRightWidth");

   private static final ObjectMapper MAPPER = new ObjectMapper();

   /**
    * What a {@code set_format} call reports back.
    *
    * @param warnings things the call applied only partly or could not confirm: messages the
    *                 Composer's format engine raised (e.g. a number format on a string column),
    *                 a read-back that found a written cell without the requested format, and
    *                 the session's own post-write warnings. Empty when nothing warned. A request
    *                 that cannot take effect at all is refused with a 400 instead.
    */
   public record FormatResult(List<String> warnings) {
   }

   public FormatResult setFormat(String sessionToken, Principal user, FormatRequest request,
                                 String linkUri) throws Exception
   {
      if(request.assemblies() == null || request.assemblies().isEmpty()) {
         throw new IllegalArgumentException(
            "set_format requires 'assemblies' with at least one assembly name.");
      }

      if(request.format() == null && !request.reset()) {
         throw new IllegalArgumentException(
            "set_format requires 'format' unless 'reset' is true.");
      }

      String target = requireTarget(request.target());

      if("text".equals(target)) {
         if(request.assemblies().size() != 1) {
            throw new IllegalArgumentException(
               "set_format: target 'text' formats a single chart's aesthetic-bound field at a " +
               "time; got " + request.assemblies().size() + " assemblies.");
         }

         if(request.field() == null || request.field().isBlank()) {
            throw new IllegalArgumentException(
               "set_format: target 'text' requires 'field' — the column bound to the chart's " +
               "text aesthetic channel.");
         }
      }

      if("field".equals(target)) {
         return setFieldFormat(sessionToken, user, request);
      }

      // Binding-only refusals run against a read-only resolve, before mutate: a refused request
      // then leaves no empty undo step, no write-revision bump and no Composer refresh behind.
      // A missing or unsupported assembly is skipped here and left to the existing error paths.
      boolean chartCheck = needsChartValueFormatCheck(request, target);
      boolean numericCheck = needsNumericCellCheck(request, target);

      if(chartCheck || numericCheck) {
         RuntimeViewsheet resolved = sessions.resolve(sessionToken, user);
         Viewsheet viewsheet = resolved == null ? null : resolved.getViewsheet();

         if(viewsheet != null) {
            for(String name : request.assemblies()) {
               if(chartCheck) {
                  refuseValueFormatOnChart(viewsheet, name, target,
                                           request.format().getFormat());
               }

               if(numericCheck) {
                  refuseDateFormatOverNumericCells(viewsheet, name, target,
                                                   request.format().getFormat());
               }
            }
         }
      }

      Set<String> warnings = new LinkedHashSet<>();

      List<String> sessionWarnings = sessions.mutate(sessionToken, user, (rvs, runtimeId,
                                                                          dispatcher) -> {
         FormatVSObjectEvent event = new FormatVSObjectEvent();
         event.setFormat(request.format());
         event.setReset(request.reset());

         if("text".equals(target)) {
            // Routes through FormatPainterService's per-chart loop instead of the whole-object
            // path: a single named field on the chart's text aesthetic, all points (-1).
            event.setObjects(new String[0]);
            event.setCharts(request.assemblies().toArray(new String[0]));
            event.setRegions(new String[]{ ChartFormatConstants.TEXT });
            event.setColumnNames(new String[][]{ { request.field() } });
            event.setIndexes(new int[][]{ { -1 } });
         }
         else {
            event.setObjects(request.assemblies().toArray(new String[0]));
            // FormatPainterService iterates `event.getCharts().length` unguarded, so a null here
            // is an immediate NPE — every set_format call, format or reset alike, failed with a
            // 500. An empty array is the correct value for assembly-level formatting: the
            // chart-region branches that read getColumnNames()/getIndexes()/getRegions() all live
            // inside that loop, so they never execute for these requests.
            event.setCharts(new String[0]);

            // TITLEPATH is FormatPainterService's own per-assembly TableDataPath[] mechanism
            // (event.getData(), indexed 1:1 with event.getObjects()) — the same generic slot
            // every titled assembly (table, gauge, crosstab, chart, ...) already stores its own
            // title-bar format in, entirely separate from a chart's axis/legend descriptors.
            // Leaving data null (the default) falls through to a whole-OBJECT write, same as
            // before this parameter existed.
            if("title".equals(target)) {
               ArrayList<TableDataPath[]> data = new ArrayList<>();

               for(int i = 0; i < request.assemblies().size(); i++) {
                  data.add(new TableDataPath[]{ VSAssemblyInfo.TITLEPATH });
               }

               event.setData(data);
            }
            else if("data".equals(target)) {
               // Same event.getData() per-path mechanism as "title" above, but computed per
               // assembly instead of a single shared constant: a Crosstab/Table's per-cell
               // rendering discards an OBJECT-level format write whenever any table style
               // applies (VSFormatTableLens's "styled" gate) -- true for essentially every
               // realistic Crosstab/Table -- so "object" can never reach body text. Writing
               // directly to each cell's own TableDataPath bypasses that gate entirely, the
               // same way a human dragging over data cells in the native UI already does
               // (FormatPainterService's paths != null branch, below).
               ArrayList<TableDataPath[]> data = new ArrayList<>();

               for(String name : request.assemblies()) {
                  data.add(requirePaths(computeDataRegionPaths(rvs, name, request.field()),
                                        name, "body"));
               }

               event.setData(data);
            }
            else if("header".equals(target)) {
               // Same event.getData() per-path mechanism as "data" above, but computed over the
               // header-family paths that method deliberately excludes -- bug 76917: a Crosstab/
               // Table's HEADER/GROUP_HEADER/SUMMARY_HEADER cells had no route to a format write
               // that beats an active table style (VSFormatTableLens's per-cell styleForeground
               // gate strips "object"; computeDataRegionPaths excludes header paths by design).
               ArrayList<TableDataPath[]> data = new ArrayList<>();

               for(String name : request.assemblies()) {
                  data.add(requirePaths(computeHeaderRegionPaths(rvs, name, request.field()),
                                        name, "header"));
               }

               event.setData(data);
            }
         }

         // Snapshot what was asked for before the painter runs: it can null the request's own
         // format in place (FormatPainterService.handleHeaderFormats), and that object is
         // request.format().
         Requested requested = Requested.of(request.format(), request.reset());

         // Stale messages from an earlier request on this pooled thread are not this call's.
         CoreTool.clearUserMessage();

         List<FormatVSObjectEvent> events = "text".equals(target) ? List.of(event) :
            seededEvents(rvs, request.format(), request.reset(), request.formatKeys(),
                         request.assemblies(), event.getData(), event);

         for(FormatVSObjectEvent part : events) {
            List<String> names = Arrays.asList(part.getObjects());
            Set<String> engineMessages = new LinkedHashSet<>();

            try {
               painter.setFormat(runtimeId, part, user, dispatcher, linkUri);
            }
            finally {
               drainUserMessages(engineMessages);
            }

            if("object".equals(target)) {
               dropUntrueStringColumnWarning(engineMessages, rvs, names, user);
            }

            warnings.addAll(engineMessages);

            if(requested != null && !"text".equals(target)) {
               readBack(rvs, names, target, part, requested, warnings);
            }

            if(!"text".equals(target)) {
               warnBorderColorWithoutStyle(rvs, request.reset(), request.formatKeys(), names,
                                           part.getData(), warnings);
            }
         }
      });

      if(sessionWarnings != null) {
         warnings.addAll(sessionWarnings);
      }

      return new FormatResult(new ArrayList<>(warnings));
   }

   private static final List<String> BORDER_SIDES = List.of("Top", "Left", "Bottom", "Right");
   private static final ObjectMapper COPY_MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   private record Unit(String assembly, TableDataPath[] paths) {
   }

   /**
    * What the painter would otherwise overwrite with the request's blank defaults: the assembly's
    * gauge fill / tab corner flags, the path's CSS class/id, and the border sides and colours the
    * caller did not send (null when no border key was sent).
    */
   private record SeedKey(String valueFill, Boolean roundTop, Boolean roundBottom,
                          String cssClass, String cssId, List<String> border)
   {
   }

   private static boolean styleSent(Set<String> keys, String side) {
      return keys.contains("border" + side + "Style") || keys.contains("border" + side + "Width");
   }

   private static boolean colorSent(Set<String> keys, String side) {
      return keys.contains("border" + side + "Color");
   }

   private static boolean anyBorderKeySent(Set<String> keys) {
      return BORDER_SIDES.stream().anyMatch(s -> styleSent(keys, s) || colorSent(keys, s));
   }

   /**
    * Bug #77044 items 3-5: {@code FormatPainterService} writes a property only when the request's
    * model differs from {@code event.getOrigFormat()}, and rewrites the whole border group, the
    * gauge fill, the tab corner flags and the CSS class/id from the model whenever it runs. A
    * request model that is blank except for the keys sent therefore (a) skipped any sent value
    * equal to the blank default (wrapText false, roundCorner 0, alpha 100, black), and (b) erased
    * every stored value the request did not carry. Each sent key group is therefore made to
    * differ in the original format, and the values the painter writes unconditionally are seeded
    * from what is stored. A request without {@code formatKeys} (built in Java), a reset, and a
    * text-region write keep the single legacy event.
    */
   private List<FormatVSObjectEvent> seededEvents(RuntimeViewsheet rvs,
                                                  VSObjectFormatInfoModel format, boolean reset,
                                                  Set<String> keys, List<String> assemblies,
                                                  List<TableDataPath[]> data,
                                                  FormatVSObjectEvent base)
   {
      if(format == null || reset || keys == null) {
         return List.of(base);
      }

      Viewsheet viewsheet = rvs == null ? null : rvs.getViewsheet();
      boolean border = anyBorderKeySent(keys);
      Map<SeedKey, List<Unit>> groups = new LinkedHashMap<>();

      for(int i = 0; i < assemblies.size(); i++) {
         String name = assemblies.get(i);
         VSAssembly assembly = viewsheet == null ? null : viewsheet.getAssembly(name);
         VSAssemblyInfo info = assembly == null ? null : assembly.getVSAssemblyInfo();
         TableDataPath[] paths = data != null && data.size() > i ? data.get(i) : null;

         if(paths == null || paths.length == 0) {
            groups.computeIfAbsent(seedKey(info, VSAssemblyInfo.OBJECTPATH, keys, border),
                                   k -> new ArrayList<>()).add(new Unit(name, null));
            continue;
         }

         Map<SeedKey, List<TableDataPath>> byKey = new LinkedHashMap<>();

         for(TableDataPath path : paths) {
            byKey.computeIfAbsent(seedKey(info, path, keys, border), k -> new ArrayList<>())
               .add(path);
         }

         byKey.forEach((key, list) -> groups.computeIfAbsent(key, k -> new ArrayList<>())
            .add(new Unit(name, list.toArray(new TableDataPath[0]))));
      }

      boolean copy = groups.size() > 1;
      List<FormatVSObjectEvent> events = new ArrayList<>();

      for(Map.Entry<SeedKey, List<Unit>> group : groups.entrySet()) {
         VSObjectFormatInfoModel model = copy ? copyModel(format) : format;
         applySeed(model, group.getKey(), keys);

         FormatVSObjectEvent event = new FormatVSObjectEvent();
         event.setFormat(model);
         event.setOrigFormat(perturbedOrig(model, keys, border));
         event.setReset(false);
         event.setObjects(group.getValue().stream().map(Unit::assembly).toArray(String[]::new));
         event.setCharts(new String[0]);

         if(data != null) {
            ArrayList<TableDataPath[]> paths = new ArrayList<>();
            group.getValue().forEach(unit -> paths.add(unit.paths()));
            event.setData(paths);
         }

         events.add(event);
      }

      return events;
   }

   private static SeedKey seedKey(VSAssemblyInfo info, TableDataPath path, Set<String> keys,
                                  boolean border)
   {
      String valueFill = null;
      Boolean roundTop = null;
      Boolean roundBottom = null;
      String cssClass = null;
      String cssId = null;
      List<String> borderSeed = null;

      if(info instanceof GaugeVSAssemblyInfo gauge) {
         valueFill = gauge.getValueFillColorValue();
      }

      if(info instanceof TabVSAssemblyInfo tab) {
         roundTop = tab.isRoundTopCornersOnly();
         roundBottom = tab.isRoundBottomCornersOnly();
      }

      FormatInfo formatInfo = info == null ? null : info.getFormatInfo();

      if(formatInfo != null) {
         // The same lookup the painter makes, so the seed is what its CSS comparison will see.
         VSCompositeFormat composite = formatInfo.getFormat(path, false);

         if(composite != null) {
            cssClass = composite.getCSSFormat().getCSSClass();
            cssId = composite.getCSSFormat().getCSSID();
         }

         if(border) {
            // The user-defined layer only: seeding the effective (CSS/inherited) borders would pin
            // them as explicit. A path whose borders come from CSS has no user layer, so its
            // unsent sides are written as no border, as the Composer does for a hand-set side.
            VSCompositeFormat stored = formatInfo.getFormat(path);
            VSFormat user = stored == null ? null : stored.getUserDefinedFormat();
            Insets insets = user == null ? null : user.getBordersValue();
            BorderColors colors = user == null ? null : user.getBorderColorsValue();
            String[] seed = new String[8];

            for(int i = 0; i < 4; i++) {
               String side = BORDER_SIDES.get(i);

               if(insets != null && !styleSent(keys, side)) {
                  seed[i] = String.valueOf(switch(i) {
                     case 0 -> insets.top;
                     case 1 -> insets.left;
                     case 2 -> insets.bottom;
                     default -> insets.right;
                  });
               }

               Color color = colors == null || colorSent(keys, side) ? null : switch(i) {
                  case 0 -> colors.topColor;
                  case 1 -> colors.leftColor;
                  case 2 -> colors.bottomColor;
                  default -> colors.rightColor;
               };

               seed[4 + i] = color == null ? null : "#" + Tool.colorToHTMLString(color);
            }

            borderSeed = Arrays.asList(seed);
         }
      }

      return new SeedKey(valueFill, roundTop, roundBottom, cssClass, cssId, borderSeed);
   }

   private static void applySeed(VSObjectFormatInfoModel model, SeedKey seed, Set<String> keys) {
      if(!keys.contains("valueFillColor")) {
         model.setValueFillColor(seed.valueFill());
      }

      if(seed.roundTop() != null && !keys.contains("roundTopCornersOnly")) {
         model.setRoundTopCornersOnly(seed.roundTop());
      }

      if(seed.roundBottom() != null && !keys.contains("roundBottomCornersOnly")) {
         model.setRoundBottomCornersOnly(seed.roundBottom());
      }

      if(!keys.contains("cssClass")) {
         model.setCssClass(seed.cssClass());
      }

      if(!keys.contains("cssID")) {
         model.setCssID(seed.cssId());
      }

      List<String> border = seed.border();

      if(border == null) {
         return;
      }

      if(border.get(0) != null) {
         model.setBorderTopStyle(border.get(0));
      }

      if(border.get(1) != null) {
         model.setBorderLeftStyle(border.get(1));
      }

      if(border.get(2) != null) {
         model.setBorderBottomStyle(border.get(2));
      }

      if(border.get(3) != null) {
         model.setBorderRightStyle(border.get(3));
      }

      if(border.get(4) != null) {
         model.setBorderTopColor(border.get(4));
      }

      if(border.get(5) != null) {
         model.setBorderLeftColor(border.get(5));
      }

      if(border.get(6) != null) {
         model.setBorderBottomColor(border.get(6));
      }

      if(border.get(7) != null) {
         model.setBorderRightColor(border.get(7));
      }
   }

   private static VSObjectFormatInfoModel copyModel(VSObjectFormatInfoModel model) {
      return COPY_MAPPER.convertValue(model, VSObjectFormatInfoModel.class);
   }

   /**
    * A blank original format with each sent key group made to differ from {@code model}, so the
    * painter writes it. Only the diff reads these values, except {@code format}, which
    * {@code FormatPainterService.isFormattedStringColumn} tests for null and is therefore left
    * null. Border colours are parsed by the painter, so only the four border styles are
    * perturbed.
    */
   private static VSObjectFormatInfoModel perturbedOrig(VSObjectFormatInfoModel model,
                                                        Set<String> keys, boolean border)
   {
      VSObjectFormatInfoModel orig = new VSObjectFormatInfoModel();

      if(keys.contains("color")) {
         orig.setColorType("\u0000");
      }

      if(keys.contains("backgroundColor")) {
         orig.setBackgroundColorType("\u0000");
      }

      if(keys.contains("backgroundAlpha")) {
         orig.setBackgroundAlpha(-1);
      }

      if(keys.contains("roundCorner")) {
         orig.setRoundCorner(-1);
      }

      if(keys.contains("wrapText")) {
         orig.setWrapText(!model.isWrapText());
      }

      if(keys.contains("align")) {
         int align = model.getAlign() == null ? 0 : model.getAlign().toAlign();

         for(int candidate : new int[]{ StyleConstants.H_LEFT, StyleConstants.H_CENTER,
                                        StyleConstants.H_RIGHT })
         {
            AlignmentInfo sentinel = new AlignmentInfo(candidate);

            if(sentinel.toAlign() != align) {
               orig.setAlign(sentinel);
               break;
            }
         }
      }

      // font stays null: a sent font is non-null. FontInfo.equals ignores the caps/shadow/
      // sub/superscript flags and FontInfo.toFont does not write them, so the two must change
      // together if those flags are ever supported.

      if(keys.contains("format") || keys.contains("formatSpec") || keys.contains("dateSpec") ||
         keys.contains("durationPadZeros"))
      {
         orig.setFormatSpec("\u0000");
         orig.setDateSpec("\u0000");
      }

      if(border) {
         orig.setBorderTopStyle("-1");
         orig.setBorderLeftStyle("-1");
         orig.setBorderBottomStyle("-1");
         orig.setBorderRightStyle("-1");
      }

      return orig;
   }

   /**
    * Bug #77044 item 7: a border colour sent with no style, on an assembly that has no border on
    * any side, is stored but draws nothing; say so rather than report a silent success.
    */
   private static void warnBorderColorWithoutStyle(RuntimeViewsheet rvs, boolean reset,
                                                   Set<String> keys, List<String> names,
                                                   List<TableDataPath[]> data,
                                                   Set<String> warnings)
   {
      if(reset || keys == null || rvs == null || rvs.getViewsheet() == null) {
         return;
      }

      boolean color = BORDER_SIDES.stream().anyMatch(s -> colorSent(keys, s));
      boolean style = BORDER_SIDES.stream().anyMatch(s -> styleSent(keys, s));

      if(!color || style) {
         return;
      }

      for(int i = 0; i < names.size(); i++) {
         VSAssembly assembly = rvs.getViewsheet().getAssembly(names.get(i));
         VSAssemblyInfo info = assembly == null ? null : assembly.getVSAssemblyInfo();
         FormatInfo formatInfo = info == null ? null : info.getFormatInfo();

         if(formatInfo == null) {
            continue;
         }

         TableDataPath[] paths = data != null && data.size() > i && data.get(i) != null ?
            data.get(i) : new TableDataPath[]{ VSAssemblyInfo.OBJECTPATH };
         boolean drawn = false;

         for(TableDataPath path : paths) {
            VSCompositeFormat stored = formatInfo.getFormat(path);
            VSFormat user = stored == null ? null : stored.getUserDefinedFormat();
            Insets insets = user == null ? null : user.getBordersValue();

            if(insets != null &&
               (insets.top != 0 || insets.left != 0 || insets.bottom != 0 || insets.right != 0))
            {
               drawn = true;
            }
         }

         if(!drawn) {
            warnings.add(
               "set_format: border colour set on '" + names.get(i) + "' but no side has a " +
               "border style, so nothing is drawn yet; also set borderTopStyle/" +
               "borderLeftStyle/borderBottomStyle/borderRightStyle to show it.");
         }
      }
   }

   /**
    * Bug #77597: a {@code data}/{@code header} write that found no cells is refused.
    * {@code FormatPainterService} treats an empty path list as "no paths" and falls through to a
    * whole-object write, so the request used to be applied to something else (headers included)
    * and reported as a success. The paths are empty when the assembly has no rendered rows, no
    * sandbox, or no table lens.
    */
   private static TableDataPath[] requirePaths(TableDataPath[] paths, String name,
                                               String region)
   {
      if(paths == null || paths.length == 0) {
         throw new IllegalArgumentException(
            "set_format: could not find any " + region + " cells on '" + name + "' to format " +
            "(it has no rendered rows, or its data could not be computed); the request was not " +
            "applied as a whole-table format. Render or refresh the viewsheet and try again, " +
            "or use target 'object'.");
      }

      return paths;
   }

   /**
    * Bug #77597 review: the painter's "Format applied to a string column" warning is true for
    * an {@code object} write only when the table really has a string column.
    *
    * <p>{@code FormatPainterService.isFormattedStringColumn} tests the data type of the path
    * being written. For a whole-object write that path is {@code VSAssemblyInfo.OBJECTPATH},
    * whose data type is the {@code TableDataPath} constructor default, {@code string} -- not a
    * column's type -- so the painter raises it on every whole-object value format to a Table,
    * an all-numeric one included. The Composer shares that engine, so it is left unchanged and
    * the message is checked here instead: it is passed on only when one of the named Tables has
    * a visible string-typed column in its binding. {@code data}/{@code header} writes are not
    * filtered: their paths come from the lens and carry the real column type.
    */
   private static void dropUntrueStringColumnWarning(Set<String> messages, RuntimeViewsheet rvs,
                                                     List<String> assemblies, Principal user)
   {
      if(messages.isEmpty()) {
         return;
      }

      String warning = Catalog.getCatalog(user).getString("composer.stringColumnFormat");

      if(warning == null || !messages.contains(warning.trim()) ||
         hasVisibleStringColumn(rvs, assemblies))
      {
         return;
      }

      messages.remove(warning.trim());
   }

   /** Whether any named assembly is a Table whose binding has a visible string column. */
   private static boolean hasVisibleStringColumn(RuntimeViewsheet rvs, List<String> assemblies) {
      Viewsheet viewsheet = rvs == null ? null : rvs.getViewsheet();

      if(viewsheet == null) {
         return false;
      }

      for(String name : assemblies) {
         if(!(viewsheet.getAssembly(name) instanceof TableVSAssembly table) ||
            table.getColumnSelection() == null)
         {
            continue;
         }

         ColumnSelection columns = table.getColumnSelection();

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            DataRef ref = columns.getAttribute(i);

            if(ref instanceof ColumnRef column && !column.isVisible()) {
               continue;
            }

            if(ref != null && XSchema.STRING.equals(ref.getDataType())) {
               return true;
            }
         }
      }

      return false;
   }

   /** Moves the Composer format engine's user messages (Tool.addUserMessage) into warnings. */
   private static void drainUserMessages(Set<String> warnings) {
      UserMessage message = CoreTool.getUserMessage();

      if(message != null && message.getMessage() != null) {
         message.getMessage().lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .forEach(warnings::add);
      }
   }

   /**
    * The value format a caller asked for, captured before the painter runs.
    *
    * @param type       the format type as the painter stores it (CommaFormat is stored as
    *                   DecimalFormat, DurationFormat as its padding variant)
    * @param customDate the caller's pattern when it asked for a custom date pattern, else null
    */
   private record Requested(String type, String customDate) {
      static Requested of(VSObjectFormatInfoModel format, boolean reset) {
         if(reset || format == null || format.getFormat() == null ||
            format.getFormat().isEmpty())
         {
            return null;
         }

         String type = FormatInfoModel.getDurationFormat(format.getFormat(),
                                                         format.isDurationPadZeros());

         if(XConstants.COMMA_FORMAT.equals(type)) {
            type = XConstants.DECIMAL_FORMAT;
         }

         String spec = format.getFormatSpec();
         boolean customDate = XConstants.DATE_FORMAT.equals(type) &&
            CUSTOM_DATE_SPEC.equals(format.getDateSpec()) && spec != null && !spec.isEmpty();
         return new Requested(type, customDate ? spec : null);
      }
   }

   /**
    * Bug #77597: a safety net for silent drops. Reads each written path's stored user format
    * with the non-mutating {@link FormatInfo#getFormat(TableDataPath)} -- never
    * {@code getFormat(path, false)}, which rewrites path defaults in place -- and warns when a
    * written path holds no format of the requested type, or holds a requested custom date
    * pattern's type without the pattern (the #2 failure). Only the type is compared, plus that
    * one pattern check, so this cannot drift from the painter's own spec translations.
    */
   private static void readBack(RuntimeViewsheet rvs, List<String> assemblies, String target,
                                FormatVSObjectEvent event, Requested requested,
                                Set<String> warnings)
   {
      Viewsheet viewsheet = rvs == null ? null : rvs.getViewsheet();

      if(viewsheet == null) {
         return;
      }

      for(int i = 0; i < assemblies.size(); i++) {
         String name = assemblies.get(i);
         VSAssembly assembly = viewsheet.getAssembly(name);
         VSAssemblyInfo info = assembly == null ? null : assembly.getVSAssemblyInfo();
         FormatInfo formatInfo = info == null ? null : info.getFormatInfo();

         // An ID-mode selection tree has its non-object paths rewritten by the painter, so the
         // path written is not the path asked for.
         if(formatInfo == null || assembly instanceof SelectionTreeVSAssembly tree &&
            tree.isIDMode() && !"object".equals(target))
         {
            continue;
         }

         TableDataPath[] paths = "object".equals(target) ?
            new TableDataPath[]{ VSAssemblyInfo.OBJECTPATH } :
            event.getData() != null && event.getData().size() > i ? event.getData().get(i) : null;

         if(paths == null) {
            continue;
         }

         int missing = 0;
         int noPattern = 0;
         String stored = null;

         for(TableDataPath path : paths) {
            VSCompositeFormat format = path == null ? null : formatInfo.getFormat(path);
            VSFormat user = format == null ? null : format.getUserDefinedFormat();
            String type = user == null ? null : user.getFormatValue();

            if(!requested.type().equals(type)) {
               missing++;
               stored = type;
            }
            else if(requested.customDate() != null && user.getFormatExtentValue() == null) {
               noPattern++;
            }
         }

         if(missing > 0) {
            warnings.add(
               "set_format: requested " + requested.type() + " on '" + name + "', but " +
               missing + " of " + paths.length + " written " + regionName(target) +
               " path(s) stored " + (stored == null ? "no format" : stored) +
               "; those cells will not show it.");
         }

         if(noPattern > 0) {
            warnings.add(
               "set_format: requested DateFormat '" + requested.customDate() + "' on '" + name +
               "', but " + noPattern + " of " + paths.length + " written path(s) stored " +
               "DateFormat with no pattern; those cells will show the default yyyy-MM-dd.");
         }
      }
   }

   /**
    * Bug #77597: {@code target: "field"} -- one chart field's value format (number, date,
    * percent ...), wherever that field renders: its axis labels, legend, data labels and plot
    * slots. Written through the wizard's own field-format writer
    * ({@link VSWizardBindingHandler#applyFieldFormats}), the one route that reaches an axis's or
    * field's value format; a chart's whole-object format carries only font and colour to its
    * axes, so a number format sent there never rendered.
    *
    * <p>Every refusal that needs only the binding runs before {@code mutate}. {@code reset}
    * clears the field's user value format (a defined-null format), so its default renders again.
    * Same-type formulas (sum/max/min/first/last of one column) share one format key, and on a
    * chart that is not separated by measure the value axis is shared by its measures; both are
    * the wizard's behaviour and are reported as a warning where they apply.
    */
   private FormatResult setFieldFormat(String sessionToken, Principal user, FormatRequest request)
      throws Exception
   {
      if(request.assemblies().size() != 1) {
         throw new IllegalArgumentException(
            "set_format: target 'field' formats one chart field at a time; got " +
            request.assemblies().size() + " assemblies.");
      }

      String field = request.field();

      if(field == null || field.isBlank()) {
         throw new IllegalArgumentException(
            "set_format: target 'field' requires 'field' -- the chart field (as get_binding " +
            "reports it, e.g. \"Sum(Revenue)\") whose value format to set.");
      }

      if(request.formatKeys() != null) {
         List<String> unsupported = request.formatKeys().stream()
            .filter(key -> !FIELD_FORMAT_KEYS.contains(key))
            .toList();

         if(!unsupported.isEmpty()) {
            throw new IllegalArgumentException(
               "set_format: target 'field' sets only a field's value format (format, " +
               "formatSpec, dateSpec, durationPadZeros); " + quoted(unsupported) +
               (unsupported.size() == 1 ? " is" : " are") + " not applied there -- use target " +
               "'object' or 'text' for them.");
         }
      }

      VSFormat format = null;

      if(!request.reset()) {
         VSObjectFormatInfoModel model = request.format();

         if(model == null || model.getFormat() == null || model.getFormat().isBlank()) {
            throw new IllegalArgumentException(
               "set_format: target 'field' requires format.format (e.g. \"DecimalFormat\" " +
               "with a formatSpec), or reset: true to clear the field's value format.");
         }

         format = toFieldFormat(model);
      }

      String name = request.assemblies().get(0);
      RuntimeViewsheet resolved = sessions.resolve(sessionToken, user);
      Viewsheet viewsheet = resolved == null ? null : resolved.getViewsheet();
      VSAssembly assembly = viewsheet == null ? null : viewsheet.getAssembly(name);

      if(!(assembly instanceof ChartVSAssembly chart)) {
         throw new IllegalArgumentException(
            "set_format: target 'field' only applies to a chart; '" + name + "' is " +
            (assembly == null ? "not found" : assembly.getClass().getSimpleName()) + ".");
      }

      VSChartInfo chartInfo = chart.getVSChartInfo();
      List<ChartRef> refs = VSWizardBindingHandler.collectFormattableRefs(chartInfo);

      if(refs.stream().noneMatch(ref -> field.equals(ref.getFullName()))) {
         String bindable = refs.stream()
            .map(ChartRef::getFullName)
            .distinct()
            .sorted()
            .collect(Collectors.joining(", "));

         throw new IllegalArgumentException(
            "No such field(s) in this chart's binding: " + field +
            ". Bindable fields: " + (bindable.isEmpty() ? "(none)" : bindable));
      }

      if(format != null) {
         WizFormatChecks.checkFormatFitsFieldType(field, format, refs);
      }

      Set<String> warnings = new LinkedHashSet<>();
      String shared = sharedValueAxisWarning(chartInfo, refs, field, request.reset());

      if(shared != null) {
         warnings.add(shared);
      }

      VSFormat toApply = request.reset() ? VSWizardBindingHandler.clearingFormat() : format;

      List<String> sessionWarnings = sessions.mutate(sessionToken, user, (rvs, runtimeId,
                                                                          dispatcher) -> {
         Viewsheet live = rvs == null ? null : rvs.getViewsheet();
         VSAssembly liveAssembly = live == null ? null : live.getAssembly(name);

         if(!(liveAssembly instanceof ChartVSAssembly liveChart)) {
            throw new IllegalArgumentException(
               "set_format: '" + name + "' is no longer a chart; nothing was formatted.");
         }

         // Never Map.of: a reset must be able to carry its value, and Map.of rejects nulls.
         Map<String, VSFormat> formats = new HashMap<>();
         formats.put(field, toApply);
         Set<String> unmatched;
         CoreTool.clearUserMessage();

         try {
            unmatched = bindingHandler.applyFieldFormats(rvs, liveChart, formats);
         }
         finally {
            drainUserMessages(warnings);
         }

         // The binding was checked before mutate; a human may have rebound the chart since.
         if(!unmatched.isEmpty()) {
            throw new IllegalArgumentException(
               "set_format: '" + field + "' is no longer bound to chart '" + name + "'; nothing " +
               "was formatted. Read the binding again and retry.");
         }

         // applyFieldFormats only clears the runtime chart info. The cached VGraphPair holds this
         // same VSChartInfo, so the sandbox's staleness check cannot see the in-place change:
         // clear the cached descriptor and graph explicitly, as WizAutoBindingService does after
         // the same call, or the next render serves the old axis.
         ((ChartVSAssemblyInfo) liveChart.getVSAssemblyInfo()).setRTChartDescriptor(null);
         rvs.getViewsheetSandbox().ifPresent(box -> box.clearGraph(liveChart.getAbsoluteName()));
      });

      if(sessionWarnings != null) {
         warnings.addAll(sessionWarnings);
      }

      return new FormatResult(new ArrayList<>(warnings));
   }

   /**
    * The model as the wizard's field-format writer consumes it, with the same translations
    * {@code FormatPainterService.setUserFormat} makes: the duration padding folded into the type,
    * CommaFormat as DecimalFormat with {@code #,##0}, and a non-Custom {@code dateSpec} as the
    * pattern ({@link #parseFormat} has already derived {@code dateSpec}).
    */
   private static VSFormat toFieldFormat(VSObjectFormatInfoModel model) {
      String formatValue = FormatInfoModel.getDurationFormat(model.getFormat(),
                                                             model.isDurationPadZeros());
      String spec = model.getFormatSpec();

      if(XConstants.COMMA_FORMAT.equals(formatValue)) {
         formatValue = XConstants.DECIMAL_FORMAT;
         spec = "#,##0";
      }
      else if(XConstants.DATE_FORMAT.equals(formatValue) &&
         !CUSTOM_DATE_SPEC.equals(model.getDateSpec()))
      {
         spec = model.getDateSpec();
      }

      VSFormat format = new VSFormat();
      format.setFormatValue(formatValue);
      format.setFormatExtentValue(spec != null && !spec.isEmpty() ? spec : null);
      return format;
   }

   /**
    * On a chart that is not separated by measure (and is not a radar), every primary-axis
    * aggregate shares one value-axis descriptor, and every secondary-axis aggregate another
    * ({@code GraphUtil.getAxisDescriptor}). A field-scoped set or reset of one of them therefore
    * sets or clears that whole axis's format, including a format a human set through the
    * Composer's axis dialog. Returned as a warning naming the other measures; null otherwise.
    */
   private static String sharedValueAxisWarning(VSChartInfo info, List<ChartRef> refs,
                                                String field, boolean reset)
   {
      ChartRef ref = refs.stream()
         .filter(r -> field.equals(r.getFullName()))
         .findFirst()
         .orElse(null);

      if(!(ref instanceof ChartAggregateRef aggregate) || info instanceof RadarChartInfo ||
         info.isSeparatedGraph())
      {
         return null;
      }

      List<String> others = new ArrayList<>();

      for(ChartRef[] fields : List.of(info.getXFields(), info.getYFields())) {
         for(ChartRef other : fields) {
            if(other instanceof ChartAggregateRef otherAggregate &&
               otherAggregate.isSecondaryY() == aggregate.isSecondaryY() &&
               !field.equals(other.getFullName()) && !others.contains(other.getFullName()))
            {
               others.add(other.getFullName());
            }
         }
      }

      if(others.isEmpty()) {
         return null;
      }

      return "'" + field + "' shares its value axis with " + String.join(", ", others) +
         " on this chart (not separated by measure): this " + (reset ? "reset" : "set") +
         " applies to that whole axis's format.";
   }

   private static String quoted(List<String> names) {
      return names.stream().map(n -> "'" + n + "'").collect(Collectors.joining(", "));
   }

   /** The format keys target 'field' applies; any other key would be dropped silently. */
   private static final Set<String> FIELD_FORMAT_KEYS =
      Set.of("format", "formatSpec", "dateSpec", "durationPadZeros");

   /**
    * Whether {@code request} sends a value format (number, date ...) to a chart's whole-object
    * or title format, which nothing renders values with.
    */
   private static boolean needsChartValueFormatCheck(FormatRequest request, String target) {
      return !request.reset() && request.format() != null &&
         request.format().getFormat() != null && !request.format().getFormat().isEmpty() &&
         ("object".equals(target) || "title".equals(target));
   }

   /**
    * Bug #77597: refuses a number/date format on a chart's {@code object} or {@code title}
    * target. {@code VGraphPair} is the only graph-side reader of a chart's OBJECT format and
    * copies only its font and colour into the axis and label formats, so such a write was stored
    * and never rendered -- the axis kept its default ticks while the call reported success. A
    * missing or non-chart assembly is left to the existing paths.
    */
   private static void refuseValueFormatOnChart(Viewsheet viewsheet, String name, String target,
                                                String formatType)
   {
      if(!(viewsheet.getAssembly(name) instanceof ChartVSAssembly)) {
         return;
      }

      throw new IllegalArgumentException(
         "set_format: a " + formatType + " on chart '" + name + "''s " + target + " format is " +
         "never used to format values -- a chart's whole-object format carries only font and " +
         "colour to its axes and labels. Use target 'field' with 'field' naming the axis or " +
         "legend field (e.g. \"Sum(Revenue)\") for its value format, or target 'text' for " +
         "data labels; send font/colour without 'format' to keep using target '" + target +
         "'.");
   }

   /**
    * Whether {@code request} writes a date/time format over a whole table region, where numeric
    * cells would inherit it. A {@code data}/{@code header} write scoped by {@code field} is an
    * explicit single-column choice (e.g. an epoch-millisecond column) and is not checked.
    */
   private static boolean needsNumericCellCheck(FormatRequest request, String target) {
      if(request.reset() || request.format() == null || request.format().getFormat() == null ||
         !DATE_LIKE_FORMATS.contains(request.format().getFormat()))
      {
         return false;
      }

      boolean hasField = request.field() != null && !request.field().isBlank();
      return "object".equals(target) ||
         ("data".equals(target) || "header".equals(target)) && !hasField;
   }

   /**
    * Bug #77597: refuses a date/time format over a table region whose cells include numbers.
    *
    * <p>The table renderer hands such a format every cell of the region that has no format of
    * its own, whatever the column's type, and a {@code java.text.DateFormat} reads a
    * {@code Number} as epoch milliseconds -- so {@code Revenue = 3600} renders as
    * {@code 1970-01-01}. Types come from the binding only (no lens, no sandbox); a column whose
    * type is unknown never triggers a refusal.
    *
    * <ul>
    *    <li>Table: the visible columns, for {@code object} and {@code data}; never for
    *        {@code header} (header cells are strings). For {@code object} only, a column whose
    *        own {@code DETAIL} path already has a user format value is exempt, since it does
    *        not inherit the object format. A {@code data} write replaces that column's own
    *        format, so nothing is exempt there.</li>
    *    <li>Crosstab: dimensions (at their effective date level) and aggregates (their output
    *        type) for {@code object}; aggregates for {@code data}; dimensions for
    *        {@code header}. Measures are checked by type even if they carry their own format:
    *        a measure spans many cell paths, and proving all are covered needs the lens.</li>
    *    <li>Calc table, {@code object} only: the source columns its cells bind, typed from the
    *        source table. A cell's output type (e.g. a part-level grouping) is not modelled, so
    *        this fails open there.</li>
    * </ul>
    */
   private static void refuseDateFormatOverNumericCells(Viewsheet viewsheet, String name,
                                                        String target, String formatType)
   {
      VSAssembly assembly = viewsheet.getAssembly(name);

      if(!(assembly instanceof TableDataVSAssembly)) {
         return;
      }

      boolean object = "object".equals(target);
      List<String> numeric = new ArrayList<>();
      String advice;

      if(assembly instanceof CrosstabVSAssembly crosstab) {
         VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();

         if(cinfo == null) {
            return;
         }

         List<String> numericDims = new ArrayList<>();

         if(!"data".equals(target)) {
            addNumeric(numericDims, runtimeOrDesign(cinfo.getRuntimeRowHeaders(),
                                                    cinfo.getRowHeaders()));
            addNumeric(numericDims, runtimeOrDesign(cinfo.getRuntimeColHeaders(),
                                                    cinfo.getColHeaders()));
         }

         numeric.addAll(numericDims);

         if(!"header".equals(target)) {
            addNumeric(numeric, runtimeOrDesign(cinfo.getRuntimeAggregates(),
                                                cinfo.getAggregates()));
         }

         advice = !numericDims.isEmpty() ?
            "The header region holds the numeric dimension(s) too, so target:\"header\" would " +
            "show the same dates; format this Crosstab in the Composer or with a script, or " +
            "group the date dimension at a full level (Year, Month, ...)." :
            (object ? "Use target:\"header\" to format the date dimension labels only, or " :
             "Use ") + "target:\"data\" with 'field' naming one date-valued measure. Crosstab " +
            "measures are checked by type even if they already have their own format.";
      }
      else if(assembly instanceof TableVSAssembly table) {
         if("header".equals(target)) {
            return;
         }

         ColumnSelection columns = table.getColumnSelection();

         if(columns == null) {
            return;
         }

         Set<String> ownFormat = object ? ownDetailFormatHeaders(table) : Set.of();
         List<String> dateColumns = new ArrayList<>();

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            DataRef ref = columns.getAttribute(i);

            if(ref instanceof ColumnRef column && !column.isVisible()) {
               continue;
            }

            if(WizFormatChecks.isNumeric(ref)) {
               if(!ownFormat.isEmpty() && candidateHeaders(ref).stream()
                  .anyMatch(ownFormat::contains))
               {
                  continue;
               }

               numeric.add(displayName(ref));
            }
            else if(WizFormatChecks.isDateLike(ref)) {
               dateColumns.add(displayName(ref));
            }
         }

         advice = "Format the date columns alone with target:\"data\" and 'field' naming each" +
            (dateColumns.isEmpty() ? "" : " (" + summarize(dateColumns) + ")") + "." +
            (object ? " Columns that already have their own number format are exempt; if one " +
             "of these does, under a different header, format the date column with " +
             "target:\"data\", field:... instead." : "");
      }
      else if(assembly instanceof CalcTableVSAssembly calc && object) {
         ColumnSelection source = calcSourceColumns(viewsheet, calc);

         if(source == null) {
            return;
         }

         for(DataRef ref : calc.getBindingRefs()) {
            DataRef column = ref == null ? null : source.getAttribute(ref.getName());

            if(WizFormatChecks.isNumeric(column)) {
               numeric.add(ref.getName());
            }
         }

         advice = "Format the date cells alone with set_calc_cell_format.";
      }
      else {
         return;
      }

      if(numeric.isEmpty()) {
         return;
      }

      throw new IllegalArgumentException(
         "set_format: a " + formatType + " over the " + regionName(target) + " of '" + name +
         "' would render its numeric cells as dates (e.g. 1970-01-01): " + summarize(numeric) +
         ". The request was not applied. " + advice);
   }

   private static String regionName(String target) {
      return "data".equals(target) ? "body cells" :
         "header".equals(target) ? "header cells" :
         "title".equals(target) ? "title" : "whole assembly";
   }

   private static DataRef[] runtimeOrDesign(DataRef[] runtime, DataRef[] design) {
      return runtime != null && runtime.length > 0 ? runtime : design;
   }

   private static void addNumeric(List<String> out, DataRef[] refs) {
      if(refs == null) {
         return;
      }

      for(DataRef ref : refs) {
         if(WizFormatChecks.isNumeric(ref)) {
            out.add(displayName(ref));
         }
      }
   }

   private static String displayName(DataRef ref) {
      if(ref instanceof VSDataRef vsRef && vsRef.getFullName() != null) {
         return vsRef.getFullName();
      }

      if(ref instanceof ColumnRef column && column.getAlias() != null &&
         !column.getAlias().isEmpty())
      {
         return column.getAlias();
      }

      return ref.getAttribute() != null ? ref.getAttribute() : ref.getName();
   }

   /** The names a Table column's {@code DETAIL} path may be stored under (its rendered header). */
   private static Set<String> candidateHeaders(DataRef ref) {
      Set<String> names = new LinkedHashSet<>();

      if(ref instanceof ColumnRef column && column.getAlias() != null) {
         names.add(column.getAlias());
      }

      names.add(ref.getAttribute());
      names.add(ref.getName());
      names.remove(null);
      return names;
   }

   /**
    * The headers of a Table's {@code DETAIL} paths that already hold a user format value, read
    * with the non-mutating {@link FormatInfo#getFormat(TableDataPath)}. Such a column keeps its
    * own format and does not inherit the object format (VSFormatTableLens).
    */
   private static Set<String> ownDetailFormatHeaders(TableVSAssembly table) {
      VSAssemblyInfo info = table.getVSAssemblyInfo();
      FormatInfo formatInfo = info == null ? null : info.getFormatInfo();
      Set<String> headers = new LinkedHashSet<>();

      if(formatInfo == null) {
         return headers;
      }

      for(TableDataPath path : formatInfo.getPaths()) {
         if(path == null || path.getType() != TableDataPath.DETAIL || path.getPath() == null ||
            path.getPath().length == 0)
         {
            continue;
         }

         VSCompositeFormat format = formatInfo.getFormat(path);

         if(format != null && format.getUserDefinedFormat() != null &&
            format.getUserDefinedFormat().isFormatValueDefined())
         {
            headers.add(path.getPath()[0]);
         }
      }

      return headers;
   }

   /** The calc table's source table columns, from the base worksheet; null when unknown. */
   private static ColumnSelection calcSourceColumns(Viewsheet viewsheet,
                                                    CalcTableVSAssembly calc)
   {
      SourceInfo source = calc.getSourceInfo();
      Worksheet worksheet = viewsheet.getBaseWorksheet();

      if(source == null || source.getSource() == null || worksheet == null) {
         return null;
      }

      return worksheet.getAssembly(source.getSource()) instanceof TableAssembly table ?
         table.getColumnSelection(true) : null;
   }

   private static String summarize(List<String> names) {
      List<String> distinct = new ArrayList<>(new LinkedHashSet<>(names));

      if(distinct.size() <= 5) {
         return String.join(", ", distinct);
      }

      return String.join(", ", distinct.subList(0, 5)) + " and " + (distinct.size() - 5) +
         " more";
   }

   private static final Set<String> DATE_LIKE_FORMATS = Set.of(
      XConstants.DATE_FORMAT, XConstants.TIME_FORMAT, XConstants.TIMEINSTANT_FORMAT);

   /**
    * Computes the {@code TableDataPath[]} for a Crosstab/Table's data (body) region -- one
    * entry per distinct cell path the assembly's own live-rendered table actually reports,
    * mirroring what a human dragging over every data cell in the native Composer UI would send
    * (see {@code FormatPainterService}'s {@code paths != null} branch). Computed from the live
    * lens rather than the binding alone because a Crosstab's body paths encode structural
    * nesting (row/col dimension levels, subtotal/grand-total rows) that isn't derivable from the
    * binding without re-implementing {@link inetsoft.report.filter.CrossFilterDataDescriptor}'s
    * own path construction -- reusing {@link TableDataDescriptor#getCellDataPath} instead keeps
    * this correct by construction for both a Crosstab and a plain Table. Column/row HEADER
    * label cells (the header row/column text, distinct from the assembly's own title bar) are
    * excluded -- only the body region is "data".
    *
    * <p>When {@code field} is non-blank, the result is narrowed to just that one column's
    * paths. The caller's typed column name is resolved to a column INDEX first (scanning the
    * live lens's rendered header row, the same convention
    * {@code TableBindingService.setColumnWidths}'s {@code scanForColumnMatch} uses, refusing
    * loud on 0 or more than 1 match), and paths are then filtered by
    * {@link TableDataDescriptor#isColDataPath} against that resolved index -- never by
    * string-comparing the caller's typed value against {@code path.getPath()[0]} directly.
    * {@code isColDataPath} and the rendered-header scan key off two notions of "this column's
    * header" that are not guaranteed to agree (see {@code DefaultTableDataDescriptor.getHeader}'s
    * own comment) -- e.g. after a column renamed via {@code set_column_labels} or a script-set
    * header -- so resolving to an index once and filtering by that same index on both sides is
    * what keeps the two notions from ever needing to agree with each other.
    *
    * <p>On a Crosstab, {@code isColDataPath} is meaningless ({@code CrossFilterDataDescriptor}
    * always answers false) and one measure repeats across many rendered columns or rows, so
    * {@code field} instead names one aggregate: it is resolved to that aggregate's data header
    * ({@link #resolveCrosstabMeasureKey}) and only the {@code SUMMARY}/{@code GRAND_TOTAL}
    * paths ending in that header are kept -- the other measures' cells and the dimension
    * {@code GROUP_HEADER} cells are left alone.
    *
    * @throws IllegalArgumentException if {@code name} does not resolve to a Crosstab or Table,
    *                                  or if {@code field} matches zero or more than one rendered
    *                                  column (Table) or aggregate (Crosstab)
    */
   private static TableDataPath[] computeDataRegionPaths(RuntimeViewsheet rvs, String name,
                                                          String field)
      throws Exception
   {
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet == null ? null : viewsheet.getAssembly(name);

      if(!(assembly instanceof CrosstabVSAssembly) && !(assembly instanceof TableVSAssembly)) {
         throw new IllegalArgumentException(
            "set_format: target 'data' only applies to a Crosstab or Table assembly; '" + name +
            "' is " + (assembly == null ? "not found" :
                       assembly.getClass().getSimpleName()) + ".");
      }

      boolean hasField = field != null && !field.isBlank();
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return new TableDataPath[0];
      }

      VSTableLens lens = box.get().getVSTableLens(name, false);

      if(lens == null) {
         return new TableDataPath[0];
      }

      TableDataDescriptor desc = lens.getDescriptor();
      LinkedHashSet<TableDataPath> paths = new LinkedHashSet<>();
      int colCount = lens.getColCount();

      // Bounded defensively against a pathological lens that never stops reporting more rows --
      // the distinct path SET is small regardless of row count (a body path encodes structural
      // nesting, not the cell's actual value), so this cap is never load-bearing for a real
      // Crosstab/Table.
      // A Crosstab's TableDataDescriptor (CrossFilterDataDescriptor) reuses GROUP_HEADER/
      // SUMMARY/GRAND_TOTAL for BOTH a header-band label cell (row/col dimension label, a
      // subtotal's label, the grand-total row/col's "Total" label) and a genuine body-band
      // value cell -- type alone can't tell them apart (bug 76981). Only cell POSITION,
      // relative to the crosstab's own header row/col band (getHeaderRowCount()/
      // getHeaderColCount() -- the same boundary VSTableLens itself already uses to decide
      // header-vs-body at render time), draws that line correctly. A plain Table's descriptor
      // (DefaultTableDataDescriptor) never emits those reused types -- only HEADER, DETAIL and
      // TRAILER -- so the original type-only check is kept for it unchanged.
      boolean isCrosstab = assembly instanceof CrosstabVSAssembly;
      String measureKey = hasField && isCrosstab ?
         resolveCrosstabMeasureKey(lens, field, name) : null;
      Integer resolvedCol = hasField && !isCrosstab ? resolveColumnIndex(lens, field, name) : null;

      for(int row = 0; row < MAX_DATA_REGION_ROWS && lens.moreRows(row); row++) {
         for(int col = 0; col < colCount; col++) {
            TableDataPath path = desc.getCellDataPath(row, col);

            if(path == null || (resolvedCol != null && !desc.isColDataPath(resolvedCol, path))) {
               continue;
            }

            boolean isBody = isCrosstab ? isBodyCell(lens, row, col) :
               path.getType() != TableDataPath.HEADER;

            // Field-scoped on a Crosstab: within the body band, keep only this measure's value
            // cells (normal summary and sub/grand totals) -- see resolveCrosstabMeasureKey.
            if(isBody && (measureKey == null || isMeasureBodyType(path.getType()) &&
               measureKey.equals(lastPathElement(path))))
            {
               paths.add(path);
            }
         }
      }

      return paths.toArray(new TableDataPath[0]);
   }

   /**
    * Computes the {@code TableDataPath[]} for a Crosstab/Table's HEADER region -- the row/
    * column dimension label cells (and, on a Crosstab, the aggregate/measure header and any
    * subtotal-header cells) -- as opposed to {@link #computeDataRegionPaths}'s body region.
    * Mirrors that method's own live-lens walk, just inverting the type filter: collects every
    * path whose type is {@link TableDataPath#HEADER}, {@link TableDataPath#GROUP_HEADER}, or
    * {@link TableDataPath#SUMMARY_HEADER} instead of excluding {@code HEADER}. Bug 76917: the
    * render engine ({@code VSFormatTableLens}) and the shared format-write engine
    * ({@code FormatPainterService}) already support a header-cell-scoped format override that
    * beats an active table style (the same mechanism the native Composer UI's own per-cell
    * Format action uses) -- this method is what finally gives {@code target: "header"} a path
    * to hand it.
    *
    * <p>A {@code TableDataPath.HEADER}-typed path is also how
    * {@link inetsoft.report.filter.CrossFilterDataDescriptor} marks a Crosstab's synthetic
    * blank/invalid corner cells (see its own {@code getCellDataPath}) -- those get swept into
    * this result too under a plain type match. That's harmless (the cells are blank, so
    * formatting them has no visible rendering effect) and deliberately not filtered out
    * separately, since doing so would need distinguishing them from a genuine header cell by
    * more than type alone.
    *
    * <p>Same {@code field} scoping as {@link #computeDataRegionPaths}: a plain Table column by
    * resolved index; on a Crosstab, one aggregate -- keeping only the {@code HEADER}-typed
    * measure-header paths ending in that aggregate's data header. Scoping a Crosstab's
    * dimension header by name is not supported; {@code field} there always names an aggregate.
    *
    * @throws IllegalArgumentException if {@code name} does not resolve to a Crosstab or Table,
    *                                  or if {@code field} matches zero or more than one rendered
    *                                  column (Table) or aggregate (Crosstab)
    */
   private static TableDataPath[] computeHeaderRegionPaths(RuntimeViewsheet rvs, String name,
                                                            String field)
      throws Exception
   {
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet == null ? null : viewsheet.getAssembly(name);

      if(!(assembly instanceof CrosstabVSAssembly) && !(assembly instanceof TableVSAssembly)) {
         throw new IllegalArgumentException(
            "set_format: target 'header' only applies to a Crosstab or Table assembly; '" +
            name + "' is " + (assembly == null ? "not found" :
                       assembly.getClass().getSimpleName()) + ".");
      }

      boolean hasField = field != null && !field.isBlank();
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return new TableDataPath[0];
      }

      VSTableLens lens = box.get().getVSTableLens(name, false);

      if(lens == null) {
         return new TableDataPath[0];
      }

      TableDataDescriptor desc = lens.getDescriptor();
      LinkedHashSet<TableDataPath> paths = new LinkedHashSet<>();
      int colCount = lens.getColCount();

      // Mirror image of computeDataRegionPaths's own isCrosstab split -- see its comment. Kept
      // as the exact logical negation (isBodyCell for a Crosstab, isHeaderFamilyType for a
      // plain Table) so the two methods still exactly partition every rendered cell: body XOR
      // header, never both, never neither.
      boolean isCrosstab = assembly instanceof CrosstabVSAssembly;
      String measureKey = hasField && isCrosstab ?
         resolveCrosstabMeasureKey(lens, field, name) : null;
      Integer resolvedCol = hasField && !isCrosstab ? resolveColumnIndex(lens, field, name) : null;

      // Same defensive row cap as computeDataRegionPaths -- see its own comment.
      for(int row = 0; row < MAX_DATA_REGION_ROWS && lens.moreRows(row); row++) {
         for(int col = 0; col < colCount; col++) {
            TableDataPath path = desc.getCellDataPath(row, col);

            if(path == null || (resolvedCol != null && !desc.isColDataPath(resolvedCol, path))) {
               continue;
            }

            boolean isHeader = isCrosstab ? !isBodyCell(lens, row, col) :
               isHeaderFamilyType(path.getType());

            // Field-scoped on a Crosstab: within the header band, keep only this measure's own
            // HEADER-typed measure-header cells.
            if(isHeader && (measureKey == null || path.getType() == TableDataPath.HEADER &&
               measureKey.equals(lastPathElement(path))))
            {
               paths.add(path);
            }
         }
      }

      return paths.toArray(new TableDataPath[0]);
   }

   private static boolean isHeaderFamilyType(int type) {
      return type == TableDataPath.HEADER || type == TableDataPath.GROUP_HEADER ||
         type == TableDataPath.SUMMARY_HEADER;
   }

   /** A Crosstab measure's value cells -- a normal summary cell or a (sub/grand) total cell. */
   private static boolean isMeasureBodyType(int type) {
      return type == TableDataPath.SUMMARY || type == TableDataPath.GRAND_TOTAL;
   }

   private static String lastPathElement(TableDataPath path) {
      String[] parts = path.getPath();
      return parts == null || parts.length == 0 ? null : parts[parts.length - 1];
   }

   /**
    * Resolves a caller-typed aggregate name on a Crosstab to that aggregate's data header -- the
    * last path element {@code CrossFilterDataDescriptor.getCellDataPath} appends to every one of
    * that measure's {@code SUMMARY}/{@code GRAND_TOTAL} body paths and its {@code HEADER}-typed
    * measure-header paths (the same convention {@code WizVsService.findHighlightCell} relies on).
    *
    * <p>The measure set is read from the live lens's body-band paths only ({@link #isBodyCell}):
    * a header-band subtotal/grand-total label cell shares the {@code SUMMARY}/{@code GRAND_TOTAL}
    * type but ends in a dimension value, not a data header (bug 76981), so it must never be
    * offered or matched as a measure. Each measure-header cell's
    * rendered text is also recorded as a label for its key, since a calc header, duplicate-suffix
    * or relabel can make the rendered text differ from the path header (the same
    * rendered-vs-path divergence {@link #resolveColumnIndex} guards against for a plain Table).
    * {@code field} is matched exactly against either first; only when nothing matches exactly is
    * the base-column form tried ({@code "Total"} for {@code "Sum(Total)"}).
    *
    * @throws IllegalArgumentException if {@code field} matches no aggregate, or more than one
    */
   private static String resolveCrosstabMeasureKey(VSTableLens lens, String field,
                                                   String assemblyName)
   {
      TableDataDescriptor desc = lens.getDescriptor();
      int colCount = lens.getColCount();
      Map<String, Set<String>> labels = new LinkedHashMap<>();
      List<int[]> headerCells = new ArrayList<>();

      for(int row = 0; row < MAX_DATA_REGION_ROWS && lens.moreRows(row); row++) {
         for(int col = 0; col < colCount; col++) {
            TableDataPath path = desc.getCellDataPath(row, col);
            String key = path == null ? null : lastPathElement(path);

            if(key == null) {
               continue;
            }

            if(isMeasureBodyType(path.getType()) && isBodyCell(lens, row, col)) {
               labels.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(key);
            }
            else if(path.getType() == TableDataPath.HEADER) {
               headerCells.add(new int[]{ row, col });
            }
         }
      }

      for(int[] cell : headerCells) {
         Set<String> keyLabels = labels.get(lastPathElement(desc.getCellDataPath(cell[0], cell[1])));
         Object val = lens.getObject(cell[0], cell[1]);

         if(keyLabels != null && val != null) {
            keyLabels.add(val.toString());
         }
      }

      List<String> matches = new ArrayList<>();

      for(Map.Entry<String, Set<String>> entry : labels.entrySet()) {
         if(entry.getValue().contains(field)) {
            matches.add(entry.getKey());
         }
      }

      if(matches.isEmpty()) {
         for(Map.Entry<String, Set<String>> entry : labels.entrySet()) {
            for(String label : entry.getValue()) {
               int open = label.indexOf('(');
               int close = label.lastIndexOf(')');

               if(open > 0 && close > open && label.substring(open + 1, close).equals(field)) {
                  matches.add(entry.getKey());
                  break;
               }
            }
         }
      }

      if(matches.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + field + "' is not an aggregate on Crosstab '" + assemblyName + "' right now; " +
            "on a Crosstab 'field' names one measure. " + (labels.isEmpty() ?
            "No aggregates are currently rendered." :
            "Available: " + String.join(", ", labels.keySet()) + "."));
      }

      if(matches.size() > 1) {
         throw new IllegalArgumentException(
            "'" + field + "' matches " + matches.size() + " aggregates on Crosstab '" +
            assemblyName + "' (" + String.join(", ", matches) + ") -- pass the full measure " +
            "name to pick one.");
      }

      return matches.get(0);
   }

   /**
    * Whether {@code (row, col)} sits in a Crosstab's body (data) region rather than its header
    * row/col band, per the crosstab lens's own authoritative boundary -- the identical
    * {@code getHeaderRowCount()}/{@code getHeaderColCount()} split {@link VSTableLens} already
    * uses to decide header-vs-body at render time ({@code VSTableLens.getObject}), and the
    * identical idiom {@link inetsoft.report.filter.CrossCalcFilter} uses for the same purpose
    * on its own crosstab-flavored lens. See bug 76981: a Crosstab's {@code TableDataPath} type
    * alone (e.g. {@code GROUP_HEADER}, or a header-band {@code SUMMARY}/{@code GRAND_TOTAL}
    * label cell that shares its type with a genuine body-band value cell) can't reliably
    * distinguish header-band from body-band cells; position can.
    */
   private static boolean isBodyCell(VSTableLens lens, int row, int col) {
      return row >= lens.getHeaderRowCount() && col >= lens.getHeaderColCount();
   }

   /**
    * Resolves a caller-typed column name to its rendered column index, scanning
    * {@code lens}'s header row for a matching rendered cell value -- the same convention
    * {@code TableBindingService.setColumnWidths}'s {@code scanForColumnMatch} uses against the
    * live {@code VSTableLens}. Kept local to this class rather than shared, since the two
    * call sites resolve against different row ranges ({@code setColumnWidths} also scans
    * frozen header columns, which a plain Table's data-region format has no equivalent of).
    *
    * @throws IllegalArgumentException if {@code field} matches zero or more than one column
    */
   private static int resolveColumnIndex(VSTableLens lens, String field, String assemblyName) {
      int headerRows = lens.getHeaderRowCount();
      int colCount = lens.getColCount();
      List<Integer> matches = new ArrayList<>();

      for(int row = 0; row < headerRows; row++) {
         for(int col = 0; col < colCount; col++) {
            Object val = lens.getObject(row, col);

            if(Objects.equals(field, val == null ? null : val.toString()) &&
               !matches.contains(col))
            {
               matches.add(col);
            }
         }
      }

      if(matches.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + field + "' is not a visible column on '" + assemblyName + "' right now.");
      }

      if(matches.size() > 1) {
         throw new IllegalArgumentException(
            "'" + field + "' matches " + matches.size() + " rendered columns on '" +
            assemblyName + "' -- format cannot be scoped by name when it's ambiguous.");
      }

      return matches.get(0);
   }

   private static final int MAX_DATA_REGION_ROWS = 100_000;

   /**
    * {@code set_calc_cell_format}. Applies a format at one {@code CalcTable} cell's own
    * {@code TableDataPath} ({@link CalcTableService#cellFormatPath}) rather than the whole
    * object -- the same {@code event.getData()} per-path mechanism {@link #setFormat}'s
    * {@code target: "title"} already uses, just with a computed cell path instead of
    * {@link VSAssemblyInfo#TITLEPATH}. One call, one cell -- a caller wanting several cells
    * formatted makes several calls, matching {@code set_calc_cell_script}'s own granularity.
    */
   public void setCellFormat(String sessionToken, Principal user, CellFormatRequest request,
                             String linkUri) throws Exception
   {
      if(request.assembly() == null || request.assembly().isBlank()) {
         throw new IllegalArgumentException("set_calc_cell_format requires 'assembly'.");
      }

      if(request.row() == null || request.col() == null) {
         throw new IllegalArgumentException(
            "set_calc_cell_format requires 'row' and 'col' -- calc-table cells are " +
            "addressed by coordinate.");
      }

      if(request.format() == null && !request.reset()) {
         throw new IllegalArgumentException(
            "set_calc_cell_format requires 'format' unless 'reset' is true.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         TableDataPath cellPath =
            calcService.cellFormatPath(rvs, request.assembly(), request.row(), request.col());

         FormatVSObjectEvent event = new FormatVSObjectEvent();
         event.setFormat(request.format());
         event.setReset(request.reset());
         event.setObjects(new String[]{ request.assembly() });
         event.setCharts(new String[0]);

         ArrayList<TableDataPath[]> data = new ArrayList<>();
         data.add(new TableDataPath[]{ cellPath });
         event.setData(data);

         for(FormatVSObjectEvent part :
            seededEvents(rvs, request.format(), request.reset(), request.formatKeys(),
                         List.of(request.assembly()), data, event))
         {
            painter.setFormat(runtimeId, part, user, dispatcher, linkUri);
         }
      });
   }

   /**
    * {@code get_calc_cell_format}. The read side of {@link #setCellFormat}'s own
    * {@code TableDataPath} -- resolves the identical cell path and reads it back through
    * {@link FormatPainterService#getFormat}, the same mechanism the Composer's own format pane
    * uses to show a selection's current format. {@code null} means the cell has no format of its
    * own (it inherits from the table's whole-object format), the same "no override" convention
    * {@code get_calc_cell_script} uses for a cell with no script.
    */
   public Map<String, Object> getCellFormat(String sessionToken, Principal user, String assembly,
                                            int row, int col) throws Exception
   {
      if(assembly == null || assembly.isBlank()) {
         throw new IllegalArgumentException("get_calc_cell_format requires 'assembly'.");
      }

      return sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         TableDataPath cellPath = calcService.cellFormatPath(rvs, assembly, row, col);

         GetVSObjectFormatEvent event = new GetVSObjectFormatEvent();
         event.setName(assembly);
         event.setDataPath(cellPath);
         painter.getFormat(runtimeId, event, user, dispatcher);

         VSObjectFormatInfoModel model = null;

         for(CapturingCommandDispatcher.Command command : dispatcher.getCapturedCommands()) {
            if(command.getCommand() instanceof SetCurrentFormatCommand current) {
               model = current.getModel();
               break;
            }
         }

         Map<String, Object> out = new LinkedHashMap<>();
         out.put("assembly", assembly);
         out.put("row", row);
         out.put("col", col);
         out.put("format", model == null ? null : toWireFormat(model));
         return out;
      });
   }

   /**
    * The inverse of {@link #parseFormat}: a plain, JSON-safe map of the same CSS-shaped fields
    * {@code set_format}/{@code set_calc_cell_format} accept, so a caller can feed a read-back
    * value straight into another format call. Returning {@code model} itself would leak its
    * {@code @JsonTypeInfo} Java class name into the response -- precisely the leak
    * {@link #parseFormat} exists to avoid on the way in.
    */
   private static Map<String, Object> toWireFormat(VSObjectFormatInfoModel model) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("color", model.getColor());
      out.put("backgroundColor", model.getBackgroundColor());
      out.put("font", model.getFont());
      out.put("align", model.getAlign());
      out.put("format", model.getFormat());
      out.put("formatSpec", model.getFormatSpec());
      out.put("borderTopStyle", model.getBorderTopStyle());
      out.put("borderTopColor", model.getBorderTopColor());
      out.put("borderLeftStyle", model.getBorderLeftStyle());
      out.put("borderLeftColor", model.getBorderLeftColor());
      out.put("borderBottomStyle", model.getBorderBottomStyle());
      out.put("borderBottomColor", model.getBorderBottomColor());
      out.put("borderRightStyle", model.getBorderRightStyle());
      out.put("borderRightColor", model.getBorderRightColor());
      out.put("roundCorner", model.getRoundCorner());
      out.put("wrapText", model.isWrapText());
      return out;
   }

   /** Bug 76325 item 3: distinguishes a chart's own title from its whole-object format. */
   private static String requireTarget(String target) {
      String name = target == null || target.isBlank() ? "object" : target.trim().toLowerCase();

      if(!"object".equals(name) && !"title".equals(name) && !"text".equals(name) &&
         !"data".equals(name) && !"header".equals(name) && !"field".equals(name))
      {
         throw new IllegalArgumentException(
            "set_format 'target' must be 'object', 'title', 'text', 'data', 'header' or " +
            "'field', got '" + target + "'. 'object' (the default) formats the whole " +
            "assembly, including — for a chart — the font and colour that unstyled axis " +
            "titles and tick labels fall back to (not their number/date format: use 'field' " +
            "for that). 'field' sets one chart field's value format wherever it renders (axis " +
            "labels, legend, data labels) — requires 'field'. " +
            "'title' formats only that assembly's own title-bar text; for a chart's " +
            "x/y axis titles, use set_chart_region_properties with region 'title' instead. " +
            "'text' formats a single chart's text-aesthetic-bound field (its data labels) — " +
            "requires 'field'. 'data' formats a Crosstab or Table's body cells directly — use " +
            "this for 'color' on a Crosstab/Table: 'object' can never reach rendered body text " +
            "once a table style applies, which is true for essentially every realistic " +
            "Crosstab/Table. 'header' formats a Crosstab or Table's header (row/column " +
            "dimension label) cells directly, for the same reason — 'object' can never reach " +
            "them once a table style colors them explicitly either.");
      }

      return name;
   }

   private final ViewsheetSessionService sessions;
   private final FormatPainterService painter;
   private final CalcTableService calcService;
   private final VSWizardBindingHandler bindingHandler;
}
