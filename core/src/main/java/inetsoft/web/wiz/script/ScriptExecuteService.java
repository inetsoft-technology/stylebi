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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.ScriptError;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes the script currently saved at a {@link ScriptTarget} (per {@code execute}/
 * {@code execute-live}'s wiz-services contract — they carry only {@code {target}}, not ad hoc
 * script text; the text must already have been written via {@code update_script}).
 *
 * <p><b>Fidelity limit:</b> there is no cheap, faithful clone of a {@link RuntimeViewsheet} to
 * dry-run against in isolation (unlike the worksheet plugin's structural edits, which mutate a
 * plain in-memory model). Both {@link #dryRun} and {@link #runLive} therefore execute against the
 * SAME live {@link ViewsheetScope} — {@code dryRun} does not guarantee zero side effects if the
 * script itself mutates state (e.g. a chart's binding). This is a documented simplification for
 * this slice, not a security boundary.</p>
 */
@Service
public class ScriptExecuteService {

   // Every entry here must be confirmed against ViewsheetScope.addFunctions()'s actual
   // propmap.put(...) registrations — do not add a name without finding its registration there.
   // Excludes toList/isCancelled/delayVisibility (pure reads/no-ops, not destructive).
   private static final List<String> DESTRUCTIVE_GLOBALS = List.of(
      "saveWorksheet", "runQuery", "setCellValue", "refreshData",
      "createConnection", "appendRow", "addImage"
   );

   // Bug #76661: runQuery('ws:<token>:<path>') is scope-sensitive syntax -- a literal "global"
   // second segment addresses AssetRepository.GLOBAL_SCOPE, anything else is treated as a
   // username and addresses that user's AssetRepository.USER_SCOPE (see
   // AssetEntry.createAssetEntry). Getting this wrong resolves to an AssetEntry that legitimately
   // has nothing at it, so the shared runtime (XUtil.runQuery / ReportWorksheetProcessor) returns
   // an empty result with no exception and no UI-visible warning -- indistinguishable from a
   // query that legitimately has no rows. Detecting that here, in the wiz-agent-only execute
   // path, lets the agent (and, transitively, the human reading its tool call) find out why
   // instead of silently treating an empty result as "no data". Deliberately NOT fixed in the
   // shared runtime: that would also change behavior for real Viewer/Composer/Schedule sessions
   // this service never touches. Bug #78285: the calls are observed as they execute (an opt-in
   // thread-local hook in XUtil.runQuery, unset everywhere else) rather than scanned for in the
   // script text, so commented-out, string-mentioned or dead-branch literals never produce a
   // note and a worksheet missing at every scope is reported too.
   private static final int MAX_WS_NOTES = 3;

   // Bug #76853 (VSD-010): a script-local var/let/const whose name collides (case-insensitively --
   // see BindingRootProxy's Calc builtin scope, which resolves last-resort via a
   // CASE_INSENSITIVE_ORDER TreeMap) with a registered global "Calc" scripting function name (e.g.
   // CalcDateTime.maxDate) and is assigned a primitive value gets routed through
   // BindingRootProxy.putMember -> VSAScriptable.putMember, landing in unrecognizedWrites even
   // though it is an ordinary local, not a real assembly-property write. Filtering the script's own
   // declared locals out of unrecognizedProperties here fixes the false positive without touching
   // that shared scope-emulation code (which every script kind in the product depends on).
   private static final Pattern DECLARATION_KEYWORD = Pattern.compile("\\b(?:var|let|const)\\b");
   private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

   @Autowired
   public ScriptExecuteService(ScriptReadService readService) {
      this.readService = readService;
   }

   /**
    * Validates + best-effort evaluates the script currently saved at {@code target}.
    *
    * <p>Because there is no isolated clone to run against (see the class javadoc), a script
    * that references a destructive global is NOT executed here at all — {@code execute} has no
    * {@code confirmed} parameter on the wiz-services contract, so there is no way for a caller
    * to accept the risk through this endpoint. Refuse and point at {@code run_script_live}
    * instead, which does have the confirmation gate.</p>
    */
   public ScriptExecResult dryRun(RuntimeViewsheet rvs, ScriptTarget target) throws PairingException {
      String text = readService.read(rvs, target).text();
      String destructive = firstDestructiveGlobal(text);

      if(destructive != null) {
         return new ScriptExecResult(false, null, null, null, true,
            "Script references \"" + destructive + "\", which can change stored/live data. " +
            "Dry-run cannot safely preview this — call run_script_live with confirmed=true " +
            "to execute it for real.", null, null);
      }

      return execute(rvs, target);
   }

   /**
    * Runs the script currently saved at {@code target}. If the script text references a
    * destructive global and {@code confirmed} is not {@code true}, execution is skipped and
    * {@code requiresConfirmation} is returned instead (per the plan's "surface the risk, don't
    * silently block" rule).
    */
   public ScriptExecResult runLive(RuntimeViewsheet rvs, ScriptTarget target, boolean confirmed)
      throws PairingException
   {
      String text = readService.read(rvs, target).text();
      String destructive = firstDestructiveGlobal(text);

      if(destructive != null && !confirmed) {
         return new ScriptExecResult(false, null, null, null, true,
            "Script references \"" + destructive + "\", which can change stored/live data. " +
            "Call run_script_live again with confirmed=true to proceed.", null, null);
      }

      return execute(rvs, target);
   }

   /**
    * A script runs against the live runtime and can write to any assembly, but, unlike every
    * other agent write, never added an undo checkpoint -- so the runtime still read as unchanged
    * since its last save and a later reload from storage (Bug #78662) would silently discard
    * the script's effect. Mark the runtime changed whenever a script was attempted.
    */
   private ScriptExecResult execute(RuntimeViewsheet rvs, ScriptTarget target) throws PairingException {
      try {
         return executeScript(rvs, target);
      }
      finally {
         Viewsheet vs = rvs.getViewsheet();

         if(vs != null) {
            rvs.addCheckpoint(vs.prepareCheckpoint());
         }
      }
   }

   private ScriptExecResult executeScript(RuntimeViewsheet rvs, ScriptTarget target) throws PairingException {
      String text = readService.read(rvs, target).text();

      if(text == null || text.isEmpty()) {
         return new ScriptExecResult(true, null, null, List.of(), false, null,
            "No script at " + target + " — nothing to execute.", null);
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);

      if(box == null) {
         throw new PairingException("Viewsheet sandbox not available for this runtime");
      }

      ViewsheetScope scope = box.getScope();
      String assemblyName = switch(target.location()) {
         case VS_INIT, VS_LOAD -> null;
         case ASSEMBLY, ASSEMBLY_ONCLICK -> target.assemblyName();
         // Permanent, not a placeholder: a calculated field is an expression evaluated per row,
         // not a runnable script, so there is no "wire it up later" for this case the way there
         // is for write/setEnabled elsewhere.
         case CALC_FIELD -> throw new PairingException(
            "A calculated field is an expression evaluated per row, not a runnable script. " +
            "Use read_script/update_script on it instead.");
         // Unreachable in practice -- readService.read(rvs, target) above already throws for
         // these two (ScriptReadService only serves RuntimeViewsheet locations; a worksheet
         // expression/condition column is read/written through WorksheetScriptService instead).
         // Handled explicitly anyway: this is a switch EXPRESSION, so the compiler -- not just
         // this method's runtime behavior -- must stay exhaustive over every Location.
         case WORKSHEET_EXPRESSION, WORKSHEET_CONDITION, WORKSHEET_CONDITION_VALUE -> throw new PairingException(
            "A worksheet expression/condition column is not a runnable viewsheet script. " +
            "Use worksheet-chat's edit_expression/edit_condition tools on it instead.");
      };

      // ASSEMBLY/ASSEMBLY_ONCLICK touch exactly the one named assembly. VS_INIT/VS_LOAD run with
      // assemblyName == null and can write to any assembly in the sheet by name, so every
      // assembly's scriptable is tracked -- each was already eagerly created and cached in the
      // scope's own propmap when the sandbox initialized (ViewsheetScope#addProperties0), so
      // this is a lookup, not a fresh instantiation with its own side effects.
      Map<String, VSAScriptable> trackedScriptables = trackedScriptables(target, assemblyName, rvs, scope);

      for(VSAScriptable scriptable : trackedScriptables.values()) {
         scriptable.resetUnrecognizedWrites();
         scriptable.resetRejectedWrites();
      }

      Map<AssetEntry, XUtil.RunQueryOutcome> wsOutcomes = new LinkedHashMap<>();
      BiConsumer<AssetEntry, XUtil.RunQueryOutcome> previousObserver =
         XUtil.setRunQueryObserver((entry, outcome) -> {
            if(wsOutcomes.size() < 64) {
               wsOutcomes.merge(entry, outcome,
                                (old, cur) -> old == XUtil.RunQueryOutcome.FOUND ? cur : old);
            }
         });

      try {
         Object value;

         try {
            value = scope.execute(text, assemblyName);
         }
         finally {
            XUtil.restoreRunQueryObserver(previousObserver);
         }

         List<String> unrecognized = new ArrayList<>();
         List<String> rejected = new ArrayList<>();

         // Sound only when exactly one assembly is tracked (ASSEMBLY/ASSEMBLY_ONCLICK): the
         // script text is that one execution context's complete source, so "declared as a
         // local anywhere in it" reliably means "this write is that local, not a real
         // property write." For VS_INIT/VS_LOAD, several assemblies are tracked from the same
         // shared script text, and a name being a local somewhere in it says nothing about
         // which assembly's dotted write it landed on -- applying the filter there could hide
         // a genuinely wrong Other.assembly.prop write that merely shares a bare name with an
         // unrelated local. Left unfiltered for that case, same as before this fix.
         Set<String> declaredLocals = assemblyName != null ? declaredLocals(text) : Set.of();

         for(Map.Entry<String, VSAScriptable> entry : trackedScriptables.entrySet()) {
            for(String name : entry.getValue().getUnrecognizedWrites()) {
               // A name the script itself declares via var/let/const is an ordinary script-local,
               // not a real assembly-property write, even though it reached this tracking (see
               // the DECLARATION_KEYWORD field comment above for why) -- exclude it.
               if(declaredLocals.contains(name)) {
                  continue;
               }

               // ASSEMBLY/ASSEMBLY_ONCLICK already name the one assembly in the message below,
               // so the bare property name is unambiguous there. VS_INIT/VS_LOAD can touch
               // several different assemblies, so qualify with which one to avoid conflating
               // e.g. two different assemblies' own unrecognized "label".
               unrecognized.add(assemblyName != null ? name : entry.getKey() + "." + name);
            }

            // Recognized properties (e.g. position/size) whose setter declined to apply the
            // value -- distinct from "unrecognized" above, which never even matched propmap.
            for(String name : entry.getValue().getRejectedWrites()) {
               rejected.add(assemblyName != null ? name : entry.getKey() + "." + name);
            }
         }

         List<String> notApplied = new ArrayList<>();
         notApplied.addAll(unrecognized);
         notApplied.addAll(rejected);

         List<String> changed = notApplied.isEmpty() ? List.of(target.toString()) : List.of();
         String summary;

         if(notApplied.isEmpty()) {
            summary = "Executed " + target + ".";
         }
         else {
            List<String> clauses = new ArrayList<>();

            if(!unrecognized.isEmpty()) {
               clauses.add("assigned " + unrecognized + " which " +
                  (unrecognized.size() == 1 ? "is not a recognized scriptable property" :
                     "are not recognized scriptable properties") +
                  (assemblyName != null ? " for this assembly type (" +
                     assemblyTypeName(rvs, assemblyName) + ")" : "") + " — the value " +
                  (unrecognized.size() == 1 ? "was" : "were") + " NOT applied; " +
                  (unrecognized.size() == 1 ? "it" : "they") + " only became an ad hoc script " +
                  "variable nothing else reads");
            }

            if(!rejected.isEmpty()) {
               clauses.add("assigned " + rejected + ", " +
                  (rejected.size() == 1 ? "a recognized property whose value was" :
                     "recognized properties whose values were") + " NOT applied because the " +
                  "connected session is a Composer design-mode session, not a live viewer " +
                  "runtime");
            }

            summary = "Executed " + target + ", but " + String.join("; and ", clauses) +
               ". Call get_script_context for " +
               (assemblyName != null ? assemblyName + "'s" : "the named assembly's") +
               " real scriptable properties, or use get_assembly_properties/update_binding if " +
               "you meant a different, non-scripted property.";
         }

         String wsScopeMismatch = wsRunQueryNotes(wsOutcomes, rvs.getUser());

         if(wsScopeMismatch != null) {
            summary = summary + " " + wsScopeMismatch;
         }

         return new ScriptExecResult(true, stringify(value), null, changed, false, null, summary,
            notApplied.isEmpty() ? null : notApplied);
      }
      catch(Exception ex) {
         return new ScriptExecResult(false, null, toScriptError(ex), null, false, null, null, null);
      }
   }

   /**
    * Which {@code VSAScriptable}(s) to reset/check {@code getUnrecognizedWrites()} on around
    * this execution. Empty for a location with no per-assembly scriptable to instrument
    * (e.g. an already-thrown location never reaches here).
    */
   private static Map<String, VSAScriptable> trackedScriptables(
      ScriptTarget target, String assemblyName, RuntimeViewsheet rvs, ViewsheetScope scope)
   {
      if(target.location() == ScriptTarget.Location.ASSEMBLY ||
         target.location() == ScriptTarget.Location.ASSEMBLY_ONCLICK)
      {
         VSAScriptable scriptable = scope.getVSAScriptable(assemblyName);
         return scriptable != null ? Map.of(assemblyName, scriptable) : Map.of();
      }

      if(target.location() == ScriptTarget.Location.VS_INIT ||
         target.location() == ScriptTarget.Location.VS_LOAD)
      {
         Map<String, VSAScriptable> tracked = new LinkedHashMap<>();

         for(Assembly assembly : rvs.getViewsheet().getAssemblies()) {
            VSAScriptable scriptable = scope.getVSAScriptable(assembly.getName());

            if(scriptable != null) {
               tracked.put(assembly.getName(), scriptable);
            }
         }

         return tracked;
      }

      return Map.of();
   }

   /**
    * {@code ViewsheetScope.execute}'s message already embeds the Rhino/GraalJS suggestion text
    * (see {@code senv.getSuggestion} in that method) — there is no separately-accessible
    * suggestion/line without reaching into private engine state, so both are folded into
    * {@code message} for this slice.
    */
   private static ScriptError toScriptError(Exception ex) {
      return new ScriptError(ex.getMessage(), null, null);
   }

   private static Object stringify(Object value) {
      if(value == null || value instanceof String || value instanceof Number
         || value instanceof Boolean)
      {
         return value;
      }

      // Polyglot/host objects (e.g. GraalJS wrapper types) may not be Jackson-serializable —
      // fall back to a safe string representation rather than risking a 500 on the response.
      return String.valueOf(value);
   }

   /**
    * Best-effort assembly type name (e.g. "SubmitVSAssembly") for the unrecognized-property
    * message. Falls back to the assembly name itself if the assembly can't be resolved (should
    * not happen for a target that just executed successfully, but this message is diagnostic
    * text, not load-bearing behavior).
    */
   private static String assemblyTypeName(RuntimeViewsheet rvs, String assemblyName) {
      VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
      return assembly != null ? assembly.getClass().getSimpleName() : assemblyName;
   }

   /**
    * Builds the explanation for the {@code runQuery('ws:...')} calls that actually executed and
    * did not resolve a worksheet, or {@code null} if there are none. Where the same path exists
    * at the sibling scope (global, or the caller's OWN user scope -- never another user's) the
    * note names the right address; otherwise one generic wording is used for not-found, denied
    * and failed alike, so the note can't be used to probe for another user's private worksheets.
    * Deduplicated per entry and capped at {@link #MAX_WS_NOTES}. Never throws -- this is a
    * best-effort diagnostic layered on top of execution, not a gate.
    */
   private static String wsRunQueryNotes(Map<AssetEntry, XUtil.RunQueryOutcome> outcomes,
                                         Principal user)
   {
      if(outcomes.isEmpty() || !(user instanceof XPrincipal)) {
         return null;
      }

      try {
         List<String> notes = new ArrayList<>();

         for(Map.Entry<AssetEntry, XUtil.RunQueryOutcome> e : outcomes.entrySet()) {
            if(e.getValue() == XUtil.RunQueryOutcome.FOUND) {
               continue;
            }

            String note = wsRunQueryNote(e.getKey(), (XPrincipal) user);

            if(note != null) {
               notes.add(note);

               if(notes.size() >= MAX_WS_NOTES) {
                  break;
               }
            }
         }

         return notes.isEmpty() ? null : String.join(" ", notes);
      }
      catch(Exception ex) {
         return null;
      }
   }

   private static String wsRunQueryNote(AssetEntry requested, XPrincipal user) {
      if(requested != null && requested.getScope() == AssetRepository.REPORT_SCOPE) {
         return wsNoScopeNote(requested, user);
      }

      if(requested == null ||
         (requested.getScope() != AssetRepository.GLOBAL_SCOPE &&
          requested.getScope() != AssetRepository.USER_SCOPE))
      {
         return null;
      }

      boolean global = requested.getScope() == AssetRepository.GLOBAL_SCOPE;
      String path = requested.getPath();
      String token = global ? "global"
         : requested.getUser() == null ? null : requested.getUser().getName();

      if(token == null) {
         return null;
      }

      String literal = "ws:" + token + ":" + path;
      IdentityID callerId = user.getIdentityID();
      boolean ownUserScope = !global && callerId != null && callerId.equals(requested.getUser());
      String generic = "runQuery('" + literal + "') returned no data: worksheet \"" + path +
         "\" at " + (global ? "global" : "user") + " scope could not be found, accessed or run " +
         "by this session. Check the name and scope token (\"global\", or your own username " +
         "for user scope); if the address is right, check the worksheet itself.";

      // The sibling probe only ever looks at global and the caller's own user scope, so it can't
      // reveal anything about another user's worksheets.
      if((!global && !ownUserScope) || (global && callerId == null)) {
         return generic;
      }

      AssetEntry sibling = global
         ? new AssetEntry(AssetRepository.USER_SCOPE, requested.getType(), path, callerId,
                          requested.getOrgID())
         : new AssetEntry(AssetRepository.GLOBAL_SCOPE, requested.getType(), path, null,
                          requested.getOrgID());

      try {
         AssetRepository repo = AssetUtil.getAssetRepository(false);

         if(repo == null || repo.containsEntry(requested) || !repo.containsEntry(sibling)) {
            return generic;
         }
      }
      catch(Exception ex) {
         return generic;
      }

      String requestedScope = global ? "global" : "user";
      String siblingScope = global ? "user" : "global";
      // getPath() excludes the optional 4th ":table" segment (stored as a property, not
      // part of the path) -- a corrected literal built from it drops that segment if the
      // original had one. Rare enough in practice not to special-case; noted, not guarded.
      // Both branches phrase this the same way -- the sibling asset already EXISTS (just
      // confirmed above via containsEntry), so this is "use the right address," never
      // "go save/create it there."
      String fix = global
         ? "Use \"ws:" + callerId.getName() + ":" + path + "\" to reach it."
         : "Use \"ws:global:" + path + "\" to reach it.";

      return "runQuery('" + literal + "') addresses worksheet \"" + path + "\" at " +
         requestedScope + " scope, but nothing exists there -- a worksheet with the same " +
         "name exists at " + siblingScope + " scope instead, so this call returns an empty " +
         "result with no error. " + fix;
   }

   /**
    * Note for a {@code ws:<path>} (or bare {@code ws:}) address with no scope token. The shared
    * address grammar parses it to REPORT_SCOPE, a legacy report-embedded worksheet address that
    * no StyleBI runtime can resolve, so the call always returns null. The grammar is left alone
    * (export/dependency extraction parse it the same way); only the agent gets told. Only the
    * global and the caller's own user scope are probed, never the REPORT_SCOPE entry itself.
    */
   private static String wsNoScopeNote(AssetEntry requested, XPrincipal user) {
      String path = requested.getPath();

      if(path == null || path.isEmpty()) {
         return "runQuery('ws:') returned no data: the address names no worksheet. Use " +
            "\"ws:global:<path>\" for a global worksheet or \"ws:<your username>:<path>\" for " +
            "one in your own user scope.";
      }

      IdentityID callerId = user.getIdentityID();
      String userLiteral = "ws:" + (callerId != null ? callerId.getName() : "<your username>") +
         ":" + path;
      String prefix = "runQuery('ws:" + path + "') returned no data: the address has no scope " +
         "token, so it does not name a global or user worksheet and can never resolve.";
      String generic = prefix + " Use \"ws:global:" + path + "\" for a global worksheet or \"" +
         userLiteral + "\" for one in your own user scope.";

      try {
         AssetRepository repo = AssetUtil.getAssetRepository(false);

         if(repo == null) {
            return generic;
         }

         AssetEntry global = new AssetEntry(AssetRepository.GLOBAL_SCOPE, requested.getType(),
                                            path, null, requested.getOrgID());

         if(repo.containsEntry(global)) {
            return prefix + " A worksheet \"" + path + "\" exists at global scope. Use \"ws:global:" +
               path + "\" to reach it.";
         }

         if(callerId != null) {
            AssetEntry own = new AssetEntry(AssetRepository.USER_SCOPE, requested.getType(), path,
                                            callerId, requested.getOrgID());

            if(repo.containsEntry(own)) {
               return prefix + " A worksheet \"" + path + "\" exists in your user scope. Use \"" +
                  userLiteral + "\" to reach it.";
            }
         }
      }
      catch(Exception ex) {
         return generic;
      }

      return generic;
   }

   /**
    * Best-effort extraction of every name this script text declares via {@code var}/{@code let}/
    * {@code const}, so a write to it can be excluded from {@code unrecognizedProperties} (see the
    * {@link #DECLARATION_KEYWORD} field comment). Comments and string/template literals are
    * stripped first so a keyword appearing inside either can't produce a false declared-local.
    *
    * <p>Limitation: only plain identifier declarators are recognized -- a destructuring target
    * (e.g. {@code var [a, b] = ...} or {@code var {a, b} = ...}) is not extracted. This only risks
    * under-filtering (a real declared local still reported as unrecognized), never
    * over-filtering a genuine unrecognized property write, so it is safe to leave unhandled for
    * this lightweight, non-parser-based scan.</p>
    */
   private static Set<String> declaredLocals(String scriptText) {
      String stripped = stripCommentsAndStrings(scriptText);
      Set<String> names = new HashSet<>();
      Matcher keyword = DECLARATION_KEYWORD.matcher(stripped);

      while(keyword.find()) {
         int pos = keyword.end();
         int depth = 0;
         boolean atDeclaratorStart = true;

         while(pos < stripped.length()) {
            char c = stripped.charAt(pos);

            if(Character.isWhitespace(c)) {
               pos++;
               continue;
            }

            if(atDeclaratorStart) {
               Matcher id = IDENTIFIER.matcher(stripped);
               id.region(pos, stripped.length());

               if(!id.lookingAt()) {
                  // Destructuring pattern or malformed declaration -- give up on this
                  // declaration's remaining declarators; see method javadoc.
                  break;
               }

               names.add(id.group());
               pos = id.end();
               atDeclaratorStart = false;
               continue;
            }

            if(c == '(' || c == '[' || c == '{') {
               depth++;
            }
            else if(c == ')' || c == ']' || c == '}') {
               if(depth == 0) {
                  break;
               }

               depth--;
            }
            else if(depth == 0 && c == ';') {
               break;
            }
            else if(depth == 0 && c == ',') {
               atDeclaratorStart = true;
            }

            pos++;
         }
      }

      return names;
   }

   /**
    * Drops comment and string/template-literal content from {@code text} so a later scan (e.g.
    * {@link #declaredLocals}) can't be fooled by {@code var}/{@code let}/{@code const} appearing
    * inside either. Not a full lexer -- e.g. a regex literal containing a quote character can
    * desync this, a limitation shared with any non-parser-based scan of script text.
    */
   private static String stripCommentsAndStrings(String text) {
      StringBuilder out = new StringBuilder(text.length());
      int i = 0;
      int n = text.length();

      while(i < n) {
         char c = text.charAt(i);

         if(c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
            while(i < n && text.charAt(i) != '\n') {
               i++;
            }
         }
         else if(c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
            i += 2;

            while(i + 1 < n && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) {
               i++;
            }

            i = Math.min(i + 2, n);
         }
         else if(c == '\'' || c == '"' || c == '`') {
            char quote = c;
            i++;

            while(i < n && text.charAt(i) != quote) {
               if(text.charAt(i) == '\\' && i + 1 < n) {
                  i++;
               }

               i++;
            }

            i++;
         }
         else {
            out.append(c);
            i++;
         }
      }

      return out.toString();
   }

   private static String firstDestructiveGlobal(String scriptText) {
      if(scriptText == null || scriptText.isEmpty()) {
         return null;
      }

      for(String name : DESTRUCTIVE_GLOBALS) {
         Pattern p = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(");
         Matcher m = p.matcher(scriptText);

         if(m.find()) {
            return name;
         }
      }

      return null;
   }

   private final ScriptReadService readService;
}
