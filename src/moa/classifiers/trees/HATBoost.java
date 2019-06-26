package moa.classifiers.trees;

import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.core.driftdetection.ADWIN;
import moa.classifiers.trees.VFDT.Node;
import moa.classifiers.trees.VFDT.SplitNode;
import moa.core.MiscUtils;
import moa.core.Utils;

public class HATBoost extends HATEFDT{

	public static class HATBoostAdaLearningNode extends AdaLearningNode{

		public HATBoostAdaLearningNode(double[] initialClassObservations, boolean isAlternate) {
			super(initialClassObservations, isAlternate);
			// TODO Auto-generated constructor stub
		}

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public Instance computeErrorChangeAndWeightInst(Instance inst, HAT ht, SplitNode parent, int parentBranch) {

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
