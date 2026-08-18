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
package inetsoft.web.wiz.pairing;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Mints and validates single-use, short-TTL pairing codes binding an agent to an open runtime. */
@Service
public class SheetPairingService {
   public static final long TTL_MILLIS = 5 * 60_000L;
   public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

   /**
    * Stand-in accessor used when no real runtime lookup is available (the legacy/test
    * constructors that predate {@link EditorContext} validation). Every lookup misses, which is
    * only reachable when a caller passes a non-null {@code editorContext} naming an assembly —
    * every existing caller of those constructors passes {@code null}, so this is never exercised
    * in practice; it exists purely so those constructors do not need a real
    * {@code WorksheetService}/{@code ViewsheetService} to satisfy the field.
    */
   private static final SheetDirectAccessor NOT_CONFIGURED = runtimeId -> null;

   /**
    * The wire vocabulary an {@link EditorContext#kind()} may carry, mirroring
    * {@code ScriptTarget.Kind}'s wire names. Kept as an independent list here rather than
    * importing {@code inetsoft.web.wiz.script.ScriptTarget} -- that class already imports
    * {@code PairingException} from this package, and reaching back into it would make the two
    * packages depend on each other in both directions. An unrecognized kind is refused here, at
    * mint time, rather than left to surface later at {@code ScriptTarget.Kind.fromWire}.
    */
   private static final List<String> RECOGNIZED_KINDS = List.of(
      "viewsheetOnInit", "viewsheetOnLoad", "assemblyMain", "assemblyOnClick", "calcField",
      "worksheetExpression", "worksheetCondition");

   private final ConcurrentHashMap<String, PairingGrant> grants;
   private final SecureRandom random = new SecureRandom();
   private final LongSupplier clock;
   private final SheetDirectAccessor worksheetAccessor;
   private final SheetDirectAccessor viewsheetAccessor;

   /**
    * Production constructor — Spring injects WorksheetService (WorksheetEngine) and
    * ViewsheetService (ViewsheetEngine), both of which implement SheetDirectAccessor. Used to
    * validate an {@link EditorContext} against the real runtime at mint time.
    */
   @Autowired
   public SheetPairingService(WorksheetService worksheetService, ViewsheetService viewsheetService)
   {
      this(System::currentTimeMillis, asAccessor(worksheetService, "WorksheetService"),
           asAccessor(viewsheetService, "ViewsheetService"));
   }

   /** Back-compat convenience constructor: no editorContext validation is possible. */
   public SheetPairingService() { this(System::currentTimeMillis); }

   SheetPairingService(LongSupplier clock) {
      this(clock, NOT_CONFIGURED, NOT_CONFIGURED);
   }

   /** Test constructor: shares the grants map of an existing service with a different clock. */
   SheetPairingService(LongSupplier clock, SheetPairingService source) {
      this.clock = clock;
      this.grants = source.grants;
      this.worksheetAccessor = source.worksheetAccessor;
      this.viewsheetAccessor = source.viewsheetAccessor;
   }

   /**
    * Test constructor: accepts SheetDirectAccessor mocks directly, for tests that need to
    * control what {@link EditorContext} validation sees.
    */
   SheetPairingService(LongSupplier clock, SheetDirectAccessor worksheetAccessor,
                       SheetDirectAccessor viewsheetAccessor)
   {
      this.clock = clock;
      this.grants = new ConcurrentHashMap<>();
      this.worksheetAccessor = worksheetAccessor;
      this.viewsheetAccessor = viewsheetAccessor;
   }

   private static SheetDirectAccessor asAccessor(Object service, String name) {
      if(!(service instanceof SheetDirectAccessor accessor)) {
         throw new IllegalStateException(
            name + " (" + service.getClass().getName() + ") does not implement " +
            "SheetDirectAccessor — SheetPairingService cannot validate an editorContext " +
            "without direct runtime access.");
      }

      return accessor;
   }

   /**
    * Mints a single-use pairing code. When {@code editorContext} is non-null, validates it
    * against the open runtime <em>at mint time</em> — while the user is still looking at the
    * editor that produced it, so the browser can show a real error — rather than deferring the
    * failure to join time, where it would surface to an agent with no way back to the user.
    *
    * @throws PairingException if editorContext names an assembly (or, for {@code calcField}, a
    *                          table+field) that the runtime does not have.
    */
   public String mint(String runtimeId, String ownerIdentity, String socketSessionId,
                      String socketUserName, SheetType sheetType, EditorContext editorContext)
      throws PairingException
   {
      if(editorContext != null) {
         validateEditorContext(sheetType, runtimeId, ownerIdentity, editorContext);
      }

      String code = newCode();
      grants.put(code, new PairingGrant(code, runtimeId, ownerIdentity, socketSessionId,
                                        socketUserName, clock.getAsLong(), TTL_MILLIS, sheetType,
                                        editorContext));
      return code;
   }

   /**
    * Refuses an editorContext naming a location the runtime does not actually have. Named after
    * what was asked for, so the error is useful back in the editor that produced it.
    */
   private void validateEditorContext(SheetType sheetType, String runtimeId, String ownerIdentity,
                                      EditorContext ctx)
      throws PairingException
   {
      if(isBlank(ctx.kind())) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
                                    "editorContext.kind is required");
      }

      if(!RECOGNIZED_KINDS.contains(ctx.kind())) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "Unknown editorContext.kind: \"" + ctx.kind() + "\". Expected one of " +
            String.join(", ", RECOGNIZED_KINDS) + ".");
      }

      SheetDirectAccessor accessor =
         sheetType == SheetType.VIEWSHEET ? viewsheetAccessor : worksheetAccessor;
      RuntimeSheet rs = accessor.getSheetDirect(runtimeId);

      // Ownership check FIRST, before anything below inspects the runtime's actual content.
      // Without this, a caller could mint against a runtimeId they do not own and learn --
      // from mint's own success/failure -- whether an assembly or calc field of a given name
      // exists on someone else's open sheet. SheetJoinService's equivalent check (its step 3b)
      // runs at JOIN time, which is too late here: mint would already have leaked it via its
      // own success/failure. A null owner (runtime not resolvable on this node) is not treated
      // as a mismatch, mirroring SheetJoinService's own null-owner tolerance.
      if(rs != null) {
         Principal runtimeOwner = rs.getUser();

         if(runtimeOwner != null && !PairingUtil.sameLogicalUser(ownerIdentity, runtimeOwner)) {
            // Deliberately the same message/kind a genuinely missing runtime would produce --
            // never varies with what editorContext asked for, so it cannot become an oracle
            // for what does or doesn't exist on someone else's open sheet.
            throw new PairingException(PairingException.Kind.SESSION_EXPIRED,
               "Runtime not found or expired: " + runtimeId);
         }
      }

      if("calcField".equals(ctx.kind())) {
         // Addressed by (table, name), not by assembly -- Viewsheet.getCalcField is keyed by
         // table. The current browser wiring sends the table name in `assembly` (mirroring
         // ScriptTarget's own assemblyName() javadoc for this kind), so accept that as an alias
         // when `table` itself is absent -- the intent is unambiguous either way.
         String table = !isBlank(ctx.table()) ? ctx.table() : ctx.assembly();
         String name = ctx.name();

         if(isBlank(table) || isBlank(name)) {
            throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
               "editorContext kind 'calcField' requires 'table' (or 'assembly') and 'name'");
         }

         if(!(rs instanceof RuntimeViewsheet rvs) || rvs.getViewsheet().getCalcField(table, name) == null) {
            throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
               "Calculated field not found: " + table + "." + name);
         }

         return;
      }

      if(WORKSHEET_COLUMN_KINDS.contains(ctx.kind())) {
         validateWorksheetColumnContext(rs, ctx);
         return;
      }

      String assembly = ctx.assembly();

      if(isBlank(assembly)) {
         // Whole-viewsheet script kinds (viewsheetOnInit/viewsheetOnLoad) name no assembly.
         return;
      }

      boolean found = switch(sheetType) {
         case VIEWSHEET -> rs instanceof RuntimeViewsheet rvs && rvs.getViewsheet().getAssembly(assembly) != null;
         case WORKSHEET -> rs instanceof RuntimeWorksheet rws && rws.getWorksheet().getAssembly(assembly) != null;
      };

      if(!found) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "Assembly not found: " + assembly);
      }
   }

   /**
    * The two worksheet kinds addressed by (table, field) rather than by assembly alone --
    * exactly like {@code calcField}, and validated the same way (whole-branch review finding 3).
    *
    * <p>Before this, both fell through to the generic "does the assembly exist" branch, which
    * neither requires nor verifies {@code name}. "New expression column" opens the formula editor
    * with no {@code formulaName}, so the mint carried no name; the mint SUCCEEDED, {@code status}
    * reported a healthy pane-scoped session, and then {@code PaneScopeService.matchesGrant}
    * compared the grant's null name against every target's non-blank one and refused all of them
    * with {@code 'Query1.null'}. A false success at mint is the exact failure mode moving this
    * validation to mint time existed to prevent -- the user is still looking at the editor here
    * and can be told; an agent hitting it later cannot get back to them.
    */
   private static final List<String> WORKSHEET_COLUMN_KINDS =
      List.of("worksheetExpression", "worksheetCondition");

   /**
    * Verifies a {@code worksheetExpression}/{@code worksheetCondition} context names a column
    * that actually exists, mirroring the {@code calcField} branch against the
    * {@link TableAssembly}'s own column selection.
    *
    * <p>{@code worksheetExpression} is checked against EXPRESSION columns specifically, matching
    * on the same {@code ExpressionRef} name/attribute pair {@code WorksheetScriptService} reads
    * and writes through, so a grant cannot be minted for a location that service would then
    * refuse. {@code worksheetCondition} is checked against any column, since adding a condition
    * to a field that has none yet is legitimate.
    */
   private static void validateWorksheetColumnContext(RuntimeSheet rs, EditorContext ctx)
      throws PairingException
   {
      // Same `table`-or-`assembly` alias the calcField branch accepts, and for the same reason:
      // the browser sends the table name in `assembly`, mirroring ScriptTarget.assemblyName().
      String table = !isBlank(ctx.table()) ? ctx.table() : ctx.assembly();
      String name = ctx.name();

      if(isBlank(table) || isBlank(name)) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "editorContext kind '" + ctx.kind() + "' requires 'table' (or 'assembly') and " +
            "'name' -- a grant with no column name matches no target, so pairing here would " +
            "report success and then refuse every edit.");
      }

      Assembly a = rs instanceof RuntimeWorksheet rws && rws.getWorksheet() != null
         ? rws.getWorksheet().getAssembly(table) : null;

      if(!(a instanceof TableAssembly t)) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "Table not found: " + table);
      }

      boolean found = "worksheetExpression".equals(ctx.kind())
         ? hasExpressionColumn(t, name) : hasColumn(t, name);

      if(!found) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            ("worksheetExpression".equals(ctx.kind())
               ? "Expression column not found: " : "Column not found: ") + table + "." + name);
      }
   }

   /** Matches on the same {@code ExpressionRef} name/attribute pair the read/write side uses. */
   private static boolean hasExpressionColumn(TableAssembly t, String name) {
      ColumnSelection cs = t.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         if(cs.getAttribute(i) instanceof ColumnRef cr &&
            cr.getDataRef() instanceof ExpressionRef er &&
            (name.equals(er.getName()) || name.equals(er.getAttribute())))
         {
            return true;
         }
      }

      return false;
   }

   private static boolean hasColumn(TableAssembly t, String name) {
      ColumnSelection cs = t.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef ref = cs.getAttribute(i);

         if(name.equals(ref.getName()) || name.equals(ref.getAttribute())) {
            return true;
         }
      }

      return false;
   }

   private static boolean isBlank(String s) {
      return s == null || s.isBlank();
   }

   /** Non-destructive lookup. Returns null if absent or expired. */
   public PairingGrant peek(String code) {
      PairingGrant g = grants.get(code);
      return (g == null || g.isExpired(clock.getAsLong())) ? null : g;
   }

   /** Single-use: removes and returns the grant, or null if absent/expired. */
   public PairingGrant consume(String code) {
      PairingGrant g = grants.remove(code);
      return (g == null || g.isExpired(clock.getAsLong())) ? null : g;
   }

   @Scheduled(fixedDelay = 10 * 60_000)
   void evictExpired() {
      long now = clock.getAsLong();
      grants.values().removeIf(g -> g.isExpired(now));
   }

   private String newCode() {
      StringBuilder sb = new StringBuilder(8);
      for (int i = 0; i < 8; i++) {
         sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
      }
      return sb.toString();
   }
}
