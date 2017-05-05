/*
 * Created by Lee Loong Kuan on 5/09/2015.
 * Interface that represents a basic Concept Drift Generator
 * Contains static methods to generate probabilities
 */

package moa.streams.generators.monash;

import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.github.javacliparser.MultiChoiceOption;
import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.Instances;
import com.yahoo.labs.samoa.instances.InstancesHeader;

import moa.core.FastVector;
import moa.streams.InstanceStream;

import org.apache.commons.math3.random.RandomDataGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public abstract class DriftGenerator extends DriftOptionHandler implements InstanceStream {

	private static final long serialVersionUID = 6853166101160277496L;

	public MultiChoiceOption distanceMeasure = new MultiChoiceOption("distanceType", 't',
			"The distance measure used", new String[]{"Hellinger Distance", "Total Variation Distance"}, new String[]{"L2 distance", "L1 distance"}, 0 );

	public IntOption nAttributes = new IntOption("nAttributes", 'n',
			"Number of attributes as parents of the class", 2, 1, 10);

	public IntOption nValuesPerAttribute = new IntOption("nValuesPerAttribute", 'v',
			"Number of values per attribute", 2, 2, 10);

	public FloatOption precisionDriftMagnitude = new FloatOption(
			"epsilon", 'e',
			"Precision of the drift magnitude for p(x) (how far from the set magnitude is acceptable)",
			0.01, 1e-20, 1.0);

	public IntOption seed = new IntOption("seed", 'r', "Seed for random number generator", -1,
			Integer.MIN_VALUE, Integer.MAX_VALUE);

	public FloatOption driftMagnitudePrior = new FloatOption("driftMagnitudePrior", 'i',
			"Magnitude of the drift between the starting probability and the one after the drift."
					+ " Magnitude is expressed as the Hellinger or Total Variation distance [0,1]", 0.5, 1e-20, 0.9);

	public IntOption burnInNInstances = new IntOption("burnInNInstances", 'b',
			"Number of instances before the start of the drift", 10000, 0, Integer.MAX_VALUE);

	static DriftMagnitude driftMag;

	public DriftGenerator() {
		super();
		// pick distance measure
		if (distanceMeasure.getChosenLabel() == "Hellinger Distance"){
			driftMag = new HellingerDistance();
		}
		else if (distanceMeasure.getChosenLabel() == "Total Variation Distance"){
			driftMag = new TotalVariationDistance();
		}
	}

	RandomDataGenerator r;

	long nInstancesGeneratedSoFar;

	// Do we need implementations for these?

	@Override
	public void getDescription(StringBuilder sb, int indent) {

	}

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


	protected InstancesHeader streamHeader;

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

	/* Refactor?:
	 * Should Generator extend OptionHandler, own one, or be composited with one?
	 * MOA's design requires all classes with options to extend AbstractOptionHandler
	 * ... extending DriftOptionHandler instead
	 * All Drift Generators will implement InstanceStream, this is fine.
	 * Will each Generator have it's own OptionHandler? That would make sense.
	 * */

	FastVector<Attribute> getHeaderAttributes(int nAttributes, int nValuesPerAttribute) {

		FastVector<Attribute> attributes = new FastVector<>();
		List<String> attributeValues = new ArrayList<String>();
		for (int v = 0; v < nValuesPerAttribute; v++) {
			attributeValues.add("v" + (v + 1));
		}
		for (int i = 0; i < nAttributes; i++) {
			attributes.addElement(new Attribute("x" + (i + 1), attributeValues));
		}
		List<String> classValues = new ArrayList<String>();

		for (int v = 0; v < nValuesPerAttribute; v++) {
			classValues.add("class" + (v + 1));
		}
		attributes.addElement(new Attribute("class", classValues));

		return attributes;
	}

	public static void generateRandomPxAfterCloseToBefore(double sigma,
			double[][] base_px, double[][] drift_px, RandomDataGenerator r) {
		double sum;
		for (int a = 0; a < drift_px.length; a++) {
			sum = 0.0;
			for (int v = 0; v < drift_px[a].length; v++) {
				drift_px[a][v] = Math.abs(r.nextGaussian(base_px[a][v], sigma));
				sum += drift_px[a][v];
			}
			// normalizing
			for (int v = 0; v < drift_px[a].length; v++) {
				drift_px[a][v] /= sum;
				// System.out.println("p(x_" + a + "=v" + v + ")=" +
				// pxbd[a][v]);
			}
		}
	}

	public static void generateRandomPyGivenX(double[][] pygx, RandomDataGenerator r) {
		for (int i = 0; i < pygx.length; i++) {
			double[] lineCPT = pygx[i];
			int chosenClass = r.nextInt(0, lineCPT.length - 1);

			for (int c = 0; c < lineCPT.length; c++) {
				if (c == chosenClass) {
					lineCPT[c] = 1.0;
				} else {
					lineCPT[c] = 0.0;
				}
			}
		}

	}

	public static void generateRandomPyGivenX(double[][] pygx, RandomDataGenerator r, double alphaDirichlet) {
		for (int i = 0; i < pygx.length; i++) {
			double[] lineCPT = pygx[i];
			double sum=0;
			for (int c = 0; c < lineCPT.length; c++) {
				lineCPT[c] = r.nextGamma(alphaDirichlet, 1.0);
				sum+=lineCPT[c];
			}
			for (int c = 0; c < lineCPT.length; c++) {
				lineCPT[c] /= sum;
			}
		}
	}
	/**
	 * Generates the "true" underlying model to learn. This is a random decision tree.
	 *
	 * Each Node will split on one attribute. That attribute will no longer be available to
	 * split on further down the tree. The partition will be a simple partition of attribute values.
	 *
	 * Now, at each split we're saying that that attribute provides the partition with greatest information gain.
	 *
	 * We'll have to ensure that the tree is constantly increasing information gain.
	 *
	 * How do we do this? We want to make our instances collected more and more homogeneous at each level.
	 *
	 * Let's say that prior to the first split the instances have a uniform class distribution.
	 *
	 * After the first split,instances collected down one attribute-value edge should have a higher probability for a given class than others.
	 *
	 * If this is a Dirichlet spike, we may not need any more splits.
	 *
	 * But if this is somewhere in between, we would need more splits on the remaining attributes to get a better picture.
	 *
	 * Strategy: Pick first attribute. Split on it. For each attribute-value edge, pick a different class to "maximise" the probability of.
	 * Now, it is quite possible that you could increase the same class for two different attribute-values... it's also possible that you're
	 * not maximising entropy. But since we get to generate the "true" decision tree, we get to decide this. So let's pick a decision tree
	 * in which each attribute value does lead to a different class having it's probability maximized.
	 *
	 *
	 *
	 * Note: ensure supplied randomgenerator has been reinitialized with the correct seed.
	 * This prevents different objects drawing from different points in the stream.
	 * This of course somewhat defeats the purpose of having a random sequence... use carefully.
	 * @param pygx
	 * @param r
	 */
	/*Perhaps don't reinitialize random as long as sequence lengths do not change between objects?*/

	public static void generateDecisionTreeDist(double[][] px, double pygx[][], double[] py, RandomDataGenerator r) {
		// Generate root node with  py[]

	}

	public static void generateRandomPx(double[][] px, RandomDataGenerator r) {
		generateRandomPx(px, r,false);
	}

	public static void generateRandomPx(double[][] px, RandomDataGenerator r,boolean verbose) {
		double sum;
		for (int a = 0; a < px.length; a++) {
			sum = 0.0;
			for (int v = 0; v < px[a].length; v++) {
				px[a][v] = r.nextGamma(1.0,1.0);
				sum += px[a][v];
			}
			// normalizing
			for (int v = 0; v < px[a].length; v++) {
				px[a][v] /= sum;

			}
			if(verbose)System.out.println("p(x_" + a + ")=" + Arrays.toString(px[a]));
		}

	}

	public static void generateRandomPy1D(double[] py, RandomDataGenerator r,boolean verbose) {
		double sum;
		sum = 0.0;
		for (int v = 0; v < py.length; v++) {
			py[v] = r.nextGamma(1.0,1.0);
			// The shape of this may need to change as per requirements
			sum += py[v];
		}
		// normalizing
		for (int v = 0; v < py.length; v++) {
			py[v] /= sum;

		}
		if(verbose)System.out.println("p(y) " + Arrays.toString(py));
	}


	public static void generateRandomPx1D(double[] px, RandomDataGenerator r,boolean verbose) {
		double sum;
		sum = 0.0;
		for (int v = 0; v < px.length; v++) {
			px[v] = r.nextGamma(1.0,1.0);
			// The shape of this may need to change as per requirements
			sum += px[v];
		}
		// normalizing
		for (int v = 0; v < px.length; v++) {
			px[v] /= sum;

		}
		if(verbose)System.out.println("p(x) " + Arrays.toString(px));
	}



	public static void generateRandomPxWithMissing(double[][] px, RandomDataGenerator r,int nMissing,boolean verbose) {
		double sum;
		for (int a = 0; a < px.length; a++) {
			sum = 0.0;
			for (int v = 0; v < px[a].length; v++) {
				px[a][v] = r.nextGamma(1.0, 1.0);
				sum += px[a][v];
			}
			int[] missing = r.nextPermutation(px[a].length, nMissing);
			for (int p:missing){
				sum-=px[a][p];
				px[a][p]=0.0;
			}
			// normalizing
			for (int v = 0; v < px[a].length; v++) {
				px[a][v] /= sum;

			}
			if(verbose)System.out.println("p(x_" + a + ")=" + Arrays.toString(px[a]));
		}


	}

	public static void generateUniformPx(double[][] px) {
		for (int a = 0; a < px.length; a++) {
			for (int v = 0; v < px[a].length; v++) {
				px[a][v] = 1.0/px[a].length;
			}
		}

	}

	/* nbCombinationsOfValuesPX is the number of all possible tuples.
	 * find the probability of every single tuple before and after drift, and compute Hellinger distance
	 * assume independent attributes
	 * if each row is an independent distribution for an attribute
	 * why doesn't each row get it's own Hellinger?
	 * What does it mean for us to simply do this over multiple row distributions?
	 * Does this make sense?
	 * Yes. However many discrete distributions we have, it looks like if we multiply them
	 * by each other, then just like in the continuous case, we will still get a sum of 1.
	 * Here we are assuming independence between the distributions for each attribute and
	 * computing values for all possible outcomes of their joint distributions.
	 * The probability values for elements of the joint distribution should all sum up to 1.
	 * So this is an already normalised distribution and we can compute the Hellinger.
	 */

	public static double computeMagnitudePX(double[] base_px, double[] drift_px) {

		double[] baseCovariate = base_px;
		double[] driftCovariate = drift_px;

		return driftMag.getValue(baseCovariate, driftCovariate);
	}

	public static double computeMagnitudePYGX(double[][] base_pygx, double[][] drift_pygx) {
		double magnitude = 0.0;
		for (int i = 0; i < base_pygx.length; i++) {

			double partialM = driftMag.getValue(base_pygx[i], drift_pygx[i]);

			// assert (partialM == 0.0 || partialM == 1.0);
			// this assert makes sense only if only one class is chosen at a time and Hellinger is used
			// otherwise it doesn't make sense
			magnitude += partialM;
		}
		magnitude /= base_pygx.length;
		return magnitude;
	}

	public static double computeMagnitudeClassPrior(double[] baseClassP, double[] driftClassP) {

		return driftMag.getValue(baseClassP, driftClassP);
	}

	/* index is the index of the instance tuple in pygx, i.e. row number
	 * indexes.length is the number of attributes, which we have also chosen as the number of classes.
	 * given an instance tuple index, this function extracts the indices of the attribute-values comprising the tuple
	 * and stores them in an array
	 * One can then use those indices to extract probability values from px
	 * Refactor- make this more intuitive
	 */
	static void getIndexes(int index, int[] indexes, int nValuesPerAttribute) {
		for (int i = indexes.length - 1; i > 0; i--) {
			int dim = nValuesPerAttribute;
			indexes[i] = index % dim;
			index /= dim;
		}
		indexes[0] = index;
	}

	public static double[] computeClassPrior(double[][] px, double[][] pygx) {
		int nClasses = pygx[0].length;
		int nAttributes = px.length;
		int nValuesPerAttribute = px[0].length;
		double []classPrior = new double[nClasses];
		int[] indexes = new int[nAttributes];
		for (int lineCPT = 0; lineCPT < pygx.length; lineCPT++) {
			getIndexes(lineCPT, indexes, nValuesPerAttribute);
			double probaLine = 1.0;
			for (int a = 0; a < indexes.length; a++) {
				probaLine *= px[a][indexes[a]];
			}
			for (int c = 0; c < nClasses; c++) {
				classPrior[c]+=probaLine*pygx[lineCPT][c];
			}
		}
		return classPrior;

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

	/**
	 * Given a distribution, get the furthest possible distribution from it
	 * This is a distribution with the previously least frequent outcome getting all the probability mass
	 * @param furthestDist
	 * @param inputDist
	 */
	public double[] getFurthestDistribution(double[] inputDist){

		double[] furthestDist = new double[inputDist.length];

		int minIndex = 0;

		for (int i = 0; i < inputDist.length; i++) {

			if (inputDist[i] < inputDist[minIndex]){ //find outcome with lowest probability
				minIndex = i;
			}
		}

		for (int i =0; i < furthestDist.length; i++) {
				furthestDist[i] = 0.0;
		}
		furthestDist[minIndex] = 1.0; //set everything to zero except index with lowest value
			// this corresponds to giving the least frequent outcome all the probability mass

		return furthestDist;
	}

	/**
	 * Under assumptions of independence between attributes, take a 2-dimensional
	 * PX and return its 1D version
	 *
	 * Example PX2D:
	 *
	 *       Val1  Val2  Val3 ...
	 * Attr1  0.1  0.01  0.09 ...
	 * Attr2
	 * Attr3
	 * .
	 * .
	 * .
	 */
	public static double[] PX2DTo1D(int nbCombinationsOfValuesPX, double[][] PX2D){

		int[] indexes = new int[PX2D.length];

		/* all possible attribute-value combinations... old method is far more space efficient
		   Because it doesn't store all of them*/
		double[] PX1D = new double[nbCombinationsOfValuesPX];

		for (int i = 0; i < nbCombinationsOfValuesPX; i++) {

			getIndexes(i, indexes, PX2D[0].length);
			PX1D[i] = 1.0;

			for (int a = 0; a < indexes.length; a++) {
				PX1D[i] *= PX2D[a][indexes[a]];
			}
		}

		return PX1D;
	}

	public static void printMatrix(double[][] matrix){
		for (int i =0; i < matrix.length; i++) {
			for (int j = 0; j < matrix[0].length; j++) {
				System.out.print(matrix[i][j] + " ");
			}
			System.out.println();
		}
	}

}
