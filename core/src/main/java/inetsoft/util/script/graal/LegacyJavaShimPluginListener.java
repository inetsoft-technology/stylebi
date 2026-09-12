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
package inetsoft.util.script.graal;

import inetsoft.util.PluginsChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A class that {@link LegacyJavaShim#tryLoad} cached as an unreachable name may
 * become reachable once a plugin (or a JDBC driver added the same way) is
 * installed at runtime, so the negative-lookup cache must be dropped whenever
 * the plugin set changes.
 */
@Component
public class LegacyJavaShimPluginListener {
   @EventListener(PluginsChangedEvent.class)
   public void onPluginsChanged(PluginsChangedEvent event) {
      LegacyJavaShim.invalidateNegativeCache();
   }
}
