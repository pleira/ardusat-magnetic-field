ardusat-magnetic-field
======================

Calculation of the expected geomagnetic field model along the orbit of a satellite.

The geomagnetic model used is included in Orekit. The satellite orbit is calculated from 
Two Line Elements that can be obtained from the <a href="celestrak.com/NORAD/elements/">CELESTRAK</a>
web site.

Configuration parameters must be given in the application.conf file. That
includes TLE orbital data for the satellite. Models are up to orbits 600 kms high. 

For properly configuring OREKIT, you must place in {user.home}/.orekit-data 
the geomagnetic model files IGRF.COF and WMM.COF. 
See  the <a href="https://www.orekit.org/forge/projects/orekit/wiki/GeomagneticFieldTutorial">GeomagneticFieldTutorial</a>
for more information.
 
