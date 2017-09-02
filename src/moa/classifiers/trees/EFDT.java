package moa.classifiers.trees;

import java.util.Arrays;

import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.classifiers.trees.VFDTWindow.AdaNode;

public class EFDT extends CVFDT{

	public class EFDTSplitNode extends CVFDTSplitNode {

		@Override
		protected void reEvaluateBestSplit() {

			int currentSplit = -1; // for no split

			//lets first find out X_a, the current split
			if(this.splitTest != null){
				currentSplit = this.splitTest.getAttsTestDependsOn()[0];
				// given the current implementations in MOA, we're only ever expecting one int to be returned
			}

			// Now let's find the best split X_n other than X_a that doesn't already have an attached alternate subtree
			// Would that be an improvement over CVFDT? Does CVFDT always compute the X_n, even if it won't be used because it has an attached alternate?
			SplitCriterion splitCriterion = (SplitCriterion) getPreparedClassOption(EFDT.this.splitCriterionOption);

			double hoeffdingBound = computeHoeffdingBound(splitCriterion.getRangeOfMerit(this.getObservedClassDistribution()),
					EFDT.this.splitConfidenceOption.getValue(), this.observedClassDistribution.sumOfValues());

			// should really be called allPossibleSplits
			AttributeSplitSuggestion[] allPossibleSplits = this.getBestSplitSuggestions(splitCriterion, EFDT.this);
			Arrays.sort(allPossibleSplits);

			// should really be called allPossibleSplits

			AttributeSplitSuggestion bestSuggestion = allPossibleSplits[allPossibleSplits.length - 1];
			AttributeSplitSuggestion currentSuggestion = null;

			for(int i = 0; i < allPossibleSplits.length; i++) {

				if(allPossibleSplits[i].splitTest != null
						&& allPossibleSplits[i].splitTest.getAttsTestDependsOn()[0] == currentSplit){
					 currentSuggestion = allPossibleSplits[i];
					 break;
				}
			}

			assert(currentSuggestion != null);

			double tieThreshold = EFDT.this.tieThresholdOption.getValue();
			double deltaG = bestSuggestion.merit - currentSuggestion.merit;

			if(currentSplit == bestSuggestion.splitTest.getAttsTestDependsOn()[0])
			{
				// do nothing, because the current split is the best split
				// this is different but equivalent to algorithm listing...
			}

			else if (deltaG > hoeffdingBound
					|| hoeffdingBound < tieThreshold && deltaG > tieThreshold / 2) {
				// if it doesn't already have an alternate subtree, build one
				if(!alternates.containsKey(bestSuggestion)) { // the hashcodes should match... this should work
					alternates.put(bestSuggestion, (AdaNode)EFDT.this.newLearningNode(true));
					// we've just created an alternate, but only if the key is not already contained
				}
			}
		}

		public EFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations) {
			super(splitTest, classObservations);
		}

	}

}
