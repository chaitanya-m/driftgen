package moa.classifiers.trees;

import com.google.common.collect.EvictingQueue;
import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.core.driftdetection.ADWIN;
import moa.classifiers.trees.HoeffdingTree.Node;
import moa.classifiers.trees.VFDT.LearningNode;
import moa.classifiers.trees.VFDTLeafWindow.AdaLearningNode;
import moa.core.MiscUtils;
import moa.core.Utils;

public class VFDTLeafWindowADWIN extends VFDTLeafWindow{

	public class AdaLearningNodeADWIN extends AdaLearningNode{

	    protected ADWIN adwin = null; // each leaf has an ADWIN
        protected boolean errorChange = false;

		public AdaLearningNodeADWIN(double[] initialClassObservations) {
			super(initialClassObservations);
			adwin = new ADWIN();
		}

        @Override
        public void learnFromInstance(Instance inst, VFDT ht) {

            // Update ADWIN
            int trueClass = (int) inst.classValue();

            int ClassPrediction = Utils.maxIndex(this.getClassVotes(inst, ht));

            boolean blCorrect = (trueClass == ClassPrediction);

            if (this.adwin == null) {
                this.adwin = new ADWIN();
            }

            double oldError = this.getErrorEstimation();
            this.errorChange = this.adwin.setInput(blCorrect == true ? 0.0 : 1.0);
            if (this.errorChange == true && oldError > this.getErrorEstimation()) {
                this.errorChange = false;
            } // else if a change has been detected and error has increased

            else if (this.errorChange == true){// && this.adwin.getWidth() > 300){ //magic number used in HAT-ADWIN
            	// we want to reduce the instance window to the same size as the ADWIN window, unless the ADWIN window is bigger
            	//shrunkWindow = EvictingQueue.create(windowSize.getMaxValue());
            	if (window.size() - window.remainingCapacity() > this.adwin.getWidth()){

            		System.err.println("Shrinking...");
            		//System.err.println(window.remainingCapacity());
            		// shrink instance window
            		int numRemovalsLeft = window.size() - window.remainingCapacity() - this.adwin.getWidth();
            		System.err.println(numRemovalsLeft);
            		while(numRemovalsLeft > 0){
            			Instance headInst = window.remove(); // find a more efficient way??
            			numRemovalsLeft--;
            			//System.err.println("Removing " + numRemovalsLeft + " " + headInst.classValue());
            		}
            		// we've shrunk the instance window!
            	}
            	else {
            		//System.err.println("Nothing...");
            		//do nothing
            	}

            }

        	// learn as usual for VFDTLeafWindow
            super.learnFromInstance(inst, VFDTLeafWindowADWIN.this);

       		//StringBuilder out = new StringBuilder();
    		//ht.treeRoot.describeSubtree(VFDTLeafWindow.this, out, 8);
    		//System.out.println("============");
    		//System.out.print(out);

        }

        public double getErrorEstimation() {
            if (this.adwin != null) {
                return this.adwin.getEstimation();
            } else {
                return 0;
            }
        }

        public double getErrorWidth() {
            double w = 0.0;
            if (isNullError() == false) {
                w = this.adwin.getWidth();
            }
            return w;
        }

        public boolean isNullError() {
            return (this.adwin == null);
        }

	}

    @Override
	protected LearningNode newLearningNode() {
        return new AdaLearningNodeADWIN(new double[0]);
    }

}
