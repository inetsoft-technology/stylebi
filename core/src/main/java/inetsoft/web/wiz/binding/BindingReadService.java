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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAestheticModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.AssemblyBinding;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an assembly's binding into the shared {@link FieldRef} vocabulary.
 *
 * <p>A pure read: {@code VSBindingService.createModel} is a function of the assembly, so no
 * dispatcher, checkpoint, or broadcast is involved.
 */
@Service
public class BindingReadService {
   @Autowired
   public BindingReadService(VSBindingService binding) {
      this.binding = binding;
   }

   public AssemblyBinding read(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      // A calc table's binding is cell-structured and lives in its layout, not its binding
      // model — CalcTableBindingModel adds no fields at all. See spec 2e.
      if(assembly instanceof CalcTableVSAssembly) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a calc table. Its binding lives in its cell layout, " +
            "not its binding model — read it with get_calc_layout instead.");
      }

      BindingModel model = binding.createModel(assembly);
      Map<String, List<FieldRef>> shelves = new LinkedHashMap<>();
      Map<String, Object> sorts = new LinkedHashMap<>();
      List<FieldRef> dateComparisonSeries = null;

      if(model instanceof ChartBindingModel chart) {
         // Under Multi Style each measure renders with its own type, so x and y carry it per field.
         // Gated on three things, not one: the shelf, because ChangeChartTypeProcessor's per-ref
         // write searches getXFieldCount()/getYFieldCount() and nothing else, so an aggregate on
         // `group` or on a single-field shelf has no such setting however willingly the model
         // answers for it; the ref being a measure, since ChartDimensionRefModel has no chart type
         // at all; and multi-style being on, because otherwise the value is inert and reporting it
         // would describe something that cannot render — the shape of this lane's inert-frame
         // finding.
         boolean perField = chart.isMultiStyles();

         shelves.put("x", refs(chart.getXFields(), perField));
         shelves.put("y", refs(chart.getYFields(), perField));
         shelves.put("group", refs(chart.getGroupFields()));

         // A map's geo dimension lives on its own list, separate from x/y/group — surface it
         // so a caller doesn't mistake x/y (lat/lon measures on a map) for "nothing is bound".
         if(GraphTypes.isGeo(chart.getChartType()) || !chart.getGeoFields().isEmpty()) {
            shelves.put("geo", refs(chart.getGeoFields()));
         }

         // describeSorts already disambiguates a column bound twice on the SAME shelf (its own
         // "[index]" suffix), but nothing disambiguated the same bare column name appearing on
         // two DIFFERENT shelves (e.g. a date column grouped by month on x and by year on group)
         // -- putAll-ing each shelf's map in turn let a later shelf's entry silently clobber an
         // earlier one sharing the same key, so a write to one shelf's sort could be invisible
         // here even though the chart correctly rendered it (Bug #76689, VCS-012). Collect every
         // shelf's map first, then requalify by shelf -- mirroring describeSorts' own
         // bracket-suffix convention -- only for a key produced by more than one shelf, so the
         // ordinary non-colliding case still reports the plain bare column name unchanged.
         Map<String, Map<String, Object>> sortsByShelf = new LinkedHashMap<>();
         Map<String, Long> keyShelfCount = new LinkedHashMap<>();

         for(String shelf : ChartBindingMutator.SHELVES) {
            Map<String, Object> described = ChartBindingMutator.describeSorts(chart, shelf);
            sortsByShelf.put(shelf, described);

            for(String key : described.keySet()) {
               keyShelfCount.merge(key, 1L, Long::sum);
            }
         }

         for(Map.Entry<String, Map<String, Object>> shelfEntry : sortsByShelf.entrySet()) {
            String shelf = shelfEntry.getKey();

            for(Map.Entry<String, Object> e : shelfEntry.getValue().entrySet()) {
               String key = e.getKey();
               boolean collidesAcrossShelves = keyShelfCount.get(key) > 1;
               sorts.put(collidesAcrossShelves ? key + " [" + shelf + "]" : key, e.getValue());
            }
         }

         // The ten single-field shelves. Left out of this read until now, so a
         // set_chart_single_shelf write could not be read back at all: four OHLC shelves bound and
         // visibly rendering while this reported x/y/group and nothing else.
         //
         // Reported only where something is bound, unlike x/y/group above. Those three are
         // present-and-empty because they are meaningful on every chart; these ten are not, and
         // listing `milestone` on a pie chart would advertise a shelf that chart cannot use — the
         // same fabricated-capability shape as the phantom Dimensions column in
         // BindableFieldsService.isColumn and the phantom y2 axis in ChartRegionResolver.
         for(String shelf : ChartBindingMutator.SINGLE_SHELVES) {
            ChartRefModel ref = ChartBindingMutator.readSingleShelf(chart, shelf);

            if(ref != null) {
               shelves.put(shelf, refs(List.of(ref)));
            }
         }

         if(assembly instanceof ChartVSAssembly chartAssembly) {
            dateComparisonSeries = dateComparisonSeries(chartAssembly.getVSChartInfo());
         }
      }
      else if(model instanceof CrosstabBindingModel crosstab) {
         shelves.put("rows", refs(crosstab.getRows()));
         shelves.put("cols", refs(crosstab.getCols()));
         shelves.put("aggregates", refs(crosstab.getAggregates()));
      }
      else if(model instanceof TableBindingModel table) {
         shelves.put("groups", refs(table.getGroups()));
         shelves.put("details", refs(table.getDetails()));
         shelves.put("aggregates", refs(table.getAggregates()));
      }

      return new AssemblyBinding(assemblyName,
                                 assembly.getClass().getSimpleName(),
                                 model == null || model.getSource() == null
                                    ? null : model.getSource().getSource(),
                                 shelves, sorts, dateComparisonSeries);
   }

   /**
    * The series an applied date comparison renders that the design-time shelves never show
    * (Bug #77015, DCG-013 b). {@code ChartDcProcessor.updateAggregatesCalc} works on the runtime
    * x/y fields only: it first clears the calculator on every runtime aggregate, then sets the
    * comparison's own calculator -- on a clone appended to the axis for changeAndValue/
    * percentChangeAndValue, or on the original runtime ref for change-only. So while a comparison
    * is applied, a runtime aggregate carrying a calculator is exactly a comparison series, and
    * nothing else can be.
    *
    * <p>Built from a clone because the model constructor ({@code BAggregateRefModel.init}) may
    * write a calculator back onto the ref it is given; this is a pure read of the live runtime
    * fields the next render reads too.
    *
    * @return the series, or {@code null} when no comparison is applied or it adds none
    */
   private static List<FieldRef> dateComparisonSeries(VSChartInfo info) {
      if(info == null || !info.isAppliedDateComparison()) {
         return null;
      }

      List<FieldRef> series = new ArrayList<>();
      addDateComparisonSeries(series, info, info.getRTXFields());
      addDateComparisonSeries(series, info, info.getRTYFields());
      return series.isEmpty() ? null : series;
   }

   private static void addDateComparisonSeries(List<FieldRef> series, VSChartInfo info,
                                               ChartRef[] fields)
   {
      if(fields == null) {
         return;
      }

      for(ChartRef field : fields) {
         if(field instanceof ChartAggregateRef aggregate && aggregate.getCalculator() != null) {
            ChartAggregateRef copy = (ChartAggregateRef) aggregate.clone();
            series.add(FieldRefFactory.from(new ChartAggregateRefModel(copy, info)));
         }
      }
   }

   private static List<FieldRef> refs(List<? extends DataRefModel> models) {
      return refs(models, false);
   }

   private static List<FieldRef> refs(List<? extends DataRefModel> models, boolean withChartType) {
      List<FieldRef> refs = new ArrayList<>();

      if(models != null) {
         for(DataRefModel model : models) {
            FieldRef ref = FieldRefFactory.from(model);

            refs.add(withChartType && model instanceof ChartAestheticModel aesthetic
                        ? withTypes(ref, aesthetic)
                        : ref);
         }
      }

      return refs;
   }

   /**
    * Copies a measure's own chart type onto its ref, plus the runtime type where that says
    * something the stored one does not.
    *
    * <p>Divergence-only, matching {@code ChartTypeState}: reported on every field it would be
    * noise, reported never and a measure left at {@code auto} would answer {@code auto} while
    * drawing something else. These are the runtime types that survive where it matters —
    * {@code AbstractChartInfo.updateChartType} maintains the assembly-level value only while
    * multi-style is <em>off</em> and delegates to {@code updateFieldChartTypes} while it is on, so
    * the per-measure ones are available exactly when per-measure types are what render. Its
    * parameter is named {@code separated} and its javadoc describes it backwards; the call sites
    * are what settle it, and {@link inetsoft.web.wiz.binding.model.ChartTypeState} records them so
    * this does not have to be re-derived twice.
    *
    * <p>{@code CHART_AUTO} is withheld for the reason it is withheld at the assembly level: a
    * render resolves to something concrete, so {@code auto} in the runtime slot is the unset
    * default rather than an answer. It has to be excluded <em>here</em> as well, because
    * {@code updateFieldChartTypes} does not reach every measure — it runs only when the last x or y
    * field is a measure, walks only the trailing run of aggregates on that shelf (it {@code break}s
    * at the first non-aggregate), and {@code updateChartType} returns early altogether when no
    * runtime x/y fields are populated. A measure missed by any of those keeps {@code 0}, and
    * reporting that against a stored {@code bar} would announce a render as {@code auto} that never
    * happened — the stale-read shape this pair of records exists to close, one level down.
    *
    * <p>Nor is there a second source to fall back on, though the model-building code looks like
    * there is: {@code ChartAestheticService.loadVisualFrames} sets the runtime type from
    * {@code bindable.getRTChartType()} and then overrides it from {@code getAggregateRtType}, which
    * returns an empty map unless the chart has an applied date comparison. Outside that case the
    * design-time ref is the only maintainer of this value, so the guard above carries the whole
    * weight rather than backing up a second opinion.
    */
   private static FieldRef withTypes(FieldRef ref, ChartAestheticModel aesthetic) {
      int stored = aesthetic.getChartType();
      int runtime = aesthetic.getRTChartType();
      boolean resolved = runtime != stored && runtime != GraphTypes.CHART_AUTO;

      // The 9-arg FieldRef constructor below predates `label`/`secondaryY` (see its own javadoc:
      // "kept so those additions did not touch every call site that already named
      // calculateInfo") and silently defaults BOTH to null -- this call site was never updated
      // when secondaryY was added, so any y/x measure on a MULTI-STYLE chart (the only case this
      // method is invoked for at all) lost its secondaryY on every read, even though the actual
      // ChartAggregateRef/render correctly carried it. Bug #76689, VCS-004. Call the canonical
      // 11-component constructor directly and thread both fields through from the original ref
      // instead.
      return new FieldRef(ref.column(), ref.type(), ref.aggregate(), ref.dateLevel(),
                          ref.namedGroup(), stored, resolved ? runtime : null,
                          ref.namedGroupValues(), ref.calculateInfo(), ref.label(),
                          ref.secondaryY());
   }

   private final VSBindingService binding;
}
