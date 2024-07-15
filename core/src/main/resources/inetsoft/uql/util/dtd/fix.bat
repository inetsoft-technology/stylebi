@REM
@REM inetsoft-core - StyleBI is a business intelligence web application.
@REM Copyright © 2024 InetSoft Technology (info@inetsoft.com)
@REM
@REM This program is free software: you can redistribute it and/or modify
@REM it under the terms of the GNU General Public License as published by
@REM the Free Software Foundation, either version 3 of the License, or
@REM (at your option) any later version.
@REM
@REM This program is distributed in the hope that it will be useful,
@REM but WITHOUT ANY WARRANTY; without even the implied warranty of
@REM MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
@REM GNU General Public License for more details.
@REM
@REM You should have received a copy of the GNU General Public License
@REM along with this program. If not, see <http://www.gnu.org/licenses/>.
@REM

del j
copy header.txt+ASCII_CharStream.java j
copy j ASCII_ChartStream.java
del j
copy header.txt+ParseException.java j
copy j ParseException.java
del j
copy header.txt+Token.java j
copy j Token.java
del j
copy header.txt+TokenMgrError.java j
copy j TokenMgrError.java
