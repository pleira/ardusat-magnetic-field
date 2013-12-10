package org.ardusat;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.orekit.attitudes.InertialProvider;
import org.orekit.errors.OrekitException;
import org.orekit.models.earth.GeoMagneticElements;
import org.orekit.models.earth.GeoMagneticField;
import org.orekit.models.earth.GeoMagneticFieldFactory;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeScalesFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import fr.cs.examples.Autoconfiguration;

/**
 * <p>This class calculates the geomagnetic field components of an earth orbiting 
 * satellite along its orbit.
 * 
 * <p>Configuration parameters must be given in the application.conf file. That
 * includes TLE orbital data for the satellite. Models are up to orbits 600 kms high. 
 *   
 * <p>For properly configuring OREKIT, you must place in {user.home}/.orekit-data 
 * the geomagnetic model files IGRF.COF and WMM.COF. 
 * See the 
 * <a href="https://www.orekit.org/forge/projects/orekit/wiki/GeomagneticFieldTutorial">GeomagneticFieldTutorial</a>
 * for more information. 
 * 
 * @author pablo.pita@gmail.com
 * @license Apache v.2
 */

public class GeomagneticFieldCalculation implements Runnable
{

	public static void main( String[] args ) throws OrekitException
	{
		Autoconfiguration.configureOrekit();
		new GeomagneticFieldCalculation().run();
	}

	// Load our own config values from the default resource location, application.conf	
	static Config conf = ConfigFactory.load();
	final double duration;
	final int timestep; 
	private String output = "geomagnetic_field.dat";
	final GeoMagneticField model;
	final TLE tle;
	final double year;

	GeomagneticFieldCalculation() throws OrekitException {
		String tle1 = conf.getString("tle.line.1");
		String tle2 = conf.getString("tle.line.2");
		tle = new TLE(tle1, tle2);
		duration = Double.parseDouble(conf.getString("duration"));
		timestep = Integer.parseInt(conf.getString("timestep"));
		output = conf.getString("output");
		DateComponents dt = tle.getDate().getComponents(TimeScalesFactory.getUTC()).getDate();
		year = GeoMagneticField.getDecimalYear(dt.getDay(), dt.getMonth(), dt.getYear());
		model = GeoMagneticFieldFactory.getWMM(year);
		System.out.println("Calculation of Geomagnetic Field" );
		System.out.println("The TLE used is:\n" + tle.toString());
		System.out.println("The duration in seconds is: " + duration);
		System.out.println("The timestep in seconds is: " + timestep);
	}

	@Override
	public void run() {
			StatesHandler stepHandler = new StatesHandler();
			try {
				List<SpacecraftState> states = calculateStates(tle, duration, timestep, stepHandler);
				save(states);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (OrekitException e) {
				e.printStackTrace();
			}
	}


	private void save(List<SpacecraftState> states) throws IOException {
		// calculate the geomagnetic field : lat, lon, alt
		// Date   Alt  Lat  Lon        X        Y         Z        H        F        I       D
		//         km  deg  deg       nT       nT        nT       nT       nT      deg     deg
        // 2010.0 100   80,   0,  6332.2,  -729.1,  52194.9,  6374.0, 52582.6,  83.04, -6.57},
        // 2012.5 100,   0, 120, 37448.1,   559.7, -11044.2, 37452.2, 39046.7, -16.43,  0.86},
		// Let's write the results in a file in order to draw some plots.
		final File file = new File(System.getProperty("user.home"), output);
		final PrintStream out = new PrintStream(file);
		//          0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890
		out.format("Date   Alt   Lat   Lon         X         Y         Z         H         F        I       D\n");
		out.format("        km   deg   deg        nT        nT        nT        nT        nT      deg     deg\n");
//		System.out.format("Date   Alt   Lat   Lon         X         Y         Z         H         F        I       D\n");
//		System.out.format("        km   deg   deg        nT        nT        nT        nT        nT      deg     deg\n");

		for (SpacecraftState state : states) {
			Orbit o = state.getOrbit();
			double alt = (o.getA() - 6378135)/1000;
			
			// How do we calculate the latitude and longitude from the satellite orbit?
			// which frames are involved in Orekit for that?
			Vector3D p = o.getPVCoordinates().getPosition();
			double lon = Math.atan (p.getY() / p.getX()) * 180 / Math.PI;
			double lat = Math.asin (p.getZ() / o.getA()) * 180 / Math.PI; 

			GeoMagneticElements elem = model.calculateField(lon, lat, alt);
			final Vector3D b = elem.getFieldVector();
			System.out.format("%6.1f %3d %5.1f %5.1f ", year, (int) alt, lat, lon);
			System.out.format("%9.2f %9.2f %9.2f %9.2f %9.2f \n",
			b.getX(), b.getY(), b.getY(),elem.getHorizontalIntensity(),elem.getTotalIntensity());
			out.format("%6.1f %3d %5.1f %5.1f ", year,(int)  alt, lat, lon);
			out.format("%9.2f %9.2f %9.2f %9.2f %9.2f ", 
					b.getX(), b.getY(), b.getY(),elem.getHorizontalIntensity(),elem.getTotalIntensity());
			out.format("%8.1f %7.1f\n", elem.getInclination(),elem.getDeclination());
		}
		out.close();
		System.out.println("Check output file: " + output);	
	}

	public static List<SpacecraftState> calculateStates(TLE tle, double duration, double timestep, StatesHandler handler) 
			throws OrekitException {
		TLEPropagator propagator = TLEPropagator.selectExtrapolator(tle, InertialProvider.EME2000_ALIGNED, 1);
		propagator.setMasterMode(timestep, handler);
		AbsoluteDate initialDate = tle.getDate();        
		AbsoluteDate endDate = initialDate.shiftedBy(duration);
		System.out.println("The tle initial date used is: " + initialDate.toString());
		System.out.println("The tle final date used is: " + endDate.toString());
		handler.init(null, initialDate);
		propagator.propagate(initialDate, endDate);
		return handler.getStates();
	}


	/** Specialized step handler.
	 * <p>This class extends the step handler in order to handle states at each step.<p>
	 */
	private static class StatesHandler implements OrekitFixedStepHandler {

		/** Points. */
		private final List<SpacecraftState> states;

		public StatesHandler() {
			// prepare an empty list of SpacecraftState
			states = new ArrayList<SpacecraftState>();
		}

		public void init(final SpacecraftState s0, final AbsoluteDate t) {
		}

		public void handleStep(SpacecraftState currentState, boolean isLast) {
			// add the current state
			states.add(currentState);
		}

		/** Get the list of handled orbital elements.
		 * @return orbital elements list
		 */
		public List<SpacecraftState> getStates() {
			return states;
		}

	}

}
