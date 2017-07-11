/*
 *    HoeffdingAdaptiveTree.java
 *    Copyright (C) 2008 University of Waikato, Hamilton, New Zealand
 *    @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package moa.classifiers.trees;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import moa.classifiers.bayes.NaiveBayes;
import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.driftdetection.ADWIN;
import moa.classifiers.core.splitcriteria.InfoGainSplitCriterion;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.classifiers.trees.HoeffdingTree.ActiveLearningNode;
import moa.classifiers.trees.HoeffdingTree.LearningNode;
import moa.classifiers.trees.HoeffdingTree.Node;
import moa.classifiers.trees.HoeffdingTree.SplitNode;
import moa.core.DoubleVector;
import moa.core.MiscUtils;
import moa.core.StringUtils;
import moa.core.Utils;
import com.yahoo.labs.samoa.instances.Instance;

/**
 * Hoeffding Adaptive Tree for evolving data streams.
 *
 * <p>This adaptive Hoeffding Tree uses ADWIN to monitor performance of
 * branches on the tree and to replace them with new branches when their
 * accuracy decreases if the new branches are more accurate.</p>
 * See details in:</p>
 * <p>Adaptive Learning from Evolving Data Streams. Albert Bifet, Ricard Gavaldà.
 * IDA 2009</p>
 *
 * <ul>
 * <li> Same parameters as <code>HoeffdingTreeNBAdaptive</code></li>
 * <li> -l : Leaf prediction to use: MajorityClass (MC), Naive Bayes (NB) or NaiveBayes
 * adaptive (NBAdaptive).
 * </ul>
 *
 * @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
 * @version $Revision: 7 $
 */
public class SubConceptTree extends HoeffdingTree {

    private static final long serialVersionUID = 1L;

    private static long numInstances = 0;

    @Override
    public String getPurposeString() {
        return "Hoeffding Adaptive Tree for evolving data streams that uses ADWIN to replace branches for new ones.";
    }

 /*   public MultiChoiceOption leafpredictionOption = new MultiChoiceOption(
            "leafprediction", 'l', "Leaf prediction to use.", new String[]{
                "MC", "NB", "NBAdaptive"}, new String[]{
                "Majority class",
                "Naive Bayes",
                "Naive Bayes Adaptive"}, 2);*/

    public interface NewNode {

        // Change for adwin
        //public boolean getErrorChange();
        public int numberLeaves();

        void setAlternateSubtree(boolean isAlternate);

		public double getErrorEstimation();

        public double getErrorWidth();

        public boolean isNullError();

        public void killTreeChilds(SubConceptTree ht);

        public void learnFromInstance(Instance inst, SubConceptTree ht, int parentBranch);

        public void filterInstanceToLeaves(Instance inst, SplitNode myparent, int parentBranch, List<AdaFoundNode> foundNodes,
                boolean updateSplitterCounts);

        public boolean isAlternate();

        public void setAlternate(boolean isAlternate);

		public void filterInstanceToLeavesForPrediction(Instance inst, AdaSplitNode parent, int parentBranch,
				List<AdaFoundNode> nodes, boolean updateSplitterCounts);

		public boolean isRoot();

		public void setRoot(boolean isRoot);

		public void setParent(AdaSplitNode parent);

		public AdaSplitNode getParent();

		public void setMainBranch(AdaSplitNode parent);

		public AdaSplitNode getMainBranch();

        }

    public static class AdaSplitNode extends SplitNode implements NewNode {

        private static final long serialVersionUID = 1L;


        protected Node alternateTree;

        protected ADWIN adwin; // this was called estimationErrorWeight
        //public boolean isAlternateTree = false;

        public boolean errorHasChanged = false;

        protected int randomSeed = 1;

        protected Random classifierRandom;

        public boolean isAlternate = false;

		private boolean isRoot = false;

		private AdaSplitNode parent = null;


		private AdaSplitNode mainBranch = null;



        //public boolean getErrorChange() {
        //		return ErrorChange;
        //}

        @Override
        public void describeSubtree(HoeffdingTree ht, StringBuilder out,
                int indent) {
            for (int branch = 0; branch < numChildren(); branch++) {
                Node child = getChild(branch);
                if (child != null) {

                    StringUtils.appendIndented(out, indent, "if ");
                    out.append(this.splitTest.describeConditionForBranch(branch,
                            ht.getModelContext()));
                    out.append(": ");
                    if (((NewNode)child).isAlternate()) {
                        out.append("=====" + ((NewNode)child).isAlternate()+ "====");
                    }else if (((NewNode)child).getParent() == null ){
                        out.append("++++++++++++++++++");

                    }

                    StringUtils.appendNewline(out);


                    child.describeSubtree(ht, out, indent + 4);
                }
            }
        }



        @Override
        public int calcByteSizeIncludingSubtree() {
            int byteSize = calcByteSize();
            if (alternateTree != null) {
                byteSize += alternateTree.calcByteSizeIncludingSubtree();
            }
            if (adwin != null) {
                byteSize += adwin.measureByteSize();
            }
            for (Node child : this.children) {
                if (child != null) {
                    byteSize += child.calcByteSizeIncludingSubtree();
                }
            }
            return byteSize;
        }

        public AdaSplitNode(InstanceConditionalTest splitTest,
                double[] classObservations, int size) {
            super(splitTest, classObservations, size);
            this.classifierRandom = new Random(this.randomSeed);
        }

        public AdaSplitNode(InstanceConditionalTest splitTest,
                double[] classObservations) {
            super(splitTest, classObservations);
            this.classifierRandom = new Random(this.randomSeed);
        }

        @Override
        public int numberLeaves() {
            int numLeaves = 0;
            for (Node child : this.children) {
                if (child != null) {
                    numLeaves += ((NewNode) child).numberLeaves();
                }
            }
            return numLeaves;
        }

        @Override
        public double getErrorEstimation() {
            return this.adwin.getEstimation();
        }

        @Override
        public double getErrorWidth() {
            double w = 0.0;
            if (isNullError() == false) {
                w = this.adwin.getWidth();
            }
            return w;
        }

        @Override
        public boolean isNullError() {
            return (this.adwin == null);
        }

        // SplitNodes can have alternative trees, but LearningNodes can't
        // LearningNodes can split, but SplitNodes can't
        // Parent nodes are allways SplitNodes
        @Override
        public void learnFromInstance(Instance inst, SubConceptTree ht, int parentBranch) {
            int trueClass = (int) inst.classValue();
            //New option vore
            int k = MiscUtils.poisson(1.0, this.classifierRandom);
            Instance weightedInst = inst.copy();

            //Compute ClassPrediction using filterInstanceToLeaf
            //int ClassPrediction = Utils.maxIndex(filterInstanceToLeaf(inst, null, -1).node.getClassVotes(inst, ht));
            int ClassPrediction = 0;
            Node s = filterInstanceToLeaf(inst, parent, parentBranch).node;
            if (s != null) {
            	// This is where all paths must be explored
            	// Suppose you are currently on a main tree... you MUST find a node on the main tree that is a normal node
            	// However, if you find here a node that is an alternate to your main tree while predicting... you have an issue
            	// Our filter test tells us that this is actually happening
            	// How can this be fixed? Have clearly delineated filters
            	// I will first write a filter that lets you go down both alternate and  the alternate nodes. I will use it both for learning and prediction
            	// before writing a separate one for learning that filters examples down all available alternate paths for learning

            	// Proof of HAT-ADWIN bug
            	// We're only finding one leaf, whether mainline or alternate. That leads to the problem!
            	// We need to find all of them at this point. They all need to learn. All alternate subtrees must learn. Only the main tree is used for prediction.
            	// I suppose what the original authors thought was happening was that
//            	if(s!=null && parent!=null) {
//            		if(((NewNode)s).isAlternate() && !((NewNode)parent).isAlternate()) {
//            			//System.err.println("Alternate child of standard parent");
//            		}
//            	}

                ClassPrediction = Utils.maxIndex(filterInstanceToLeaf(inst, parent, parentBranch).node.getClassVotes(inst, ht)); // why filter again... just use s
            }

            boolean correctlyClassified = (trueClass == ClassPrediction);

            if (this.adwin == null) {
                this.adwin = new ADWIN();
            }
            double oldError = this.getErrorEstimation();

            this.errorHasChanged = this.adwin.setInput(correctlyClassified == true ? 0.0 : 1.0);
            //setInput really returns a boolean: change detected or not. This is the input to ADWIN.

            if (this.errorHasChanged == true && oldError > this.getErrorEstimation()) {
                //if error is decreasing, don't do anything
                this.errorHasChanged = false;
            }
            else if (this.errorHasChanged == true && oldError <= this.getErrorEstimation() && correctlyClassified == false){
                if (k > 0) {
                   weightedInst.setWeight(inst.weight() * k);
                }
                ////System.err.println("Weighted Instance weight is:" + weightedInst.weight());

            }

            // Check condition to build a new alternate tree
            //if (this.isAlternateTree == false) {
            if (this.errorHasChanged == true && !this.isAlternate()) {// disabling alternates of alternates
            	//&& this.alternateTree == null) { //should this be an else-if?

                //Start a new alternative tree : learning node
                this.alternateTree = ht.newLearningNode();
                ((NewNode)this.alternateTree).setAlternate(true);
                ((NewNode)this.alternateTree).setParent(this.parent);
                ((NewNode)this.alternateTree).setMainBranch(this);

                //System.err.println("Building alternate tree");
                //this.alternateTree.isAlternateTree = true;
                ht.alternateTrees++; //but... looks like you can only have one at a time...

                ////System.err.println("=======New Alternate initialised====" + " Depth: " + this.subtreeDepth());
            } // Check condition to replace tree

            else if (this.alternateTree != null && ((NewNode) this.alternateTree).isNullError() == false) {
                if (this.getErrorWidth() > 300 && ((NewNode) this.alternateTree).getErrorWidth() > 300) {
                	// you discard the alternate tree if your ADWIN buckets have over 300 instances in all
                	// in other words, this is claiming that 300 instances should suffice to determine subtree performance
                    double oldErrorRate = this.getErrorEstimation();
                    double altErrorRate = ((NewNode) this.alternateTree).getErrorEstimation();
                    double fDelta = .05;
                    //if (gNumAlts>0) fDelta=fDelta/gNumAlts;
                    double fN = 1.0 / (((NewNode) this.alternateTree).getErrorWidth()) + 1.0 / (this.getErrorWidth());
                    double Bound = Math.sqrt(2.0 * oldErrorRate * (1.0 - oldErrorRate) * Math.log(2.0 / fDelta) * fN);
                    if (Bound < (oldErrorRate - altErrorRate)
                    		//* Math.pow((double)(this.subtreeDepth()+1) / (ht.treeRoot.subtreeDepth()+2), 5)
                        	&& this.subtreeDepth() < 0
                    		) {
                		System.err.println("Change depth: " +
                    		(1.0 - (double)(this.subtreeDepth()+1) / (ht.treeRoot.subtreeDepth()+1))
                    		+ " of distance from root to leaf" + " at time " + numInstances);
                    	// Bound is +ve. If oldErrorRate is smaller, Bound > -ve RHS, so this is fine.

                        // System.err.println("++++++++Alternate picked for tree of" + " Depth: " + this.subtreeDepth());

                        // Switch alternate tree
                        ht.activeLeafNodeCount -= this.numberLeaves();
                        ht.activeLeafNodeCount += ((NewNode) this.alternateTree).numberLeaves();
                        this.killTreeChilds(ht);
                        ((NewNode)(this.alternateTree)).setAlternateSubtree(false);
                        ((NewNode)(this.alternateTree)).setAlternate(false);
                        ((NewNode)(this.alternateTree)).setMainBranch(null);

                        if (!this.isRoot()) {
                        	if(parent == null){
                        		System.err.println("Non-root node has null parent");
                            	StringBuilder out = new StringBuilder();

                        		//((AdaSplitNode)ht.treeRoot).describeSubtree(ht, out, 2);
                        		this.describeSubtree(ht, out, 2);

                        		//System.err.print(out);
                        		//System.exit(0);
                        	}
                            parent.setChild(parentBranch, this.alternateTree);
                            ((NewNode)this.alternateTree).setParent(this.getParent());
                            //((AdaSplitNode) parent.getChild(parentBranch)).alternateTree = null;
                        } else {
                            // Switch root tree
                        	((NewNode)(this.alternateTree)).setRoot(true);
                            ht.treeRoot = this.alternateTree;
                        }
                        ht.switchedAlternateTrees++; //Never Initialised?
                    } else if (Bound < altErrorRate - oldErrorRate) { // Once again, Bound is +ve.
                        // Erase alternate tree
                        if (this.alternateTree instanceof ActiveLearningNode) {
                            this.alternateTree = null;
                            //ht.activeLeafNodeCount--;
                        } else if (this.alternateTree instanceof InactiveLearningNode) {
                            this.alternateTree = null;
                            //ht.inactiveLeafNodeCount--;
                        } else {
                            ((AdaSplitNode) this.alternateTree).killTreeChilds(ht);
                        }
                        ht.prunedAlternateTrees++;
                    }
                }
            }
            //}
            //learnFromInstance alternate Tree and Child nodes

            if (this.alternateTree != null) { //
                ((NewNode) this.alternateTree).learnFromInstance(weightedInst, ht, parentBranch);
            }//
            int childBranch = this.instanceChildIndex(inst); //
            Node child = this.getChild(childBranch); //
            if (child != null) { //
                ((NewNode) child).learnFromInstance(inst, ht, childBranch);
                if (!((NewNode)this).isAlternate() && ((NewNode)child).isAlternate()){
                	System.err.println("Alternate child of main branch parent!!");
                }
            }

        }

        @Override
        public void killTreeChilds(SubConceptTree ht) {
            for (Node child : this.children) {
                if (child != null) {
                    //Delete alternate tree if it exists
                    if (child instanceof AdaSplitNode && ((AdaSplitNode) child).alternateTree != null) {
                        ((NewNode) ((AdaSplitNode) child).alternateTree).killTreeChilds(ht);
                        ht.prunedAlternateTrees++;
                    }
                    //Recursive delete of SplitNodes
                    if (child instanceof AdaSplitNode) {
                        ((NewNode) child).killTreeChilds(ht);
                    }
                    if (child instanceof ActiveLearningNode) {
                        child = null;
                        ht.activeLeafNodeCount--;
                    } else if (child instanceof InactiveLearningNode) {
                        child = null;
                        ht.inactiveLeafNodeCount--;
                    }
                }
            }
        }

        @Override
        public void setAlternateSubtree(boolean isAlternate) {
          for (Node child : this.children) {
            if (child != null) {
              ((NewNode)child).setAlternateSubtree(isAlternate);
            }
            this.setAlternate(isAlternate);
          }
        }



        //New for option votes
        //@Override
        @Override
		public void filterInstanceToLeaves(Instance inst, SplitNode myparent,
                int parentBranch, List<AdaFoundNode> foundNodes,
                boolean updateSplitterCounts) {
            if (updateSplitterCounts) {
                this.observedClassDistribution.addToValue((int) inst.classValue(), inst.weight());
            }
        	if (this.isAlternate()){
        		////System.err.println("Alternate node found even though filtering through alternates is turned off");
        		//An alternate node has been found.
        	}

        	// Can a normal SplitNode have an alternate child LearningNode?

        	if(myparent!= null && this!=null) {
        		if((!((NewNode)myparent).isAlternate()) && this.isAlternate()){
        			//System.err.println("Alternate child of standard parent");
        		}
        	}

            int childIndex = instanceChildIndex(inst);
            if (childIndex >= 0) {
                Node child = getChild(childIndex);
                if (child != null){
                	//if(!((NewNode)child).isAlternate() && !this.isAlternate()){
                    ((NewNode) child).filterInstanceToLeaves(inst, this, childIndex,
                            foundNodes, updateSplitterCounts);
                	//}
                } else {//if (!this.isAlternate()){

                    foundNodes.add(new AdaFoundNode(null, this, childIndex));
                    //... this will have a mixture of alternate and normal foundNodes
                    // that's because only the top of the main tree has parent -1, and only the top of each alternate tree has parent -999
                    // looks like nodes found in alternates of alternates voting is good for accuracy
                    // but first... do I need to choose nodes so I extract default HoeffdingTree behavior? I mean... simply disabling tree building gets me that
                    // as long as child is null... node gets added to foundNodes
                }
            }
            if (this.alternateTree != null) {
            	if (this.alternateTree.getClass() == AdaSplitNode.class){
            		((NewNode) this.alternateTree).filterInstanceToLeaves(inst, (SplitNode)this.alternateTree, -999, foundNodes, updateSplitterCounts);
            		// provide an alternate node as parent!
            	}
            }

        	// why does an alternate node vote even when this is turned off? why do they feature in the foundNodes?
            // when no tree is built, they don't exist, but when you build one...
            // an alternate tree might identify it's root as -999, but the standard parent of an alternate learning node won't
            // so instances will get filtered to alternate leaves if they happen to be child nodes of a standard parent
            // when that happens, they will be added to foundNodes, and have a chance to vote
            // but why do standard parent split nodes have alternate child nodes in the first place?
            // because of this line above. shouldn't it pass this.alternateTree as parent??

        }


        // Make sure a non-alternate leaf is found
		@Override
		public void filterInstanceToLeavesForPrediction(Instance inst, AdaSplitNode myparent,
                int parentBranch, List<AdaFoundNode> foundNodes,
                boolean updateSplitterCounts) {
            if (updateSplitterCounts) {
                this.observedClassDistribution.addToValue((int) inst.classValue(), inst.weight());
            }
        	if (this.isAlternate()){
        		////System.err.println("Alternate node found even though filtering through alternates is turned off");
        		//An alternate node has been found.
        	}

        	// Can a normal SplitNode have an alternate child LearningNode?

        	if(myparent!= null && this!=null) {
        		if((!((NewNode)myparent).isAlternate()) && this.isAlternate()){

        		//System.err.println("Alternate child of standard parent");
        		//System.exit(1);

        		// Design decision: Alternate nodes won't have standard parents.
        		// Alternates that have just branched off from a main node will have parents set to null.
        		// If for some reason in future I want to traverse up the tree, alternate or not,
        		// I can create a mapping between alternates and the nodes they've branched out from so the traversal can continue
        		// i.e. I will create a mapping from alternate nodes with null parents to their respective main nodes

        		}
        	}

            int childIndex = instanceChildIndex(inst);
            if (childIndex >= 0) {
                Node child = getChild(childIndex);
                if (child != null){
                	if(((NewNode)child).isAlternate() && !this.isAlternate()){
                		////System.err.println("Alternate child of standard parent");
                		//System.exit(1);
                	}
                    ((NewNode) child).filterInstanceToLeavesForPrediction(inst, this, childIndex,
                            foundNodes, updateSplitterCounts);
                	//}
                } else { //if (!this.isAlternate()){

                    foundNodes.add(new AdaFoundNode(null, this, childIndex));

            }
            if (this.alternateTree != null) {
            	if (this.alternateTree.getClass() == AdaSplitNode.class){
            		//((NewNode) this.alternateTree).filterInstanceToLeaves(inst, (SplitNode)this.alternateTree, -999, foundNodes, updateSplitterCounts);
            		// provide an alternate node as parent!
            	}
            }
          }
            if (foundNodes.size() < 1) {
            	System.err.println(foundNodes.size() + " predictors found");

            	//System.exit(1);
            }

        }



		@Override
		public boolean isAlternate() {
			return this.isAlternate;
		}

		@Override
		public void setAlternate(boolean isAlternate) {
			this.isAlternate = isAlternate;
		}


		@Override
		public boolean isRoot() {
			return this.isRoot;
		}



		@Override
		public void setRoot(boolean isRoot) {
			this.isRoot = isRoot;

		}



		@Override
		public void setParent(AdaSplitNode parent) {
			this.parent = parent;

		}



		@Override
		public AdaSplitNode getParent() {
			return this.parent;
		}



		@Override
		public void setMainBranch(AdaSplitNode mainBranch) {
			this.mainBranch = mainBranch;
		}

		@Override
		public AdaSplitNode getMainBranch() {
			return this.mainBranch;
		}


    }

    // if you disable building an alternateTree, you approximate VFDT; if you disable learning for the alternate tree, you approximate VFDT.
    // it must be possible to allow the alternateTree to learn and still not use it's outputs so as to approximate VFDT
    // what must be happening is that if the alternate tree is allowed to learn, no main tree leaf sees the example
    // so excluding alternate leaves kills the learner. Is this what is happening?
    // We will need two different filters for prediction and learning
    // the learning filter must explore at least one main tree path. It must also explore any alternate paths available.
    // If you have alternates of alternates... the learning filter must let the instance filter down all possible prediction paths.
    // the prediction filter must only consider main tree paths

    public static class AdaLearningNode extends LearningNodeNBAdaptive implements NewNode {

        private static final long serialVersionUID = 1L;

        protected ADWIN estimationErrorWeight;

        public boolean ErrorChange = false;

        protected int randomSeed = 1;

        protected Random classifierRandom;

        public boolean isAlternate = false;

        private boolean isRoot = false;

		private AdaSplitNode parent = null;

		private AdaSplitNode mainBranch = null;

        @Override
        public int calcByteSize() {
            int byteSize = super.calcByteSize();
            if (estimationErrorWeight != null) {
                byteSize += estimationErrorWeight.measureByteSize();
            }
            return byteSize;
        }

        public AdaLearningNode(double[] initialClassObservations) {
            super(initialClassObservations);
            this.classifierRandom = new Random(this.randomSeed);
            this.setAlternate(false);
        }

        @Override
        public int numberLeaves() {
            return 1;
        }

        @Override
        public double getErrorEstimation() {
            if (this.estimationErrorWeight != null) {
                return this.estimationErrorWeight.getEstimation();
            } else {
                return 0;
            }
        }

        @Override
        public double getErrorWidth() {
            return this.estimationErrorWeight.getWidth();
        }

        @Override
        public boolean isNullError() {
            return (this.estimationErrorWeight == null);
        }

        @Override
        public void killTreeChilds(SubConceptTree ht) {
        }

		@Override
		public void learnFromInstance(Instance inst, SubConceptTree ht, int parentBranch) {
            int trueClass = (int) inst.classValue();
            //New option vore
            int k = MiscUtils.poisson(50.0, this.classifierRandom);
            Instance weightedInst = inst.copy();

            //Compute ClassPrediction using filterInstanceToLeaf
            int ClassPrediction = Utils.maxIndex(this.getClassVotes(inst, ht));

            boolean correctlyClassified = (trueClass == ClassPrediction);

            if (k > 0 && !correctlyClassified && this.isAlternate()) {
                weightedInst.setWeight(inst.weight() * k);
            }

            if (this.estimationErrorWeight == null) {
                this.estimationErrorWeight = new ADWIN();
            }
            double oldError = this.getErrorEstimation();
            this.ErrorChange = this.estimationErrorWeight.setInput(correctlyClassified == true ? 0.0 : 1.0);
            if (this.ErrorChange == true && oldError > this.getErrorEstimation()) {
                this.ErrorChange = false;
            }

            //Update statistics
            learnFromInstance(weightedInst, ht);	//inst

            //Check for Split condition
            double weightSeen = this.getWeightSeen();
            if (weightSeen
                    - this.getWeightSeenAtLastSplitEvaluation() >= ht.gracePeriodOption.getValue()) {
                ht.attemptToSplit(this, parentBranch); //note that weighting misclassified instances will push us to attempt to split earlier than usual...
                this.setWeightSeenAtLastSplitEvaluation(weightSeen);
            }


            //learnFromInstance alternate Tree and Child nodes
			/*if (this.alternateTree != null)  {
            this.alternateTree.learnFromInstance(inst,ht);
            }
            for (Node child : this.children) {
            if (child != null) {
            child.learnFromInstance(inst,ht);
            }
            }*/
        }



        @Override
        public double[] getClassVotes(Instance inst, HoeffdingTree ht) {
            double[] dist;
            int predictionOption = ((SubConceptTree) ht).leafpredictionOption.getChosenIndex();
            if (predictionOption == 0) { //MC
                dist = this.observedClassDistribution.getArrayCopy();
            } else if (predictionOption == 1) { //NB
                dist = NaiveBayes.doNaiveBayesPrediction(inst,
                        this.observedClassDistribution, this.attributeObservers);
            } else { //NBAdaptive
                if (this.mcCorrectWeight > this.nbCorrectWeight) {
                    dist = this.observedClassDistribution.getArrayCopy();
                } else {
                    dist = NaiveBayes.doNaiveBayesPrediction(inst,
                            this.observedClassDistribution, this.attributeObservers);
                }
            }
            //New for option votes
            double distSum = Utils.sum(dist);
            if (distSum * this.getErrorEstimation() * this.getErrorEstimation() > 0.0) {
                Utils.normalize(dist, distSum * this.getErrorEstimation() * this.getErrorEstimation()); //Adding weight
            }
            return dist;
        }

        //New for option votes
        @Override
		public void filterInstanceToLeaves(Instance inst,
                SplitNode splitparent, int parentBranch,
                List<AdaFoundNode> foundNodes, boolean updateSplitterCounts) {
        	if (this.isAlternate()){
        		////System.err.println("Alternate node found even though filtering through alternates is turned off");
        		////System.err.println("The parent is parentBranch " + parentBranch);
        		//So... the alternate node comes from here, even before a split node ever creates an alternate.
        		// but only split nodes should be able to create alternates!
        	}

            foundNodes.add(new AdaFoundNode(this, splitparent, parentBranch));
        }

		@Override
		public boolean isAlternate() {
			return this.isAlternate;
		}

		@Override
		public void setAlternate(boolean isAlternate) {
			this.isAlternate = isAlternate;
		}

		@Override
		public void filterInstanceToLeavesForPrediction(Instance inst, AdaSplitNode splitParent, int parentBranch,
				List<AdaFoundNode> foundNodes, boolean updateSplitterCounts) {
			if (splitParent!=null){
				//System.err.println("Parent alternate? " + splitParent.isAlternate() + " Leaf alternate? " + this.isAlternate());
			}
			else{
				//System.err.println("Parent is null. "  + "Leaf alternate? " + this.isAlternate());

			}

				if(!this.isAlternate()){
					foundNodes.add(new AdaFoundNode(this, splitParent, parentBranch));
				}

		}

		@Override
		public boolean isRoot() {
			return this.isRoot;
		}

		@Override
		public void setRoot(boolean isRoot) {
			this.isRoot = isRoot;

		}

		@Override
		public void setParent(AdaSplitNode parent) {
			this.parent = parent;

		}

		@Override
		public AdaSplitNode getParent() {
			return this.parent;
		}

		@Override
		public void setMainBranch(AdaSplitNode mainBranch) {
			this.mainBranch = mainBranch;
		}

		@Override
		public AdaSplitNode getMainBranch() {
			return this.mainBranch;
		}

		@Override
		public void setAlternateSubtree(boolean isAlternate) {
			this.setAlternate(isAlternate);
		}

    }

    public static class AdaFoundNode extends FoundNode{

		public AdaFoundNode(Node node, SplitNode parent, int parentBranch) {
			super(node, parent, parentBranch);
			// TODO Auto-generated constructor stub
		}
    }

    protected int alternateTrees;

    protected int prunedAlternateTrees;

    protected int switchedAlternateTrees;


    // leaves with perfect classifications must be getting pulled regardless of whether they are alternates or not!!
    // that would explain the bug
    @Override
    protected LearningNode newLearningNode() {
        return newLearningNode(new double[0]);
    }

    protected void attemptToSplit(ActiveLearningNode node, int parentIndex) {
    	// pure nodes skip this whole function body. Why would that let them vote even if they're alternates?

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

            	//System.err.println(bestSuggestion.merit + " " + secondBestSuggestion.merit + " " +
            	//InfoGainSplitCriterion.computeEntropy(node.getObservedClassDistribution()));


                if(bestSuggestion.merit > InfoGainSplitCriterion.computeEntropy(node.getObservedClassDistribution())){
                	//System.err.println(bestSuggestion.merit + " " + secondBestSuggestion.merit + " " + InfoGainSplitCriterion.computeEntropy(node.getObservedClassDistribution()));
                }
                if ((bestSuggestion.merit - secondBestSuggestion.merit > hoeffdingBound)
                        || (hoeffdingBound < this.tieThresholdOption.getValue())) {
                    shouldSplit = true;
                }
                else if(bestSuggestion.merit > InfoGainSplitCriterion.computeEntropy(node.getObservedClassDistribution())){

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

                AttributeSplitSuggestion splitDecision = bestSplitSuggestions[bestSplitSuggestions.length - 1];
                if (splitDecision.splitTest == null) {
                    // preprune - null wins
                    deactivateLearningNode(node, ((NewNode)node).getParent(),parentIndex);
                } else {
                    AdaSplitNode newSplit = newSplitNode(splitDecision.splitTest,
                            node.getObservedClassDistribution(),splitDecision.numSplits() );
                    ((NewNode) newSplit).setAlternate(((NewNode)node).isAlternate()); // set alternate status to that of the learning node being split from
                    ((NewNode) newSplit).setParent(((NewNode)node).getParent()); // set parent to that of the learning node being split from

                    for (int i = 0; i < splitDecision.numSplits(); i++) {
                        AdaLearningNode newChild = newLearningNode(splitDecision.resultingClassDistributionFromSplit(i));
                        ((NewNode) newChild).setAlternate(((NewNode)newSplit).isAlternate()); // set alternate status to that of the parent split node
                        ((NewNode) newChild).setParent(newSplit); // set the parent when node is split
                        newSplit.setChild(i, newChild);

                        if(newSplit.isAlternate() != newChild.isAlternate()){
                        	System.err.println("Bug here!!!!!!");
                        }

                    }
                    this.activeLeafNodeCount--;
                    this.decisionNodeCount++;
                    this.activeLeafNodeCount += splitDecision.numSplits();
                    if (((NewNode)node).isRoot()) {
                        ((NewNode) newSplit).setAlternate(false);
                        ((NewNode) newSplit).setParent(null);
                        ((NewNode) newSplit).setRoot(true);
                        this.treeRoot = newSplit;
                        //System.err.println((newSplit.isRoot()) + " " + (newSplit.getParent()==null));
                        //System.err.println("================Root has been split==============");
                    }
                    else if (((NewNode)node).getMainBranch() != null) { // if the node happens to have a main branch
                    	((NewNode)node).getMainBranch().alternateTree = newSplit;
                    }
                    else {
                        newSplit.getParent().setChild(parentIndex, newSplit);
                    }
                }
                // manage memory
                enforceTrackerLimit();
            }
        }
    }




    @Override
    protected AdaLearningNode newLearningNode(double[] initialClassObservations) {
        // IDEA: to choose different learning nodes depending on predictionOption
        return new AdaLearningNode(initialClassObservations);
    }

   @Override
    protected AdaSplitNode newSplitNode(InstanceConditionalTest splitTest,
            double[] classObservations, int size) {
        return new AdaSplitNode(splitTest, classObservations, size);
    }

    @Override
    protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
            double[] classObservations) {
        return new AdaSplitNode(splitTest, classObservations);
    }

    @Override
    public void trainOnInstanceImpl(Instance inst) {

        if (this.treeRoot == null) {
            this.treeRoot = newLearningNode();
            this.activeLeafNodeCount = 1;
            ((NewNode) this.treeRoot).setAlternate(false);
            ((NewNode) this.treeRoot).setRoot(true);
            ((NewNode) this.treeRoot).setParent(null);
            ((NewNode) this.treeRoot).setMainBranch(null);
        }
        ((NewNode) this.treeRoot).learnFromInstance(inst, this, -1);
    }

    //New for options vote
    public AdaFoundNode[] filterInstanceToLeavesForPrediction(Instance inst,
            AdaSplitNode parent, AdaSplitNode mainBranch, int parentBranch, boolean updateSplitterCounts) {
        List<AdaFoundNode> nodes = new LinkedList<AdaFoundNode>();
        ((NewNode) this.treeRoot).filterInstanceToLeavesForPrediction(inst, parent, parentBranch, nodes,
                updateSplitterCounts);
        return nodes.toArray(new AdaFoundNode[nodes.size()]);
    }

    // The behaviour of a HAT-ADWIN that doesn't allow alternate substitution but allows an alternate to be built
    // being similar to HAT-ADWIN would indicate that the alternate is being allowed to vote...!
    @Override
    public double[] getVotesForInstance(Instance inst) {
    	numInstances++;

    	if (numInstances > 100000 && numInstances%100000 == 0){
    		//System.err.println(numInstances);
    	}

        if (this.treeRoot != null) {
            AdaFoundNode[] foundNodes = filterInstanceToLeavesForPrediction(inst,
                    null, null, -1, false);
            DoubleVector result = new DoubleVector();
            int predictionPaths = 0;
            for (AdaFoundNode foundNode : foundNodes) {
                //if (foundNode.parentBranch != -999 ){
            	 //(!((NewNode)foundNode.node).isAlternate()){
//(1>0){
                    Node leafNode = foundNode.node;
                    if (leafNode == null) {
                        leafNode = foundNode.parent;
                    }

                    if(((NewNode)leafNode).isAlternate()){
                    	System.err.println("An alternate node has voted. It is of " + leafNode.getClass()); // AdaLearningNode, as expected.
                    	StringBuilder out = new StringBuilder();
                    	((AdaSplitNode)treeRoot).describeSubtree(this, out, 2);
                    	//foundNode.parent.describeSubtree(this, out, 2);
                    	out.append("\n\n+++++++++++++++++++++++++++\n\n");
                    	if(((AdaSplitNode)foundNode.parent).alternateTree !=null) {
                    	    ((AdaSplitNode)foundNode.parent).alternateTree.describeSubtree(this, out, 2);
                    	}
                    	else{
                    		out.append("Parent has no alternate, and parent alternate status is " + ((AdaSplitNode)foundNode.parent).isAlternate());
                    	}
                    	out.append("\n\n+++++++++++++++++++++++++++\n\n");

                    	((AdaSplitNode)treeRoot).describeSubtree(this, out, 2);

                    	//System.err.print(out);
                    	System.exit(1);
                    }else if (numInstances%25000 == 0){
                    	StringBuilder out = new StringBuilder();

                    	treeRoot.describeSubtree(this, out, 2);
                    	out.append(numInstances + " instances\n");
                    	//System.out.print(out);

                    }

                    double[] dist = leafNode.getClassVotes(inst, this);
                    //Albert: changed for weights
                    //double distSum = Utils.sum(dist);
                    //if (distSum > 0.0) {
                    //	Utils.normalize(dist, distSum);
                    //}

                    if(!((NewNode)leafNode).isAlternate()){
                    	result.addValues(dist);
                    }
                    predictionPaths++;
                //}
            }

            ////System.err.println("prediction paths = " + predictionPaths);
            //if (predictionPaths > this.maxPredictionPaths) {
            //	this.maxPredictionPaths++;
            //}
            return result.getArrayRef();
        }
        return new double[0];
    }
}