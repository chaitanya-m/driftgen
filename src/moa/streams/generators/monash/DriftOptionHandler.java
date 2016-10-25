package moa.streams.generators.monash;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.github.javacliparser.MultiChoiceOption;


import moa.core.ObjectRepository;
import moa.options.AbstractOptionHandler;
import moa.streams.filters.StreamFilter;
import moa.tasks.TaskMonitor;
/**
 * An option handler for drift generators.
 */
public abstract class DriftOptionHandler extends AbstractOptionHandler {

	private static final long serialVersionUID = 1836139477178981456L;
	// Looks like the serializability comes from MOA
 
	public IntOption nAttributes = new IntOption("nAttributesss", 'n',
    	    "Number of attributes as parents of the class", 2, 1, 10);

    public IntOption nValuesPerAttribute = new IntOption("nValuesPerAttribute", 'v',
    	    "Number of values per attribute", 2, 2, 5);
        
        public IntOption burnInNInstances = new IntOption("burnInNInstances", 'b',
    	    "Number of instances before the start of the drift", 10000, 0, Integer.MAX_VALUE);
        
        public FloatOption driftMagnitudePrior = new FloatOption("driftMagnitudePrior", 'i',
    	    "Magnitude of the drift between the starting probability and the one after the drift."
    		    + " Magnitude is expressed as the Hellinger or Total Variation distance [0,1]", 0.5, 1e-20, 0.9);
        public FloatOption driftMagnitudeConditional = new FloatOption("driftMagnitudeConditional",
    	    'o',
    	    "Magnitude of the drift between the starting probability and the one after the drift."
    		    + " Magnitude is expressed as the Hellinger or Total Variation distance [0,1]", 0.5, 1e-20, 0.9);
        
        public FloatOption precisionDriftMagnitude = new FloatOption(
    	    "epsilon", 'e', 
    	    "Precision of the drift magnitude for p(x) (how far from the set magnitude is acceptable)",
    	    0.01, 1e-20, 1.0);
        
        public FlagOption driftConditional = new FlagOption("driftConditional", 'c',
    	    "States if the drift should apply to the conditional distribution p(y|x).");
        
        public FlagOption driftPriors = new FlagOption("driftPriors", 'p',
    	    "States if the drift should apply to the prior distribution p(x). ");
        
        public MultiChoiceOption distanceMeasure = new MultiChoiceOption("distanceType", 't',
        	    "The distance measure used", new String[]{"Hellinger Distance", "Total Variation Distance"}, new String[]{"L2 distance", "L1 distance"}, 0 );
        // Why are the option descriptions not visible anywhere?
            
        public IntOption seed = new IntOption("seed", 'r', "Seed for random number generator", -1,
    	    Integer.MIN_VALUE, Integer.MAX_VALUE);
	
	@Override
	public void getDescription(StringBuilder arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void prepareForUseImpl(TaskMonitor arg0, ObjectRepository arg1) {
		// TODO Auto-generated method stub
		
	}

}
