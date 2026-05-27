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
package inetsoft.web.wiz.model;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.graph.ChartInfo;

/**
 * Internal binding carrier for the primary visualization selected by
 * {@code buildPrimaryVisualization}. Holds native engine types so that
 * {@code WizVsService} can create the assembly without an intermediate
 * {@code VisualizationConfig} roundtrip.
 */
public sealed interface PrimaryBinding
   permits PrimaryBinding.ChartPrimaryBinding,
           PrimaryBinding.CrosstabPrimaryBinding,
           PrimaryBinding.TablePrimaryBinding,
           PrimaryBinding.GaugePrimaryBinding,
           PrimaryBinding.TextPrimaryBinding
{
   record ChartPrimaryBinding(ChartInfo info) implements PrimaryBinding {}
   record CrosstabPrimaryBinding(VSCrosstabInfo info) implements PrimaryBinding {}
   record TablePrimaryBinding(ColumnSelection columns, AssetEntry[] entries) implements PrimaryBinding {}
   record GaugePrimaryBinding(DataRef dataRef) implements PrimaryBinding {}
   record TextPrimaryBinding(DataRef dataRef) implements PrimaryBinding {}
}
