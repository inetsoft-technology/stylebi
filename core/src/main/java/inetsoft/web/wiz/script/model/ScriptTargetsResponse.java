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
package inetsoft.web.wiz.script.model;

import java.util.List;

/**
 * The {@code /targets} body.
 *
 * <p>An envelope rather than a bare array so the grammar can be advertised rather than probed —
 * "Invalid target" is indistinguishable from "no such assembly", so a probe cannot tell an old
 * server from a typo. {@code targets} stays a plain array at a stable key, which is what keeps an
 * older plugin's read path working: it forwards the body without parsing it structurally.
 */
public record ScriptTargetsResponse(int grammarVersion, List<String> supportedKinds,
                                    List<ScriptTargetInfo> targets) {}
