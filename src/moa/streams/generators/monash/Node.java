package moa.streams.generators.monash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.random.RandomDataGenerator;

public class Node {

	private static int nAttr;
	private static int nValPerAttr;
	private static int nClasses;

	private static double precision;

	private List<Node> children;
	private List<Integer> availableAttr;
	private List<Double> nodePY;

	private static RandomDataGenerator r;

	int level;

	void setPY (List<Double> givenPY) {
		nodePY = givenPY;
	}

    void setLevel(int lev) {
    	level = lev;
    }

    void split() {

        // recursion termination
        if (availableAttr.size() == 0){ return;} // no attributes left to split on
        for (Double p : nodePY){
            if (1.0 - p < precision){return;}
        } // low enough entropy achieved- one class dominates


        // randomly choose an attribute from those remaining to split on
        // attributes numbered from 0
        int attrRemain = availableAttr.size();
        int chosenAttrIndex=-1;
        try{
            chosenAttrIndex = r.nextInt(0, attrRemain-1);

        }catch(Exception e){
            System.err.println("Remaining " + attrRemain + "   \n");
            System.err.println("Chosen " + chosenAttrIndex + "   \n");
        }
        int chosenAttr = availableAttr.get(chosenAttrIndex);
        availableAttr.remove(chosenAttrIndex);

        System.out.println( "splitting on attribute " + chosenAttr + "\n");

        // This matrix will be filled greedily so that row totals equal class probabilities
        // And column totals equal edge probabilities
        // Then when each value is divided by the corresponding edge probability(column total)
        // one gets the new pygx conditioned on the first attribute
        double A[][] = new double[nClasses][nValPerAttr];
        for (int i = 0; i < nClasses; i++){
            for (int j = 0; j < nValPerAttr; j++){
                A[i][j] = 0.0; // initialise
            }
        }

        // row totals: nodePY sorted in descending order
        // deep copy again
        List<Double> rowTotals = new ArrayList<Double>();
        for(Double p : nodePY) {rowTotals.add(new Double(p));}
        Collections.sort(rowTotals); Collections.reverse(rowTotals);


        // column totals: edgeweights sorted in descending order
        // generate edgeweights for this split
        double[] px1d = new double[nValPerAttr];
        DriftGenerator.generateRandomPx1D(px1d, r, true);
        List<Double> edgeWeights = new ArrayList<Double>();
        for (double p : px1d) {edgeWeights.add(new Double(p));}


        List<Double> colTotalsAscending = new ArrayList<Double>();
        for(Double p : edgeWeights) {colTotalsAscending.add(new Double(p));} //deep copy from edgeweights
        Collections.sort(colTotalsAscending);
        List<Double> colTotals = new ArrayList<Double>();
        for (double p : colTotalsAscending) {colTotals.add(new Double(p));} // deep copy from colTotalsAscending
        Collections.reverse(colTotals);


        // pack the matrix using a greedy approach
        int i = 0, j = 0;
        while (i < rowTotals.size() && j < colTotals.size()) {
                A[i][j] = rowTotals.get(i) < colTotals.get(j) ? rowTotals.get(i) : colTotals.get(j);
                rowTotals.set(i, rowTotals.get(i) - A[i][j]);
                colTotals.set(j, colTotals.get(j) - A[i][j]);

                if (rowTotals.get(i) == 0.0) {i++;}
                if (colTotals.get(j) == 0.0) {j++;}
        }
        // divide by the original column totals (the copied ones are now zero)
        for (int c = 0; c < nClasses; c++){
            for (int k = 0; k < nValPerAttr; k++){
                A[c][k] /= colTotalsAscending.get(k);
            }
        }
        // convert each column into a list- this is a PY for a child node
        // first create empty py vectors- one per attribute value
        ArrayList<ArrayList<Double>> py_updated = new ArrayList<ArrayList<Double>>();
        for (int k = 0; k < nValPerAttr; k++){
            ArrayList<Double> py_ind = new ArrayList<Double>();
            py_updated.add(py_ind);
        }

        // then traverse the matrix, adding the column elements to the corresponding py
        for (int c = 0; c < nClasses; c++){
            for (int k = 0; k < nValPerAttr; k++){
                py_updated.get(k).add(new Double(A[c][k]));
            }
        }

        // create child nodes with the newly created py's
        children = new ArrayList<Node>();
        for (ArrayList<Double> child_py : py_updated){
            children.add(new Node(child_py, level+1, availableAttr));
            // each Node references the ArrayList<Double> objects in py_updated
            // this is fine unless we need to evolve this objects differently
            // for the two references for different uses
        }

        // recursively split on each child node


        for (Node child : children){
            child.split();
        }

    }

    void printNode() {

    }

    Node(int n_attr, int n_val_per_attr, int n_classes, double prec, RandomDataGenerator input_r) {
    	nAttr = n_attr;
    	nValPerAttr = n_val_per_attr;
    	nClasses = n_classes;
    	precision = prec;
    	r = input_r;

		double[] py = new double[nClasses];

    	DriftGenerator.generateRandomPy1D(py, r, true); //generate random py at this node

    	ArrayList<Double> pyList = new ArrayList<Double>();
    	for(double p : py) {pyList.add(new Double(p));} //store it in a list

    	nodePY = pyList; // make the node's reference point to the new list

    	availableAttr = new ArrayList<Integer>();
    	for(int i = 0; i < nAttr; i++) {availableAttr.add(i);} // add all possible attributes to list

    }

    Node(List<Double> py_new, Integer lev, List<Integer> parent_avail_attr) {
    	nodePY = py_new;
    	lev = level + 1;
    	// we want a new copy of the parent's available attributes- we do not want two references to the same memory location!!
    	// we want to create a copy of both the list and the object it contains- a deep copy
    	availableAttr = new ArrayList<Integer>();
    	for (Integer i : parent_avail_attr) {
    		  availableAttr.add(new Integer(i));
    		}
    }

}
