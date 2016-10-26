package moa.streams.generators.monash;

public interface DriftMagnitude {

	/**
	 * Distance between two normalised probability distributions
	 * @param probDist1
	 * @param probDist2
	 * @return
	 */

	public double getValue(double probDist1[], double probDist2[]);
}
