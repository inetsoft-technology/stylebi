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

/**
 * A chart's type and the three flags that decide what that type means.
 *
 * <p>Nothing in the agent surface reported any of this: not the binding read, not the aesthetics
 * read, not the property tools, and not {@code get_assembly_properties(raw)} — the documented
 * escape hatch for a property with no short name, whose dialog model carries no chart type at all.
 * So {@code set_chart_type}'s own advice, to read the binding again afterwards because retyping
 * changes which shelves are meaningful, could not be acted on.
 *
 * <p><b>{@code multiStyles} decides where the authoritative type lives.</b> With it off, the type
 * is the assembly's — {@code chartType} here. With it on, StyleBI renders each measure with its own
 * type, held on the {@code x} and {@code y} aggregates and reported by the binding read; the
 * assembly-level value is then a default rather than what any measure necessarily draws as. The
 * write side is the same split: {@code ChangeChartTypeEvent.ref} names one aggregate, and
 * {@code ChangeChartTypeProcessor} sets that ref's type instead of the assembly's — searching x and
 * y only, which is why a per-measure type exists on those two shelves and nowhere else.
 *
 * <p>{@code runtimeChartType} is reported alongside {@code chartType} rather than instead of it. A
 * read that returns only the stored value is how a caller ends up shown what it wrote while the
 * renderer used something else — {@code ChangeChartTypeProcessor} clears runtime types on a
 * per-ref write for exactly that reason, so the two genuinely diverge.
 *
 * <p>The types are the raw {@code GraphTypes} codes. Naming them is the plugin's job: the
 * code↔name vocabulary already exists in three copies on this side
 * ({@code WizAutoBindingService} twice, {@code WizVsService} once) and a fourth would be one more
 * to drift, while the plugin already owns the one facing the agent and already echoes names back
 * from {@code set_chart_type}.
 *
 * @param assembly         the chart's name
 * @param chartType        the assembly-level GraphTypes code
 * @param runtimeChartType the code the last render resolved to
 * @param multiStyles      whether each measure carries its own type
 * @param separated        separated rather than merged graphs
 * @param stackMeasures    whether measures are stacked
 */
public record ChartTypeState(String assembly, int chartType, int runtimeChartType,
                             boolean multiStyles, boolean separated, boolean stackMeasures) {}
