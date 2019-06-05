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
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.core.DoubleVector;
import moa.core.MiscUtils;
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
public class HATErrorRedist extends HAT {

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

    public interface HATErrRedistNode extends NewNode {

		public int getLevel();

		public void setLevel(int level);

    }

    public static class HATErrLearningNode extends AdaLearningNode implements HATErrRedistNode {

		public HATErrLearningNode(double[] initialClassObservations, boolean isAlternate) {
			super(initialClassObservations, isAlternate);
			if(this.getParent()==null) {
				this.setLevel(0);
			} else {
				this.setLevel(((HATErrRedistNode)this.getParent()).getLevel()+1);
			}
		}

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private int level;

		@Override
		public int getLevel() {
			return level;
		}

		@Override
		public void setLevel(int level) {
			this.level = level;
		}
    
    }
    
    
    public static class HATErrSplitNode extends AdaSplitNode implements HATErrRedistNode {


		public HATErrSplitNode(InstanceConditionalTest splitTest, double[] classObservations, int size,
				boolean isAlternate) {
			super(splitTest, classObservations, size, isAlternate);
			if(this.getParent()==null) {
				this.setLevel(0);
			} else {
				this.setLevel(((HATErrRedistNode)this.getParent()).getLevel()+1);
			}

		}

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private int level;
		
		@Override
        public Instance computeErrorChangeAndWeightInst(Instance inst, HAT ht, SplitNode parent, int parentBranch) {
			
//          System.out.println("Main Tree is of depth " + ht.treeRoot.subtreeDepth());

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

          if (!blCorrect){
              if (k > 0) {
                  weightedInst.setWeight(inst.weight() * k); // a domino effect of increasing the weight down to the leaf
                  // subtree boosting
              }
          }
          
          if (this.estimationErrorWeight == null) {
              this.estimationErrorWeight = new ADWIN();
          }
          if (leaf != null) {
          	//simple strategy
          	//this.ErrorChange = this.estimationErrorWeight.setInput(blCorrect == true ?
          		//	0.0 : 1.0*1/(((NewNode)leaf).getLevel() - this.getLevel() + 1.0));
          	// inverted proportions, except for at leaves
          	//this.ErrorChange = this.estimationErrorWeight.setInput(blCorrect == true ?
          		//	0.0 : 1.0/(leaf.observedClassDistribution.sumOfAbsoluteValues() 
          			//		/ this.observedClassDistribution.sumOfAbsoluteValues()));
          	//proportional
          	this.ErrorChange = this.estimationErrorWeight.setInput(blCorrect == true ?
          			0.0 : 1.0*(leaf.observedClassDistribution.sumOfAbsoluteValues() 
          					/ this.observedClassDistribution.sumOfAbsoluteValues()));
	
          }
          
          return weightedInst;
        }

		@Override
		public int getLevel() {
			return level;
		}

		@Override
		public void setLevel(int level) {
			this.level = level;
		}
    }

}