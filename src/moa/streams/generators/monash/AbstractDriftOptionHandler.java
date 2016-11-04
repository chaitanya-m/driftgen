package moa.streams.generators.monash;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;

public class AbstractDriftOptionHandler extends DriftOptionHandler {



	public FloatOption driftMagnitudeConditional = new FloatOption("driftMagnitudeConditional",
			'o',
			"Magnitude of the drift between the starting probability and the one after the drift."
					+ " Magnitude is expressed as the Hellinger or Total Variation distance [0,1]", 0.5, 1e-20, 0.9);

	public FlagOption driftPriors = new FlagOption("driftPriors", 'p',
			"States if the drift should apply to the prior distribution p(x). ");

	public FlagOption driftConditional = new FlagOption("driftConditional", 'c',
			"States if the drift should apply to the conditional distribution p(y|x).");

}
