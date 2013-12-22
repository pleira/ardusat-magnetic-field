ardusat-magnetic-field
======================

Calculation of the expected geomagnetic field model along the orbit of a satellite.

The geomagnetic model used is included in <a href="https://www.orekit.org/">Orekit</a>. 

Models are up to orbits 600 kms high. 

The satellite orbit is calculated from 
Two Line Elements series that can be obtained from the <a href="celestrak.com/NORAD/elements/">CELESTRAK</a> or <a href="https://www.space-track.org">Space Track</a> web sites.

Configuration parameters can be given in the application.conf file. 
The TLE orbital data for the satellite is given in a separate file. 

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
The initial date is: 2013-12-20T12:00:00.000
The final   date is: 2013-12-20T13:40:00.000
The TLE used is:
1 39412U 98067DA  13354.31648267  .00083988  00000-0  12110-2 0   952
2 39412 051.6499 265.5298 0006520 145.9879 311.6308 15.55250916  4777
Check in your home directory the output file: ardusat_geomagnetic_field.dat
</pre>


