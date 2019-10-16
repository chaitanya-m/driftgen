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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import moa.classifiers.bayes.NaiveBayes;
import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.driftdetection.ADWIN;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.classifiers.trees.HATOriginal.AdaSplitNode;
import moa.classifiers.trees.HoeffdingTree.FoundNode;
import moa.classifiers.trees.HoeffdingTree.Node;
import moa.core.DoubleVector;
import moa.core.MiscUtils;
import moa.core.Utils;

import com.github.javacliparser.FlagOption;
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
public class HAT extends VFDT {

    public FlagOption alternateVoterOption = new FlagOption("alternatesVote", 'A',
            "Allow alternates to vote");
    public FlagOption allowAlternatesofAlternatesOption = new FlagOption("alternatesHaveAlternates", 'B',
            "Allow alternates to sprout their own alternates");
    public FlagOption rootLevelBug = new FlagOption("rootLevelBug", 'F',
            "Top level: an alternate of an alternate (or so on) substitutes the root in ADWIN substitution");
    public FlagOption parentBug = new FlagOption("parentBug", 'G',
            "When an alternate is split, its parent replaces the mainline child with the alternate");
    public FlagOption singleLeafAlternateDoesntVote = new FlagOption("singleLeafAlternateDoesntVote", 'H',
            "In the original code, though the intention seems to be not to let alternates vote, only single leaf alternates don't do so because of -999");
    public FlagOption leafWeighting = new FlagOption("leafWeighting", 'I',
            "In the original code, learnFromInstance weights at leaves with a Poisson");
    
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

        void setAlternateStatusForSubtreeNodes(boolean isAlternate);

		public double getErrorEstimation();

        public double getErrorWidth();

        public boolean isNullError();

        public void killTreeChilds(HAT ht);

        public void learnFromInstance(Instance inst, HAT ht, SplitNode parent, int parentBranch);

        public void filterInstanceToLeaves(Instance inst, SplitNode myparent, int parentBranch, List<FoundNode> foundNodes,
                boolean updateSplitterCounts);

        public boolean isAlternate();

        public void setAlternate(boolean isAlternate);

		public boolean isRoot();

		public void setRoot(boolean isRoot);

		public void setMainlineNode(AdaSplitNode parent);

		public AdaSplitNode getMainlineNode();

		public void setParent(AdaSplitNode parent);

		public AdaSplitNode getParent();

    }

    public static class AdaSplitNode extends SplitNode implements NewNode {

        private static final long serialVersionUID = 1L;

        protected Node alternateTree;

        protected ADWIN estimationErrorWeight;
        //public boolean isAlternateTree = false;

        public boolean ErrorChange = false;

        protected int randomSeed = 1;

        protected Random classifierRandom;

        private boolean isAlternate = false;

        private boolean isRoot = false;

		private AdaSplitNode mainlineNode = null; //null by default unless there is an attachment point

		private AdaSplitNode parent = null;

		@Override
		public void setParent(AdaSplitNode parent) {
			this.parent = parent;

		}

		@Override
		public AdaSplitNode getParent() {
			return this.parent;
		}


		@Override
		public boolean isAlternate() {
			return this.isAlternate;
		}

		@Override
		public void setAlternate(boolean isAlternate) {
			this.isAlternate = isAlternate;
		}



        //public boolean getErrorChange() {
        //		return ErrorChange;
        //}
        @Override
        public int calcByteSizeIncludingSubtree() {
            int byteSize = calcByteSize();
            if (alternateTree != null) {
                byteSize += alternateTree.calcByteSizeIncludingSubtree();
            }
            if (estimationErrorWeight != null) {
                byteSize += estimationErrorWeight.measureByteSize();
            }
            for (Node child : this.children) {
                if (child != null) {
                    byteSize += child.calcByteSizeIncludingSubtree();
                }
            }
            return byteSize;
        }

        public AdaSplitNode(InstanceConditionalTest splitTest,
                double[] classObservations, int size, boolean isAlternate) {
            super(splitTest, classObservations, size);
            this.classifierRandom = new Random(this.randomSeed);
            this.setAlternate(isAlternate);
        }

        public AdaSplitNode(InstanceConditionalTest splitTest,
                double[] classObservations, boolean isAlternate) {
            super(splitTest, classObservations);
            this.classifierRandom = new Random(this.randomSeed);
            this.setAlternate(isAlternate);
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
            return this.estimationErrorWeight.getEstimation();
        }

        @Override
        public double getErrorWidth() {
            double w = 0.0;
            if (isNullError() == false) {
                w = this.estimationErrorWeight.getWidth();
            }
            return w;
        }

        @Override
        public boolean isNullError() {
            return (this.estimationErrorWeight == null);
        }


		public Instance computeErrorChangeAndWeightInst(Instance inst, HAT ht, SplitNode parent, int parentBranch) {
			
            int trueClass = (int) inst.classValue();
            //New option vore
            int k = MiscUtils.poisson(1.0, this.classifierRandom);
            Instance weightedInst = inst.copy();
            if (k > 0) {
                //weightedInst.setWeight(inst.weight() * k);
            }
            //Compute ClassPrediction using filterInstanceToLeaf
            //int ClassPrediction = Utils.maxIndex(filterInstanceToLeaf(inst, null, -1).node.getClassVotes(inst, ht));
            int ClassPrediction = 0;
            Node leaf = filterInstanceToLeaf(inst, this.getParent(), parentBranch).node;
            if (leaf != null) {
                ClassPrediction = Utils.maxIndex(leaf.getClassVotes(inst, ht));
            }

            boolean blCorrect = (trueClass == ClassPrediction);

            if (this.estimationErrorWeight == null) {
                this.estimationErrorWeight = new ADWIN();
            }
            this.ErrorChange = this.estimationErrorWeight.setInput(blCorrect == true ? 0.0 : 1.0);
            
            return weightedInst;
		}
        
        // SplitNodes can have alternative trees, but LearningNodes can't
        // LearningNodes can split, but SplitNodes can't
        // Parent nodes are always SplitNodes
        @Override
        public void learnFromInstance(Instance inst, HAT ht, SplitNode parent, int parentBranch) {

//            System.out.println("Main Tree is of depth " + ht.treeRoot.subtreeDepth());

        	Instance weightedInst = computeErrorChangeAndWeightInst(inst, ht, parent, parentBranch);
            double oldError = this.getErrorEstimation();
            if (this.ErrorChange == true && oldError > this.getErrorEstimation()) {
                //if error is decreasing, don't do anything
                this.ErrorChange = false;
            }

            // Check condition to build a new alternate tree
            if ((this.ErrorChange && !this.isAlternate()) || 
            		(this.ErrorChange && ht.allowAlternatesofAlternatesOption.isSet())){
            	// disabling alternates of alternates unless so specified

                //Start a new alternative tree : learning node
                this.alternateTree = ht.newLearningNode(true); // isAlternate is set to true
                ((NewNode)this.alternateTree).setMainlineNode(this); // this node is the alternate's attachment point
                ((NewNode)this.alternateTree).setParent(this.getParent());
                ht.alternateTrees++;
        		assert ((NewNode)(this.alternateTree)).getMainlineNode() != null;           
                
            } // Check condition to replace tree

            else if (this.alternateTree != null && ((NewNode) this.alternateTree).isNullError() == false) {
                if (this.getErrorWidth() > 300 && ((NewNode) this.alternateTree).getErrorWidth() > 300) { // magic number...
                    double oldErrorRate = this.getErrorEstimation();
                    double altErrorRate = ((NewNode) this.alternateTree).getErrorEstimation();
                    double fDelta = .05;
                    //if (gNumAlts>0) fDelta=fDelta/gNumAlts;
                    double fN = 1.0 / (((NewNode) this.alternateTree).getErrorWidth()) + 1.0 / (this.getErrorWidth());
                    double Bound = Math.sqrt(2.0 * oldErrorRate * (1.0 - oldErrorRate) * Math.log(2.0 / fDelta) * fN);

//                    System.out.print(this.alternateTree.subtreeDepth()
//                    		+ " " + this.subtreeDepth() +
//                    		" " + this.isRoot() +
//                    		" " + this.isAlternate());
//
//                    if(this.getParent() == null){
//                    	System.out.print(" ||parent is null; root level node||");
//                    }
//
//                    System.out.println();


                    if (Bound < oldErrorRate - altErrorRate
                    		  //&& this.subtreeDepth() < 4
                    		) {
                        //System.out.println("Main Tree is of depth " + ht.treeRoot.subtreeDepth());

                        // Switch alternate tree
                        ht.activeLeafNodeCount -= this.numberLeaves();
                        ht.activeLeafNodeCount += ((NewNode) this.alternateTree).numberLeaves();
                        this.killTreeChilds(ht);
                       /* 
                        // if alternates of alternates are enabled, replacement takes alternate status of this node 
                        if (ht.allowAlternatesofAlternatesOption.isSet()) {
                        	((NewNode)this.alternateTree).setAlternateStatusForSubtreeNodes(this.isAlternate());
                        	
                        	if(this.isAlternate() == false) { // no mainline for mainline nodes
                        		System.err.println("Substitution on mainline");
                        		assert ((NewNode)(this.alternateTree)).isAlternate() == false;
                        		
                        		((NewNode)(this.alternateTree)).setMainlineNode(null);

                        	}
                        	else {
                        		System.err.println("Substitution on alternate");
                        		//assert ((NewNode)(this.alternateTree)).isAlternate() == true;
                        		//assert ((NewNode)(this.alternateTree)).getMainlineNode() != null;
                        	}
                        }
                        else {
                        	((NewNode)this.alternateTree).setAlternateStatusForSubtreeNodes(false);
                        	((NewNode)(this.alternateTree)).setMainlineNode(null);
                        }*/
                        // Done preparing alternate substitute's status, move on to the substitution
                        // If alternates can have alternates, and we are simulating the root level bug
                        // wherein an alternate of an alternate may replace root...
                        // Or if we are not simulating it and the alternate just replaces the alternate or mainline it is replacing
                        // Or neither of these is set. In each case, whether the node is top level or not, root or not.
                        
                        //no, actually what is happening is when root level alternate has root level alternate and 
                        //needs to be replaced by it, treeroot is replaced instead by its alternate
                    	if(ht.allowAlternatesofAlternatesOption.isSet()) {
                    		if (ht.rootLevelBug.isSet()) {
                                if ( (!this.isRoot() && this.getParent() == null && this.isAlternate()) || this.isRoot() ) { 
                                	// top level alternate of this top level alternate replacing root directly instead of this node
                                    ht.treeRoot = ((AdaSplitNode) ht.treeRoot).alternateTree;

                            		((NewNode)(ht.treeRoot)).setRoot(true);
                            		((NewNode)(ht.treeRoot)).setParent(null);
                            		((NewNode)(ht.treeRoot)).setMainlineNode(null);
                            		((NewNode)(ht.treeRoot)).setAlternate(false);
                                	((NewNode)(ht.treeRoot)).setAlternateStatusForSubtreeNodes(false);

                                } else { // root level bug is set but we are replacing a non-top-level node
                            		((NewNode)(this.alternateTree)).setRoot(false);
                            		((NewNode)this.alternateTree).setParent(this.getParent());
                            		((NewNode)(this.alternateTree)).setMainlineNode(this.getMainlineNode());
                            		((NewNode)(this.alternateTree)).setAlternate(this.isAlternate());
                                	((NewNode)this.alternateTree).setAlternateStatusForSubtreeNodes(this.isAlternate());

                            		this.getParent().setChild(parentBranch, this.alternateTree);
                                }
                    		}
                    		else { // rootLevelBug is not set
                    			if ( (!this.isRoot() && this.getParent() == null && this.isAlternate())) {
                    				// this is a top level alternate and not root, so don't change root
                    			
                            		((NewNode)(this.alternateTree)).setRoot(this.isRoot());
                            		((NewNode)this.alternateTree).setParent(this.getParent());
                            		((NewNode)(this.alternateTree)).setMainlineNode(this.getMainlineNode());
                            		((NewNode)(this.alternateTree)).setAlternate(this.isAlternate());
                                	((NewNode)this.alternateTree).setAlternateStatusForSubtreeNodes(this.isAlternate());
                                	
                                	this.getMainlineNode().alternateTree = this.alternateTree; //replace the mainline's alternate with this.

                    			}
                    			else if (this.isRoot()){
                    				// this is root
                            		((NewNode)(this.alternateTree)).setRoot(true);
                               		((NewNode)this.alternateTree).setParent(null);
                            		((NewNode)(this.alternateTree)).setMainlineNode(null);
                            		((NewNode)(this.alternateTree)).setAlternate(false);
                                	((NewNode)this.alternateTree).setAlternateStatusForSubtreeNodes(false);

                                    ht.treeRoot = this.alternateTree;
                    			} else { // not top level
                            		((NewNode)(this.alternateTree)).setRoot(false);
                            		((NewNode)this.alternateTree).setParent(this.getParent());
                            		((NewNode)(this.alternateTree)).setMainlineNode(this.getMainlineNode());
                            		((NewNode)(this.alternateTree)).setAlternate(this.isAlternate());
                                	((NewNode)this.alternateTree).setAlternateStatusForSubtreeNodes(this.isAlternate());

                                	this.getParent().setChild(parentBranch, this.alternateTree);
                    			}
                    			
                    		}
                    	} else { // alternates may not have alternates

                    		if(!this.isRoot()) {// in the single alternate case there will be a parent
                        		((NewNode)(this.alternateTree)).setRoot(false);
                        		((NewNode)this.alternateTree).setParent(this.getParent());
                        		((NewNode)(this.alternateTree)).setMainlineNode(null);
                        		((NewNode)(this.alternateTree)).setAlternate(false);
                            	((NewNode)this.alternateTree).setAlternateStatusForSubtreeNodes(false);

                        		this.getParent().setChild(parentBranch, this.alternateTree);

                    		} else { // this is the Root and only a single alternate exists
                            	((NewNode)(this.alternateTree)).setRoot(true);
                            	((NewNode)(this.alternateTree)).setParent(null);
                            	((NewNode)(this.alternateTree)).setMainlineNode(null);
                        		((NewNode)(this.alternateTree)).setAlternate(false);
                            	((NewNode)this.alternateTree).setAlternateStatusForSubtreeNodes(false);

                                ht.treeRoot = this.alternateTree;
                    		}
                    	}

                        /*if (!this.isRoot()) {
                        	
                        	if(ht.allowAlternatesofAlternatesOption.isSet()) { 
                        		&& this.getParent() == null
                        		&& this.isAlternate()) { // alternate of a top level alternate
                        		((NewNode)(this.alternateTree)).setRoot(false);
                        		((NewNode)this.alternateTree).setParent(null);
                        		System.err.println(this.isAlternate());
                        		System.err.println(this.getMainlineNode() == null);
                        		this.getMainlineNode().alternateTree = this.alternateTree;
                        		}
                        	else { // in the single alternate case there will be a parent
                        		this.getParent().setChild(parentBranch, this.alternateTree);
                        		((NewNode)(this.alternateTree)).setRoot(false);
                        		((NewNode)this.alternateTree).setParent(this.getParent());
                        	}
                            
                            //((AdaSplitNode) parent.getChild(parentBranch)).alternateTree = null;
                        } else {
                            // Switch root tree
                        	((NewNode)(this.alternateTree)).setRoot(true);
                        	((NewNode)(this.alternateTree)).setParent(null);
                            ht.treeRoot = this.alternateTree;
                        }
                        if(!ht.allowAlternatesofAlternatesOption.isSet()) {
                        	this.alternateTree = null;
                        }*/
                        ht.switchedAlternateTrees++;
                    } else if (Bound < altErrorRate - oldErrorRate) {
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
            if (this.alternateTree != null) {
                ((NewNode) this.alternateTree).learnFromInstance(weightedInst, ht, this.getParent(), parentBranch);
            }
            int childBranch = this.instanceChildIndex(inst);
            Node child = this.getChild(childBranch);
            if (child != null) {
                ((NewNode) child).learnFromInstance(weightedInst, ht, this, childBranch);
            }
        }

		@Override
        public void setAlternateStatusForSubtreeNodes(boolean isAlternate) {

          this.setAlternate(isAlternate);

          for (Node child : this.children) {
            if (child != null) {
              ((NewNode)child).setAlternateStatusForSubtreeNodes(isAlternate);
            }
          }
        }



        @Override
        public void killTreeChilds(HAT ht) {
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

        //New for option votes
        //@Override
        @Override
		public void filterInstanceToLeaves(Instance inst, SplitNode myparent,
                int parentBranch, List<FoundNode> foundNodes,
                boolean updateSplitterCounts) {
            if (updateSplitterCounts) {
                this.observedClassDistribution.addToValue((int) inst.classValue(), inst.weight());
            }
            int childIndex = instanceChildIndex(inst);
            if (childIndex >= 0) {
                Node child = getChild(childIndex);
                if (child != null) {
                    ((NewNode) child).filterInstanceToLeaves(inst, this, childIndex,
                            foundNodes, updateSplitterCounts);
                    // this will usually just take you down one path until you hit a learning node. Unless you are overextending
                    // your tree without pruning
                } else {
                    foundNodes.add(new FoundNode(null, this, childIndex));
                    // Only killTreeChilds would create null child nodes
                }
            }
            if (this.alternateTree != null) {
                ((NewNode) this.alternateTree).filterInstanceToLeaves(inst, this, -999, foundNodes, updateSplitterCounts);
                // the -999 used to launch this subtree filter becomes inutile immediately following
                // the top node of the subtree. Only the immediate children of a split will see this as a parentBranch
                // So a foundnode created further down cannot be distinguished from the mainline thing
                // Using this to separate out the alternate found nodes from the mainline ones won't work.
                // But that is how it seems to be used...

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
		public void setMainlineNode(AdaSplitNode mainlineNode) {
			this.mainlineNode  = mainlineNode;
		}

		@Override
		public AdaSplitNode getMainlineNode() {
			return this.mainlineNode;
		}

    }

    public static class AdaLearningNode extends LearningNodeNBAdaptive implements NewNode {

        private static final long serialVersionUID = 1L;

        protected ADWIN estimationErrorWeight;

        public boolean ErrorChange = false;

        protected int randomSeed = 1;

        protected Random classifierRandom;

        private boolean isAlternate = false;

		private boolean isRoot = false;

		private AdaSplitNode mainlineNode = null; //null by default unless there is an attachment point

		private AdaSplitNode parent = null;

		@Override
		public void setParent(AdaSplitNode parent) {
			this.parent = parent;

		}

		@Override
		public AdaSplitNode getParent() {
			return this.parent;
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
        }

        public AdaLearningNode(double[] initialClassObservations, boolean isAlternate) {
            super(initialClassObservations);
            this.classifierRandom = new Random(this.randomSeed);
            this.setAlternate(isAlternate);
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
        public void killTreeChilds(HAT ht) {
        }

        @Override
        public void learnFromInstance(Instance inst, HAT ht, SplitNode parent, int parentBranch) {
//
//        	if(!this.isAlternate()){
//        		System.err.println(numInstances);
//        		// this shows mainline learning nodes stop learning once drift occurs
//        	}

            int trueClass = (int) inst.classValue();
            //New option vore
            int k = MiscUtils.poisson(1.0, this.classifierRandom);
            Instance weightedInst = inst.copy();
            if (k > 0) {
            	if(ht.leafWeighting.isSet()) {
            		weightedInst.setWeight(inst.weight() * k);
            	}
            }
            //if (k > 0 && this.isAlternate()) {
            	// use weighted instance if necessary for asymmetric alternate weighting
                //weightedInst.setWeight(inst.weight() * k);
                // this wasn't in the paper
            //}
            //Compute ClassPrediction using filterInstanceToLeaf
            int ClassPrediction = Utils.maxIndex(this.getClassVotes(inst, ht));

            boolean blCorrect = (trueClass == ClassPrediction);

            if (this.estimationErrorWeight == null) {
                this.estimationErrorWeight = new ADWIN();
            }
            this.ErrorChange = this.estimationErrorWeight.setInput(blCorrect == true ? 0.0 : 1.0);

            
            double oldError = this.getErrorEstimation();
            if (this.ErrorChange == true && oldError > this.getErrorEstimation()) {
                this.ErrorChange = false;
            }

            //Update statistics
            learnFromInstance(weightedInst, ht);	//inst

            //Check for split condition
            double weightSeen = this.getWeightSeen();
            if (weightSeen
                    - this.getWeightSeenAtLastSplitEvaluation() >= ht.gracePeriodOption.getValue()) {
                ht.attemptToSplit(this, this.getParent(),
                        parentBranch);
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
        public double[] getClassVotes(Instance inst, VFDT ht) {
            double[] dist;
            int predictionOption = ((HAT) ht).leafpredictionOption.getChosenIndex();
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
                List<FoundNode> foundNodes, boolean updateSplitterCounts) {

            foundNodes.add(new FoundNode(this, splitparent, parentBranch));
        }

		@Override
		public boolean isRoot() {
			return this.isRoot ;
		}

		@Override
		public void setRoot(boolean isRoot) {
			this.isRoot = isRoot;

		}
		@Override
		public void setMainlineNode(AdaSplitNode mainlineNode) {
			this.mainlineNode  = mainlineNode;
		}

		@Override
		public AdaSplitNode getMainlineNode() {
			return this.mainlineNode;
		}

		@Override
		public void setAlternateStatusForSubtreeNodes(boolean isAlternate) {
			this.setAlternate(isAlternate);
		}

    }

    protected int alternateTrees;

    protected int prunedAlternateTrees;

    protected int switchedAlternateTrees;


    protected LearningNode newLearningNode(boolean isAlternate) {
        return new AdaLearningNode(new double[0], isAlternate);
    }
    protected LearningNode newLearningNode(double[] initialClassObservations, boolean isAlternate) {
        return new AdaLearningNode(initialClassObservations, isAlternate);
    }

    @Override
    protected LearningNode newLearningNode(double[] initialClassObservations) {
        // IDEA: to choose different learning nodes depending on predictionOption
        return new AdaLearningNode(initialClassObservations);
    }

    protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
            double[] classObservations, int size, boolean isAlternate) {
    	return new AdaSplitNode(splitTest, classObservations, size, isAlternate);
    }

	protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
            double[] classObservations, boolean isAlternate) {
    	return new AdaSplitNode(splitTest, classObservations, isAlternate);
    	}

   @Override
    protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
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
            this.treeRoot = newLearningNode(false); // root cannot be alternate
            ((NewNode) this.treeRoot).setRoot(true);
            ((NewNode) this.treeRoot).setParent(null);
            ((NewNode) this.treeRoot).setMainlineNode(null);

            this.activeLeafNodeCount = 1;
        }
        ((NewNode) this.treeRoot).learnFromInstance(inst, this, null, -1);
    }

    //New for options vote
    public FoundNode[] filterInstanceToLeaves(Instance inst,
            SplitNode parent, int parentBranch, boolean updateSplitterCounts) {
        List<FoundNode> nodes = new LinkedList<FoundNode>();
        ((NewNode) this.treeRoot).filterInstanceToLeaves(inst, parent, parentBranch, nodes,
                updateSplitterCounts);
        return nodes.toArray(new FoundNode[nodes.size()]);
    }

    @Override
	protected void attemptToSplit(ActiveLearningNode node, SplitNode parent,
            int parentIndex) {
    	
    	
        if (!node.observedClassDistributionIsPure()) {
        	
			node.addToSplitAttempts(1); // even if we don't actually attempt to split, we've computed infogains
            SplitCriterion splitCriterion = (SplitCriterion) getPreparedClassOption(this.splitCriterionOption);
            AttributeSplitSuggestion[] bestSplitSuggestions = node.getBestSplitSuggestions(splitCriterion, this);
            

            if (decideToSplitAndPrune(node, parent, parentIndex, bestSplitSuggestions, splitCriterion)) {
            	splitCount++;
                AttributeSplitSuggestion splitDecision = bestSplitSuggestions[bestSplitSuggestions.length - 1];
                if (splitDecision.splitTest == null) {
                    // preprune - null wins
                    deactivateLearningNode(node, ((NewNode)node).getParent(), parentIndex);
                } else {
                    SplitNode newSplit = newSplitNode(splitDecision.splitTest,
                            node.getObservedClassDistribution(),splitDecision.numSplits(), ((NewNode)(node)).isAlternate());
                    for (int i = 0; i < splitDecision.numSplits(); i++) {
                        Node newChild = newLearningNode(splitDecision.resultingClassDistributionFromSplit(i), ((NewNode)newSplit).isAlternate());
                        ((NewNode)newChild).setParent((AdaSplitNode)newSplit);

                    	newChild.usedNominalAttributes = new ArrayList<Integer>(node.usedNominalAttributes); //deep copy
                    	newChild.usedNominalAttributes.add(splitDecision.splitTest.getAttsTestDependsOn()[0]);

                        newSplit.setChild(i, newChild);
                    }
                    this.activeLeafNodeCount--;
                    this.decisionNodeCount++;
                    this.activeLeafNodeCount += splitDecision.numSplits();
                    
//                    if (parent == null) {
//                        this.treeRoot = newSplit;
//                        ((NewNode)newSplit).setRoot(true);
//                        ((NewNode)newSplit).setParent(null);
//                    	((NewNode)newSplit).setMainlineNode(null);
//                		((NewNode)newSplit).setAlternate(false); 
//                    	((NewNode)newSplit).setAlternateStatusForSubtreeNodes(false);
//                        this.treeRoot = newSplit;
//
//                    } else {
//                        //parent.setChild(parentIndex, newSplit);
//                        
//                		((NewNode)newSplit).setParent(((NewNode)node).getParent());                    	
//                    	((NewNode)newSplit).setMainlineNode(null);
//                		((NewNode)newSplit).setAlternate(false); 
//                    	((NewNode)newSplit).setAlternateStatusForSubtreeNodes(false);
//                    	
//                		((NewNode)node).getParent().setChild(parentIndex, newSplit);
//                        
//                        
//                    }
//                }
                    
                    if(rootLevelBug.isSet() && ((NewNode)node).getParent()==null) { // root level split reassigns to root, even if an alternate was split
                    	if (((NewNode)node).isRoot() ) {
                    		((NewNode)newSplit).setRoot(true);
                    		((NewNode)newSplit).setParent(null);
                        	((NewNode)newSplit).setMainlineNode(null);
                    		((NewNode)newSplit).setAlternate(false); 
                        	((NewNode)newSplit).setAlternateStatusForSubtreeNodes(false);

                    		this.treeRoot = newSplit;
                    	}
                    } else if(parentBug.isSet() 
                    		&& ((NewNode)node).getParent()!=null 
                    		&& ((NewNode)node).getMainlineNode() != null
                    		&& ((NewNode)node).isAlternate()
                    		){
                    	// splitting an alternate causes parent to replace it's mainline child with the alternate
                		((NewNode)newSplit).setParent(((NewNode)node).getParent());                    	
                    	((NewNode)newSplit).setMainlineNode(null);
                		((NewNode)newSplit).setAlternate(false); 
                    	((NewNode)newSplit).setAlternateStatusForSubtreeNodes(false);
                    	
                		((NewNode)node).getParent().setChild(parentIndex, newSplit);
                    } else {
                    	if (((NewNode)node).isRoot()) {
                    		((NewNode)newSplit).setRoot(true);
                    		((NewNode)newSplit).setParent(null);
                        	((NewNode)newSplit).setMainlineNode(null);
                    		
                    		this.treeRoot = newSplit;
                    	}
                    	else if (((NewNode)node).getMainlineNode() != null) { // if the node happens to be an alternate with an attachment point(i.e. at the top of the subtree)
                        	
                    		((NewNode)newSplit).setMainlineNode(((NewNode)node).getMainlineNode());
                    		((NewNode)newSplit).setParent(((NewNode)node).getParent());
                    		
                    		((NewNode)node).getMainlineNode().alternateTree = newSplit;
                    		

                    	}
                    	else { //if the node is neither root nor an alternate top level, it must have a split parent

                    		((NewNode)node).getParent().setChild(parentIndex, newSplit);
                    		((NewNode)newSplit).setParent(((NewNode)node).getParent());
                    	}
                    	/*if(allowAlternatesofAlternatesOption.isSet()) {
                    		// where an alternate of an alternate is being split, it must have the correct attachment point backreference
                    		if (((NewNode)node).getMainlineNode() != null) {
                    			((NewNode)newSplit).setMainlineNode(((NewNode)node).getMainlineNode());
                    		}
                    	}*/
                }
                }
                // manage memory
                enforceTrackerLimit();
            }
        }
    }

    
    public static class AdaInactiveLearningNode extends InactiveLearningNode implements NewNode {

        public AdaInactiveLearningNode(double[] initialClassObservations, 
        		boolean isAlternate, boolean isRoot, AdaSplitNode mainLineNode, AdaSplitNode parent) {
			super(initialClassObservations);
			this.setRoot(isRoot);
			this.setAlternate(isAlternate);
			this.setMainlineNode(mainLineNode);
			this.setParent(parent);

		}
        private boolean isAlternate = false;

        private boolean isRoot = false;

		private AdaSplitNode mainlineNode = null; //null by default unless there is an attachment point

		private AdaSplitNode parent = null;


		private static final long serialVersionUID = 1L;


        @Override
        public void learnFromInstance(Instance inst, VFDT ht) {
        	this.nodeTime++;
            this.observedClassDistribution.addToValue((int) inst.classValue(),
                    inst.weight());
        }


		@Override
		public int numberLeaves() {
			// TODO Auto-generated method stub
			return 0;
		}


		@Override
		public void setAlternateStatusForSubtreeNodes(boolean isAlternate) {
			// TODO Auto-generated method stub
			
		}


		@Override
		public double getErrorEstimation() {
			// TODO Auto-generated method stub
			return 0;
		}


		@Override
		public double getErrorWidth() {
			// TODO Auto-generated method stub
			return 0;
		}


		@Override
		public boolean isNullError() {
			// TODO Auto-generated method stub
			return false;
		}


		@Override
		public void killTreeChilds(HAT ht) {
			// TODO Auto-generated method stub
			
		}


		@Override
		public void learnFromInstance(Instance inst, HAT ht, SplitNode parent, int parentBranch) {
			// TODO Auto-generated method stub
			
		}


		@Override
		public void filterInstanceToLeaves(Instance inst, SplitNode myparent, int parentBranch,
				List<FoundNode> foundNodes, boolean updateSplitterCounts) {
			// TODO Auto-generated method stub
			
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
		public void setMainlineNode(AdaSplitNode mainLineNode) {
			this.mainlineNode = mainLineNode;
		}


		@Override
		public AdaSplitNode getMainlineNode() {
			return this.mainlineNode;
		}


		@Override
		public void setParent(AdaSplitNode parent) {
			this.parent = parent;
		}


		@Override
		public AdaSplitNode getParent() {
			return this.parent;
		}
    }
    
    @Override
    protected void activateLearningNode(InactiveLearningNode toActivate,
            SplitNode parent, int parentBranch) {
    	// Note that deactivated nodes lose their ADWINs etc. 
    	//This is not in the spec so there is room for interpretation.
    	LearningNode newLeaf = newLearningNode(toActivate.getObservedClassDistribution(), 
    			((NewNode)toActivate).isAlternate());
    	((NewNode)(newLeaf)).setRoot(((NewNode)toActivate).isRoot());
    	((NewNode)(newLeaf)).setParent(((NewNode)toActivate).getParent());    	
    	((NewNode)(newLeaf)).setMainlineNode(((NewNode)toActivate).getMainlineNode()); 	
    	
        // if node is alternate and is top of subtree reattach to mainline
        if(((NewNode)(newLeaf)).isAlternate() && ((NewNode)(newLeaf)).getMainlineNode() != null) {
        	((NewNode)(newLeaf)).getMainlineNode().alternateTree = newLeaf; // attach newLeaf to its mainline
        }
        // if node is root then set to treeRoot
        else if (parent == null && ((NewNode)(newLeaf)).isRoot() && !((NewNode)(newLeaf)).isAlternate()) {
            this.treeRoot = newLeaf;
        } else {
            parent.setChild(parentBranch, newLeaf);
        }
        this.activeLeafNodeCount--;
        this.inactiveLeafNodeCount++;
        if ( ((NewNode)(newLeaf)).isAlternate() 
        		&& ((NewNode)(newLeaf)).getParent() == null){
        	System.err.println("Node activation causing perf diff?");
        }
    }

    @Override
    protected void deactivateLearningNode(ActiveLearningNode toDeactivate,
            SplitNode parent, int parentBranch) {
    	// Note that deactivated nodes lose their ADWINs etc. 
    	//This is not in the spec so there is room for interpretation.
        AdaInactiveLearningNode newLeaf = new AdaInactiveLearningNode(
        		toDeactivate.getObservedClassDistribution(),
        		((NewNode)(toDeactivate)).isAlternate(), 
        		((NewNode)(toDeactivate)).isRoot(),
        		((NewNode)(toDeactivate)).getMainlineNode(),
        		((NewNode)(toDeactivate)).getParent()
        		);
        // if node is alternate and is top of subtree reattach to mainline
        if(newLeaf.isAlternate() && newLeaf.getMainlineNode() != null) {
        	newLeaf.getMainlineNode().alternateTree = newLeaf; // attach newLeaf to its mainline
        }
        // if node is root then set to treeRoot
        else if (parent == null && newLeaf.isRoot() && !newLeaf.isAlternate()) {
            this.treeRoot = newLeaf;
        } else {
        	
            parent.setChild(parentBranch, newLeaf);
        }
        this.activeLeafNodeCount--;
        this.inactiveLeafNodeCount++;
    }
    
    
    
    
    @Override
    public double[] getVotesForInstance(Instance inst) {
    	if (this.treeRoot != null) {
    		numInstances++;
    		FoundNode[] foundNodes = filterInstanceToLeaves(inst,
    				null, -1, false);
    		DoubleVector result = new DoubleVector();
    		int predictionPaths = 0;
    		for (FoundNode foundNode : foundNodes) {
                //if (foundNode.parentBranch != -999) {

    			Node leafNode = foundNode.node;

    			if (leafNode == null) {
    				leafNode = foundNode.parent;
    			}

				// count only votes from non-alternates... alternates shouldn't be voting
				// also, according to the -999 bug, single-leaf alternates won't vote, but others will.

    			if( !(((NewNode)leafNode).isAlternate()) || alternateVoterOption.isSet() ){

    				if (alternateVoterOption.isSet() 
    						&& singleLeafAlternateDoesntVote.isSet()
    						&& ((NewNode)leafNode).getMainlineNode()!=null // if null it is single level alternate
    						&& ((NewNode)leafNode).isAlternate()
    						) {
    				}
    				else {			
    					double[] dist = leafNode.getClassVotes(inst, this);
    					result.addValues(dist);
    				}
    			}
    			//predictionPaths++;
    		}
    		return result.getArrayRef();

    	}
    	return new double[0];
    }
        
    
}