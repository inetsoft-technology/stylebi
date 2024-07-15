/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
export enum RepositoryEntryType {
   FOLDER = 0x01,
   REPLET = 0x02,
   TRASHCAN = 0x10,
   TRASHCAN_FOLDER = FOLDER | TRASHCAN,
   FILE = 0x20,
   VIEWSHEET = 0x40,
   WORKSHEET = 0x100,
   WORKSHEET_FOLDER = 0x200 | FOLDER,
   RECYCLEBIN_FOLDER = 0x400 << 3 | FOLDER,
   ALL = FOLDER | TRASHCAN | FILE | VIEWSHEET | WORKSHEET | WORKSHEET_FOLDER,
   DATA_SOURCE = 0x400,
   DATA_SOURCE_FOLDER = (DATA_SOURCE << 1) | FOLDER,
   LIBRARY_FOLDER = (DATA_SOURCE << 2) | FOLDER,
   BEAN = 0x4000,
   META_TEMPLATE = BEAN << 1,
   PARAMETER_SHEET = META_TEMPLATE << 1,
   SCRIPT = PARAMETER_SHEET << 1,
   TABLE_STYLE = SCRIPT << 1,
   REPOSITORY = TABLE_STYLE << 1,
   REPOSITORY_FOLDER = REPOSITORY | FOLDER,
   USER = REPOSITORY << 1,
   USER_FOLDER = USER | FOLDER,
   PROTOTYPE = USER << 1,
   PROTOTYPE_FOLDER = PROTOTYPE | FOLDER,
   QUERY = PROTOTYPE << 1,
   LOGIC_MODEL = QUERY << 1,
   PARTITION = LOGIC_MODEL << 1,
   VPM = PARTITION << 1,
   DASHBOARD = VPM << 1,
   CUBE = DASHBOARD << 1,
   DATA_MODEL = CUBE << 1,
   DASHBOARD_FOLDER = DASHBOARD | FOLDER,
   USER_FOLDERS = 0x01,
   LIVE_REPORTS = 0x02,
   SNAPSHOTS = 0x04,
   VIEWSHEETS = 0x10,
   WORKSHEETS = 0x20,
   AUTO_SAVE_FILE = 0x20000000,
   AUTO_SAVE_VS = AUTO_SAVE_FILE | VIEWSHEET,
   AUTO_SAVE_WS = AUTO_SAVE_FILE | WORKSHEET,
   AUTO_SAVE_FOLDER = AUTO_SAVE_FILE | FOLDER,
   VS_AUTO_SAVE_FOLDER = AUTO_SAVE_VS | FOLDER,
   WS_AUTO_SAVE_FOLDER = AUTO_SAVE_WS | FOLDER,
   SCHEDULE_TASK = AUTO_SAVE_FILE << 1,
   SCHEDULE_TASK_FOLDER = SCHEDULE_TASK | FOLDER,
   ALL_FILTERS = USER_FOLDERS | LIVE_REPORTS | SNAPSHOTS | VIEWSHEETS | WORKSHEETS,
   DATA_MODEL_FOLDER = DATA_MODEL | FOLDER
}