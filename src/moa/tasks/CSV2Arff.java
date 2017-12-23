package moa.tasks;

import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.io.IOException;

import com.github.javacliparser.StringOption;

import moa.MOAObject;
import moa.core.ObjectRepository;
import moa.options.OptionsHandler;

public class CSV2Arff extends MainTask{



/**
	 *
	 */
	private static final long serialVersionUID = -4002173105867194181L;
public StringOption inputFile = new StringOption("inputCSV", 'i', "CSV Input", null);
  public StringOption outputFile = new StringOption("outputARFF", 'o', "ARFF Output", null);

/**
   * takes 2 arguments:
   * - CSV input file
   * - ARFF output file
   */


  public CSV2Arff(){
		//System.out.println("Creating CSV2Arff object");
  }

 /* public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.out.println("\nUsage: CSV2Arff <input.csv> <output.arff>\n");
      System.exit(1);
    }

	  Object doTask = doTask();

  }*/


@Override
public void getDescription(StringBuilder arg0, int arg1) {
	// TODO Auto-generated method stub

}



@Override
protected Object doMainTask(TaskMonitor arg0, ObjectRepository arg1) {

	System.out.println("Attempting to convert CSV to Arff");
    // load CSV
    CSVLoader loader = new CSVLoader();
    try {
		loader.setSource(new File(inputFile.getValue()));

    Instances data = loader.getDataSet();

    // save ARFF
    ArffSaver saver = new ArffSaver();
    saver.setInstances(data);
    saver.setFile(new File(outputFile.getValue()));
    saver.setDestination(new File(outputFile.getValue()));
    saver.writeBatch();
    } catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    System.out.println("Done!");
	return null;
}

@Override
public Class<?> getTaskResultType() {
	// TODO Auto-generated method stub
	return null;
}
}