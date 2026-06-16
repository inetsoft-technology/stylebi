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
package inetsoft.web.wiz.script;

/**
 * Request body shared across script write, enable, dry-run, and live-execute endpoints.
 *
 * <p>All fields are optional at the wire level; each endpoint validates and uses only
 * the fields it needs:</p>
 * <ul>
 *   <li>{@code write} — requires {@code target} and {@code text}</li>
 *   <li>{@code enable} — requires {@code target} and {@code enabled}</li>
 *   <li>{@code execute} (dry-run) — requires {@code target}</li>
 *   <li>{@code execute-live} — requires {@code target}; {@code confirmed} bypasses the
 *       destructive-globals guardrail when {@code true}</li>
 * </ul>
 *
 * @param target    canonical script target string, e.g. {@code "vs-init"},
 *                  {@code "assembly:Chart1"}, {@code "assembly:Button1:onClick"}
 * @param text      script text to write; {@code null} clears the script
 * @param enabled   assembly script enabled flag; used only by the enable endpoint
 * @param confirmed when {@code true}, bypasses the destructive-globals confirmation
 *                  guardrail in the live-execute endpoint
 */
public record ScriptEditRequest(String target, String text, Boolean enabled, Boolean confirmed) {}
