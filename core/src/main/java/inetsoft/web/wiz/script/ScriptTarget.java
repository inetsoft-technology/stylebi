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
import java.util.List;

/**
 * Parses/formats the {@code target} string the wiz-services proxy (and the MCP script tools)
 * send for every read/write/execute call.
 *
 * <p>Format: {@code "vs-init"}, {@code "vs-load"}, {@code "assembly:<name>"}, or
 * {@code "assembly:<name>:onClick"}.</p>
 */
public final class ScriptTarget {
   public enum Location { VS_INIT, VS_LOAD, ASSEMBLY, ASSEMBLY_ONCLICK }

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
      // Reserved so the schema does not churn when pane-scoped pairing lands. A session cannot be
      // scoped to one yet, so ScriptTarget.of refuses them with a scope error.
      WORKSHEET_EXPRESSION("worksheetExpression", null),
      WORKSHEET_CONDITION("worksheetCondition", null);

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
      this.kind = kind;
      this.location = kind.location();
      this.assemblyName = assemblyName;
   }

   public Location location() {
      return location;
   }

   /** The assembly name, or {@code null} for {@code VS_INIT}/{@code VS_LOAD}. */
   public String assemblyName() {
      return assemblyName;
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
      if(kind == null) {
         throw new PairingException("kind is required");
      }

      if(kind.location() == null) {
         throw new PairingException(
            "kind '" + kind.wireName() + "' requires a session paired from that expression's " +
            "editor; this session is bound to a whole viewsheet.");
      }

      boolean needsAssembly = kind == Kind.ASSEMBLY_MAIN || kind == Kind.ASSEMBLY_ON_CLICK;

      if(needsAssembly && (assemblyName == null || assemblyName.isBlank())) {
         throw new PairingException("kind '" + kind.wireName() + "' requires an assembly name.");
      }

      if(!needsAssembly && assemblyName != null && !assemblyName.isBlank()) {
         throw new PairingException(
            "kind '" + kind.wireName() + "' is viewsheet-level and takes no assembly name.");
      }

      return new ScriptTarget(kind, needsAssembly ? assemblyName : null);
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
      String canonical = kind.wireName() + "|" + (assemblyName == null ? "" : assemblyName);
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

      // Split once: the kind is a fixed vocabulary with no '|', everything after the first
      // separator is the assembly name verbatim -- which is what makes a name containing ':'
      // (or '|') addressable at all.
      int sep = canonical.indexOf('|');

      if(sep < 0) {
         throw new PairingException("Invalid target id: \"" + id + "\".");
      }

      String assembly = canonical.substring(sep + 1);
      return of(Kind.fromWire(canonical.substring(0, sep)), assembly.isEmpty() ? null : assembly);
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
    * @param assembly      the assembly name, required for kinds that need one
    * @param legacyTarget  the v1 delimited string, still accepted
    */
   public static ScriptTarget resolve(Viewsheet vs, String id, String kind, String assembly,
                                      String legacyTarget)
      throws PairingException
   {
      if(id != null && !id.isBlank()) {
         return fromId(id);
      }

      if(kind != null && !kind.isBlank()) {
         return of(Kind.fromWire(kind), assembly);
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
      };
   }

   private final Kind kind;
   private final Location location;
   private final String assemblyName;
}
