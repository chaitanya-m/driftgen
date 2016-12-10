package moa.streams.generators.monash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.math3.random.RandomDataGenerator;

public class Node {

	private static double[] px1d;
	private static double[][] pygx;
	private static double[] py;

	private static SortedMap<Integer, Double> px1dMap;
	private static SortedMap<Integer, List<Double>> pygxMap;

	private static int nAttr;
	private static int nValPerAttr;
	private static int nClasses;

	private static RandomDataGenerator r;

	private static double precision;

	private static int nodeCount;
	private int nodeID;

	private List<Node> children;
	private List<Integer> availableAttr;
	private List<Double> nodePY;

	private int level;
	private double pxVal;

	void setPY (List<Double> givenPY) {
		nodePY = givenPY;
	}

    void setLevel(int lev) {
    	level = lev;
    }

    void postSplit() {
    	System.out.println(px1dMap.size() + " " + px1d.length);
    	assert(px1dMap.size() == px1d.length);
    	assert( px1dMap.size() == pygxMap.size());
    	assert( px1dMap.keySet().size() == pygxMap.keySet().size());

    	int i = 0;
    	for (Iterator<Integer> iterator = px1dMap.keySet().iterator(); iterator.hasNext();) {
			Integer node_id = iterator.next();

			px1d[i] = px1dMap.get(node_id).doubleValue();

			for(int k = 0; k < nClasses; k++){
				pygx[i][k] = pygxMap.get(node_id).get(k).doubleValue();
			}

			i++;

			System.out.print(node_id.intValue() + ": " + px1d[i-1] + " " + Arrays.toString(pygx[i-1]));
			System.out.println();
		}
    }

    void split() {

        // recursion termination
        if (availableAttr.size() == 0){
        	Integer node_ID = new Integer(nodeID);
        	px1dMap.put(node_ID, new Double(this.pxVal));
        	pygxMap.put(node_ID, nodePY);

        	//create new key object or share between maps??
        	return;
        }

        // no attributes left to split on
        /*
        for (Double p : nodePY){
            if (1.0 - p.doubleValue() < precision){return;}
        } // low enough entropy achieved- one class dominates
*/

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


        //System.out.println( "splitting on attribute " + chosenAttr + "\n");

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
        //System.err.println("Rowtotals level " + level + " " + rowTotals );

        // column totals: edgeweights sorted in descending order
        // generate edgeweights for this split
        double[] node_px1d = new double[nValPerAttr];
        DriftGenerator.generateRandomPx1D(node_px1d, r, false);
        List<Double> edgeWeights = new ArrayList<Double>();
        for (double p : node_px1d) {edgeWeights.add(new Double(p));}


        List<Double> colTotalsOrig = new ArrayList<Double>();
        for(Double p : edgeWeights) {colTotalsOrig.add(new Double(p));} //deep copy from edgeweights
        Collections.sort(colTotalsOrig); Collections.reverse(colTotalsOrig);
        List<Double> colTotals = new ArrayList<Double>();
        for (double p : colTotalsOrig) {colTotals.add(new Double(p));} // deep copy from colTotalsAscending
        //System.err.println("Coltotals level " + level + " " + colTotals );


        // pack the matrix using a greedy approach
        int i = 0, j = 0;
        while (i < rowTotals.size() && j < colTotals.size()) {
                A[i][j] = rowTotals.get(i) < colTotals.get(j) ? rowTotals.get(i) : colTotals.get(j);
                rowTotals.set(i, rowTotals.get(i).doubleValue() - A[i][j]);
                colTotals.set(j, colTotals.get(j).doubleValue() - A[i][j]);

                if (rowTotals.get(i) == 0.0) {i++;}
                if (colTotals.get(j) == 0.0) {j++;}
        }
        // divide by the original column totals (the copied ones are now zero)
        for (int c = 0; c < nClasses; c++){
            for (int k = 0; k < nValPerAttr; k++){
                A[c][k] /= colTotalsOrig.get(k);
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
        // each Node references the ArrayList<Double> objects in py_updated
        // this is fine unless we need to evolve this objects differently
        // for the two references for different uses

        // colTotalsOrig are just the reverse sorted edgeweights corresponding to the child py's
        // we're just multiplying all edge-weights to parent with that to child
        // this gives us the ferquency of any sequence of attribute values

        children = new ArrayList<Node>();
        for (int k = 0; k < py_updated.size(); k++){
            children.add(new Node(py_updated.get(k), level+1, availableAttr, colTotalsOrig.get(k)*this.pxVal));
        }

        // recursively split on each child node
        /*for (Node child : children){
            child.printNode();
        }*/
        for (Node child : children){
            child.split();
        }

    }

    void printNode() {
        System.out.println( "\n\n ===== Node " + " level " + level + " ======= \n");
        assert(nodePY!=null);
        try {
            System.out.println("PY is :");
            System.out.println(nodePY);
            System.out.println();
            System.out.println("Available attributes are: ");
            System.out.println(availableAttr);
            System.out.println();
        } catch (Exception e){

        }

        System.out.println(" ========================\n\n ");

    }
    /**
     * Constructor for root node
     * @param n_attr
     * @param n_val_per_attr
     * @param n_classes
     * @param prec
     * @param input_r
     */
    Node(int n_attr, int n_val_per_attr, int n_classes, double prec, RandomDataGenerator input_r) {
    	nAttr = n_attr;
    	nValPerAttr = n_val_per_attr;
    	nClasses = n_classes;
    	precision = prec;
    	r = input_r;

    	// refactor! redundant code
		int nCombinationsValuesForPX = 1;
		for (int a = 0; a < nAttr; a++) {
			nCombinationsValuesForPX *= nValPerAttr;
		}


		py = new double[nClasses];
		px1d = new double[nCombinationsValuesForPX];
		pygx = new double[nCombinationsValuesForPX][nClasses];

		px1dMap = new TreeMap<Integer, Double>();
		pygxMap = new TreeMap<Integer, List<Double>>();

		this.pxVal = 1.0;

    	DriftGenerator.generateRandomPy1D(py, r, false); //generate random py at this node

    	ArrayList<Double> pyList = new ArrayList<Double>();
    	for(double p : py) {pyList.add(new Double(p));} //store it in a list

    	nodePY = pyList; // make the node's reference point to the new list

    	availableAttr = new ArrayList<Integer>();
    	for(int i = 0; i < nAttr; i++) {availableAttr.add(new Integer(i));} // add all possible attributes to list

    	nodeCount = 1;
    	nodeID = nodeCount;

    }

    Node(List<Double> py_new, Integer lev, List<Integer> parent_avail_attr, double px_val) {

    	nodeCount++;
    	this.nodeID = nodeCount;

    	// deep copy
    	nodePY = new ArrayList<Double>();
    	for (Double p : py_new){
    		nodePY.add(new Double(p));
    	}

    	this.setLevel(lev);
    	this.pxVal = px_val;

    	// we want a new copy of the parent's available attributes- we do not want two references to the same memory location!!
    	// we want to create a copy of both the list and the object it contains- a deep copy
    	availableAttr = new ArrayList<Integer>();
    	for (Integer i : parent_avail_attr) {
    		  availableAttr.add(new Integer(i));
    		}
    }

}
