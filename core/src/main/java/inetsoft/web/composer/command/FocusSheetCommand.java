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
package inetsoft.web.composer.command;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * Composer-level command: an agent just edited the sheet runtime {@link #runtimeId()}, so a
 * Composer whose "Follow Focus" checkbox is ticked may switch to that sheet's tab.
 *
 * <p>Delivered on {@code /user/composer-client}, like {@link OpenComposerAssetCommand}, to the
 * browser that owns the runtime. It is only a hint: the browser decides whether to act on it
 * (checkbox, modal open, editing mode, tab already focused, sheet not open in this window) and
 * never opens a tab because of it.
 *
 * <p>Deliberately not {@link OpenComposerAssetCommand}: that command also adds the asset to the
 * recently-viewed list on every edit, forces the tab's agent-active indicator on, matches the tab
 * by asset id rather than runtime id, and has no browser-side gate.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableFocusSheetCommand.class)
public abstract class FocusSheetCommand {
   /**
    * Discriminator the browser uses to tell this command from an
    * {@link OpenComposerAssetCommand} on the shared composer-client topic.
    */
   public static final String TYPE = "focusSheet";

   @Value.Default
   public String type() {
      return TYPE;
   }

   /** The runtime id of the sheet the agent edited. */
   public abstract String runtimeId();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableFocusSheetCommand.Builder {
   }
}
