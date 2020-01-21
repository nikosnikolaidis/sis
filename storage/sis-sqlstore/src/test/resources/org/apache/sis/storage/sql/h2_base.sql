-- A sample database to test spatial operations over database.
-- This is designed to work over orbisgis:H2GIS database.
-- Inspired by official example at https://github.com/orbisgis/orbisgis-samples/blob/master/demoh2gis/src/main/java/org/orbisgis/demoh2gis/Main.java

CREATE TABLE ROADS (geometry LINESTRING, speed_limit INT) CHECK ST_SRID(geometry)=4326;

INSERT INTO ROADS VALUES (ST_GeomFromText('LINESTRING(15 5, 20 6, 25 7)', 4326), 80)
INSERT INTO ROADS VALUES (ST_GeomFromText('LINESTRING(20 6, 21 15, 21 25)', 4326), 50)
INSERT INTO ROADS VALUES (ST_GeomFromText('LINESTRING(34 12, 36 13, 35.2 16)', 4326), 70)
