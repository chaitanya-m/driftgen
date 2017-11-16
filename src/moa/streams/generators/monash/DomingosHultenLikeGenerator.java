package moa.streams.generators.monash;

import com.yahoo.labs.samoa.instances.DenseInstance;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.InstancesHeader;

import moa.MOAObject;
import moa.core.Example;
import moa.core.InstanceExample;
import moa.streams.InstanceStream;

public class DomingosHultenLikeGenerator implements InstanceStream {

/*Generate a tree based on some user input, then generate a data stream from that tree*/

@Override
public long estimatedRemainingInstances() {
	// TODO Auto-generated method stub
	return 0;
}

@Override
public InstancesHeader getHeader() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public boolean hasMoreInstances() {
	// TODO Auto-generated method stub
	return false;
}

@Override
public boolean isRestartable() {
	// TODO Auto-generated method stub
	return false;
}

@Override
public void restart() {
	// TODO Auto-generated method stub

}

@Override
public MOAObject copy() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public void getDescription(StringBuilder arg0, int arg1) {
	// TODO Auto-generated method stub

}

@Override
public int measureByteSize() {
	// TODO Auto-generated method stub
	return 0;
}

@Override
public Example<Instance> nextInstance() {
	// TODO Auto-generated method stub
	return null;
}


}
