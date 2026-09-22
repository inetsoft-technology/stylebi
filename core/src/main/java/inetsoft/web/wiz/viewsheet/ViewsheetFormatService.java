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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.adhoc.model.chart.ChartFormatConstants;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.command.SetCurrentFormatCommand;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.GetVSObjectFormatEvent;
import inetsoft.web.wiz.binding.CalcTableService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
                                 CalcTableService calcService)
   {
      this.sessions = sessions;
      this.painter = painter;
      this.calcService = calcService;
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
    *                   body/header; null/blank keeps today's whole-table behavior. Not
    *                   supported against a Crosstab under {@code "data"} or {@code "header"} --
    *                   refused rather than silently ignored. Unused for any other target.
    */
   public record FormatRequest(List<String> assemblies,
                               VSObjectFormatInfoModel format,
                               boolean reset,
                               String target,
                               String field)
   {
      /** Kept for existing callers that predate {@code target}/{@code field} — defaults both. */
      public FormatRequest(List<String> assemblies, VSObjectFormatInfoModel format, boolean reset) {
         this(assemblies, format, reset, null, null);
      }

      /** Kept for existing callers that predate {@code field} — defaults it to null. */
      public FormatRequest(List<String> assemblies, VSObjectFormatInfoModel format, boolean reset,
                           String target)
      {
         this(assemblies, format, reset, target, null);
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
                                  field);
      }
   }

   /**
    * @param assembly the calc table's name
    * @param row      0-based design-grid row
    * @param col      0-based design-grid column
    * @param format   the format to apply; may be null only when {@code reset} is true
    * @param reset    clear this cell's own format back to inherited, rather than applying
    *                 {@code format}
    */
   public record CellFormatRequest(String assembly, Integer row, Integer col,
                                   VSObjectFormatInfoModel format, boolean reset)
   {
      /** @see FormatRequest#fromJson -- same reason: bypasses Jackson's polymorphic resolver. */
      @JsonCreator
      public static CellFormatRequest fromJson(@JsonProperty("assembly") String assembly,
                                               @JsonProperty("row") Integer row,
                                               @JsonProperty("col") Integer col,
                                               @JsonProperty("format") JsonNode format,
                                               @JsonProperty("reset") boolean reset)
      {
         return new CellFormatRequest(assembly, row, col,
                                      parseFormat(format, "set_calc_cell_format"), reset);
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

      try {
         return MAPPER.treeToValue(object, VSObjectFormatInfoModel.class);
      }
      catch(JsonProcessingException e) {
         throw new IllegalArgumentException(
            toolName + " could not read 'format': " + e.getOriginalMessage(), e);
      }
   }

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

   public void setFormat(String sessionToken, Principal user, FormatRequest request,
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

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
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
                  data.add(computeDataRegionPaths(rvs, name, request.field()));
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
                  data.add(computeHeaderRegionPaths(rvs, name, request.field()));
               }

               event.setData(data);
            }
         }

         painter.setFormat(runtimeId, event, user, dispatcher, linkUri);
      });
   }

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
    * <p>Column-scoping via {@code field} is only supported for a plain Table -- a Crosstab's
    * body region is refused loud rather than silently ignoring {@code field} or attempting a
    * filter that doesn't map onto its structurally nested body.
    *
    * @throws IllegalArgumentException if {@code name} does not resolve to a Crosstab or Table,
    *                                  if {@code field} is given against a Crosstab, or if
    *                                  {@code field} matches zero or more than one rendered
    *                                  column
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

      if(hasField && assembly instanceof CrosstabVSAssembly) {
         throw new IllegalArgumentException(
            "set_format: 'field' is only supported for a plain Table body under target " +
            "'data', not a Crosstab ('" + name + "'); omit 'field' to format the whole body.");
      }

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
      Integer resolvedCol = hasField ? resolveColumnIndex(lens, field, name) : null;

      // Bounded defensively against a pathological lens that never stops reporting more rows --
      // the distinct path SET is small regardless of row count (a body path encodes structural
      // nesting, not the cell's actual value), so this cap is never load-bearing for a real
      // Crosstab/Table.
      for(int row = 0; row < MAX_DATA_REGION_ROWS && lens.moreRows(row); row++) {
         for(int col = 0; col < colCount; col++) {
            TableDataPath path = desc.getCellDataPath(row, col);

            if(path != null && path.getType() != TableDataPath.HEADER &&
               (resolvedCol == null || desc.isColDataPath(resolvedCol, path)))
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
    * <p>Same {@code field} column-scoping support and restriction as
    * {@link #computeDataRegionPaths}: only for a plain Table, refused loud against a Crosstab.
    *
    * @throws IllegalArgumentException if {@code name} does not resolve to a Crosstab or Table,
    *                                  if {@code field} is given against a Crosstab, or if
    *                                  {@code field} matches zero or more than one rendered
    *                                  column
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

      if(hasField && assembly instanceof CrosstabVSAssembly) {
         throw new IllegalArgumentException(
            "set_format: 'field' is only supported for a plain Table header under target " +
            "'header', not a Crosstab ('" + name + "'); omit 'field' to format the whole " +
            "header.");
      }

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
      Integer resolvedCol = hasField ? resolveColumnIndex(lens, field, name) : null;

      // Same defensive row cap as computeDataRegionPaths -- see its own comment.
      for(int row = 0; row < MAX_DATA_REGION_ROWS && lens.moreRows(row); row++) {
         for(int col = 0; col < colCount; col++) {
            TableDataPath path = desc.getCellDataPath(row, col);

            if(path != null && isHeaderFamilyType(path.getType()) &&
               (resolvedCol == null || desc.isColDataPath(resolvedCol, path)))
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

         painter.setFormat(runtimeId, event, user, dispatcher, linkUri);
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
         !"data".equals(name) && !"header".equals(name))
      {
         throw new IllegalArgumentException(
            "set_format 'target' must be 'object', 'title', 'text', 'data' or 'header', got '" +
            target + "'. 'object' (the default) formats the whole assembly, including — for a " +
            "chart — the default text style that unstyled axis titles and tick labels fall " +
            "back to. 'title' formats only that assembly's own title-bar text; for a chart's " +
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
}
