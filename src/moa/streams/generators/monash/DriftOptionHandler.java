package moa.streams.generators.monash;

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
