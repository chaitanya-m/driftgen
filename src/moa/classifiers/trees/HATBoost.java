package moa.classifiers.trees;

import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.driftdetection.ADWIN;
import moa.classifiers.trees.VFDT.SplitNode;
import moa.core.MiscUtils;
import moa.core.Utils;

public class HATBoost extends HATEFDT{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;


	public static class HATBoostAdaSplitNode extends AdaSplitNode{

		public HATBoostAdaSplitNode(InstanceConditionalTest splitTest, double[] classObservations) {
			super(splitTest, classObservations);
		}	
		public HATBoostAdaSplitNode(InstanceConditionalTest splitTest, double[] classObservations,
				int size) {
			super(splitTest, classObservations, size);
		}	
		
		public HATBoostAdaSplitNode(InstanceConditionalTest splitTest, double[] classObservations,
				boolean isAlternate) {
			super(splitTest, classObservations, isAlternate);
		}
		public HATBoostAdaSplitNode(InstanceConditionalTest splitTest, double[] classObservations, int size,
				boolean isAlternate) {
			super(splitTest, classObservations, size, isAlternate);
		}

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		
		@Override
		public Instance computeErrorChangeAndWeightInst(Instance inst, HATEFDT ht, SplitNode parent, int parentBranch) {

			int trueClass = (int) inst.classValue();
			//New option vore
			int k = MiscUtils.poisson(1.0, this.classifierRandom);
			Instance weightedInst = inst.copy();
			if (k > 0) {
				//weightedInst.setWeight(inst.weight() * k);
			}

			int ClassPrediction = 0;
			Node leaf = filterInstanceToLeaf(inst, this.getParent(), parentBranch).node;
			if (leaf != null) {
				ClassPrediction = Utils.maxIndex(leaf.getClassVotes(inst, ht));
			}

			boolean blCorrect = (trueClass == ClassPrediction);
			//*** If you get it wrong, weight higher. Weight cascades.
            if (!blCorrect){
                if (k > 0) {
                    weightedInst.setWeight(inst.weight() * k); // a domino effect of increasing the weight down to the leaf
                    // subtree boosting
                }
            }
			//***
			if (this.estimationErrorWeight == null) {
				this.estimationErrorWeight = new ADWIN();
			}
			this.ErrorChange = this.estimationErrorWeight.setInput(blCorrect == true ? 0.0 : 1.0);

			return weightedInst;
		}
	}
	
}
