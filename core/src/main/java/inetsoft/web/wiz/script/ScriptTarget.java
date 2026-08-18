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

import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.pairing.PairingException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Parses/formats the {@code target} string the wiz-services proxy (and the MCP script tools)
 * send for every read/write/execute call.
 *
 * <p>Format: {@code "vs-init"}, {@code "vs-load"}, {@code "assembly:<name>"}, or
 * {@code "assembly:<name>:onClick"}.</p>
 */
public final class ScriptTarget {
   public enum Location {
      VS_INIT, VS_LOAD, ASSEMBLY, ASSEMBLY_ONCLICK, CALC_FIELD,
      WORKSHEET_EXPRESSION, WORKSHEET_CONDITION
   }

   /**
    * The wire vocabulary. Distinct from {@link Location}, which is the internal dispatch key every
    * read/write/execute service already switches on: a wire rename must not ripple into five
    * services, and two of these kinds have no Location at all until pane-scoped pairing lands.
    */
   public enum Kind {
      VIEWSHEET_ON_INIT("viewsheetOnInit", Location.VS_INIT),
      VIEWSHEET_ON_LOAD("viewsheetOnLoad", Location.VS_LOAD),
      ASSEMBLY_MAIN("assemblyMain", Location.ASSEMBLY),
      ASSEMBLY_ON_CLICK("assemblyOnClick", Location.ASSEMBLY_ONCLICK),

      /**
       * A viewsheet calculated field's expression.
       *
       * <p>Addressed by (table, field name), not by assembly — {@code Viewsheet.getCalcFields}
       * is keyed by table. So for THIS kind {@code assemblyName()} carries the TABLE name and
       * {@code name()} carries the field's own name. That asymmetry is deliberate: a fourth
       * addressing component, or renaming {@code assembly} to {@code owner} across all five
       * kinds, would be a larger change than the one thing this kind actually needs.
       */
      CALC_FIELD("calcField", Location.CALC_FIELD),

      /**
       * A worksheet expression column's formula (G2 Task 8). Addressed the same way as
       * {@link #CALC_FIELD} and for the same reason: {@code assemblyName()} carries the owning
       * TABLE, {@code name()} carries the column's own name. Servable only through
       * {@code WorksheetScriptService}, which routes onto {@code WorksheetAgentController}'s
       * existing {@code edit_expression} op rather than writing the worksheet directly.
       */
      WORKSHEET_EXPRESSION("worksheetExpression", Location.WORKSHEET_EXPRESSION),

      /**
       * A worksheet column's filter condition (G2 Task 8). Addressed by (table, field) exactly
       * like {@link #WORKSHEET_EXPRESSION}/{@link #CALC_FIELD} -- see those for the asymmetry
       * this implies for {@code assemblyName()}/{@code name()}. Servable only through
       * {@code WorksheetScriptService}, routed onto {@code WorksheetAgentController}'s existing
       * {@code edit_condition} op.
       */
      WORKSHEET_CONDITION("worksheetCondition", Location.WORKSHEET_CONDITION);

      Kind(String wireName, Location location) {
         this.wireName = wireName;
         this.location = location;
      }

      public String wireName() {
         return wireName;
      }

      /** The internal dispatch key, or {@code null} for a kind no service can serve yet. */
      public Location location() {
         return location;
      }

      /**
       * Whether an action addressed to this kind may run only from a session paired at that
       * expression's own script pane or formula editor -- never from a whole-sheet ("Connect to
       * Claude" toolbar) session, regardless of the {@code wiz.agent.script.require-script-pane}
       * strict posture (see {@link PaneScopeService}, which enforces this).
       *
       * <p>Deliberately NOT {@code location() == null}. Before G2 Task 8, {@code WORKSHEET_EXPRESSION}/
       * {@code WORKSHEET_CONDITION} had no {@code Location} at all and needed this to be
       * {@code true} anyway; giving them a real {@code Location} (so they become servable, see
       * {@code WorksheetScriptService}) would have silently flipped this to {@code false} the
       * moment {@code location()} stopped being null -- exactly the kind of change that reports
       * success while quietly inverting a security-relevant rule, since a calc field and a
       * worksheet expression/condition column have no name or identity at the whole-sheet level
       * regardless of whether a service can dispatch on their location.
       *
       * <p>Instead this is an explicit classification of the {@link Location}, in
       * {@link #isExpressionLevel}, which a new expression-level {@code Location} must be added
       * to on purpose. Still derived from {@code kind} in one place (not a hand-maintained
       * parallel list of {@code Kind} constants) -- the derivation just keys on the property that
       * actually matters ("does this kind address something with sheet-level identity"), not on
       * whether a service happens to be wired up yet.
       */
      public boolean requiresPaneSession() {
         return location == null || isExpressionLevel(location);
      }

      /**
       * Whether {@code location} addresses something with no name or identity outside its own
       * expression editor.
       *
       * <p>Deliberately an exhaustive {@code switch} <b>with no {@code default}</b>: adding a
       * {@link Location} for a kind the spec already names (highlight / cell / dynamicValue) and
       * forgetting to classify it is then a COMPILE error, not a silent {@code false} that flips
       * that kind out of pane-scope enforcement with the whole suite still green. That silent-flip
       * shape is exactly what the previous {@code EnumSet} membership test permitted -- a set
       * literal has no way to notice a constant nobody put in it. The companion
       * {@code PaneScopeServiceTest#everyKindIsClassifiedByExactlyOneGuard} covers the other half
       * (a new {@link Kind} mapped onto an EXISTING location, which the compiler cannot see).
       */
      private static boolean isExpressionLevel(Location location) {
         return switch(location) {
            case CALC_FIELD, WORKSHEET_EXPRESSION, WORKSHEET_CONDITION -> true;
            case VS_INIT, VS_LOAD, ASSEMBLY, ASSEMBLY_ONCLICK -> false;
         };
      }

      public static Kind fromWire(String wire) throws PairingException {
         if(wire == null || wire.isBlank()) {
            throw new PairingException("kind is required");
         }

         // Named explicitly rather than aliased: there is no onRefresh in viewsheet scripting, and
         // silently mapping it to onLoad would teach the caller a location that does not exist.
         if("onRefresh".equals(wire)) {
            throw new PairingException(
               "There is no 'onRefresh' script in StyleBI. 'viewsheetOnLoad' is the script that " +
               "runs on every refresh; 'viewsheetOnInit' runs once at initialization.");
         }

         for(Kind k : values()) {
            if(k.wireName.equals(wire)) {
               return k;
            }
         }

         throw new PairingException("Unknown kind: \"" + wire + "\". Expected one of " +
                                    String.join(", ", wireNames()) + ".");
      }

      public static List<String> wireNames() {
         return Arrays.stream(values()).map(Kind::wireName).toList();
      }

      private final String wireName;
      private final Location location;
   }

   private ScriptTarget(Kind kind, String assemblyName) {
      this(kind, assemblyName, null);
   }

   private ScriptTarget(Kind kind, String assemblyName, String name) {
      this.kind = kind;
      this.location = kind.location();
      this.assemblyName = assemblyName;
      this.name = name;
   }

   public Location location() {
      return location;
   }

   /**
    * The assembly name, or {@code null} for {@code VS_INIT}/{@code VS_LOAD}.
    *
    * <p>For {@link Kind#CALC_FIELD}, this carries the TABLE the field belongs to, not an
    * assembly — see the javadoc on that constant.
    */
   public String assemblyName() {
      return assemblyName;
   }

   /** The calc field's own name, or {@code null} for every kind that needs no third component. */
   public String name() {
      return name;
   }

   public Kind kind() {
      return kind;
   }

   /**
    * Builds a target from the canonical tuple.
    *
    * @throws PairingException if the kind has no servable location, or an assembly kind carries no
    *                          assembly name.
    */
   public static ScriptTarget of(Kind kind, String assemblyName) throws PairingException {
      return of(kind, assemblyName, null);
   }

   /**
    * Builds a target from the canonical tuple, with the third component {@link Kind#CALC_FIELD}
    * needs.
    *
    * @throws PairingException if the kind has no servable location, an assembly kind carries no
    *                          assembly name, {@code CALC_FIELD} is missing its table or field
    *                          name, or a non-{@code CALC_FIELD} kind is given a name.
    */
   public static ScriptTarget of(Kind kind, String assemblyName, String name)
      throws PairingException
   {
      if(kind == null) {
         throw new PairingException("kind is required");
      }

      if(kind.location() == null) {
         throw new PairingException(
            "kind '" + kind.wireName() + "' requires a session paired from that expression's " +
            "editor; this session is bound to a whole viewsheet.");
      }

      if(COLUMN_ADDRESSED_KINDS.contains(kind)) {
         if(assemblyName == null || assemblyName.isBlank()) {
            throw new PairingException(
               "kind '" + kind.wireName() + "' requires the table the field belongs to, in " +
               "'assembly'.");
         }

         if(name == null || name.isBlank()) {
            throw new PairingException(
               "kind '" + kind.wireName() + "' requires the field's 'name'.");
         }

         return new ScriptTarget(kind, assemblyName, name);
      }

      if(name != null && !name.isBlank()) {
         throw new PairingException(
            "kind '" + kind.wireName() + "' takes no 'name'; only " +
            String.join(", ", COLUMN_ADDRESSED_KINDS.stream().map(Kind::wireName).sorted().toList()) +
            " are addressed by one.");
      }

      boolean needsAssembly = kind == Kind.ASSEMBLY_MAIN || kind == Kind.ASSEMBLY_ON_CLICK;

      if(needsAssembly && (assemblyName == null || assemblyName.isBlank())) {
         throw new PairingException("kind '" + kind.wireName() + "' requires an assembly name.");
      }

      if(!needsAssembly && assemblyName != null && !assemblyName.isBlank()) {
         throw new PairingException(
            "kind '" + kind.wireName() + "' is viewsheet-level and takes no assembly name.");
      }

      return new ScriptTarget(kind, needsAssembly ? assemblyName : null, null);
   }

   /**
    * A stable, opaque, URL-safe id for this target.
    *
    * <p>A deterministic encoding of the canonical tuple, NOT a server-issued handle: the pairing
    * store is in-memory, so an id that only resolved inside one session would strand every
    * workflow that lists targets in one turn and edits in the next. Decodable, which keeps it
    * readable in logs and errors.
    */
   public String id() {
      String canonical = kind.wireName() + "|" + escape(assemblyName) + "|" + escape(name);
      return Base64.getUrlEncoder().withoutPadding()
         .encodeToString(canonical.getBytes(StandardCharsets.UTF_8));
   }

   public static ScriptTarget fromId(String id) throws PairingException {
      if(id == null || id.isBlank()) {
         throw new PairingException("id is required");
      }

      String canonical;

      try {
         canonical = new String(Base64.getUrlDecoder().decode(id), StandardCharsets.UTF_8);
      }
      catch(IllegalArgumentException ex) {
         throw new PairingException("Invalid target id: \"" + id + "\".");
      }

      // The kind is a fixed vocabulary containing no '|' or '\', so the first UNESCAPED separator
      // ends it. Assembly and name are arbitrary strings that may themselves contain '|' -- each
      // is escaped ('\' -> '\\', '|' -> '\|') before encoding, so decoding scans for the next
      // separator NOT preceded by an escape, rather than splitting blindly on every '|' or trusting
      // the last '|' in the string to be the true boundary. (A naive "first separator ends the
      // kind, last separator begins the name" split is NOT sufficient on its own: when the table
      // name and the field name both contain a literal '|', the last '|' in the string can land
      // inside the field name rather than at the true assembly/name boundary -- escaping is what
      // actually makes every component position-independent.)
      int firstSep = indexOfUnescapedSeparator(canonical, 0);

      if(firstSep < 0) {
         throw new PairingException("Invalid target id: \"" + id + "\".");
      }

      Kind kind = Kind.fromWire(canonical.substring(0, firstSep));
      int secondSep = indexOfUnescapedSeparator(canonical, firstSep + 1);

      if(secondSep < 0) {
         throw new PairingException("Invalid target id: \"" + id + "\".");
      }

      String assembly = unescape(canonical.substring(firstSep + 1, secondSep), id);
      String name = unescape(canonical.substring(secondSep + 1), id);

      return of(kind, assembly.isEmpty() ? null : assembly, name.isEmpty() ? null : name);
   }

   /** Escapes '\' and '|' so a literal '|' inside a component is never mistaken for a separator. */
   private static String escape(String s) {
      if(s == null || s.isEmpty()) {
         return "";
      }

      StringBuilder sb = new StringBuilder(s.length());

      for(int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);

         if(c == '\\' || c == '|') {
            sb.append('\\');
         }

         sb.append(c);
      }

      return sb.toString();
   }

   /**
    * Reverses {@link #escape}. A trailing unpaired {@code '\'} cannot come from anything
    * {@code escape} produced -- it always emits {@code '\'} in a pair with the character it
    * guards -- so one showing up here means the id is corrupted or was hand-crafted, and is
    * refused rather than silently kept as a literal backslash.
    */
   private static String unescape(String s, String id) throws PairingException {
      StringBuilder sb = new StringBuilder(s.length());

      for(int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);

         if(c == '\\') {
            if(i + 1 >= s.length()) {
               throw new PairingException(
                  "Invalid target id: \"" + id + "\" (dangling escape character).");
            }

            sb.append(s.charAt(++i));
         }
         else {
            sb.append(c);
         }
      }

      return sb.toString();
   }

   /**
    * Index of the next {@code '|'} at or after {@code from} that is not preceded by an unpaired
    * {@code '\'} escape, or {@code -1} if none.
    */
   private static int indexOfUnescapedSeparator(String s, int from) {
      boolean escaped = false;

      for(int i = from; i < s.length(); i++) {
         char c = s.charAt(i);

         if(escaped) {
            escaped = false;
         }
         else if(c == '\\') {
            escaped = true;
         }
         else if(c == '|') {
            return i;
         }
      }

      return -1;
   }

   public static ScriptTarget parse(String target) throws PairingException {
      if(target == null || target.isBlank()) {
         throw new PairingException("target is required");
      }

      if("vs-init".equals(target)) {
         return new ScriptTarget(Kind.VIEWSHEET_ON_INIT, null);
      }

      if("vs-load".equals(target)) {
         return new ScriptTarget(Kind.VIEWSHEET_ON_LOAD, null);
      }

      if(target.startsWith("assembly:")) {
         String rest = target.substring("assembly:".length());

         if(rest.endsWith(":onClick")) {
            String name = rest.substring(0, rest.length() - ":onClick".length());

            if(name.isBlank()) {
               throw new PairingException("Invalid target: " + target);
            }

            return new ScriptTarget(Kind.ASSEMBLY_ON_CLICK, name);
         }

         if(rest.isBlank()) {
            throw new PairingException("Invalid target: " + target);
         }

         return new ScriptTarget(Kind.ASSEMBLY_MAIN, rest);
      }

      throw new PairingException("Invalid target: \"" + target +
         "\". Expected \"vs-init\", \"vs-load\", \"assembly:<name>\", or \"assembly:<name>:onClick\".");
   }

   /**
    * The v1 string parse, but able to tell an assembly literally named {@code Foo:onClick} from
    * the onClick script of an assembly named {@code Foo}. An exact assembly match always wins.
    *
    * <p>{@link #parse(String)} cannot do this — it is static with no viewsheet in hand — which is
    * exactly why the delimited grammar is being retired.
    */
   public static ScriptTarget parse(Viewsheet vs, String target) throws PairingException {
      if(vs != null && target != null && target.startsWith("assembly:")) {
         String rest = target.substring("assembly:".length());

         if(!rest.isBlank() && vs.getAssembly(rest) != null) {
            return of(Kind.ASSEMBLY_MAIN, rest);
         }
      }

      return parse(target);
   }

   /**
    * Resolves whichever dialect the caller sent into one canonical target.
    *
    * <p>Three dialects, one precedence rule, in one place: six endpoints accept all three, and six
    * copies of this would be six chances for them to disagree about which wins.
    *
    * @param vs            the joined viewsheet, for the exact-name precedence fix; may be null
    * @param id            preferred for an existing target — the caller copies it back verbatim
    * @param kind          the wire kind name, with {@code assembly} when the kind needs one
    * @param assembly      the assembly name, required for kinds that need one; the TABLE name for
    *                      {@code calcField}
    * @param name          the calc field's own name; required for (and only meaningful to)
    *                      {@code calcField}
    * @param legacyTarget  the v1 delimited string, still accepted
    */
   public static ScriptTarget resolve(Viewsheet vs, String id, String kind, String assembly,
                                      String name, String legacyTarget)
      throws PairingException
   {
      if(id != null && !id.isBlank()) {
         return fromId(id);
      }

      if(kind != null && !kind.isBlank()) {
         return of(Kind.fromWire(kind), assembly, name);
      }

      if(legacyTarget != null && !legacyTarget.isBlank()) {
         return parse(vs, legacyTarget);
      }

      throw new PairingException(
         "A script target is required: pass 'id' (from list_script_targets), or 'kind' with " +
         "'assembly' where the kind needs one, or the legacy 'target' string.");
   }

   @Override
   public String toString() {
      return switch(location) {
         case VS_INIT -> "vs-init";
         case VS_LOAD -> "vs-load";
         case ASSEMBLY -> "assembly:" + assemblyName;
         case ASSEMBLY_ONCLICK -> "assembly:" + assemblyName + ":onClick";
         // A calc field has no legacy PARSEABLE form -- parse(String) does not (and must not)
         // recognize this -- the v1 grammar never addressed one, and coining a parseable
         // "calc:Query1:Margin" would recreate the delimiter ambiguity this design removed. This
         // is a display string only, e.g. for error messages built by callers that string-concat
         // a target (see ScriptReadService's "Unsupported target: " + target on an unwired
         // location) -- it must not throw, or every such message breaks instead of reporting
         // the thing it was trying to report.
         case CALC_FIELD -> "calcField:" + assemblyName + ":" + name;
         // Same reasoning as CALC_FIELD immediately above: no legacy parseable form, display
         // string only.
         case WORKSHEET_EXPRESSION -> "worksheetExpression:" + assemblyName + ":" + name;
         case WORKSHEET_CONDITION -> "worksheetCondition:" + assemblyName + ":" + name;
      };
   }

   /**
    * Kinds addressed by (table, field) rather than by assembly alone — see {@link Kind#CALC_FIELD}'s
    * javadoc for why. Centralized so {@link #of} has one place to check membership rather than an
    * {@code ||} chain that a future column-addressed kind could be added to incompletely.
    */
   private static final Set<Kind> COLUMN_ADDRESSED_KINDS =
      EnumSet.of(Kind.CALC_FIELD, Kind.WORKSHEET_EXPRESSION, Kind.WORKSHEET_CONDITION);

   private final Kind kind;
   private final Location location;
   private final String assemblyName;
   private final String name;
}
