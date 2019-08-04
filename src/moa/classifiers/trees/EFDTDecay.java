package moa.classifiers.trees;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NominalAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.DiscreteAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NullAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NumericAttributeClassObserver;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.trees.EFDT.EFDTNode;
import moa.classifiers.trees.EFDT.EFDTSplitNode;
import moa.classifiers.trees.VFDT.LearningNode;
import moa.classifiers.trees.VFDT.Node;
import moa.classifiers.trees.VFDT.SplitNode;
import moa.classifiers.trees.VFDTLeafWindow.AdaLearningNode;
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

	public class DecayLearningNode extends EFDTLearningNode{

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public DecayLearningNode(double[] initialClassObservations) {
			super(initialClassObservations);
			// TODO Auto-generated constructor stub
		}

		@Override
		public void learnFromInstance(Instance inst, EFDT ht, EFDTSplitNode parent, int parentBranch) {

			super.learnFromInstance(inst, ht, parent, parentBranch);

			if(((EFDTDecay)ht).voterAmnesia.isSet()){
				//decay
				if(((EFDTDecay)ht).exponentialDecayOption.isSet()){
					this.observedClassDistribution.scaleValues(Math.exp(-((EFDTDecay)ht).decayOption.getValue()));
				}else{
					this.observedClassDistribution.scaleValues(((EFDTDecay)ht).decayOption.getValue());
				}
			}

			if(((EFDTDecay)ht).archiveAmnesia.isSet()){

				// for every attribute observer, for every class, get it's attvaldists and and scale them (effectively scaling counts n_ijk)
				for(AttributeClassObserver obs: this.attributeObservers){
					if(obs.getClass() == NominalAttributeClassObserver.class) {
						// It doesn't make much sense to decay a Gaussian where history isn't stored... just decay nominal counts
						for (int i = 0; i < ( (NominalAttributeClassObserver)obs).attValDistPerClass.size(); i++) {
							DoubleVector attValDist = ((NominalAttributeClassObserver)obs).attValDistPerClass.get(i);
							if (attValDist != null) {
								if(((EFDTDecay)ht).exponentialDecayOption.isSet()){
									attValDist.scaleValues(Math.exp(-((EFDTDecay)ht).decayOption.getValue()));
								}
								else{
									attValDist.scaleValues(((EFDTDecay)ht).decayOption.getValue());
								}
							}
						}
					}
				}
			}
		}

	}

	public class DecaySplitNode extends EFDTSplitNode{

		private static final long serialVersionUID = 1L;

		public DecaySplitNode(InstanceConditionalTest splitTest, double[] classObservations, int size) {
			super(splitTest, classObservations, size);
		}

		public DecaySplitNode(InstanceConditionalTest splitTest, double[] classObservations) {
			super(splitTest, classObservations);
		}

		@Override
		public void learnFromInstance(Instance inst, EFDT ht, EFDTSplitNode parent, int parentBranch) {

			super.learnFromInstance(inst, ht, parent, parentBranch);

			if(((EFDTDecay)ht).voterAmnesia.isSet()){
				//decay
				if(((EFDTDecay)ht).exponentialDecayOption.isSet()){
					this.observedClassDistribution.scaleValues(Math.exp(-((EFDTDecay)ht).decayOption.getValue()));
				}else{
					this.observedClassDistribution.scaleValues(((EFDTDecay)ht).decayOption.getValue());
				}
			}

			if(((EFDTDecay)ht).archiveAmnesia.isSet()){

				// for every attribute observer, for every class, get it's attvaldists and and scale them (effectively scaling counts n_ijk)
				for(AttributeClassObserver obs: this.attributeObservers){
					if(obs.getClass() == NominalAttributeClassObserver.class) {
						// It doesn't make much sense to decay a Gaussian where history isn't stored... just decay nominal counts

						for (int i = 0; i < ( (NominalAttributeClassObserver)obs).attValDistPerClass.size(); i++) {
							DoubleVector attValDist = ((NominalAttributeClassObserver)obs).attValDistPerClass.get(i);
							if (attValDist != null) {
								if(((EFDTDecay)ht).exponentialDecayOption.isSet()){
									attValDist.scaleValues(Math.exp(-((EFDTDecay)ht).decayOption.getValue()));
								}
								else{
									attValDist.scaleValues(((EFDTDecay)ht).decayOption.getValue());
								}
							}
						}
					}
				}
			}
		}
	}


	@Override
	protected LearningNode newLearningNode() {
		return new DecayLearningNode(new double[0]);
	}

	@Override
	protected LearningNode newLearningNode(double[] initialClassObservations) {
		return new DecayLearningNode(initialClassObservations);
	}

	@Override
	protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
			double[] classObservations, int size) {
		return new DecaySplitNode(splitTest, classObservations, size);
	}

	@Override
	protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
			double[] classObservations) {
		return new DecaySplitNode(splitTest, classObservations);
	}


}



