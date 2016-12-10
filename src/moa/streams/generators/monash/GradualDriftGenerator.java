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

import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.DenseInstance;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import com.yahoo.labs.samoa.instances.InstancesHeader;

public class GradualDriftGenerator extends DriftGenerator{

	private static final long serialVersionUID = -3513131640712137498L;

	public IntOption driftDuration = new IntOption("driftDuration", 'd',
			"How long drift lasts", 10000, 0, Integer.MAX_VALUE);

	public GradualDriftGenerator() {
		super();

	}

	/* TODO: Do we really need a serializable object, and to set the UID
	 * explicitly rather than let JDK handle it?*/
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
	 * interpolation factor
	 */
	double[][] pxdiff;
	/**
	 * Current px
	 */
	double[][] px;

	RandomGenerator rg = new JDKRandomGenerator();

	@Override
	public String getPurposeString() {
		return "Generates a stream with a gradual drift of given magnitude.";
	}

	@Override
	public InstanceExample nextInstance() {


		if(nInstancesGeneratedSoFar > burnInNInstances.getValue() && nInstancesGeneratedSoFar < burnInNInstances.getValue() + driftDuration.getValue()){

			for (int i = 0; i < nAttributes.getValue(); i++) {
				for (int j =0; j < nValuesPerAttribute.getValue(); j++) {
					px[i][j] = px[i][j] + pxdiff[i][j];	//Add the interpolation step
				}
			}
		} else if (nInstancesGeneratedSoFar < burnInNInstances.getValue()) {
			px = pxbd;
		} else {

		}

		double[][] pygx = pygxbd;

		Instance inst = new DenseInstance(streamHeader.numAttributes());
		inst.setDataset(streamHeader);

		int[] indexes = new int[nAttributes.getValue()];

		/* This for-loop contains an algorithm to sample from a multinomial distribution. It is
		 * implemented correctly. See http://www.win.tue.nl/~marko/2WB05/lecture8.pdf
		 * Pick U from the uniform distribution. Find the index where the sum of the independent
		 * probability values exceeds the picked value. The smallest index that exceeds U is the outcome.
		 * There are as many indexes as attributes. For each attribute, we need a value.
		 * Each trial is picking a value for an attribute. Each trial draws from a categorical distribution- it picks one out of several available values.
		 * If you have as many trials as attributes, you have to pick from a multinomial distribution with n = nAttributes and k = nValuesPerAttribute.
		 * This happens to be the layout of the px matrix.
		 * Each outcome is a combination of attribute-values, and each attribute corresponds to one categorical trial.
		 */
		for (int a = 0; a < indexes.length; a++) {
			double rand = r.nextUniform(0.0, 1.0, true);
			//System.out.println(Arrays.toString(px[a]));

			int chosenVal = 0;
			double sumProba = px[a][chosenVal];
			while (rand > sumProba) {

				chosenVal++;
				sumProba += px[a][chosenVal];
			}

			indexes[a] = chosenVal;
			inst.setValue(a, chosenVal);
		}

		int lineNoCPT = getIndex(indexes);

		int chosenClassValue = 0;
		while (pygx[lineNoCPT][chosenClassValue] != 1.0) {
			chosenClassValue++;
		}//finds the class Value in pygx, and sets the instance to it
		inst.setClassValue(chosenClassValue);

		nInstancesGeneratedSoFar++;
		// System.out.println("generated "+inst);
		return new InstanceExample(inst);
	}

	@Override
	protected void prepareForUseImpl(TaskMonitor monitor,
			ObjectRepository repository) {
		// prepare the start and end px's here


		System.out.println("burnIn=" + burnInNInstances.getValue());
		generateHeader();

		int nCombinationsValuesForPX = 1;
		for (int a = 0; a < nAttributes.getValue(); a++) {
			nCombinationsValuesForPX *= nValuesPerAttribute.getValue();
		}

		pxbd = new double[nAttributes.getValue()][nValuesPerAttribute.getValue()];
		pygxbd = new double[nCombinationsValuesForPX][nValuesPerAttribute.getValue()]; // number of class = nValuesPerAttributes

		// generating distribution before drift

		// p(x)
		rg.setSeed(seed.getValue());
		r = new RandomDataGenerator(rg);
		generateRandomPx(pxbd, r);


		// p(y|x)
		rg.setSeed(seed.getValue());
		r = new RandomDataGenerator(rg);

		/*
		 * This totally randomly assigns a class to each instance. Let's replace this with something that
		 * (a) has some distribution over classes
		 * (b) has a dependency between attributes
		 */
		generateRandomPyGivenX(pygxbd, r);

		// generating covariate drift

			pxad = new double[nAttributes.getValue()][nValuesPerAttribute.getValue()];
			px = new double[nAttributes.getValue()][nValuesPerAttribute.getValue()];
			pxdiff = new double[nAttributes.getValue()][nValuesPerAttribute.getValue()];

			/* Let us first make a guess based on "proportion" of distance to the furthest distribution.*/

			/* Actually, let's do this later. Let's first do a binary search.*/
			double[][] startDist = pxbd;
			double[][] furthestDist = getFurthestDistribution(nCombinationsValuesForPX, pxbd);
			double[][] dist1 = startDist;
			double[][] dist2 = furthestDist;
			System.out.println("Searching p(x) for required magnitude...");

//System.out.println("FURTHEST DISTANCE IS: " + Math.abs(computeMagnitudePX(nCombinationsValuesForPX, dist1, dist2)));
//printMatrix(furthestDist);
			//PX2DTo1D(nCombinationsValuesForPX
			while (	Math.abs(computeMagnitudePX(nCombinationsValuesForPX, dist1, dist2) - driftMagnitudePrior.getValue())
						> precisionDriftMagnitude.getValue() ) {

				double[][] guessDist = new double[nAttributes.getValue()][nValuesPerAttribute.getValue()];

				for (int i =0; i < nAttributes.getValue(); i++) {
					for (int j =0; j < nValuesPerAttribute.getValue(); j++) {
						guessDist[i][j] = dist1[i][j] + 0.5 * (dist2[i][j] - dist1[i][j]); //this is a first guess distribution
					}
				}

				if (Math.abs(computeMagnitudePX(nCombinationsValuesForPX, startDist, guessDist) - driftMagnitudePrior.getValue())
						<= precisionDriftMagnitude.getValue() ) {
					dist2 = guessDist;
					break;
				}
				else if ( Math.abs(computeMagnitudePX(nCombinationsValuesForPX, startDist, guessDist)) > driftMagnitudePrior.getValue()) {
					dist2 = guessDist;
				}
				else{
					dist1 = guessDist;
				}

			} //This should converge. But going back from 1D to 2D involves adding up all the marginals. So let's just do it in 2D.
			pxad = dist2;

			//precisionDriftMagnitude.getValue()

//printMatrix(pxad);

			System.out.println("exact magnitude for p(x)="
					+ computeMagnitudePX(nCombinationsValuesForPX, pxbd, pxad) + "\tasked="
					+ driftMagnitudePrior.getValue());

			for (int i = 0; i < nAttributes.getValue(); i++) {
				for (int j =0; j < nValuesPerAttribute.getValue(); j++) {
					pxdiff[i][j] = (pxad[i][j] - pxbd[i][j])/driftDuration.getValue();
				}
			}

		// System.out.println(Arrays.toString(pxbd));
		// System.out.println(Arrays.toString(pxad));

		nInstancesGeneratedSoFar = 0L;

		//reset the generator; different drift magnitudes generate sequences of different lengths during the search
		rg.setSeed(seed.getValue());
		r = new RandomDataGenerator(rg);
	}

}