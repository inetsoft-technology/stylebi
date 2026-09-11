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
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.model.vs.InputParameterDialogModel;
import inetsoft.web.composer.vs.dialog.HyperlinkDialogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

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
   /**
    * Agent-facing link types. The integers never appear in either direction.
    *
    * <p><b>{@code none} is {@link HyperlinkDialogService#NONE}, not 0.</b> That sentinel is what the
    * dialog service reports for an assembly with no link and what it tests to decide to clear one,
    * and mapping {@code none} to 0 instead broke this class in both directions at once. A read of a
    * never-linked assembly carried the sentinel 9, which matched no entry here and so reported
    * itself as {@code "unknown(9)"} rather than as {@code none}. A write of {@code none} sent 0, which
    * {@code HyperlinkDialogService.getHyperlink} does not recognise as "clear", so it built a real
    * {@code Hyperlink} carrying type 0. The link survived with its tooltip and target frame intact
    * while {@code tokenOf(0)} read it back as {@code "none"}: a clear that reported success and
    * cleared nothing.
    */
   private static final Map<String, Integer> LINK_TYPES = Map.of(
      "none", HyperlinkDialogService.NONE,
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

      return describe(assemblyName, model, target);
   }

   /** One {@code sessions.mutate}, so one undo checkpoint. */
   public void set(String sessionToken, Principal user, String assemblyName, Region region,
                   Map<String, Object> link, String linkUri) throws Exception
   {
      // Validated before the runtime is touched: a link whose type and value disagree costs
      // nothing to refuse here, and opens no checkpoint the caller then has to undo.
      String type = requireType(link);
      requireValueForType(type, link);

      // Resolved here for the same reason, and it is the reason this ordering matters rather than
      // being tidy. A viewsheet link is stored by asset ID; resolving inside the mutate meant an
      // unresolvable target threw with the model already half-written, leaving the assembly
      // carrying linkType=viewsheet with a null destination -- precisely the broken link the
      // validation above exists to prevent, arrived at by another road.
      String assetId = "viewsheet".equals(type)
         ? resolveViewsheetTarget(link, sessionToken, user) : null;

      // Also validated here, before the runtime is touched, and after assetId so it isn't
      // resolved twice: a bookmark that HyperlinkDialogService.getHyperlink would otherwise
      // silently drop (wrong linkType, or a name matching no VSBookmarkInfo) is refused instead.
      requireValidBookmark(type, link, assetId, user);

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         Region target = region == null ? Region.whole() : region;
         HyperlinkDialogModel model = hyperlinkService.getHyperlinkDialogModel(
            runtimeId, assemblyName, target.row(), target.col(), target.colName(),
            target.axis(), target.text(), target.titleLink(), target.emptyPlotLink(), user);

         apply(model, type, link, assetId);
         hyperlinkService.setHyperlinkDialogModel(runtimeId, assemblyName, model, linkUri, user,
                                                 dispatcher);
      });
   }

   public Map<String, Object> linkTypes() {
      List<String> names = new ArrayList<>(LINK_TYPES.keySet());
      Collections.sort(names);
      return Map.of("linkTypes", names);
   }

   /**
    * Enumerates the viewsheet asset paths a {@code "viewsheet"}-type {@link #set} call will
    * actually accept as {@code assetLinkPath} — the same two scopes, same
    * {@link AssetEntry.Type#VIEWSHEET} filter, same resolution order {@link #resolveViewsheetTarget}
    * uses, so a path this method returns can never be one the write path then refuses.
    *
    * <p>Deliberately does not dedupe a name that exists in both scopes: a caller needs to see the
    * collision in order to reach for {@code assetLinkId} instead, and silently picking one (even by
    * {@code resolveViewsheetTarget}'s own global-first tie-break) would hide exactly the ambiguity
    * {@code assetLinkId} exists to resolve.
    */
   public Map<String, Object> listLinkTargets(String sessionToken, Principal user, String folder,
                                              String query, Integer limit) throws Exception
   {
      AssetRepository repository = sessions.resolve(sessionToken, user).getAssetRepository();
      IdentityID owner = IdentityID.getIdentityIDFromKey(user.getName());
      int cap = limit == null ? 200 : limit;
      String root = folder == null || folder.isEmpty() ? "/" : folder;
      List<Map<String, Object>> targets = new ArrayList<>();
      boolean truncated = false;

      List<Map.Entry<String, AssetEntry>> scopes = List.of(
         Map.entry("global", new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, root, null)),
         Map.entry("user", new AssetEntry(
            AssetRepository.USER_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, root, owner)));

      for(Map.Entry<String, AssetEntry> scope : scopes) {
         // A folder that only exists in one scope is normal, not a mistake -- so the scope
         // missing it is skipped rather than erroring.
         if(!repository.containsEntry(scope.getValue())) {
            continue;
         }

         truncated |= walk(repository, user, scope.getValue(), scope.getKey(), query, cap, targets);
      }

      targets.sort(Comparator.comparing(t -> (String) t.get("path")));
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("targets", targets);
      out.put("truncated", truncated);
      return out;
   }

   /**
    * Plain DFS over {@code getEntries}, collecting {@code VIEWSHEET} leaves whose name matches
    * {@code query} and descending into every {@code REPOSITORY_FOLDER} regardless of its own
    * name's match, since a match can be several levels deeper. Returns {@code true} once
    * {@code targets} hits {@code cap} — the same signal the caller reports back as {@code truncated}.
    */
   private static boolean walk(AssetRepository repository, Principal user, AssetEntry folder,
                               String scope, String query, int cap,
                               List<Map<String, Object>> targets) throws Exception
   {
      AssetEntry[] entries = repository.getEntries(folder, user, ResourceAction.READ, SELECTOR);

      for(AssetEntry entry : entries) {
         if("Recycle Bin".equals(entry.getName())) {
            continue;
         }

         if(entry.getType() == AssetEntry.Type.REPOSITORY_FOLDER) {
            if(walk(repository, user, entry, scope, query, cap, targets)) {
               return true;
            }
         }
         else if(entry.getType() == AssetEntry.Type.VIEWSHEET) {
            if(query != null && !entry.getName().toLowerCase().contains(query.toLowerCase())) {
               continue;
            }

            Map<String, Object> target = new LinkedHashMap<>();
            target.put("path", entry.getPath());
            target.put("assetLinkId", entry.toIdentifier());
            target.put("scope", scope);
            targets.add(target);

            if(targets.size() >= cap) {
               return true;
            }
         }
      }

      return false;
   }

   private static final AssetEntry.Selector SELECTOR = new AssetEntry.Selector(
      AssetEntry.Type.REPOSITORY_FOLDER, AssetEntry.Type.VIEWSHEET);

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

   /**
    * Turns the {@code assetLinkPath} a caller naturally supplies into the asset ID a viewsheet link
    * is actually stored by.
    *
    * <p>{@code HyperlinkDialogService.getHyperlink} stores {@code getAssetLinkId()} as the link
    * value and hands the same string to {@code VSUtil.getBookmarks}. Nothing here ever set it, so
    * every viewsheet link died inside that lookup on a null — a raw
    * {@code NullPointerException: … because "this.text" is null} reaching the caller, with the
    * dialog model already partly written.
    *
    * <p>Both scopes are tried, because a path alone does not say which one it means: global first,
    * then the caller's own user scope, which is where a sheet under My Dashboards lives. An
    * explicit {@code assetLinkId} short-circuits the whole thing for a caller that already holds
    * one — the Composer's own dialog works that way, from its asset tree.
    *
    * <p>A path that resolves to nothing is refused, naming both scopes that were tried. That is the
    * one behaviour this method must not soften: the tool's contract says a link with no matching
    * destination is refused, because such a link is accepted by the dialog and then does nothing
    * when clicked.
    */
   private String resolveViewsheetTarget(Map<String, Object> link, String sessionToken,
                                         Principal user) throws Exception
   {
      String explicit = str(link, "assetLinkId");

      if(explicit != null) {
         return explicit;
      }

      String path = str(link, "assetLinkPath");
      AssetRepository repository = sessions.resolve(sessionToken, user).getAssetRepository();
      AssetEntry global = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null);

      if(repository.containsEntry(global)) {
         return global.toIdentifier();
      }

      IdentityID owner = IdentityID.getIdentityIDFromKey(user.getName());
      AssetEntry personal = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, path, owner);

      if(repository.containsEntry(personal)) {
         return personal.toIdentifier();
      }

      throw new IllegalArgumentException(
         "No viewsheet at '" + path + "'. Looked in the global scope and in " + owner.getName() +
         "'s own scope. A link to a viewsheet that does not exist is accepted by the dialog and " +
         "then does nothing when clicked, so it is refused here instead. Pass 'assetLinkId' " +
         "directly if you already hold the asset identifier.");
   }

   /**
    * {@code bookmark} is only ever wired into persistence for a {@code viewsheet}-type link
    * ({@code HyperlinkDialogService.getHyperlink}'s bookmark block sits inside an
    * {@code if(linkType == VIEWSHEET_LINK)}), and even there it silently no-ops -- no exception,
    * no signal -- when the supplied name matches no {@link VSBookmarkInfo} for that viewsheet.
    * Both are refused here instead, at the agent-facing layer, rather than in the shared
    * {@code HyperlinkDialogService} the real Composer dialog also uses.
    */
   private static void requireValidBookmark(String type, Map<String, Object> link, String assetId,
                                            Principal user)
   {
      String bookmark = str(link, "bookmark");

      if(bookmark == null) {
         return;
      }

      if(!"viewsheet".equals(type)) {
         throw new IllegalArgumentException(
            "'bookmark' only applies to a 'viewsheet' hyperlink, got linkType '" + type + "'. " +
            "It is silently ignored at persist time for any other type, so it is refused here " +
            "instead.");
      }

      IdentityID currentUser = IdentityID.getIdentityIDFromKey(user.getName());
      VSBookmarkInfo[] bookmarks = VSUtil.getBookmarks(assetId, currentUser);

      // Mirrors HyperlinkDialogService.getHyperlink's own parsing exactly, so a name this
      // validation accepts is guaranteed to be one persistence itself would also match: a
      // trailing "(Owner)" (the composer bookmark dropdown's own display form) is stripped
      // before comparing.
      int userIdx = bookmark.lastIndexOf('(') > 0 ? bookmark.lastIndexOf('(') : bookmark.length();
      String bookmarkName = bookmark.substring(0, userIdx);
      boolean matches = Arrays.stream(bookmarks).anyMatch(b -> b.getName().equals(bookmarkName));

      if(!matches) {
         List<String> names = Arrays.stream(bookmarks)
            .map(VSBookmarkInfo::getName)
            .collect(Collectors.toList());
         Collections.sort(names);
         throw new IllegalArgumentException(
            "No bookmark named '" + bookmark + "' on this viewsheet. A name that matches none " +
            "is silently ignored at persist time, so it is refused here instead. Known " +
            "bookmarks: " + names);
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
                             Map<String, Object> link, String assetId)
   {
      model.setLinkType(LINK_TYPES.get(type));

      if("none".equals(type)) {
         // With the type now set to the sentinel the dialog service recognises, it discards the
         // whole Hyperlink and none of this model survives -- so these nulls, and the tooltip and
         // target frame that used to linger beside them, no longer decide anything. Kept because a
         // model handed on with a stale destination still reads wrong to anything that looks.
         model.setWebLink(null);
         model.setAssetLinkPath(null);
         model.setAssetLinkId(null);
         model.setBookmark(null);
         model.setTargetFrame(null);
         model.setTooltip(null);
         model.setParamList(null);
         return;
      }

      if(link.containsKey("webLink")) {
         model.setWebLink(str(link, "webLink"));
      }

      if(link.containsKey("assetLinkPath")) {
         model.setAssetLinkPath(str(link, "assetLinkPath"));
      }

      // The ID is what getHyperlink actually stores and what VSUtil.getBookmarks looks up; the path
      // is the display half. Setting only the path is what made every viewsheet link fail.
      if(assetId != null) {
         model.setAssetLinkId(assetId);
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

      if(link.containsKey("paramList")) {
         model.setParamList(parseParamList(link.get("paramList"), model));
      }

      // HyperlinkDialog.submit() (the Angular dialog) treats "self" as pure UI sugar: checking
      // it rewrites targetFrame to "SELF" right before the model is sent, and
      // HyperlinkDialogService.setHyperlinkDialogModel only ever reads targetFrame -- it never
      // reads getSelf(). Without this, a caller sending self:true (and nothing else) gets ok:true
      // and a link that does nothing when clicked, since the boolean this method stores above is
      // discarded at persist time. Applying the same rewrite here, unconditionally and after every
      // other field, is what makes self:true behave the way the checkbox does -- including
      // overriding an explicit targetFrame in the same call, exactly like the checkbox overrides
      // the dialog's own free-text field.
      if(Boolean.TRUE.equals(link.get("self"))) {
         model.setTargetFrame("SELF");
      }
   }

   /**
    * Maps the agent's {@code paramList} entries -- {@code {name, value, valueSource, type}} --
    * onto {@link InputParameterDialogModel}, the same shape {@code HyperlinkDialogService} reads
    * when building the persisted {@link Hyperlink}'s custom parameters.
    *
    * <p>Only two {@code valueSource} values exist on the real dialog
    * ({@code InputParameterDialog.changeValueSource}): {@code constant} (a literal value, typed
    * by {@code type}) and {@code field} (an existing bindable field's name, matching one of
    * {@code model.getFields()} by {@link DataRefModel#getName()} -- the same identifier
    * {@code InputParameterDialog.ok()} matches against). A {@code field} entry naming a column
    * this assembly cannot actually bind is refused here rather than accepted and silently
    * producing a parameter with no data behind it.
    */
   private static List<InputParameterDialogModel> parseParamList(Object raw,
                                                                  HyperlinkDialogModel model)
   {
      if(!(raw instanceof List<?> rawList)) {
         throw new IllegalArgumentException(
            "'paramList' must be an array of {name, value, valueSource, type} objects, got '" +
            raw + "'.");
      }

      Set<String> fieldNames = model.getFields().stream()
         .map(DataRefModel::getName)
         .collect(Collectors.toSet());
      List<InputParameterDialogModel> params = new ArrayList<>();

      for(Object entry : rawList) {
         if(!(entry instanceof Map<?, ?> rawEntry)) {
            throw new IllegalArgumentException(
               "Each 'paramList' entry must be an object with 'name' and 'value', got '" + entry +
               "'.");
         }

         @SuppressWarnings("unchecked")
         Map<String, Object> paramMap = (Map<String, Object>) rawEntry;
         String name = str(paramMap, "name");

         if(name == null) {
            throw new IllegalArgumentException("Each 'paramList' entry needs a 'name'.");
         }

         String source = str(paramMap, "valueSource");
         source = source == null ? "constant" : source.trim().toLowerCase();

         if(!"constant".equals(source) && !"field".equals(source)) {
            throw new IllegalArgumentException(
               "paramList entry '" + name + "': 'valueSource' must be 'constant' or 'field', " +
               "got '" + source + "'.");
         }

         String value = str(paramMap, "value");

         if(value == null) {
            throw new IllegalArgumentException("paramList entry '" + name + "' needs a 'value'.");
         }

         if("field".equals(source) && !fieldNames.contains(value)) {
            throw new IllegalArgumentException(
               "paramList entry '" + name + "': 'value' must name a field this assembly can " +
               "bind (see list_bindable_fields), got '" + value + "'. Known fields: " +
               new TreeSet<>(fieldNames));
         }

         InputParameterDialogModel param = new InputParameterDialogModel();
         param.setName(name);
         param.setValueSource(source);
         param.setValue(value);

         // A constant entry always needs a type -- Hyperlink.getParameterType(name) returning
         // null is how HyperlinkDialogService.getHyperlinkDialogModel's read path infers
         // valueSource "field" (type == null ? "field" : "constant"). Leaving type null here for
         // an agent that (naturally) omitted it would round-trip a constant param back as a
         // "field" one on the very next read, and this class's own field-name validation above
         // would then refuse it on resubmission -- rejecting a param that worked. XSchema.STRING
         // matches what the real dialog itself defaults to (InputParameterDialog: every
         // newly-created or "constant"-switched-to param starts as XSchema.STRING).
         String type = str(paramMap, "type");
         param.setType("constant".equals(source) ? (type == null ? XSchema.STRING : type) : null);
         params.add(param);
      }

      return params;
   }

   private static Map<String, Object> describe(String assemblyName, HyperlinkDialogModel model,
                                               Region asked)
   {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);

      if(model == null) {
         out.put("linkType", "none");
         return out;
      }

      out.put("linkType", tokenOf(model.getLinkType()));
      out.put("webLink", model.getWebLink());
      out.put("assetLinkPath", model.getAssetLinkPath());
      // The model carries this correctly (HyperlinkDialogService.getHyperlinkDialogModel sets it
      // on every read), but it was never copied into this response -- a caller could set an
      // explicit assetLinkId and then never see it come back, unable to confirm which of two
      // same-named assets across scopes a link actually resolved to.
      out.put("assetLinkId", model.getAssetLinkId());
      out.put("bookmark", model.getBookmark());
      // model.isSelf() is dialog-model UI sugar: HyperlinkDialogService.getHyperlinkDialogModel
      // blanks targetFrame to "" and sets self=true whenever the persisted Hyperlink.targetFrame
      // was the literal "SELF", so it must be reconstructed here for this class's own wire
      // contract to hold. But self=true is ALSO the default for a never-linked assembly (line 182
      // of that class, hyperlink == null) -- guarding on linkType != NONE mirrors the real
      // Angular dialog's own rewrite (hyperlink-dialog.component.ts:309-310), which only fires
      // when there is an actual link, so an unlinked assembly still reads back an absent/blank
      // targetFrame rather than a fabricated "SELF".
      boolean selfWithARealLink =
         model.isSelf() && model.getLinkType() != HyperlinkDialogService.NONE;
      out.put("targetFrame", selfWithARealLink ? "SELF" : model.getTargetFrame());
      out.put("tooltip", model.getTooltip());
      out.put("self", model.isSelf());
      out.put("disableParameterPrompt", model.isDisableParameterPrompt());
      out.put("sendViewsheetParameters", model.isSendViewsheetParameters());
      out.put("sendSelectionsAsParameters", model.isSendSelectionsAsParameters());
      out.put("paramList", describeParamList(model.getParamList()));
      out.put("row", model.getRow());
      out.put("col", model.getCol());
      // The dialog service does not echo colName back into the model, so reporting only the model's
      // value meant a caller that addressed a cell BY NAME read back null and could not confirm it
      // had read the same cell it wrote. Falling back to what was asked for is the honest answer:
      // it is the name this read was scoped to, whether or not the model repeats it.
      out.put("colName", model.getColName() != null ? model.getColName()
                 : asked == null ? null : asked.colName());
      // A chart field's data-point link and its axis-label link are the same underlying storage:
      // HyperlinkRef (VSChartAggregateRef/VSChartDimensionRef) declares exactly one Hyperlink
      // field, with no second, axis-specific slot, and HyperlinkDialogService.getHyperlinkDialogModel
      // only sets model.colName from its chart branch -- so a non-null model colName here, outside
      // a title/empty-plot-link request, is exactly the shape where axis:true resolves to the same
      // ChartRef (and therefore the same Hyperlink) as a plain colName call. Setting one overwrites
      // the other's destination. This is the "fail loud" half of VHL-001 (bug #76580): a caller is
      // told plainly rather than silently losing the other link.
      out.put("axisAndDataPointShareLink",
              model.getColName() != null &&
              !(asked != null && (asked.titleLink() || asked.emptyPlotLink())));
      return out;
   }

   private static List<Map<String, Object>> describeParamList(
      List<InputParameterDialogModel> paramList)
   {
      List<Map<String, Object>> out = new ArrayList<>();

      for(InputParameterDialogModel param : paramList) {
         Map<String, Object> entry = new LinkedHashMap<>();
         entry.put("name", param.getName());
         entry.put("valueSource", param.getValueSource());
         entry.put("value", param.getValue());
         entry.put("type", param.getType());
         out.add(entry);
      }

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
