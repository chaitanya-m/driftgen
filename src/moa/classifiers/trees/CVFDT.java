package moa.classifiers.trees;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.util.Pair;

import com.github.javacliparser.IntOption;
import com.google.common.collect.EvictingQueue;
import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.classifiers.trees.VFDT.Node;
import moa.classifiers.trees.VFDTSlidingWindow.AdaNode;
import moa.classifiers.trees.VFDTSlidingWindow.AdaSplitNode;
import moa.core.AutoExpandVector;
import moa.core.Utils;

public class CVFDT extends VFDTSlidingWindow {

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

		public int getNodeTrainingTime();

		public boolean isTopAlternate();

		public void setTopAlternate(boolean isTopAlt);

		public int getNodeTime();

	}

/*Too much cruft to use delegation...
 *
 * 	public interface CVFDTAltRootAdaNode extends CVFDTAdaNode{

		public int getLowestErrorDiff(); // only top level alternates should get this...

		public void setLowestErrorDiff(int errorDiff); // only top level alternates should get this...

	}

	public class CVFDTSplitAltRootAdaNode implements CVFDTAltRootAdaNode{

		CVFDTAdaNode altRootAdaNode;

		@Override
		public int getLowestErrorDiff() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public void setLowestErrorDiff(int errorDiff) {
			// TODO Auto-generated method stub

		}

	}
*/

	public class CVFDTSplitNode extends AdaSplitNode implements AdaNode, CVFDTAdaNode {

		private static final long serialVersionUID = 1L;

		private boolean inAlternateTestPhase = false;

		private int testPhaseError = 0;

		private int nodeTrainingTime = -1;

		private int subtreeTestingTime = -1;

		private boolean isTopAlternate = false;

		private int lowestErrorDiff = Integer.MAX_VALUE; // only top level alternates should get this... REFACTOR!!!

		@Override
		public int getTestPhaseError() {
			return testPhaseError;
		}

		// maintain a mapping from attributes to alternate trees
		protected Map<Integer, CVFDTAdaNode> alternates;

		// maintain a mapping from alternate trees to error
		protected Map<CVFDTAdaNode, Integer> alternateError;

		// maintain a mapping from alternate trees to lowest ever error diff
		protected Map<CVFDTAdaNode, Double> lowestAltAccuracyDiff = new HashMap<CVFDTAdaNode, Double>();

		// static nested classes don't have a reference to the containing object while nonstatic nested classes do
		// so changing over to a nonstatic nested class

		public CVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations) {
			super(splitTest, classObservations);
			alternates = new HashMap<Integer, CVFDTAdaNode>();
		}

		public CVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations, int size) {
			super(splitTest, classObservations, size);
			alternates = new HashMap<Integer, CVFDTAdaNode>();
		}

		public CVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations, boolean isAlternate) {
			super(splitTest, classObservations, isAlternate);
			alternates = new HashMap<Integer, CVFDTAdaNode>();

		}

		public CVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations, int size, boolean isAlternate) {
			super(splitTest, classObservations, size, isAlternate);
			alternates = new HashMap<Integer, CVFDTAdaNode>();
		}

		@Override
        public void forgetInstance(Instance inst, VFDTSlidingWindow ht, AdaSplitNode parent, int parentBranch, long maxNodeID) {

            // DRY... for now this code is repeated...
            // Updates statistics in split nodes also
        	assert (this.createdFromInitializedLearningNode = true);
        		//inst.setWeight(-1.0);

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

			if (!this.alternates.isEmpty() && !this.isAlternate()) {

				for (CVFDTAdaNode alt : alternates.values()){
					if(alt.getUniqueID() <= maxNodeID){
						alt.forgetInstance(inst, ht, parent, parentBranch, maxNodeID);
					}
				}
			}

            int childBranch = this.instanceChildIndex(inst);
            Node child = this.getChild(childBranch);
            if (child != null && ((CVFDTAdaNode)child).getUniqueID() <= maxNodeID) {
                ((CVFDTAdaNode) child).forgetInstance(inst, ht, this, childBranch, maxNodeID);
            }
            else if(child != null && ((AdaNode)child).getUniqueID() > maxNodeID){
            	child.observedClassDistribution.addToValue((int) inst.classValue(), inst.weight());
            }
           	// because the child would have had had examples "escape" into it from the parent at the time of the split
        	// but it isn't a problem for it's child because the examples are seen only one node down
            // and we want to only do this for the observed class distribution but not for the attribute observers, because those are created fresh for each node


        }


		@Override
		public void learnFromInstance(Instance inst, VFDTSlidingWindow ht, SplitNode parent, int parentBranch,
				AutoExpandVector<Long> reachedLeafIDs){

			nodeTime++;

			// only mainline split nodes are capable of launching a test phase
			// alternate nodes shouldn't get to the point of actually learning
			// launch test phase
			if(nodeTrainingTime % testPhaseFrequency.getValue() == 0 && !this.alternates.isEmpty() && !this.isAlternate()){
				inAlternateTestPhase = true;
			} // this check will remain the same for the length of the test phase unless the only remaining alternate is pruned

			if(subtreeTestingTime == testPhaseLength.getValue()){
				subtreeTestingTime = -1;
				inAlternateTestPhase = false;
			}

			if(inAlternateTestPhase){
//				System.out.println(subtreeTestingTime);

				subtreeTestingTime++;
			}
			else{
				nodeTrainingTime++;
				testPhaseError = 0;
				if(alternateError != null){
					alternateError = null;
//					for (CVFDTAdaNode alt : alternateError.keySet()){
//						alternateError.put(alt, 0);
//					}
				}
			}

			if(inAlternateTestPhase){

				//increment error
				int trueClass = (int) inst.classValue();

				int ClassPrediction = 0;
				FoundNode foundNode = this.filterInstanceToLeaf(inst, this.getParent(), parentBranch);
				Node leaf = foundNode.node;

				if (leaf != null) {
					ClassPrediction = Utils.maxIndex(leaf.getClassVotes(inst, ht));
				} // what happens if leaf is null?
				else{
					// This is actually what VFDT does!!! Why?
		            if (leaf == null) {
		                leaf = foundNode.parent;
		            }
				}


				boolean predictedCorrectly = (trueClass == ClassPrediction);

				if(!predictedCorrectly){
					this.testPhaseError++;
				}

				// increment error for alternates
				for (CVFDTAdaNode alt : alternates.values()){

					int altClassPrediction = 0;
					FoundNode altFoundNode = ((Node)alt).filterInstanceToLeaf(inst, alt.getParent(), parentBranch);
					Node altLeaf = altFoundNode.node;
					if (altLeaf != null) {
						altClassPrediction = Utils.maxIndex(altLeaf.getClassVotes(inst, ht));
					} // what happens if leaf is null?
					else{
						// This is actually what VFDT does! Why?
			            if (altLeaf == null) {
			                altLeaf = altFoundNode.parent;
			            }
					}

					boolean altPredictedCorrectly = (trueClass == altClassPrediction);

					if (alternateError == null){
						alternateError = new HashMap<CVFDTAdaNode, Integer>();
					}

					if (!alternateError.containsKey(alt)){
						alternateError.put(alt, 0);
					}

					if (!altPredictedCorrectly && alt.isAlternate() && !this.isAlternate()){
						int altCurrentError = alternateError.get(alt);
						alternateError.put(alt, (altCurrentError+1)); // don't do this every time! accumulate the error for efficiency... or is this already cheap enough?
//						System.out.println(getNumInstances() +
//								" " + testPhaseError+ " " + altCurrentError +
//								" " + ClassPrediction + " " + altClassPrediction +
//								" " + leaf.observedClassDistribution + " " + altLeaf.observedClassDistribution +
//								" [True Class: " + trueClass+ " ] " + ((CVFDTAdaNode)leaf).getUniqueID() + " " + ((CVFDTAdaNode)altLeaf).getUniqueID()
//								+ " " + ((CVFDTAdaNode)leaf).isAlternate() + " " + ((CVFDTAdaNode)altLeaf).isAlternate()
//								+ " " + this.getUniqueID() + " " + alt.getUniqueID()
//								//+ " " + this.getChild(instanceChildIndex(inst)).observedClassDistribution + " " + ((SplitNode)(alt)).getChild(instanceChildIndex(inst)).observedClassDistribution
//								);
					}

				}

				// if you're at the end of the phase and not an alternate but have alternates, check if a replacement is required and replace
				if (subtreeTestingTime == testPhaseLength.getValue() - 1){

					if(!this.alternates.isEmpty()){

//						System.out.println("Node " + this.getUniqueID() + " has alternates" );
//						System.out.println("Picking a replacement");
//						pick the option with lowest test phase error... and replace...

						int lowestAlternateErrorThisTestPhase = this.testPhaseError;

						CVFDTAdaNode bestAlternate = null;


						// the paper tells us to prune alternates that have an accuracy diff no greater than 1% of the lowest diff ever recorded

						Iterator<Map.Entry<Integer,CVFDT.CVFDTAdaNode>> iter =  alternates.entrySet().iterator();
						while(iter.hasNext()){
							Map.Entry<Integer,CVFDT.CVFDTAdaNode> mapEntry = iter.next();
							CVFDTAdaNode altTopNode = alternates.get(mapEntry.getKey());

							double currentAltAccuracyDiff = // don't use getTestPhaseError for anything other than mainline nodes! Refactor when possible.
									(double)((testPhaseLength.getValue() - this.getTestPhaseError())
											-(testPhaseLength.getValue() - alternateError.get(altTopNode)))
									/(double)(testPhaseLength.getValue());

							if(!lowestAltAccuracyDiff.containsKey(altTopNode)){
								lowestAltAccuracyDiff.put(altTopNode, currentAltAccuracyDiff);
							}
							else{
								lowestAltAccuracyDiff.put(altTopNode,
										currentAltAccuracyDiff < lowestAltAccuracyDiff.get(altTopNode) ? currentAltAccuracyDiff : lowestAltAccuracyDiff.get(altTopNode));
							}

							if(currentAltAccuracyDiff > (0.01 + lowestAltAccuracyDiff.get(altTopNode))){
								alternateError.put(altTopNode, null); // defuses this alternates' error
								lowestAltAccuracyDiff.put(altTopNode, null); // defuses this alternates' accuracy diff
								iter.remove(); // Remove the key-value pair from alternates! (Isn't it okay to just remove the value instead?)

							}

						}
						//Don't iterate directly over alternateError or lowestAltAccuracyDiff...

						// pick the best alternate
						for (CVFDTAdaNode alt: alternates.values()){

							//System.err.println(getNumInstances() + " " + this.testPhaseError + " " + alternateError.get(alt) + " " +
							//this.observedClassDistribution + " " + ((Node)alt).observedClassDistribution);

							if(alternateError.get(alt) < lowestAlternateErrorThisTestPhase){

								lowestAlternateErrorThisTestPhase = alternateError.get(alt);
								bestAlternate = alt;

							}
						}
						// replace with best alternate!!
						if(bestAlternate != null){ //DRY!!! (copied from HAT-ADWIN)
							// Switch alternate tree

							//System.err.println(getNumInstances() + "  " + " SWITCHING");

							ht.activeLeafNodeCount -= this.numberLeaves();
							ht.activeLeafNodeCount += bestAlternate.numberLeaves();
							this.killSubtree((CVFDT)ht);
							bestAlternate.setAlternateStatusForSubtreeNodes(false);
							bestAlternate.setTopAlternate(false);
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

							// ALERT: TODO: prune alternates
							return; // Once you've switched, you shouldn't continue...
						}

					}
				}
			}

			//			// if you're not in a test phase, continue as usual
			else {
				//System.out.println(nodeTrainingTime);
				//System.out.println(this.observedClassDistribution);

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

				if(!this.isAlternate() && nodeTrainingTime%200 ==0 && !inAlternateTestPhase){ //ALERT magic number alert
					reEvaluateBestSplit();
				}

				if (!this.alternates.isEmpty() && !this.isAlternate()) {

					for (CVFDTAdaNode alt:alternates.values()){

						alt.learnFromInstance(inst, ht, this.getParent(), parentBranch, reachedLeafIDs);
					}
				}

				int childBranch = this.instanceChildIndex(inst);
				Node child = this.getChild(childBranch);
				if (child != null) {
					((CVFDTAdaNode) child).learnFromInstance(inst, ht, this, childBranch, reachedLeafIDs);
				}
			}
		}

			@Override
			public void setAlternateStatusForSubtreeNodes(boolean isAlternate) {

				this.setAlternate(isAlternate);

				for (Node child : this.children) {
					if (child != null) {
						((AdaNode)child).setAlternateStatusForSubtreeNodes(isAlternate);
						if(isAlternate == false){
							((CVFDTAdaNode)child).setMainlineNode(null);
						}
					}
				}
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

			@Override
			public void killSubtree(CVFDT ht) {
				for (Node child : this.children) {
					if (child != null) {
						//Delete alternate tree if it exists
						if (child instanceof CVFDTSplitNode && !((CVFDTSplitNode) child).alternates.isEmpty()) {
							Iterator<CVFDTAdaNode> iter = alternates.values().iterator();
							while (iter.hasNext()){
								(iter.next()).killSubtree(ht);
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
				AttributeSplitSuggestion currentSuggestion = null;

				// Find attribute suggestion corresponding to current split
				for(int i = 0; i < bestSplitSuggestions.length; i++) {

					if(bestSplitSuggestions[i].splitTest != null
							&& bestSplitSuggestions[i].splitTest.getAttsTestDependsOn()[0] == currentSplit){
						 currentSuggestion = bestSplitSuggestions[i];
						 break;
					}
				}

				double tieThreshold = CVFDT.this.tieThresholdOption.getValue();
				double deltaG = bestSuggestion.merit - secondBestSuggestion.merit;
				//double deltaG = bestSuggestion.merit - currentSuggestion.merit;

				if(currentSplit == bestSuggestion.splitTest.getAttsTestDependsOn()[0])
				{
					// do nothing, because the current split is the best split
					// this is different but equivalent to algorithm listing...
					// ensures that the current split doesn't get added on as an alternate!
				}

				else if (deltaG > hoeffdingBound
						|| (hoeffdingBound < tieThreshold && deltaG > tieThreshold / 2)) {

					// if it doesn't already have an alternate subtree, build one
					if(!alternates.containsKey(bestSuggestion.splitTest.getAttsTestDependsOn()[0])) {

//						System.err.println(getNumInstances() + " Building alt subtree now for node " + this.getUniqueID());

						CVFDTAdaNode newAlternate = (CVFDTAdaNode)newLearningNode(true, false, this);
						newAlternate.setTopAlternate(true);
						this.alternates.put(bestSuggestion.splitTest.getAttsTestDependsOn()[0], newAlternate);

//						System.out.println(getNumInstances() + " " + this.getUniqueID() +
//								" bestSuggestion.merit-secondBestSuggestion.merit " + (bestSuggestion.merit-secondBestSuggestion.merit) + " \n " +
//								" secondBestSuggestion.merit-currentSuggestion.merit " + (secondBestSuggestion.merit-currentSuggestion.merit) + " \n " +
//								" bestSuggestion.merit-currentSuggestion.merit " + (bestSuggestion.merit-currentSuggestion.merit) + " \n "
//								+ " bestSuggestion.merit "	+	bestSuggestion.merit + " \n "
//								+ " currentSuggestion.merit " + currentSuggestion.merit +"\n"
//								+ " secondBestSuggestion.merit " + secondBestSuggestion.merit +"\n");

						// we've just created an alternate, but only if the key is not already contained
					}
				}
			}

			@Override
			public int getNodeTrainingTime() {
				return nodeTrainingTime;
			}

			@Override
			public boolean isTopAlternate() {
				return isTopAlternate;
			}

			@Override
			public void setTopAlternate(boolean isTopAlt) {
				isTopAlternate = isTopAlt;
			}

			@Override
			public int getNodeTime() {
				return nodeTime;
			}

		}

		public class CVFDTLearningNode extends AdaLearningNode implements AdaNode, CVFDTAdaNode {

			private static final long serialVersionUID = -7477758640913802578L;

			private int testPhaseError = 0;

			private int nodeTrainingTime = 0;

			private boolean isTopAlternate = false;

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
			public void learnFromInstance(Instance inst, VFDTSlidingWindow ht, SplitNode parent, int parentBranch,
					AutoExpandVector<Long> reachedLeafIDs) {

				super.learnFromInstance(inst, ht, parent, parentBranch, reachedLeafIDs);

				nodeTrainingTime++;
			}

			@Override
			public void killSubtree(CVFDT ht) {

			}

			@Override
			public int getNodeTrainingTime() {
				return nodeTrainingTime;
			}

			@Override
			public boolean isTopAlternate() {
				return isTopAlternate;
			}

			@Override
			public void setTopAlternate(boolean isTopAlt) {
				isTopAlternate = isTopAlt;
			}

			@Override
			public int getNodeTime() {
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
				((CVFDTAdaNode) this.treeRoot).forgetInstance(forgetInst, this, null, -1, window.peek().getSecond());
			}

			// Create an object to store the IDs visited while learning this instance. Pass a reference so you add all the IDs...
			AutoExpandVector<Long> reachedNodeIDs = new AutoExpandVector<>();
			((CVFDTAdaNode) this.treeRoot).learnFromInstance(inst, this, null, -1, reachedNodeIDs);

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
		protected void attemptToSplit(ActiveLearningNode node, SplitNode parent,
				int parentIndex) {
			if (!node.observedClassDistributionIsPure()) {
				SplitCriterion splitCriterion = (SplitCriterion) getPreparedClassOption(this.splitCriterionOption);
				AttributeSplitSuggestion[] bestSplitSuggestions = node.getBestSplitSuggestions(splitCriterion, this);
				Arrays.sort(bestSplitSuggestions);
				boolean shouldSplit = false;
				if (bestSplitSuggestions.length < 2) {
					shouldSplit = bestSplitSuggestions.length > 0;
				} else {
					double hoeffdingBound = computeHoeffdingBound(splitCriterion.getRangeOfMerit(node.getObservedClassDistribution()),
							this.splitConfidenceOption.getValue(), node.getWeightSeen());

					AttributeSplitSuggestion bestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 1];
					AttributeSplitSuggestion secondBestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 2];

					// Take out these lines to simulate the original bug in VFDT
					if(bestSuggestion.merit < 1e-10){
						shouldSplit = false;
					}

					else if ((bestSuggestion.merit - secondBestSuggestion.merit > hoeffdingBound)
							|| (hoeffdingBound < this.tieThresholdOption.getValue())) {
						shouldSplit = true;
					}

					if ((this.removePoorAttsOption != null)
							&& this.removePoorAttsOption.isSet()) {
						Set<Integer> poorAtts = new HashSet<Integer>();
						// scan 1 - add any poor to set
						for (int i = 0; i < bestSplitSuggestions.length; i++) {
							if (bestSplitSuggestions[i].splitTest != null) {
								int[] splitAtts = bestSplitSuggestions[i].splitTest.getAttsTestDependsOn();
								if (splitAtts.length == 1) {
									if (bestSuggestion.merit
											- bestSplitSuggestions[i].merit > hoeffdingBound) {
										poorAtts.add(new Integer(splitAtts[0]));
									}
								}
							}
						}
						// scan 2 - remove good ones from set
						for (int i = 0; i < bestSplitSuggestions.length; i++) {
							if (bestSplitSuggestions[i].splitTest != null) {
								int[] splitAtts = bestSplitSuggestions[i].splitTest.getAttsTestDependsOn();
								if (splitAtts.length == 1) {
									if (bestSuggestion.merit
											- bestSplitSuggestions[i].merit < hoeffdingBound) {
										poorAtts.remove(new Integer(splitAtts[0]));
									}
								}
							}
						}
						for (int poorAtt : poorAtts) {
							node.disableAttribute(poorAtt);
						}
					}
				}

				if (shouldSplit) {
					AttributeSplitSuggestion splitDecision = bestSplitSuggestions[bestSplitSuggestions.length - 1];
					if (splitDecision.splitTest == null) {
						// preprune - null wins
						//deactivateLearningNode(node, ((AdaNode)node).getParent(), parentIndex);
					} else {
						CVFDTSplitNode newSplit = (CVFDTSplitNode)newSplitNode(splitDecision.splitTest,
								node.getObservedClassDistribution(),splitDecision.numSplits(), ((AdaNode)(node)).isAlternate());

						((AdaNode)newSplit).setUniqueID(((AdaNode)node).getUniqueID());
						//Ensure that the split node's ID is the same as it's ID as a leaf

						// Copy statistics from the learning node being replaced
						newSplit.createdFromInitializedLearningNode = node.isInitialized;
						newSplit.observedClassDistribution = node.observedClassDistribution; // copy the class distribution
						newSplit.attributeObservers = node.attributeObservers; // copy the attribute observers
						newSplit.setMainlineNode(((AdaNode)node).getMainlineNode()); // Copy the mainline attachment, if any
						newSplit.setTopAlternate(((CVFDTAdaNode)node).isTopAlternate());
						newSplit.nodeTrainingTime = ((CVFDTAdaNode)node).getNodeTrainingTime();

						for (int i = 0; i < splitDecision.numSplits(); i++) {
							Node newChild = newLearningNode(splitDecision.resultingClassDistributionFromSplit(i),
									((AdaNode)newSplit).isAlternate(), false, ((AdaNode)node).getMainlineNode());
							((AdaNode)newChild).setParent(newSplit);
							((CVFDTAdaNode)newChild).setMainlineNode(newSplit.getMainlineNode());// All children are given a mainline node for test phase determination.

							newSplit.setChild(i, newChild);
						}
						this.activeLeafNodeCount--;
						this.decisionNodeCount++;
						this.activeLeafNodeCount += splitDecision.numSplits();

						if (((AdaNode)node).isRoot()) {
							((AdaNode)newSplit).setRoot(true);
							((AdaNode)newSplit).setParent(null);
							this.treeRoot = newSplit;
						}

						else if(((CVFDTAdaNode)node).isTopAlternate()) { // its alternate and is attached directly to a mainline node, must have a mainline split parent
							((AdaNode)newSplit).setParent(((AdaNode)node).getMainlineNode().getParent());
						}

						else { //if the node is neither root nor an alternate attached directly to mainline, it must have a non-null split parent
							((AdaNode)newSplit).setParent(((AdaNode)node).getParent());
							((AdaNode)node).getParent().setChild(parentIndex, newSplit);
						}

					}
					// manage memory
					enforceTrackerLimit();
				}
			}
		}

		@Override
	    protected void deactivateLearningNode(ActiveLearningNode toDeactivate,
	            SplitNode parent, int parentBranch) {
	        Node newLeaf = new InactiveLearningNode(toDeactivate.getObservedClassDistribution());
	        if (((CVFDTAdaNode)toDeactivate).isRoot()) {
	            this.treeRoot = newLeaf;
	        } else if (parent == null && ((CVFDTAdaNode)toDeactivate).isAlternate()){
	        	// Do nothing- don't deactivate alternate top nodes here!!!
	        }
	        else{
	            parent.setChild(parentBranch, newLeaf);
	        }
	        this.activeLeafNodeCount--;
	        this.inactiveLeafNodeCount++;
	    }

		@Override
	    protected void activateLearningNode(InactiveLearningNode toActivate,
	            SplitNode parent, int parentBranch) {
	        Node newLeaf = newLearningNode(toActivate.getObservedClassDistribution());
	        if (((CVFDTAdaNode)toActivate).isRoot()) {
	        	this.treeRoot = newLeaf;
	        } else {
	            parent.setChild(parentBranch, newLeaf);
	        }
	        this.activeLeafNodeCount++;
	        this.inactiveLeafNodeCount--;
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
