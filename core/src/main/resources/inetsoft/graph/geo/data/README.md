1. Download world-cities from https://simplemaps.com/data/world-cities.
2. Extract and copy worldcities.csv here.
3. Run in this directory. This will update cities csv files including cities.names.csv.
       inetsoft.tool.AppendCities worldcities.csv

If the data file is fetched from other places and have different format from simplemaps,
modify the program accordingly.

To add new column to cities database, use the inetsoft.tool.AddCityColumn as a template.
This is an one-off program and is hardcoded with current columns, and will need to be
modified depending on the new data/format.
