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

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
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
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.ScriptError;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
   // JS identifiers may be Unicode, and Script Library entry names are not restricted to ASCII.
   private static final String ID = "[\\p{L}_$][\\p{L}\\p{M}\\p{N}_$]*";
   private static final Pattern IDENTIFIER = Pattern.compile(ID);
   private static final Set<String> GLOBAL_OBJECTS = Set.of("this", "globalThis", "window", "self");

   // Bug #78812: ways a Script Library entry can declare a callable global function.
   private static final List<Pattern> FUNCTION_DECLARATIONS = List.of(
      Pattern.compile("\\bfunction\\s*\\*?\\s*(" + ID + ")\\s*\\("),
      Pattern.compile("(" + ID + ")\\s*=\\s*(?:async\\s+)?function\\b"),
      Pattern.compile("(" + ID + ")\\s*=\\s*(?:async\\s*)?" +
                      "(?:\\([^)]*\\)|" + ID + ")\\s*=>")
   );

   // Bug #78998: a library entry can also expose functions only as members of a top-level object
   // (var Ns = {f: function(){}}, Ns.f = ..., A.B.f = ..., IIFE/factory results, Object.assign).
   // The call site Ns.f() drops the member token, so the entry's top-level ROOT identifiers
   // (Ns, A) are indexed as well. Only assignments that bind the global scope count (see
   // topLevelRoots), never function-local vars.
   private static final Pattern ROOT_ASSIGNMENT = Pattern.compile(
      "(?<![\\p{L}\\p{M}\\p{N}_$.])(" + ID + ")((?:\\s*\\.\\s*" + ID + ")*)\\s*=(?![=>])");
   // Bug #79322: the global-binding scope of an assignment is decided by what opens each brace.
   // Plain blocks do not open a scope; an IIFE is a function expression called where it is written.
   private static final Set<String> PLAIN_BLOCK_HEADS =
      Set.of("if", "for", "while", "switch", "catch", "with");
   private static final Set<String> PLAIN_BLOCK_KEYWORDS = Set.of("else", "do", "try", "finally");
   private static final int MAX_SCOPED_SOURCE = 100_000;
   private static final int MAX_SCOPE_DEPTH = 64;
   private static final Pattern CALL_SUFFIX = Pattern.compile("\\.\\s*(call|apply)\\s*\\(");
   private static final Pattern OBJECT_ASSIGN = Pattern.compile(
      "(?<![\\p{L}\\p{M}\\p{N}_$.])Object\\s*\\.\\s*assign\\s*\\(\\s*(" + ID + ")");
   // Bug #79346 (SSL-014 gap D): a computed-member assignment, e.g. Q["M"] = ... or Q[M] = ...
   // Only matches once stripCommentsAndStrings has preserved a bracket-key string's own content
   // (see its own bracket-key handling) -- a non-key string stays fully stripped as before.
   private static final Pattern BRACKET_ASSIGNMENT = Pattern.compile(
      "(?<![\\p{L}\\p{M}\\p{N}_$.])(" + ID + ")\\s*\\[\\s*(" + ID + ")\\s*\\]\\s*=(?![=>])");
   // Bug #79346 (SSL-014 gap F): a bare call passing a global-object reference as its one and only
   // argument, e.g. install(this) -- see applyGlobalInvokedFunctionDeclarations. Deliberately
   // single-argument/literal-global only; an alias or a call with other arguments is not matched.
   private static final Pattern GLOBAL_ARG_CALL = Pattern.compile(
      "(?<![\\p{L}\\p{M}\\p{N}_$.])(" + ID + ")\\s*\\(\\s*(" + ID + ")\\s*\\)");

   private static final Logger LOG = LoggerFactory.getLogger(ScriptExecuteService.class);

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
      DestructiveRef destructive = firstDestructiveReference(text, rvs);

      if(destructive != null) {
         return new ScriptExecResult(false, null, null, null, true,
            destructive.describe() + ", which can change stored/live data. " +
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
      DestructiveRef destructive = firstDestructiveReference(text, rvs);

      if(destructive != null && !confirmed) {
         return new ScriptExecResult(false, null, null, null, true,
            destructive.describe() + ", which can change stored/live data. " +
            "Call run_script_live again with confirmed=true to proceed.", null, null);
      }

      return execute(rvs, target);
   }

   /**
    * A script runs against the live runtime and can write to any assembly, but, unlike every
    * other agent write, never added an undo checkpoint -- so the runtime still read as unchanged
    * since its last save and a later reload from storage (Bug #78662) would silently discard
    * the script's effect. Mark the runtime changed when the run changed the viewsheet, whether
    * it finished or threw part-way.
    *
    * <p>A run that changed nothing (a read-only script) adds no checkpoint: it would duplicate
    * the current state as an extra undo step and truncate the redo tail (Bug #79051). The change
    * is detected by comparing the viewsheet's serialized form before and after the run. That
    * form does not hold every script effect (sandbox variables, selection state), so such a run
    * is treated as a read. It is also not free of fetch side effects: a chart's first data fetch
    * fills in inferred column metadata, so that first read can still add one checkpoint; later
    * reads add none. When the viewsheet cannot be serialized the run counts as a change, which is
    * the checkpoint-after-every-run behavior of Bug #78662.</p>
    */
   private ScriptExecResult execute(RuntimeViewsheet rvs, ScriptTarget target) throws PairingException {
      String before = viewsheetXml(rvs.getViewsheet());

      try {
         return executeScript(rvs, target);
      }
      finally {
         Viewsheet vs = rvs.getViewsheet();

         String after = viewsheetXml(vs);

         if(vs != null && (before == null || after == null || !before.equals(after))) {
            rvs.addCheckpoint(vs.prepareCheckpoint());
            rvs.bumpWriteRevision();
         }
      }
   }

   /**
    * The viewsheet's serialized form without the runtime-only chart fields, which a chart's first
    * data fetch fills in and an undo restore regenerates. Works on a copy: serializing the live
    * viewsheet would shrink it as a side effect.
    *
    * @return the serialized form, or {@code null} when the viewsheet is null or cannot be
    *         serialized.
    */
   private static String viewsheetXml(Viewsheet vs) {
      if(vs == null) {
         return null;
      }

      try {
         Viewsheet copy = vs.prepareCheckpoint();

         for(Assembly assembly : copy.getAssemblies()) {
            if(assembly instanceof ChartVSAssembly chart) {
               chart.getVSChartInfo().clearRuntime();
            }
         }

         StringWriter out = new StringWriter();
         PrintWriter writer = new PrintWriter(out);
         copy.writeXML(writer);
         writer.flush();
         return out.toString();
      }
      catch(RuntimeException ex) {
         LOG.warn("Could not serialize the viewsheet to detect script changes", ex);
         return null;
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
      Set<String> names = new HashSet<>();
      forEachDeclared(stripCommentsAndStrings(scriptText), (pos, name, keyword) -> names.add(name));
      return names;
   }

   /**
    * @param blockScoped true for a {@code let}/{@code const} declarator -- bound to its own
    *                    enclosing block, not merely to the nearest function -- or false for a
    *                    {@code var} declarator or a function declaration (both function-scoped,
    *                    the pre-#79346 treatment of every {@code Declared} entry).
    */
   private record Declared(int pos, String name, boolean blockScoped) {
   }

   private interface DeclaredSink {
      void accept(int pos, String name, String keyword);
   }

   /** Every var/let/const declarator and function declaration, in source order. */
   private static List<Declared> declarations(String stripped) {
      List<Declared> found = new ArrayList<>();
      forEachDeclared(stripped, (pos, name, keyword) ->
         found.add(new Declared(pos, name, "let".equals(keyword) || "const".equals(keyword))));
      Matcher function = FUNCTION_DECLARATIONS.get(0).matcher(stripped);

      while(function.find()) {
         found.add(new Declared(function.start(1), function.group(1), false));
      }

      found.sort(Comparator.comparingInt(Declared::pos));
      return found;
   }

   private static void forEachDeclared(String stripped, DeclaredSink sink) {
      Matcher keyword = DECLARATION_KEYWORD.matcher(stripped);

      while(keyword.find()) {
         String keywordText = keyword.group();
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

               sink.accept(id.start(), id.group(), keywordText);
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

   }

   /**
    * Drops comment and string/template-literal content from {@code text} so a later scan (e.g.
    * {@link #declaredLocals}) can't be fooled by {@code var}/{@code let}/{@code const} appearing
    * inside either. Not a full lexer -- e.g. a regex literal containing a quote character can
    * desync this, a limitation shared with any non-parser-based scan of script text.
    */
   private static String stripCommentsAndStrings(String text) {
      return stripCommentsAndStrings(text, false);
   }

   /**
    * @param keepTemplateExpressions keep the bodies of template-literal {@code ${...}}
    *                                interpolations (they are executable code), dropping only the
    *                                literal text.
    */
   private static String stripCommentsAndStrings(String text, boolean keepTemplateExpressions) {
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
            // Bug #79346 (SSL-014 gap D): a string literal written directly as a computed member
            // key, e.g. Q["M"], is the one case where the key itself must survive stripping (as a
            // bare identifier-like token) so BRACKET_ASSIGNMENT can find it; every other string
            // literal is stripped exactly as before (deleted, with nothing substituted).
            boolean bracketKey = quote != '`' && precededByOpenBracket(out);
            int contentStart = i + 1;
            i++;

            while(i < n && text.charAt(i) != quote) {
               if(text.charAt(i) == '\\' && i + 1 < n) {
                  i++;
               }
               else if(keepTemplateExpressions && quote == '`' && text.charAt(i) == '$' &&
                  i + 1 < n && text.charAt(i + 1) == '{')
               {
                  int depth = 1;
                  out.append(' ');
                  i += 2;

                  while(i < n && depth > 0) {
                     char d = text.charAt(i);
                     depth += d == '{' ? 1 : d == '}' ? -1 : 0;

                     if(depth > 0) {
                        out.append(d);
                     }

                     i++;
                  }

                  out.append(' ');
                  continue;
               }

               i++;
            }

            if(bracketKey && i < n && isFollowedByCloseBracket(text, i + 1)) {
               out.append(text, contentStart, i);
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

   /** True when the last non-whitespace character already written to {@code out} is {@code [}. */
   private static boolean precededByOpenBracket(StringBuilder out) {
      int p = out.length() - 1;

      while(p >= 0 && Character.isWhitespace(out.charAt(p))) {
         p--;
      }

      return p >= 0 && out.charAt(p) == '[';
   }

   /** True when the next non-whitespace character of {@code text} starting at {@code p} is {@code ]}. */
   private static boolean isFollowedByCloseBracket(String text, int p) {
      p = skipWhitespaceForward(text, p);
      return p < text.length() && text.charAt(p) == ']';
   }

   /**
    * Bug #78812: finds the first destructive global the script reaches, directly or through
    * Script Library functions it calls (transitively). Library functions are hoisted globals
    * installed from the whole entry source, so the call-site identifier is the DECLARED function
    * name (an entry may declare several, and its name may differ from them), not the entry name;
    * both are indexed. Sources come from the runtime user's org library, the same set the engine
    * installs. Like the direct scan this is a name scan only: eval/string-built/aliased global
    * calls and library top-level code (which runs at engine init) are not covered.
    */
   private DestructiveRef firstDestructiveReference(String scriptText, RuntimeViewsheet rvs) {
      String direct = firstDestructiveGlobal(scriptText);

      if(direct != null) {
         return new DestructiveRef(direct, null, null);
      }

      if(scriptText == null || scriptText.isEmpty()) {
         return null;
      }

      Map<String, String> sources = libraryScripts(rvs.getUser());

      if(sources == null || sources.isEmpty()) {
         return null;
      }

      Map<String, Set<String>> index = new LinkedHashMap<>();

      for(Map.Entry<String, String> e : sources.entrySet()) {
         index.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>()).add(e.getKey());

         for(String declared : declaredFunctions(e.getValue())) {
            index.computeIfAbsent(declared, k -> new LinkedHashSet<>()).add(e.getKey());
         }

         for(String root : topLevelRoots(e.getValue())) {
            index.computeIfAbsent(root, k -> new LinkedHashSet<>()).add(e.getKey());
         }
      }

      Deque<String> work = new ArrayDeque<>(identifiers(stripCommentsAndStrings(scriptText, true), true));
      Set<String> visited = new HashSet<>();

      while(!work.isEmpty()) {
         String token = work.poll();
         Set<String> entries = index.get(token);

         if(entries == null) {
            continue;
         }

         for(String entry : entries) {
            if(!visited.add(entry)) {
               continue;
            }

            String source = sources.get(entry);
            String hit = firstDestructiveGlobal(source);

            if(hit != null) {
               return new DestructiveRef(hit, token, entry);
            }

            work.addAll(identifiers(source, false));
         }
      }

      return null;
   }

   /**
    * Library entry name to source for the runtime user's organization, or null when the library
    * is unavailable (no provider bean, e.g. a minimal context), in which case only the direct
    * scan applies. Package-private so tests can supply a library.
    */
   Map<String, String> libraryScripts(Principal user) {
      LibManagerProvider provider;

      try {
         provider = LibManagerProvider.getInstance();
      }
      catch(NoSuchBeanDefinitionException ex) {
         LOG.debug("No Script Library provider; scanning the script text only", ex);
         return null;
      }

      // any other failure (storage, cluster, org lookup) propagates instead of silently
      // degrading to the direct-only scan, which would re-open the bug
      LibManager mgr =
         user instanceof XPrincipal ? provider.getManager(user) : provider.getManager();

      Map<String, String> sources = new LinkedHashMap<>();
      Enumeration<String> names = mgr.getScripts();

      while(names.hasMoreElements()) {
         String name = names.nextElement();
         String source = mgr.getScript(name);

         if(source != null) {
            sources.put(name, source);
         }
      }

      return sources;
   }

   private static Set<String> declaredFunctions(String source) {
      Set<String> names = new LinkedHashSet<>();

      for(Pattern p : FUNCTION_DECLARATIONS) {
         Matcher m = p.matcher(source);

         while(m.find()) {
            names.add(m.group(1));
         }
      }

      return names;
   }

   /**
    * Bug #78998 / #79322: root identifiers a library entry binds in the GLOBAL scope while it
    * loads, i.e. the globals it exposes as objects/namespaces. The axis is which scope an
    * assignment binds to (global vs function-local), not how deeply it is nested:
    * <ul>
    * <li>top level, including inside plain blocks (if/else/for/while/try/switch/bare {}), which
    * do not open a scope: {@code var Ns = ...}, {@code Ns = ...}, {@code A.B.f = ...} (root A),
    * {@code Object.assign(Ns, ...)}; for {@code window.Ns = ...}/{@code this.Ns = ...} the global
    * qualifier is skipped and {@code Ns} is the root.</li>
    * <li>inside an IIFE (it runs at load time): a qualifier that is a global object, an IIFE
    * parameter bound to one ({@code (function(g){ g.Ns = ... })(this)}), or {@code this} when the
    * IIFE runs with the global {@code this}; and an undeclared bare {@code Ns = ...} (leaks to the
    * global scope) unless the IIFE declares {@code Ns} itself.</li>
    * </ul>
    * Anything inside an ordinary function body, an object literal or parentheses is never
    * indexed (a common local name such as {@code result} must not gate every script that uses it).
    */
   private static Set<String> topLevelRoots(String source) {
      Set<String> roots = new LinkedHashSet<>();

      try {
         collectRoots(source, roots);
      }
      catch(RuntimeException ex) {
         // a scan defect must never fail execute_script; keep whatever was found so far
         LOG.warn("Could not fully scan a Script Library entry for exposed globals", ex);
      }

      return roots;
   }

   private static void collectRoots(String source, Set<String> roots) {
      String text = stripCommentsAndStrings(source, false);
      ScopePositions positions = scopeOwners(text);
      ScopeFrame[] owners = positions.owners();
      ScopeFrame[] lexical = positions.lexical();
      Matcher m = ROOT_ASSIGNMENT.matcher(text);

      while(m.find()) {
         ScopeFrame owner = owners[m.start()];

         if(owner == null) {
            continue;
         }

         String first = m.group(1);

         // Bug #79346 (SSL-014 gap A): var/let/const X = this; (or a bare X = this;) makes X an
         // alias of the global object in this frame, so a later X.Ns = ... resolves Ns the same
         // way this.Ns = ... would -- instead of X itself being (wrongly) indexed as a root.
         if(m.group(2).isEmpty() && !GLOBAL_OBJECTS.contains(first) &&
            isBareGlobalAliasAssignment(text, m.end(), owner))
         {
            owner.aliases.add(first);
            continue;
         }

         String root = null;

         if(GLOBAL_OBJECTS.contains(first) || owner.aliases.contains(first)) {
            if(owner.qualifiesAsGlobal(first)) {
               Matcher member = IDENTIFIER.matcher(m.group(2));
               root = member.find() ? member.group() : null;
            }
         }
         else if(owner.bindsBareGlobal(first, lexical[m.start()])) {
            root = first;
         }

         if(root != null && !GLOBAL_OBJECTS.contains(root)) {
            roots.add(root);
         }
      }

      // Bug #79346 (SSL-014 gap D): a computed-member assignment, Q["M"] = ... or Q[M] = ... --
      // stripCommentsAndStrings has already reduced a bracket-key STRING to its bare content, so
      // this matches the same way ROOT_ASSIGNMENT's dot-chain branch matches Q.M = ....
      m = BRACKET_ASSIGNMENT.matcher(text);

      while(m.find()) {
         ScopeFrame owner = owners[m.start()];

         if(owner == null) {
            continue;
         }

         String first = m.group(1);
         String member = m.group(2);
         String root = null;

         if(GLOBAL_OBJECTS.contains(first) || owner.aliases.contains(first)) {
            if(owner.qualifiesAsGlobal(first)) {
               root = member;
            }
         }
         else if(owner.bindsBareGlobal(first, lexical[m.start()])) {
            root = first;
         }

         if(root != null && !GLOBAL_OBJECTS.contains(root)) {
            roots.add(root);
         }
      }

      m = OBJECT_ASSIGN.matcher(text);

      while(m.find()) {
         ScopeFrame owner = owners[m.start()];

         if(owner != null && owner.bindsBareGlobal(m.group(1), lexical[m.start()]) &&
            !GLOBAL_OBJECTS.contains(m.group(1)))
         {
            roots.add(m.group(1));
         }
      }
   }

   /**
    * Bug #79346 (SSL-014 gap A): true when the text starting right after an {@code =} is a bare
    * reference to a name {@code owner} already recognizes as the global object (a literal
    * {@code this}/{@code window}/{@code globalThis}/{@code self}, or an already-known alias),
    * with no {@code .}/{@code [}/{@code (} suffix -- i.e. the identifier itself is being assigned,
    * not a property read off it (which would make this an ordinary value extraction, not an
    * alias).
    */
   private static boolean isBareGlobalAliasAssignment(String text, int afterEquals, ScopeFrame owner) {
      int p = skipWhitespaceForward(text, afterEquals);
      Matcher id = IDENTIFIER.matcher(text);
      id.region(p, text.length());

      if(!id.lookingAt()) {
         return false;
      }

      String name = id.group();
      int afterId = skipWhitespaceForward(text, id.end());

      if(afterId < text.length()) {
         char next = text.charAt(afterId);

         if(next == '.' || next == '[' || next == '(') {
            return false;
         }
      }

      return owner.qualifiesAsGlobal(name);
   }

   /**
    * {@link #scopeOwners}'s result: {@code owners[i]} is the function-level frame an assignment
    * written at position {@code i} binds in (null when it binds nowhere indexable), and
    * {@code lexical[i]} is the innermost block (plain block, function, or the owner itself) that
    * position lexically sits in -- used only to find a block-scoped let/const/catch-parameter
    * declared directly on an intermediate plain block (Bug #79346 / SSL-015).
    */
   private record ScopePositions(ScopeFrame[] owners, ScopeFrame[] lexical) {
   }

   /**
    * For every character of {@code text} (comments/strings already stripped), the function-level
    * frame that an assignment written there binds in, or null when it binds nowhere indexable (an
    * ordinary function body, an object literal, parentheses/brackets). Plain blocks resolve to the
    * frame of their enclosing scope.
    */
   private static ScopePositions scopeOwners(String text) {
      int n = text.length();
      int[] match = new int[n];
      Arrays.fill(match, -1);
      Deque<Integer> opens = new ArrayDeque<>();

      for(int i = 0; i < n; i++) {
         char c = text.charAt(i);

         if(c == '{' || c == '(' || c == '[') {
            opens.push(i);
         }
         else if((c == '}' || c == ')' || c == ']') && !opens.isEmpty()) {
            int open = opens.pop();
            match[open] = i;
            match[i] = open;
         }
      }

      // beyond this size the IIFE analysis is skipped (only the top level and plain blocks bind
      // globals), which bounds the cost of a pathological entry
      boolean scoped = n <= MAX_SCOPED_SOURCE;
      List<Declared> declared = scoped ? declarations(text) : List.of();
      int nextDeclared = 0;
      ScopeFrame top = new ScopeFrame();
      top.function = true;
      top.thisGlobal = true;
      top.owner = top;
      top.fnFrame = top;
      top.locals = new HashSet<>();
      top.aliases = new HashSet<>();
      Deque<ScopeFrame> stack = new ArrayDeque<>();
      stack.push(top);
      ScopeFrame[] owners = new ScopeFrame[n];
      ScopeFrame[] lexical = new ScopeFrame[n];

      for(int i = 0; i < n; i++) {
         char c = text.charAt(i);

         if((c == '}' || c == ')' || c == ']') && stack.size() > 1) {
            stack.pop();
         }

         owners[i] = stack.peek().owner;
         lexical[i] = stack.peek();

         // Bug #79346 (SSL-015): a let/const declarator belongs to the innermost LEXICAL block it
         // is written in -- it is genuinely block-scoped, never visible outside its own { } --
         // while var/a function declaration still belongs to the nearest FUNCTION-level frame
         // (pre-existing, unchanged), and only counts there when that function is itself an IIFE.
         while(nextDeclared < declared.size() && declared.get(nextDeclared).pos() <= i) {
            Declared d = declared.get(nextDeclared);

            if(d.blockScoped()) {
               stack.peek().locals.add(d.name());
            }
            else {
               ScopeFrame fn = stack.peek().fnFrame;

               if(fn.iife) {
                  fn.locals.add(d.name());
               }
            }

            nextDeclared++;
         }

         if(c == '{' || c == '(' || c == '[') {
            stack.push(openFrame(text, i, match, stack, scoped));
         }
      }

      // Bug #79346 (SSL-014 gap F): a function declared and only invoked later, by name, with a
      // global-object reference, is itself load-time code even though nothing precedes or follows
      // it in the IIFE idiom -- retarget the positions inside its body accordingly.
      applyGlobalInvokedFunctionDeclarations(text, match, owners, lexical);

      return new ScopePositions(owners, lexical);
   }

   /**
    * Bug #79346 (SSL-014 gap F): {@code function install(g){ g.Ns = ...; } install(this);} --
    * {@code install}'s own body is an ordinary (non-IIFE) function as far as {@link #openFrame} is
    * concerned, since nothing in the classic IIFE idiom (a preceding operator, an adjacent call)
    * appears anywhere near it; the call that actually makes it run at load time is a wholly
    * separate, later statement. Finds every named function DECLARATION reachable from load-time
    * code (its own {@code owners[]} position, before this override, is non-null) whose name is
    * also called, elsewhere in a load-time-reachable statement, with exactly one argument that is
    * itself a literal global-object reference -- and promotes its body the same way an IIFE's body
    * would be: its own frame, its first parameter aliased to the global object, every position
    * inside it that was previously unowned now resolves through this frame.
    *
    * <p>Deliberately narrow: only a single-argument call whose one argument is textually
    * {@code this}/{@code window}/{@code globalThis}/{@code self} is recognized (not an arbitrary
    * alias, and not a multi-argument call with the global reference in some other position) --
    * this covers the reported shape without attempting general call-graph/alias reasoning.</p>
    */
   private static void applyGlobalInvokedFunctionDeclarations(
      String text, int[] match, ScopeFrame[] owners, ScopeFrame[] lexical)
   {
      Set<String> calledWithGlobal = new HashSet<>();
      Matcher call = GLOBAL_ARG_CALL.matcher(text);

      while(call.find()) {
         if(owners[call.start()] != null && GLOBAL_OBJECTS.contains(call.group(2))) {
            calledWithGlobal.add(call.group(1));
         }
      }

      if(calledWithGlobal.isEmpty()) {
         return;
      }

      List<Declared> declared = declarations(text);
      Matcher decl = FUNCTION_DECLARATIONS.get(0).matcher(text);

      while(decl.find()) {
         String name = decl.group(1);

         if(!calledWithGlobal.contains(name) || owners[decl.start()] == null) {
            continue;
         }

         int parenOpen = decl.end() - 1;
         int parenClose = match[parenOpen];

         if(parenClose < 0) {
            continue;
         }

         int bodyOpen = skipWhitespaceForward(text, parenClose + 1);

         if(bodyOpen >= text.length() || text.charAt(bodyOpen) != '{' || match[bodyOpen] < 0) {
            continue;
         }

         int bodyClose = match[bodyOpen];
         ScopeFrame synthetic = new ScopeFrame();
         synthetic.function = true;
         synthetic.iife = true;
         synthetic.thisGlobal = true;
         synthetic.fnFrame = synthetic;
         synthetic.owner = synthetic;
         synthetic.lexicalParent = lexical[decl.start()];
         synthetic.locals = new HashSet<>();
         synthetic.aliases = new HashSet<>();

         List<String> params = splitTopLevel(text, parenOpen + 1, parenClose).stream()
            .map(ScriptExecuteService::parameterName).toList();

         for(int i = 0; i < params.size(); i++) {
            String param = params.get(i);

            if(param == null) {
               continue;
            }

            synthetic.locals.add(param);

            if(i == 0) {
               // the call's own single argument is the global reference itself (GLOBAL_ARG_CALL
               // only ever matches exactly one argument)
               synthetic.aliases.add(param);
            }
         }

         for(Declared d : declared) {
            if(d.pos() > bodyOpen && d.pos() < bodyClose) {
               synthetic.locals.add(d.name());
            }
         }

         for(int i = bodyOpen + 1; i < bodyClose; i++) {
            if(owners[i] == null) {
               owners[i] = synthetic;
               lexical[i] = synthetic;
            }
         }
      }
   }

   private static ScopeFrame openFrame(String text, int open, int[] match,
                                       Deque<ScopeFrame> stack, boolean scoped)
   {
      ScopeFrame parent = stack.peek();
      ScopeFrame frame = new ScopeFrame();
      frame.fnFrame = parent.fnFrame;
      // Bug #79346 (SSL-015): every frame -- plain block, (, [ or function -- keeps a pointer to
      // its own immediate lexical parent (not the flattened/resolved owner), so a block-scoped
      // let/const/catch-parameter can be found by walking lexical nesting, not just the owner.
      frame.lexicalParent = parent;
      // Every frame starts with its own mutable, empty locals set, so a plain block (if/catch/...)
      // can record a block-scoped let/const/catch-parameter directly on itself, not only an IIFE's
      // function-level frame (the one case the pre-existing code wired up).
      frame.locals = new HashSet<>();

      if(text.charAt(open) != '{') {
         // Bug #79346 (SSL-014 gap C): a ( or [ does not open a new scope for assignments written
         // directly inside it (a function's own call-argument list, a computed-member key, ...) --
         // it binds in whatever scope encloses it, the same as any other statement there.
         frame.owner = parent.owner;
         return frame;
      }

      int p = skipWhitespaceBack(text, open - 1);
      int paramsStart = -1;
      int paramsEnd = -1;
      int funcStart = -1;
      boolean function = false;
      boolean arrow = false;

      if(p < 0) {
         frame.owner = parent.owner;
         return frame;
      }

      char c = text.charAt(p);

      if(c == '>' && p > 0 && text.charAt(p - 1) == '=') {
         int q = skipWhitespaceBack(text, p - 2);
         function = true;
         arrow = true;

         if(q >= 0 && text.charAt(q) == ')' && match[q] >= 0) {
            paramsStart = match[q] + 1;
            paramsEnd = q;
            funcStart = match[q];
         }
         else if(q >= 0 && isIdentifierPart(text.charAt(q))) {
            funcStart = q;

            while(funcStart > 0 && isIdentifierPart(text.charAt(funcStart - 1))) {
               funcStart--;
            }

            paramsStart = funcStart;
            paramsEnd = q + 1;
         }
      }
      else if(c == ')' && match[p] >= 0) {
         int q = skipWhitespaceBack(text, match[p] - 1);
         String word = wordEndingAt(text, q);

         if(word != null && PLAIN_BLOCK_HEADS.contains(word)) {
            frame.owner = parent.owner;

            // Bug #79346 (SSL-015): a catch clause's own exception parameter is locally bound to
            // its catch body in EVERY frame kind, IIFE or not -- recorded directly on this block's
            // own frame (not routed through the var/let/const declaration scan, which never visits
            // "catch (...)" at all).
            if("catch".equals(word)) {
               int paramStart = skipWhitespaceForward(text, match[p] + 1);

               if(paramStart < p) {
                  Matcher param = IDENTIFIER.matcher(text);
                  param.region(paramStart, p);

                  if(param.lookingAt()) {
                     frame.locals.add(param.group());
                  }
               }
            }

            return frame;
         }

         if(word != null) {
            function = true;
            paramsStart = match[p] + 1;
            paramsEnd = p;

            if(word.equals("function")) {
               funcStart = q + 1 - word.length();
            }
            else {
               // named function expression; anything else is a method shorthand, which is an
               // ordinary function
               int r = skipWhitespaceBack(text, q - word.length());

               if(r >= 0 && text.charAt(r) == '*') {
                  r = skipWhitespaceBack(text, r - 1);
               }

               if("function".equals(wordEndingAt(text, r))) {
                  funcStart = r + 1 - "function".length();
               }
            }
         }
      }
      // Bug #79346 (SSL-014 gap E): a { preceded by a bare : (a statement label, or a case N:) is
      // just as transparent as one after ;/{/} -- it introduces no scope of its own.
      else if(c == ';' || c == '{' || c == '}' || c == ':') {
         frame.owner = parent.owner;
         return frame;
      }
      else if(isIdentifierPart(c) && PLAIN_BLOCK_KEYWORDS.contains(wordEndingAt(text, p))) {
         frame.owner = parent.owner;
         return frame;
      }

      if(!function) {
         return frame;
      }

      ScopeFrame enclosing = parent.fnFrame;

      List<String> params = paramsStart >= 0 ?
         splitTopLevel(text, paramsStart, paramsEnd).stream()
            .map(ScriptExecuteService::parameterName).toList() :
         List.of();
      frame.function = true;
      frame.fnFrame = frame;
      frame.depth = enclosing.depth + 1;
      frame.aliases = new HashSet<>(enclosing.aliases);
      frame.aliases.removeAll(params);
      frame.thisGlobal = arrow && enclosing.thisGlobal;
      int close = match[open];

      // only code that runs while the entry loads can bind a global: an IIFE nested in an
      // ordinary function body (or in an unscanned/too deep scope) is just another function
      if(funcStart < 0 || close < 0 || !scoped || enclosing.owner == null ||
         enclosing.depth >= MAX_SCOPE_DEPTH)
      {
         return frame;
      }

      // IIFE: a function expression that is called where it is written. Bug #79346 (SSL-014 gap B):
      // "=" (var _ = function(){}()) and the keyword "void" (void function(){}()) also force
      // expression parsing, the same as a leading (!+-~.
      int before = skipWhitespaceBack(text, funcStart - 1);
      boolean precededByOperator = before >= 0 && "(!+-~=".indexOf(text.charAt(before)) >= 0;
      boolean precededByVoid = before >= 0 && "void".equals(wordEndingAt(text, before));

      if(before < 0 || !(precededByOperator || precededByVoid)) {
         return frame;
      }

      int after = skipWhitespaceForward(text, close + 1);

      if(after < text.length() && text.charAt(after) == ')') {
         after = skipWhitespaceForward(text, after + 1);
      }

      int argsOpen = -1;
      boolean viaCall = false;
      boolean viaApply = false;

      if(after < text.length() && text.charAt(after) == '(') {
         argsOpen = after;
      }
      else {
         Matcher call = CALL_SUFFIX.matcher(text);
         call.region(Math.min(after, text.length()), text.length());

         if(call.lookingAt()) {
            argsOpen = call.end() - 1;
            viaCall = call.group(1).equals("call");
            viaApply = !viaCall;
         }
      }

      if(argsOpen < 0 || match[argsOpen] < 0) {
         return frame;
      }

      List<String> args = splitTopLevel(text, argsOpen + 1, match[argsOpen]);
      int firstParamArg = 0;
      frame.iife = true;
      frame.owner = frame;

      if(arrow) {
         frame.thisGlobal = enclosing.thisGlobal;
      }
      else if(viaCall || viaApply) {
         frame.thisGlobal = !args.isEmpty() && enclosing.isGlobalReference(args.get(0));
      }
      else {
         // a sloppy-mode function called plainly gets the global object as this
         frame.thisGlobal = true;
      }

      if(viaApply) {
         args = List.of();
      }
      else if(viaCall) {
         firstParamArg = 1;
      }

      frame.outer = enclosing;
      frame.locals = new HashSet<>();

      for(int i = 0; i < params.size(); i++) {
         String param = params.get(i);

         if(param == null) {
            continue;
         }

         frame.locals.add(param);

         if(firstParamArg + i < args.size() &&
            enclosing.isGlobalReference(args.get(firstParamArg + i)))
         {
            frame.aliases.add(param);
         }
      }

      return frame;
   }

   /** Name of one declared parameter ({@code a}, {@code a = 1}, {@code ...a}), else null. */
   private static String parameterName(String param) {
      String text = param.trim();

      if(text.startsWith("...")) {
         text = text.substring(3).trim();
      }

      Matcher id = IDENTIFIER.matcher(text);
      return id.lookingAt() ? id.group() : null;
   }

   /** Splits {@code text[from, to)} at commas that are not inside brackets. */
   private static List<String> splitTopLevel(String text, int from, int to) {
      List<String> parts = new ArrayList<>();
      int depth = 0;
      int start = from;

      for(int i = from; i < to; i++) {
         char c = text.charAt(i);

         if(c == '(' || c == '[' || c == '{') {
            depth++;
         }
         else if(c == ')' || c == ']' || c == '}') {
            depth--;
         }
         else if(c == ',' && depth == 0) {
            parts.add(text.substring(start, i));
            start = i + 1;
         }
      }

      String last = text.substring(start, to);

      if(!last.isBlank() || !parts.isEmpty()) {
         parts.add(last);
      }

      return parts;
   }

   private static String wordEndingAt(String text, int end) {
      if(end < 0 || !isIdentifierPart(text.charAt(end))) {
         return null;
      }

      int start = end;

      while(start > 0 && isIdentifierPart(text.charAt(start - 1))) {
         start--;
      }

      return text.substring(start, end + 1);
   }

   private static int skipWhitespaceForward(String text, int p) {
      while(p < text.length() && Character.isWhitespace(text.charAt(p))) {
         p++;
      }

      return p;
   }

   /** One brace/paren/bracket level seen by {@link #scopeOwners}. */
   private static final class ScopeFrame {
      /** The function-level frame assignments written in this frame bind in; null if none. */
      ScopeFrame owner;
      /** True for the script top level and for function bodies. */
      boolean function;
      /** True for a function expression called where it is written. */
      boolean iife;
      /** True when {@code this} is the global object here. */
      boolean thisGlobal;
      /** Names (IIFE parameters) known to hold the global object. */
      Set<String> aliases = Set.of();
      /** Names an IIFE declares itself (var/let/const/function/parameters). */
      Set<String> locals = Set.of();
      /** Nearest function-level frame, this frame included. */
      ScopeFrame fnFrame;
      /** For an IIFE, the load-time frame it is nested in. */
      ScopeFrame outer;
      /**
       * Bug #79346 (SSL-015): the immediate lexical (brace/paren/bracket) parent, this frame's
       * own enclosing {@code openFrame} call -- unlike {@link #owner}, which flattens through
       * every transparent plain block straight to the function/IIFE/top frame that owns it. Used
       * only to find a block-scoped let/const/catch-parameter declared directly on an
       * intermediate plain block, which {@link #owner} alone cannot see.
       */
      ScopeFrame lexicalParent;
      /** Function nesting depth, the top level being 0. */
      int depth;

      boolean qualifiesAsGlobal(String qualifier) {
         return qualifier.equals("this") ? thisGlobal :
            GLOBAL_OBJECTS.contains(qualifier) || aliases.contains(qualifier);
      }

      boolean isGlobalReference(String arg) {
         return qualifiesAsGlobal(arg.trim());
      }

      /**
       * True when a bare {@code name = ...} written at {@code lexical} (the innermost block that
       * position sits in -- this frame itself when there is no intervening plain block) creates
       * or updates a global, i.e. {@code name} is not locally bound anywhere between there and
       * here.
       *
       * <p>Bug #79346 (SSL-015): first walks the LEXICAL chain -- {@code lexical}, then each of
       * its plain-block ancestors, up to and including this (the owning) frame -- checking each
       * block's own {@code locals} for a {@code let}/{@code const}/{@code catch} parameter
       * declared directly in it. This runs regardless of {@link #iife}, unlike the pre-existing
       * walk below, because a plain block (including the top level) can now record its own
       * block-scoped locals too, not only an IIFE's function-level frame.</p>
       *
       * <p>Beyond this frame, only a chain of IIFEs (load-time code) can still bind locally -- an
       * IIFE's own declared {@code var}/parameters, added to its own function-level frame. This is
       * the pre-existing behavior, unchanged.</p>
       */
      boolean bindsBareGlobal(String name, ScopeFrame lexical) {
         for(ScopeFrame f = lexical; f != null; f = f.lexicalParent) {
            if(f.locals.contains(name)) {
               return false;
            }

            if(f == this) {
               break;
            }
         }

         for(ScopeFrame f = this.outer; f != null && f.iife; f = f.outer) {
            if(f.locals.contains(name)) {
               return false;
            }
         }

         return true;
      }
   }

   private static List<String> identifiers(String text, boolean skipMembers) {
      List<String> tokens = new ArrayList<>();
      Matcher m = IDENTIFIER.matcher(text);

      while(m.find()) {
         if(skipMembers && isPlainMember(text, m.start())) {
            continue;
         }

         tokens.add(m.group());
      }

      return tokens;
   }

   /**
    * True only for {@code ident.name}, {@code call().name}, {@code arr[0].name} and their
    * {@code ?.} forms, where the qualifier is clearly not a global object. Anything else (spread
    * {@code ...name}, {@code this.name}, {@code globalThis?.name}, an unrecognised preceding
    * token) is scanned: the skip only reduces false positives, so it must fail towards scanning.
    */
   private static boolean isPlainMember(String text, int start) {
      int p = skipWhitespaceBack(text, start - 1);

      if(p < 0 || text.charAt(p) != '.') {
         return false;
      }

      p = skipWhitespaceBack(text, p - 1);

      if(p >= 0 && text.charAt(p) == '?') {
         p = skipWhitespaceBack(text, p - 1);
      }
      else if(p >= 0 && text.charAt(p) == '.') {
         return false;
      }

      if(p < 0) {
         return false;
      }

      char c = text.charAt(p);

      if(c == ')' || c == ']') {
         return true;
      }

      int end = p + 1;

      while(p >= 0 && isIdentifierPart(text.charAt(p))) {
         p--;
      }

      return p + 1 < end && !GLOBAL_OBJECTS.contains(text.substring(p + 1, end));
   }

   private static boolean isIdentifierPart(char c) {
      return Character.isLetterOrDigit(c) || c == '_' || c == '$' ||
         Character.getType(c) == Character.NON_SPACING_MARK;
   }

   private static int skipWhitespaceBack(String text, int p) {
      while(p >= 0 && Character.isWhitespace(text.charAt(p))) {
         p--;
      }

      return p;
   }

   private record DestructiveRef(String global, String function, String entry) {
      String describe() {
         if(function == null) {
            return "Script references \"" + global + "\"";
         }

         String via = "library function \"" + function + "\"" +
            (entry.equals(function) ? "" : " (entry \"" + entry + "\")");
         return "Script calls " + via + ", which references \"" + global + "\"";
      }
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
