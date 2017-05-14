/*
 * Created by Lee Loong Kuan on 5/09/2015.
 * Interface that represents a basic Concept Drift Generator
 * Contains static methods to generate probabilities
 */

package moa.streams.generators.categorical;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.random.RandomDataGenerator;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Attribute;

import moa.core.FastVector;
import moa.options.AbstractOptionHandler;
import moa.streams.InstanceStream;

public abstract class CategoricalDriftGenerator extends AbstractOptionHandler implements InstanceStream {

	private static final long serialVersionUID = -3582081197584644644L;

	public IntOption nAttributes = new IntOption("nAttributes", 'n',
			"Number of attributes as parents of the class", 2, 1, 10);
	// Number of attributes.
	public IntOption nValuesPerAttribute = new IntOption("nValuesPerAttribute", 'v',
			"Number of values per attribute", 2, 2, 5);
	// Number of values each attribute can take
	public IntOption burnInNInstances = new IntOption("burnInNInstances", 'b',
			"Number of instances before the start of the drift", 10000, 0, Integer.MAX_VALUE);
	// Number of instances trained on before drift starts.
	public FloatOption driftMagnitudePrior = new FloatOption("driftMagnitudePrior", 'i',
			"Magnitude of the drift between the starting probability and the one after the drift."
					+ " Magnitude is expressed as the Hellinger distance [0,1]", 0.5, 1e-20, 0.9);

	/* There's a px table; imagine this as having attributes for row labels and attribute values
	 * for column labels. The cells contain probability values.
	 * 
	 * The sum of each row of the matrix should equal 1.
	 * 
	 * When you set a value for the drift magnitude, it is the values in this table that are 
	 * altered to produce a drift of a given magnitude. This is of course covariate drift.
	 * 
	 * How exactly is this generated?
	 *
	 **/

	public FloatOption driftMagnitudeConditional = new FloatOption("driftMagnitudeConditional",
			'o',
			"Magnitude of the drift between the starting probability and the one after the drift."
					+ " Magnitude is expressed as the Hellinger distance [0,1]", 0.5, 1e-20, 0.9);

	/* There's a pygx table.
	 * 
	 * The row labels are tuples of attribute-values that together give us an instance,
	 * i.e. each row is labelled with an instance.
	 * 
	 * The column labels are the class labels.
	 * 
	 * The cells once again contain a probability assignment.
	 * 
	 * But in this case, each row is the class distribution for a given instance, so each
	 * row sums up to 1.
	 * 
	 * Since the tuples each have an individual class distribution summing up to 1, they are effectively independent.
	 * i.e. changing the class distribution for one tuple doesn't change it for other tuples. 
	 * 
	 * When you set a value for conditional drift magnitude, ? 
	 * */
	public FloatOption precisionDriftMagnitude = new FloatOption(
			"epsilon",
			'e',
			"Precision of the drift magnitude for p(x) (how far from the set magnitude is acceptable)",
			0.01, 1e-20, 1.0);
	public FlagOption driftConditional = new FlagOption("driftConditional", 'c',
			"States if the drift should apply to the conditional distribution p(y|x).");
	public FlagOption driftPriors = new FlagOption("driftPriors", 'p',
			"States if the drift should apply to the prior distribution p(x). ");
	public IntOption seed = new IntOption("seed", 'r', "Seed for random number generator", -1,
			Integer.MIN_VALUE, Integer.MAX_VALUE);

	FastVector<Attribute> getHeaderAttributes(int nAttributes, int nValuesPerAttribute) {

		FastVector<Attribute> attributes = new FastVector<>();
		List<String> attributeValues = new ArrayList<String>();
		for (int v = 0; v < nValuesPerAttribute; v++) {
			attributeValues.add("v" + (v + 1));
		} // generate attribute-value labels, one for each value, and store in a list
		for (int i = 0; i < nAttributes; i++) {
			attributes.addElement(new Attribute("x" + (i + 1), attributeValues));
		}
		List<String> classValues = new ArrayList<String>();

		for (int v = 0; v < nValuesPerAttribute; v++) {
			classValues.add("class" + (v + 1));
		} // There are as many class values as attribute values. How does this arbitrary choice impact us?
		attributes.addElement(new Attribute("class", classValues));

		return attributes;
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
			// normalizing each row!
			for (int v = 0; v < px[a].length; v++) {
				px[a][v] /= sum;

			}
			/* Potential improvement? Note that this is an entirely new distribution. To generate distributions that are
			 * closer in magnitude, why can't we just divide this by a further factor to get 
			 * a drift "difference" to add to the existing distribution?? 
			 *  */			

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
	 * find the probability of every single tuple before and after drift, and compute hellinger distance
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
	public static double computeMagnitudePX(int nbCombinationsOfValuesPX, double[][] base_px,
			double[][] drift_px) {
		int[] indexes = new int[base_px.length];
		double m = 0.0;
		for (int i = 0; i < nbCombinationsOfValuesPX; i++) {
			getIndexes(i, indexes, base_px[0].length);
			double p = 1.0, q = 1.0;
			for (int a = 0; a < indexes.length; a++) {
				p *= base_px[a][indexes[a]];
				q *= drift_px[a][indexes[a]];
			}
			double diff = Math.sqrt(p) - Math.sqrt(q);
			m += diff * diff;
		}
		m = Math.sqrt(m) / Math.sqrt(2);
		return m;
	}

	public static double computeMagnitudePYGX(double[][] base_pygx, double[][] drift_pygx) {
		double magnitude = 0.0;
		for (int i = 0; i < base_pygx.length; i++) {
			double partialM = 0.0;
			for (int c = 0; c < base_pygx[i].length; c++) {
				double diff = Math.sqrt(base_pygx[i][c]) - Math.sqrt(drift_pygx[i][c]);
				partialM += diff * diff;
			}
			partialM = Math.sqrt(partialM) / Math.sqrt(2);

			//It should be 0 or 1 for each row?? So any generated distribution can either differ by 0 or 1? Why???

			assert (partialM == 0.0 || partialM == 1.0);
			magnitude += partialM;
		}
		magnitude /= base_pygx.length;
		return magnitude;
	}

	public static double computeMagnitudeClassPrior(double[] baseClassP, double[] driftClassP) {
		double magnitude = 0.0;
		for (int c = 0; c < baseClassP.length; c++) {
			double diff = Math.sqrt(baseClassP[c]) - Math.sqrt(driftClassP[c]);
			magnitude += diff * diff;
		}
		magnitude = Math.sqrt(magnitude) / Math.sqrt(2);
		return magnitude;
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
			//start with the max class index
			int dim = nValuesPerAttribute;
			// this is a constant, and dim never changes!!! dim must be the number of dimensions for each attribute
			indexes[i] = index % dim;
			// the i'th element of indexes is set to tuple index modulo nValuesPerAttribute
			index /= dim;
		}
		indexes[0] = index;
	}

	public static double[] computeClassPrior(double[][] px, double[][] pygx) {
		int nClasses = pygx[0].length;
		/* each row stores the py values for a given x. ie probability of a class given an attribute.
		   so the number of columns = number of classes = length of a row, i.e. pygx[0].length
		   there are as many rows as all possible combinations of attributes
		 in other words, each cell has a probability number for the class corresponding to its column and the x-value corresponding to the row
		 */
		int nAttributes = px.length;
		// self explanatory. number of attributes = px.length = number of rows in pygx
		int nValuesPerAttribute = px[0].length;
		// each column contains a probability for a value of that row's attribute
		// so px stores the probabilities of each individual attribute value
		double []classPrior = new double[nClasses];
		/* This should be a prior for the class distribution
		 * to compute it... we must use the Law of Total Probability
		 * py = pygx1*px1 + pygx2*px2 + ...
		 * here x1, x2 etc are instance tuples, not attribute-values
		 * how do we find px1, px2...?
		 */
		int[] indexes = new int[nAttributes];
		// as many indexes as attributes
		for (int lineCPT = 0; lineCPT < pygx.length; lineCPT++) {
			//start with the first tuple's class distribution, iterate through the distributions for each tuple
			getIndexes(lineCPT, indexes, nValuesPerAttribute);
			double probaLine = 1.0;
			for (int a = 0; a < indexes.length; a++) {
				probaLine *= px[a][indexes[a]];
				/* for each tuple x1, this should give us the probability of that tuple by multiplying all the individual probabilities of 
				 * attribute-values composing the tuple
				 * getIndexes probably gathers these
				 * we need an attribute-value probability from each row of the px table (i.e. one per attribute), and multiply all of them
				 * so indexes must contain which one of the attribute-values for each attribute has it's probability multiplied
				 */
			}
			for (int c = 0; c < nClasses; c++) {
				classPrior[c]+=probaLine*pygx[lineCPT][c];
				/* probaline must be the px1, px2,... values.
				 * The px1, px2... values must be computed by assuming the components are independent, and multiplying their probability values
				 * So to compute px1, the probability values of the attribute-values would be multiplied... with an independence assumption (though they're dependent)
				 * these will have to be retrieved from the px table
				 * */

			}
		}
		return classPrior;

	}

}