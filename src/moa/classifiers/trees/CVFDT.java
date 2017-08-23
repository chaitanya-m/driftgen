package moa.classifiers.trees;

import java.util.List;

import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.driftdetection.ADWIN;
import moa.classifiers.trees.HATADWIN.AdaSplitNode;
import moa.classifiers.trees.HATADWIN.NewNode;
import moa.classifiers.trees.HoeffdingTree.ActiveLearningNode;
import moa.classifiers.trees.HoeffdingTree.FoundNode;
import moa.classifiers.trees.HoeffdingTree.InactiveLearningNode;
import moa.classifiers.trees.HoeffdingTree.Node;
import moa.classifiers.trees.HoeffdingTree.SplitNode;
import moa.classifiers.trees.VFDTWindow.AdaNode;
import moa.core.AutoExpandVector;
import moa.core.MiscUtils;
import moa.core.Utils;

public class CVFDT extends VFDTWindow {

	// lets begin by adding counters and split tests to every splitnode
	// then we can add a list of alternates to grow the results of the split tests
	// then when we incorporate alternate replacement, we will finally have our tree

	// How do we add a counter?

	public interface CVFDTAdaNode extends AdaNode{
		public void learnFromInstance(Instance inst, CVFDT ht, SplitNode parent, int parentBranch,
				AutoExpandVector<Long> reachedLeafIDs);
	}


	public static class CVFDTSplitNode extends AdaSplitNode implements CVFDTAdaNode {

		public CVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations) {
			super(splitTest, classObservations);
			// TODO Auto-generated constructor stub
		}

		@Override
		public void learnFromInstance(Instance inst, CVFDT ht, SplitNode parent, int parentBranch,
				AutoExpandVector<Long> reachedLeafIDs){

			//System.out.println("Main Tree is of depth " + ht.treeRoot.subtreeDepth());

			assert (this.createdFromInitializedLearningNode = true);

			this.observedClassDistribution.addToValue((int) inst.classValue(), inst.weight());

			for (int i = 0; i < inst.numAttributes() - 1; i++) {
				int instAttIndex = modelAttIndexToInstanceAttIndex(i, inst);
				AttributeClassObserver obs = this.attributeObservers.get(i);
				if (obs == null) {
					obs = inst.attribute(instAttIndex).isNominal() ? ht.newNominalClassObserver() : ht.newNumericClassObserver();
					this.attributeObservers.set(i, obs);
				}
				obs.observeAttributeClass(inst.value(instAttIndex), (int) inst.classValue(), inst.weight());
			}
			// DRY... for now this code is repeated...

			int childBranch = this.instanceChildIndex(inst);
			Node child = this.getChild(childBranch);
			if (child != null) {
				((AdaNode) child).learnFromInstance(inst, ht, this, childBranch, reachedLeafIDs);
			}

		}
	}
}


