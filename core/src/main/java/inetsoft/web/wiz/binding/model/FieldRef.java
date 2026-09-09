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
package inetsoft.web.wiz.binding.model;

import inetsoft.web.binding.model.graph.CalculateInfo;

import java.util.List;

/**
 * The agent-facing shape of one bound field, shared by every binding spec (2a–2e) and
 * embedded by highlights in spec #4.
 *
 * <p>{@code type} is the mandatory discriminator — {@code "dimension"} or {@code "measure"}.
 * A reference without it is exactly the input that gets coerced onto the wrong shelf and
 * renders plausibly wrong, so it is never defaulted.
 *
 * <p>{@code chartType} is read-only and chart-only. Under Multi Style each measure renders with its
 * own type, so that type belongs to the bound field rather than to the assembly — and it is
 * reported only where it can render: on {@code x} and {@code y}, on a measure, and only while the
 * chart is multi-style. Writing it is {@code set_chart_type}'s {@code field} argument, and an
 * inbound value here is <em>refused</em> rather than ignored — see
 * {@link inetsoft.web.wiz.binding.FieldRefFactory#requireType}.
 *
 * <p>{@code runtimeChartType} is the code that measure actually draws as, and it is present only
 * when it differs from {@code chartType} — its presence is the signal, so there is nothing to
 * compare by hand. It matters most in the case a design-time-only read serves worst: a measure left
 * at {@code auto} reports {@code auto} forever, while the runtime value is the only thing that says
 * what appeared on screen. Reported per measure rather than left to
 * {@link ChartTypeState#runtimeChartType()} because the two are maintained in opposite branches of
 * {@code AbstractChartInfo.updateChartType}, and <b>{@code multiStyles} is the flag that selects
 * between them</b> — not the chart's {@code separated} setting, despite that being the name of the
 * parameter. Its call sites pass {@code !info.isMultiStyles()}. So these per-measure values are
 * maintained exactly while multi-style is on, which is exactly when they are what renders, and the
 * assembly-level one is maintained exactly while it is off.
 *
 * @param column           the column name, as it appears in the binding tree
 * @param type             "dimension" or "measure"
 * @param aggregate        aggregate formula, measures only (e.g. "Sum", "Count")
 * @param dateLevel        date grouping level, dimensions only
 * @param namedGroup       named-group name, dimensions only — resolves against a worksheet-local
 *                         group ({@code add_named_group}) or a repository-registered predefined
 *                         one. Mutually exclusive with {@code namedGroupValues}: this names a
 *                         group that already exists, the other defines one inline.
 * @param namedGroupValues an inline, value-list-defined named group, dimensions only — the same
 *                         kind the Composer's own right-click "Group columns" builds (renders
 *                         with the {@code DataGroup(...)} axis/header label). Unlike
 *                         {@code namedGroup}, nothing has to exist beforehand: the group and its
 *                         membership are defined in this call. Has no "bucket the rest together"
 *                         option — {@link inetsoft.uql.asset.SNamedGroupInfo}, what this builds,
 *                         carries no such concept; a value not named in any group renders as its
 *                         own, ungrouped row, the same as it would with no grouping at all.
 * @param chartType        the measure's own GraphTypes code under Multi Style; null everywhere else
 * @param runtimeChartType what that measure resolved to, when it differs from {@code chartType}
 * @param calculateInfo    a per-measure Trend/Calculator (running total, change, moving, value-of,
 *                         percent, compound growth, or a custom expression), measures only —
 *                         mirrors the Composer binding pane's own Calculator button. {@code null}
 *                         leaves it unchanged; there is no separate "clear" signal because
 *                         clearing is just setting the aggregate's formula, which every caller
 *                         already does independently of this.
 */
public record FieldRef(String column, String type, String aggregate, String dateLevel,
                       String namedGroup, Integer chartType, Integer runtimeChartType,
                       NamedGroupValues namedGroupValues, CalculateInfo calculateInfo) {
   /**
    * Every caller but the chart read builds a ref with no chart type. Kept so that adding the
    * components did not touch forty-odd construction sites that have nothing to do with charts.
    */
   public FieldRef(String column, String type, String aggregate, String dateLevel,
                   String namedGroup)
   {
      this(column, type, aggregate, dateLevel, namedGroup, null, null, null, null);
   }

   /** A chart ref whose design-time type is all the read has to report. */
   public FieldRef(String column, String type, String aggregate, String dateLevel,
                   String namedGroup, Integer chartType)
   {
      this(column, type, aggregate, dateLevel, namedGroup, chartType, null, null, null);
   }

   /**
    * The full pre-existing shape (both chart-type components, no inline group) — kept so this
    * addition did not touch every call site that already named both.
    */
   public FieldRef(String column, String type, String aggregate, String dateLevel,
                   String namedGroup, Integer chartType, Integer runtimeChartType)
   {
      this(column, type, aggregate, dateLevel, namedGroup, chartType, runtimeChartType, null, null);
   }

   /**
    * The shape before {@code calculateInfo} was added — kept so that addition did not touch every
    * call site that already named {@code namedGroupValues}.
    */
   public FieldRef(String column, String type, String aggregate, String dateLevel,
                   String namedGroup, Integer chartType, Integer runtimeChartType,
                   NamedGroupValues namedGroupValues)
   {
      this(column, type, aggregate, dateLevel, namedGroup, chartType, runtimeChartType,
           namedGroupValues, null);
   }

   /**
    * An inline, value-list-defined named group — see {@code namedGroupValues} above.
    *
    * @param groups one entry per group: its name and the raw member values that belong to it.
    * @param others reserved, currently always refused when present — {@link #groups} carries
    *               everything {@link inetsoft.uql.asset.SNamedGroupInfo} can hold; kept as its
    *               own key (rather than silently accepted and dropped) so a caller who assumes
    *               this mirrors a calc-table cell's inline named group — which does support
    *               grouping the leftovers — is told why that assumption does not carry over here,
    *               instead of the option quietly doing nothing.
    */
   public record NamedGroupValues(List<Clause> groups, String others) {
      /** One named group's membership: {@code name} plus the raw values that belong to it. */
      public record Clause(String name, List<Object> values) {}
   }
}
