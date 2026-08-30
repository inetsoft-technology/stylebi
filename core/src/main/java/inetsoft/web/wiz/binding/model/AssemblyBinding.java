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

import java.util.List;
import java.util.Map;

/**
 * An assembly's current binding, keyed by shelf name.
 *
 * <p>Chart shelves are {@code x}, {@code y}, {@code group}; crosstab are {@code rows},
 * {@code cols}, {@code aggregates}; table are {@code groups}, {@code details},
 * {@code aggregates}. Calc tables are not represented here — their binding lives in the cell
 * layout, per spec 2e.
 *
 * <p>A chart additionally carries whichever of the ten single-field shelves
 * ({@code open}, {@code high}, {@code low}, {@code close}, {@code path}, {@code source},
 * {@code target}, {@code start}, {@code end}, {@code milestone}) hold a field. Those keys are
 * present only when bound, unlike the three list shelves above which are always present: they are
 * meaningful on every chart, whereas {@code milestone} on a pie chart is not, and listing it would
 * advertise a shelf that chart cannot use.
 *
 * <p>{@code sorts} carries per-dimension sort/ranking, keyed by column (or {@code "column [i]"}
 * when the same column is bound more than once on the same shelf) — currently populated for a
 * chart's x/y/group shelves only, in the vocabulary {@code DimensionSortRanking.describe}
 * produces. Empty for a table or crosstab, whose own sort/ranking is reported by the richer
 * {@code table/binding} read instead.
 */
public record AssemblyBinding(String assembly, String objectType, String source,
                              Map<String, List<FieldRef>> shelves,
                              Map<String, Object> sorts) {}
