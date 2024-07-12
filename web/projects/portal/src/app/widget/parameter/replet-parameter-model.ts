/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
export interface RepletParameterModel {
   name: string;
   alias: string;
   value: any;
   type: string;
   tooltip: string;
   multi: boolean;
   decimalType: boolean;
   required: boolean;
   changed?: boolean;
}

export interface DateFormatModel extends RepletParameterModel {
   dateFormat: string;
}

export interface ChoiceParameterModel extends RepletParameterModel {
   choicesLabel: any[];
   choicesValue: any[];
   dataTruncated: boolean;
}

export interface TextAreaParameterModel extends RepletParameterModel {
   rows: number;
   cols: number;
}

export interface ListParameterModel extends ChoiceParameterModel {
   values: any[];
}

export interface OptionParameterModel extends ChoiceParameterModel {
   selectedValues: boolean[];
}
