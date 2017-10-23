package moa.classifiers.trees;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.conditionaltests.NominalAttributeBinaryTest;
import moa.classifiers.core.conditionaltests.NominalAttributeMultiwayTest;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.classifiers.trees.VFDT.ActiveLearningNode;
import moa.classifiers.trees.VFDT.Node;
import moa.classifiers.trees.VFDT.SplitNode;

public class EFDT extends VFDT{

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
            }

            else {
            	double hoeffdingBound = computeHoeffdingBound(splitCriterion.getRangeOfMerit(node.getClassDistributionAtTimeOfCreation()),
                        this.splitConfidenceOption.getValue(), node.getWeightSeen());
                AttributeSplitSuggestion bestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 1];
                AttributeSplitSuggestion secondBestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 2];

                //comment this if statement to get VFDT bug
                if(bestSuggestion.merit < 1e-10){
                	shouldSplit = false;
                }

                else
                	if ((bestSuggestion.merit - secondBestSuggestion.merit > hoeffdingBound)
                        || (hoeffdingBound < this.tieThresholdOption.getValue()))
                    	{
                    shouldSplit = true;
                }

                if(shouldSplit){
                	for(Integer i : node.usedNominalAttributes){
                		if(bestSuggestion.splitTest.getAttsTestDependsOn()[0] == i){
                			shouldSplit = false;
                			break;
                		}
                	}
                }

                // }
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
            	splitCount++;
            	System.out.println("=======================");
            	StringBuilder out = new StringBuilder();
            	getModelDescription(out, 2);
            	System.out.println(out);
            	System.out.println("=======================");


                AttributeSplitSuggestion splitDecision = bestSplitSuggestions[bestSplitSuggestions.length - 1];
                if (splitDecision.splitTest == null) {
                    // preprune - null wins
                    deactivateLearningNode(node, parent, parentIndex);
                } else {
                    SplitNode newSplit = newSplitNode(splitDecision.splitTest,
                            node.getObservedClassDistribution(), splitDecision.numSplits());
                    for (int i = 0; i < splitDecision.numSplits(); i++) {

                        double[] j = splitDecision.resultingClassDistributionFromSplit(i);

                        Node newChild = newLearningNode(splitDecision.resultingClassDistributionFromSplit(i));

                        if(splitDecision.splitTest.getClass() == NominalAttributeBinaryTest.class
                        		||splitDecision.splitTest.getClass() == NominalAttributeMultiwayTest.class){
                        	newChild.usedNominalAttributes = new ArrayList<Integer>(node.usedNominalAttributes); //deep copy
                        	newChild.usedNominalAttributes.add(splitDecision.splitTest.getAttsTestDependsOn()[0]);
                        	// no  nominal attribute should be split on more than once in the path
                        }
                        newSplit.setChild(i, newChild);
                    }
                    this.activeLeafNodeCount--;
                    this.decisionNodeCount++;
                    this.activeLeafNodeCount += splitDecision.numSplits();
                    if (parent == null) {
                        this.treeRoot = newSplit;
                    } else {
                        parent.setChild(parentIndex, newSplit);
                    }

                }
            	System.out.println("SPLIT AT:" + numInstances);
            	out = new StringBuilder();
            	getModelDescription(out, 2);
            	System.out.println(out);
                // manage memory
                enforceTrackerLimit();
            }
        }
    }



}

