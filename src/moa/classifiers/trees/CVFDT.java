package moa.classifiers.trees;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.splitcriteria.SplitCriterion;
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

	public interface CVFDTAdaNode extends AdaNode{

		public void learnFromInstance(Instance inst, CVFDT ht, SplitNode parent, int parentBranch,
				AutoExpandVector<Long> reachedLeafIDs);

		public int getTestPhaseError();

	}

	public class CVFDTSplitNode extends AdaSplitNode implements CVFDTAdaNode {

		private boolean inAlternateTestPhase = false;

		private int testPhaseError = 0;

		@Override
		public int getTestPhaseError() {
			return testPhaseError;
		}

		// maintain a mapping from attributes to alternate trees
		protected Map<AttributeSplitSuggestion, AdaNode> alternates;

		// static nested classes don't have a reference to the containing object while nonstatic nested classes do
		// so changing over to a nonstatic nested class

		public CVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations) {
			super(splitTest, classObservations);
			alternates = new HashMap<AttributeSplitSuggestion, VFDTWindow.AdaNode>();
			// TODO Auto-generated constructor stub
		}

		@Override
		public void learnFromInstance(Instance inst, CVFDT ht, SplitNode parent, int parentBranch,
				AutoExpandVector<Long> reachedLeafIDs){

			// if you're in a test phase
			if (getNumInstances() % testPhaseFrequency.getValue() < testPhaseLength.getValue()) {
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
				if (getNumInstances() % testPhaseFrequency.getValue() == testPhaseLength.getValue() - 1){
					if(!this.isAlternate() && !this.alternates.isEmpty()){

						//pick the option with lowest test phase error... and replace...
						int lowestError = testPhaseError;

						AdaNode bestAlternate = null;

						Iterator<AdaNode> iter = alternates.values().iterator();

						while (iter.hasNext()){
							CVFDTAdaNode alt = (CVFDTAdaNode) iter.next();

							if(alt.getTestPhaseError() < lowestError){

								lowestError = alt.getTestPhaseError();
								bestAlternate = alt;

							}

						}
						// replace with best alternate!!
						if(bestAlternate != null){ //DRY!!! (copied from HAT-ADWIN)
							// Switch alternate tree
							ht.activeLeafNodeCount -= this.numberLeaves();
							ht.activeLeafNodeCount += bestAlternate.numberLeaves();
							this.killTreeChilds(ht);
							bestAlternate.setAlternateStatusForSubtreeNodes(false);
							bestAlternate.setMainlineNode(null);


							if (!this.isRoot()) {
								this.getParent().setChild(parentBranch, this.alternateTree);
								bestAlternate.setRoot(false);
								bestAlternate.setParent(this.getParent());
								//((AdaSplitNode) parent.getChild(parentBranch)).alternateTree = null;
							} else {
								// Switch root tree
								bestAlternate.setRoot(true);
								bestAlternate.setParent(null);
								ht.treeRoot = this.alternateTree;
							}
							this.alternateTree = null;
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

				else if (this.isAlternate() || !this.alternates.isEmpty()){

					return; // skip learning and split evaluation!
				}

				// if neither of the above conditions apply continue as usual

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

				if(!this.isAlternate()){
					reEvaluateBestSplit();
				}


				// Going down alternate paths here

				if (this.alternates != null && !this.isAlternate()) {

					Iterator<AdaNode> iter = alternates.values().iterator();

					while (iter.hasNext()){
						CVFDTAdaNode alt = (CVFDTAdaNode) iter.next();
						alt.learnFromInstance(inst, ht, this.getParent(), parentBranch, reachedLeafIDs);
					}
				}


				int childBranch = this.instanceChildIndex(inst);
				Node child = this.getChild(childBranch);
				if (child != null) {
					((AdaNode) child).learnFromInstance(inst, ht, this, childBranch, reachedLeafIDs);
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
					|| hoeffdingBound < tieThreshold && deltaG > tieThreshold / 2) {
				// if it doesn't already have an alternate subtree, build one
				if(!alternates.containsKey(bestSuggestion)) { // the hashcodes should match... this should work
					alternates.put(bestSuggestion, (AdaNode)CVFDT.this.newLearningNode(true));
					// we've just created an alternate, but only if the key is not already contained
				}
			}
		}
	}

	public class CVFDTLearningNode extends AdaLearningNode implements CVFDTAdaNode {

		private boolean inAlternateTestPhase = false;

		private int testPhaseError = 0;

		@Override
		public int getTestPhaseError() {
			return testPhaseError;
		}

		public CVFDTLearningNode(double[] initialClassObservations) {
			super(initialClassObservations);
		}

		@Override
		public void learnFromInstance(Instance inst, CVFDT ht, SplitNode parent, int parentBranch,
				AutoExpandVector<Long> reachedLeafIDs) {

			if (getNumInstances() % testPhaseFrequency.getValue() < testPhaseLength.getValue()) {
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
		}
	}

}
