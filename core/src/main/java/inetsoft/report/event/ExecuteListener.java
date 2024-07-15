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
package inetsoft.report.event;

/**
 * Execute listener. It can be registered with a ReportSheet to be
 * invoked when the report queries are processed. The listener can
 * perform additional data retrieval and manipulation.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface ExecuteListener extends java.util.EventListener {
   /**
    * Invoked when a report is executed.
    */
   public void execute(ExecuteEvent e);
}

