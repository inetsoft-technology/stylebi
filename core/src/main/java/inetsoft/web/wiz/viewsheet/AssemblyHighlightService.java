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

import inetsoft.web.wiz.binding.VisualFrameAliases;
import inetsoft.web.composer.model.vs.HighlightDialogModel;
import inetsoft.web.composer.model.vs.HighlightModel;
import inetsoft.web.composer.model.vs.VSConditionDialogModel;
import inetsoft.web.composer.vs.dialog.HighlightDialogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Highlights — conditional formatting on an assembly.
 *
 * <p>Each {@code HighlightModel} embeds a full {@code VSConditionDialogModel}, so highlights
 * inherit the alternating-array hazard wholesale. They therefore use {@link ConditionVocabulary}
 * unchanged: one implementation, two callers. A caller who has written a condition for
 * {@code set_condition} already knows how to write one here.
 *
 * <p><b>{@code usedHighlightNames} exists to stop name collisions, and this honours it.</b> A
 * duplicate name silently replaces an existing highlight, so adding one under a name already in
 * use is refused; updating requires saying so, which makes the destructive case explicit rather
 * than accidental.
 *
 * <p>Region-addressed like hyperlinks: a highlight applies to a cell, an axis or a text element,
 * not to the assembly as a whole.
 */
@Service
public class AssemblyHighlightService {
   @Autowired
   public AssemblyHighlightService(ViewsheetSessionService sessions,
                                   HighlightDialogService highlightService)
   {
      this.sessions = sessions;
      this.highlightService = highlightService;
   }

   /** Where a highlight lives. All-defaults means the assembly itself. */
   public record Region(Integer row, Integer col, String colName, boolean axis, boolean text) {
      /**
       * Normalizes a null row/col to <b>0</b> — on every construction path.
       *
       * <p>For a table-type assembly {@code HighlightDialogService.getHighlightDialogModel} calls
       * {@code lens.getTableDataPath(row, col)}, which NPEs on null, so highlights were unusable
       * on every table, crosstab and calc table (a chart never reaches that branch). Zero is what
       * the rest of that same method already assumes: a few lines later it reads
       * {@code row == null ? 0 : row}.
       *
       * <p>This lives in the compact constructor rather than in {@link #whole()} because the
       * controller builds a Region straight from its nullable {@code @RequestParam}s and never
       * calls the factory — normalizing only there fixed nothing on the live path.
       */
      public Region {
         row = row == null ? 0 : row;
         col = col == null ? 0 : col;
      }

      public static Region whole() {
         return new Region(0, 0, null, false, false);
      }

      /**
       * The first data cell of a table-type assembly.
       *
       * <p>Cell (0,0) is the header, which exposes no highlightable fields. Used only as a
       * fall-forward when the caller named no region and the header exposed nothing — see
       * {@code read}.
       */
      public static Region firstDataCell() {
         return new Region(1, 1, null, false, false);
      }
   }

   /** One highlight in the agent vocabulary. Colours are {@code #RRGGBB}. */
   public record Highlight(String name, String foreground, String background,
                           List<ConditionVocabulary.Clause> conditions, boolean applyRow) {}

   public Map<String, Object> list(String sessionToken, Principal user, String assemblyName,
                                   Region region) throws Exception
   {
      HighlightDialogModel model = read(sessions.resolve(sessionToken, user).getID(),
                                       assemblyName, region, user);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);

      if(model == null) {
         out.put("highlights", List.of());
         return out;
      }

      List<Map<String, Object>> highlights = new ArrayList<>();

      for(HighlightModel highlight : model.getHighlights() == null
         ? new HighlightModel[0] : model.getHighlights())
      {
         highlights.add(describe(highlight));
      }

      out.put("highlights", highlights);
      out.put("usedNames", usedNames(model));
      out.put("fields", fieldNames(model));
      out.put("appliesToRow", model.isShowRow());
      return out;
   }

   /**
    * Adds a highlight, or updates one by name when {@code replace} is set.
    *
    * <p>One {@code sessions.mutate}, so one undo checkpoint.
    */
   public void set(String sessionToken, Principal user, String assemblyName, Region region,
                   Highlight highlight, boolean replace, String linkUri) throws Exception
   {
      requireName(highlight);

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         HighlightDialogModel model = read(runtimeId, assemblyName, region, user);

         if(model == null) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' has no highlight dialog — it is not an assembly that " +
               "supports conditional formatting.");
         }

         // Before the shared condition vocabulary gets a chance to blame the field name.
         requireHighlightableRegion(model, assemblyName, region);

         List<HighlightModel> highlights = new ArrayList<>();
         boolean found = false;

         for(HighlightModel existing : model.getHighlights() == null
            ? new HighlightModel[0] : model.getHighlights())
         {
            if(existing != null && highlight.name().equalsIgnoreCase(existing.getName())) {
               found = true;

               // Silently replacing is the recorded hazard usedHighlightNames exists to
               // prevent, so overwriting has to be asked for.
               if(!replace) {
                  throw new IllegalArgumentException(
                     "A highlight named '" + highlight.name() + "' already exists on '" +
                     assemblyName + "'. Adding another under the same name would silently " +
                     "replace it. Pass replace:true to update it deliberately, or choose a " +
                     "different name.");
               }

               highlights.add(build(highlight, model));
            }
            else {
               highlights.add(existing);
            }
         }

         if(!found) {
            highlights.add(build(highlight, model));
         }

         model.setHighlights(highlights.toArray(new HighlightModel[0]));
         highlightService.setHighlightDialogModel(runtimeId, assemblyName, model, linkUri, user,
                                                 dispatcher);
      });
   }

   /** Removes one highlight by name. Removing one that is not there is an error, not a no-op. */
   public void delete(String sessionToken, Principal user, String assemblyName, Region region,
                      String name, String linkUri) throws Exception
   {
      if(name == null || name.isBlank()) {
         throw new IllegalArgumentException("delete_highlight needs the highlight's 'name'.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         HighlightDialogModel model = read(runtimeId, assemblyName, region, user);
         HighlightModel[] existing = model == null || model.getHighlights() == null
            ? new HighlightModel[0] : model.getHighlights();
         List<HighlightModel> kept = new ArrayList<>();
         List<String> names = new ArrayList<>();

         for(HighlightModel highlight : existing) {
            if(highlight == null) {
               continue;
            }

            names.add(highlight.getName());

            if(!name.equalsIgnoreCase(highlight.getName())) {
               kept.add(highlight);
            }
         }

         if(kept.size() == existing.length) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' has no highlight named '" + name + "'. It has: " +
               (names.isEmpty() ? "(none)" : String.join(", ", names)) + ".");
         }

         model.setHighlights(kept.toArray(new HighlightModel[0]));
         highlightService.setHighlightDialogModel(runtimeId, assemblyName, model, linkUri, user,
                                                 dispatcher);
      });
   }

   // ── conversions ───────────────────────────────────────────────────────────

   private static void requireName(Highlight highlight) {
      if(highlight == null || highlight.name() == null || highlight.name().isBlank()) {
         throw new IllegalArgumentException(
            "A highlight needs a 'name' — it is how the highlight is addressed for updates and " +
            "deletion.");
      }

      if(highlight.foreground() == null && highlight.background() == null) {
         throw new IllegalArgumentException(
            "Highlight '" + highlight.name() + "' sets no colour. A highlight with no " +
            "foreground and no background is stored and renders nothing, which reads as a " +
            "condition that never matched.");
      }
   }

   private static HighlightModel build(Highlight highlight, HighlightDialogModel model) {
      HighlightModel out = new HighlightModel();
      out.setName(highlight.name());

      if(highlight.foreground() != null) {
         out.setForeground(VisualFrameAliases.normalizeColor(highlight.foreground()));
      }

      if(highlight.background() != null) {
         out.setBackground(VisualFrameAliases.normalizeColor(highlight.background()));
      }

      out.setApplyRow(highlight.applyRow());

      // The embedded condition model reuses spec #4's vocabulary rather than a parallel one,
      // and is validated against the highlight dialog's own fields.
      VSConditionDialogModel condition = new VSConditionDialogModel();
      condition.setTableName(model.getTableName());
      condition.setFields(model.getFields());
      condition.setConditionList(
         ConditionVocabulary.toConditionList(highlight.conditions(), model.getFields()));
      out.setVsConditionDialogModel(condition);
      return out;
   }

   private static Map<String, Object> describe(HighlightModel highlight) {
      Map<String, Object> out = new LinkedHashMap<>();

      if(highlight == null) {
         return out;
      }

      out.put("name", highlight.getName());
      out.put("foreground", highlight.getForeground());
      out.put("background", highlight.getBackground());
      out.put("applyRow", highlight.isApplyRow());
      out.put("conditions", highlight.getVsConditionDialogModel() == null
         ? List.of()
         : ConditionVocabulary.describe(
            highlight.getVsConditionDialogModel().getConditionList()));
      return out;
   }

   private HighlightDialogModel read(String runtimeId, String assemblyName, Region region,
                                     Principal user) throws Exception
   {
      Region target = region == null ? Region.whole() : region;
      HighlightDialogModel model = readAt(runtimeId, assemblyName, target, user);

      // A crosstab's cell (0,0) is a HEADER, and a header has no highlightable fields — so
      // addressing the whole assembly returned `fields: []` on an assembly whose data cells have
      // two, and every condition was then refused for naming a field "this assembly cannot filter
      // on". The field was fine; the region was wrong, and nothing said so.
      //
      // When the caller named no region at all, their intent is the assembly's data, so fall
      // forward to the first data cell rather than reporting an empty vocabulary. An explicit
      // region is never second-guessed: the caller meant that cell, and silently moving it would
      // highlight somewhere they did not ask for.
      // A null model means the assembly has no highlight dialog at all; that is the caller's
      // answer, and re-reading a different cell of it would only produce the same null.
      if(region == null && model != null && fieldNames(model).isEmpty()) {
         HighlightDialogModel atData =
            readAt(runtimeId, assemblyName, Region.firstDataCell(), user);

         if(!fieldNames(atData).isEmpty()) {
            return atData;
         }
      }

      return model;
   }

   private HighlightDialogModel readAt(String runtimeId, String assemblyName, Region target,
                                       Principal user) throws Exception
   {
      return highlightService.getHighlightDialogModel(runtimeId, assemblyName, target.row(),
                                                      target.col(), target.colName(),
                                                      target.axis(), target.text(), user);
   }

   /**
    * Refuses a highlight whose region exposes no fields, naming the region rather than the field.
    *
    * <p>Without this the failure surfaced from the shared condition vocabulary as "names 'X', which
    * this assembly cannot filter on. Available fields: (none)" — which reads as a bad field name on
    * an assembly that cannot be filtered, when in fact the assembly filters fine and the region was
    * the header row.
    */
   private static void requireHighlightableRegion(HighlightDialogModel model, String assemblyName,
                                                  Region region)
   {
      if(!fieldNames(model).isEmpty()) {
         return;
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' exposes no highlightable fields at " +
         (region == null ? "the default region" :
            "row " + region.row() + ", col " + region.col()) +
         ". For a crosstab or table this usually means a header cell: highlights attach to DATA " +
         "cells, so pass 'row' and 'col' of one (row 1, col 1 is the first). Call list_highlights " +
         "with that region to see the fields it exposes.");
   }

   /**
    * The highlight names already taken on this assembly.
    *
    * <p>Derived from the highlights themselves, not from {@code getUsedHighlightNames()} alone.
    * That array is populated by the dialog for its own editing flow and comes back empty on a plain
    * read, so an assembly carrying a highlight reported {@code usedNames: []} — while
    * {@link #set} refused that very name, because its duplicate check walks {@code getHighlights()}.
    * A caller told to "call list_highlights to see what is taken" was therefore told the wrong
    * thing, then refused. Both answers now come from the same place.
    *
    * <p>The dialog's own array is still unioned in: it is authoritative for names reserved outside
    * the highlights currently on this region, and dropping it would trade one gap for another.
    */
   private static List<String> usedNames(HighlightDialogModel model) {
      // Case-insensitively deduplicated, because set()'s refusal compares that way.
      Map<String, String> byLower = new LinkedHashMap<>();

      if(model.getHighlights() != null) {
         for(HighlightModel highlight : model.getHighlights()) {
            if(highlight != null && highlight.getName() != null) {
               byLower.putIfAbsent(highlight.getName().toLowerCase(), highlight.getName());
            }
         }
      }

      if(model.getUsedHighlightNames() != null) {
         for(String name : model.getUsedHighlightNames()) {
            if(name != null) {
               byLower.putIfAbsent(name.toLowerCase(), name);
            }
         }
      }

      return new ArrayList<>(byLower.values());
   }

   private static List<String> fieldNames(HighlightDialogModel model) {
      List<String> names = new ArrayList<>();

      if(model.getFields() != null) {
         for(var field : model.getFields()) {
            if(field != null && field.getName() != null) {
               names.add(field.getName());
            }
         }
      }

      return names;
   }

   private final ViewsheetSessionService sessions;
   private final HighlightDialogService highlightService;
}
