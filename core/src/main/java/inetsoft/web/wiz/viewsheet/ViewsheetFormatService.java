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
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

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
   public ViewsheetFormatService(ViewsheetSessionService sessions, FormatPainterService painter) {
      this.sessions = sessions;
      this.painter = painter;
   }

   /**
    * @param assemblies the assemblies to format; at least one
    * @param format     the format to apply; may be null only when {@code reset} is true
    * @param reset      clear formatting back to the default rather than applying {@code format}
    */
   public record FormatRequest(List<String> assemblies,
                               VSObjectFormatInfoModel format,
                               boolean reset)
   {
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
                                           @JsonProperty("reset") boolean reset)
      {
         return new FormatRequest(assemblies, toModel(format), reset);
      }

      private static VSObjectFormatInfoModel toModel(JsonNode format) {
         if(format == null || format.isNull()) {
            return null;
         }

         ObjectNode object = ((ObjectNode) format).deepCopy();
         object.put("type", VSObjectFormatInfoModel.class.getName());
         coerceAlign(object);
         coerceBorderStyles(object);

         try {
            return MAPPER.treeToValue(object, VSObjectFormatInfoModel.class);
         }
         catch(JsonProcessingException e) {
            throw new IllegalArgumentException(
               "set_format could not read 'format': " + e.getOriginalMessage(), e);
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
      private static void coerceAlign(ObjectNode object) {
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
               "set_format could not read 'align': '" + word + "' is not an alignment. " +
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
      private static void coerceBorderStyles(ObjectNode object) {
         for(String side : BORDER_STYLES) {
            JsonNode style = object.get(side);

            if(style == null || !style.isTextual()) {
               continue;
            }

            String word = style.asText().trim().toLowerCase();

            if(word.chars().allMatch(Character::isDigit)) {
               continue;
            }

            object.put(side, String.valueOf(switch(word) {
               case "none" -> StyleConstants.NO_BORDER;
               case "solid", "thin" -> StyleConstants.THIN_LINE;
               case "medium" -> StyleConstants.MEDIUM_LINE;
               case "thick" -> StyleConstants.THICK_LINE;
               case "dashed" -> StyleConstants.DASH_LINE;
               case "dotted" -> StyleConstants.DOT_LINE;
               case "double" -> StyleConstants.DOUBLE_LINE;
               default -> throw new IllegalArgumentException(
                  "set_format could not read '" + side + "': '" + word + "' is not a border " +
                  "style. Accepted: none, solid, dashed, dotted, double, thin, medium, thick. " +
                  "A StyleBI line constant is accepted as a number.");
            }));
         }
      }

      private static final List<String> BORDER_STYLES =
         List.of("borderTopStyle", "borderLeftStyle", "borderBottomStyle", "borderRightStyle");

      private static final ObjectMapper MAPPER = new ObjectMapper();
   }

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

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         FormatVSObjectEvent event = new FormatVSObjectEvent();
         event.setObjects(request.assemblies().toArray(new String[0]));
         event.setFormat(request.format());
         event.setReset(request.reset());
         // FormatPainterService iterates `event.getCharts().length` unguarded, so a null here is an
         // immediate NPE — every set_format call, format or reset alike, failed with a 500. An
         // empty array is the correct value for assembly-level formatting: the chart-region
         // branches that read getColumnNames()/getIndexes()/getRegions() all live inside that
         // loop, so they never execute for these requests.
         event.setCharts(new String[0]);
         painter.setFormat(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   private final ViewsheetSessionService sessions;
   private final FormatPainterService painter;
}
