ardusat-magnetic-field
======================

Calculation of the expected geomagnetic field model along the orbit of a satellite.

To try, run 

mvn exec:java

As output comes:
<pre>
Calculation of Geomagnetic Field
The TLE used is:
1 39412U 98067DA  13343.12890038  .00068429  00000-0  10624-2 0   630
2 39412  51.6509 321.3059 0005304  96.9471 359.5554 15.53317866  3035
The duration in seconds is: 6000.0
The timestep in seconds is: 20
The tle initial date used is: 2013-12-09T03:05:36.993
The tle final date used is: 2013-12-09T04:45:36.993
Check in your home directory the output file: ardusat_geomagnetic_field.dat
</pre>


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
 
