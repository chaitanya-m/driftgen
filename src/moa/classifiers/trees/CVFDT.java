package moa.classifiers.trees;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;

import com.github.javacliparser.IntOption;
import com.google.common.collect.EvictingQueue;
import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.classifiers.trees.VFDTWindow.AdaNode;
import moa.core.AutoExpandVector;
import moa.core.Utils;

public class CVFDT extends VFDTWindow {

	// lets begin by adding counters and split tests to every splitnode
	// then we can add a list of alternates to grow the results of the split tests
	// then when we incorporate alternate replacement, we will finally have our tree

	// How do we add a counter?

	public IntOption testPhaseFrequency = new IntOption("testPhaseFrequency", 'f',
			"How frequently alternates are tested", 10000, 0, Integer.MAX_VALUE);

	public IntOption testPhaseLength = new IntOption("testPhaseLength", 'L',
			"How long each test phase for alternates is", 200, 0, Integer.MAX_VALUE);

	public interface CVFDTAdaNode extends AdaNode {

		public int getTestPhaseError();

		public void killSubtree(CVFDT ht);

		public long getNodeTime();
	}

	public class CVFDTSplitNode extends AdaSplitNode implements AdaNode, CVFDTAdaNode {

		private static final long serialVersionUID = 1L;

		private boolean inAlternateTestPhase = false;

		private int testPhaseError = 0;

		private int nodeTime = 0;

		@Override
		public int getTestPhaseError() {
			return testPhaseError;
		}

		// maintain a mapping from attributes to alternate trees
		protected Map<AttributeSplitSuggestion, CVFDTAdaNode> alternates;

		// static nested classes don't have a reference to the containing object while nonstatic nested classes do
		// so changing over to a nonstatic nested class

		public CVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations) {
			super(splitTest, classObservations);
			alternates = new HashMap<AttributeSplitSuggestion, CVFDTAdaNode>();
		}

		public CVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations, int size) {
			super(splitTest, classObservations, size);
			alternates = new HashMap<AttributeSplitSuggestion, CVFDTAdaNode>();
		}

		public CVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations, boolean isAlternate) {
			super(splitTest, classObservations, isAlternate);
			alternates = new HashMap<AttributeSplitSuggestion, CVFDTAdaNode>();

		}

		public CVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations, int size, boolean isAlternate) {
			super(splitTest, classObservations, size, isAlternate);
			alternates = new HashMap<AttributeSplitSuggestion, CVFDTAdaNode>();
		}
		/*
		@Override
		public void learnFromInstance(Instance inst, VFDTWindow ht, SplitNode parent, int parentBranch,
				AutoExpandVector<Long> reachedLeafIDs){

			// if you're in a test phase
			if (nodeTime % testPhaseFrequency.getValue() < testPhaseLength.getValue()) {
				inAlternateTestPhase = true;

				//increment error
				int trueClass = (int) inst.classValue();
				int ClassPrediction = 0;
				Node leaf = filterInstanceToLeaf(inst, this.getParent(), parentBranch).node;
				if (leaf != null) {
					ClassPrediction = Utils.maxIndex(leaf.getClassVotes(inst, ht));
				} // what happens if leaf is null?
				boolean predictedCorrectly = (trueClass == ClassPrediction);

				if(!predictedCorrectly){
					testPhaseError++;
				}

				// if you're at the end of the phase and not an alternate but have alternates, check if a replacement is required and replace
				if (nodeTime % testPhaseFrequency.getValue() == testPhaseLength.getValue() - 1){
					System.out.println(">>> " + getNumInstances());

					if(!this.alternates.isEmpty()){
						System.out.println("=======================================");
					}

					if(!this.isAlternate() && !this.alternates.isEmpty()){

						System.err.println("Picking a replacement");
						//pick the option with lowest test phase error... and replace...
						int lowestError = testPhaseError;

						AdaNode bestAlternate = null;

						Iterator<CVFDTAdaNode> iter = alternates.values().iterator();

						while (iter.hasNext()){
							AdaNode alt = iter.next();

							if(((CVFDTAdaNode)alt).getTestPhaseError() < lowestError){

								lowestError = ((CVFDTAdaNode)alt).getTestPhaseError();
								bestAlternate = alt;
							}
						}

						// replace with best alternate!!
						if(bestAlternate != null){ //DRY!!! (copied from HAT-ADWIN)
							// Switch alternate tree

							System.err.println(getNumInstances() + "  " + " SWITCHING");

							ht.activeLeafNodeCount -= this.numberLeaves();
							ht.activeLeafNodeCount += bestAlternate.numberLeaves();
							this.killSubtree((CVFDT)ht);
							bestAlternate.setAlternateStatusForSubtreeNodes(false);
							bestAlternate.setMainlineNode(null);

							if (!this.isRoot()) {
								bestAlternate.setRoot(false);
								bestAlternate.setParent(this.getParent());
								this.getParent().setChild(parentBranch, (Node)bestAlternate);

								//((AdaSplitNode) parent.getChild(parentBranch)).alternateTree = null;
							} else {
								// Switch root tree
								bestAlternate.setRoot(true);
								bestAlternate.setParent(null);
								ht.treeRoot = (Node)bestAlternate;
							}
							ht.switchedAlternateTrees++;
						}
						else{
							// ALERT: TODO: prune alternates
						}

					}
					testPhaseError = 0; //reset test phase error
					return; // skip learning and split evaluation!
				}

				// if you're alternate or not an alternate but have alternates, in the middle of the phase, just increment error and skip learning!

				else if (this.isAlternate()){

					return; // skip learning and split evaluation!
				}

				else if (this.alternates.isEmpty()){
					return;
				}

			}

			// if you're not in a test phase, continue as usual
			else {
				inAlternateTestPhase = false;
			}
			if(!inAlternateTestPhase) {
				// remember you're not supposed to learn anything if you happen to be in a test phase and happen to have alternates...
				// or if you happen to be an alternate
				// You're just supposed to keep track of error

				// System.out.println("Main Tree is of depth " + ht.treeRoot.subtreeDepth());

				// First, update counts
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
				// Counts have been updated for this node (make certain of this...)

				// We need to re-evaluate splits at each mainline split node...

				if(!this.isAlternate() && nodeTime%200 ==0){ //magic number alert
					//System.err.println("Re-evaluating internal node splits");
					reEvaluateBestSplit();
				}

				// Going down alternate paths here

				if (this.alternates != null && !this.isAlternate()) {

					Iterator<CVFDTAdaNode> iter = alternates.values().iterator();

					while (iter.hasNext()){
						AdaNode alt = iter.next();
						alt.learnFromInstance(inst, ht, this.getParent(), parentBranch, reachedLeafIDs);
					}
				}

				int childBranch = this.instanceChildIndex(inst);
				Node child = this.getChild(childBranch);
				if (child != null) {
					((AdaNode) child).learnFromInstance(inst, ht, this, childBranch, reachedLeafIDs);
				}
			}
			nodeTime++;

		}

		// DRY... code duplicated from ActiveLearningNode in VFDT.java
		public AttributeSplitSuggestion[] getBestSplitSuggestions(
				SplitCriterion criterion, VFDT ht) {
			List<AttributeSplitSuggestion> bestSuggestions = new LinkedList<AttributeSplitSuggestion>();
			double[] preSplitDist = this.observedClassDistribution.getArrayCopy();
			if (!ht.noPrePruneOption.isSet()) {
				// add null split as an option
				bestSuggestions.add(new AttributeSplitSuggestion(null,
						new double[0][], criterion.getMeritOfSplit(
								preSplitDist, new double[][]{preSplitDist})));
			}
			for (int i = 0; i < this.attributeObservers.size(); i++) {
				AttributeClassObserver obs = this.attributeObservers.get(i);
				if (obs != null) {
					AttributeSplitSuggestion bestSuggestion = obs.getBestEvaluatedSplitSuggestion(criterion,
							preSplitDist, i, ht.binarySplitsOption.isSet());
					if (bestSuggestion != null) {
						bestSuggestions.add(bestSuggestion);
					}
				}
			}
			return bestSuggestions.toArray(new AttributeSplitSuggestion[bestSuggestions.size()]);
		}
*/
        @Override
		public void killSubtree(CVFDT ht) {
            for (Node child : this.children) {
                if (child != null) {
                    //Delete alternate tree if it exists
                    if (child instanceof CVFDTSplitNode && !((CVFDTSplitNode) child).alternates.isEmpty()) {
                    	Iterator iter = alternates.values().iterator();
                    	while (iter.hasNext()){
                    		((CVFDTAdaNode)(iter.next())).killSubtree(ht);
                    	}
                        ht.prunedAlternateTrees++;
                    }
                    //Recursive delete of SplitNodes
                    if (child instanceof AdaSplitNode) {
                        ((CVFDTAdaNode) child).killSubtree(ht);
                    }
                    else if (child instanceof ActiveLearningNode) {
                        child = null;
                        ht.activeLeafNodeCount--;
                    }
                    else if (child instanceof InactiveLearningNode) {
                        child = null;
                        ht.inactiveLeafNodeCount--;
                    }
                    else{

                    }
                }
            }
        }

/*
		protected void reEvaluateBestSplit() {

			int currentSplit = -1; // for no split

			//lets first find out X_a, the current split
			if(this.splitTest != null){
				currentSplit = this.splitTest.getAttsTestDependsOn()[0];
				// given the current implementations in MOA, we're only ever expecting one int to be returned
			}

			// Now let's find the best split X_n other than X_a that doesn't already have an attached alternate subtree
			// Would that be an improvement over CVFDT? Does CVFDT always compute the X_n, even if it won't be used because it has an attached alternate?
			SplitCriterion splitCriterion = (SplitCriterion) getPreparedClassOption(CVFDT.this.splitCriterionOption);

			double hoeffdingBound = computeHoeffdingBound(splitCriterion.getRangeOfMerit(this.getObservedClassDistribution()),
					CVFDT.this.splitConfidenceOption.getValue(), this.observedClassDistribution.sumOfValues());

			AttributeSplitSuggestion[] bestSplitSuggestions = this.getBestSplitSuggestions(splitCriterion, CVFDT.this);
			Arrays.sort(bestSplitSuggestions);

			AttributeSplitSuggestion bestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 1];
			AttributeSplitSuggestion secondBestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 2];

			double tieThreshold = CVFDT.this.tieThresholdOption.getValue();
			double deltaG = bestSuggestion.merit - secondBestSuggestion.merit;

			if(currentSplit == bestSuggestion.splitTest.getAttsTestDependsOn()[0])
			{
				// do nothing, because the current split is the best split
				// this is different but equivalent to algorithm listing...
			}

			else if (deltaG > hoeffdingBound
					|| (hoeffdingBound < tieThreshold && deltaG > tieThreshold / 2)) {

				// if it doesn't already have an alternate subtree, build one
				if(!alternates.containsKey(bestSuggestion)) { // the hashcodes should match... this should work
					//System.err.println("Building alt subtree");

					System.err.println(getNumInstances() + " Building alt subtree ");

					this.alternates.put(bestSuggestion, (CVFDTAdaNode)newLearningNode(true, false, this));

					// we've just created an alternate, but only if the key is not already contained
				}
			}
		}
*/
		@Override
		public long getNodeTime() {
			return nodeTime;
		}
	}

	public class CVFDTLearningNode extends AdaLearningNode implements AdaNode, CVFDTAdaNode {

		private static final long serialVersionUID = -7477758640913802578L;

		private boolean inAlternateTestPhase = false;

		private int testPhaseError = 0;

		private long nodeTime = 0;

		@Override
		public int getTestPhaseError() {
			return testPhaseError;
		}

		public CVFDTLearningNode(double[] initialClassObservations) {
			super(initialClassObservations);
		}

		public CVFDTLearningNode(double[] initialClassObservations, boolean isAlternate, boolean isRoot,
				AdaNode mainlineNode) {
			super(initialClassObservations, isAlternate, isRoot, mainlineNode);
		}

		@Override
		public void learnFromInstance(Instance inst, VFDTWindow ht, SplitNode parent, int parentBranch,
				AutoExpandVector<Long> reachedLeafIDs) {

			if (nodeTime % testPhaseFrequency.getValue() < testPhaseLength.getValue()) {
				inAlternateTestPhase = true;
				if (inst.classValue() != Utils.maxIndex(this.getClassVotes(inst, ht))){
					testPhaseError++;
				}
			}

			else {
				testPhaseError = 0;
				inAlternateTestPhase = false;
			}

			if(!inAlternateTestPhase){
				super.learnFromInstance(inst, ht, parent, parentBranch, reachedLeafIDs);
			}
			nodeTime++;
		}

		@Override
		public void killSubtree(CVFDT ht) {

		}

		@Override
		public long getNodeTime() {
			return nodeTime;
		}
	}


    @Override
    public void trainOnInstanceImpl(Instance inst) {

    	// If treeRoot is null, create a new tree, rooted with a learning node.
        if (this.treeRoot == null) {
            this.treeRoot = newLearningNode(false, true, null); // root cannot be alternate
            this.activeLeafNodeCount = 1;
        }

        // If you have no window, create one.
    	if(window == null){
    		window = EvictingQueue.create(windowSize.getValue());
    	}

    	// Forget an instance. The window stores along with each instance the maximum node reached. So look at the head of the queue and forget the instance there.
    	Instance forgetInst;
        if(window.remainingCapacity() == 0){
        	forgetInst = window.peek().getFirst();
        	forgetInst.setWeight(-1.0);
            ((AdaNode) this.treeRoot).forgetInstance(forgetInst, this, null, -1, window.peek().getSecond());
        }

        // Create an object to store the IDs visited while learning this instance. Pass a reference so you add all the IDs...
        AutoExpandVector<Long> reachedNodeIDs = new AutoExpandVector<>();
        ((AdaNode) this.treeRoot).learnFromInstance(inst, this, null, -1, reachedNodeIDs);

        // Store the max ID reached along with the instance in the window

        long maxIDreached = 0;
        for(int i = 0; i < reachedNodeIDs.size(); i++){
        	maxIDreached = maxIDreached > reachedNodeIDs.get(i) ? maxIDreached : reachedNodeIDs.get(i);
        }

        window.add(new Pair<Instance, Long>(inst, maxIDreached));

    	if (numInstances == 0){
    		try {
				writer = new PrintWriter(new FileOutputStream(new File("moa_output.txt"),false));
				writer = new PrintWriter(new FileOutputStream(new File("moa_output.txt"),true));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
    	}

        numInstances++;

    }

    @Override
	protected LearningNode newLearningNode() {
        return new CVFDTLearningNode(new double[0]);
    }

    @Override
	protected LearningNode newLearningNode(double[] initialClassObservations) {
        return new CVFDTLearningNode(initialClassObservations);
    }

    @Override
    protected LearningNode newLearningNode(boolean isAlternate, boolean isRoot, AdaNode mainlineNode) {
        return new CVFDTLearningNode(new double[0], isAlternate, isRoot, mainlineNode);
    }

    @Override
	protected LearningNode newLearningNode(double[] initialClassObservations, boolean isAlternate, boolean isRoot, AdaNode mainlineNode) {
        return new CVFDTLearningNode(initialClassObservations, isAlternate, isRoot, mainlineNode);
    }

    @Override
	protected AdaSplitNode newSplitNode(InstanceConditionalTest splitTest,
            double[] classObservations, int size, boolean isAlternate) {
    	return new CVFDTSplitNode(splitTest, classObservations, size, isAlternate);
    }

	@Override
	protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
            double[] classObservations, boolean isAlternate) {
    	return new CVFDTSplitNode(splitTest, classObservations, isAlternate);
    }

   @Override
    protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
            double[] classObservations, int size) {
        return new CVFDTSplitNode(splitTest, classObservations, size);
    }

    @Override
    protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
            double[] classObservations) {
        return new CVFDTSplitNode(splitTest, classObservations);
    }

}
