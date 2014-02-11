ardusat-magnetic-field
======================

Calculation of the expected geomagnetic field model along the orbit of a satellite.

The geomagnetic model used is included in <a href="https://www.orekit.org/">Orekit</a>. 
This geomagnetic model is valid for orbits up to 600 kms high. 

The satellite orbit is calculated from 
Two Line Elements series that can be obtained from the <a href="celestrak.com/NORAD/elements/">CELESTRAK</a> or <a href="https://www.space-track.org">Space Track</a> web sites.

Configuration parameters can be given in the application.conf file. 
The TLE orbital data for the satellite is given in a separate file. 

For properly configuring Orekit, you should place in {user.home}/.orekit-data 
the geomagnetic model files IGRF.COF and WMM.COF. 
See  the <a href="https://www.orekit.org/forge/projects/orekit/wiki/GeomagneticFieldTutorial">GeomagneticFieldTutorial</a>
for more information.

The data directory contains sample output generated with the code.
 
Installing
---------------

For the first try, set up the configuration to be used by placing in your home directory the resources/application.conf and the resources/ardusat.tle files.
And then, execute the <a href="http://maven.apache.org">maven</a> command:

mvn exec:java

As output comes:
<pre>
Calculation of Geomagnetic Field for 39414U
The TLEFile used is /home/user/ardusat.tle
The duration in seconds is: 6000.0
The timestep in seconds is: 20
The initial date is: 2014-02-10T12:00:00.000
The final   date is: 2014-02-10T13:40:00.000
The TLE used is:
1 39414U 98067DC  14041.36797544  .00142009  00000-0  14609-2 0  2469
2 39414 051.6481 004.1277 0006213 044.2858 088.8424 15.63876110 12805
Check in your home directory the output file: ardusat_geomagnetic_field.dat
</pre>
The data should be as the file data/ardusat_geomagnetic_field.dat. You can now tweak the configuration paths and input data as needed. Feel free to <a href="mailto:pablo.pita@gmail.com">contact</a> me for support.


