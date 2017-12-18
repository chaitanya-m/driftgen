package moa.classifiers.trees;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.conditionaltests.NominalAttributeBinaryTest;
import moa.classifiers.core.conditionaltests.NominalAttributeMultiwayTest;
import moa.classifiers.core.conditionaltests.NumericAttributeBinaryTest;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.classifiers.rules.core.conditionaltests.NominalAttributeBinaryRulePredicate;
import moa.classifiers.rules.core.conditionaltests.NumericAttributeBinaryRulePredicate;
import moa.classifiers.trees.VFDT.ActiveLearningNode;
import moa.classifiers.trees.VFDT.FoundNode;
import moa.classifiers.trees.VFDT.LearningNode;
import moa.classifiers.trees.VFDT.Node;
import moa.classifiers.trees.VFDT.SplitNode;
import moa.core.AutoExpandVector;


public class EFDT extends VFDT{


    public interface EFDTNode {

		public boolean isRoot();
		public void setRoot(boolean isRoot);

		public void learnFromInstance(Instance inst, EFDT ht, EFDTSplitNode parent, int parentBranch);
		public void setParent(EFDTSplitNode parent);

		public EFDTSplitNode getParent();

    }



    public class EFDTSplitNode extends SplitNode implements EFDTNode{

		/**
		 *
		 */

    	private boolean isRoot;

		private EFDTSplitNode parent = null;

		private static final long serialVersionUID = 1L;

		protected AutoExpandVector<AttributeClassObserver> attributeObservers;

		public EFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations, int size) {
			super(splitTest, classObservations, size);
		}

		public EFDTSplitNode(InstanceConditionalTest splitTest, double[] classObservations) {
			super(splitTest, classObservations);
		}

		@Override
		public boolean isRoot() {
			return isRoot;
		}

		@Override
		public void setRoot(boolean isRoot) {
			this.isRoot = isRoot;
		}

		public void killSubtree(VFDT ht) {
			for (Node child : this.children) {
				if (child != null) {

					//Recursive delete of SplitNodes
					if (child instanceof SplitNode) {
						((EFDTSplitNode) child).killSubtree(ht);
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


		// DRY... code duplicated from ActiveLearningNode in VFDT.java
		public AttributeSplitSuggestion[] getBestSplitSuggestions(
				SplitCriterion criterion, EFDT ht) {
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
		public void learnFromInstance(Instance inst, EFDT ht, EFDTSplitNode parent, int parentBranch) {

			//// Update node statistics and class distribution

			this.observedClassDistribution.addToValue((int) inst.classValue(), inst.weight()); // update prior (predictor)

			for (int i = 0; i < inst.numAttributes() - 1; i++) { //update likelihood
				int instAttIndex = modelAttIndexToInstanceAttIndex(i, inst);
				AttributeClassObserver obs = this.attributeObservers.get(i);
				if (obs == null) {
					obs = inst.attribute(instAttIndex).isNominal() ? ht.newNominalClassObserver() : ht.newNumericClassObserver();
					this.attributeObservers.set(i, obs);
				}
				obs.observeAttributeClass(inst.value(instAttIndex), (int) inst.classValue(), inst.weight());
			}

			////

			// check if a better split is available. if so, chop the tree at this point, copying likelihood. predictors for children are from parent likelihood.
			if(ht.numInstances % ht.gracePeriodOption.getValue() == 0){
				this.reEvaluateBestSplit(this, parent, parentBranch);
			}


	        int childBranch = this.instanceChildIndex(inst);
	        Node child = this.getChild(childBranch);

	        if (child != null) {
	            ((EFDTNode) child).learnFromInstance(inst, ht, this, childBranch);
	        }

		}

		protected void reEvaluateBestSplit(EFDTSplitNode node, EFDTSplitNode parent,
	            int parentIndex) {

			// node is a reference to this anyway... why have it at all?

			int currentSplit = -1; // for no split

			//lets first find out X_a, the current split
			if(this.splitTest != null){
				currentSplit = this.splitTest.getAttsTestDependsOn()[0];
				// given the current implementations in MOA, we're only ever expecting one int to be returned
			}

			// Now let's find the best split X_n other than X_a that doesn't already have an attached alternate subtree
			// Would that be an improvement over CVFDT? Does CVFDT always compute the X_n, even if it won't be used because it has an attached alternate?
			SplitCriterion splitCriterion = (SplitCriterion) getPreparedClassOption(EFDT.this.splitCriterionOption);

			double hoeffdingBound = computeHoeffdingBound(splitCriterion.getRangeOfMerit(node.getClassDistributionAtTimeOfCreation()),
					EFDT.this.splitConfidenceOption.getValue(), node.observedClassDistribution.sumOfValues());

			AttributeSplitSuggestion[] bestSplitSuggestions = node.getBestSplitSuggestions(splitCriterion, EFDT.this);
			Arrays.sort(bestSplitSuggestions);

			AttributeSplitSuggestion bestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 1];
			AttributeSplitSuggestion currentSuggestion = null;

			// Find attribute suggestion corresponding to current split. this is specific to the nominal case...
			// the numeric case will find you the same attribute, which is slightly useless
			for(int i = 0; i < bestSplitSuggestions.length; i++) {

				if(bestSplitSuggestions[i].splitTest != null
						&& bestSplitSuggestions[i].splitTest.getAttsTestDependsOn()[0] == currentSplit){
					 currentSuggestion = bestSplitSuggestions[i];
					 break;
				}
			}

			double tieThreshold = EFDT.this.tieThresholdOption.getValue();
			double deltaG = bestSuggestion.merit - currentSuggestion.merit;

			if(bestSuggestion == currentSuggestion && bestSuggestion.splitTest.getClass() == NumericAttributeBinaryTest.class){
				//in this case the previous deltaG computation is useless- always zero
				// we need to compute actual current merit(infogain, G) and recompute deltaG
				// we need new and old split points
				double[][] childDists = new double[node.numChildren()][];
				for(int i = 0; i < node.numChildren(); i++){
					childDists[i] = node.getChild(i).getObservedClassDistribution();
				}


				deltaG = bestSuggestion.merit - splitCriterion.getMeritOfSplit(node.getObservedClassDistribution(), childDists);


			}








			// if the new best is null, or if it is the same as current and a nominal, don't do anything... we'd rather have the existing tree structure

			if(
					(
							currentSplit == bestSuggestion.splitTest.getAttsTestDependsOn()[0] &&
							(
									bestSuggestion.splitTest.getClass() == NominalAttributeBinaryTest.class ||
									bestSuggestion.splitTest.getClass() == NominalAttributeMultiwayTest.class ||
									bestSuggestion.splitTest.getClass() == NominalAttributeBinaryRulePredicate.class
							)
					)

					|| bestSuggestion.splitTest == null
					)
			{
				// do nothing, because the current nominal split is the best split
				// this is different but equivalent to algorithm listing...
				// ensures that the current split doesn't get added on as an alternate!
			}

			//if the current is numeric and the same as current, only split if the majority class has changed down one of the paths
			else if( currentSplit == bestSuggestion.splitTest.getAttsTestDependsOn()[0] &&
						bestSuggestion.splitTest.getClass() == NumericAttributeBinaryTest.class
								&&
								(argmax(bestSuggestion.resultingClassDistributions[0]) != argmax(node.getChild(0).getObservedClassDistribution())
								||	argmax(bestSuggestion.resultingClassDistributions[1]) != argmax(node.getChild(1).getObservedClassDistribution()))
								&&
								deltaG < hoeffdingBound
						// || bestSuggestion.splitTest.getClass() == NumericAttributeBinaryRulePredicate.class // handle this later

					){
//				if (deltaG > hoeffdingBound){
//					System.err.println(numInstances + " Classes are unequal but no split decision to be made because deltaG is too small at " + deltaG
//							+ " while hoeffdingBound is " + hoeffdingBound
//							);
//
//				}
				//else{
				//}

//					if(argmax(bestSuggestion.resultingClassDistributions[0]) != argmax(node.getChild(0).getObservedClassDistribution()) ){
//						System.err.println(Arrays.toString(bestSuggestion.resultingClassDistributions[0])
//								+ " $$$$$ " +
//								Arrays.toString(node.getChild(0).getObservedClassDistribution())
//						+ " $$$$$ " +
//						Arrays.toString(node.getChild(0).getClassDistributionAtTimeOfCreation()))
//
//						;
//					}
//
//						System.err.println(argmax(bestSuggestion.resultingClassDistributions[0])
//								+ " $$$$$ " +
//								argmax(node.getChild(0).getObservedClassDistribution()));

			}




			else if (deltaG > hoeffdingBound
					|| (hoeffdingBound < tieThreshold && deltaG > tieThreshold / 2)) {


				if (currentSuggestion == bestSuggestion && currentSuggestion.splitTest.getClass() == NumericAttributeBinaryTest.class &&
						(argmax(bestSuggestion.resultingClassDistributions[0]) != argmax(node.getChild(0).getObservedClassDistribution())
						||	argmax(bestSuggestion.resultingClassDistributions[1]) != argmax(node.getChild(1).getObservedClassDistribution()) )
						){
					System.err.println(numInstances);

				}







            	AttributeSplitSuggestion splitDecision = bestSuggestion;

				// otherwise, torch the subtree and split on the new best attribute.

                	this.killSubtree(EFDT.this);

                    Node newSplit = newSplitNode(splitDecision.splitTest,
                            node.getObservedClassDistribution(), splitDecision.numSplits());
                    ((EFDTSplitNode)newSplit).attributeObservers = node.attributeObservers; // copy the attribute observers

                    for (int i = 0; i < splitDecision.numSplits(); i++) {

                        double[] j = splitDecision.resultingClassDistributionFromSplit(i);

                        Node newChild = newLearningNode(splitDecision.resultingClassDistributionFromSplit(i));

                        if(splitDecision.splitTest.getClass() == NominalAttributeBinaryTest.class
                        		||splitDecision.splitTest.getClass() == NominalAttributeMultiwayTest.class){
                        	newChild.usedNominalAttributes = new ArrayList<Integer>(node.usedNominalAttributes); //deep copy
                        	newChild.usedNominalAttributes.add(splitDecision.splitTest.getAttsTestDependsOn()[0]);
                        	// no  nominal attribute should be split on more than once in the path
                        }
                        ((EFDTSplitNode)newSplit).setChild(i, newChild);
                    }
                    EFDT.this.activeLeafNodeCount--;
                    EFDT.this.decisionNodeCount++;
                    EFDT.this.activeLeafNodeCount += splitDecision.numSplits();

                    if (parent == null) {
                		((EFDTNode)newSplit).setRoot(true);
                		((EFDTNode)newSplit).setParent(null);
                        EFDT.this.treeRoot = newSplit;
                    } else {
                    	((EFDTNode)newSplit).setRoot(false);
                    	((EFDTNode)newSplit).setParent(parent);
						parent.setChild(parentIndex, newSplit);
					}

//					System.out.println(getNumInstances() + " " + this.getUniqueID() +
//							" bestSuggestion.merit-secondBestSuggestion.merit " + (bestSuggestion.merit-secondBestSuggestion.merit) + " \n " +
//							" secondBestSuggestion.merit-currentSuggestion.merit " + (secondBestSuggestion.merit-currentSuggestion.merit) + " \n " +
//							" bestSuggestion.merit-currentSuggestion.merit " + (bestSuggestion.merit-currentSuggestion.merit) + " \n "
//							+ " bestSuggestion.merit "	+	bestSuggestion.merit + " \n "
//							+ " currentSuggestion.merit " + currentSuggestion.merit +"\n"
//							+ " secondBestSuggestion.merit " + secondBestSuggestion.merit +"\n");

					// we've just created an alternate, but only if the key is not already contained
				}
			}

		@Override
		public void setParent(EFDTSplitNode parent) {
			this.parent = parent;
		}

		@Override
		public EFDTSplitNode getParent() {
			return this.parent;
		}
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
            }

            else {
            	double hoeffdingBound = computeHoeffdingBound(splitCriterion.getRangeOfMerit(node.getClassDistributionAtTimeOfCreation()),
                        this.splitConfidenceOption.getValue(), node.getWeightSeen());
                AttributeSplitSuggestion bestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 1];

                //comment this if statement and remove check that a nominal isn't being reused to get VFDT bug
                if(bestSuggestion.merit < 1e-10){
                	shouldSplit = false;
                }

                else
                	if ((bestSuggestion.merit  > hoeffdingBound)
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
//            	System.out.println("=======================");
//            	StringBuilder out = new StringBuilder();
//            	getModelDescription(out, 2);
//            	System.out.println(out);
//            	System.out.println("=======================");


                AttributeSplitSuggestion splitDecision = bestSplitSuggestions[bestSplitSuggestions.length - 1];
                if (splitDecision.splitTest == null) {
                    // preprune - null wins
                    deactivateLearningNode(node, parent, parentIndex);
                } else {
                    Node newSplit = newSplitNode(splitDecision.splitTest,
                            node.getObservedClassDistribution(), splitDecision.numSplits());
                    ((EFDTSplitNode)newSplit).attributeObservers = node.attributeObservers; // copy the attribute observers

                    for (int i = 0; i < splitDecision.numSplits(); i++) {

                        double[] j = splitDecision.resultingClassDistributionFromSplit(i);

                        Node newChild = newLearningNode(splitDecision.resultingClassDistributionFromSplit(i));

                        if(splitDecision.splitTest.getClass() == NominalAttributeBinaryTest.class
                        		||splitDecision.splitTest.getClass() == NominalAttributeMultiwayTest.class){
                        	newChild.usedNominalAttributes = new ArrayList<Integer>(node.usedNominalAttributes); //deep copy
                        	newChild.usedNominalAttributes.add(splitDecision.splitTest.getAttsTestDependsOn()[0]);
                        	// no  nominal attribute should be split on more than once in the path
                        }
                        ((EFDTSplitNode)newSplit).setChild(i, newChild);
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
//            	System.out.println("SPLIT AT:" + numInstances);
//            	out = new StringBuilder();
//            	getModelDescription(out, 2);
//            	System.out.println(out);
                // manage memory
                enforceTrackerLimit();
            }
        }
    }


    public class EFDTLearningNode extends LearningNodeNBAdaptive implements EFDTNode{

    	private boolean isRoot;

		private EFDTSplitNode parent = null;

		public EFDTLearningNode(double[] initialClassObservations) {
			super(initialClassObservations);
		}


		/**
		 *
		 */
		private static final long serialVersionUID = -2525042202040084035L;

		@Override
		public boolean isRoot() {
			return isRoot;
		}

		@Override
		public void setRoot(boolean isRoot) {
			this.isRoot = isRoot;
		}

		@Override
		public void learnFromInstance(Instance inst, VFDT ht) {
			super.learnFromInstance(inst, ht);

		}

		@Override
		public void learnFromInstance(Instance inst, EFDT ht, EFDTSplitNode parent, int parentBranch) {
			learnFromInstance(inst, ht);

	            if (ht.growthAllowed
	                    && (this instanceof ActiveLearningNode)) {
	                ActiveLearningNode activeLearningNode = this;
	                double weightSeen = activeLearningNode.getWeightSeen();
	                if (activeLearningNode.nodeTime % ht.gracePeriodOption.getValue() == 0) {
	                    attemptToSplit(activeLearningNode, parent,
	                            parentBranch);
	                    activeLearningNode.setWeightSeenAtLastSplitEvaluation(weightSeen);
	                }
	            }
	        }

		@Override
		public void setParent(EFDTSplitNode parent) {
			this.parent = parent;
		}

		@Override
		public EFDTSplitNode getParent() {
			return this.parent;
		}


    }

    @Override
    public void trainOnInstanceImpl(Instance inst) {

        if (this.treeRoot == null) {
            this.treeRoot = newLearningNode();
            ((EFDTNode) this.treeRoot).setRoot(true);
            this.activeLeafNodeCount = 1;
        }

        FoundNode foundNode = this.treeRoot.filterInstanceToLeaf(inst, null, -1);
        Node leafNode = foundNode.node;

        if (leafNode == null) {
            leafNode = newLearningNode();
            foundNode.parent.setChild(foundNode.parentBranch, leafNode);
            this.activeLeafNodeCount++;
        }

        ((EFDTNode) this.treeRoot).learnFromInstance(inst, this, null, -1);

        numInstances++;
    }


	@Override
	protected LearningNode newLearningNode() {
		return new EFDTLearningNode(new double[0]);
	}

	@Override
	protected LearningNode newLearningNode(double[] initialClassObservations) {
		return new EFDTLearningNode(initialClassObservations);
	}

	@Override
    protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
            double[] classObservations, int size) {
        return new EFDTSplitNode(splitTest, classObservations, size);
    }

	@Override
    protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
            double[] classObservations) {
        return new EFDTSplitNode(splitTest, classObservations);
    }

	private int argmax(double[] array){

		double max = array[0];
		int maxarg = 0;

		for (int i = 1; i < array.length; i++){

			if(array[i] > max){
				max = array[i];
				maxarg = i;
			}
		}
		return maxarg;
	}
}
