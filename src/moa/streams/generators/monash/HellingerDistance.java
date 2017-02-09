package moa.streams.generators.monash;

public class HellingerDistance implements DriftMagnitude{

	@Override
	public double getValue(double[] probDist1, double[] probDist2) {

		assert(probDist1.length == probDist2.length);

		double diff;
		double magnitude = 0.0;

		for (int i = 0; i < probDist1.length; i++){
			diff = Math.sqrt(probDist1[i]) - Math.sqrt(probDist2[i]);
			magnitude += diff * diff;
		}
		magnitude = Math.sqrt(magnitude)/Math.sqrt(2);

		return magnitude;
	}

}
