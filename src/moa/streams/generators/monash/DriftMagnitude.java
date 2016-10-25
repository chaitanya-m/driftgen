package moa.streams.generators.monash;

public class DriftMagnitude {

	/**
	 * Hellinger distance between two normalised probability distributions
	 * @param probDist1 
	 * @param probDist2
	 * @return
	 */
	public static double getHellingerDistance(double probDist1[], double probDist2[]){

		assert(probDist1.length == probDist2.length);

		double diff;
		double magnitude = 0.0;

		for (int i = 0; i < probDist1.length; i++){
			diff = Math.sqrt(probDist1[i]) - Math.sqrt(probDist2[i]);
			magnitude += diff * diff;
		}
		magnitude = magnitude/Math.sqrt(2);
		return magnitude;
	}

	public static double getTotalVariationDistance(double probDist1[], double probDist2[]){

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
