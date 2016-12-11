package moa.streams.generators.monash;

import java.util.Arrays;

import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;

public class TestingStub {

	public static void main(String[] args){

		int nAttr = 3;
		int nValPerAttr = 3;
		int nClasses = 3;
		double precision = 0.05;
		int nCombinationsValuesForPX = 1;
		for (int a = 0; a < nAttr; a++) {
			nCombinationsValuesForPX *= nValPerAttr;
		}



		RandomDataGenerator r;
		RandomGenerator rg = new JDKRandomGenerator();
		rg.setSeed(5);
		r = new RandomDataGenerator(rg);

		double px1d[] = new double[nCombinationsValuesForPX];
		double pygx[][] = new double[nCombinationsValuesForPX][nClasses];
		double py[] = new double[nClasses];

		Node.buildTree(nAttr, nValPerAttr, nClasses, precision, r, px1d, pygx, py);

		Arrays.stream(py).forEach(i->System.out.print(i + " "));
		System.out.println();
		Arrays.stream(px1d).forEach(i->System.out.print(i + " "));
		Arrays.stream(pygx).forEach(i->System.out.println(Arrays.toString(i)));



	}

}
