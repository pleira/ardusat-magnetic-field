package org.ardusat;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;
import org.orekit.attitudes.InertialProvider;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataProvider;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.errors.OrekitException;
import org.orekit.frames.FactoryManagedFrame;
import org.orekit.frames.FramesFactory;
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
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

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
 * 
 * @author pablo.pita@gmail.com
 * @license Apache v.2
 */

public class GeomagneticFieldCalculation implements Runnable
{

	public static void main( String[] args ) throws OrekitException
	{
		new GeomagneticFieldCalculation().run();
	}

	// Load our own config values from the default resource location, application.conf	
	final double duration;
	final int timestep; 
	final String output;
	final String outDir;
	final GeoMagneticField model;
	final TLE tle;
	final double year;

	GeomagneticFieldCalculation() throws OrekitException {
		Config conf = ConfigFactory.load();
		outDir = conf.hasPath("output.dir") ? conf.getString("output.dir") : System.getProperty("user.home");
		output = conf.hasPath("output.file") ? conf.getString("output.file") : "ardusat_geomagnetic_field.dat";
		String orekitData = conf.hasPath("orekit") ?  conf.getString("orekit") : System.getProperty("user.home") + "/.orekit-data";
		DataProvider provider = new DirectoryCrawler(new File(orekitData));
		DataProvidersManager.getInstance().addProvider(provider);
		if (conf.hasPath("tle.line.1") && conf.hasPath("tle.line.2")) {
			tle = new TLE(conf.getString("tle.line.1"), conf.getString("tle.line.2"));
		} else {
			// 39412U object related to the ISS for the date 2013-12-09
			tle = new TLE("1 39412U 98067DA  13343.12890038  .00068429  00000-0  10624-2 0   630",
					"2 39412  51.6509 321.3059 0005304  96.9471 359.5554 15.53317866  3035");
		}
		duration = conf.hasPath("duration") ? conf.getDouble("duration") : 6000;
		timestep = conf.hasPath("timestep") ? conf.getInt("timestep") : 20;
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

	/**
	 * Computes altitude, latitude and longitude
	 *  
	 * The TLE propagator does not propagate orbits in the EME2000 frame but rather in the TEME frame, 
	 * which is a strange frame used only by TLE. Due to precession, the TEME frame is not fixed 
	 * with respect to EME2000.
	 * 
	 * The OneAxisEllipsoid object should not be set up with an inertially oriented frame like the EME2000, 
	 * but should should be set up with a frame that represents Earth rotation 
	 * (i.e. which is fixed with respect to Earth), like ITRF. 
	 * 
	 * We give a position in ITRF, then we use ITRF as the second argument. 
	 * You cannot mix. The reason for that is that if you give a position in first argument, 
	 * you specify in the second argument in which frame you did provide the first argument, 
	 * then the object which remembers the rotating frame in which it was built will do the transform 
	 * if it need to. 
	 * So if you decided to already provides the position in ITRF *and* passed ITRF as the second argument, 
	 * then the object will notice it is already the same frame it was built with and it will not transform 
	 * the cartesian position before computing the angle.
	 *  
	 * If on the other hand you provide a position in EME2000 *and* pass EME2000 as the second argument, 
	 * then the object will notice EME2000 is not the same as the ITRF frame it was built with, 
	 * so it will first perform the transformation to convert the EME2000 position into ITRF 
	 * and then compute the angles. 
	 */
	private void save(List<SpacecraftState> states) throws IOException, OrekitException {
		final File file = new File(outDir, output);
		final PrintStream out = new PrintStream(file);
		printOutputHeader(out);
		// printOutputHeader(System.out);
		
		final FactoryManagedFrame ITRF = FramesFactory.getITRF(IERSConventions.IERS_2010, true); // tidal effects ignored
		final OneAxisEllipsoid OAE = new OneAxisEllipsoid(
				Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
				Constants.WGS84_EARTH_FLATTENING,
				ITRF);
		for (SpacecraftState state : states) {
			Orbit o = state.getOrbit();
			Vector3D p = o.getPVCoordinates().getPosition();			
			GeodeticPoint gp = OAE.transform(p, ITRF, o.getDate());
			double alt = gp.getAltitude() / 1000;
			double lat = FastMath.toDegrees(gp.getLatitude());
			double lon = FastMath.toDegrees(gp.getLongitude());
			GeoMagneticElements elem = model.calculateField(lon, lat, alt);
			final Vector3D b = elem.getFieldVector();
			// printOutput(System.out, alt, lat, lon, b, elem);
			printOutput(out, alt, lat, lon, b, elem);
		}
		out.close();
		System.out.println("Check in your home directory the output file: " + output);	
	}

	private void printOutputHeader(PrintStream stream) {
		stream.format("Date   Alt   Lat   Lon         X         Y         Z         H         F        I       D\n");
		stream.format("        km   deg   deg        nT        nT        nT        nT        nT      deg     deg\n");	
	}
	
	private void printOutput(PrintStream stream, double alt, double lat, double lon, Vector3D b, GeoMagneticElements elem) {
		String fmt1 = "%6.1f %3d %5.1f %5.1f ";
		String fmt2 = "%9.2f %9.2f %9.2f %9.2f %9.2f ";
		String fmt3 = "%8.1f %7.1f";
		stream.format(fmt1, year,(int)  alt, lat, lon);
		stream.format(fmt2, 
				b.getX(), b.getY(), b.getY(),elem.getHorizontalIntensity(),elem.getTotalIntensity());
		stream.format(fmt3 + "\n", elem.getInclination(),elem.getDeclination());		
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
