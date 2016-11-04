package moa.streams.generators.monash;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.ml.distance.DistanceMeasure;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.github.javacliparser.MultiChoiceOption;


import moa.core.ObjectRepository;
import moa.options.AbstractOptionHandler;
import moa.tasks.TaskMonitor;
/**
 * An option handler for drift generators.
 */
public abstract class DriftOptionHandler extends AbstractOptionHandler {

	public DriftOptionHandler() {
		super();
	}

	private static final long serialVersionUID = 1836139477178981456L;
	// Looks like the serializability comes from MOA





	@Override
	public void getDescription(StringBuilder arg0, int arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void prepareForUseImpl(TaskMonitor arg0, ObjectRepository arg1) {
		// TODO Auto-generated method stub

	}

}
