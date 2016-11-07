package moa.streams.generators.monash;

import java.util.Arrays;
import java.util.List;

import moa.core.Example;
import moa.core.FastVector;
import moa.core.InstanceExample;
import moa.core.ObjectRepository;
import moa.streams.InstanceStream;
import moa.tasks.TaskMonitor;

import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.DenseInstance;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import com.yahoo.labs.samoa.instances.InstancesHeader;

public class AbruptDriftGenerator extends DriftGenerator{


	public FloatOption driftMagnitudePrior = new FloatOption("driftMagnitudePrior", 'i',
			"Magnitude of the drift between the starting probability and the one after the drift."
					+ " Magnitude is expressed as the Hellinger or Total Variation distance [0,1]", 0.5, 1e-20, 0.9);

	public FloatOption driftMagnitudeConditional = new FloatOption("driftMagnitudeConditional", 'o',
			"Magnitude of the drift between the starting probability and the one after the drift."
					+ " Magnitude is expressed as the Hellinger or Total Variation distance [0,1]", 0.5, 1e-20, 0.9);

	public FlagOption driftPriors = new FlagOption("driftPriors", 'p',
			"States if the drift should apply to the prior distribution p(x). ");

	public FlagOption driftConditional = new FlagOption("driftConditional", 'c',
			"States if the drift should apply to the conditional distribution p(y|x).");

	public IntOption burnInNInstances = new IntOption("burnInNInstances", 'b',
			"Number of instances before the start of the drift", 10000, 0, Integer.MAX_VALUE);


	public AbruptDriftGenerator() {
		super();

	}

	private static final long serialVersionUID = 1291115908166720203L;
	/* TODO: Do we really need a serializable object, and to set the UID
	 * explicitly rather than let JDK handle it?*/

	protected InstancesHeader streamHeader;

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

	RandomDataGenerator r;

	long nInstancesGeneratedSoFar;

	// Do we need implementations for these?

	@Override
	public long estimatedRemainingInstances() {
		return -1;
	}

	@Override
	public boolean hasMoreInstances() {
		return true;
	}

	@Override
	public boolean isRestartable() {
		return true;
	}

	@Override
	public void restart() {
		nInstancesGeneratedSoFar = 0L;
	}

	@Override
	public void getDescription(StringBuilder sb, int indent) {

	}

	@Override
	public String getPurposeString() {
		return "Generates a stream with an abrupt drift of given magnitude.";
	}

	@Override
	public InstancesHeader getHeader() {
		return streamHeader;
	}

	protected void generateHeader() {

		FastVector<Attribute> attributes = getHeaderAttributes(nAttributes
				.getValue(), nValuesPerAttribute.getValue());

		this.streamHeader = new InstancesHeader(new Instances(
				getCLICreationString(InstanceStream.class), attributes, 0));
		this.streamHeader.setClassIndex(this.streamHeader.numAttributes() - 1);
	}

	@Override
	public InstanceExample nextInstance() {
		// which distribution to draw from?? depending on timestep, modulate your gradual drift here.
		// we are changing px. do we need to also update pygx?? Does it change??
		// if we assume pure covariate drift, it doesn't...

		double[][] px = (nInstancesGeneratedSoFar <= burnInNInstances
				.getValue()) ? pxbd : pxad;
		double[][] pygx = (nInstancesGeneratedSoFar <= burnInNInstances
				.getValue()) ? pygxbd : pygxad;

		Instance inst = new DenseInstance(streamHeader.numAttributes());
		inst.setDataset(streamHeader);

		int[] indexes = new int[nAttributes.getValue()];

		// setting values for x_1,...,x_n
		for (int a = 0; a < indexes.length; a++) {
			// choosing values of x_1,...,x_n
			double rand = r.nextUniform(0.0, 1.0, true);
			int chosenVal = 0;
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
		return new InstanceExample(inst);
	}

	@Override
	protected void prepareForUseImpl(TaskMonitor monitor,
			ObjectRepository repository) {
		System.out.println("burnIn=" + burnInNInstances.getValue());
		generateHeader();

		int nCombinationsValuesForPX = 1;
		for (int a = 0; a < nAttributes.getValue(); a++) {
			nCombinationsValuesForPX *= nValuesPerAttribute.getValue();
		}

		pxbd = new double[nAttributes.getValue()][nValuesPerAttribute.getValue()];
		pygxbd = new double[nCombinationsValuesForPX][nValuesPerAttribute.getValue()];

		RandomGenerator rg = new JDKRandomGenerator();
		rg.setSeed(seed.getValue());
		r = new RandomDataGenerator(rg);

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
			 * but it doesn't take into account the relaionship between the Hellinger and the diff in prob. values... so more exploration
			 * Then it normalises the obtained distribution.
			 * Instead, can we "grow" the distribution by a certain magnitude at each timestep?
			 * Let us first try this in the abrupt case. In a single timestep, we will grow the distribution
			 * by some magnitude.
			 * Pick some random throwaway distribution.
			 * */

			System.out.println("Sampling p(x) for required magnitude...");
			do {
				if (driftMagnitudePrior.getValue() >= 0.2) {
					generateRandomPx(pxad, r);
				} else if (driftMagnitudePrior.getValue() < 0.2) {
					generateRandomPxAfterCloseToBefore(driftMagnitudePrior.getValue(), pxbd, pxad, r);
					// This doesn't actually solve the problem in the Hellinger case where sqrt(p) + sqrt(q) > 1
				}
				//note this workaround so he doesn't explore a large number of random distributions!
				obtainedMagnitude = computeMagnitudePX(nCombinationsValuesForPX, pxbd, pxad);
			} while (Math.abs(obtainedMagnitude - driftMagnitudePrior.getValue()) > precisionDriftMagnitude
					.getValue());

			System.out.println("exact magnitude for p(x)="
					+ computeMagnitudePX(nCombinationsValuesForPX, pxbd, pxad) + "\tasked="
					+ driftMagnitudePrior.getValue());
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
				System.out
				.println("Not enough drift to be noticeable in p(y|x) - unchanged");
				pygxad = pygxbd;

			} else {
				int[] linesToChange = r.nextPermutation(
						nCombinationsValuesForPX, nLinesToChange);

				for (int line : linesToChange) {
					pygxad[line] = new double[nValuesPerAttribute.getValue()];

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
				System.out.println("exact magnitude for p(y|x)="
						+ computeMagnitudePYGX(pygxbd, pygxad) + "\tasked="
						+ driftMagnitudeConditional.getValue());
			}
		} else {
			pygxad = pygxbd;
		}

		// System.out.println(Arrays.toString(pxbd));
		// System.out.println(Arrays.toString(pxad));

		nInstancesGeneratedSoFar = 0L;

	}
	/**
	 * Gets the index of a given instance-tuple
	 */
	protected final int getIndex(int... indexes) { //size of indexes is total number of attributes. It contains chosen nValues.

		int index = indexes[0];
		for (int i = 1; i < indexes.length; i++) {
			index *= nValuesPerAttribute.getValue();
			index += indexes[i];
		}
		return index;
		// multiply nValue for first attribute by numValuesPerAttribute. Add the nValue of the next attribute. repeat.
		// then multiply whichever nValue you picked for 0th attribute by numAttributes
	}

}