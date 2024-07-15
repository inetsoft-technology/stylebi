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
import { Point } from "../../../common/data/point";
import { Dimension } from "../../../common/data/dimension";
import { FormatInfoModel } from "../../../common/data/format-info-model";
import { HyperlinkModel } from "../../../common/data/hyperlink-model";

export class VSAssemblyInfo {
   cls: string;
   enval: string;
   vval: string;
   enabled: boolean = true;
   visible: boolean = true;
   visible2: boolean = true;
   ovisible: boolean = true;
   fmtInfo: FormatInfoModel;
   desc: string;
   absoluteName: string;
   embedded: boolean = false;
   link: HyperlinkModel;
   pixelpos: Point;
   layoutpos: Point;
   layoutsize: Dimension;
   scalepos: Point;
   scalesize: Dimension;
   zIndex: number = 0;
   actionNames: string[] = [];
   script: string;
   scriptEnabled: boolean;
}
