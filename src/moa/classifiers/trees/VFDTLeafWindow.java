package moa.classifiers.trees;

/*
 * VFDTLeafWindow must maintain ADWIN windows for errors at all the leaves.
 * We don't really care whether examples are transferred down to children or not on splitting.
 * What is important is that each leaf has a window of examples
 * And that that window is resized depending on ADWIN
 * So when ADWIN resizes itself, so should the window of examples stored at each leaf
 * Imagine a policy also where instead of a window at each leaf, one has a fading factor
 * The fading factor creeps up as long as there is no change detected. If a change is detected, it is halved, and it creeps up again.
 */


import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.AbstractClassifier;
import moa.classifiers.trees.VFDT.ActiveLearningNode;
import moa.classifiers.trees.VFDT.FoundNode;
import moa.classifiers.trees.VFDT.LearningNode;
import moa.classifiers.trees.VFDT.Node;
import moa.core.Measurement;

public class VFDTLeafWindow extends VFDT {

	private static final long serialVersionUID = 1L;


	public static class AdaLearningNode extends LearningNodeNBAdaptive{

		public AdaLearningNode(double[] initialClassObservations) {
			super(initialClassObservations);
			// TODO Auto-generated constructor stub
		}

	}


    @Override
    public void trainOnInstanceImpl(Instance inst) {


    }



}
