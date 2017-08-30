package moa.classifiers.trees;

import java.util.Arrays;
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

public class CVFDT extends VFDTWindow {

	// lets begin by adding counters and split tests to every splitnode
	// then we can add a list of alternates to grow the results of the split tests
	// then when we incorporate alternate replacement, we will finally have our tree

	// How do we add a counter?

    public IntOption testPhaseFrequency = new IntOption("testPhaseFrequency", 'f',
            "How frequently alternates are tested", 10000, 0, Integer.MAX_VALUE);

    public IntOption testPhaseLength = new IntOption("testPhaseLength", 'l',
            "How long each test phase for alternates is", 200, 0, Integer.MAX_VALUE);

	public interface CVFDTAdaNode extends AdaNode{

		public void learnFromInstance(Instance inst, CVFDT ht, SplitNode parent, int parentBranch,
				AutoExpandVector<Long> reachedLeafIDs);
	}

	public class CVFDTSplitNode extends AdaSplitNode implements CVFDTAdaNode {

		private boolean inAlternateTestPhase = false;

		// maintain a mapping from attributes to alternate trees
		private Map<AttributeSplitSuggestion, AdaNode> alternates;

		// static nested classes don't have a reference to the containing object while nonstatic nested classes do
		// so changing over to a nonstatic nested class

		public CVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations) {
			super(splitTest, classObservations);
			// TODO Auto-generated constructor stub
		}

		@Override
		public void learnFromInstance(Instance inst, CVFDT ht, SplitNode parent, int parentBranch,
				AutoExpandVector<Long> reachedLeafIDs){

			if (getNumInstances() % testPhaseFrequency.getValue() < testPhaseLength.getValue()){
				inAlternateTestPhase = true;
			}
			else {
				inAlternateTestPhase = false;
			}

			//System.out.println("Main Tree is of depth " + ht.treeRoot.subtreeDepth());

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

			// We need to re-evaluate splits at each split node...

			reEvaluateBestSplit();
			// excellent! are any of the evaluated splits better than current?
			// if so, give it an alternate!

			// If a split is better, we build an alternate
			// We ignore evaluating attributes that already have a corresponding alternate
			// ALERT: We're missing code for going down alternate paths here

			int childBranch = this.instanceChildIndex(inst);
			Node child = this.getChild(childBranch);
			if (child != null) {
				((AdaNode) child).learnFromInstance(inst, ht, this, childBranch, reachedLeafIDs);
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

			// note that we have to get rid of inefficient alternates as mentioned in paper... TODO

			// We now need to compare error accumulated in the alternates with error accumulated in the mainline
			// Use ADWIN as error estimator? Or is that too slow?
			// AdaNode needs a getAccumulatedError()

			// how frequently do we check if accuracy has been beaten?
			// just do it every time we re-evaluate splits (default 200)... the paper doesn't say
			// what happens to existing alternates when a substitution occurs? do they get destroyed?

			// hold on, it's not that simple... every n instances, all alternates and mainline enter a testing phase where no learning happens!
			// good for gradual drift perhaps? but so many examples are lost! And if you make the intervals too long you won't replace!
			// so, just having a persistent error counter might do better than CVFDT, and a windowed error tracker may do even better

			// Then we will find the best split X_b other than X_n -  this could be X_a

		}

	}

}
