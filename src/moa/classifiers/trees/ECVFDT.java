package moa.classifiers.trees;

import java.util.Arrays;

import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.classifiers.trees.CVFDT.CVFDTAdaNode;

public class ECVFDT extends CVFDT{


	public class ECVFDTSplitNode extends CVFDTSplitNode{





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

			SplitCriterion splitCriterion = (SplitCriterion) getPreparedClassOption(ECVFDT.this.splitCriterionOption);
			double hoeffdingBound = computeHoeffdingBound(splitCriterion.getRangeOfMerit(this.getObservedClassDistribution()),
					ECVFDT.this.splitConfidenceOption.getValue(), this.observedClassDistribution.sumOfValues());
			// should really be called allPossibleSplits

			AttributeSplitSuggestion[] allPossibleSplits = this.getBestSplitSuggestions(splitCriterion, ECVFDT.this);
			Arrays.sort(allPossibleSplits);
			// should really be called allPossibleSplits

			AttributeSplitSuggestion bestSuggestion = allPossibleSplits[allPossibleSplits.length - 1];
			AttributeSplitSuggestion secondBestSuggestion = allPossibleSplits[allPossibleSplits.length - 2];
			AttributeSplitSuggestion currentSuggestion = null;

			// Find attribute suggestion corresponding to current split
			for(int i = 0; i < allPossibleSplits.length; i++) {

				if(allPossibleSplits[i].splitTest != null
						&& allPossibleSplits[i].splitTest.getAttsTestDependsOn()[0] == currentSplit){
					 currentSuggestion = allPossibleSplits[i];
					 break;
				}
			}

			assert(currentSuggestion != null);

			double tieThreshold = ECVFDT.this.tieThresholdOption.getValue();
			double deltaG = bestSuggestion.merit - currentSuggestion.merit;
			//double deltaG = bestSuggestion.merit - secondBestSuggestion.merit;

			//System.out.println(bestSuggestion.merit + " " + secondBestSuggestion.merit +  " " + deltaG);


			if(currentSplit == bestSuggestion.splitTest.getAttsTestDependsOn()[0])
			{
				// do nothing, because the current split is the best split
				// this is equivalent to algorithm listing...
			}
			else if (deltaG > hoeffdingBound
					|| hoeffdingBound < tieThreshold && deltaG > tieThreshold / 2) {
				// if it doesn't already have an alternate subtree, build one
				if(!alternates.containsKey(bestSuggestion.splitTest.getAttsTestDependsOn()[0])) { // the hashcodes should match... this should work
					CVFDTAdaNode newAlternate = (CVFDTAdaNode)newLearningNode(true, false, this);
					newAlternate.setTopAlternate(true);
					alternates.put(bestSuggestion.splitTest.getAttsTestDependsOn()[0], newAlternate);
//					System.out.println(getNumInstances() +
//							" secondBestSuggestion.merit-currentSuggestion.merit " + (secondBestSuggestion.merit-currentSuggestion.merit) + " \n " +
//							" bestSuggestion.merit-currentSuggestion.merit " + (bestSuggestion.merit-currentSuggestion.merit) + " \n "
//							+ " bestSuggestion.merit "	+	bestSuggestion.merit + " \n "
//							+ " currentSuggestion.merit " + currentSuggestion.merit +"\n"
//							+ " secondBestSuggestion.merit " + secondBestSuggestion.merit +"\n");

//					System.out.println(currentSplit + " is current split attribute. " +
//							currentSuggestion.splitTest.getAttsTestDependsOn()[0] + " is current split attribute. " +
//					bestSuggestion.splitTest.getAttsTestDependsOn()[0] + " is the best suggestion."
//					+ secondBestSuggestion.splitTest.getAttsTestDependsOn()[0] + " is the second best suggestion."+"\n\n");
					// we've just created an alternate, but only if the key is not already contained

				}
			}
		}




		public ECVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations, int size,
				boolean isAlternate) {
			super(splitTest, classObservations, size, isAlternate);
		}

		public ECVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations, boolean isAlternate) {
			super(splitTest, classObservations, isAlternate);
		}

		public ECVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations, int size) {
			super(splitTest, classObservations, size);
		}

		public ECVFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations) {
			super(splitTest, classObservations);
		}

	}

	@Override
	protected AdaSplitNode newSplitNode(InstanceConditionalTest splitTest,
			double[] classObservations, int size, boolean isAlternate) {
		return new ECVFDTSplitNode(splitTest, classObservations, size, isAlternate);
	}

	@Override
	protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
			double[] classObservations, boolean isAlternate) {
		return new ECVFDTSplitNode(splitTest, classObservations, isAlternate);
	}

	@Override
	protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
			double[] classObservations, int size) {
		return new ECVFDTSplitNode(splitTest, classObservations, size);
	}

	@Override
	protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
			double[] classObservations) {
		return new ECVFDTSplitNode(splitTest, classObservations);
	}

}
