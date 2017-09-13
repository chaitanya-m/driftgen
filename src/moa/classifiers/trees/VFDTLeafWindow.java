package moa.classifiers.trees;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Random;

import org.apache.commons.math3.util.Pair;

import com.github.javacliparser.IntOption;
import com.google.common.collect.EvictingQueue;

import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.AbstractClassifier;
import moa.classifiers.bayes.NaiveBayes;
import moa.classifiers.trees.VFDT.ActiveLearningNode;
import moa.classifiers.trees.VFDT.FoundNode;
import moa.classifiers.trees.VFDT.LearningNode;
import moa.classifiers.trees.VFDT.Node;
import moa.classifiers.trees.VFDT.SplitNode;
import moa.classifiers.trees.VFDTSlidingWindow.AdaLearningNode;
import moa.classifiers.trees.VFDTSlidingWindow.AdaNode;
import moa.core.AutoExpandVector;
import moa.core.Measurement;
import moa.core.Utils;

/*
 * VFDTLeafWindow must maintain ADWIN windows for errors at all the leaves.
 * We don't really care whether examples are transferred down to children or not on splitting.
 * What is important is that each leaf has a window of examples
 * And that that window is resized depending on ADWIN
 * So when ADWIN resizes itself, so should the window of examples stored at each leaf
 * Define a max window size for the leaf for practical reasons
 * Now we have an issue: how do we handle splits? When a change is detected, do we shrink the leaf window? Is this a little premature?
 *
 * Not really. It's way better than replacing the whole model.
 *
 * How about how it compares with HAT-ADWIN?
 *
 * We could do a comparison. HAT-ADWIN splits on the old window, but not on the new one
 *
 * What we have will split on the latest, truncated window.
 *
 * It is certainly worth studying.
 */

/*
 * Imagine a policy also where instead of a window at each leaf, one has a fading factor
 * The fading factor creeps up as long as there is no change detected. If a change is detected, it is halved, and it creeps up again.
 *
 */

public class VFDTLeafWindow extends VFDT {

	private static final long serialVersionUID = 1L;

    public IntOption windowSize = new IntOption("windowSize", 'W',
            "Maximum moving window size", 5000, 0, Integer.MAX_VALUE);
    // Make the max window size sort of big if ADWIN is being used so ADWIN is mostly responsible

	public class AdaLearningNode extends LearningNodeNBAdaptive{
		// Why was this nested class static? Static nested classes don't have a reference to the enclosing object
		// But the enclosing object is being passed around in previous code
		// So let's just switch from static nested class to inner class

	    protected EvictingQueue<Instance> window = null; //each leaf gets a window

	    // Now... we need to ensure that when an instance is learned, we update the ADWIN, and to forget an example if we've exceeded window size
	    // If ADWIN needs to reset, we find out the size of the resized window and shrink our data window to the same size,
	    // as long as it is not larger than current window
	    // when we forget an example we just directly delete it from the class distribution by reducing the count

		public AdaLearningNode(double[] initialClassObservations) {
			super(initialClassObservations);
			window = EvictingQueue.create(VFDTLeafWindow.this.windowSize.getValue());
		}

		// So far we've made this class an inner class instead of a static nested class so it has a reference to the enclosing
		// object; we've created a window and an ADWIN instance and we've initialized them both in the constructor
		// So every time a leaf is created it comes with a window and an ADWIN
		// Now we need to update the window and also the ADWIN. By window we mean instance window. ADWIN has a separate error window.

		// Note that one issue we run into is: what happens when ADWIN detects a change? Should we resize the example-window immediately?
		// We arbitrarily decide to do so.

		// First, create a method to forget an instance at the leaf

        public void forgetInstance(Instance inst, VFDT ht) {
        	inst.setWeight(-inst.weight());
        	super.learnFromInstance(inst, ht);
        }

        // Now, when does the window and adwin get updated? At the time of learning...

        @Override
        public void learnFromInstance(Instance inst, VFDT ht) {

        	//note we never use ht because we've now switched to an internal class (nonstatic nested class) from a static nested class
        	// ... just keeping the override though

        	// We add the instance to the window, forgetting from distribution the instance that will be dequeued if window is at capacity

        	Instance forgetInst;
            if(window.remainingCapacity() == 0){

            	forgetInst = window.peek();
                forgetInstance(forgetInst, ht);
            }

            window.add(inst); //Order of statements important because EvictingQueue automatically evicts when window at capacity

        	// Then we update the distribution with the instance just added

            super.learnFromInstance(inst, VFDTLeafWindow.this);

       		//StringBuilder out = new StringBuilder();
    		//ht.treeRoot.describeSubtree(VFDTLeafWindow.this, out, 8);
    		//System.out.println("============");
    		//System.out.print(out);

        }

        // Next, we need to update ADWIN, and based on whether ADWIN resizes it's error window, we also need to resize the instance window
	}

    @Override
	protected LearningNode newLearningNode() {
        return new AdaLearningNode(new double[0]);
    }

    @Override
	protected LearningNode newLearningNode(double[] initialClassObservations) {
        return new AdaLearningNode(initialClassObservations);
    }

}
