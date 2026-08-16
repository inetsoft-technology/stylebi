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

import inetsoft.report.Hyperlink;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.vs.dialog.HyperlinkDialogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * An assembly's hyperlink.
 *
 * <p>Unlike the property engine, this is <b>region-addressed</b>: a hyperlink hangs off a cell,
 * an axis, a title, or the empty plot area rather than off the assembly as a whole. So it takes
 * a region selector instead of a dotted path, and gets its own tools.
 *
 * <p>{@code linkType} is an integer constant on the wire — 1 web, 8 viewsheet, 16 message — and
 * appears here as a token. Which value field matters depends on the type, and a link whose type
 * and value disagree is accepted by the dialog and then does nothing when clicked, so this
 * refuses the combination instead.
 *
 * <p><b>Highlights are deliberately not here.</b> {@code HighlightDialogModel} carries
 * {@code HighlightModel[]}, each with a condition list, and conditions are spec #4's
 * vocabulary. A second condition vocabulary in this class would put those semantics in two
 * places, which is the drift the property design exists to prevent — so highlights wait for #4.
 */
@Service
public class AssemblyHyperlinkService {
   /** Agent-facing link types. The integers never appear in either direction. */
   private static final Map<String, Integer> LINK_TYPES = Map.of(
      "none", 0,
      "web", Hyperlink.WEB_LINK,
      "viewsheet", Hyperlink.VIEWSHEET_LINK,
      "message", Hyperlink.MESSAGE_LINK);

   @Autowired
   public AssemblyHyperlinkService(ViewsheetSessionService sessions,
                                   HyperlinkDialogService hyperlinkService)
   {
      this.sessions = sessions;
      this.hyperlinkService = hyperlinkService;
   }

   /**
    * A region within an assembly. All-defaults addresses the assembly itself, which is what a
    * caller naming only the assembly means.
    */
   public record Region(Integer row, Integer col, String colName, boolean axis, boolean text,
                        boolean titleLink, boolean emptyPlotLink) {
      /**
       * Normalizes a null row/col to <b>0</b> — on every construction path.
       *
       * <p>{@code HyperlinkDialogService.getHyperlinkDialogModel} dereferences row as an int
       * (through {@code getFields}), so nulls threw
       * {@code NullPointerException: … because "row" is null} and made set_hyperlink unusable at
       * assembly level for every assembly type. The Composer never sends null — its controller
       * declares {@code @RequestParam(value = "row", required = false, defaultValue = "0")} — so
       * calling the service directly means supplying that default ourselves.
       *
       * <p>This lives in the compact constructor rather than in {@link #whole()} because the
       * agent controller builds a Region straight from its nullable {@code @RequestParam}s and
       * never calls the factory — normalizing only there fixed nothing on the live path.
       */
      public Region {
         row = row == null ? 0 : row;
         col = col == null ? 0 : col;
      }

      public static Region whole() {
         return new Region(0, 0, null, false, false, false, false);
      }
   }

   public Map<String, Object> read(String sessionToken, Principal user, String assemblyName,
                                   Region region) throws Exception
   {
      Region target = region == null ? Region.whole() : region;
      HyperlinkDialogModel model = hyperlinkService.getHyperlinkDialogModel(
         sessions.resolve(sessionToken, user).getID(), assemblyName, target.row(), target.col(),
         target.colName(), target.axis(), target.text(), target.titleLink(),
         target.emptyPlotLink(), user);

      return describe(assemblyName, model);
   }

   /** One {@code sessions.mutate}, so one undo checkpoint. */
   public void set(String sessionToken, Principal user, String assemblyName, Region region,
                   Map<String, Object> link, String linkUri) throws Exception
   {
      // Validated before the runtime is touched: a link whose type and value disagree costs
      // nothing to refuse here, and opens no checkpoint the caller then has to undo.
      String type = requireType(link);
      requireValueForType(type, link);

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         Region target = region == null ? Region.whole() : region;
         HyperlinkDialogModel model = hyperlinkService.getHyperlinkDialogModel(
            runtimeId, assemblyName, target.row(), target.col(), target.colName(),
            target.axis(), target.text(), target.titleLink(), target.emptyPlotLink(), user);

         apply(model, type, link);
         hyperlinkService.setHyperlinkDialogModel(runtimeId, assemblyName, model, linkUri, user,
                                                 dispatcher);
      });
   }

   public Map<String, Object> linkTypes() {
      List<String> names = new ArrayList<>(LINK_TYPES.keySet());
      Collections.sort(names);
      return Map.of("linkTypes", names);
   }

   // ── vocabulary ────────────────────────────────────────────────────────────

   private static String requireType(Map<String, Object> link) {
      Object raw = link == null ? null : link.get("linkType");
      String type = raw == null ? "" : String.valueOf(raw).trim().toLowerCase();

      if(!LINK_TYPES.containsKey(type)) {
         throw new IllegalArgumentException(
            "'linkType' must be one of " + new TreeSet<>(LINK_TYPES.keySet()) + ", got '" +
            raw + "'. Integer constants are not accepted — the words are the vocabulary.");
      }

      return type;
   }

   /**
    * Which field carries the destination depends on the type. A link with a type but no
    * matching value is accepted by the dialog and then does nothing when clicked, which reads
    * as a broken report rather than a bad call.
    */
   private static void requireValueForType(String type, Map<String, Object> link) {
      switch(type) {
         case "web" -> require(link, "webLink", type);
         case "viewsheet" -> require(link, "assetLinkPath", type);
         case "message" -> require(link, "webLink", type);
         default -> { /* none clears the link, so it needs no destination */ }
      }
   }

   private static void require(Map<String, Object> link, String field, String type) {
      if(str(link, field) == null) {
         throw new IllegalArgumentException(
            "A '" + type + "' hyperlink needs '" + field + "'. Without it the link is stored " +
            "and then does nothing when clicked, which reads as a broken report rather than a " +
            "bad call.");
      }
   }

   private static void apply(HyperlinkDialogModel model, String type,
                             Map<String, Object> link)
   {
      model.setLinkType(LINK_TYPES.get(type));

      if("none".equals(type)) {
         model.setWebLink(null);
         model.setAssetLinkPath(null);
         model.setAssetLinkId(null);
         return;
      }

      if(link.containsKey("webLink")) {
         model.setWebLink(str(link, "webLink"));
      }

      if(link.containsKey("assetLinkPath")) {
         model.setAssetLinkPath(str(link, "assetLinkPath"));
      }

      if(link.containsKey("bookmark")) {
         model.setBookmark(str(link, "bookmark"));
      }

      if(link.containsKey("targetFrame")) {
         model.setTargetFrame(str(link, "targetFrame"));
      }

      if(link.containsKey("tooltip")) {
         model.setTooltip(str(link, "tooltip"));
      }

      if(link.get("self") instanceof Boolean self) {
         model.setSelf(self);
      }

      if(link.get("sendViewsheetParameters") instanceof Boolean send) {
         model.setSendViewsheetParameters(send);
      }

      if(link.get("sendSelectionsAsParameters") instanceof Boolean send) {
         model.setSendSelectionsAsParameters(send);
      }

      if(link.get("disableParameterPrompt") instanceof Boolean disable) {
         model.setDisableParameterPrompt(disable);
      }
   }

   private static Map<String, Object> describe(String assemblyName, HyperlinkDialogModel model) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);

      if(model == null) {
         out.put("linkType", "none");
         return out;
      }

      out.put("linkType", tokenOf(model.getLinkType()));
      out.put("webLink", model.getWebLink());
      out.put("assetLinkPath", model.getAssetLinkPath());
      out.put("bookmark", model.getBookmark());
      out.put("targetFrame", model.getTargetFrame());
      out.put("tooltip", model.getTooltip());
      out.put("self", model.isSelf());
      out.put("sendViewsheetParameters", model.isSendViewsheetParameters());
      out.put("sendSelectionsAsParameters", model.isSendSelectionsAsParameters());
      out.put("row", model.getRow());
      out.put("col", model.getCol());
      out.put("colName", model.getColName());
      return out;
   }

   /** An unrecognized constant reads back as itself rather than as a guessed token. */
   private static String tokenOf(int value) {
      for(Map.Entry<String, Integer> entry : LINK_TYPES.entrySet()) {
         if(entry.getValue() == value) {
            return entry.getKey();
         }
      }

      return "unknown(" + value + ")";
   }

   private static String str(Map<String, Object> link, String key) {
      Object value = link == null ? null : link.get(key);
      String text = value == null ? "" : String.valueOf(value).trim();
      return text.isEmpty() ? null : text;
   }

   private final ViewsheetSessionService sessions;
   private final HyperlinkDialogService hyperlinkService;
}
