/*
 *    HoeffdingAdaptiveTree.java
 *    Copyright (C) 2008 University of Waikato, Hamilton, New Zealand
 *    @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
 * CVFDT.java
 * Chaitanya Manapragada
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
 * Based on HoeffdingAdaptiveTree by Albert Bifet and HoeffdingTree by Richard Kirkby
 *
 */
package moa.classifiers.trees;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.util.Pair;

import moa.classifiers.bayes.NaiveBayes;
import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.driftdetection.ADWIN;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.classifiers.trees.HoeffdingTree.SplitNode;
import moa.core.AutoExpandVector;
import moa.core.DoubleVector;
import moa.core.MiscUtils;
import moa.core.Utils;
import com.yahoo.labs.samoa.instances.Instance;
import com.github.javacliparser.IntOption;
import com.google.common.collect.EvictingQueue;

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
 * @author
 * @version $Revision: 7 $
 */

/*
 * What do we need to do to change this to CVFDT?
 * First, we need to get the tree substitutions out. Then we get the change detectors out. Get the weighting out. Then we should perform similarly to VFDT.
 * Next, add counters at nodes.
 * Then use those counters to regularly assess splits.
 * Add examples to window. I won't be testing under conditions where I have to store examples on disk. That's one change to the algorithm.
 *
 * Parent-child state in nodes: I've currently got the parent, but not the parentBranch within the node. Changing that may require a full rewrite of VFDT.
 * I'll use the existing framework and change things later.
 *
 */


public class CVFDT extends HoeffdingTree {

    private static final long serialVersionUID = 1L;

    private static long numInstances = 0;

    private static long nodeIDGenerator = 0; // nodeIDs start from 0 (incremented with post ++ operator)

    private EvictingQueue<Pair<Instance, Long>> window = null;

	private PrintWriter writer = null;

    public IntOption windowSize = new IntOption("windowSize", 'W',
            "Maximum moving window size", 20000, 0,
            Integer.MAX_VALUE);

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

    public interface AdaNode {

        // Change for adwin
        //public boolean getErrorChange();
        public int numberLeaves();

		void learnFromInstance(Instance inst, CVFDT ht, SplitNode parent, int parentBranch,
				AutoExpandVector<Long> reachedLeafIDs);

		void setAlternateStatusForSubtreeNodes(boolean isAlternate);

		public double getErrorEstimation();

        public double getErrorWidth();

        public boolean isNullError();

        public void killTreeChilds(CVFDT ht);

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

		public long getUniqueID();

        public void setUniqueID(long uniqueID);

		public void forgetInstance(Instance inst, CVFDT ht, AdaSplitNode adaSplitNode, int childBranch, long maxNodeID);
    }


    public static class AdaSplitNode extends SplitNode implements AdaNode {

        private static final long serialVersionUID = 1L;

        private long uID;

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

		protected AutoExpandVector<AttributeClassObserver> attributeObservers;

		protected boolean createdFromInitializedLearningNode = false;

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
                    numLeaves += ((AdaNode) child).numberLeaves();
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

        // SplitNodes can have alternative trees, but LearningNodes can't
        // LearningNodes can split, but SplitNodes can't
        // Parent nodes are allways SplitNodes
        @Override
		public void learnFromInstance(Instance inst, CVFDT ht, SplitNode parent, int parentBranch, AutoExpandVector<Long> reachedLeafIDs) {

            //System.out.println("Main Tree is of depth " + ht.treeRoot.subtreeDepth());

            // DRY... for now this code is repeated...
            // Updates statistics in split nodes also
        	assert (this.createdFromInitializedLearningNode = true);


        	if(numInstances > 200510 && numInstances < 200513){
        		System.out.println(this.observedClassDistribution.toString()+ "-----------");
        	}

            this.observedClassDistribution.addToValue((int) inst.classValue(), inst.weight());

            if(numInstances > 200510 && numInstances < 200513){
        		System.out.println(this.observedClassDistribution.toString()+ "-----------");
        	}
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

            int childBranch = this.instanceChildIndex(inst);
            Node child = this.getChild(childBranch);
            if (child != null) {
                ((AdaNode) child).learnFromInstance(inst, ht, this, childBranch, reachedLeafIDs);
            }

            // Note that all this is doing is filtering down the instance to a leaf
            // Q: so what's the point of a dedicated filter-to-leaves function?
            // A: it is only used for voting
            // This is where we must update statistics for split nodes.
            // First let's create statistics fields for split nodes similarly to what learning nodes have
            // Note that once we have a moving window, implemented as a queue, we can learn popped instances with weight -1... to forget
            // Note also that once a learning node is replaced with a split node... we must transfer the statistics smoothly
            // Then learning can proceed...

        }
		@Override
        public void forgetInstance(Instance inst, CVFDT ht, AdaSplitNode parent, int parentBranch, long maxNodeID) {

            //System.out.println("Main Tree is of depth " + ht.treeRoot.subtreeDepth());

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


            int childBranch = this.instanceChildIndex(inst);
            Node child = this.getChild(childBranch);
            if (child != null && ((AdaNode)child).getUniqueID() <= maxNodeID) {
                ((AdaNode) child).forgetInstance(inst, ht, this, childBranch, maxNodeID);
            }

            // Note that all this is doing is filtering down the instance to a leaf
            // Q: so what's the point of a dedicated filter-to-leaves function?
            // A: it is only used for voting
            // This is where we must update statistics for split nodes.
            // First let's create statistics fields for split nodes similarly to what learning nodes have
            // Note that once we have a moving window, implemented as a queue, we can learn popped instances with weight -1... to forget
            // Note also that once a learning node is replaced with a split node... we must transfer the statistics smoothly
            // Then learning can proceed...

        }

		@Override
        public void setAlternateStatusForSubtreeNodes(boolean isAlternate) {

          this.setAlternate(isAlternate);

          for (Node child : this.children) {
            if (child != null) {
              ((AdaNode)child).setAlternateStatusForSubtreeNodes(isAlternate);
            }
          }
        }



        @Override
        public void killTreeChilds(CVFDT ht) {
            for (Node child : this.children) {
                if (child != null) {
                    //Delete alternate tree if it exists
                    if (child instanceof AdaSplitNode && ((AdaSplitNode) child).alternateTree != null) {
                        ((AdaNode) ((AdaSplitNode) child).alternateTree).killTreeChilds(ht);
                        ht.prunedAlternateTrees++;
                    }
                    //Recursive delete of SplitNodes
                    if (child instanceof AdaSplitNode) {
                        ((AdaNode) child).killTreeChilds(ht);
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
                    ((AdaNode) child).filterInstanceToLeaves(inst, this, childIndex,
                            foundNodes, updateSplitterCounts);
                    // this will usually just take you down one path until you hit a learning node. Unless you are overextending
                    // your tree without pruning
                } else {
                    foundNodes.add(new FoundNode(null, this, childIndex));
                    // Only killTreeChilds would create null child nodes
                }
            }
            if (this.alternateTree != null) {
                ((AdaNode) this.alternateTree).filterInstanceToLeaves(inst, this, -999, foundNodes, updateSplitterCounts);

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

		@Override
		public long getUniqueID() {

			return this.uID;
		}

		@Override
		public void setUniqueID(long uniqueID) {
			this.uID = uniqueID;
		}


    }

    public static class AdaLearningNode extends LearningNodeNBAdaptive implements AdaNode {

        private static final long serialVersionUID = 1L;

        protected ADWIN estimationErrorWeight;

        public boolean ErrorChange = false;

        protected int randomSeed = 1;

        protected Random classifierRandom;

        private boolean isAlternate = false;

		private boolean isRoot = false;

		private AdaSplitNode mainlineNode = null; //null by default unless there is an attachment point

		private AdaSplitNode parent = null;

		private long uID;

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
            this.uID = nodeIDGenerator++;
        }

        public AdaLearningNode(double[] initialClassObservations, boolean isAlternate) {
            super(initialClassObservations);
            this.classifierRandom = new Random(this.randomSeed);
            this.setAlternate(isAlternate);
            this.uID = nodeIDGenerator++;
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
        public void killTreeChilds(CVFDT ht) {
        }

        @Override
        public void learnFromInstance(Instance inst, CVFDT ht, SplitNode parent, int parentBranch, AutoExpandVector<Long> reachedLeafIDs) {

            Instance weightedInst = inst.copy();

        	if(numInstances > 200510 && numInstances < 200513){
        		System.out.println(this.observedClassDistribution.toString()+ "+++++++++++");
        	}

//Update statistics
            learnFromInstance(weightedInst, ht);
            //this is where we call VFDT's learnFromInstance which then calls a super that updates statistics... but only for learning nodes at this time
            if(numInstances > 200510 && numInstances < 200513){
        		System.out.println(this.observedClassDistribution.toString()+ "+++++++++++");
        	}



            //Check for Split condition
            double weightSeen = this.getWeightSeen();
            if (weightSeen
                    - this.getWeightSeenAtLastSplitEvaluation() >= ht.gracePeriodOption.getValue()) {
                ht.attemptToSplit(this, this.getParent(), parentBranch);

                this.setWeightSeenAtLastSplitEvaluation(weightSeen);
            }

            reachedLeafIDs.add(new Long(this.getUniqueID()));

        }

        @Override
        public double[] getClassVotes(Instance inst, HoeffdingTree ht) {
            double[] dist;
            int predictionOption = ((CVFDT) ht).leafpredictionOption.getChosenIndex();
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

		@Override
		public long getUniqueID() {

			return this.uID;
		}

		@Override
		public void setUniqueID(long uniqueID) {
			this.uID = uniqueID;
		}

		@Override
		public void forgetInstance(Instance inst, CVFDT ht, AdaSplitNode adaSplitNode, int childBranch,
				long maxNodeID) {
			//if(numInstances < ht.windowSize.getValue() + 10000){

			//}else{
			//inst.setWeight(-1.0);

			super.learnFromInstance(inst, ht);
			//}

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

    protected AdaSplitNode newSplitNode(InstanceConditionalTest splitTest,
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


    	// If treeRoot is null, create a new tree, rooted with a learning node.
        if (this.treeRoot == null) {
            this.treeRoot = newLearningNode(false); // root cannot be alternate
            ((AdaNode) this.treeRoot).setRoot(true);
            ((AdaNode) this.treeRoot).setParent(null);
            this.activeLeafNodeCount = 1;
        }

        // If you have no window, create one.
    	if(window == null){
    		window = EvictingQueue.create(windowSize.getValue());
    	}

    	// Forget an instance. The window stores along with each instance the maximum node reached. So look at the head of the queue and forget the instance there.
    	Instance forgetInst;
        if(window.remainingCapacity() == 0){
        	//forgetInst = window.peek().getFirst();
        	//forgetInst.setWeight(-0.95);
            //((AdaNode) this.treeRoot).forgetInstance(forgetInst, this, null, -1, window.peek().getSecond());
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
    	//System.out.println(this.measureTreeDepth());

        numInstances++;

		//System.out.println(numInstances);


    	if(numInstances > 200510 && numInstances < 200513 && numInstances % 1 == 0){
    		StringBuilder out = new StringBuilder();
    		this.treeRoot.describeSubtree(this, out, 8);
    		System.out.println("===== " + numInstances + " =======");
    		System.out.print(out);
    		writer.println(numInstances);
    		writer.print(out);
    	}
    	if(numInstances > 300000){
    		writer.close();
    	}


//        for(int i = 0; i < reachedNodeIDs.size(); i ++){ System.out.print(reachedNodeIDs.get(i) + " ");}
//        System.out.println();

/*
         1. Implement a moving, forgetting window
         	(a) Node $ observedClassDistribution is the thing that needs to updated
         	(b) Give each node a monotonically increasing ID
         	(c) To delete an example, filter it down the tree and delete it from all the nodes with lower ID's than the highest it had hit
         		(i) A creative way of doing this might be to use weight -1
         		(ii) So you learn from two instances each training cycle, from either edge of the window
         		(iii) One is weighted -1 and the other is weighted 1 (or whatever it was originally weighted)
         		(iv) Each old instance will be mapped to a highest ID number. Anything node with ID less than or equal to that number is allowed to learn.

         2. Build alternates as necessary: Once the moving window is well implemented... this shouldn't be hard
         	(a) Periodically check each node for the best available splits now
         	(b) If alternate splits pass the requisite conditions... start building alternate trees
         	(c) When accuracy is higher... substitute. Remember that the original CVFDT uses a 9000-1000 split for training/testing alternates.
         	(d) Original CVFDT does allow you to change window size during a run... manually...
*/
    }

    //New for options vote
    public FoundNode[] filterInstanceToLeaves(Instance inst,
            SplitNode parent, int parentBranch, boolean updateSplitterCounts) {
        List<FoundNode> nodes = new LinkedList<FoundNode>();
        ((AdaNode) this.treeRoot).filterInstanceToLeaves(inst, parent, parentBranch, nodes,
                updateSplitterCounts);
        return nodes.toArray(new FoundNode[nodes.size()]);
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
                    deactivateLearningNode(node, ((AdaNode)node).getParent(), parentIndex);
                } else {
                    AdaSplitNode newSplit = newSplitNode(splitDecision.splitTest,
                            node.getObservedClassDistribution(),splitDecision.numSplits(), ((AdaNode)(node)).isAlternate());

                    ((AdaNode)newSplit).setUniqueID(((AdaNode)node).getUniqueID());
                    //Ensure that the split node's ID is the same as it's ID as a leaf

                    // Copy statistics from the learning node being replaced
                    newSplit.createdFromInitializedLearningNode = node.isInitialized;
                    newSplit.observedClassDistribution = node.observedClassDistribution; // copy the class distribution
                    newSplit.attributeObservers = node.attributeObservers; // copy the attribute observers

                    for (int i = 0; i < splitDecision.numSplits(); i++) {
                        Node newChild = newLearningNode(splitDecision.resultingClassDistributionFromSplit(i), ((AdaNode)newSplit).isAlternate());
                        ((AdaNode)newChild).setParent(newSplit);
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
                    else if (((AdaNode)node).getMainlineNode() != null) { // if the node happens to have a mainline attachment, i.e it is alternate
                    	((AdaNode)newSplit).setParent(((AdaNode)node).getParent());
                    	((AdaNode)node).getMainlineNode().alternateTree = newSplit;
                    }
                    else { //if the node is neither root nor an alternate, it must have a mainline split parent
                    	((AdaNode)newSplit).setParent(((AdaNode)node).getParent());
                    	((AdaNode)node).getParent().setChild(parentIndex, newSplit);
                    }

                    // Now transfer all the statistics from the learning node being replaced

                }
                // manage memory
                enforceTrackerLimit();
            }
        }
    }

    @Override
    public double[] getVotesForInstance(Instance inst) {
    	if (this.treeRoot != null) {
    		FoundNode[] foundNodes = filterInstanceToLeaves(inst,
    				null, -1, false);
    		DoubleVector result = new DoubleVector();
    		int predictionPaths = 0;
    		for (FoundNode foundNode : foundNodes) {

    					Node leafNode = foundNode.node;
    					if (leafNode == null) {
    						leafNode = foundNode.parent;
    					}
    					double[] dist = leafNode.getClassVotes(inst, this);

    					if(!((AdaNode)leafNode).isAlternate()){

    						result.addValues(dist);

    					}
    					predictionPaths++;

    					return result.getArrayRef();
    		}

    	}
    	return new double[0];
    }
}
