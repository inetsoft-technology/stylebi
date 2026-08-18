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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.internal.ClickableInputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ClickableOutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads script text/enabled-state from a joined viewsheet's onInit/onLoad, per-assembly
 * script, and onClick locations (see {@link ScriptTarget}).
 */
@Service
public class ScriptReadService {

   public ScriptReadService() {
      this(new CalcFieldService());
   }

   @Autowired
   public ScriptReadService(CalcFieldService calcFields) {
      this.calcFields = calcFields;
   }

   /** Enumerates every scriptable target on the viewsheet with its has-script/enabled state. */
   public List<ScriptTargetInfo> list(RuntimeViewsheet rvs) {
      List<ScriptTargetInfo> targets = new ArrayList<>();
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return targets;
      }

      ViewsheetInfo vsInfo = vs.getViewsheetInfo();
      boolean vsScriptEnabled = vsInfo.isScriptEnabled();
      targets.add(describe(ScriptTarget.Kind.VIEWSHEET_ON_INIT, null,
                           !isBlank(vsInfo.getOnInit()), vsScriptEnabled));
      targets.add(describe(ScriptTarget.Kind.VIEWSHEET_ON_LOAD, null,
                           !isBlank(vsInfo.getOnLoad()), vsScriptEnabled));

      for(Assembly a : vs.getAssemblies()) {
         if(!(a instanceof VSAssembly vsAssembly)) {
            continue;
         }

         VSAssemblyInfo info = vsAssembly.getVSAssemblyInfo();
         targets.add(describe(ScriptTarget.Kind.ASSEMBLY_MAIN, a.getName(),
                              !isBlank(info.getScript()), info.isScriptEnabled()));

         if(supportsOnClick(info)) {
            targets.add(describe(ScriptTarget.Kind.ASSEMBLY_ON_CLICK, a.getName(),
                                 !isBlank(getOnClick(info)), info.isScriptEnabled()));
         }
      }

      for(CalcFieldService.Found f : calcFields.list(vs)) {
         targets.add(describeCalcField(f));
      }

      return targets;
   }

   /**
    * Enumerates every scriptable target, scoped to {@code session}'s own grant.
    *
    * <p>{@code list_script_targets} must not roam: a whole-sheet session ({@code session} is
    * {@code null} or carries no {@link JoinSession#editorContext()}) sees the same full
    * enumeration {@link #list(RuntimeViewsheet)} always has, but a pane-scoped session sees only
    * the target(s) {@link PaneScopeService#matchesGrant} would let it act on — its own location,
    * plus its dialog sibling (see that method's javadoc), never the whole sheet.
    *
    * <p>Deliberately a filter over the full enumeration rather than a variant of
    * {@link PaneScopeService#check}: {@code check} refuses one caller-supplied target against a
    * session; here there is no single target to refuse, only a list to narrow. Scoping an
    * OUTPUT is a different mechanism from refusing an INPUT, even though both read the same
    * grant, so this reuses {@code matchesGrant} rather than trying to route through {@code check}.
    */
   public List<ScriptTargetInfo> list(RuntimeViewsheet rvs, JoinSession session) {
      List<ScriptTargetInfo> all = list(rvs);

      if(session == null || session.editorContext() == null) {
         return all;
      }

      EditorContext grant = session.editorContext();
      return all.stream().filter(info -> matchesGrant(grant, info)).toList();
   }

   /** Decodes {@code info}'s own id back into a {@link ScriptTarget} to test it against grant. */
   private static boolean matchesGrant(EditorContext grant, ScriptTargetInfo info) {
      try {
         return PaneScopeService.matchesGrant(grant, ScriptTarget.fromId(info.id()));
      }
      catch(PairingException ex) {
         // A target whose own id fails to decode cannot be addressed anyway; exclude it from a
         // scoped listing rather than let a malformed id leak past the filter.
         return false;
      }
   }

   /**
    * Projects one target. Enumeration decides WHICH targets exist; this decides how each is
    * described, and the two are kept apart so the taxonomy can grow without touching traversal.
    */
   private static ScriptTargetInfo describe(ScriptTarget.Kind kind, String assembly,
                                            boolean hasScript, boolean enabled)
   {
      ScriptTarget target;

      try {
         target = ScriptTarget.of(kind, assembly);
      }
      catch(PairingException ex) {
         // Unreachable: every kind passed here is Tier 1 with the assembly name its kind requires.
         throw new IllegalStateException("cannot describe " + kind + " for " + assembly, ex);
      }

      return new ScriptTargetInfo(
         target.id(), kind.wireName(), assembly,
         null,                            // name: only a calc field is keyed by one
         null,                            // sql: SQL-vs-JavaScript is a calc-field distinction
         null,                            // baseOnDetail: likewise
         label(kind, assembly), runsWhen(kind),
         hasScript, enabled, enableScope(kind, assembly), "viewsheet", target.toString());
   }

   /**
    * Projects one calc-field target. Kept apart from {@link #describe}, which every OTHER kind
    * still uses: a calc field is addressed by (table, field name) rather than (kind, assembly)
    * alone, has no legacy delimited form, and is always has-script/enabled (an expression, once
    * created, has no disabled state) -- forcing it through {@code describe}'s four-boolean shape
    * would just relitigate those differences inside a signature meant for the other four kinds.
    *
    * <p>It is also the only kind that reports {@code sql} and {@code baseOnDetail}. Both say which
    * expression LANGUAGE and which evaluation SCOPE an edit has to be written for, and a caller
    * that cannot see them has to guess from the expression text -- {@code PRICE - COST} reads as
    * either.
    */
   private static ScriptTargetInfo describeCalcField(CalcFieldService.Found f) {
      ScriptTarget target;

      try {
         target = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, f.table(), f.name());
      }
      catch(PairingException ex) {
         throw new IllegalStateException("cannot describe calc field " + f.name(), ex);
      }

      return new ScriptTargetInfo(
         target.id(), ScriptTarget.Kind.CALC_FIELD.wireName(), f.table(), f.name(),
         f.sql(), f.baseOnDetail(),
         "Calculated field '" + f.name() + "' on " + f.table(),
         "per row, when the field is evaluated",
         true,                            // a calc field always has an expression
         true,                            // no per-field enable flag exists
         "calcField:" + f.table() + ":" + f.name(),
         "viewsheet",
         null);                           // no legacy string form
   }

   private static String label(ScriptTarget.Kind kind, String assembly) {
      return switch(kind) {
         case VIEWSHEET_ON_INIT -> "Viewsheet onInit";
         case VIEWSHEET_ON_LOAD -> "Viewsheet onLoad";
         case ASSEMBLY_MAIN -> assembly + " script";
         case ASSEMBLY_ON_CLICK -> assembly + " onClick";
         default -> kind.wireName();
      };
   }

   private static String runsWhen(ScriptTarget.Kind kind) {
      return switch(kind) {
         case VIEWSHEET_ON_INIT -> "once, at viewsheet initialization";
         case VIEWSHEET_ON_LOAD -> "on every refresh";
         case ASSEMBLY_MAIN -> "each time the assembly renders";
         case ASSEMBLY_ON_CLICK -> "on user click";
         default -> "unknown";
      };
   }

   /**
    * Which enable flag {@code enabled} reflects — the footgun made legible. onInit and onLoad
    * share the viewsheet flag; an assembly's main and onClick scripts share that assembly's flag.
    */
   private static String enableScope(ScriptTarget.Kind kind, String assembly) {
      return switch(kind) {
         case VIEWSHEET_ON_INIT, VIEWSHEET_ON_LOAD -> "viewsheet";
         default -> "assembly:" + assembly;
      };
   }

   /** Reads the current text + enabled-state for {@code target}. */
   public ScriptInfo read(RuntimeViewsheet rvs, ScriptTarget target) throws PairingException {
      String text;
      boolean enabled;

      switch(target.location()) {
         case VS_INIT -> {
            ViewsheetInfo vsInfo = requireViewsheetInfo(rvs);
            text = vsInfo.getOnInit();
            enabled = vsInfo.isScriptEnabled();
         }
         case VS_LOAD -> {
            ViewsheetInfo vsInfo = requireViewsheetInfo(rvs);
            text = vsInfo.getOnLoad();
            enabled = vsInfo.isScriptEnabled();
         }
         case ASSEMBLY -> {
            VSAssemblyInfo info = requireAssemblyInfo(rvs, target.assemblyName());
            text = info.getScript();
            enabled = info.isScriptEnabled();
         }
         case ASSEMBLY_ONCLICK -> {
            VSAssemblyInfo info = requireAssemblyInfo(rvs, target.assemblyName());

            if(!supportsOnClick(info)) {
               throw new PairingException("Assembly does not support onClick: " + target.assemblyName());
            }

            text = getOnClick(info);
            enabled = info.isScriptEnabled();
         }
         case CALC_FIELD -> {
            Viewsheet vs = requireViewsheet(rvs);
            text = calcFields.read(vs, target.assemblyName(), target.name());
            enabled = true;
         }
         // A worksheet expression/condition column lives on a RuntimeWorksheet, never a
         // RuntimeViewsheet -- this method structurally cannot serve it. Named explicitly
         // (rather than left to the generic default below) so the message says where to go
         // instead of just "unsupported".
         case WORKSHEET_EXPRESSION, WORKSHEET_CONDITION, WORKSHEET_CONDITION_VALUE -> throw new PairingException(
            "'" + target.kind().wireName() + "' is a worksheet-level target; it is not readable " +
            "through the viewsheet script API. Use worksheet-chat's read_worksheet_model or " +
            "get_binding instead.");
         default -> throw new PairingException("Unsupported target: " + target);
      }

      return new ScriptInfo(target.toString(), text == null ? "" : text, enabled);
   }

   ViewsheetInfo requireViewsheetInfo(RuntimeViewsheet rvs) throws PairingException {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         throw new PairingException("Viewsheet not found in runtime");
      }

      return vs.getViewsheetInfo();
   }

   Viewsheet requireViewsheet(RuntimeViewsheet rvs) throws PairingException {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         throw new PairingException("Viewsheet not found in runtime");
      }

      return vs;
   }

   VSAssemblyInfo requireAssemblyInfo(RuntimeViewsheet rvs, String name) throws PairingException {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         throw new PairingException("Viewsheet not found in runtime");
      }

      return requireAssemblyInfo(vs, name);
   }

   static VSAssemblyInfo requireAssemblyInfo(Viewsheet vs, String name) throws PairingException {
      Assembly a = vs.getAssembly(name);

      if(!(a instanceof VSAssembly vsAssembly)) {
         throw new PairingException("Assembly not found: " + name);
      }

      return vsAssembly.getVSAssemblyInfo();
   }

   static boolean supportsOnClick(VSAssemblyInfo info) {
      return info instanceof ClickableOutputVSAssemblyInfo || info instanceof ClickableInputVSAssemblyInfo;
   }

   static String getOnClick(VSAssemblyInfo info) {
      if(info instanceof ClickableOutputVSAssemblyInfo c) {
         return c.getOnClick();
      }

      if(info instanceof ClickableInputVSAssemblyInfo c) {
         return c.getOnClick();
      }

      return null;
   }

   static void setOnClick(VSAssemblyInfo info, String text) throws PairingException {
      if(info instanceof ClickableOutputVSAssemblyInfo c) {
         c.setOnClick(text);
      }
      else if(info instanceof ClickableInputVSAssemblyInfo c) {
         c.setOnClick(text);
      }
      else {
         throw new PairingException("Assembly does not support onClick");
      }
   }

   private static boolean isBlank(String s) {
      return s == null || s.isEmpty();
   }

   private final CalcFieldService calcFields;
}
