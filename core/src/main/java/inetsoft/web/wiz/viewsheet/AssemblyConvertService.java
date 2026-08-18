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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.vs.objects.controller.*;
import inetsoft.web.composer.vs.objects.event.ConvertToFreehandTableEvent;
import inetsoft.web.composer.vs.objects.event.ConvertToRangeSliderEvent;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Changes an assembly's <b>type</b>: selection list &harr; range slider, and table or crosstab to a
 * freehand (calc) table.
 *
 * <p>Four Composer menu actions over three endpoints. Table and crosstab share one endpoint — the
 * assembly is named in the payload — and <b>both selection directions send
 * {@code ConvertToRangeSliderEvent}</b>, including range-slider&rarr;selection-list where the name
 * reads backwards. Direction lives in the endpoint, never in the payload, so a caller that infers
 * direction from the event type gets it wrong. This class maps {@code (current type, to)} onto the
 * right endpoint so that never reaches the agent.
 *
 * <p><b>Why this class validates so much before dispatching.</b> Every one of these endpoints
 * answers a wrong request with silence or a crash, never a refusal:
 *
 * <ul>
 *   <li>{@code ComposerVSTableService.convertToFreehandTable} returns {@code null} — success, having
 *       done nothing — when the target is not a {@code TableDataVSAssembly}. Point it at a chart and
 *       nothing happens, loudly reported as fine.</li>
 *   <li>Both selection converts return {@code null} on an unknown name and on an embedded assembly.
 *       The embedded refusal is a real product rule expressed only as silence.</li>
 *   <li><b>Both selection converts then crash on a standalone assembly.</b> Their guards read
 *       {@code if(!(assembly instanceof X) && !(container instanceof CurrentSelectionVSAssembly))}
 *       — {@code &&} where the body requires <i>both</i>, so an assembly of the right type passes
 *       even with no container. {@code AbstractVSAssembly.getContainer()} returns {@code null}
 *       unless the assembly sits in a Tab, GroupContainer or CurrentSelection container, the
 *       cast of {@code null} succeeds, and the next line throws {@code NullPointerException}.
 *       Inside a Tab or GroupContainer it throws {@code ClassCastException} instead.</li>
 * </ul>
 *
 * <p>That last one is latent from the UI only because the menu item is hidden unless the assembly is
 * in a selection container — a check that exists in Angular and nowhere else. <b>The Composer's
 * menus carry preconditions the server does not enforce</b>, so wrapping these endpoints means
 * porting the action's {@code visible:} conditions too, not just reading the service guards:
 * {@code inSelectionContainer} and {@code !adhocFilter} for the selection pair, and
 * {@code sourceType == VS_ASSEMBLY && metadata} for the freehand pair, which both Angular handlers
 * refuse with {@code composer.vs.table.cannotConvertToFreehand}.
 *
 * <p><b>{@code confirmed} is deliberately left false.</b> On an unconfirmed crosstab the service runs
 * {@code VSEventUtil.fixAggregateInfo}, which builds an {@code AggregateInfo} from the source when
 * the crosstab has none and then reclassifies aggregates as measures and headers as dimensions. The
 * freehand table's cells depend on that, and <b>no caller in the product ever sets {@code confirmed}
 * true</b> — both Angular handlers construct the event with only a name. Setting it true to "skip a
 * prompt" would skip the repair and build a freehand table from an empty {@code AggregateInfo}.
 *
 * <p><b>Converting a crosstab discards state, so the result says what was lost.</b> The service calls
 * {@code clearCrosstabDrill} and {@code clearDateComparison} unconditionally, and a date comparison
 * is something the agent itself may have set minutes earlier through
 * {@code set_date_comparison}. The conversion is legitimate; silent loss is not.
 */
@Service
public class AssemblyConvertService {
   public AssemblyConvertService(ViewsheetSessionService sessions,
                                 ComposerVSSelectionListService selectionListService,
                                 ComposerRangeSliderService rangeSliderService,
                                 ComposerVSTableService tableService)
   {
      this.sessions = sessions;
      this.selectionListService = selectionListService;
      this.rangeSliderService = rangeSliderService;
      this.tableService = tableService;
   }

   /**
    * The conversions this build supports, as {@code to} values, with the aliases each accepts.
    *
    * <p>Exposed so the tool layer can list them rather than hard-coding a second copy that drifts.
    */
   public Map<String, Object> vocabulary() {
      return Map.of(
         "to", List.of(
            Map.of("value", RANGE_SLIDER, "from", "selection list",
                   "aliases", List.of("rangeSlider", "range-slider", "timeSlider", "slider")),
            Map.of("value", SELECTION_LIST, "from", "range slider",
                   "aliases", List.of("selectionList", "selection-list", "list")),
            Map.of("value", FREEHAND_TABLE, "from", "table or crosstab",
                   "aliases", List.of("freehand", "calc_table", "calcTable", "freehandTable"))));
   }

   /**
    * Converts one assembly to another type.
    *
    * @param assemblyName the assembly to convert.
    * @param to           the target type — see {@link #vocabulary()}.
    *
    * @return what happened: the source and target types, anything the conversion discarded, and the
    *         staleness note. Never a bare "ok": the caller cannot see the discarded state.
    */
   public Map<String, Object> convert(String sessionToken, Principal user, String assemblyName,
                                      String to, String linkUri)
      throws Exception
   {
      if(assemblyName == null || assemblyName.isBlank()) {
         throw new IllegalArgumentException("'assembly' is required — name the assembly to convert.");
      }

      final String target = requireTarget(to);
      final Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         VSAssembly assembly = resolve(rvs, assemblyName);
         String from = describe(assembly);

         requireNotEmbedded(assembly, assemblyName);
         requireLegalPair(assembly, assemblyName, from, target);
         requireConvertibleSource(rvs, assembly, assemblyName, target);

         result.put("assembly", assemblyName);
         result.put("from", from);
         result.put("to", target);
         result.put("cleared", discardedBy(assembly, target));

         // NOTE the argument order. The two selection converts take (…, principal, dispatcher,
         // linkUri); convertToFreehandTable takes (…, principal, linkUri, dispatcher) — the last two
         // swapped. Only the differing types make that a compile error rather than a runtime one, so
         // do not "tidy" these three calls into a uniform shape.
         switch(target) {
            case RANGE_SLIDER -> selectionListService.convertToRangeSlider(
               runtimeId, nameEvent(assemblyName), user, dispatcher, linkUri);
            case SELECTION_LIST -> rangeSliderService.convertCSComponent(
               runtimeId, nameEvent(assemblyName), user, dispatcher, linkUri);
            default -> tableService.convertToFreehandTable(
               runtimeId, freehandEvent(assemblyName), user, linkUri, dispatcher);
         }
      });

      // The conversion replaces the assembly rather than editing it: a new assembly is created, the
      // old layout position copied onto it, and the old absolute name taken over. Anything holding
      // the old identity is stale.
      result.put("note",
                 "The assembly was replaced, not edited — re-read the viewsheet model before " +
                 "addressing it again. A binding-editor session paired to it is no longer valid. " +
                 "This conversion is undoable within the session.");
      return result;
   }

   /** Resolves the assembly, refusing an unknown name instead of letting the server no-op. */
   private static VSAssembly resolve(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException(
            "Unknown assembly '" + assemblyName + "'. Every convert endpoint answers an unknown " +
            "name with success and no change, so this is refused here instead.");
      }

      return assembly;
   }

   private static void requireNotEmbedded(VSAssembly assembly, String assemblyName) {
      if(((VSAssemblyInfo) assembly.getInfo()).isEmbedded()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is embedded from another viewsheet and cannot be converted. " +
            "The server declines this silently; convert it in the viewsheet that owns it.");
      }
   }

   /**
    * Enforces the pair, and the preconditions the Composer's menus enforce and the endpoints do not.
    */
   private static void requireLegalPair(VSAssembly assembly, String assemblyName, String from,
                                        String target)
   {
      switch(target) {
         case RANGE_SLIDER -> {
            if(!(assembly instanceof SelectionListVSAssembly)) {
               throw new IllegalArgumentException(
                  illegal(assemblyName, from, target, "a selection list"));
            }

            // The Composer hides this action on an ad hoc filter (selection-list-actions.ts:
            // visible: composer && !adhocFilter && inSelectionContainer).
            if(((SelectionVSAssemblyInfo) assembly.getInfo()).isAdhocFilter()) {
               throw new IllegalArgumentException(
                  "'" + assemblyName + "' is an ad hoc filter, and the Composer does not offer " +
                  "converting one to a range slider.");
            }

            requireSelectionContainer(assembly, assemblyName, target);
         }
         case SELECTION_LIST -> {
            if(!(assembly instanceof TimeSliderVSAssembly)) {
               throw new IllegalArgumentException(
                  illegal(assemblyName, from, target, "a range slider"));
            }

            requireSelectionContainer(assembly, assemblyName, target);
         }
         default -> {
            if(!(assembly instanceof TableDataVSAssembly)) {
               throw new IllegalArgumentException(
                  illegal(assemblyName, from, target, "a table or a crosstab"));
            }
         }
      }
   }

   /**
    * The {@code inSelectionContainer} precondition. Without it the server crashes rather than
    * refusing — see this class's header.
    */
   private static void requireSelectionContainer(VSAssembly assembly, String assemblyName,
                                                 String target)
   {
      if(!(assembly.getContainer() instanceof CurrentSelectionVSAssembly)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is not inside a selection container, and this conversion only " +
            "works for assemblies that are. The Composer hides the menu item in this case; the " +
            "endpoint does not check, and fails with an internal error instead of refusing.");
      }
   }

   /**
    * The {@code metadata} precondition, which lives only in Angular: both freehand handlers refuse
    * with {@code composer.vs.table.cannotConvertToFreehand} when the table is bound to another
    * assembly and the viewsheet is in metadata mode, because the layout the conversion has to
    * generate needs real data. The endpoint does not check.
    */
   private static void requireConvertibleSource(RuntimeViewsheet rvs, VSAssembly assembly,
                                                String assemblyName, String target)
   {
      if(!FREEHAND_TABLE.equals(target)) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      boolean metadata = vs != null && vs.getViewsheetInfo() != null &&
         vs.getViewsheetInfo().isMetadata();

      if(!metadata) {
         return;
      }

      SourceInfo source = assembly.getInfo() instanceof DataVSAssemblyInfo info
         ? info.getSourceInfo() : null;

      if(source != null && source.getType() == SourceInfo.VS_ASSEMBLY) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is bound to another assembly and this viewsheet is in metadata " +
            "mode, so the freehand layout cannot be generated. The Composer refuses this in the " +
            "same case; the endpoint does not check and would produce an empty layout.");
      }
   }

   /**
    * What the conversion will discard, named so the caller can see it.
    *
    * <p>Three things, not the two the design spec knew about. <b>Calculators are the third and the
    * least visible:</b> {@code convertToFreehandTable} calls {@code clearCalculators} on every
    * aggregate whose calculator is not a {@code PercentCalc} — running total, change-from-previous
    * and the rest — "because freehand does not support calculator". The service even collects their
    * names, then passes the list to {@code VSLayoutTool.syncCellFormat} for format syncing and
    * <b>never tells anyone</b>. So the numbers in the converted table are computed differently and
    * nothing says so.
    */
   private static List<String> discardedBy(VSAssembly assembly, String target) {
      if(!FREEHAND_TABLE.equals(target) || !(assembly instanceof CrosstabVSAssembly crosstab)) {
         return List.of();
      }

      List<String> cleared = new ArrayList<>();
      CrosstabVSAssemblyInfo info = crosstab.getCrosstabInfo();

      if(info != null && info.getDateComparisonInfo() != null) {
         cleared.add("date comparison");
      }

      CrosstabTree tree = crosstab.getCrosstabTree();

      if(tree != null && tree.getExpandedPaths() != null && !tree.getExpandedPaths().isEmpty()) {
         cleared.add("drill expansion");
      }

      for(String named : calculators(crosstab)) {
         cleared.add("calculator on " + named);
      }

      return cleared;
   }

   /** Mirrors {@code ComposerVSTableService.clearCalculators}' predicate without mutating anything. */
   private static List<String> calculators(CrosstabVSAssembly crosstab) {
      VSCrosstabInfo crosstabInfo = crosstab.getVSCrosstabInfo();
      DataRef[] aggregates = crosstabInfo == null ? null : crosstabInfo.getAggregates();

      if(aggregates == null) {
         return List.of();
      }

      List<String> named = new ArrayList<>();

      for(DataRef aggregate : aggregates) {
         if(aggregate instanceof VSAggregateRef ref && ref.getCalculator() != null &&
            !(ref.getCalculator() instanceof PercentCalc))
         {
            named.add(ref.getFullName());
         }
      }

      return named;
   }

   private static String illegal(String assemblyName, String from, String target, String expected) {
      return "'" + assemblyName + "' is " + from + ", so it cannot be converted to " + target +
         " — that conversion needs " + expected + ".";
   }

   /** A readable type for messages and for the result's {@code from}. */
   private static String describe(VSAssembly assembly) {
      if(assembly instanceof SelectionListVSAssembly) {
         return "a selection list";
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         return "a selection tree";
      }
      else if(assembly instanceof TimeSliderVSAssembly) {
         return "a range slider";
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         return "a crosstab";
      }
      else if(assembly instanceof CalcTableVSAssembly) {
         return "a freehand table";
      }
      else if(assembly instanceof TableVSAssembly) {
         return "a table";
      }

      return "a " + assembly.getClass().getSimpleName();
   }

   private static ConvertToRangeSliderEvent nameEvent(String assemblyName) {
      ConvertToRangeSliderEvent event = new ConvertToRangeSliderEvent();
      event.setName(assemblyName);
      return event;
   }

   /**
    * Leaves {@code confirmed} false, matching every caller in the product — see the class header for
    * why true would skip a repair rather than a prompt.
    */
   private static ConvertToFreehandTableEvent freehandEvent(String assemblyName) {
      ConvertToFreehandTableEvent event = new ConvertToFreehandTableEvent();
      event.setName(assemblyName);
      return event;
   }

   private static String requireTarget(String to) {
      String key = to == null ? "" : to.trim().toLowerCase(Locale.ROOT).replace('-', '_');
      String canonical = TARGETS.get(key);

      if(canonical == null) {
         throw new IllegalArgumentException(
            "Unknown conversion target '" + to + "'. Supported: " + RANGE_SLIDER + ", " +
            SELECTION_LIST + ", " + FREEHAND_TABLE + ".");
      }

      return canonical;
   }

   private static final String RANGE_SLIDER = "range_slider";
   private static final String SELECTION_LIST = "selection_list";
   private static final String FREEHAND_TABLE = "freehand_table";

   /**
    * Aliases, because the menu and the model disagree: the Composer says "Convert to Freehand Table"
    * while the assembly it creates is a {@code CalcTableVSAssembly}, so both names are natural.
    */
   private static final Map<String, String> TARGETS = Map.ofEntries(
      Map.entry("range_slider", RANGE_SLIDER),
      Map.entry("rangeslider", RANGE_SLIDER),
      Map.entry("timeslider", RANGE_SLIDER),
      Map.entry("time_slider", RANGE_SLIDER),
      Map.entry("slider", RANGE_SLIDER),
      Map.entry("selection_list", SELECTION_LIST),
      Map.entry("selectionlist", SELECTION_LIST),
      Map.entry("list", SELECTION_LIST),
      Map.entry("freehand_table", FREEHAND_TABLE),
      Map.entry("freehandtable", FREEHAND_TABLE),
      Map.entry("freehand", FREEHAND_TABLE),
      Map.entry("calc_table", FREEHAND_TABLE),
      Map.entry("calctable", FREEHAND_TABLE));

   private final ViewsheetSessionService sessions;
   private final ComposerVSSelectionListService selectionListService;
   private final ComposerRangeSliderService rangeSliderService;
   private final ComposerVSTableService tableService;
}
