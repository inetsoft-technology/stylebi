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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The body of a worksheet {@code update_script} answer when the write was NOT a plain edit of an
 * existing column: a pending session (Create Expression) never writes the worksheet, it pushes a
 * draft into the user's open dialog.
 *
 * @param ok      always {@code true} (a failure is an error response instead)
 * @param pending {@code true} when the text was pushed to the open dialog as a draft; nothing
 *                exists until the user clicks OK
 * @param note    what happened and what the agent can and cannot know about it
 * @param warning set when the draft did not pass the JavaScript syntax check; it may still be a
 *                valid SQL expression, which the server cannot tell
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScriptWriteResult(boolean ok, Boolean pending, String note, String warning) {}
