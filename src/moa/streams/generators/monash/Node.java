package moa.streams.generators.monash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
    	System.out.println(Arrays.toString(py));
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

    	int i = 0, j = 0;

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
        // but since we've sorted the rows and columns, the pygx will have to be reshuffled back!
        // Create an integer indexed map for both column and row totals.
        // Sort by value
        // create the matrix and solve it
        // copy in the values and sort by key to get back the original indices

        double A[][] = new double[nClasses][nValPerAttr];
        for (i = 0; i < nClasses; i++){
            for (j = 0; j < nValPerAttr; j++){
                A[i][j] = 0.0; // initialise
            }
        }

        // row totals: indexed nodePY
        // deep copy again

        List<Double> rowTotals = new ArrayList<Double>();
        for(Double p : nodePY) {rowTotals.add(new Double(p));}
        Collections.sort(rowTotals); Collections.reverse(rowTotals);
        // row totals in descending order

        // map the original indices to row totals in descending order
        Map<Integer, Double> rowTotalsDesc = new LinkedHashMap<Integer, Double>();
        i = 0;
        for(Double p : nodePY) {rowTotalsDesc.put(new Integer(i), new Double(p)); i++;}
        rowTotalsDesc = rowTotalsDesc.entrySet().stream()
        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
        .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (x,y)-> {throw new AssertionError();},
                LinkedHashMap::new
        ));
        // we now have rowTotals in descending order by value, mapped to original indices
        // the final values in the matrices should go to the appropriate indices for the new py's

        //System.out.println("rowTotalsDesc " + rowTotalsDesc + " level " + level);
        //System.out.println();

        //System.err.println("Rowtotals level " + level + " " + rowTotals );

        // column totals: edgeweights sorted in descending order
        // generate edgeweights for this split
        double[] node_px1d = new double[nValPerAttr];
        DriftGenerator.generateRandomPx1D(node_px1d, r, false);
        List<Double> edgeWeights = new ArrayList<Double>();
        for (double p : node_px1d) {edgeWeights.add(new Double(p));}


        List<Double> colTotalsOrig = new ArrayList<Double>(); //original descending col totals
        for(Double p : edgeWeights) {colTotalsOrig.add(new Double(p));} // deep copy from edgeweights
        Collections.sort(colTotalsOrig); Collections.reverse(colTotalsOrig);
        List<Double> colTotals = new ArrayList<Double>();
        for (double p : colTotalsOrig) {colTotals.add(new Double(p));} // deep copy from colTotalsAscending
        //System.err.println("Coltotals level " + level + " " + colTotals );

        // map the original indices to col totals in descending order
        Map<Integer, Double> colTotalsDesc = new LinkedHashMap<Integer, Double>();
        i = 0;
        for(Double p : edgeWeights) {colTotalsDesc.put(new Integer(i), new Double(p)); i++;}
        colTotalsDesc = colTotalsDesc.entrySet().stream()
        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
        .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (x,y)-> {throw new AssertionError();},
                LinkedHashMap::new
        ));
        //System.out.println("colTotalsDesc " + colTotalsDesc + " level " + level);

        // pack the matrix using a greedy approach
        i = 0; j = 0;
        while (i < rowTotals.size() && j < colTotals.size()) {
                A[i][j] = rowTotals.get(i) < colTotals.get(j) ? rowTotals.get(i) : colTotals.get(j);
                rowTotals.set(i, rowTotals.get(i).doubleValue() - A[i][j]);
                colTotals.set(j, colTotals.get(j).doubleValue() - A[i][j]);

                if (rowTotals.get(i) == 0.0) {i++;}
                if (colTotals.get(j) == 0.0) {j++;}
        }
        // divide by the original descending column totals (the copied ones are now zero)
        for (int c = 0; c < nClasses; c++){
            for (int k = 0; k < nValPerAttr; k++){
                A[c][k] /= colTotalsOrig.get(k);
            }
        }

        double A_orig[][] = new double[nClasses][nValPerAttr];
        for (i = 0; i < nClasses; i++){
            for (j = 0; j < nValPerAttr; j++){
                A_orig[i][j] = 0.0; // initialise
            }
        }
        // linked hashmap entryset iterator will return in insertion order
        i = 0; j = 0;
        for(Iterator<Map.Entry<Integer, Double>> row_iter = rowTotalsDesc.entrySet().iterator(); row_iter.hasNext();){
        	j = 0;
        	int orig_row_index = row_iter.next().getKey().intValue();
        	//System.out.println(orig_row_index);
            for(Iterator<Map.Entry<Integer, Double>> col_iter = colTotalsDesc.entrySet().iterator(); col_iter.hasNext();){
            	int orig_col_index = col_iter.next().getKey().intValue();
            	//System.out.println(orig_col_index);

            	A_orig[orig_row_index][orig_col_index] = A[i][j];
            	j++;
            }
            i++;
        }
/*
        for (i = 0; i < A.length; i++) {
        	System.out.println(Arrays.toString(A_orig[i]));
        }
        System.out.println("=");
        for (i = 0; i < A.length; i++) {
        	System.out.println(Arrays.toString(A[i]));
        }
        System.out.println("============");
*/

        // convert each column into a list- this is a PY for a child node
        // first create empty py vectors- one per attribute value
        ArrayList<ArrayList<Double>> py_updated = new ArrayList<ArrayList<Double>>();
        for (int k = 0; k < nValPerAttr; k++){
            ArrayList<Double> py_ind = new ArrayList<Double>();
            py_updated.add(py_ind);
        }

        // then traverse the matrix, adding the column elements to the corresponding py
        // attribute by attribute, you are adding the class values one by one
        // iterate through all the attributes (across a row) adding the values (1st row, then 2nd row) to respective columns
        // these have to go to original indices
        // the matrix with original indices for rows and columns- before the sort- will have to be contructed and values placed
        // each element will have to go from A[i][j] -> A[i_orig][j_orig]

        for (int c = 0; c < nClasses; c++){
            for (int k = 0; k < nValPerAttr; k++){
                py_updated.get(k).add(new Double(A_orig[c][k]));
            }

        }
        //System.out.println(py_updated + " level " + level);

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
