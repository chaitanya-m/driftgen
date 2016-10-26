package moa.streams.generators.monash;

public class TotalVariationDistance implements DriftMagnitude{

	@Override
	public double getValue(double[] probDist1, double[] probDist2) {

		assert(probDist1.length == probDist2.length);

		double diff;
		double magnitude = 0.0;

		for (int i = 0; i < probDist1.length; i++){
			diff = (probDist1[i] > probDist2[i]) ? (probDist1[i] - probDist2[i]) : (probDist2[i] - probDist1[i]);
			magnitude += diff;
		}
		magnitude = magnitude/2;
		return magnitude;
	}


}
