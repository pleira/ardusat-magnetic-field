ardusat-magnetic-field
======================

Calculation of the expected geomagnetic field model along the orbit of a satellite.

The geomagnetic model used is included in <a href="https://www.orekit.org/">Orekit</a>. 
The satellite orbit is calculated from 
Two Line Elements that can be obtained from the <a href="celestrak.com/NORAD/elements/">CELESTRAK</a>
web site.

Configuration parameters can be given in the application.conf file. That
includes TLE orbital data for the satellite. Models are up to orbits 600 kms high. 

For properly configuring Orekit, you should place in {user.home}/.orekit-data 
the geomagnetic model files IGRF.COF and WMM.COF. 
See  the <a href="https://www.orekit.org/forge/projects/orekit/wiki/GeomagneticFieldTutorial">GeomagneticFieldTutorial</a>
for more information.

The data directory contains sample output generated with the code.
 
To try, run 

mvn exec:java

As output comes:
<pre>
Calculation of Geomagnetic Field for 39412U
The TLEFile used is /home/user/ardusat.tle
The duration in seconds is: 6000.0
The timestep in seconds is: 20
The initial date is: 2012-12-20T12:00:00.000
The final   date is: 2012-12-20T13:40:00.000
The TLE used is:
1 39412U 98067DA  13332.31589257  .00048279  00000-0  78876-3 0   328
2 39412 051.6512 015.0820 0004249 059.7435 037.7484 15.52056926  1352
Check in your home directory the output file: ardusat_geomagnetic_field.dat
</pre>


