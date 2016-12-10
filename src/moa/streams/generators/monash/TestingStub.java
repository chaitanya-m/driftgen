package moa.streams.generators.monash;

import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;


public class TestingStub {


	public static void main(String[] args){

		RandomDataGenerator r;

		RandomGenerator rg = new JDKRandomGenerator();
		rg.setSeed(5);
		r = new RandomDataGenerator(rg);

		Node root = new Node(1, 2, 7, 0.05, r);
		root.setLevel(0);
		root.split();
	}

}
