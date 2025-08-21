/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
export interface TreeNodeModel {
   label?: string;
   baseLabel?: string;
   organization?: string;
   alias?: string;
   data?: any;
   dataLabel?: string;
   icon?: string;
   expandedIcon?: string;
   collapsedIcon?: string;
   toggleExpandedIcon?: string;
   toggleCollapsedIcon?: string;
   leaf?: boolean;
   children?: TreeNodeModel[];
   expanded?: boolean;
   dragName?: string;
   dragData?: string;
   type?: string;
   cssClass?: string;
   disabled?: boolean;
   tooltip?: string;
   loading?: boolean;
   materialized?: boolean;
   childrenLoaded?: boolean;
   parent?: TreeNodeModel;
   treeView?: number;
   defaultOrgAsset?: boolean;
   expired?: boolean;
}
