/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.wiz.docs;

import java.io.IOException;

/**
 * One outbound JSON POST to the AI assistant server.
 *
 * <p>Exists as an interface so {@link WizDocSearchController} — which owns every decision about
 * status mapping and configuration — can be unit-tested without opening a socket. The HTTP
 * implementation is verified by the live smoke test.</p>
 */
public interface AssistantDocSearchGateway {
   /** The assistant's raw reply. A null body is normalised to "" by implementations. */
   record Response(int status, String body) {}

   /**
    * @param authorization the caller's Authorization header, forwarded verbatim; may be null.
    * @throws IOException when the assistant produced no response at all (refused connection,
    *                     DNS failure, timeout) — as opposed to responding with an error status.
    */
   Response post(String baseUrl, String path, String body, String authorization)
      throws IOException, InterruptedException;
}
