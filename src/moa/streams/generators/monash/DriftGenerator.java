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
import java.util.List;

public abstract class DriftGenerator extends DriftOptionHandler implements InstanceStream {

	private static final long serialVersionUID = 6853166101160277496L;

	public MultiChoiceOption distanceMeasure = new MultiChoiceOption("distanceType", 't',
			"The distance measure used", new String[]{"Hellinger Distance", "Total Variation Distance"}, new String[]{"L2 distance", "L1 distance"}, 0 );

	public IntOption nAttributes = new IntOption("nAttributes", 'n',
			"Number of attributes as parents of the class", 2, 1, 10);

	public IntOption nValuesPerAttribute = new IntOption("nValuesPerAttribute", 'v',
			"Number of values per attribute", 2, 2, 5);

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



	/* Refactor:
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
			int chosenClass = r.nextSecureInt(0, lineCPT.length - 1);

			for (int c = 0; c < lineCPT.length; c++) {
				if (c == chosenClass) {
					lineCPT[c] = 1.0;
				} else {
					lineCPT[c] = 0.0;
				}
			}
		}

	}

	public static void generateRandomPyGivenX(double[][] pygx, RandomDataGenerator r,double alphaDirichlet) {
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

	public static double computeMagnitudePX(int nbCombinationsOfValuesPX, double[][] base_px,
			double[][] drift_px) {

		int[] indexes = new int[base_px.length];
		double[] baseCovariate = new double[nbCombinationsOfValuesPX]; //all possible attribute-value combinations... old method is far more space efficient
		double[] driftCovariate = new double[nbCombinationsOfValuesPX];

		for (int i = 0; i < nbCombinationsOfValuesPX; i++) {

			getIndexes(i, indexes, base_px[0].length);
			baseCovariate[i] = 1.0;
			driftCovariate[i] = 1.0;

			for (int a = 0; a < indexes.length; a++) {
				baseCovariate[i] *= base_px[a][indexes[a]];
				driftCovariate[i] *= drift_px[a][indexes[a]];
			}
		}
		return driftMag.getValue(baseCovariate, driftCovariate);
	}

	public static double computeMagnitudePYGX(double[][] base_pygx, double[][] drift_pygx) {
		double magnitude = 0.0;
		for (int i = 0; i < base_pygx.length; i++) {

			double partialM = driftMag.getValue(base_pygx[i], drift_pygx[i]);
			assert (partialM == 0.0 || partialM == 1.0);
			magnitude += partialM;
		}
		magnitude /= base_pygx.length;
		return magnitude;
	}

	public static double computeMagnitudeClassPrior(double[] baseClassP, double[] driftClassP) {

		return driftMag.getValue(baseClassP, driftClassP);
	}

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


}
