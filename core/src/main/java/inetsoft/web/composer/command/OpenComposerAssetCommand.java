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
package inetsoft.web.composer.command;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableOpenComposerAssetCommand.class)
public abstract class OpenComposerAssetCommand {
   @Nullable
   public abstract String assetId();

   @Nullable
   public abstract String folderId();

   @Nullable
   public abstract String baseDataSource();

   public abstract boolean viewsheet();

   @Value.Default
   public boolean wsWizard() {
      return false;
   }

   @Nullable
   public abstract String parentId();

   @Value.Default
   public int baseDataSourceType() {
      return -1;
   }

   /**
    * The server-opened runtime id to attach to, or {@code null} for the normal flow where the
    * browser opens its own runtime. Set when an agent tool (e.g. {@code open_base_worksheet})
    * already opened the runtime server-side -- the browser must attach to that runtime instead
    * of opening a second one of the same asset.
    */
   @Nullable
   public abstract String runtimeId();

   /**
    * Whether an agent session is already attached to {@link #runtimeId()} at the moment this
    * command is sent, so the newly-opened tab's agent-connected indicator is correct from the
    * start.
    *
    * <p>Deliberately carried here rather than left to a separate {@code SetAgentActiveCommand}
    * push: that command rides the per-runtime {@code CommandDispatcher.COMMANDS_TOPIC}, which the
    * browser only has a subscriber for once it has actually opened {@link #runtimeId()} -- exactly
    * what THIS command is what causes to happen. A tool that mints a brand-new runtime server-side
    * (open_base_worksheet/create_viewsheet/create_worksheet) and pushes
    * {@code SetAgentActiveCommand} first, then this command second, sends the indicator update to a
    * channel nobody is listening to yet; it is not merely a narrow race, since the browser cannot
    * possibly be subscribed before it has processed this very command. Carrying the state here
    * instead means the tab that opens already knows.
    */
   @Value.Default
   public boolean agentActive() {
      return false;
   }

   /** The agent's identity, when {@link #agentActive()} is true; unused otherwise. */
   @Nullable
   public abstract String agentOwnerIdentity();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableOpenComposerAssetCommand.Builder {
   }
}
