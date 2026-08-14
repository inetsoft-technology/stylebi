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
package inetsoft.web.wiz.viewsheet;

import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

/**
 * Applies assembly-level formatting through the Composer's own format service.
 *
 * <p>{@code VSObjectFormatInfoModel} is CSS-shaped — {@code color}, {@code backgroundColor},
 * {@code font}, {@code align}, {@code format}/{@code formatSpec}, and the four border sides —
 * so it passes straight through without an alias layer.
 */
@Service
public class ViewsheetFormatService {
   @Autowired
   public ViewsheetFormatService(ViewsheetSessionService sessions, FormatPainterService painter) {
      this.sessions = sessions;
      this.painter = painter;
   }

   /**
    * @param assemblies the assemblies to format; at least one
    * @param format     the format to apply; may be null only when {@code reset} is true
    * @param reset      clear formatting back to the default rather than applying {@code format}
    */
   public record FormatRequest(List<String> assemblies,
                               VSObjectFormatInfoModel format,
                               boolean reset) {}

   public void setFormat(String sessionToken, Principal user, FormatRequest request,
                         String linkUri) throws Exception
   {
      if(request.assemblies() == null || request.assemblies().isEmpty()) {
         throw new IllegalArgumentException(
            "set_format requires 'assemblies' with at least one assembly name.");
      }

      if(request.format() == null && !request.reset()) {
         throw new IllegalArgumentException(
            "set_format requires 'format' unless 'reset' is true.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         FormatVSObjectEvent event = new FormatVSObjectEvent();
         event.setObjects(request.assemblies().toArray(new String[0]));
         event.setFormat(request.format());
         event.setReset(request.reset());
         painter.setFormat(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   private final ViewsheetSessionService sessions;
   private final FormatPainterService painter;
}
