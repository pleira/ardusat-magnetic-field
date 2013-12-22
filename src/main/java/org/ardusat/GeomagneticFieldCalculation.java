package org.ardusat;

import java.io.File;
import java.io.FileInputStream;
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
import org.orekit.propagation.analytical.tle.TLESeries;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.DateTimeComponents;
import org.orekit.time.TimeScalesFactory;
import org.orekit.time.UTCScale;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * <p>This class calculates the geomagnetic field components of an earth orbiting 
 * satellite along its orbit.
 * 
 * <p>Configuration parameters must be given in the application.conf file. 
 * The TLE orbital data for the satellite is given apart. 
 * 
 * Geomagnetic Models are valid for orbits up to 600 kms high. 
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

	public static void main( String[] args ) throws Exception
	{
		new GeomagneticFieldCalculation().run();
	}

	// Load our own config values from the default resource location, application.conf	
	final double duration;
	final int timestep; 
	final String output;
	final String outDir;
	final String input;
	final String inDir;
	final String name;
	final GeoMagneticField model;
	final TLESeries tles;
	final TLE tle;
	final double year;
	final Config conf = ConfigFactory.load();
	final AbsoluteDate startDate;
	final AbsoluteDate endDate;
	
	GeomagneticFieldCalculation() throws Exception {
		name   = conf.hasPath("sat.code.name") ? conf.getString("sat.code.name") : "39412U";
		outDir = conf.hasPath("output.dir") ? conf.getString("output.dir") : System.getProperty("user.home");
		
		output = conf.hasPath("output.file") ? conf.getString("output.file") : "ardusat_geomagnetic_field.dat";
		inDir = conf.hasPath("input.tle.dir") ? conf.getString("input.tle.dir") : System.getProperty("user.home");
		input = conf.hasPath("input.tle.file") ? conf.getString("input.tle.file") : "ardusat.tle";
		String orekitData = conf.hasPath("orekit") ?  conf.getString("orekit") : System.getProperty("user.home") + "/.orekit-data";
		DataProvider provider = new DirectoryCrawler(new File(orekitData));
		DataProvidersManager.getInstance().addProvider(provider);
		
        tles = new TLESeries(null, true); // pattern matching with .tle and ignore non tle lines
        FileInputStream stream = new FileInputStream(new File(inDir, input));
        tles.loadData(stream, name);			
		duration = conf.hasPath("duration") ? conf.getDouble("duration") : 6000;
		timestep = conf.hasPath("timestep") ? conf.getInt("timestep") : 20;
		final String start = conf.hasPath("start") ? conf.getString("start") : "2012-12-20T12:00:00";
		final UTCScale utc = TimeScalesFactory.getUTC();
		startDate = new AbsoluteDate(start, utc);
		endDate = startDate.shiftedBy(duration);
		tle = tles.getClosestTLE(startDate);
		final DateTimeComponents dtc = startDate.getComponents(utc);
		final DateComponents dt = dtc.getDate();
		year = GeoMagneticField.getDecimalYear(dt.getDay(), dt.getMonth(), dt.getYear());
		model = GeoMagneticFieldFactory.getWMM(year);
		System.out.println("Calculation of Geomagnetic Field for " + name);
		System.out.println("The TLEFile used is " + inDir + "/" + input);
		System.out.println("The duration in seconds is: " + duration);
		System.out.println("The timestep in seconds is: " + timestep);
		System.out.println("The initial date is: " + startDate.toString());
		System.out.println("The final   date is: " + endDate.toString());
		System.out.println("The TLE used is:\n" + tle.toString());
		
	}

	@Override
	public void run() {
		StatesHandler stepHandler = new StatesHandler();
		try {
			List<SpacecraftState> states = calculateStates(tle, startDate, endDate, timestep, stepHandler);
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
	 * We give a position in ITRF, then we use ITRF as the second argument to transform coordinates with 
	 * respect the Earth Ellipsoid. 
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

	public static List<SpacecraftState> calculateStates(TLE tle, AbsoluteDate initialDate, AbsoluteDate endDate, double timestep, StatesHandler handler) 
			throws OrekitException {
		TLEPropagator propagator = TLEPropagator.selectExtrapolator(tle, InertialProvider.EME2000_ALIGNED, 1);
		propagator.setMasterMode(timestep, handler);
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
