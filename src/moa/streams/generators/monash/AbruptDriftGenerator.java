package moa.streams.generators.monash;

import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.DenseInstance;
import com.yahoo.labs.samoa.instances.Instance;

import moa.core.InstanceExample;
import moa.core.ObjectRepository;
import moa.tasks.TaskMonitor;

public class AbruptDriftGenerator extends DriftGenerator{

	public FloatOption driftMagnitudeConditional = new FloatOption("driftMagnitudeConditional", 'o',
			"Magnitude of the drift between the starting probability and the one after the drift."
					+ " Magnitude is expressed as the Hellinger or Total Variation distance [0,1]", 0.5, 0.0, 1.0);

	public FlagOption driftPriors = new FlagOption("driftPriors", 'p',
			"States if the drift should apply to the prior distribution p(x). ");

	public FlagOption driftConditional = new FlagOption("driftConditional", 'c',
			"States if the drift should apply to the conditional distribution p(y|x).");
	public IntOption numClasses = new IntOption("numClasses", 'z',
			"How many classes?", 4, 2, Integer.MAX_VALUE);
	public AbruptDriftGenerator() {
		super();
	}

	private static final long serialVersionUID = 1291115908166720203L;
	/* TODO: Do we really need a serializable object, and to set the UID
	 * explicitly rather than let JDK handle it?*/

	double[][] px;//Temporary container, maybe move into method = new double[pxbd.length][pxbd[0].length]; 
	double[][] pygx;//Temporary container, maybe move into method = new double[pygxbd.length][pygxbd[0].length]; 
	
	/**
	 * p(x) before drift
	 */
	double[][] pxbd;
	/**
	 * p(y|x) before drift
	 */
	double[][] pygxbd;

	/**
	 * p(x) after drift
	 */
	double[][] pxad;
	/**
	 * p(y|x) after drift
	 */
	double[][] pygxad;

	RandomGenerator rg = new JDKRandomGenerator();

	@Override
	public String getPurposeString() {
		return "Generates a stream with an abrupt drift of given magnitude.";
	}

	@Override
	public InstanceExample nextInstance() {
		// which distribution to draw from?? depending on timestep, modulate your gradual drift here.
		// we are changing px. do we need to also update pygx?? Does it change??
		// if we assume pure covariate drift, it doesn't...


		if (recurrentDrift.getChosenLabel().compareTo("NotRecurrent") == 0){
			if(nInstancesGeneratedSoFar <= burnInNInstances.getValue()){
				px = pxbd;
				pygx = pygxbd;								
			}
			else {
				px = pxad;
				pygx = pygxad;
			}
		}
		if (recurrentDrift.getChosenLabel().compareTo("Recurrent") == 0){
			if((nInstancesGeneratedSoFar / burnInNInstances.getValue()) % 2 == 0){
				px = pxbd;
				pygx = pygxbd;						
			}
			else {
				px = pxad;
				pygx = pygxad;
			}
		}
				
		Instance inst = new DenseInstance(streamHeader.numAttributes());
		inst.setDataset(streamHeader);

		int[] indexes = new int[nAttributes.getValue()];

		// setting values for x_1,...,x_n
		for (int a = 0; a < indexes.length; a++) {
			// choosing values of x_1,...,x_n
			double rand = r.nextUniform(0.0, 1.0, true);
			// pick seed for distribution from interface, but to spit out a different sequence using timeinmillis, use r2. For consistent sequences, use r.
			int chosenVal = 0;
			if(pxad==null) {
				System.err.println ("NULL PXAD");
			}
			if(pxbd==null) {
				System.err.println ("NULL PXBD");
			}
			if(px==null) {
				System.err.println ("NULL PX");
			}
			double sumProba = px[a][chosenVal];

			while (rand > sumProba) { //pick one of the nAttributes based on the rand
				chosenVal++;
				sumProba += px[a][chosenVal];
			}
			indexes[a] = chosenVal;
			inst.setValue(a, chosenVal); //set the a'th attribute to chosenVal
		}

		int lineNoCPT = getIndex(indexes);

		int chosenClassValue = 0;
		while (pygx[lineNoCPT][chosenClassValue] != 1.0) {
			//find the index which corresponds to the class value of the data
			chosenClassValue++;
		}
		inst.setClassValue(chosenClassValue);

		nInstancesGeneratedSoFar++;

		//if(nInstancesGeneratedSoFar % 100 == 0 && nInstancesGeneratedSoFar < 302000 && nInstancesGeneratedSoFar > 300000) {System.err.println(nInstancesGeneratedSoFar);}

		return new InstanceExample(inst);
	}

	@Override
	protected void prepareForUseImpl(TaskMonitor monitor,
			ObjectRepository repository) {
		//System.out.println("burnIn=" + burnInNInstances.getValue());
		generateHeader();

		int nCombinationsValuesForPX = 1;
		for (int a = 0; a < nAttributes.getValue(); a++) {
			nCombinationsValuesForPX *= nValuesPerAttribute.getValue();
		}

		pxbd = new double[nAttributes.getValue()][nValuesPerAttribute.getValue()];
		pygxbd = new double[nCombinationsValuesForPX][numClasses.getValue()];// this used to default to the number of values per attribute

		RandomGenerator rg = new JDKRandomGenerator();
		rg.setSeed(seed.getValue());
		r = new RandomDataGenerator(rg);

		RandomGenerator rg2 = new JDKRandomGenerator(); // pick seed for distribution from interface, but spit out a different sequence using timeinmillis
		rg2.setSeed(System.currentTimeMillis());
		r2 = new RandomDataGenerator(rg2);

		// generating distribution before drift

		// p(x)
		generateRandomPx(pxbd, r);

		// p(y|x)
		generateRandomPyGivenX(pygxbd, r);

		// generating distribution after drift

		if (driftPriors.isSet()) {
			pxad = new double[nAttributes.getValue()][nValuesPerAttribute
			                                          .getValue()];
			double obtainedMagnitude;

			/*
			 * don't sample p(x)!!
			 * instead, pick some random distribution
			 * Francois' code randomly perturbs the distribution if the required magnitude is less than 0.2,
			 * by adding a random value from a gaussian with some deviation sigma centred at each cell value
			 * but it doesn't take into account the relationship between the Hellinger and the diff in prob. values... so more exploration
			 * Then it normalises the obtained distribution.
			 * Instead, can we "grow" the distribution by a certain magnitude at each timestep?
			 * Let us first try this in the abrupt case. In a single timestep, we will grow the distribution
			 * by some magnitude.
			 * Pick some random throwaway distribution.
			 * */

			//System.out.println("Sampling p(x) for required magnitude...");
			do {
				if (driftMagnitudePrior.getValue() >= 0.2) {
					generateRandomPx(pxad, r);
				} else if (driftMagnitudePrior.getValue() < 0.2) {
					generateRandomPxAfterCloseToBefore(driftMagnitudePrior.getValue(), pxbd, pxad, r);
					// This doesn't actually solve the problem in the Hellinger case where sqrt(p) + sqrt(q) > 1
				}
				//note this workaround so he doesn't explore a large number of random distributions!
				obtainedMagnitude = computeMagnitudePX(PX2DTo1D(nCombinationsValuesForPX, pxbd),
						PX2DTo1D(nCombinationsValuesForPX, pxad));

			} while (Math.abs(obtainedMagnitude - driftMagnitudePrior.getValue()) > precisionDriftMagnitude
					.getValue());

			/*System.out.println("exact magnitude for p(x)="
					+ computeMagnitudePX(
							PX2DTo1D(nCombinationsValuesForPX, pxbd),
							PX2DTo1D(nCombinationsValuesForPX, pxad)) + "\tasked="
					+ driftMagnitudePrior.getValue());*/
		} else {
			pxad = pxbd;
		}

		// conditional
		if (driftConditional.isSet()) {
			pygxad = new double[nCombinationsValuesForPX][];
			for (int line = 0; line < pygxad.length; line++) {
				// default is same distrib
				pygxad[line] = pygxbd[line];
			}

			int nLinesToChange = (int) Math.round(driftMagnitudeConditional.getValue()
					* nCombinationsValuesForPX);
			if (nLinesToChange == 0.0) {
			/*	System.out
				.println("ExactMagnitude:[0.0]");
				pygxad = pygxbd;*/

			} else {
				int[] linesToChange = r.nextPermutation(
						nCombinationsValuesForPX, nLinesToChange);

				for (int line : linesToChange) {
					pygxad[line] = new double[numClasses.getValue()];

					double[] lineCPT = pygxad[line];
					int chosenClass;

					do {
						chosenClass = r.nextInt(0, lineCPT.length - 1);
						// making sure we choose a different class value
					} while (pygxbd[line][chosenClass] == 1.0);

					for (int c = 0; c < lineCPT.length; c++) {
						if (c == chosenClass) {
							lineCPT[c] = 1.0;
						} else {
							lineCPT[c] = 0.0;
						}
					}
				}
				/*System.out.println("p(y|x) ExactMagnitude:["
						+ computeMagnitudePYGX(pygxbd, pygxad) + "]\tasked="
						+ driftMagnitudeConditional.getValue());*/
			}
		} else {
			pygxad = pygxbd;
		}

		// System.out.println(Arrays.toString(pxbd));
		// System.out.println(Arrays.toString(pxad));

		nInstancesGeneratedSoFar = 0L;

		rg.setSeed(seed.getValue());
		r = new RandomDataGenerator(rg);
		// reset the generator- different magnitudes generate sequences of different lengths during search
		// however, this sort of fix is only useful for my own gradual drift generator. it was made particularly for my own gradual drift generator.
		// in the case of a blip... or a moa conceptDrift... do I still need to create a new RandomDataGenerator?
		// that is leading to streams being twice as long...

	}

}