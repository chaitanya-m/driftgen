package moa.classifiers.trees;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NominalAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.DiscreteAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NullAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NumericAttributeClassObserver;
import moa.core.AutoExpandVector;
import moa.core.DoubleVector;

public class EFDTDecay extends EFDT{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;


	public FloatOption decayOption = new FloatOption("decay",
			'D', "Decay or fading factor",0.9999, 0.0, 1.0);
	public FlagOption exponentialDecayOption = new FlagOption("exponentialDecay", 'E',
			"Decay by exp(decayOption)");

	public FlagOption voterAmnesia = new FlagOption("voterAmnesia", 'V',
			"Whether class distributions forget");

	public FlagOption archiveAmnesia = new FlagOption("archiveAmnesia", 'A',
			"Whether counts n_{ijk} forget");

	public static class DecayNode extends ActiveLearningNode{

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public DecayNode(double[] initialClassObservations) {
			super(initialClassObservations);
			// TODO Auto-generated constructor stub
		}

		public void learnFromInstance(Instance inst, EFDTDecay ht) {
			nodeTime++;

			if (this.isInitialized == false) {
				this.attributeObservers = new AutoExpandVector<AttributeClassObserver>(inst.numAttributes());
				this.isInitialized = true;
			}

			if(ht.voterAmnesia.isSet()){
				//decay
				if(ht.exponentialDecayOption.isSet()){
					this.observedClassDistribution.scaleValues(Math.exp(-ht.decayOption.getValue()));
				}else{
					this.observedClassDistribution.scaleValues(ht.decayOption.getValue());
				}
			}

			this.observedClassDistribution.addToValue((int) inst.classValue(),
					inst.weight());

			if(ht.archiveAmnesia.isSet()){

				// for every attribute observer, for every class, get it's attvaldists and and scale them (effectively scaling counts n_ijk)
				for(AttributeClassObserver obs: this.attributeObservers){
					for (int i = 0; i < ( (NominalAttributeClassObserver)obs).attValDistPerClass.size(); i++) {
						DoubleVector attValDist = ((NominalAttributeClassObserver)obs).attValDistPerClass.get(i);
						if (attValDist != null) {
							if(ht.exponentialDecayOption.isSet()){
								attValDist.scaleValues(Math.exp(-ht.decayOption.getValue()));
							}
							else{
								attValDist.scaleValues(ht.decayOption.getValue());
							}
						}
					}
				}
			}

			for (int i = 0; i < inst.numAttributes() - 1; i++) {
				int instAttIndex = modelAttIndexToInstanceAttIndex(i, inst);
				AttributeClassObserver obs = this.attributeObservers.get(i);
				if (obs == null) {
					obs = inst.attribute(instAttIndex).isNominal() ? ht.newNominalClassObserver() : ht.newNumericClassObserver();
					this.attributeObservers.set(i, obs);
				}
				obs.observeAttributeClass(inst.value(instAttIndex), (int) inst.classValue(), inst.weight());
			}

		}

	}

}



