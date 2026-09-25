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
/**
 * Command to open an asset in composer
 */
export interface OpenComposerAssetCommand {
   assetId: string;
   folderId: string;
   viewsheet: boolean;
   wsWizard: boolean;
   baseDataSource?: string;
   baseDataSourceType?: number;
   parentId?: string;
   runtimeId?: string;
   /**
    * Whether an agent session is already attached to runtimeId when this command is sent -- set
    * by open_base_worksheet/create_viewsheet/create_worksheet so a freshly-opened tab's
    * agent-connected indicator is correct immediately, instead of racing a separate
    * SetAgentActiveCommand push against this command establishing the tab's own subscription.
    */
   agentActive?: boolean;
   /** Display label of the agent's owner, when agentActive is true. */
   agentOwnerLabel?: string;
}