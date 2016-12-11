package moa.streams.generators.monash;

import java.util.ArrayList;
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
	public IntOption nClasses = new IntOption("numClasses", 'z',
			"How many classes?", 4, 0, Integer.MAX_VALUE);
	public GradualDriftGenerator() {
		super();

	}

	/* TODO: Do we really need a serializable object, and to set the UID
	 * explicitly rather than let JDK handle it?*/

	ArrayList<ArrayList<AttrVal>> attr_val;

	/**
	 * p(x) before drift
	 */
	//double[][] pxbd;
	double[] pxbd1d;

	/**
	 * p(y|x) before drift
	 */
	double[][] pygxbd;

	/**
	 * p(x) after drift
	 */
	//double[][] pxad;
	double[] pxad1d;


	/**
	 * interpolation factor
	 */
	//double[][] pxdiff;
	double[] pxdiff1d;
	/**
	 * Current px
	 */
	//double[][] px;
	double[] px1d;

	/**
	 * pybd
	 */
	double pybd[];

	private static int nCombinationsValuesForPX = -1;

	RandomGenerator rg = new JDKRandomGenerator();

	@Override
	public String getPurposeString() {
		return "Generates a stream with a gradual drift of given magnitude.";
	}

	@Override
	public InstanceExample nextInstance() {


		if(nInstancesGeneratedSoFar > burnInNInstances.getValue() && nInstancesGeneratedSoFar < burnInNInstances.getValue() + driftDuration.getValue()){

			for (int i = 0; i < nCombinationsValuesForPX; i++) {
					px1d[i] = px1d[i] + pxdiff1d[i];	//Add the interpolation step
			}
		} else if (nInstancesGeneratedSoFar < burnInNInstances.getValue()) {
			px1d = pxbd1d;
		} else {

		}

		double[][] pygx = pygxbd;

		Instance inst = new DenseInstance(streamHeader.numAttributes());
		inst.setDataset(streamHeader);

		double[] px1d_cdf = new double[nCombinationsValuesForPX];

		/* This for-loop contains an algorithm to sample from a multinomial distribution. It is
		 * implemented correctly. See http://www.win.tue.nl/~marko/2WB05/lecture8.pdf
		 * Pick U from the uniform distribution. Find the index where the sum of the independent
		 * probability values exceeds the picked value. The smallest index that exceeds U is the outcome.
		 * There are as many indexes as attributes. For each attribute, we need a value.
		 * Each trial is picking a value for an attribute. Each trial draws from a categorical distribution- it picks one out of several available values.
		 * If you have as many trials as attributes, you have to pick from a multinomial distribution with n = nAttributes and k = nValuesPerAttribute.
		 * This happens to be the layout of the px matrix.
		 * Each outcome is a combination of attribute-values, and each attribute corresponds to one categorical trial.
		 * Note: Bernoulli applies to a single trial over a categorical distribution with 2 categories
		 * Binomial applies to multiple trials over a categorical distribution with 2 categories- multiple Bernoulli's are binomially distributed
		 * Multinomial applies to multiple trials over a categorical distribution with greater than 2 categories-
		 * multiple categorical trials are multinomially distributed
		 */
		/*
		 * Since we are moving from a 2-d array to a 1-d one, we no longer have multiple trials(one per attribute) over a
		 * categorical distribution(attribute-values)
		 * Each generating instance at each timestep is now equivalent to drawing from a single categorical distribution
		 *
		 * To sample from this, we first convert it into a CDF; then we pick a uniformly distributed number between 0 and 1; then we find the greatest index that has a cumulative probability less than the number from the uniform
		 * */

		for (int i = 0; i < px1d_cdf.length; i++) {	px1d_cdf[i] = 0.0;} //initialize
		for (int i = 0; i < px1d_cdf.length; i++) {
			if(i == 0){	px1d_cdf[i] = px1d[i];}
			else{
				px1d_cdf[i] = px1d_cdf[i-1] + px1d[i];
			}
		}// cdf created
		double rand = r.nextUniform(0.0, 1.0, true);
		int chosenIndex = 0;
		while (rand > px1d_cdf[chosenIndex]) { chosenIndex++; }
		if(chosenIndex > 0) {chosenIndex--;}
		// we need a mapping from indices to attributes and values! We already have the class.
		for ( AttrVal x : attr_val.get(chosenIndex)) {
		     inst.setValue(x.getAttr(), x.getVal());
		}
		//finds the class Value in pygx, and sets the instance to it
		int i = 0;
		double max = 0.0;
		int chosenClass = 0;
		while (i < pygx[chosenIndex].length) {
			if (pygx[chosenIndex][i] > max) { chosenClass = i;}
			i++;
		}
		inst.setClassValue(chosenClass);

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

		nCombinationsValuesForPX = 1;
		for (int a = 0; a < nAttributes.getValue(); a++) {
			nCombinationsValuesForPX *= nValuesPerAttribute.getValue();
		}

		pxbd1d = new double[nCombinationsValuesForPX];
		pygxbd = new double[nCombinationsValuesForPX][nClasses.getValue()];

		// generating distribution before drift
		rg.setSeed(seed.getValue());
		r = new RandomDataGenerator(rg);
		pybd = new double[nClasses.getValue()]; //TODO:  should be numClasses
		assert(pygxbd !=null);
		attr_val = Node.buildTree(nAttributes.getValue(), nValuesPerAttribute.getValue(), nClasses.getValue(), 0.05, r, pxbd1d, pygxbd, pybd);

/*
		// p(x)
		rg.setSeed(seed.getValue());
		r = new RandomDataGenerator(rg);
		generateRandomPx(pxbd, r);


		// p(y|x)
		rg.setSeed(seed.getValue());
		r = new RandomDataGenerator(rg);
*/
		/*
		 * This totally randomly assigns a class to each instance. Let's replace this with something that
		 * (a) has some distribution over classes
		 * (b) has a dependency between attributes
		 */
/*		generateRandomPyGivenX(pygxbd, r);*/

		// generating covariate drift

			pxad1d = new double[nCombinationsValuesForPX];
			px1d = new double[nCombinationsValuesForPX];
			pxdiff1d = new double[nCombinationsValuesForPX];

			/* Let us first make a guess based on "proportion" of distance to the furthest distribution.*/

			/* Actually, let's do this later. Let's first do a binary search.*/
			double[] startDist = pxbd1d;
			double[] furthestDist = getFurthestDistribution(pxbd1d);
			double[] dist1 = startDist;
			double[] dist2 = furthestDist;
			System.out.println("Searching p(x) for required magnitude...");

//System.out.println("FURTHEST DISTANCE IS: " + Math.abs(computeMagnitudePX(nCombinationsValuesForPX, dist1, dist2)));
//printMatrix(furthestDist);
			//PX2DTo1D(nCombinationsValuesForPX
			while (	Math.abs(computeMagnitudePX(dist1, dist2) - driftMagnitudePrior.getValue())
						> precisionDriftMagnitude.getValue() ) {

				double[] guessDist = new double[nCombinationsValuesForPX];

				for (int i =0; i < nCombinationsValuesForPX; i++) {
						guessDist[i] = dist1[i] + 0.5 * (dist2[i] - dist1[i]); //this is a first guess distribution
				}

				if (Math.abs(computeMagnitudePX(startDist, guessDist) - driftMagnitudePrior.getValue())
						<= precisionDriftMagnitude.getValue() ) {
					dist2 = guessDist;
					break;
				}
				else if ( Math.abs(computeMagnitudePX(startDist, guessDist)) > driftMagnitudePrior.getValue()) {
					dist2 = guessDist;
				}
				else{
					dist1 = guessDist;
				}

			} //This should converge. But going back from 1D to 2D involves adding up all the marginals. So let's just do it in 2D.
			pxad1d = dist2;

			//precisionDriftMagnitude.getValue()

//printMatrix(pxad);

			System.out.println("exact magnitude for p(x)="
					+ computeMagnitudePX(pxbd1d, pxad1d) + "\tasked="
					+ driftMagnitudePrior.getValue());

			for (int i = 0; i < nCombinationsValuesForPX; i++) {
					pxdiff1d[i] = (pxad1d[i] - pxbd1d[i])/driftDuration.getValue();
			}

		// System.out.println(Arrays.toString(pxbd));
		// System.out.println(Arrays.toString(pxad));

		nInstancesGeneratedSoFar = 0L;

		//reset the generator; different drift magnitudes generate sequences of different lengths during the search
		rg.setSeed(seed.getValue());
		r = new RandomDataGenerator(rg);
	}

}